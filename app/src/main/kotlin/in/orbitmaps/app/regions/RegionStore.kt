// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.app.net.Download
import `in`.orbitmaps.app.net.HttpFiles
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A region that is fully on the phone: every file was checked against the manifest. */
data class InstalledRegion(val manifest: RegionManifest, val dir: File) {
    val id: String get() = manifest.id

    fun file(role: PackRole): File = File(dir, role.fileName)

    /** The search index and routing files are optional in a pack; the map is always there. */
    fun hasFile(role: PackRole): Boolean = manifest.file(role) != null && file(role).isFile
}

sealed interface PackInstall {
    data class Installed(val region: InstalledRegion) : PackInstall

    data object Cancelled : PackInstall

    /** Not enough free space: [neededBytes] more are required. */
    data class NoSpace(val neededBytes: Long) : PackInstall

    /** Offline, the server failed, or the pack isn't there. A partial download is kept to resume. */
    data object Unavailable : PackInstall

    /** A downloaded file didn't match its checksum; it was deleted. */
    data class Corrupt(val fileName: String) : PackInstall
}

/**
 * Region packs on the phone: `root/installed/<id>/` holds a pack that was downloaded in full and
 * verified, `root/staging/<id>/` a download in progress. A region only becomes installed by one
 * rename, after every file's size and SHA-256 matched the manifest, so a half-finished or damaged
 * pack is never used. Nothing here logs which regions are chosen.
 */
class RegionStore(private val root: File) {
    private val installedRoot = File(root, "installed")
    private val stagingRoot = File(root, "staging")

    /** Every complete installed region (manifest readable and each file present at its listed size). */
    fun installed(): List<InstalledRegion> =
        installedRoot.listFiles { f -> f.isDirectory && !f.name.endsWith(OLD_SUFFIX) }.orEmpty()
            .mapNotNull { read(it) }
            .sortedBy { it.manifest.name.lowercase() }

    fun get(id: String): InstalledRegion? = if (isSafeId(id)) read(File(installedRoot, id)) else null

    private fun read(dir: File): InstalledRegion? {
        val manifestFile = File(dir, MANIFEST)
        if (!manifestFile.isFile) return null
        val manifest = try {
            RegionJson.parseManifest(manifestFile.readText())
        } catch (e: ManifestException) {
            return null
        } catch (e: IOException) {
            return null
        }
        if (manifest.id != dir.name) return null
        val complete = manifest.files.all { File(dir, it.role.fileName).let { f -> f.isFile && f.length() == it.size } }
        return if (complete) InstalledRegion(manifest, dir) else null
    }

    fun delete(id: String) {
        if (!isSafeId(id)) return
        File(installedRoot, id).deleteRecursively()
        File(stagingRoot, id).deleteRecursively()
    }

    /** Bytes of a partial download already on the phone, for "resumes at 40%" and the space check. */
    fun stagedBytes(id: String): Long =
        if (isSafeId(id)) File(stagingRoot, id).walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L

    /**
     * Downloads and verifies every file of [manifest] from [baseUrl]/<id>/, then installs the region.
     * Blocking: call off the main thread.
     *
     * @param manifestText the manifest exactly as downloaded; it is stored beside the files.
     * @param freeBytes free space on the phone's storage now.
     * @param shouldContinue polled while downloading; false cancels and keeps what was downloaded.
     * @param onProgress called with (bytes done, bytes total) across all files.
     */
    fun install(
        manifest: RegionManifest,
        manifestText: String,
        baseUrl: String,
        freeBytes: Long,
        shouldContinue: () -> Boolean = { true },
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): PackInstall {
        require(isSafeId(manifest.id)) { "unsafe region id" }
        val staging = File(stagingRoot, manifest.id).also { it.mkdirs() }
        val total = manifest.totalBytes
        val needed = total - stagedBytes(manifest.id)
        val margin = maxOf(MIN_MARGIN_BYTES, total / MARGIN_DIVISOR)
        if (freeBytes < needed + margin) return PackInstall.NoSpace(needed + margin - freeBytes)

        var finishedBytes = 0L
        for (pack in manifest.files) {
            val target = File(staging, pack.role.fileName)
            if (!(target.isFile && target.length() == pack.size && sha256(target) == pack.sha256)) {
                val result = HttpFiles.downloadResumable(
                    url = "$baseUrl/${manifest.id}/${pack.role.fileName}",
                    target = target,
                    expectedSize = pack.size,
                    shouldContinue = shouldContinue,
                    onBytes = { done -> onProgress(finishedBytes + done, total) }
                )
                when (result) {
                    is Download.Saved -> Unit
                    Download.Cancelled -> return PackInstall.Cancelled
                    Download.NotFound, Download.Failed -> return PackInstall.Unavailable
                }
                if (sha256(target) != pack.sha256) {
                    target.delete()
                    return PackInstall.Corrupt(pack.role.fileName)
                }
            }
            finishedBytes += pack.size
            onProgress(finishedBytes, total)
        }
        File(staging, MANIFEST).writeText(manifestText)
        return swapIn(manifest, staging)
    }

    /** Replaces any older copy by renaming, keeping the old one until the new one is in place. */
    private fun swapIn(manifest: RegionManifest, staging: File): PackInstall {
        installedRoot.mkdirs()
        val target = File(installedRoot, manifest.id)
        val old = File(installedRoot, manifest.id + OLD_SUFFIX)
        old.deleteRecursively()
        if (target.exists() && !target.renameTo(old)) return PackInstall.Unavailable
        if (!staging.renameTo(target)) {
            if (old.exists()) old.renameTo(target)
            return PackInstall.Unavailable
        }
        old.deleteRecursively()
        val region = read(target) ?: return PackInstall.Unavailable
        return PackInstall.Installed(region)
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val OLD_SUFFIX = ".old"
        const val MIN_MARGIN_BYTES = 20L * 1024 * 1024
        const val MARGIN_DIVISOR = 20L // keep 5% of the pack's size free as well
        val SAFE_ID = Regex("^[a-z0-9][a-z0-9-]{0,40}$")

        fun isSafeId(id: String) = SAFE_ID.matches(id)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

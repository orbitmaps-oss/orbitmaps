// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface InstallResult {
    /** [copied] is false when the installed file was already up to date. */
    data class Installed(val file: File, val copied: Boolean) : InstallResult

    /** The APK doesn't contain the region (release builds). */
    data object NotBundled : InstallResult

    /** The bundled file isn't a PMTiles v3 archive. Nothing was installed. */
    data object Invalid : InstallResult

    /** Copying failed (for example, the disk is full). Nothing was installed. */
    data object Failed : InstallResult
}

/**
 * Copies a bundled PMTiles region into app storage so MapLibre can read it as a plain file.
 *
 * The copy goes to a `.tmp` file that is synced and then atomically renamed, so an interrupted copy
 * never leaves a half-written region behind. A marker file next to the region records [install]'s
 * `stamp` and the file size; while both match, the copy is skipped.
 */
object RegionInstaller {
    private val PMTILES_MAGIC = "PMTiles".toByteArray(Charsets.US_ASCII)
    private const val PMTILES_VERSION = 3

    /**
     * @param open opens the bundled region, or returns null if it isn't bundled.
     * @param stamp changes whenever the bundled file may have changed (the app's last update time).
     */
    fun install(open: () -> InputStream?, target: File, stamp: String): InstallResult {
        val marker = File(target.parentFile, "${target.name}.installed")
        val upToDate = target.isFile &&
            marker.isFile &&
            marker.readText() == markerText(stamp, target.length()) &&
            hasPmTilesHeader(target)
        if (upToDate) return InstallResult.Installed(target, copied = false)
        val dir = target.absoluteFile.parentFile
        val tmp = File(dir, "${target.name}.tmp")
        return try {
            val input = open() ?: return InstallResult.NotBundled
            dir.mkdirs()
            marker.delete()
            input.use { source ->
                FileOutputStream(tmp).use { out ->
                    source.copyTo(out)
                    out.fd.sync()
                }
            }
            if (!hasPmTilesHeader(tmp)) {
                tmp.delete()
                return InstallResult.Invalid
            }
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            marker.writeText(markerText(stamp, target.length()))
            InstallResult.Installed(target, copied = true)
        } catch (e: IOException) {
            tmp.delete()
            InstallResult.Failed
        }
    }

    fun hasPmTilesHeader(file: File): Boolean {
        val header = ByteArray(PMTILES_MAGIC.size + 1)
        val read = file.inputStream().use { it.readNBytesCompat(header) }
        return read == header.size &&
            header.copyOf(PMTILES_MAGIC.size).contentEquals(PMTILES_MAGIC) &&
            header[PMTILES_MAGIC.size].toInt() == PMTILES_VERSION
    }

    private fun markerText(stamp: String, size: Long) = "$stamp\n$size\n"

    private fun InputStream.readNBytesCompat(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}

/** Installs [SampleRegion] from the APK assets into `filesDir`, off the main thread. */
suspend fun installSampleRegion(context: Context): InstallResult = withContext(Dispatchers.IO) {
    RegionInstaller.install(
        open = {
            try {
                context.assets.open(SampleRegion.ASSET_PATH)
            } catch (e: FileNotFoundException) {
                null
            }
        },
        target = File(context.filesDir, SampleRegion.INSTALLED_PATH),
        stamp = lastUpdateTime(context).toString()
    )
}

private fun lastUpdateTime(context: Context): Long {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    return info.lastUpdateTime
}

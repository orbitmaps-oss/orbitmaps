// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface Download {
    data class Saved(val bytes: Long) : Download

    /** The server has no such file (HTTP 404). */
    data object NotFound : Download

    /** The caller asked to stop; a partial file is kept so the download can resume later. */
    data object Cancelled : Download

    /** Offline, timed out, or the server failed; nothing was written. */
    data object Failed : Download
}

/**
 * Downloads our static files. Requests carry only the URL: no cookies, no identifiers, no redirects,
 * and a generic User-Agent (Android's default one names the phone model). Files are written to a
 * `.part` file first and renamed when complete. Nothing is logged.
 */
object HttpFiles {
    const val USER_AGENT = "OrbitMaps"
    private const val TIMEOUT_MS = 15_000
    private const val BUFFER_BYTES = 64 * 1024
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    /**
     * Whether [url] answers with content starting with [expectedPrefix], reading only those bytes
     * (an HTTP range request). Used to check that our server has a file before relying on it.
     * Blocking: call off the main thread.
     */
    fun startsWith(url: String, expectedPrefix: ByteArray): Boolean {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return false
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Range", "bytes=0-${expectedPrefix.size - 1}")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) return false
            val head = ByteArray(expectedPrefix.size)
            var read = 0
            connection.inputStream.use { input ->
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
            }
            read == head.size && head.contentEquals(expectedPrefix)
        } catch (e: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads a file of known [expectedSize] that may be large, resuming a partial copy left by an
     * earlier attempt (HTTP Range). The result is renamed to [target] only when it has exactly the
     * expected size; checking its content is the caller's job. [onBytes] gets the total bytes present
     * so far (including resumed ones), and [shouldContinue] is polled while reading.
     * Blocking: call off the main thread.
     */
    fun downloadResumable(
        url: String,
        target: File,
        expectedSize: Long,
        shouldContinue: () -> Boolean = { true },
        onBytes: (Long) -> Unit = {}
    ): Download {
        val partial = File(target.parentFile, "${target.name}.part")
        target.parentFile?.mkdirs()
        var have = if (partial.isFile) partial.length() else 0L
        if (have > expectedSize) {
            partial.delete()
            have = 0
        }
        if (have == expectedSize) return finish(partial, target, expectedSize)
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Download.Failed
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit
                HttpURLConnection.HTTP_OK -> have = 0 // the server sent everything: start over
                HttpURLConnection.HTTP_NOT_FOUND -> return Download.NotFound
                HTTP_RANGE_NOT_SATISFIABLE -> {
                    partial.delete()
                    return Download.Failed
                }
                else -> return Download.Failed
            }
            onBytes(have)
            val buffer = ByteArray(BUFFER_BYTES)
            var cancelled = false
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, have > 0).use { out ->
                    var total = have
                    while (true) {
                        if (!shouldContinue()) {
                            cancelled = true
                            break
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        total += read
                        onBytes(total)
                        if (total > expectedSize) break
                    }
                }
            }
            when {
                cancelled -> Download.Cancelled
                partial.length() > expectedSize -> {
                    partial.delete()
                    Download.Failed
                }
                partial.length() < expectedSize -> Download.Failed // keep it: the next try resumes
                else -> finish(partial, target, expectedSize)
            }
        } catch (e: IOException) {
            Download.Failed // the partial file stays for the next attempt
        } finally {
            connection.disconnect()
        }
    }

    private fun finish(partial: File, target: File, expectedSize: Long): Download {
        target.delete()
        return if (partial.renameTo(target)) Download.Saved(expectedSize) else Download.Failed
    }

    /** Blocking: call off the main thread. */
    fun download(url: String, target: File): Download {
        val partial = File(target.parentFile, "${target.name}.part")
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Download.Failed
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    target.parentFile?.mkdirs()
                    val size = connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                    target.delete() // renaming over an existing file fails on some systems
                    if (partial.renameTo(target)) {
                        Download.Saved(size)
                    } else {
                        partial.delete()
                        Download.Failed
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Download.NotFound
                else -> Download.Failed
            }
        } catch (e: IOException) {
            partial.delete()
            Download.Failed
        } finally {
            connection.disconnect()
        }
    }
}

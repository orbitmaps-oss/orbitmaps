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

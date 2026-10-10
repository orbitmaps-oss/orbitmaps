// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.net

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpFilesTest {
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val ranges = CopyOnWriteArrayList<String?>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            ranges += exchange.requestHeaders.getFirst("Range")
            val body = when (exchange.requestURI.path) {
                "/world.pmtiles" -> "PMTiles\u0003rest of the archive".toByteArray()
                "/wrong.pmtiles" -> "<html>not found page</html>".toByteArray()
                else -> null
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                // Like R2: answer a range request with just those bytes.
                val part = body.copyOf(7)
                exchange.sendResponseHeaders(206, part.size.toLong())
                exchange.responseBody.use { it.write(part) }
            }
            exchange.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun aRealPmtilesFileIsReadyAndOnlyItsHeaderIsAskedFor() {
        assertTrue(HttpFiles.startsWith("$base/world.pmtiles", PMTILES_MAGIC))
        assertEquals(listOf<String?>("bytes=0-6"), ranges)
    }

    @Test
    fun anErrorPageOrMissingFileIsNotReady() {
        assertFalse(HttpFiles.startsWith("$base/wrong.pmtiles", PMTILES_MAGIC))
        assertFalse(HttpFiles.startsWith("$base/missing.pmtiles", PMTILES_MAGIC))
    }

    @Test
    fun anUnreachableServerIsNotReady() {
        val port = server.address.port
        server.stop(0)
        assertFalse(HttpFiles.startsWith("http://127.0.0.1:$port/world.pmtiles", PMTILES_MAGIC))
    }
}

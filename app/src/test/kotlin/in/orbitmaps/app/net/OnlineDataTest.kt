// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineDataTest {
    @Test
    fun everyUrlIsOnOurHostOverHttps() {
        listOf(OnlineData.WORLD_TILES_URL, OnlineData.ROUTING_TILES_URL, OnlineData.SEARCH_SHARDS_URL).forEach {
            assertTrue(it, it.startsWith("https://data.orbitmaps.in/v1/"))
            assertTrue(it, OnlineData.isOurs(it))
        }
    }

    @Test
    fun otherHostsAndLookalikesAreNotOurs() {
        assertFalse(OnlineData.isOurs("https://tile.openstreetmap.org/1/2/3.png"))
        assertFalse(OnlineData.isOurs("http://data.orbitmaps.in/v1/tiles/world.pmtiles"))
        assertFalse(OnlineData.isOurs("https://data.orbitmaps.in.evil.example/v1/x"))
        assertFalse(OnlineData.isOurs("https://data.orbitmaps.in/v1"))
    }

    @Test
    fun mapStreamsOnlyWhenAllowedAndOnline() {
        val wifi = NetworkStatus(online = true, unmetered = true)
        val mobile = NetworkStatus(online = true, unmetered = false)
        assertEquals(MapMode.Online, chooseMapMode(streamAllowed = true, network = wifi))
        assertEquals(MapMode.Online, chooseMapMode(streamAllowed = true, network = mobile))
        assertEquals(MapMode.Offline, chooseMapMode(streamAllowed = false, network = wifi))
        assertEquals(MapMode.Offline, chooseMapMode(streamAllowed = true, network = NetworkStatus.Offline))
    }
}

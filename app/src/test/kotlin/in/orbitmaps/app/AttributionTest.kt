// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionTest {
    @Test
    fun allLinksUseHttps() {
        Attribution.all.forEach { url -> assertTrue(url, url.startsWith("https://")) }
    }

    @Test
    fun osmLinkPointsToCopyrightPage() {
        assertEquals("https://www.openstreetmap.org/copyright", Attribution.OSM_COPYRIGHT_URL)
    }

    @Test
    fun sourceLinkPointsToProjectRepository() {
        assertEquals("https://github.com/orbitmaps-oss/orbitmaps", Attribution.SOURCE_CODE_URL)
    }
}

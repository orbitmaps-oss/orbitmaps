// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLocationTest {
    @Test
    fun androidTwelveUsesTheFusedProviderWhenThePhoneHasIt() {
        assertEquals(listOf("fused"), DeviceLocation.providers(listOf("gps", "network", "fused", "passive"), sdk = 31))
    }

    @Test
    fun otherwiseGpsAndNetworkTogether() {
        assertEquals(listOf("gps", "network"), DeviceLocation.providers(listOf("passive", "network", "gps"), sdk = 30))
        assertEquals(listOf("gps", "network"), DeviceLocation.providers(listOf("gps", "network"), sdk = 34))
        assertEquals(listOf("network"), DeviceLocation.providers(listOf("network", "passive"), sdk = 26))
        assertEquals(emptyList<String>(), DeviceLocation.providers(listOf("passive"), sdk = 26))
    }

    @Test
    fun theFirstFixIsAlwaysTaken() {
        assertTrue(DeviceLocation.isBetter(1_000, 50f, currentTimeMs = null, currentAccuracyM = Float.MAX_VALUE))
    }

    @Test
    fun aNewerFixOfTheSameOrBetterAccuracyWins() {
        assertTrue(DeviceLocation.isBetter(2_000, 10f, 1_000, 10f))
        assertTrue(DeviceLocation.isBetter(2_000, 5f, 1_000, 10f))
    }

    @Test
    fun aSlightlyNewerButMuchWorseFixLoses() {
        // A network fix (500 m) arriving just after a GPS fix (8 m).
        assertFalse(DeviceLocation.isBetter(2_000, 500f, 1_000, 8f))
        // A little worse is fine.
        assertTrue(DeviceLocation.isBetter(2_000, 30f, 1_000, 8f))
    }

    @Test
    fun staleFixesLoseAndOldOnesAreReplaced() {
        assertFalse(DeviceLocation.isBetter(1_000, 5f, 60_000, 50f))
        assertTrue(DeviceLocation.isBetter(60_000, 500f, 1_000, 5f))
    }

    @Test
    fun bothLocationPermissionsAreRequestedSoTheUserCanPickApproximate() {
        assertEquals(
            setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            DeviceLocation.PERMISSIONS.toSet()
        )
    }
}

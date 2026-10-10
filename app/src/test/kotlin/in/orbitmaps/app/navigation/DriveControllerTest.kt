// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.core.model.LatLon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveControllerTest {
    private val p0 = LatLon(15.40, 73.82)
    private val p1 = LatLon(15.41, 73.82)
    private val p2 = LatLon(15.42, 73.82)
    private val p3 = LatLon(15.42, 73.83)

    private fun m(type: Int, text: String, pre: String?, begin: Int, km: Double, s: Double) =
        Maneuver(type, text, pre, null, emptyList(), km, s, begin)

    private fun route(shape: List<LatLon> = listOf(p0, p1, p2, p3)) = NavRoute(
        shape,
        listOf(
            m(1, "Drive north.", "Drive north.", 0, 2.224, 200.0),
            m(10, "Turn right.", "Turn right.", 2, 1.072, 100.0),
            m(4, "You have arrived.", "You have arrived at your destination.", 3, 0.0, 0.0)
        )
    )

    private fun east(p: LatLon, metres: Double) =
        LatLon(p.latitude, p.longitude + metres / (111_320.0 * Math.cos(Math.toRadians(p.latitude))))

    @Test
    fun followingTheRouteUpdatesTheStateAndSpeaksTheStartOnce() = runBlocking {
        val spoken = mutableListOf<Announcement>()
        val controller = DriveController(route(), { null }, { spoken += it })
        controller.onFix(Fix(p0))
        controller.onFix(Fix(LatLon(15.401, 73.82)))
        val nav = controller.state.value.nav
        assertTrue(nav is NavState.OnRoute)
        assertEquals(listOf("Drive north."), spoken.map { it.text })
        assertFalse(controller.state.value.rerouting)
    }

    @Test
    fun leavingTheRouteReroutesOnceAndFollowsTheNewRoute() = runBlocking {
        var clock = 0L
        val newRoute = route(listOf(east(p1, 300.0), LatLon(15.415, 73.8227), LatLon(15.42, 73.8227), p3))
        var calls = 0
        val spoken = mutableListOf<Announcement>()
        val controller = DriveController(route(), {
            calls++
            newRoute
        }, { spoken += it }, now = { clock })
        controller.onFix(Fix(p0))
        val away = Fix(east(p1, 300.0))
        controller.onFix(away) // one bad fix first
        assertEquals(0, calls)
        controller.onFix(away)
        assertEquals(1, calls)
        assertTrue(controller.state.value.route === newRoute)
        assertFalse(controller.state.value.rerouting)
        assertFalse(controller.state.value.rerouteFailed)
        // The new route is announced from its start.
        controller.onFix(away)
        assertEquals(listOf("Drive north.", "Drive north."), spoken.map { it.text })
    }

    @Test
    fun aFailedRerouteIsReportedAndNotRetriedBeforeTheCooldown() = runBlocking {
        var clock = 1_000_000L
        var calls = 0
        val controller = DriveController(route(), {
            calls++
            null
        }, {}, now = { clock })
        val away = Fix(east(p1, 300.0))
        repeat(2) { controller.onFix(away) }
        assertEquals(1, calls)
        assertTrue(controller.state.value.rerouteFailed)
        assertTrue(controller.state.value.nav is NavState.OffRoute)

        clock += 2_000
        controller.onFix(away)
        assertEquals("still cooling down", 1, calls)

        clock += 10_000
        controller.onFix(away)
        assertEquals(2, calls)
    }

    @Test
    fun arrivalIsSpokenOnceAndStays() = runBlocking {
        val spoken = mutableListOf<Announcement>()
        val controller = DriveController(route(), { null }, { spoken += it })
        controller.onFix(Fix(LatLon(15.42, 73.8299)))
        controller.onFix(Fix(LatLon(15.42, 73.8299)))
        assertEquals(NavState.Arrived, controller.state.value.nav)
        assertEquals(1, spoken.count { it.text.contains("arrived") })
    }

    @Test
    fun simulatedDriveStartsAtTheStartEndsAtTheEndAndHeadsNorthThenEast() {
        val r = route()
        val fixes = SimulatedDrive.along(r, stepM = 100.0, speedMps = 20f).toList()
        assertEquals(p0.latitude, fixes.first().fix.position.latitude, 1e-9)
        assertEquals(p3.longitude, fixes.last().fix.position.longitude, 1e-9)
        assertEquals(0f, fixes[1].bearingDeg, 1f)
        assertEquals(90f, fixes[fixes.size - 3].bearingDeg, 1f)
        assertTrue(fixes.size in 30..40)
        assertEquals(20f, fixes[1].fix.speedMps)
    }

    @Test
    fun aSimulatedDriveRunsTheWholeRouteToArrival() = runBlocking {
        val r = route()
        val spoken = mutableListOf<Announcement>()
        val controller = DriveController(r, { null }, { spoken += it }, simulated = true)
        SimulatedDrive.along(r, stepM = 25.0, speedMps = 14f).forEach { controller.onFix(it.fix) }
        assertEquals(NavState.Arrived, controller.state.value.nav)
        assertTrue(controller.state.value.simulated)
        assertTrue(spoken.any { it.kind == Announcer.Kind.Start })
        assertNotNull(spoken.firstOrNull { it.kind == Announcer.Kind.Pre })
        assertNull(spoken.firstOrNull { it.text.isBlank() })
    }

    @Test
    fun arrowsPointTheWayOfTheTurn() {
        assertEquals(-90f, NavFormat.turnRotation(8))
        assertEquals(0f, NavFormat.turnRotation(10))
        assertEquals(180f, NavFormat.turnRotation(15))
        assertEquals(-45f, NavFormat.turnRotation(9))
        assertEquals(-135f, NavFormat.turnRotation(16))
        assertEquals(-90f, NavFormat.turnRotation(999))
    }

    @Test
    fun distancesAreRoundedForSpeechAndDisplay() {
        assertEquals(10, NavFormat.roundedMetres(4.0))
        assertEquals(30, NavFormat.roundedMetres(26.0))
        assertEquals(100, NavFormat.roundedMetres(99.0))
        assertEquals(350, NavFormat.roundedMetres(337.0))
        assertEquals(950, NavFormat.roundedMetres(960.0))
        assertTrue(NavFormat.isKm(975.0))
        assertTrue(NavFormat.isKm(1000.0))
        assertFalse(NavFormat.isKm(974.0))
        assertEquals(1.2, NavFormat.kmTenths(1234.0), 1e-9)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlanceModelTest {
    private val a = LatLon(15.40, 73.82)
    private val b = LatLon(15.41, 73.82)
    private val c = LatLon(15.41, 73.83)

    private fun m(type: Int, text: String, begin: Int, km: Double, s: Double) =
        Maneuver(type, text, text, null, emptyList(), km, s, begin)

    private val route = NavRoute(
        listOf(a, b, c),
        listOf(
            m(1, "Drive north.", 0, 1.1, 100.0),
            m(10, "Turn right.", 1, 1.0, 90.0),
            m(4, "You have arrived.", 2, 0.0, 0.0)
        )
    )

    private val onRoute = NavState.OnRoute(
        current = route.maneuvers[0],
        next = route.maneuvers[1],
        nextIndex = 1,
        distanceToNextM = 400.0,
        remainingM = 1500.0,
        remainingSeconds = 125.0,
        snapped = a,
        speedMps = 10f
    )

    @Test
    fun beforeTheFirstFixItWaitsAndShowsTheWholeTrip() {
        val model = GlanceModel.from(DriveUi(route), nowMs = 1_000)
        assertEquals(GlanceStatus.Waiting, model.status)
        assertEquals("Drive north.", model.instruction)
        assertNull(model.distanceM)
        assertEquals(4, model.remainingMinutes)
        assertEquals(1_000 + (route.timeSeconds * 1000).toLong(), model.arrivalAtMs)
    }

    @Test
    fun onTheRouteItShowsTheNextTurnWithItsDistanceSpeedAndTimeLeft() {
        val model = GlanceModel.from(DriveUi(route, onRoute, fix = Fix(a, speedMps = 13.9f), lastOnRoute = onRoute), 0)
        assertEquals(GlanceStatus.None, model.status)
        assertEquals("Turn right.", model.instruction)
        assertEquals(0f, model.arrowRotation)
        assertEquals(400.0, model.distanceM!!, 0.0)
        assertEquals(50, model.speedKmh)
        assertEquals(3, model.remainingMinutes)
        assertEquals(1.5, model.remainingKm, 1e-9)
        assertEquals(125_000, model.arrivalAtMs)
    }

    @Test
    fun whileReroutingItKeepsTheLastStepAndSaysSo() {
        val off = NavState.OffRoute(b, 120.0)
        val model = GlanceModel.from(DriveUi(route, off, rerouting = true, lastOnRoute = onRoute), 0)
        assertEquals(GlanceStatus.Rerouting, model.status)
        assertEquals("Turn right.", model.instruction)
        assertEquals(400.0, model.distanceM!!, 0.0)
    }

    @Test
    fun aFailedRerouteSaysOffRoute() {
        val off = NavState.OffRoute(b, 120.0)
        val model = GlanceModel.from(DriveUi(route, off, rerouteFailed = true, lastOnRoute = onRoute), 0)
        assertEquals(GlanceStatus.OffRoute, model.status)
    }

    @Test
    fun arrivalShowsTheDestinationAndNothingLeft() {
        val model = GlanceModel.from(DriveUi(route, NavState.Arrived, lastOnRoute = onRoute), 5)
        assertEquals(GlanceStatus.Arrived, model.status)
        assertEquals("You have arrived.", model.instruction)
        assertNull(model.distanceM)
        assertEquals(0, model.remainingMinutes)
        assertEquals(5, model.arrivalAtMs)
    }

    @Test
    fun theSimulatedFlagIsCarriedThrough() {
        assertEquals(true, GlanceModel.from(DriveUi(route, simulated = true), 0).simulated)
    }
}

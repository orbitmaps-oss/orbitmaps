// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.app.routing.RoutingException
import `in`.orbitmaps.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TripNavigatorTest {
    // North along 73.82E for about 2.2 km, turn right, east for about 1.1 km to the destination.
    private val p0 = LatLon(15.40, 73.82)
    private val p1 = LatLon(15.41, 73.82)
    private val p2 = LatLon(15.42, 73.82)
    private val p3 = LatLon(15.42, 73.83)

    private val start =
        Maneuver(1, "Drive north on A Road.", "Drive north on A Road.", null, listOf("A Road"), 2.224, 200.0, 0)
    private val turn =
        Maneuver(
            10,
            "Turn right onto B Road.",
            "Turn right onto B Road.",
            "Turn right onto B Road.",
            listOf("B Road"),
            1.072,
            100.0,
            2
        )
    private val arrive =
        Maneuver(4, "You have arrived.", "You have arrived at your destination.", null, emptyList(), 0.0, 0.0, 3)

    private fun route() = NavRoute(listOf(p0, p1, p2, p3), listOf(start, turn, arrive))

    private fun onRoute(state: NavState): NavState.OnRoute {
        assertTrue("expected OnRoute, was $state", state is NavState.OnRoute)
        return state as NavState.OnRoute
    }

    /** About [metres] east of [p]. */
    private fun east(p: LatLon, metres: Double) = LatLon(
        p.latitude,
        p.longitude + metres / (111_320.0 * Math.cos(Math.toRadians(p.latitude)))
    )

    @Test
    fun routeLengthAddsUpTheLine() {
        assertEquals(2224.0 + 1072.0, route().lengthM, 15.0)
        assertEquals(300.0, route().timeSeconds, 0.0)
    }

    @Test
    fun atTheStartTheTurnIsNextAndEverythingIsAhead() {
        val state = onRoute(TripNavigator(route()).update(Fix(p0)))
        assertEquals(start, state.current)
        assertEquals(turn, state.next)
        assertEquals(2224.0, state.distanceToNextM, 10.0)
        assertEquals(route().lengthM, state.remainingM, 1.0)
        assertEquals(300.0, state.remainingSeconds, 1.0)
    }

    @Test
    fun halfwayAlongTheFirstStepWithANoisyFix() {
        val state = onRoute(TripNavigator(route()).update(Fix(east(p1, 10.0), speedMps = 12f)))
        assertEquals(1112.0, state.distanceToNextM, 10.0)
        assertEquals(p1.latitude, state.snapped.latitude, 1e-4)
        assertEquals(p1.longitude, state.snapped.longitude, 1e-6)
        assertEquals(100.0 + 100.0, state.remainingSeconds, 2.0)
        assertEquals(12f, state.speedMps)
    }

    @Test
    fun afterTheTurnTheDestinationIsNext() {
        val state = onRoute(TripNavigator(route()).update(Fix(LatLon(15.42, 73.825))))
        assertEquals(turn, state.current)
        assertEquals(arrive, state.next)
        assertEquals(536.0, state.distanceToNextM, 10.0)
    }

    @Test
    fun oneBadFixIsIgnoredButTwoInARowAreOffRoute() {
        val navigator = TripNavigator(route())
        navigator.update(Fix(p0))
        val away = east(p1, 200.0)
        assertTrue(navigator.update(Fix(away)) is NavState.OnRoute)
        val off = navigator.update(Fix(away))
        assertTrue(off is NavState.OffRoute)
        assertEquals(200.0, (off as NavState.OffRoute).distanceFromRouteM, 10.0)
        // Back on the road: following again.
        assertTrue(navigator.update(Fix(p1)) is NavState.OnRoute)
    }

    @Test
    fun aVagueFixGetsMoreRoom() {
        val navigator = TripNavigator(route())
        val sixtyOff = east(p1, 60.0)
        navigator.update(Fix(sixtyOff, accuracyM = 50f))
        assertTrue(navigator.update(Fix(sixtyOff, accuracyM = 50f)) is NavState.OnRoute)
        navigator.update(Fix(sixtyOff, accuracyM = 5f))
        assertTrue(navigator.update(Fix(sixtyOff, accuracyM = 5f)) is NavState.OffRoute)
    }

    @Test
    fun aJumpFarAheadIsFoundAlongTheWholeRoute() {
        val navigator = TripNavigator(route(), NavConfig(searchAhead = 0))
        navigator.update(Fix(p0))
        assertEquals(turn, onRoute(navigator.update(Fix(LatLon(15.42, 73.825)))).current)
    }

    @Test
    fun arrivingNearTheEndStaysArrived() {
        val navigator = TripNavigator(route())
        assertEquals(NavState.Arrived, navigator.update(Fix(LatLon(15.42, 73.8299))))
        assertEquals(NavState.Arrived, navigator.update(Fix(p0)))
    }

    @Test
    fun announcementsComeOnceEachAtSpeedScaledDistances() {
        val r = route()
        val navigator = TripNavigator(r)
        val announcer = Announcer()
        fun at(distanceBeforeTurnM: Double): Announcement? {
            val position = LatLon(p2.latitude - distanceBeforeTurnM / 111_320.0, 73.82)
            return announcer.next(r, onRoute(navigator.update(Fix(position, speedMps = 15f))))
        }
        assertEquals(Announcer.Kind.Start, at(2000.0)?.kind)
        assertNull(at(1500.0))
        // At 15 m/s: alert within 450 m, instruction within 120 m.
        val alert = at(440.0)
        assertEquals(Announcer.Kind.Alert, alert?.kind)
        assertEquals("Turn right onto B Road.", alert?.text)
        assertEquals(440.0, alert!!.distanceM, 15.0)
        assertNull(at(300.0))
        assertEquals(Announcer.Kind.Pre, at(110.0)?.kind)
        assertNull(at(50.0))
    }

    @Test
    fun parsesValhallasJsonAcrossLegs() {
        val leg1 = encode(listOf(p0, p1, p2))
        val leg2 = encode(listOf(p2, p3))
        val json = """
            {"trip":{"legs":[
              {"shape":"$leg1","maneuvers":[
                {"type":1,"instruction":"Drive north.","verbal_pre_transition_instruction":"Drive north.","street_names":["A Road"],"length":2.224,"time":200,"begin_shape_index":0,"end_shape_index":2},
                {"type":10,"instruction":"Turn right.","verbal_transition_alert_instruction":"Turn right.","length":0,"time":0,"begin_shape_index":2,"end_shape_index":2}]},
              {"shape":"$leg2","maneuvers":[
                {"type":4,"instruction":"You have arrived.","length":1.072,"time":100,"begin_shape_index":1,"end_shape_index":1}]}
            ],"summary":{"length":3.296,"time":300}}}
        """.trimIndent()
        val route = NavRoute.fromValhalla(json)
        assertEquals(4, route.shape.size)
        assertEquals(listOf(0, 2, 3), route.maneuvers.map { it.beginIndex })
        assertEquals(listOf("A Road"), route.maneuvers[0].streetNames)
        assertNull(route.maneuvers[1].verbalPre)
        assertEquals("Turn right.", route.maneuvers[1].verbalAlert)
        assertTrue(route.maneuvers[2].isDestination)
    }

    @Test
    fun malformedResponsesAreRoutingErrors() {
        assertThrows(RoutingException::class.java) { NavRoute.fromValhalla("""{"error":"No path could be found"}""") }
        assertThrows(RoutingException::class.java) { NavRoute.fromValhalla("""{"trip":{}}""") }
        assertThrows(RoutingException::class.java) { NavRoute.fromValhalla("{not json") }
        assertThrows(RoutingException::class.java) {
            NavRoute.fromValhalla(
                """{"trip":{"legs":[{"shape":"${encode(
                    listOf(p0, p1)
                )}","maneuvers":[{"type":1,"begin_shape_index":9}]}]}}"""
            )
        }
    }

    /** Polyline6 for [points], escaped for use inside a JSON string (the alphabet includes '\'). */
    private fun encode(points: List<LatLon>): String = polyline(points).replace("\\", "\\\\")

    private fun polyline(points: List<LatLon>): String = buildString {
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = Math.round(p.latitude * 1e6)
            val lon = Math.round(p.longitude * 1e6)
            appendValue(lat - lastLat)
            appendValue(lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
    }

    private fun StringBuilder.appendValue(value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        append((v + 63).toInt().toChar())
    }
}

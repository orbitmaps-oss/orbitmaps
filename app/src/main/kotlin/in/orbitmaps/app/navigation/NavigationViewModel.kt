// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.places.PlaceItem
import `in`.orbitmaps.app.routing.Costing
import `in`.orbitmaps.app.routing.RouteSummary
import `in`.orbitmaps.core.model.LatLon
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plans a route to a place and drives it: the route preview, the glance screen, the voice, the
 * simulated drive of debug builds. Positions and routes live in memory only, are never logged, and
 * are dropped when the trip ends.
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {
    /** Whether on-demand map data may be fetched now (switch on and online); set by the activity. */
    @Volatile var fetchAllowed: Boolean = false

    private val planner = RoutePlanner(application) { fetchAllowed }
    private var voice: Voice? = null

    private val _plan = MutableStateFlow<PlanUi>(PlanUi.Idle)
    val plan: StateFlow<PlanUi> = _plan.asStateFlow()

    private val _drive = MutableStateFlow<DriveUi?>(null)
    val drive: StateFlow<DriveUi?> = _drive.asStateFlow()

    /** The simulated position, while a drive is simulated; shown on the map in place of the real one. */
    private val _simulatedLocation = MutableStateFlow<Location?>(null)
    val simulatedLocation: StateFlow<Location?> = _simulatedLocation.asStateFlow()

    private var planJob: Job? = null
    private var driveJob: Job? = null
    private var fixes = Channel<Fix>(Channel.CONFLATED)
    private var simulating = false

    /** Plans a route from [from] to [place]; the latest request wins. */
    fun plan(from: LatLon, place: PlaceItem, costing: Costing) {
        planJob?.cancel()
        val to = place.location
        if (to == null) {
            _plan.value = PlanUi.Failed(PlanFailure.NoDestination)
            return
        }
        _plan.value = PlanUi.Planning
        planJob = viewModelScope.launch {
            val language = Locale.getDefault().toLanguageTag()
            _plan.value = when (val outcome = planner.plan(from, to, costing, language)) {
                is PlanOutcome.Ok -> ready(outcome, to, costing)
                PlanOutcome.NeedsData -> PlanUi.Failed(PlanFailure.NeedsData)
                PlanOutcome.NoRoute -> PlanUi.Failed(PlanFailure.NoRoute)
            }
        }
    }

    private fun ready(outcome: PlanOutcome.Ok, to: LatLon, costing: Costing): PlanUi = try {
        PlanUi.Ready(
            route = NavRoute.fromValhalla(outcome.summary.json),
            summary = outcome.summary,
            destination = to,
            costing = costing,
            fromRegion = outcome.fromRegion,
            tilesIncomplete = outcome.tilesIncomplete
        )
    } catch (e: Exception) {
        // A reply we can't follow (no maneuvers, a broken line) is no usable route.
        PlanUi.Failed(PlanFailure.NoRoute)
    }

    /** Debug builds: a built-in route, for trying the driving screens without map data. */
    fun useSampleRoute() {
        planJob?.cancel()
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().assets.open(SAMPLE_ROUTE_ASSET).bufferedReader().use { it.readText() }
                } catch (e: IOException) {
                    null
                }
            }
            _plan.value = try {
                val route = NavRoute.fromValhalla(checkNotNull(json))
                PlanUi.Ready(
                    route = route,
                    summary = RouteSummary(
                        route.lengthM / 1000.0,
                        route.timeSeconds,
                        route.maneuvers.size,
                        route.shape
                    ),
                    destination = route.shape.last(),
                    costing = Costing.Car,
                    fromRegion = true,
                    tilesIncomplete = false,
                    sample = true
                )
            } catch (e: Exception) {
                PlanUi.Failed(PlanFailure.NoRoute)
            }
        }
    }

    /** Drops the planned route when the preview closes, unless a trip is being driven. */
    fun clearPlan() {
        if (_drive.value != null) return
        planJob?.cancel()
        _plan.value = PlanUi.Idle
    }

    /** Starts driving the planned route, with GPS fixes or (debug) a simulated drive. */
    fun start(simulate: Boolean) {
        val ready = _plan.value as? PlanUi.Ready ?: return
        end()
        val useSimulation = simulate || ready.sample
        simulating = useSimulation
        fixes = Channel(Channel.CONFLATED)
        val controller = DriveController(
            initial = ready.route,
            reroute = { from -> reroute(from, ready) },
            speak = ::say,
            simulated = useSimulation
        )
        _plan.value = PlanUi.Idle
        // Keep the road tiles along the route, so the trip and rerouting survive losing signal.
        if (!ready.fromRegion && !ready.sample) {
            viewModelScope.launch { planner.saveCorridor(ready.summary) }
        }
        driveJob = viewModelScope.launch {
            launch { controller.state.collect { _drive.value = it } }
            if (useSimulation) launch { simulate(ready.route) }
            for (fix in fixes) controller.onFix(fix)
        }
    }

    /** A real GPS fix: followed only while a real (not simulated) trip is being driven. */
    fun onLocation(location: Location) {
        if (_drive.value == null || simulating) return
        val position = LatLon.orNull(location.latitude, location.longitude) ?: return
        fixes.trySend(
            Fix(
                position = position,
                accuracyM = if (location.hasAccuracy()) location.accuracy else DEFAULT_ACCURACY_M,
                speedMps = if (location.hasSpeed()) location.speed else null
            )
        )
    }

    fun end() {
        driveJob?.cancel()
        driveJob = null
        fixes.close()
        _drive.value = null
        _simulatedLocation.value = null
        simulating = false
        voice?.stop()
    }

    private suspend fun reroute(from: LatLon, ready: PlanUi.Ready): NavRoute? {
        if (ready.sample) return null
        val language = Locale.getDefault().toLanguageTag()
        val outcome = planner.plan(from, ready.destination, ready.costing, language)
        return (outcome as? PlanOutcome.Ok)?.let {
            try {
                NavRoute.fromValhalla(it.summary.json)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun simulate(route: NavRoute) {
        for (step in SimulatedDrive.along(route, stepM = SIM_SPEED_MPS, speedMps = SIM_SPEED_MPS.toFloat())) {
            _simulatedLocation.value = Location("simulated").apply {
                latitude = step.fix.position.latitude
                longitude = step.fix.position.longitude
                bearing = step.bearingDeg
                speed = step.fix.speedMps ?: 0f
                accuracy = step.fix.accuracyM
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            fixes.send(step.fix)
            delay(SIM_INTERVAL_MS)
        }
    }

    private fun say(announcement: Announcement) {
        val app = getApplication<Application>()
        val text = if (announcement.kind == Announcer.Kind.Alert) {
            app.getString(R.string.nav_alert_in, spokenDistance(announcement.distanceM), announcement.text)
        } else {
            announcement.text
        }
        val engine = voice ?: Voice(app).also { voice = it }
        engine.speak(text)
    }

    private fun spokenDistance(metres: Double): String {
        val resources = getApplication<Application>().resources
        return if (NavFormat.isKm(metres)) {
            resources.getString(R.string.distance_km_spoken, NavFormat.kmTenths(metres))
        } else {
            val rounded = NavFormat.roundedMetres(metres)
            resources.getQuantityString(R.plurals.distance_metres_spoken, rounded, rounded)
        }
    }

    override fun onCleared() {
        end()
        voice?.shutdown()
        planner.close()
    }

    private companion object {
        const val SAMPLE_ROUTE_ASSET = "sim/sample-route.json"
        const val DEFAULT_ACCURACY_M = 25f
        const val SIM_SPEED_MPS = 25.0
        const val SIM_INTERVAL_MS = 1_000L
    }
}

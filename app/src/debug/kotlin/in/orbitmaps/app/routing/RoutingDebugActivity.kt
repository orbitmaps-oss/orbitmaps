// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.ui.theme.OrbitTheme
import `in`.orbitmaps.core.model.LatLon
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only benchmark for the month-1 routing gate (plan step 3): engine start time, route time and
 * memory on a real phone, from the bundled sample tiles. Results are shown on screen only.
 */
class RoutingDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OrbitTheme { RoutingDebugScreen() } }
    }
}

private data class SampleRoute(@StringRes val name: Int, val from: LatLon, val to: LatLon)

/** Fixed points inside the sample box (73.734,15.401 to 73.921,15.581); not anyone's location. */
private val SampleRoutes = listOf(
    SampleRoute(R.string.routing_debug_route_city, LatLon(15.4989, 73.8278), LatLon(15.4818, 73.8079)),
    SampleRoute(R.string.routing_debug_route_bridge, LatLon(15.4989, 73.8278), LatLon(15.5395, 73.8135)),
    SampleRoute(R.string.routing_debug_route_across, LatLon(15.4567, 73.8037), LatLon(15.5006, 73.8576)),
    SampleRoute(R.string.routing_debug_route_long, LatLon(15.4818, 73.8079), LatLon(15.5034, 73.9120))
)

private const val TIMED_RUNS = 5
private const val MB = 1024 * 1024

private sealed interface RouteResult {
    val name: Int

    data class Ok(override val name: Int, val summary: RouteSummary, val firstMs: Long, val medianMs: Long) :
        RouteResult

    data class Error(override val name: Int, val message: String) : RouteResult
}

private sealed interface BenchState {
    data object Idle : BenchState

    data object Running : BenchState

    data object NotBundled : BenchState

    data class Failed(val message: String) : BenchState

    data class Done(
        val setupMs: Long,
        val engineMs: Long,
        val tilesMb: Double,
        val pssBeforeMb: Int,
        val pssAfterMb: Int,
        val nativeGrowthMb: Int,
        val routes: List<RouteResult>
    ) : BenchState
}

@Composable
private fun RoutingDebugScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BenchState>(BenchState.Idle) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.routing_debug_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.routing_debug_intro), style = MaterialTheme.typography.bodyMedium)
            Button(
                enabled = state != BenchState.Running,
                onClick = {
                    state = BenchState.Running
                    scope.launch { state = runBenchmark(context) }
                }
            ) {
                Text(
                    stringResource(
                        if (state ==
                            BenchState.Running
                        ) {
                            R.string.routing_debug_running
                        } else {
                            R.string.routing_debug_run
                        }
                    )
                )
            }
            Results(state)
        }
    }
}

@Composable
private fun Results(state: BenchState) {
    when (state) {
        BenchState.Idle, BenchState.Running -> Unit
        BenchState.NotBundled -> Text(stringResource(R.string.routing_debug_not_bundled))
        is BenchState.Failed -> Text(stringResource(R.string.routing_debug_failed, state.message))
        is BenchState.Done -> {
            Text(stringResource(R.string.routing_debug_tiles, state.tilesMb))
            Text(stringResource(R.string.routing_debug_setup, state.setupMs))
            Text(stringResource(R.string.routing_debug_engine, state.engineMs))
            Text(
                stringResource(R.string.routing_debug_memory, state.pssBeforeMb, state.pssAfterMb, state.nativeGrowthMb)
            )
            state.routes.forEach { result ->
                val name = stringResource(result.name)
                when (result) {
                    is RouteResult.Ok -> Text(
                        stringResource(
                            R.string.routing_debug_route,
                            name,
                            result.summary.lengthKm,
                            result.summary.timeSeconds / 60,
                            result.summary.maneuvers,
                            result.medianMs,
                            result.firstMs
                        )
                    )
                    is RouteResult.Error -> Text(
                        stringResource(R.string.routing_debug_route_error, name, result.message)
                    )
                }
            }
        }
    }
}

private suspend fun runBenchmark(context: Context): BenchState = withContext(Dispatchers.Default) {
    val setupStart = SystemClock.elapsedRealtime()
    val setup = setUpSampleRouting(context)
    val setupMs = SystemClock.elapsedRealtime() - setupStart
    val config = when (setup) {
        RoutingSetup.NotBundled -> return@withContext BenchState.NotBundled
        RoutingSetup.Failed -> return@withContext BenchState.Failed("install")
        is RoutingSetup.Ready -> setup.configFile
    }
    val pssBefore = pssMb()
    val nativeBefore = Debug.getNativeHeapAllocatedSize()
    val engineStart = SystemClock.elapsedRealtime()
    val router = try {
        OfflineRouter(config)
    } catch (e: RuntimeException) {
        return@withContext BenchState.Failed(e.message ?: e.javaClass.simpleName)
    }
    val engineMs = SystemClock.elapsedRealtime() - engineStart
    val routes = router.use { SampleRoutes.map { timeRoute(it, router) } }
    BenchState.Done(
        setupMs = setupMs,
        engineMs = engineMs,
        tilesMb = File(context.filesDir, SampleRouting.TILES_PATH).length().toDouble() / MB,
        pssBeforeMb = pssBefore,
        pssAfterMb = pssMb(),
        nativeGrowthMb = ((Debug.getNativeHeapAllocatedSize() - nativeBefore) / MB).toInt(),
        routes = routes
    )
}

/** One cold route, then [TIMED_RUNS] warm ones; reports the first and the median. */
private fun timeRoute(sample: SampleRoute, router: OfflineRouter): RouteResult = try {
    val times = mutableListOf<Long>()
    var summary: RouteSummary? = null
    repeat(TIMED_RUNS + 1) {
        val start = SystemClock.elapsedRealtime()
        summary = router.route(sample.from, sample.to)
        times += SystemClock.elapsedRealtime() - start
    }
    val warm = times.drop(1).sorted()
    RouteResult.Ok(sample.name, checkNotNull(summary), firstMs = times.first(), medianMs = warm[warm.size / 2])
} catch (e: RoutingException) {
    RouteResult.Error(sample.name, e.message ?: "error")
}

private fun pssMb(): Int {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)
    return info.totalPss / 1024
}

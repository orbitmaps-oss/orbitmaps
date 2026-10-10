// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.glance

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.navigation.DriveUi
import `in`.orbitmaps.app.navigation.Fix
import `in`.orbitmaps.app.navigation.GlanceModel
import `in`.orbitmaps.app.navigation.GlanceStatus
import `in`.orbitmaps.app.navigation.Maneuver
import `in`.orbitmaps.app.navigation.NavFormat
import `in`.orbitmaps.app.navigation.NavRoute
import `in`.orbitmaps.app.navigation.NavState
import `in`.orbitmaps.app.ui.components.MapPlaceholder
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.theme.OrbitColors
import `in`.orbitmaps.app.ui.theme.OrbitTheme
import `in`.orbitmaps.core.model.LatLon
import java.util.Date

private val EdgePadding = 12.dp
private val SpeedPanelHeight = 40.dp

/** Distance from the bottom card's bottom edge to the speed panel, with the card closed or open. */
private fun speedPanelLift(actionsOpen: Boolean) = if (actionsOpen) 208.dp else 96.dp

/**
 * How much of the bottom of the screen glance mode covers, above the system bars. The map draws its
 * attribution above this, so the speed panel never hides "© OpenStreetMap contributors".
 */
fun glanceBottomInset(actionsOpen: Boolean): Dp = EdgePadding + speedPanelLift(actionsOpen) + SpeedPanelHeight

/**
 * Glance mode while navigating (windows 7 to 9): the next turn with its distance, the current speed,
 * at most one status, and a bottom card with time left and arrival that opens Report, Add stop, Share
 * ETA and End. The screen stays on only while this is shown. Night (window 8) is the same layout in
 * the dark theme.
 */
@Composable
fun GlanceMode(
    drive: DriveUi,
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onEnd: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    KeepScreenOn()
    val model = GlanceModel.from(drive, System.currentTimeMillis())
    val arrived = model.status == GlanceStatus.Arrived
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(EdgePadding)) {
        Column(modifier = Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TurnCard(model)
            StatusPill(model, Modifier.align(Alignment.CenterHorizontally))
        }
        model.speedKmh?.let { speed ->
            SpeedPanel(speed, Modifier.align(Alignment.BottomStart).padding(bottom = speedPanelLift(actionsOpen)))
        }
        BottomCard(
            model = model,
            actionsOpen = actionsOpen || arrived,
            canToggle = !arrived,
            onToggleActions = onToggleActions,
            onEnd = onEnd,
            onNotYet = onNotYet,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun TurnCard(model: GlanceModel) {
    Surface(color = OrbitColors.DarkGreen, contentColor = Color.White, shape = RoundedCornerShape(20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(48.dp).rotate(model.arrowRotation)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                val distance = model.distanceM
                if (distance != null) {
                    Text(
                        text = if (NavFormat.isKm(distance)) {
                            stringResource(R.string.glance_distance_km, NavFormat.kmTenths(distance))
                        } else {
                            stringResource(R.string.glance_distance_m, NavFormat.roundedMetres(distance))
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                val text = if (model.status == GlanceStatus.Arrived) {
                    stringResource(R.string.nav_arrived)
                } else {
                    model.instruction
                }
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The single status slot: glance mode never shows more than one alert at a time (plan step 12.4). */
@Composable
private fun StatusPill(model: GlanceModel, modifier: Modifier = Modifier) {
    val text = when {
        model.status == GlanceStatus.Waiting -> stringResource(R.string.nav_status_waiting)
        model.status == GlanceStatus.Rerouting -> stringResource(R.string.nav_status_rerouting)
        model.status == GlanceStatus.OffRoute -> stringResource(R.string.nav_status_off_route)
        model.simulated && model.status != GlanceStatus.Arrived -> stringResource(R.string.nav_status_simulated)
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(OrbitColors.Amber, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.Black, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SpeedPanel(speedKmh: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.glance_speed_now_description, speedKmh)
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Text(
            stringResource(R.string.glance_speed_kmh, speedKmh),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.heightIn(min = SpeedPanelHeight).padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BottomCard(
    model: GlanceModel,
    actionsOpen: Boolean,
    canToggle: Boolean,
    onToggleActions: () -> Unit,
    onEnd: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val arrival = remember(model.arrivalAtMs) { DateFormat.getTimeFormat(context).format(Date(model.arrivalAtMs)) }
    Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onToggleActions, enabled = canToggle, color = Color.Transparent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        if (model.status == GlanceStatus.Arrived) {
                            Text(stringResource(R.string.nav_arrived), style = MaterialTheme.typography.titleLarge)
                        } else {
                            Text(
                                stringResource(R.string.glance_eta, model.remainingMinutes, model.remainingKm),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                stringResource(R.string.glance_arrive_at, arrival),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (canToggle) {
                        Text(
                            stringResource(if (actionsOpen) R.string.glance_less else R.string.glance_more),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            if (actionsOpen) {
                if (model.status != GlanceStatus.Arrived) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNotYet, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.glance_report))
                        }
                        OutlinedButton(onClick = onNotYet, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.glance_add_stop))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNotYet, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.glance_share_eta))
                        }
                        EndButton(onEnd, Modifier.weight(1f))
                    }
                } else {
                    EndButton(onEnd, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EndButton(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onEnd,
        colors = ButtonDefaults.buttonColors(containerColor = OrbitColors.SpeedRed),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(stringResource(R.string.glance_end), color = Color.White)
    }
}

/** A short route for previews only: 700 m north, then 500 m east. */
private fun previewRoute(): NavRoute {
    val a = LatLon(15.49, 73.82)
    val b = LatLon(15.4963, 73.82)
    val c = LatLon(15.4963, 73.8246)
    fun m(type: Int, text: String, begin: Int, km: Double, s: Double) =
        Maneuver(type, text, text, text, emptyList(), km, s, begin)
    return NavRoute(
        listOf(a, b, c),
        listOf(
            m(1, "Drive north.", 0, 0.7, 60.0),
            m(10, "Turn right.", 1, 0.5, 40.0),
            m(4, "You have arrived.", 2, 0.0, 0.0)
        )
    )
}

@ThemePreviews
@Composable
private fun GlanceModePreview() {
    OrbitTheme {
        Box {
            MapPlaceholder()
            val route = previewRoute()
            val nav = NavState.OnRoute(
                current = route.maneuvers[0],
                next = route.maneuvers[1],
                nextIndex = 1,
                distanceToNextM = 320.0,
                remainingM = 820.0,
                remainingSeconds = 80.0,
                snapped = route.shape[0],
                speedMps = 12f
            )
            GlanceMode(
                DriveUi(route, nav, fix = Fix(route.shape[0], speedMps = 12f)),
                actionsOpen = false,
                onToggleActions = {},
                onEnd = {},
                onNotYet = {}
            )
        }
    }
}

@ThemePreviews
@Composable
private fun GlanceActionsPreview() {
    OrbitTheme {
        Box {
            MapPlaceholder()
            val route = previewRoute()
            GlanceMode(
                DriveUi(route, rerouting = true),
                actionsOpen = true,
                onToggleActions = {},
                onEnd = {},
                onNotYet = {}
            )
        }
    }
}

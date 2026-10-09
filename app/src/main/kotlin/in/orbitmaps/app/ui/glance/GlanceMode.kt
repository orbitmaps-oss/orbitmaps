// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.glance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import `in`.orbitmaps.app.ui.components.MapPlaceholder
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.theme.OrbitColors
import `in`.orbitmaps.app.ui.theme.OrbitTheme

/** Sample values until Ferrostar provides the real navigation state. */
private const val SAMPLE_DISTANCE_M = 300
private const val SAMPLE_SPEED_LIMIT = 50
private const val SAMPLE_SPEED = 46
private const val SAMPLE_MINUTES = 18
private const val SAMPLE_KM = 7.2

private val EdgePadding = 12.dp
private val SpeedSignSize = 64.dp

/** Distance from the bottom card's bottom edge to the speed panel, with the card closed or open. */
private fun speedPanelLift(actionsOpen: Boolean) = if (actionsOpen) 208.dp else 96.dp

/**
 * How much of the bottom of the screen glance mode covers, above the system bars. The map draws its
 * attribution above this, so the speed panel never hides "© OpenStreetMap contributors".
 */
fun glanceBottomInset(actionsOpen: Boolean): Dp = EdgePadding + speedPanelLift(actionsOpen) + SpeedSignSize

/**
 * Glance mode while navigating (windows 7 to 9): next turn, speed limit beside the current speed, at
 * most one alert, and a bottom card that opens Report, Add stop, Share ETA and End. The screen stays on
 * only while this is shown. Night (window 8) is the same layout in the dark theme.
 */
@Composable
fun GlanceMode(
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onEnd: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    KeepScreenOn()
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(EdgePadding)) {
        Column(modifier = Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TurnCard()
            AlertPill(Modifier.align(Alignment.CenterHorizontally))
        }
        SpeedPanel(Modifier.align(Alignment.BottomStart).padding(bottom = speedPanelLift(actionsOpen)))
        BottomCard(
            actionsOpen = actionsOpen,
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
private fun TurnCard() {
    Surface(color = OrbitColors.DarkGreen, contentColor = Color.White, shape = RoundedCornerShape(20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.glance_distance_m, SAMPLE_DISTANCE_M),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.sample_turn_instruction), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The single alert slot: glance mode never shows more than one alert at a time (plan step 12.4). */
@Composable
private fun AlertPill(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(OrbitColors.Amber, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.sample_alert_camera),
            color = Color.Black,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SpeedPanel(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.glance_speed_description, SAMPLE_SPEED, SAMPLE_SPEED_LIMIT)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SpeedSignSize)
                .background(Color.White, CircleShape)
                .border(6.dp, OrbitColors.SpeedRed, CircleShape)
        ) {
            Text(
                SAMPLE_SPEED_LIMIT.toString(),
                color = Color.Black,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp) {
            Text(
                stringResource(R.string.glance_speed_kmh, SAMPLE_SPEED),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BottomCard(
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onEnd: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onToggleActions, color = Color.Transparent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.glance_eta, SAMPLE_MINUTES, SAMPLE_KM),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.sample_arrival),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(if (actionsOpen) R.string.glance_less else R.string.glance_more),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            if (actionsOpen) {
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
                    Button(
                        onClick = onEnd,
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitColors.SpeedRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.glance_end), color = Color.White)
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun GlanceModePreview() {
    OrbitTheme {
        Box {
            MapPlaceholder()
            GlanceMode(actionsOpen = false, onToggleActions = {}, onEnd = {}, onNotYet = {})
        }
    }
}

@ThemePreviews
@Composable
private fun GlanceActionsPreview() {
    OrbitTheme {
        Box {
            MapPlaceholder()
            GlanceMode(actionsOpen = true, onToggleActions = {}, onEnd = {}, onNotYet = {})
        }
    }
}

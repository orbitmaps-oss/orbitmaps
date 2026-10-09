// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.ui.components.ChipRow
import `in`.orbitmaps.app.ui.components.InitialsBadge
import `in`.orbitmaps.app.ui.components.ListRow
import `in`.orbitmaps.app.ui.components.SampleDataNote
import `in`.orbitmaps.app.ui.components.SectionTitle
import `in`.orbitmaps.app.ui.components.Tag
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.components.ToggleRow
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.app.ui.shell.MapTheme
import `in`.orbitmaps.app.ui.theme.OrbitColors
import `in`.orbitmaps.app.ui.theme.OrbitTheme

/** Hazards that can be reported from the contribute sheet and while driving. */
val HazardTypes = listOf(
    R.string.report_pothole,
    R.string.report_closed_road,
    R.string.report_flooding,
    R.string.report_landslide,
    R.string.report_speed_check,
    R.string.report_fuel_price
)

/** Window 10: the single + button's sheet for every kind of contribution. */
@Composable
fun ContributeSheet(onAddPlace: () -> Unit, onNotYet: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.contribute_title), style = MaterialTheme.typography.titleLarge)
        SectionTitle(stringResource(R.string.contribute_report_here))
        HazardTypes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { label ->
                    OutlinedButton(onClick = onNotYet, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = OrbitColors.Amber)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label))
                    }
                }
            }
        }
        SectionTitle(stringResource(R.string.contribute_improve))
        ListRow(
            Icons.Filled.AddCircle,
            stringResource(R.string.contribute_add_place),
            subtitle = stringResource(R.string.contribute_add_place_hint),
            onClick = onAddPlace
        )
        ListRow(
            Icons.Filled.Build,
            stringResource(R.string.contribute_fix_map),
            subtitle = stringResource(R.string.contribute_fix_map_hint),
            onClick = onNotYet
        )
    }
}

private val ThemeLabels = mapOf(
    MapTheme.Day to R.string.theme_day,
    MapTheme.Night to R.string.theme_night,
    MapTheme.Rider to R.string.theme_rider,
    MapTheme.Trek to R.string.theme_trek
)

private val Overlays = listOf(
    R.string.overlay_hazards,
    R.string.overlay_cameras,
    R.string.overlay_school_zones,
    R.string.overlay_ev,
    R.string.overlay_trails
)

/** Window 12: map theme and overlay switches. */
@Composable
fun LayersSheet(theme: MapTheme, onThemeChange: (MapTheme) -> Unit, modifier: Modifier = Modifier) {
    val enabled = remember { mutableStateMapOf(R.string.overlay_hazards to true, R.string.overlay_cameras to true) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.layers_title), style = MaterialTheme.typography.titleLarge)
        SectionTitle(stringResource(R.string.layers_theme))
        ChipRow {
            MapTheme.entries.forEach { option ->
                FilterChip(
                    selected = option == theme,
                    onClick = { onThemeChange(option) },
                    label = { Text(stringResource(ThemeLabels.getValue(option))) }
                )
            }
        }
        Text(
            stringResource(R.string.layers_theme_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionTitle(stringResource(R.string.layers_overlays))
        Overlays.forEach { label ->
            ToggleRow(
                title = stringResource(label),
                checked = enabled[label] == true,
                onCheckedChange = { enabled[label] = it }
            )
        }
    }
}

private val QuickStatuses = listOf(
    R.string.status_stop,
    R.string.status_fuel_break,
    R.string.status_wait,
    R.string.status_all_ok,
    R.string.status_need_help
)

/** Window 13: group members on the map, radio and region status, quick statuses, chat and invite. */
@Composable
fun GroupTripSheet(onOpenChat: () -> Unit, onNotYet: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.group_title), style = MaterialTheme.typography.titleLarge)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.group_radio_disconnected), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.group_radio_hint), style = MaterialTheme.typography.bodySmall)
                }
                Tag(stringResource(R.string.group_region_unknown), OrbitColors.Amber)
            }
        }
        SectionTitle(stringResource(R.string.group_members))
        SampleData.members.forEach { member ->
            val name = stringResource(member.name)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
                InitialsBadge(name)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pluralStringResource(
                            R.plurals.group_member_seen,
                            member.minutesAgo,
                            member.distanceKm,
                            member.minutesAgo
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SectionTitle(stringResource(R.string.group_quick_status))
        ChipRow {
            QuickStatuses.forEach { label ->
                FilterChip(selected = false, onClick = onNotYet, label = { Text(stringResource(label)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.group_chat)) }
            OutlinedButton(onClick = onNotYet, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.group_invite))
            }
        }
        Text(
            stringResource(R.string.group_honest_wording),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SampleDataNote()
    }
}

@ThemePreviews
@Composable
private fun ContributePreview() {
    OrbitTheme { Surface { ContributeSheet(onAddPlace = {}, onNotYet = {}, modifier = Modifier.padding(16.dp)) } }
}

@ThemePreviews
@Composable
private fun LayersPreview() {
    OrbitTheme { Surface { LayersSheet(MapTheme.Day, onThemeChange = {}, modifier = Modifier.padding(16.dp)) } }
}

@ThemePreviews
@Composable
private fun GroupTripPreview() {
    OrbitTheme { Surface { GroupTripSheet(onOpenChat = {}, onNotYet = {}, modifier = Modifier.padding(16.dp)) } }
}

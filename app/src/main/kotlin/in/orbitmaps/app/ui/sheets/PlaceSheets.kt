// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.sheets

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.places.PlaceItem
import `in`.orbitmaps.app.places.SamplePlaces
import `in`.orbitmaps.app.ui.components.ChipRow
import `in`.orbitmaps.app.ui.components.ListRow
import `in`.orbitmaps.app.ui.components.SampleDataNote
import `in`.orbitmaps.app.ui.components.StatusTag
import `in`.orbitmaps.app.ui.components.Tag
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.theme.OrbitColors
import `in`.orbitmaps.app.ui.theme.OrbitTheme

/** Loads a place for a sheet; null while loading or if the place is unknown. */
@Composable
private fun rememberPlace(placeId: String, loadPlace: suspend (String) -> PlaceItem?): PlaceItem? {
    val place by produceState<PlaceItem?>(null, placeId) { value = loadPlace(placeId) }
    return place
}

/**
 * Window 5: name, category and distance, actions and the data credit. Sample places also show the
 * planned community parts (status, rating from visits, hours, phone, tags).
 */
@Composable
fun PlaceDetailsSheet(
    placeId: String,
    loadPlace: suspend (String) -> PlaceItem?,
    onGo: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val place = rememberPlace(placeId, loadPlace) ?: return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                place.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            place.status?.let { StatusTag(it, confirmations = place.confirmations) }
        }
        Text(
            placeSubtitle(place),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGo) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_go))
            }
            OutlinedButton(onClick = onNotYet) { Text(stringResource(R.string.action_save)) }
            OutlinedButton(onClick = onNotYet) { Text(stringResource(R.string.action_confirm)) }
        }
        if (place.isSample) SampleCommunityDetails(place, onNotYet)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNotYet) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_share))
            }
            OutlinedButton(onClick = onNotYet) {
                Icon(Icons.Filled.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_rate))
            }
        }
        Text(
            stringResource(R.string.place_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (place.isSample) SampleDataNote()
    }
}

/** The community parts of a place sheet, shown with sample data until the places API exists. */
@Composable
private fun SampleCommunityDetails(place: PlaceItem, onNotYet: () -> Unit) {
    val rating = place.rating
    ListRow(
        icon = Icons.Filled.Star,
        title = if (rating != null) {
            stringResource(R.string.place_rating_from_visits, rating)
        } else {
            stringResource(R.string.place_rating_none)
        },
        subtitle = stringResource(R.string.place_rating_explainer),
        onClick = onNotYet
    )
    ListRow(
        Icons.Filled.DateRange,
        stringResource(R.string.sample_hours),
        subtitle = stringResource(R.string.place_hours)
    )
    ListRow(
        Icons.Filled.Phone,
        stringResource(R.string.sample_phone),
        subtitle = stringResource(R.string.place_phone)
    )
    ChipRow {
        listOf(R.string.sample_tag_wifi, R.string.sample_tag_parking, R.string.sample_tag_wheelchair).forEach {
            AssistChip(onClick = {}, label = { Text(stringResource(it)) })
        }
    }
}

private data class RouteOption(
    @StringRes val label: Int,
    val minutes: Int,
    val km: Double,
    val cameras: Int,
    val hazards: Int
)

private val SampleRoutes = listOf(
    RouteOption(R.string.route_fastest, 18, 7.2, cameras = 1, hazards = 2),
    RouteOption(R.string.route_shortest, 22, 6.4, cameras = 0, hazards = 1)
)

/** Window 6: travel mode, route options with the alerts on each, and Start. */
@Composable
fun RoutePreviewSheet(
    placeId: String,
    loadPlace: suspend (String) -> PlaceItem?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val place = rememberPlace(placeId, loadPlace) ?: return
    val modes = listOf(R.string.mode_car, R.string.mode_bike, R.string.mode_walk)
    var mode by remember { mutableIntStateOf(0) }
    var route by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.route_to, place.name),
            style = MaterialTheme.typography.titleLarge
        )
        ChipRow {
            modes.forEachIndexed { index, label ->
                FilterChip(selected = index == mode, onClick = {
                    mode = index
                }, label = { Text(stringResource(label)) })
            }
        }
        SampleRoutes.forEachIndexed { index, option ->
            val chosen = index == route
            Card(
                onClick = { route = index },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = chosen },
                border = if (chosen) BorderStroke(2.dp, OrbitColors.RouteGreen) else null
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.route_summary, stringResource(option.label), option.minutes, option.km),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (option.cameras > 0) {
                            Tag(
                                pluralStringResource(R.plurals.route_alert_cameras, option.cameras, option.cameras),
                                OrbitColors.SpeedRed
                            )
                        }
                        if (option.hazards > 0) {
                            Tag(
                                pluralStringResource(R.plurals.route_alert_hazards, option.hazards, option.hazards),
                                OrbitColors.Amber
                            )
                        }
                    }
                }
            }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_start))
        }
        SampleDataNote()
    }
}

@ThemePreviews
@Composable
private fun PlaceDetailsPreview() {
    val samples = SamplePlaces(LocalResources.current::getString)
    OrbitTheme {
        Surface {
            PlaceDetailsSheet("cafe", samples::place, onGo = {}, onNotYet = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

@ThemePreviews
@Composable
private fun RoutePreviewPreview() {
    val samples = SamplePlaces(LocalResources.current::getString)
    OrbitTheme {
        Surface { RoutePreviewSheet("cafe", samples::place, onStart = {}, modifier = Modifier.padding(16.dp)) }
    }
}

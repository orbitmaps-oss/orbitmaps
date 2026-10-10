// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.navigation.PlanFailure
import `in`.orbitmaps.app.navigation.PlanUi
import `in`.orbitmaps.app.navigation.TripUi
import `in`.orbitmaps.app.places.PlaceItem
import `in`.orbitmaps.app.places.SamplePlaces
import `in`.orbitmaps.app.routing.Costing
import `in`.orbitmaps.app.ui.components.ChipRow
import `in`.orbitmaps.app.ui.components.ListRow
import `in`.orbitmaps.app.ui.components.SampleDataNote
import `in`.orbitmaps.app.ui.components.StatusTag
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

/** The travel modes offered in the route preview, with their Valhalla costing. */
private val TravelModes = listOf(
    R.string.mode_car to Costing.Car,
    R.string.mode_bike to Costing.Bike,
    R.string.mode_walk to Costing.Walk
)

/**
 * Window 6: travel mode and the route calculated on the phone, with Start. While planning it shows
 * progress; if no route can be found it says why. Debug builds also offer a simulated drive.
 */
@Composable
fun RoutePreviewSheet(
    placeId: String,
    loadPlace: suspend (String) -> PlaceItem?,
    trip: TripUi,
    onStart: (simulate: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val place = rememberPlace(placeId, loadPlace) ?: return
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val currentTrip = rememberUpdatedState(trip)
    LaunchedEffect(place.id, mode) { currentTrip.value.onPlan(place, TravelModes[mode].second) }
    DisposableEffect(Unit) { onDispose { currentTrip.value.onClearPlan() } }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.route_to, place.name), style = MaterialTheme.typography.titleLarge)
        ChipRow {
            TravelModes.forEachIndexed { index, (label, _) ->
                FilterChip(selected = index == mode, onClick = {
                    mode = index
                }, label = { Text(stringResource(label)) })
            }
        }
        when (val plan = trip.plan) {
            PlanUi.Idle, PlanUi.Planning -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.route_finding), style = MaterialTheme.typography.bodyLarge)
            }
            is PlanUi.Ready -> ReadyRoute(plan, trip, onStart)
            is PlanUi.Failed -> FailedRoute(plan.reason, trip)
        }
        if (place.isSample) SampleDataNote()
    }
}

@Composable
private fun ReadyRoute(plan: PlanUi.Ready, trip: TripUi, onStart: (Boolean) -> Unit) {
    Card(
        border = BorderStroke(2.dp, OrbitColors.RouteGreen),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    R.string.route_summary_short,
                    kotlin.math.ceil(plan.route.timeSeconds / 60.0).toInt(),
                    plan.route.lengthM / 1000.0
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(if (plan.fromRegion) R.string.route_on_phone_offline else R.string.route_on_phone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (plan.tilesIncomplete) {
                Text(
                    stringResource(R.string.route_data_incomplete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    Button(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_start))
    }
    if (trip.debugTools) {
        OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.route_simulate))
        }
    }
}

@Composable
private fun FailedRoute(reason: PlanFailure, trip: TripUi) {
    Text(
        stringResource(
            when (reason) {
                PlanFailure.NeedsData -> R.string.route_needs_data
                PlanFailure.NoRoute -> R.string.route_none
                PlanFailure.NoDestination -> R.string.route_no_destination
            }
        ),
        style = MaterialTheme.typography.bodyLarge
    )
    if (trip.debugTools) {
        OutlinedButton(onClick = trip.onUseSampleRoute, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.route_use_sample))
        }
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
        Surface {
            RoutePreviewSheet("cafe", samples::place, TripUi.None, onStart = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

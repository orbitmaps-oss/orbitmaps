// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.places.PlaceItem
import `in`.orbitmaps.app.places.SamplePlaces
import `in`.orbitmaps.app.search.CategoryGroup
import `in`.orbitmaps.app.ui.components.ChipRow
import `in`.orbitmaps.app.ui.components.ListRow
import `in`.orbitmaps.app.ui.components.MapPlaceholder
import `in`.orbitmaps.app.ui.components.SampleDataNote
import `in`.orbitmaps.app.ui.components.SectionTitle
import `in`.orbitmaps.app.ui.components.StatusTag
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.app.ui.theme.OrbitTheme

/** The search chips under the search bar (window 1) and on the search page (window 4). */
val QuickGroups = listOf(CategoryGroup.Food, CategoryGroup.Fuel, CategoryGroup.Ev, CategoryGroup.Trails)

/**
 * The home sheet. What the user sees depends on the sheet height: peek shows search and chips
 * (window 1), half adds shortcuts and "Near you now" (window 2), full adds the community feed (window 3).
 */
@Composable
fun HomeSheet(
    onSearch: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchBarButton(onClick = onSearch)
        ChipRow {
            QuickGroups.forEach { group ->
                AssistChip(onClick = onSearch, label = { Text(stringResource(group.label)) })
            }
        }

        // Window 2: half height.
        SectionTitle(stringResource(R.string.home_shortcuts))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNotYet) {
                Icon(Icons.Filled.Home, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shortcut_home))
            }
            OutlinedButton(onClick = onNotYet) { Text(stringResource(R.string.shortcut_work)) }
            OutlinedButton(onClick = onOpenSaved) {
                Icon(Icons.Filled.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shortcut_saved))
            }
        }
        SectionTitle(stringResource(R.string.home_near_you))
        // Sample until the app knows where the user is.
        val resources = LocalResources.current
        val samples = remember(resources) { SamplePlaces(resources::getString) }
        SampleData.places.take(3).forEach { place ->
            PlaceRow(samples.item(place), onClick = { onOpenPlace(place.id) })
        }

        // Window 3: full height.
        CommunityFeed(onNotYet = onNotYet)
    }
}

@Composable
private fun SearchBarButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        }
    }
}

/** One place with its category, distance and status label; used on the sheet and in search results. */
@Composable
fun PlaceRow(place: PlaceItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                placeSubtitle(place),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        place.status?.let { StatusTag(it, confirmations = place.confirmations) }
    }
}

/** "Food · 0.4 km", or "Place · 450.2 km · Maharashtra, India" for places outside the regions. */
@Composable
fun placeSubtitle(place: PlaceItem): String {
    val base = stringResource(R.string.place_category_distance, stringResource(place.category), place.distanceKm)
    return place.detail?.let { stringResource(R.string.two_parts, base, it) } ?: base
}

@Composable
private fun CommunityFeed(onNotYet: () -> Unit) {
    val filters = listOf(
        R.string.community_for_you,
        R.string.community_road,
        R.string.community_events,
        R.string.category_food,
        R.string.community_outdoors
    )
    var selected by remember { mutableIntStateOf(0) }
    SectionTitle(stringResource(R.string.community_title))
    ChipRow {
        filters.forEachIndexed { index, label ->
            FilterChip(selected = index == selected, onClick = {
                selected = index
            }, label = { Text(stringResource(label)) })
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.community_weekend_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.sample_weekend_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
    SampleData.reports.forEach { report ->
        ListRow(
            icon = Icons.Filled.Warning,
            title = stringResource(report.title),
            subtitle = stringResource(
                R.string.two_parts,
                stringResource(R.string.report_where_when, stringResource(report.where), report.minutesAgo),
                pluralStringResource(R.plurals.report_still_there_count, report.stillThere, report.stillThere)
            ),
            trailing = {
                OutlinedButton(onClick = onNotYet) { Text(stringResource(R.string.action_still_there)) }
            }
        )
    }
    SampleDataNote(Modifier.padding(vertical = 8.dp))
}

@ThemePreviews
@Composable
private fun HomeSheetPreview() {
    OrbitTheme {
        Surface {
            MapPlaceholder()
            HomeSheet(onSearch = {
            }, onOpenPlace = {}, onOpenSaved = {}, onNotYet = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

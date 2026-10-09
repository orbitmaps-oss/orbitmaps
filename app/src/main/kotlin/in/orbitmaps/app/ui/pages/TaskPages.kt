// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.ui.components.ChipRow
import `in`.orbitmaps.app.ui.components.ListRow
import `in`.orbitmaps.app.ui.components.PageScaffold
import `in`.orbitmaps.app.ui.components.SampleDataNote
import `in`.orbitmaps.app.ui.components.SectionTitle
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.sample.Delivery
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.app.ui.sheets.PlaceRow
import `in`.orbitmaps.app.ui.sheets.QuickCategories
import `in`.orbitmaps.app.ui.theme.OrbitTheme

/** Window 4: offline search with New and Confirmed labels, filters and recent searches. */
@Composable
fun SearchPage(onBack: () -> Unit, onOpenPlace: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(-1) }
    PageScaffold(title = stringResource(R.string.search_title), onBack = onBack, modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ChipRow {
            QuickCategories.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == filter,
                    onClick = { filter = if (filter == index) -1 else index },
                    label = { Text(stringResource(label)) }
                )
            }
        }
        Text(
            stringResource(R.string.search_offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (query.isEmpty() && filter < 0) {
            SectionTitle(stringResource(R.string.search_recent))
            listOf(R.string.sample_recent_market, R.string.sample_recent_station).forEach { recent ->
                val text = stringResource(recent)
                ListRow(Icons.Filled.Refresh, text, onClick = { query = text })
            }
        }
        SectionTitle(stringResource(R.string.search_results))
        val categoryName = if (filter >= 0) stringResource(QuickCategories[filter]) else null
        val results = SampleData.places.filter { place ->
            val nameMatches = stringResource(place.name).contains(query.trim(), ignoreCase = true)
            val categoryMatches = categoryName == null || stringResource(place.category) == categoryName
            nameMatches && categoryMatches
        }
        if (results.isEmpty()) {
            Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodyMedium)
        }
        results.forEach { place -> PlaceRow(place, onClick = { onOpenPlace(place.id) }) }
        SampleDataNote(Modifier.padding(vertical = 8.dp))
    }
}

private val PlaceCategories = listOf(
    R.string.category_food,
    R.string.category_fuel,
    R.string.category_ev,
    R.string.category_hospital,
    R.string.category_shop
)

/** Window 11: add a place at the spot. Publishing comes with the open places API (plan step 2.4). */
@Composable
fun AddPlacePage(onBack: () -> Unit, onPublish: () -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var hours by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var notCopied by rememberSaveable { mutableStateOf(false) }
    PageScaffold(title = stringResource(R.string.add_place_title), onBack = onBack, modifier = modifier) {
        ListRow(
            Icons.Filled.LocationOn,
            stringResource(R.string.add_place_spot_check),
            subtitle = stringResource(R.string.add_place_spot_check_hint)
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.add_place_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SectionTitle(stringResource(R.string.add_place_category))
        ChipRow {
            PlaceCategories.forEachIndexed { index, label ->
                FilterChip(selected = index == category, onClick = {
                    category = index
                }, label = { Text(stringResource(label)) })
            }
        }
        OutlinedTextField(
            value = hours,
            onValueChange = { hours = it },
            label = { Text(stringResource(R.string.add_place_hours)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(stringResource(R.string.add_place_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Checkbox) { notCopied = !notCopied }
        ) {
            Checkbox(checked = notCopied, onCheckedChange = null)
            Spacer(Modifier.widthIn(min = 8.dp))
            Text(stringResource(R.string.add_place_not_copied), style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = onPublish,
            enabled = name.isNotBlank() && notCopied,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.add_place_publish))
        }
        Text(
            stringResource(R.string.add_place_licence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Window 14: private group messages with delivery states and a regroup notice. */
@Composable
fun GroupChatPage(onBack: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    var draft by remember { mutableStateOf("") }
    PageScaffold(title = stringResource(R.string.group_chat), onBack = onBack, modifier = modifier) {
        ListRow(
            Icons.Filled.Info,
            stringResource(R.string.sample_regroup_notice),
            subtitle = stringResource(R.string.group_regroup_hint)
        )
        SampleData.messages.forEach { message ->
            val bubbleColor = if (message.mine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Column(
                horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .widthIn(max = 280.dp)
                        .background(bubbleColor, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(stringResource(message.author), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(message.text), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    stringResource(
                        when (message.delivery) {
                            Delivery.Sent -> R.string.delivery_sent
                            Delivery.Relayed -> R.string.delivery_relayed
                            Delivery.Acknowledged -> R.string.delivery_acknowledged
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.group_message_hint)) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
        SampleDataNote()
    }
}

@ThemePreviews
@Composable
private fun SearchPagePreview() {
    OrbitTheme { SearchPage(onBack = {}, onOpenPlace = {}) }
}

@ThemePreviews
@Composable
private fun AddPlacePagePreview() {
    OrbitTheme { AddPlacePage(onBack = {}, onPublish = {}) }
}

@ThemePreviews
@Composable
private fun GroupChatPagePreview() {
    OrbitTheme { GroupChatPage(onBack = {}, onSend = {}) }
}

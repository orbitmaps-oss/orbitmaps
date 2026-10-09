// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.pages

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.Attribution
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.ui.components.Divider
import `in`.orbitmaps.app.ui.components.ListRow
import `in`.orbitmaps.app.ui.components.PageScaffold
import `in`.orbitmaps.app.ui.components.SampleDataNote
import `in`.orbitmaps.app.ui.components.SectionTitle
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.components.ToggleRow
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.app.ui.shell.Destination
import `in`.orbitmaps.app.ui.theme.OrbitTheme

/** Window 15: sign-in status, contributions and links to everything personal. */
@Composable
fun ProfilePage(signedIn: Boolean, onBack: () -> Unit, onOpen: (Destination) -> Unit, modifier: Modifier = Modifier) {
    PageScaffold(title = stringResource(R.string.profile_title), onBack = onBack, modifier = modifier) {
        Card(
            onClick = { onOpen(if (signedIn) Destination.Account else Destination.SignIn) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(if (signedIn) R.string.profile_signed_in else R.string.profile_not_signed_in),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(if (signedIn) R.string.profile_manage_account else R.string.profile_sign_in_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        ListRow(
            Icons.Filled.Star,
            stringResource(R.string.profile_contributions),
            subtitle = stringResource(R.string.profile_contributions_hint)
        )
        Divider()
        ListRow(Icons.Filled.Favorite, stringResource(R.string.saved_title), onClick = { onOpen(Destination.Saved) })
        ListRow(Icons.Filled.Place, stringResource(R.string.regions_title), onClick = {
            onOpen(Destination.OfflineRegions)
        })
        ListRow(Icons.Filled.Face, stringResource(R.string.group_title), onClick = { onOpen(Destination.GroupTrip) })
        ListRow(Icons.Filled.Lock, stringResource(R.string.privacy_title), onClick = {
            onOpen(Destination.PrivacySettings)
        })
        ListRow(Icons.Filled.Info, stringResource(R.string.about_title), onClick = { onOpen(Destination.About) })
    }
}

/** Window 16: saved places and trips, stored on the phone, with the sync status. */
@Composable
fun SavedPage(signedIn: Boolean, onBack: () -> Unit, onOpenPlace: (String) -> Unit, modifier: Modifier = Modifier) {
    PageScaffold(title = stringResource(R.string.saved_title), onBack = onBack, modifier = modifier) {
        ListRow(
            Icons.Filled.Refresh,
            stringResource(if (signedIn) R.string.saved_sync_on else R.string.saved_sync_off),
            subtitle = stringResource(R.string.saved_on_phone)
        )
        SectionTitle(stringResource(R.string.saved_places))
        SampleData.places.take(2).forEach { place ->
            ListRow(
                Icons.Filled.Favorite,
                stringResource(place.name),
                subtitle = stringResource(place.category),
                onClick = { onOpenPlace(place.id) }
            )
        }
        SectionTitle(stringResource(R.string.saved_trips))
        ListRow(
            Icons.AutoMirrored.Filled.List,
            stringResource(R.string.sample_trip),
            subtitle = stringResource(R.string.sample_trip_detail)
        )
        SampleDataNote(Modifier.padding(vertical = 8.dp))
    }
}

/** Window 17: region sizes, ready or download, and Wi-Fi-only updates. */
@Composable
fun OfflineRegionsPage(
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    PageScaffold(title = stringResource(R.string.regions_title), onBack = onBack, modifier = modifier) {
        ToggleRow(
            title = stringResource(R.string.regions_wifi_only),
            subtitle = stringResource(R.string.regions_wifi_only_hint),
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange
        )
        Divider()
        SampleData.regions.forEach { region ->
            ListRow(
                Icons.Filled.Place,
                stringResource(region.name),
                subtitle = stringResource(R.string.regions_size_mb, region.sizeMb),
                trailing = {
                    if (region.installed) {
                        Text(stringResource(R.string.regions_ready), color = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.regions_download)) }
                    }
                }
            )
        }
        SampleDataNote(Modifier.padding(vertical = 8.dp))
    }
}

/** Switches that are placeholders until their features exist. */
private val PlannedPrivacySwitches = listOf(
    R.string.privacy_search_history to R.string.privacy_search_history_hint,
    R.string.privacy_share_hazards to R.string.privacy_share_hazards_hint,
    R.string.privacy_crash_reports to R.string.privacy_crash_reports_hint
)

/**
 * Window 18: every data switch. "Browse undownloaded areas" is real ([streamMap]); the others are
 * placeholders until their features exist.
 */
@Composable
fun PrivacySettingsPage(
    streamMap: Boolean,
    onStreamMapChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = remember { mutableStateMapOf(R.string.privacy_search_history to true) }
    PageScaffold(title = stringResource(R.string.privacy_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodyMedium)
        ToggleRow(
            title = stringResource(R.string.privacy_stream_tiles),
            subtitle = stringResource(R.string.privacy_stream_tiles_hint),
            checked = streamMap,
            onCheckedChange = onStreamMapChange
        )
        PlannedPrivacySwitches.forEach { (title, hint) ->
            ToggleRow(
                title = stringResource(title),
                subtitle = stringResource(hint),
                checked = enabled[title] == true,
                onCheckedChange = { enabled[title] = it }
            )
        }
        Divider()
        ListRow(Icons.Filled.Delete, stringResource(R.string.privacy_clear_history), onClick = onClearHistory)
    }
}

/** Window 19: version, licences, data credits, the data repository and support. */
@Composable
fun AboutPage(onBack: () -> Unit, onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember(context) { versionName(context) }
    // Opens the browser app; Orbit Maps itself makes no network request.
    val open: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (e: ActivityNotFoundException) {
            Unit
        } catch (e: IllegalArgumentException) {
            Unit
        }
    }
    PageScaffold(title = stringResource(R.string.about_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(stringResource(R.string.about_promise), style = MaterialTheme.typography.bodyMedium)
        SectionTitle(stringResource(R.string.about_credits))
        ListRow(
            Icons.Filled.Place,
            stringResource(R.string.osm_attribution),
            subtitle = stringResource(R.string.about_osm_licence),
            onClick = { open(Attribution.OSM_COPYRIGHT_URL) }
        )
        ListRow(
            Icons.Filled.Info,
            stringResource(R.string.about_app_licence),
            subtitle = stringResource(R.string.about_source_code),
            onClick = { open(Attribution.SOURCE_CODE_URL) }
        )
        ListRow(
            Icons.Filled.Share,
            stringResource(R.string.about_data_repository),
            subtitle = stringResource(R.string.about_data_repository_hint)
        )
        ListRow(Icons.Filled.Favorite, stringResource(R.string.about_support), onClick = onSupport)
    }
}

private fun versionName(context: Context): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}

/** Window 20: optional sign-in, only to sync encrypted data. Skipping must always be possible. */
@Composable
fun SignInPage(
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onEmail: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    PageScaffold(title = stringResource(R.string.sign_in_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.sign_in_explainer), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onGoogle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sign_in_google))
        }
        OutlinedButton(onClick = onEmail, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sign_in_email))
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sign_in_skip))
        }
        Text(
            stringResource(R.string.sign_in_not_linked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Window 21: email and a 6-digit code with a resend timer. Verifying is a placeholder for now. */
@Composable
fun EmailCodePage(onBack: () -> Unit, onVerify: () -> Unit, onResend: () -> Unit, modifier: Modifier = Modifier) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    PageScaffold(title = stringResource(R.string.email_title), onBack = onBack, modifier = modifier) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter(Char::isDigit).take(CODE_LENGTH) },
            label = { Text(stringResource(R.string.email_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.email_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onVerify,
            enabled = code.length == CODE_LENGTH,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.email_verify))
        }
        TextButton(onClick = onResend) { Text(stringResource(R.string.email_resend)) }
    }
}

private const val CODE_LENGTH = 6

/** Window 22: sign-in method, recovery phrase, move to a new phone, sign out and delete account. */
@Composable
fun AccountPage(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    PageScaffold(title = stringResource(R.string.account_title), onBack = onBack, modifier = modifier) {
        ListRow(
            Icons.Filled.Email,
            stringResource(R.string.account_method_email),
            subtitle = stringResource(R.string.account_method)
        )
        ListRow(
            Icons.Filled.Lock,
            stringResource(R.string.account_recovery_phrase),
            subtitle = stringResource(R.string.account_recovery_phrase_hint),
            onClick = onNotYet
        )
        ListRow(
            Icons.Filled.AccountCircle,
            stringResource(R.string.account_move_phone),
            subtitle = stringResource(R.string.account_move_phone_hint),
            onClick = onNotYet
        )
        ListRow(Icons.Filled.Settings, stringResource(R.string.account_sync_settings), onClick = onNotYet)
        Divider()
        ListRow(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.account_sign_out), onClick = onSignOut)
        ListRow(
            Icons.Filled.Delete,
            stringResource(R.string.account_delete),
            subtitle = stringResource(R.string.account_delete_hint),
            onClick = onDelete
        )
        ListRow(Icons.Filled.Person, stringResource(R.string.account_contributions_separate))
    }
}

@ThemePreviews
@Composable
private fun ProfilePagePreview() {
    OrbitTheme { ProfilePage(signedIn = false, onBack = {}, onOpen = {}) }
}

@ThemePreviews
@Composable
private fun SavedPagePreview() {
    OrbitTheme { SavedPage(signedIn = false, onBack = {}, onOpenPlace = {}) }
}

@ThemePreviews
@Composable
private fun OfflineRegionsPagePreview() {
    OrbitTheme { OfflineRegionsPage(wifiOnly = true, onWifiOnlyChange = {}, onBack = {}, onDownload = {}) }
}

@ThemePreviews
@Composable
private fun PrivacySettingsPagePreview() {
    OrbitTheme { PrivacySettingsPage(streamMap = true, onStreamMapChange = {}, onBack = {}, onClearHistory = {}) }
}

@ThemePreviews
@Composable
private fun AboutPagePreview() {
    OrbitTheme { AboutPage(onBack = {}, onSupport = {}) }
}

@ThemePreviews
@Composable
private fun SignInPagePreview() {
    OrbitTheme { SignInPage(onBack = {}, onGoogle = {}, onEmail = {}, onSkip = {}) }
}

@ThemePreviews
@Composable
private fun EmailCodePagePreview() {
    OrbitTheme { EmailCodePage(onBack = {}, onVerify = {}, onResend = {}) }
}

@ThemePreviews
@Composable
private fun AccountPagePreview() {
    OrbitTheme { AccountPage(onBack = {}, onSignOut = {}, onDelete = {}, onNotYet = {}) }
}

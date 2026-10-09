// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.ui.components.MapPlaceholder
import `in`.orbitmaps.app.ui.components.ThemePreviews
import `in`.orbitmaps.app.ui.glance.GlanceMode
import `in`.orbitmaps.app.ui.glance.glanceBottomInset
import `in`.orbitmaps.app.ui.pages.AboutPage
import `in`.orbitmaps.app.ui.pages.AccountPage
import `in`.orbitmaps.app.ui.pages.AddPlacePage
import `in`.orbitmaps.app.ui.pages.EmailCodePage
import `in`.orbitmaps.app.ui.pages.GroupChatPage
import `in`.orbitmaps.app.ui.pages.OfflineRegionsPage
import `in`.orbitmaps.app.ui.pages.PrivacySettingsPage
import `in`.orbitmaps.app.ui.pages.ProfilePage
import `in`.orbitmaps.app.ui.pages.SavedPage
import `in`.orbitmaps.app.ui.pages.SearchPage
import `in`.orbitmaps.app.ui.pages.SignInPage
import `in`.orbitmaps.app.ui.sheets.ContributeSheet
import `in`.orbitmaps.app.ui.sheets.GroupTripSheet
import `in`.orbitmaps.app.ui.sheets.HomeSheet
import `in`.orbitmaps.app.ui.sheets.LayersSheet
import `in`.orbitmaps.app.ui.sheets.PlaceDetailsSheet
import `in`.orbitmaps.app.ui.sheets.RoutePreviewSheet
import `in`.orbitmaps.app.ui.theme.OrbitColors
import `in`.orbitmaps.app.ui.theme.OrbitTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val FabSize = 56.dp

/**
 * The app shell (plan step 12.1): the map fills the screen, one bottom sheet carries everything else, a
 * single + button handles contributions, and glance mode replaces it all while navigating.
 *
 * @param map draws the map; `bottomInset` is how much of the bottom of the screen is covered.
 */
@Composable
fun AppShell(
    state: AppState,
    update: (AppState.() -> AppState) -> Unit,
    map: @Composable (bottomInset: Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notYetMessage = stringResource(R.string.not_available_yet)
    val notYet: () -> Unit = { scope.launch { snackbar.showSnackbar(notYetMessage) } }
    val back: () -> Unit = { update { back() ?: this } }

    BackHandler(enabled = state.back() != null, onBack = back)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sheetState = rememberSheetState(state.sheetAnchor)
        val heightPx = with(density) { maxHeight.toPx() }
        val peekTopPx = heightPx - with(density) { PeekHeight.toPx() }
        // The live drag position changes every frame, so it is read only during layout (the + button).
        val liveSheetTop: () -> Float = { sheetState.offset.takeUnless { it.isNaN() } ?: peekTopPx }
        // The map inset follows the height the sheet is heading for, which changes only at thresholds.
        val targetSheetTop =
            sheetState.anchors.positionOf(sheetState.targetValue).takeUnless { it.isNaN() } ?: peekTopPx
        val current = state.current

        map(
            when (current) {
                is Destination.Sheet -> with(density) { (heightPx - targetSheetTop).coerceAtLeast(0f).toDp() }
                Destination.Driving -> glanceBottomInset(state.drivingActionsOpen)
                is Destination.Page -> 0.dp
            }
        )

        when (current) {
            is Destination.Sheet -> {
                MapControls(
                    onProfile = { update { open(Destination.Profile) } },
                    onLayers = { update { open(Destination.Layers) } }
                )
                if (current == Destination.Home) {
                    FloatingActionButton(
                        onClick = { update { open(Destination.Contribute) } },
                        containerColor = OrbitColors.Emerald,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                val margin = 16.dp.roundToPx()
                                IntOffset(-margin, (liveSheetTop() - FabSize.toPx() - margin).roundToInt())
                            }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_contribute))
                    }
                }
                MapSheet(
                    state = sheetState,
                    requested = state.sheetAnchor,
                    onSettled = { anchor -> update { settleSheet(anchor) } },
                    containerHeight = maxHeight
                ) {
                    if (current != Destination.Home) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = back) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                    }
                    SheetContent(current, state, update, notYet)
                }
            }
            Destination.Driving -> {
                OrbitTheme(darkTheme = state.mapTheme == MapTheme.Night || isSystemInDarkTheme()) {
                    GlanceMode(
                        actionsOpen = state.drivingActionsOpen,
                        onToggleActions = { update { toggleDrivingActions() } },
                        onEnd = { update { endNavigation() } },
                        onNotYet = notYet
                    )
                }
            }
            is Destination.Page -> PageContent(current, state, update, back, notYet)
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

@Composable
private fun SheetContent(
    current: Destination.Sheet,
    state: AppState,
    update: (AppState.() -> AppState) -> Unit,
    notYet: () -> Unit
) {
    when (current) {
        Destination.Home -> HomeSheet(
            onSearch = { update { open(Destination.Search) } },
            onOpenPlace = { id -> update { open(Destination.PlaceDetails(id)) } },
            onOpenSaved = { update { open(Destination.Saved) } },
            onNotYet = notYet
        )
        is Destination.PlaceDetails -> PlaceDetailsSheet(
            placeId = current.placeId,
            onGo = { update { open(Destination.RoutePreview(current.placeId)) } },
            onNotYet = notYet
        )
        is Destination.RoutePreview -> RoutePreviewSheet(
            placeId = current.placeId,
            onStart = { update { startNavigation() } }
        )
        Destination.Contribute -> ContributeSheet(onAddPlace = {
            update { open(Destination.AddPlace) }
        }, onNotYet = notYet)
        Destination.Layers -> LayersSheet(theme = state.mapTheme, onThemeChange = { theme ->
            update { selectMapTheme(theme) }
        })
        Destination.GroupTrip -> GroupTripSheet(onOpenChat = {
            update { open(Destination.GroupChat) }
        }, onNotYet = notYet)
    }
}

@Composable
private fun PageContent(
    current: Destination.Page,
    state: AppState,
    update: (AppState.() -> AppState) -> Unit,
    back: () -> Unit,
    notYet: () -> Unit
) {
    val openPlace: (String) -> Unit = { id -> update { open(Destination.PlaceDetails(id)) } }
    when (current) {
        Destination.Search -> SearchPage(onBack = back, onOpenPlace = openPlace)
        Destination.AddPlace -> AddPlacePage(onBack = back, onPublish = notYet)
        Destination.GroupChat -> GroupChatPage(onBack = back, onSend = notYet)
        Destination.Profile -> ProfilePage(signedIn = state.signedIn, onBack = back, onOpen = { update { open(it) } })
        Destination.Saved -> SavedPage(signedIn = state.signedIn, onBack = back, onOpenPlace = openPlace)
        Destination.OfflineRegions -> OfflineRegionsPage(onBack = back, onDownload = notYet)
        Destination.PrivacySettings -> PrivacySettingsPage(onBack = back, onClearHistory = notYet)
        Destination.About -> AboutPage(onBack = back, onSupport = notYet)
        Destination.SignIn -> SignInPage(
            onBack = back,
            onGoogle = notYet,
            onEmail = { update { open(Destination.EmailCode) } },
            onSkip = { update { skipSignIn() } }
        )
        Destination.EmailCode -> EmailCodePage(onBack = back, onVerify = { update { signIn() } }, onResend = notYet)
        Destination.Account -> AccountPage(
            onBack = back,
            onSignOut = { update { signOut() } },
            onDelete = notYet,
            onNotYet = notYet
        )
    }
}

/** The round profile and layers buttons at the top right, and the offline pill at the top left. */
@Composable
private fun MapControls(onProfile: () -> Unit, onLayers: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        Text(
            text = stringResource(R.string.offline_pill, stringResource(R.string.sample_region_panaji)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(OrbitColors.DarkGreen, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Column(modifier = Modifier.align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundButton(onClick = onProfile) {
                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.action_profile))
            }
            RoundButton(onClick = onLayers) {
                Icon(painterResource(R.drawable.ic_layers), contentDescription = stringResource(R.string.action_layers))
            }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) { content() }
    }
}

@ThemePreviews
@Composable
private fun AppShellHomePreview() {
    OrbitTheme {
        AppShell(state = AppState(), update = {}, map = { MapPlaceholder() })
    }
}

@ThemePreviews
@Composable
private fun AppShellHalfPreview() {
    OrbitTheme {
        AppShell(state = AppState(sheetAnchor = SheetAnchor.Half), update = {}, map = { MapPlaceholder() })
    }
}

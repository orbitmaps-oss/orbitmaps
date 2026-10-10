// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {
    private val home = AppState()

    @Test
    fun startsOnTheMapWithTheSheetLowered() {
        assertEquals(Destination.Home, home.current)
        assertEquals(SheetAnchor.Peek, home.sheetAnchor)
        assertTrue(home.showsMapControls)
        assertFalse(home.signedIn)
    }

    @Test
    fun backOnTheLoweredHomeSheetLetsTheActivityClose() {
        assertNull(home.back())
    }

    @Test
    fun backLowersARaisedHomeSheetFirst() {
        val raised = home.settleSheet(SheetAnchor.Full)
        assertEquals(SheetAnchor.Peek, raised.back()?.sheetAnchor)
        assertEquals(Destination.Home, raised.back()?.current)
    }

    @Test
    fun sheetsOpenAtHalfHeightAndBackReturnsToTheLoweredMap() {
        val place = home.open(Destination.PlaceDetails("cafe"))
        assertEquals(SheetAnchor.Half, place.sheetAnchor)
        assertTrue(place.showsMapControls)
        val back = requireNotNull(place.back())
        assertEquals(Destination.Home, back.current)
        assertEquals(SheetAnchor.Peek, back.sheetAnchor)
    }

    @Test
    fun pagesHideTheMapControlsAndKeepTheSheetHeight() {
        val search = home.settleSheet(SheetAnchor.Half).open(Destination.Search)
        assertEquals(Destination.Search, search.current)
        assertFalse(search.showsMapControls)
        assertEquals(SheetAnchor.Half, search.sheetAnchor)
    }

    @Test
    fun searchResultToPlaceToRouteToNavigation() {
        val route = home
            .open(Destination.Search)
            .open(Destination.PlaceDetails("fuel"))
            .open(Destination.RoutePreview("fuel"))
        assertEquals(Destination.RoutePreview("fuel"), route.current)
        assertEquals(Destination.PlaceDetails("fuel"), route.back()?.current)
        assertEquals(Destination.Search, route.back()?.back()?.current)

        val driving = route.startNavigation()
        assertEquals(Destination.Driving, driving.current)
        assertFalse(driving.showsMapControls)
        assertEquals(listOf(Destination.Home, Destination.Driving), driving.backStack)
    }

    @Test
    fun openingDrivingIsTheSameAsStartingNavigation() {
        assertEquals(home.startNavigation(), home.open(Destination.Driving))
    }

    @Test
    fun backInGlanceModeClosesTheActionsBeforeEndingNavigation() {
        val actions = home.startNavigation().toggleDrivingActions()
        assertTrue(actions.drivingActionsOpen)
        val closed = requireNotNull(actions.back())
        assertEquals(Destination.Driving, closed.current)
        assertFalse(closed.drivingActionsOpen)
        val ended = requireNotNull(closed.back())
        assertEquals(Destination.Home, ended.current)
        assertEquals(SheetAnchor.Peek, ended.sheetAnchor)
    }

    @Test
    fun endNavigationReturnsToTheMapWithActionsClosed() {
        val ended = home.startNavigation().toggleDrivingActions().endNavigation()
        assertEquals(listOf(Destination.Home), ended.backStack)
        assertFalse(ended.drivingActionsOpen)
    }

    @Test
    fun contributeToAddPlaceAndBack() {
        val form = home.open(Destination.Contribute).open(Destination.AddPlace)
        assertEquals(Destination.AddPlace, form.current)
        val contribute = requireNotNull(form.back())
        assertEquals(Destination.Contribute, contribute.current)
        assertEquals(SheetAnchor.Half, contribute.sheetAnchor)
        assertEquals(Destination.Home, contribute.back()?.current)
    }

    @Test
    fun profilePagesReturnToTheProfileMenu() {
        val profile = home.open(Destination.Profile)
        listOf(
            Destination.Saved,
            Destination.OfflineRegions,
            Destination.PrivacySettings,
            Destination.About,
            Destination.GroupTrip
        ).forEach { page ->
            assertEquals(Destination.Profile, profile.open(page).back()?.current)
        }
    }

    @Test
    fun theAppWorksWithoutSigningIn() {
        val skipped = home.open(Destination.Profile).open(Destination.SignIn).skipSignIn()
        assertEquals(Destination.Profile, skipped.current)
        assertFalse(skipped.signedIn)

        val skippedFromEmail = home.open(Destination.Profile).open(Destination.SignIn).open(Destination.EmailCode)
            .skipSignIn()
        assertEquals(Destination.Profile, skippedFromEmail.current)
    }

    @Test
    fun signingInShowsTheAccountAndBackSkipsTheSignInForms() {
        val account = home.open(Destination.Profile).open(Destination.SignIn).open(Destination.EmailCode).signIn()
        assertTrue(account.signedIn)
        assertEquals(Destination.Account, account.current)
        assertEquals(Destination.Profile, account.back()?.current)
    }

    @Test
    fun signingOutReturnsToTheProfileMenu() {
        val signedOut = home.open(Destination.Profile).open(Destination.SignIn).signIn().signOut()
        assertFalse(signedOut.signedIn)
        assertEquals(Destination.Profile, signedOut.current)
    }

    @Test
    fun homeClearsTheBackStack() {
        val deep = home.open(Destination.Profile).open(Destination.Saved).open(Destination.PlaceDetails("cafe"))
        assertEquals(AppState(), deep.home())
        assertEquals(deep.home(), deep.open(Destination.Home))
    }

    @Test
    fun mapThemeChangesWithoutMovingAnywhere() {
        val layers = home.open(Destination.Layers)
        val night = layers.selectMapTheme(MapTheme.Night)
        assertEquals(MapTheme.Night, night.mapTheme)
        assertEquals(Destination.Layers, night.current)
        assertEquals(MapTheme.Night, night.back()?.mapTheme)
    }

    @Test
    fun tappingTheHandleCyclesThroughTheThreeHeights() {
        assertEquals(SheetAnchor.Half, next(SheetAnchor.Peek))
        assertEquals(SheetAnchor.Full, next(SheetAnchor.Half))
        assertEquals(SheetAnchor.Peek, next(SheetAnchor.Full))
    }

    @Test(expected = IllegalArgumentException::class)
    fun theBackStackMustStartAtHome() {
        AppState(backStack = listOf(Destination.Search))
    }
}

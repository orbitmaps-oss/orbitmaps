// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.shell

/** The three heights of the bottom sheet (plan step 12.1). */
enum class SheetAnchor { Peek, Half, Full }

/** The map themes offered in the layers sheet (window 12). */
enum class MapTheme { Day, Night, Rider, Trek }

/**
 * Every place the app can be, by window number from plan step 12.2.
 *
 * [Sheet] destinations are drawn in the bottom sheet over the map, [Page] destinations cover the whole
 * screen, and [Driving] is glance mode. The home sheet (windows 1 to 3) is [Home] at each [SheetAnchor].
 */
sealed interface Destination {
    sealed interface Sheet : Destination

    sealed interface Page : Destination

    /** Windows 1 to 3. */
    data object Home : Sheet

    /** Window 5. */
    data class PlaceDetails(val placeId: String) : Sheet

    /** Window 6. */
    data class RoutePreview(val placeId: String) : Sheet

    /** Window 10. */
    data object Contribute : Sheet

    /** Window 12. */
    data object Layers : Sheet

    /** Window 13. */
    data object GroupTrip : Sheet

    /** Windows 7 to 9. */
    data object Driving : Destination

    /** Window 4. */
    data object Search : Page

    /** Window 11. */
    data object AddPlace : Page

    /** Window 14. */
    data object GroupChat : Page

    /** Window 15. */
    data object Profile : Page

    /** Window 16. */
    data object Saved : Page

    /** Window 17. */
    data object OfflineRegions : Page

    /** Window 18. */
    data object PrivacySettings : Page

    /** Window 19. */
    data object About : Page

    /** Window 20. */
    data object SignIn : Page

    /** Window 21. */
    data object EmailCode : Page

    /** Window 22. */
    data object Account : Page
}

/**
 * The whole UI state as an immutable value. Every change is a pure function returning a new state, so
 * navigation can be unit-tested without Android. This replaces a NavHost, as plan step 12.3 asks.
 */
data class AppState(
    val backStack: List<Destination> = listOf(Destination.Home),
    val sheetAnchor: SheetAnchor = SheetAnchor.Peek,
    val mapTheme: MapTheme = MapTheme.Day,
    val signedIn: Boolean = false,
    val drivingActionsOpen: Boolean = false
) {
    init {
        require(backStack.firstOrNull() == Destination.Home) { "the back stack must start at Home" }
    }

    val current: Destination get() = backStack.last()

    /** The profile and layers buttons and the + button are shown only on the plain map with a sheet. */
    val showsMapControls: Boolean get() = current is Destination.Sheet

    /** Opens [destination] on top of the current one. Sheets open at half height. */
    fun open(destination: Destination): AppState = when (destination) {
        Destination.Home -> home()
        Destination.Driving -> startNavigation()
        else -> copy(
            backStack = backStack + destination,
            sheetAnchor = if (destination is Destination.Sheet) SheetAnchor.Half else sheetAnchor
        )
    }

    /**
     * Handles the system back gesture. Returns null when there is nothing left to go back to, so the
     * activity can close.
     */
    fun back(): AppState? = when {
        drivingActionsOpen -> copy(drivingActionsOpen = false)
        current == Destination.Driving -> endNavigation()
        backStack.size > 1 -> {
            val previous = backStack.dropLast(1)
            copy(
                backStack = previous,
                sheetAnchor = if (previous.last() == Destination.Home) SheetAnchor.Peek else SheetAnchor.Half
            )
        }
        sheetAnchor != SheetAnchor.Peek -> copy(sheetAnchor = SheetAnchor.Peek)
        else -> null
    }

    /** Clears everything back to the map with the sheet lowered. */
    fun home(): AppState = copy(backStack = listOf(Destination.Home), sheetAnchor = SheetAnchor.Peek)

    /** Called when the user drags the sheet to a new height. */
    fun settleSheet(anchor: SheetAnchor): AppState = copy(sheetAnchor = anchor)

    /** Glance mode replaces the route preview; back or End returns to the plain map. */
    fun startNavigation(): AppState =
        copy(backStack = listOf(Destination.Home, Destination.Driving), drivingActionsOpen = false)

    fun endNavigation(): AppState = home().copy(drivingActionsOpen = false)

    fun toggleDrivingActions(): AppState = copy(drivingActionsOpen = !drivingActionsOpen)

    fun selectMapTheme(theme: MapTheme): AppState = copy(mapTheme = theme)

    /** Placeholder sign-in: the email code page signs in and shows the account page. */
    fun signIn(): AppState = copy(
        backStack = backStack.filterNot { it == Destination.SignIn || it == Destination.EmailCode } +
            Destination.Account,
        signedIn = true
    )

    /** Signing out or deleting the account returns to the profile menu. Nothing else changes. */
    fun signOut(): AppState = copy(
        backStack = backStack.filterNot { it == Destination.Account },
        signedIn = false
    )

    /** "Skip, use without account" leaves the sign-in flow without changing anything else. */
    fun skipSignIn(): AppState =
        copy(backStack = backStack.filterNot { it == Destination.SignIn || it == Destination.EmailCode })
}

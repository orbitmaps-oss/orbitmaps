// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import kotlin.math.roundToInt

/** Height of the sheet at [SheetAnchor.Peek]: the handle, the search bar and one row of chips. */
val PeekHeight = 148.dp

/** Space left above the sheet at [SheetAnchor.Full], so the map stays visible behind it. */
private val FullTopGap = 56.dp

/** Creates the sheet's drag state; [MapSheet] sets its anchors once the screen height is known. */
@Composable
fun rememberSheetState(initial: SheetAnchor): AnchoredDraggableState<SheetAnchor> =
    remember { AnchoredDraggableState(initialValue = initial) }

/**
 * The single bottom sheet over the map, with three heights (plan step 12.1). Drag the handle, or tap it
 * to cycle peek, half and full. [requested] comes from [AppState]; drags are reported by [onSettled].
 *
 * @param containerHeight height of the area the sheet moves in.
 */
@Composable
fun MapSheet(
    state: AnchoredDraggableState<SheetAnchor>,
    requested: SheetAnchor,
    onSettled: (SheetAnchor) -> Unit,
    containerHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    val heightPx = with(density) { containerHeight.toPx() }
    val peekPx = with(density) { PeekHeight.toPx() } + bottomInset
    val fullPx = with(density) { FullTopGap.toPx() } + WindowInsets.safeDrawing.getTop(density)
    SideEffect {
        state.updateAnchors(
            DraggableAnchors {
                SheetAnchor.Full at fullPx
                SheetAnchor.Half at heightPx / 2f
                SheetAnchor.Peek at heightPx - peekPx
            }
        )
    }

    // App state -> sheet: animate when a screen asks for another height.
    LaunchedEffect(requested) {
        if (state.settledValue != requested) state.animateTo(requested)
    }
    // Sheet -> app state: report where a drag ended.
    val currentRequested = rememberUpdatedState(requested)
    val currentOnSettled = rememberUpdatedState(onSettled)
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { settled ->
            if (settled != currentRequested.value) currentOnSettled.value(settled)
        }
    }

    val handleLabel = stringResource(R.string.sheet_handle)
    val handleState = stringResource(
        when (state.settledValue) {
            SheetAnchor.Peek -> R.string.sheet_state_peek
            SheetAnchor.Half -> R.string.sheet_state_half
            SheetAnchor.Full -> R.string.sheet_state_full
        }
    )
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight - with(density) { fullPx.toDp() })
            .offset {
                val offset = if (state.offset.isNaN()) heightPx - peekPx else state.offset
                IntOffset(0, offset.roundToInt())
            }
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Vertical,
                        flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)
                    )
                    .clickable(role = Role.Button) { onSettled(next(state.settledValue)) }
                    .semantics {
                        contentDescription = handleLabel
                        stateDescription = handleState
                    }
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                content = content
            )
        }
    }
}

/** Tapping the handle cycles peek -> half -> full -> peek. */
internal fun next(anchor: SheetAnchor): SheetAnchor = when (anchor) {
    SheetAnchor.Peek -> SheetAnchor.Half
    SheetAnchor.Half -> SheetAnchor.Full
    SheetAnchor.Full -> SheetAnchor.Peek
}

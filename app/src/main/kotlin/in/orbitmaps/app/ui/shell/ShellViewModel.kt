// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Keeps [AppState] across configuration changes such as rotation. Nothing is stored on disk. */
class ShellViewModel : ViewModel() {
    var state by mutableStateOf(AppState())
        private set

    fun update(change: AppState.() -> AppState) {
        state = state.change()
    }
}

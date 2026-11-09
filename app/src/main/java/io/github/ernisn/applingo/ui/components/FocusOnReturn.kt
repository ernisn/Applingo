package io.github.ernisn.applingo.ui.components

import io.github.ernisn.applingo.ui.LocalPageActive
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/*
 * D-pad focus when a pane comes back. The control that had focus went away with the pane it was in
 * (an app's detail shown instead of the list, the picker closed by Apply), and focus went nowhere:
 * the next key press started over at the top of the screen. Touch input has no focus to restore.
 */

/** The app whose row a list pane focuses when it comes back, and what to call once it has. */
class FocusOnReturnRequest(val packageName: String, val onDone: () -> Unit)

/**
 * When a list pane comes back, focuses the row at [index] (the app that was open), scrolling to it
 * only if it is out of sight, and calls [onDone]. Runs once each time the pane enters the
 * composition, or becomes the page shown again ([LocalPageActive]: beside the upright island the
 * list stays composed behind it while the detail is shown), with the index it had then; null does
 * nothing.
 */
@Composable
fun FocusRowOnReturn(index: Int?, listState: LazyListState, requester: FocusRequester, onDone: () -> Unit) {
    val inputModeManager = LocalInputModeManager.current
    val shown = LocalPageActive.current
    LaunchedEffect(shown) {
        if (!shown || index == null) return@LaunchedEffect
        onDone()
        if (inputModeManager.inputMode != InputMode.Keyboard) return@LaunchedEffect
        withFrameNanos {}
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
            withFrameNanos {}
        }
        requester.requestFocusIfShown()
    }
}

/**
 * Focuses [requester]'s control once [returned] turns true, after a frame for it to be laid out, and
 * then calls [onDone] so the caller can reset [returned] (or the pane coming back for another reason,
 * such as switching destinations, would take the focus again). Only then: resetting [returned]
 * restarts this effect, and done first, a recomposition before the next frame (one more state change
 * as the picker closed) cancelled it before it could take the focus.
 */
@Composable
fun FocusOnReturn(returned: Boolean, requester: FocusRequester, onDone: () -> Unit) {
    val inputModeManager = LocalInputModeManager.current
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(returned) {
        if (!returned) return@LaunchedEffect
        if (inputModeManager.inputMode == InputMode.Keyboard) {
            withFrameNanos {}
            requester.requestFocusIfShown()
        }
        currentOnDone()
    }
}

/**
 * Focuses [requester]'s control once [pending] turns true, after a frame for it to be laid out, then
 * calls [onDone]. For a control that replaces the focused one (Select apps swaps the search bar for
 * the selection bar), which would otherwise leave focus nowhere.
 */
@Composable
fun FocusWhenPending(pending: Boolean, requester: FocusRequester, onDone: () -> Unit) {
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        if (inputModeManager.inputMode == InputMode.Keyboard) {
            withFrameNanos {}
            requester.requestFocusIfShown()
        }
        onDone()
    }
}

/** The control may not be there after all (an app uninstalled meanwhile); then focus stays put. */
private fun FocusRequester.requestFocusIfShown() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
    }
}

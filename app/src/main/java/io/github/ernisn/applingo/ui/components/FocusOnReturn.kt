package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * composition, with the index it had then; null does nothing.
 */
@Composable
fun FocusRowOnReturn(index: Int?, listState: LazyListState, requester: FocusRequester, onDone: () -> Unit) {
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (index == null) return@LaunchedEffect
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
 * calls [onDone] so the caller can reset [returned] (or the pane coming back for another reason,
 * such as switching destinations, would take the focus again).
 */
@Composable
fun FocusOnReturn(returned: Boolean, requester: FocusRequester, onDone: () -> Unit) {
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(returned) {
        if (!returned) return@LaunchedEffect
        onDone()
        if (inputModeManager.inputMode != InputMode.Keyboard) return@LaunchedEffect
        withFrameNanos {}
        requester.requestFocusIfShown()
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

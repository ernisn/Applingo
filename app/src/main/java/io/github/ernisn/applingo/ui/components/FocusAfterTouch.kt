package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * On touchscreens, lets a text field take focus only when it is actually tapped.
 *
 * When a window or pane gains focus, Android hands it to the Compose root, which passes it on to the
 * first focusable child — usually the search field — and that pops up the keyboard unasked. On D-pad
 * devices (see [isDpadDevice]) [SearchField] gates the field itself, opened with OK.
 */
@Composable
fun Modifier.focusableOnlyAfterTouch(): Modifier {
    if (isDpadDevice()) return this
    var allowFocus by remember { mutableStateOf(false) }
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                allowFocus = true
            }
        }
        .focusProperties { canFocus = allowFocus }
        .onFocusChanged { if (!it.hasFocus) allowFocus = false }
}

package io.github.ernisn.applingo.ui.components

import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext

/**
 * On touchscreens, lets a text field take focus only when it is actually tapped.
 *
 * When a window or pane gains focus, Android hands it to the Compose root, which passes it on to the
 * first focusable child — usually the search field — and that pops up the keyboard unasked. Devices
 * without a touchscreen (TV, D-pad) keep normal focus behavior so the field stays reachable.
 */
@Composable
fun Modifier.focusableOnlyAfterTouch(): Modifier {
    val context = LocalContext.current
    val hasTouchscreen = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) }
    if (!hasTouchscreen) return this
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

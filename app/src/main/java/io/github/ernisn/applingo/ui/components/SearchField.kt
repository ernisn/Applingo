package io.github.ernisn.applingo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.FieldShape

/**
 * The one search field style, for apps and for languages: a filled rounded rectangle with a search
 * icon, a clear button while there is text, and [trailing] actions (the app list's sort menu).
 *
 * Single line: pasted line breaks become spaces instead of breaking the match. [onFocused] runs when
 * the field is tapped (OK on a D-pad device), before the keyboard has finished opening.
 *
 * On a D-pad device the field never holds on to the D-pad. The text field opens its keyboard as soon
 * as it gets focus (it ignores showKeyboardOnFocus), and the keyboard then takes the arrows. So the
 * D-pad focuses the area around the text instead, outlined, and OK opens the field with its keyboard.
 * Once open, the arrows move on to the next control rather than the cursor, and leaving the field or
 * pressing Back (after Back has closed the keyboard) closes it again.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dpad = isDpadDevice()
    var open by remember { mutableStateOf(false) }
    var areaFocused by remember { mutableStateOf(false) }
    val areaFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (open) fieldFocus.requestFocus()
    }
    BackHandler(enabled = open) { areaFocus.requestFocus() }

    fun isConfirm(event: KeyEvent) =
        event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter

    fun onAreaKey(event: KeyEvent): Boolean {
        if (!isConfirm(event)) return false
        if (event.type == KeyEventType.KeyUp) {
            onFocused()
            open = true
        }
        return true
    }

    // OK brings back a keyboard closed with Back.
    fun onFieldKey(event: KeyEvent): Boolean {
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            else -> null
        }
        if (direction != null) {
            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
            return true
        }
        if (isConfirm(event)) {
            if (event.type == KeyEventType.KeyUp) keyboard?.show()
            return true
        }
        return false
    }

    Surface(
        shape = FieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (areaFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .then(
                        if (dpad) {
                            Modifier
                                .focusRequester(areaFocus)
                                .onFocusChanged { areaFocused = it.isFocused }
                                .onKeyEvent(::onAreaKey)
                                .focusable()
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { onQueryChange(it.replace(Regex("[\\r\\n]+"), " ")) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // With a D-pad the field stays focused, so Down goes on to the results.
                    keyboardActions = KeyboardActions(onSearch = { if (dpad) keyboard?.hide() else focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (dpad) {
                                Modifier
                                    .focusRequester(fieldFocus)
                                    .onFocusChanged { if (!it.isFocused) open = false }
                                    .focusProperties { canFocus = open }
                                    .onPreviewKeyEvent(::onFieldKey)
                            } else {
                                Modifier
                                    .onFocusChanged { if (it.isFocused) onFocused() }
                                    .focusableOnlyAfterTouch()
                            },
                        ),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
            trailing()
        }
    }
}

package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.ControlShape

/**
 * A message over the bottom of the page, frosted like the island it sits above and the menus and
 * dialogs ([frostedFill]), with the same hairline edge in place of a shadow. [action] goes at the
 * end, before [dismissAction]. Must not lie inside the recording it blurs (see LocalWindowBackdrop).
 */
@Composable
fun FrostedSnackbar(
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    dismissAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.padding(12.dp).widthIn(max = 600.dp).fillMaxWidth(),
        shape = ControlShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = frostedBorder(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .frostedFill(MaterialTheme.colorScheme.surfaceContainerHighest)
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
            }
            action?.invoke()
            if (dismissAction != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { dismissAction() }
            }
        }
    }
}

/** A [FrostedSnackbar] for a message from a SnackbarHost: its text, action and dismiss button. */
@Composable
fun FrostedSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    FrostedSnackbar(
        action = visuals.actionLabel?.let { label ->
            { TextButton(onClick = data::performAction, shape = ControlShape) { Text(label) } }
        },
        dismissAction = if (visuals.withDismissAction) {
            {
                IconButton(onClick = data::dismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
        } else {
            null
        },
    ) { Text(visuals.message) }
}

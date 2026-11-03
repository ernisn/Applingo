package io.github.ernisn.applingo.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.ControlShape

/**
 * Asks before a change that takes a single tap but drops settings: Follow system, removing records,
 * clearing the log. Changing a language needs no asking, it takes a pick and Apply.
 */
@Composable
fun ConfirmDialog(question: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(question, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                shape = ControlShape,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ControlShape) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

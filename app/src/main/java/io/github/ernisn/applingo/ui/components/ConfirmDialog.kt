package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.CardShape

/**
 * Every dialog, built like the rest of the app rather than as Material's AlertDialog: a frosted card
 * like the island and the menus, with the page blurred through it ([frostedFill]), even padding all
 * round, and buttons styled as elsewhere (outlined, or filled for the change itself).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostedDialog(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = CardShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = frostedBorder(),
        ) {
            Column(Modifier.frostedFill(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp), content = content)
        }
    }
}

/**
 * Asks before a change that takes a single tap but drops settings: Follow system, removing records,
 * clearing the log. Changing a language needs no asking, it takes a pick and Apply. Cancel and the
 * change are the same pair of buttons as the language actions ([ActionPair]).
 */
@Composable
fun ConfirmDialog(question: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FrostedDialog(onDismissRequest = onDismiss) {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        ActionPair(
            secondaryLabel = stringResource(R.string.action_cancel),
            onSecondary = onDismiss,
            primaryLabel = confirmLabel,
            onPrimary = {
                onDismiss()
                onConfirm()
            },
        )
    }
}

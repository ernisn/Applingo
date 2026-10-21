package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.ControlShape

/**
 * "Follow system" and "Change language" side by side, equally wide and equally tall even when one
 * label wraps. Used for one app (its detail) and for the apps selected in the list.
 */
@Composable
fun LanguageActions(
    onFollowSystem: (() -> Unit)?,
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        if (onFollowSystem != null) {
            OutlinedButton(
                onClick = onFollowSystem,
                shape = ControlShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(stringResource(R.string.action_reset_to_system), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Button(
            onClick = onChangeLanguage,
            shape = ControlShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Text(stringResource(R.string.action_change_language), textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

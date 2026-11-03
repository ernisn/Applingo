package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.ControlShape
import io.github.ernisn.applingo.ui.theme.TRANSLUCENT_BUTTON_ALPHA

/**
 * "Follow system" and "Change language" side by side (see [ActionPair]). Used for one app (its
 * detail) and for the apps selected in a list.
 */
@Composable
fun LanguageActions(
    onFollowSystem: (() -> Unit)?,
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    changeLanguageModifier: Modifier = Modifier,
) {
    ActionPair(
        secondaryLabel = stringResource(R.string.action_reset_to_system),
        onSecondary = onFollowSystem,
        primaryLabel = stringResource(R.string.action_change_language),
        onPrimary = onChangeLanguage,
        modifier = modifier,
        secondaryEnabled = enabled,
        primaryEnabled = enabled,
        primaryModifier = changeLanguageModifier,
    )
}

/**
 * Two actions side by side, equally wide and equally tall even when one label wraps: the secondary
 * one outlined (left out without [onSecondary]), the main one filled ([translucentButtonColors]).
 */
@Composable
fun ActionPair(
    secondaryLabel: String,
    onSecondary: (() -> Unit)?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryEnabled: Boolean = true,
    primaryEnabled: Boolean = true,
    primaryModifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        if (onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                enabled = secondaryEnabled,
                shape = ControlShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(secondaryLabel, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            shape = ControlShape,
            colors = translucentButtonColors(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = primaryModifier.weight(1f).fillMaxHeight(),
        ) {
            Text(primaryLabel, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/**
 * Every filled button's colours: a little of what lies behind shows through, as through the outlined
 * ones beside them, while the label stays readable (see [TRANSLUCENT_BUTTON_ALPHA]).
 */
@Composable
fun translucentButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = TRANSLUCENT_BUTTON_ALPHA))

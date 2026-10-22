package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.theme.ControlShape

/*
 * While apps are selected in a list, its top bar shows only the count and Select all, and the batch
 * actions sit at the bottom like the detail page's language buttons, so both fit a narrow pane in
 * any language. On a D-pad device the actions sit right under the top bar instead, where Up from
 * the top of the list reaches them (see [isDpadDevice]). Shared by the app list and the Configured
 * list.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(R.plurals.selection_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        actions = {
            TextButton(onClick = onSelectAll, shape = ControlShape) { Text(stringResource(R.string.action_select_all)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        windowInsets = WindowInsets(0),
    )
}

/** The batch actions; disabled while nothing is picked yet (selecting started from a menu). */
@Composable
fun SelectionActionsBar(
    count: Int,
    onFollowSystem: () -> Unit,
    onChangeLanguage: () -> Unit,
    underTopBar: Boolean = false,
) {
    // Under the top bar it continues that bar's color rather than starting a second bar.
    Surface(
        color = if (underTopBar) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        tonalElevation = if (underTopBar) 0.dp else 3.dp,
    ) {
        LanguageActions(
            onFollowSystem = onFollowSystem,
            onChangeLanguage = onChangeLanguage,
            enabled = count > 0,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (underTopBar) 0.dp else 12.dp, bottom = 12.dp),
        )
    }
}

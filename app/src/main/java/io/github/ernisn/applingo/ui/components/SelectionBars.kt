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
 * any language. Shared by the app list and the Configured list.
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

@Composable
fun SelectionActionsBar(onFollowSystem: () -> Unit, onChangeLanguage: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        LanguageActions(
            onFollowSystem = onFollowSystem,
            onChangeLanguage = onChangeLanguage,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

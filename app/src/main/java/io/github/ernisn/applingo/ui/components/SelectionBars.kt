package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        // The header it sits in paints the selection colour (see FrostedHeaderLayout).
        colors = frostedTopAppBarColors(),
        windowInsets = WindowInsets(0),
    )
}

/**
 * The batch actions; disabled while nothing is picked yet (selecting started from a menu). Follow
 * system asks first, as on the detail page.
 */
@Composable
fun SelectionActionsBar(
    count: Int,
    onFollowSystem: () -> Unit,
    onChangeLanguage: () -> Unit,
    underTopBar: Boolean = false,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    if (confirmReset && count > 0) {
        ConfirmDialog(
            question = pluralStringResource(R.plurals.confirm_reset_batch, count, count),
            confirmLabel = stringResource(R.string.action_reset_to_system),
            onConfirm = onFollowSystem,
            onDismiss = { confirmReset = false },
        )
    }
    // Either way inside a frosted bar that paints the background and keeps clear of the system bars:
    // the header under the top bar, or at the bottom the footer in the island's place (see
    // FrostedBarsLayout, HideNavIsland).
    LanguageActions(
        onFollowSystem = { confirmReset = true },
        onChangeLanguage = onChangeLanguage,
        enabled = count > 0,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (underTopBar) 0.dp else 12.dp, bottom = 12.dp),
    )
}

/**
 * For a bar at the bottom of a pane that runs behind the system bar (see safeArea): its background
 * reaches the screen edge, its buttons stay above the system bar, and above the keyboard while open.
 */
@Composable
fun Modifier.bottomBarInsets(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))

package io.github.ernisn.applingo.ui.managed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.UninstalledRecord
import io.github.ernisn.applingo.ui.applist.LocaleBadge
import io.github.ernisn.applingo.ui.components.ConfirmDialog
import io.github.ernisn.applingo.ui.components.FocusWhenPending
import io.github.ernisn.applingo.ui.components.FrostedBarsLayout
import io.github.ernisn.applingo.ui.components.frostedTopAppBarColors
import io.github.ernisn.applingo.ui.components.SelectionTopBar
import io.github.ernisn.applingo.ui.components.appRow
import io.github.ernisn.applingo.ui.components.groupedRowColors
import io.github.ernisn.applingo.ui.components.translucentButtonColors
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.components.rememberRevealer
import io.github.ernisn.applingo.ui.components.revealable
import io.github.ernisn.applingo.ui.HideNavIsland
import io.github.ernisn.applingo.ui.bottomChrome
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.theme.ControlShape

/**
 * Records kept for apps that are no longer installed, in place of the Configured list until Back.
 * They bring an app's languages back if it is reinstalled, so they stay until removed: one by one
 * with the row's delete button, all at once, or picked by long press (or Select apps). Removing
 * several asks first; every removal can be undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UninstalledPane(viewModel: MainViewModel, records: List<UninstalledRecord>, onBack: () -> Unit) {
    val selection by viewModel.uninstalledSelection.packages.collectAsStateWithLifecycle()
    val selectionStarted by viewModel.uninstalledSelection.started.collectAsStateWithLifecycle()
    val selecting = selectionStarted || selection.isNotEmpty()
    val listState = rememberLazyListState()
    val dpad = isDpadDevice()
    // Packages waiting for the user to confirm their removal; from the selection or all of them.
    var confirmRemove by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var removeSelection by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    // Composed later, so it comes first: Back ends the selection before it leaves the list.
    BackHandler(enabled = selecting) { viewModel.uninstalledSelection.clear() }
    // A secondary page: the island makes way.
    HideNavIsland()

    // D-pad focus: on the first control here once shown, and on the first row on screen after
    // Select apps (which replaced the focused button).
    val openFocus = remember { FocusRequester() }
    var opened by rememberSaveable { mutableStateOf(false) }
    FocusWhenPending(!opened, openFocus) { opened = true }
    var selectFocusAt by remember { mutableStateOf<Int?>(null) }
    val selectFocus = remember { FocusRequester() }
    FocusWhenPending(selectFocusAt != null, selectFocus) { selectFocusAt = null }

    confirmRemove?.let { packages ->
        ConfirmDialog(
            question = pluralStringResource(R.plurals.confirm_remove_records, packages.size, packages.size),
            confirmLabel = stringResource(R.string.action_remove_records),
            onConfirm = {
                viewModel.forget(packages, selection = if (removeSelection) viewModel.uninstalledSelection else null)
            },
            onDismiss = { confirmRemove = null },
        )
    }
    fun removeSelected() {
        removeSelection = true
        confirmRemove = selection.toList()
    }
    val removeBar = @Composable { underTopBar: Boolean ->
        // Like the batch actions of the other lists (SelectionActionsBar): the frosted header or
        // footer around it paints the background.
        Button(
            onClick = ::removeSelected,
            enabled = selection.isNotEmpty(),
            shape = ControlShape,
            colors = translucentButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = if (underTopBar) 0.dp else 12.dp, bottom = 12.dp),
        ) { Text(stringResource(R.string.action_remove_records)) }
    }

    Scaffold(
        modifier = Modifier.safeArea(top = false, bottom = false),
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        FrostedBarsLayout(
            footer = if (selecting && !dpad) { { removeBar(false) } } else null,
            modifier = Modifier.padding(padding).fillMaxSize(),
            tint = if (selecting) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
            header = {
                if (selecting) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = viewModel.uninstalledSelection::clear,
                        onSelectAll = { viewModel.uninstalledSelection.selectAll(records.map { it.packageName }) },
                    )
                    if (dpad) removeBar(true)
                } else {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = frostedTopAppBarColors(),
                        title = {
                            Text(
                                text = stringResource(R.string.managed_uninstalled_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.focusRequester(openFocus)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            // The alternative to long press, as in the Configured list's menu.
                            TextButton(
                                onClick = {
                                    selectFocusAt = listState.firstVisibleItemIndex.coerceAtLeast(1)
                                    viewModel.uninstalledSelection.start()
                                },
                                shape = ControlShape,
                            ) { Text(stringResource(R.string.action_select_apps)) }
                        },
                    )
                }
            },
        ) { headerHeight, footerHeight ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = headerHeight, bottom = 16.dp + if (selecting && !dpad) footerHeight else bottomChrome()),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Gone with the last record, while its removal can still be undone.
                if (records.isNotEmpty()) item(key = "info") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = pluralStringResource(R.plurals.managed_uninstalled, records.size, records.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.managed_uninstalled_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        removeSelection = false
                                        confirmRemove = records.map { it.packageName }
                                    },
                                    enabled = !selecting,
                                    shape = ControlShape,
                                ) { Text(stringResource(R.string.action_forget_all)) }
                            }
                        }
                    }
                }
                itemsIndexed(records, key = { _, record -> record.packageName }) { index, record ->
                    val packageName = record.packageName
                    UninstalledItem(
                        record = record,
                        checked = if (selecting) packageName in selection else null,
                        onClick = { if (selecting) viewModel.uninstalledSelection.toggle(packageName) },
                        onLongClick = { viewModel.uninstalledSelection.toggle(packageName) },
                        onRemove = { viewModel.forget(listOf(packageName)) },
                        modifier = if (index + 1 == selectFocusAt) Modifier.focusRequester(selectFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * The app's last known name, its package and the languages kept for it. Where its icon was, a cross
 * on the blank the lists show while an icon loads, so it doesn't read as one still loading. Not a
 * bin, which is the row's button for removing the record.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UninstalledItem(
    record: UninstalledRecord,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revealer = rememberRevealer()
    ListItem(
        modifier = modifier
            .appRow(highlighted = checked == true)
            .revealable(revealer)
            .combinedClickable(
                onClick = {
                    revealer.reveal()
                    onClick()
                },
                onLongClick = {
                    revealer.reveal()
                    onLongClick()
                },
            ),
        colors = groupedRowColors(),
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (checked != null) {
                    Checkbox(checked = checked, onCheckedChange = null)
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        headlineContent = { Text(record.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (record.label != record.packageName) {
            { Text(record.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.locales.isNotEmpty()) {
                    // Neutral, like a language not written yet: nothing is applied while the app is gone.
                    LocaleBadge(record.locales, LocaleStatus.Pending)
                }
                if (checked == null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_forget))
                    }
                }
            }
        },
    )
}

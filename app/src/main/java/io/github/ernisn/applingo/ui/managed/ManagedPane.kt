package io.github.ernisn.applingo.ui.managed

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.ActivityEntry
import io.github.ernisn.applingo.data.ActivityKind
import io.github.ernisn.applingo.shizuku.ShizukuState
import io.github.ernisn.applingo.ui.AppIcon
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.theme.ControlShape
import io.github.ernisn.applingo.ui.ManagedApp
import io.github.ernisn.applingo.ui.ManagedState
import io.github.ernisn.applingo.ui.applist.LocaleBadge
import io.github.ernisn.applingo.ui.components.PackageLine
import io.github.ernisn.applingo.ui.components.SegmentedTabRow
import io.github.ernisn.applingo.ui.components.SelectionActionsBar
import io.github.ernisn.applingo.ui.components.SelectionTopBar
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.picker.BatchLocalePickerDialog

private const val MAX_ACTIVITY_SHOWN = 100

private enum class ManagedTab(val label: Int) {
    Apps(R.string.managed_section_apps),
    Activity(R.string.managed_section_activity),
}

/**
 * Every app Applingo keeps a language for with the result of the last check, and recent activity, as
 * two tabs like the app list's: one list after the other meant scrolling past every app to reach the
 * activity. Long press selects apps for a batch change, as in the app list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedPane(viewModel: MainViewModel, selectedPackage: String?, onOpenApp: (String) -> Unit) {
    val state by viewModel.managedState.collectAsStateWithLifecycle()
    val selection by viewModel.managedSelection.packages.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ManagedTab.Apps) }
    // One per tab, so each keeps its scroll position while the other is shown.
    val appsListState = rememberLazyListState()
    val activityListState = rememberLazyListState()

    val selecting = selection.isNotEmpty()
    var showBatchPicker by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { viewModel.managedSelection.clear() }

    if (showBatchPicker && selecting) {
        BatchLocalePickerDialog(
            viewModel = viewModel,
            count = selection.size,
            onApply = { chain -> viewModel.applyToSelection(viewModel.managedSelection, chain) },
            onDismiss = { showBatchPicker = false },
        )
    }

    Scaffold(
        modifier = Modifier.safeArea(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = viewModel.managedSelection::clear,
                    // Apps that aren't installed have no language to change.
                    onSelectAll = {
                        viewModel.managedSelection.selectAll(state.apps.filter { it.row != null }.map { it.packageName })
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.managed_title)) },
                    actions = {
                        if (tab == ManagedTab.Activity && state.activity.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearActivity, shape = ControlShape) {
                                Text(stringResource(R.string.action_clear_activity))
                            }
                        }
                    },
                    windowInsets = WindowInsets(0),
                )
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionActionsBar(
                    onFollowSystem = { viewModel.applyToSelection(viewModel.managedSelection, emptyList()) },
                    onChangeLanguage = { showBatchPicker = true },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ShizukuStatusCard(
                state = state.shizuku,
                onRequestPermission = viewModel::requestShizukuPermission,
                onRetry = viewModel::retryShizuku,
                shizukuLaunchIntent = viewModel::shizukuLaunchIntent,
            )
            SegmentedTabRow(
                labels = ManagedTab.entries.map { stringResource(it.label) },
                selected = tab.ordinal,
                onSelect = { tab = ManagedTab.entries[it] },
                // The selection lives in the apps tab; it is finished or dropped there first.
                enabled = !selecting,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            when (tab) {
                ManagedTab.Apps -> LazyColumn(
                    state = appsListState,
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "summary") { SummaryCard(state, onCheckNow = viewModel::checkNow) }
                    if (state.apps.isEmpty()) {
                        item(key = "empty") { Hint(stringResource(R.string.managed_empty)) }
                    }
                    items(state.apps, key = { it.packageName }) { managed ->
                        val packageName = managed.packageName
                        ManagedAppItem(
                            managed = managed,
                            selected = packageName == selectedPackage,
                            checked = if (selecting && managed.row != null) packageName in selection else null,
                            onClick = {
                                if (selecting) viewModel.managedSelection.toggle(packageName) else onOpenApp(packageName)
                            },
                            onLongClick = { viewModel.managedSelection.toggle(packageName) },
                            onForget = { viewModel.forget(packageName) },
                        )
                    }
                }
                ManagedTab.Activity -> LazyColumn(
                    state = activityListState,
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.activity.isEmpty()) {
                        item(key = "empty") { Hint(stringResource(R.string.managed_activity_empty)) }
                    }
                    items(state.activity.take(MAX_ACTIVITY_SHOWN)) { ActivityItem(it) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: ManagedState, onCheckNow: () -> Unit) {
    val attention = state.apps.count { it.status == LocaleStatus.Mismatch || it.row == null }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.managed_summary_count, state.apps.size, state.apps.size),
                style = MaterialTheme.typography.titleMedium,
            )
            if (attention > 0) {
                Text(
                    text = pluralStringResource(R.plurals.managed_summary_attention, attention, attention),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val check = state.lastCheck
            Text(
                text = if (check == null) {
                    stringResource(R.string.managed_last_check_never)
                } else {
                    stringResource(
                        R.string.managed_last_check,
                        relativeTime(check.time),
                        check.repaired,
                        check.adopted,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                // Filled: the tonal container color is too close to the card's in the light theme.
                Button(
                    onClick = onCheckNow,
                    enabled = !state.checking && state.shizuku is ShizukuState.Connected,
                    shape = ControlShape,
                ) { Text(stringResource(R.string.action_check_now)) }
            }
        }
    }
}

/** Like an app list row; an app that isn't installed can't be opened or selected, only forgotten. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManagedAppItem(
    managed: ManagedApp,
    selected: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onForget: () -> Unit,
) {
    val row = managed.row
    val highlighted = selected || checked == true
    ListItem(
        modifier = if (row != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier,
        colors = if (highlighted) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            when {
                checked != null -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
                row != null -> AppIcon(row.app.packageName, row.app.lastUpdateTime, dimmed = !row.app.isEnabled)
                else -> Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            }
        },
        headlineContent = { Text(managed.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (row != null) {
                PackageLine(row.app, favorite = row.favorite)
            } else {
                Text(
                    text = stringResource(R.string.managed_not_installed) + " · " + managed.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            if (row != null) {
                if (managed.locales.isNotEmpty()) LocaleBadge(managed.locales, managed.status, onHighlight = highlighted)
            } else {
                IconButton(onClick = onForget) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_forget))
                }
            }
        },
    )
}

@Composable
private fun ActivityItem(entry: ActivityEntry) {
    val (textRes, color) = when (entry.kind) {
        ActivityKind.Applied -> R.string.activity_applied to MaterialTheme.colorScheme.primary
        ActivityKind.Reset -> R.string.activity_reset to MaterialTheme.colorScheme.outline
        ActivityKind.Repaired -> R.string.activity_repaired to MaterialTheme.colorScheme.tertiary
        ActivityKind.Mismatch -> R.string.activity_mismatch to MaterialTheme.colorScheme.error
        ActivityKind.Adopted -> R.string.activity_adopted to MaterialTheme.colorScheme.secondary
        ActivityKind.Imported -> R.string.activity_imported to MaterialTheme.colorScheme.secondary
        ActivityKind.Failed -> R.string.activity_failed to MaterialTheme.colorScheme.error
    }
    val detail = when (entry.kind) {
        ActivityKind.Failed -> stringResource(textRes, entry.message.orEmpty())
        ActivityKind.Repaired, ActivityKind.Mismatch -> stringResource(textRes, chain(entry.from), chain(entry.to))
        ActivityKind.Reset -> stringResource(textRes)
        else -> stringResource(textRes, chain(entry.to))
    }
    ListItem(
        leadingContent = { Box(Modifier.size(10.dp).background(color, CircleShape)) },
        headlineContent = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Text(
                relativeTime(entry.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun chain(locales: List<String>): String =
    if (locales.isEmpty()) stringResource(R.string.locale_follow_system)
    else locales.joinToString(", ") { LocaleTags.nativeName(it) }

@Composable
private fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.time_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

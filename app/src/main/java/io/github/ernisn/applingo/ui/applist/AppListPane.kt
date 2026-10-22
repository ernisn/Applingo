package io.github.ernisn.applingo.ui.applist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.AppSort
import io.github.ernisn.applingo.data.AppTypeFilter
import io.github.ernisn.applingo.data.ListPreferences
import io.github.ernisn.applingo.ui.AppIcon
import io.github.ernisn.applingo.ui.AppRow
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.components.AppTypeFilterRow
import io.github.ernisn.applingo.ui.components.PackageLine
import io.github.ernisn.applingo.ui.components.SelectionActionsBar
import io.github.ernisn.applingo.ui.components.SelectionTopBar
import io.github.ernisn.applingo.ui.components.SearchField
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.picker.BatchLocalePickerDialog
import io.github.ernisn.applingo.ui.theme.FieldShape

@Composable
fun AppListPane(
    viewModel: MainViewModel,
    selectedPackage: String?,
    onOpenApp: (String) -> Unit,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val selection by viewModel.appSelection.packages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // The field owns its text: echoing it back through the (asynchronously computed) list state lags
    // behind fast typing and IME composition, which scrambles characters and resets the cursor.
    var queryText by rememberSaveable { mutableStateOf("") }

    // A new query, filter or order is a new list: start at its top instead of keeping the scroll
    // anchored to whichever app happened to be first before.
    LaunchedEffect(state.query, state.typeFilter, state.preferences) {
        listState.scrollToItem(0)
    }

    val selecting = selection.isNotEmpty()
    var showBatchPicker by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { viewModel.appSelection.clear() }

    if (showBatchPicker && selecting) {
        BatchLocalePickerDialog(
            viewModel = viewModel,
            count = selection.size,
            onApply = { chain -> viewModel.applyToSelection(viewModel.appSelection, chain) },
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
                    onClose = viewModel.appSelection::clear,
                    onSelectAll = { viewModel.appSelection.selectAll(state.rows.map { it.app.packageName }) },
                )
            } else {
                SearchHeader(
                    query = queryText,
                    onQueryChange = { value ->
                        queryText = value
                        viewModel.onQueryChange(value)
                    },
                    preferences = state.preferences,
                    filtersApply = state.typeFilter != AppTypeFilter.Favorites,
                    onPreferencesChange = { next -> viewModel.updatePreferences { next } },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionActionsBar(
                    onFollowSystem = { viewModel.applyToSelection(viewModel.appSelection, emptyList()) },
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
            // A search covers every app, which the row shows by switching to All while it is disabled.
            val searching = state.query.isNotBlank()
            AppTypeFilterRow(
                selected = if (searching) AppTypeFilter.All else state.typeFilter,
                onSelect = viewModel::setTypeFilter,
                enabled = !searching,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(
                                when {
                                    searching -> R.string.list_no_results
                                    state.typeFilter == AppTypeFilter.Favorites -> R.string.list_no_favorites
                                    else -> R.string.list_empty
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(state.rows, key = { it.app.packageName }) { row ->
                            val packageName = row.app.packageName
                            AppListItem(
                                row = row,
                                selected = packageName == selectedPackage,
                                checked = if (selecting) packageName in selection else null,
                                onClick = {
                                    if (selecting) viewModel.appSelection.toggle(packageName) else onOpenApp(packageName)
                                },
                                onLongClick = { viewModel.appSelection.toggle(packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The search field, with the menu for order and the filters that apply on top of the tabs. The
 * filters are greyed out where they don't apply ([filtersApply] false: the Favorites tab).
 */
@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    preferences: ListPreferences,
    filtersApply: Boolean,
    onPreferencesChange: (ListPreferences) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.search_hint),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.action_sort))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = FieldShape) {
                AppSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        leadingIcon = { RadioButton(selected = option == preferences.sort, onClick = null) },
                        onClick = {
                            onPreferencesChange(preferences.copy(sort = option))
                            menuOpen = false
                        },
                    )
                }
                CheckItem(R.string.sort_reverse, preferences.reverseOrder) {
                    onPreferencesChange(preferences.copy(reverseOrder = it))
                }
                HorizontalDivider()
                CheckItem(R.string.filter_launchable_only, preferences.launchableOnly, enabled = filtersApply) {
                    onPreferencesChange(preferences.copy(launchableOnly = it))
                }
                CheckItem(R.string.filter_enabled_only, preferences.enabledOnly, enabled = filtersApply) {
                    onPreferencesChange(preferences.copy(enabledOnly = it))
                }
            }
        }
    }
}

@Composable
private fun CheckItem(label: Int, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    row: AppRow,
    selected: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected || checked == true) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            if (checked != null) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            } else {
                AppIcon(row.app.packageName, row.app.lastUpdateTime, dimmed = !row.app.isEnabled)
            }
        },
        headlineContent = { Text(row.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { PackageLine(row.app, favorite = row.favorite) },
        trailingContent = if (row.locales.isNotEmpty()) {
            { LocaleBadge(row.locales, row.status, onHighlight = selected || checked == true) }
        } else {
            null
        },
    )
}

@Composable
internal fun LocaleBadge(locales: List<String>, status: LocaleStatus, onHighlight: Boolean = false) {
    val (container, content) = when (status) {
        LocaleStatus.Mismatch -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LocaleStatus.Pending -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        // Highlighted rows already use secondaryContainer; keep the badge distinguishable on them.
        else -> if (onHighlight) {
            MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (status == LocaleStatus.Mismatch) Icon(Icons.Filled.Warning, null, Modifier.size(14.dp))
            Text(
                text = LocaleTags.nativeName(locales.first()) + if (locales.size > 1) " +${locales.size - 1}" else "",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

private fun AppSort.labelRes(): Int = when (this) {
    AppSort.Name -> R.string.sort_name
    AppSort.RecentlySet -> R.string.sort_recently_set
    AppSort.RecentlyUpdated -> R.string.sort_recently_updated
    AppSort.RecentlyInstalled -> R.string.sort_recently_installed
}

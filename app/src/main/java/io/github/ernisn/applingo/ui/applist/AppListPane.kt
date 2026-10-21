package io.github.ernisn.applingo.ui.applist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.AppSort
import io.github.ernisn.applingo.data.AppTypeFilter
import io.github.ernisn.applingo.ui.AppIcon
import io.github.ernisn.applingo.ui.AppRow
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.components.focusableOnlyAfterTouch
import io.github.ernisn.applingo.ui.picker.BatchLocalePickerDialog

@Composable
fun AppListPane(
    viewModel: MainViewModel,
    selectedPackage: String?,
    onOpenApp: (String) -> Unit,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // A new query, filter or order is a new list: start at its top instead of keeping the scroll
    // anchored to whichever app happened to be first before.
    LaunchedEffect(state.query, state.preferences) {
        listState.scrollToItem(0)
    }

    val selecting = state.selection.isNotEmpty()
    var showBatchPicker by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    if (showBatchPicker && selecting) {
        BatchLocalePickerDialog(viewModel, count = state.selection.size, onDismiss = { showBatchPicker = false })
    }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = state.selection.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAll(state.rows.map { it.app.packageName }) },
                    onChangeLanguage = { showBatchPicker = true },
                    onFollowSystem = { viewModel.applyToSelection(emptyList()) },
                )
            } else {
                SearchHeader(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    sort = state.preferences.sort,
                    withLocaleFirst = state.preferences.withLocaleFirst,
                    onSortChange = { sort -> viewModel.updatePreferences { it.copy(sort = sort) } },
                    onWithLocaleFirstChange = { value -> viewModel.updatePreferences { it.copy(withLocaleFirst = value) } },
                    onRefresh = viewModel::refresh,
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
            FilterRow(
                typeFilter = state.preferences.typeFilter,
                typeFilterEnabled = state.query.isBlank(),
                onlyWithLocale = state.preferences.onlyWithLocale,
                onTypeFilterChange = { filter -> viewModel.updatePreferences { it.copy(typeFilter = filter) } },
                onOnlyWithLocaleChange = { value -> viewModel.updatePreferences { it.copy(onlyWithLocale = value) } },
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
                    state.rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(if (state.query.isBlank()) R.string.list_empty else R.string.list_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                checked = if (selecting) packageName in state.selection else null,
                                onClick = {
                                    if (selecting) viewModel.toggleSelection(packageName) else onOpenApp(packageName)
                                },
                                onLongClick = { viewModel.toggleSelection(packageName) },
                            )
                        }
                        item { Spacer(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: AppSort,
    withLocaleFirst: Boolean,
    onSortChange: (AppSort) -> Unit,
    onWithLocaleFirstChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusableOnlyAfterTouch(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.action_sort))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AppSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                            onClick = {
                                onSortChange(option)
                                menuOpen = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_with_locale_first)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (withLocaleFirst) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        },
                        onClick = { onWithLocaleFirstChange(!withLocaleFirst) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_refresh)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            onRefresh()
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    typeFilter: AppTypeFilter,
    typeFilterEnabled: Boolean,
    onlyWithLocale: Boolean,
    onTypeFilterChange: (AppTypeFilter) -> Unit,
    onOnlyWithLocaleChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        AppTypeFilter.entries.forEach { filter ->
            FilterChip(
                selected = typeFilterEnabled && filter == typeFilter || !typeFilterEnabled && filter == AppTypeFilter.All,
                onClick = { onTypeFilterChange(filter) },
                enabled = typeFilterEnabled,
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
        VerticalDivider(Modifier.height(24.dp))
        FilterChip(
            selected = onlyWithLocale,
            onClick = { onOnlyWithLocaleChange(!onlyWithLocale) },
            label = { Text(stringResource(R.string.filter_only_with_locale)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onChangeLanguage: () -> Unit,
    onFollowSystem: () -> Unit,
) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        actions = {
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.action_select_all)) }
            TextButton(onClick = onFollowSystem) { Text(stringResource(R.string.action_reset_to_system)) }
            IconButton(onClick = onChangeLanguage) {
                Icon(painterResource(R.drawable.ic_translate), contentDescription = stringResource(R.string.action_change_language))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                AppIcon(row.app.packageName, row.app.lastUpdateTime)
            }
        },
        headlineContent = { Text(row.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val prefix = when {
                !row.app.isEnabled -> stringResource(R.string.app_badge_disabled) + " · "
                row.app.isSystem -> stringResource(R.string.app_badge_system) + " · "
                else -> ""
            }
            Text(
                text = prefix + row.app.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
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

private fun AppTypeFilter.labelRes(): Int = when (this) {
    AppTypeFilter.Launchable -> R.string.filter_launchable
    AppTypeFilter.User -> R.string.filter_user
    AppTypeFilter.System -> R.string.filter_system
    AppTypeFilter.All -> R.string.filter_all
}

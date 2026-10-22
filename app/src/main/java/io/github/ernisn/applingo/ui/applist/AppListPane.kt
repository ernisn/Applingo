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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import io.github.ernisn.applingo.ui.components.FocusOnReturnRequest
import io.github.ernisn.applingo.ui.components.FocusRowOnReturn
import io.github.ernisn.applingo.ui.components.FocusWhenPending
import io.github.ernisn.applingo.ui.components.PackageLine
import io.github.ernisn.applingo.ui.components.SelectionActionsBar
import io.github.ernisn.applingo.ui.components.SelectionTopBar
import io.github.ernisn.applingo.ui.components.SearchField
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.picker.BatchLocalePickerDialog
import io.github.ernisn.applingo.ui.theme.FieldShape

@Composable
fun AppListPane(
    viewModel: MainViewModel,
    selectedPackage: String?,
    focusOnReturn: FocusOnReturnRequest?,
    onOpenApp: (String) -> Unit,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val selection by viewModel.appSelection.packages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val returnTo = remember { focusOnReturn }
    val returnFocus = remember { FocusRequester() }
    // Select apps from the menu: the menu went with the search bar, so focus goes to the first row on
    // screen (see FocusWhenPending).
    var selectFocusAt by remember { mutableStateOf<Int?>(null) }
    val selectFocus = remember { FocusRequester() }
    FocusWhenPending(selectFocusAt != null, selectFocus) { selectFocusAt = null }
    // The field owns its text: echoing it back through the (asynchronously computed) list state lags
    // behind fast typing and IME composition, which scrambles characters and resets the cursor.
    var queryText by rememberSaveable { mutableStateOf("") }

    // A new query, filter or order is a new list: start at its top instead of keeping the scroll
    // anchored to whichever app happened to be first before. Only on an actual change: one pane at a
    // time, the list also comes back after an app's detail, and it jumped to the top every time.
    val listKey = "${state.query}\n${state.typeFilter}\n${state.preferences}"
    var shownListKey by rememberSaveable { mutableStateOf(listKey) }
    LaunchedEffect(listKey) {
        if (listKey != shownListKey) {
            shownListKey = listKey
            listState.scrollToItem(0)
        }
    }
    FocusRowOnReturn(
        index = returnTo?.let { request -> state.rows.indexOfFirst { it.app.packageName == request.packageName } }
            ?.takeIf { it >= 0 },
        listState = listState,
        requester = returnFocus,
        onDone = { returnTo?.onDone() },
    )

    val selectionStarted by viewModel.appSelection.started.collectAsStateWithLifecycle()
    val selecting = selectionStarted || selection.isNotEmpty()
    var showBatchPicker by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { viewModel.appSelection.clear() }
    val dpad = isDpadDevice()
    val batchActions = @Composable { underTopBar: Boolean ->
        SelectionActionsBar(
            count = selection.size,
            onFollowSystem = { viewModel.applyToSelection(viewModel.appSelection, emptyList()) },
            onChangeLanguage = { showBatchPicker = true },
            underTopBar = underTopBar,
        )
    }

    if (showBatchPicker && selection.isNotEmpty()) {
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
                Column {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = viewModel.appSelection::clear,
                        onSelectAll = { viewModel.appSelection.selectAll(state.rows.map { it.app.packageName }) },
                    )
                    if (dpad) batchActions(true)
                }
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
                    onSelectApps = {
                        selectFocusAt = listState.firstVisibleItemIndex
                        viewModel.appSelection.start()
                    },
                    onRescan = if (dpad) viewModel::refresh else null,
                )
            }
        },
        bottomBar = {
            if (selecting && !dpad) batchActions(false)
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
            val listContent = @Composable {
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
                        itemsIndexed(state.rows, key = { _, row -> row.app.packageName }) { index, row ->
                            val packageName = row.app.packageName
                            AppListItem(
                                row = row,
                                modifier = when {
                                    packageName == returnTo?.packageName -> Modifier.focusRequester(returnFocus)
                                    index == selectFocusAt -> Modifier.focusRequester(selectFocus)
                                    else -> Modifier
                                },
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
            // Pull to refresh needs touch: a D-pad moving up at the top of the list set it off halfway
            // and left it stuck there. D-pad devices rescan from the menu instead.
            if (dpad) {
                Box(Modifier.fillMaxSize()) {
                    listContent()
                    if (state.refreshing) {
                        CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) { listContent() }
            }
        }
    }
}

/**
 * The search field, with the menu for order and the filters that apply on top of the tabs. The
 * filters are greyed out where they don't apply ([filtersApply] false: the Favorites tab). The menu
 * ends with the alternatives to gestures: Rescan apps for pull to refresh ([onRescan], D-pad devices
 * only) and Select apps for long press (a remote's OK button may not long-press).
 */
@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    preferences: ListPreferences,
    filtersApply: Boolean,
    onPreferencesChange: (ListPreferences) -> Unit,
    onSelectApps: () -> Unit,
    onRescan: (() -> Unit)?,
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
                HorizontalDivider()
                // The empty icons line the labels up with the radio buttons and checkboxes above.
                if (onRescan != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rescan_apps)) },
                        leadingIcon = { Spacer(Modifier.size(24.dp)) },
                        onClick = {
                            menuOpen = false
                            onRescan()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_select_apps)) },
                    leadingIcon = { Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        menuOpen = false
                        onSelectApps()
                    },
                )
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
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
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

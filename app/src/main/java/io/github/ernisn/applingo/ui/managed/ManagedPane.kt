package io.github.ernisn.applingo.ui.managed

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.ActivityEntry
import io.github.ernisn.applingo.data.ActivityKind
import io.github.ernisn.applingo.data.ListPreferences
import io.github.ernisn.applingo.shizuku.ShizukuState
import io.github.ernisn.applingo.ui.AppIcon
import io.github.ernisn.applingo.ui.AppRow
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.ManagedState
import io.github.ernisn.applingo.ui.applist.ActionItem
import io.github.ernisn.applingo.ui.applist.LocaleBadge
import io.github.ernisn.applingo.ui.applist.SortMenuItems
import io.github.ernisn.applingo.ui.components.ConfirmDialog
import io.github.ernisn.applingo.ui.components.FocusOnReturn
import io.github.ernisn.applingo.ui.components.FocusOnReturnRequest
import io.github.ernisn.applingo.ui.components.FocusRowOnReturn
import io.github.ernisn.applingo.ui.components.FocusWhenPending
import io.github.ernisn.applingo.ui.components.FrostedBarsLayout
import io.github.ernisn.applingo.ui.components.FrostedPullToRefresh
import io.github.ernisn.applingo.ui.components.PackageLine
import io.github.ernisn.applingo.ui.components.SearchField
import io.github.ernisn.applingo.ui.components.SegmentedTabRow
import io.github.ernisn.applingo.ui.components.SelectionActionsBar
import io.github.ernisn.applingo.ui.components.SelectionTopBar
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.components.rememberRevealer
import io.github.ernisn.applingo.ui.components.revealable
import io.github.ernisn.applingo.ui.picker.BatchLocalePickerDialog
import io.github.ernisn.applingo.ui.HideNavIsland
import io.github.ernisn.applingo.ui.bottomChrome
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.theme.ControlShape
import io.github.ernisn.applingo.ui.theme.FieldShape
import kotlinx.coroutines.delay

private const val MAX_ACTIVITY_SHOWN = 100
private const val UNINSTALLED_CLOSE_DELAY_MS = 500L

private enum class ManagedTab(val label: Int) {
    Apps(R.string.managed_section_apps),
    Activity(R.string.managed_section_activity),
}

/**
 * Every installed app Applingo keeps a language for with the result of the last check, and recent
 * activity, as two tabs like the app list's: one list after the other meant scrolling past every app
 * to reach the activity. The search field on top filters whichever tab is shown (the log too), and
 * pull to refresh checks now. Long press (or Select apps) selects apps for a batch change, as in the
 * app list.
 *
 * Records of apps that are no longer installed sit apart, behind an entry in the summary card that
 * shows only while there are any (see [UninstalledPane]): they matter less than the apps on the
 * device, and mixed in with them they read as apps needing attention.
 */
@Composable
fun ManagedPane(
    viewModel: MainViewModel,
    selectedPackage: String?,
    focusOnReturn: FocusOnReturnRequest?,
    onOpenApp: (String) -> Unit,
) {
    val state by viewModel.managedState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ManagedTab.Apps) }
    // One per tab, so each keeps its scroll position while the other is shown, and kept while the
    // uninstalled apps are.
    val appsListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    // The field owns its text, as in the app list; the view model may have lost it with the process.
    var queryText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.onManagedQueryChange(queryText) }

    var showUninstalled by rememberSaveable { mutableStateOf(false) }
    // Closes by itself once the last record is gone, after the chance to undo that: Undo brings the
    // rows back in place, and the selection they were removed from. With a moment's grace, as the
    // snackbar leaves on Undo a little before the records are back.
    val undoable by viewModel.undoableChange.collectAsStateWithLifecycle()
    val noRecordsLeft = showUninstalled && state.loaded && state.uninstalled.isEmpty() && undoable == null
    LaunchedEffect(noRecordsLeft) {
        if (noRecordsLeft) {
            delay(UNINSTALLED_CLOSE_DELAY_MS)
            showUninstalled = false
        }
    }
    // D-pad focus goes back to the entry that opened them.
    val uninstalledEntryFocus = remember { FocusRequester() }
    var uninstalledWasShown by rememberSaveable { mutableStateOf(false) }
    if (showUninstalled) uninstalledWasShown = true

    if (showUninstalled) {
        UninstalledPane(viewModel, state.uninstalled, onBack = { showUninstalled = false })
        return
    }
    FocusOnReturn(returned = uninstalledWasShown, uninstalledEntryFocus) { uninstalledWasShown = false }

    ConfiguredPane(
        viewModel = viewModel,
        state = state,
        tab = tab,
        onTabChange = { tab = it },
        query = queryText,
        onQueryChange = { value ->
            queryText = value
            viewModel.onManagedQueryChange(value)
        },
        appsListState = appsListState,
        activityListState = activityListState,
        selectedPackage = selectedPackage,
        focusOnReturn = focusOnReturn,
        onOpenApp = onOpenApp,
        onShowUninstalled = { showUninstalled = true },
        uninstalledEntryFocus = uninstalledEntryFocus,
    )
}

@Composable
private fun ConfiguredPane(
    viewModel: MainViewModel,
    state: ManagedState,
    tab: ManagedTab,
    onTabChange: (ManagedTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    appsListState: LazyListState,
    activityListState: LazyListState,
    selectedPackage: String?,
    focusOnReturn: FocusOnReturnRequest?,
    onOpenApp: (String) -> Unit,
    onShowUninstalled: () -> Unit,
    uninstalledEntryFocus: FocusRequester,
) {
    val selection by viewModel.managedSelection.packages.collectAsStateWithLifecycle()
    val searching = state.query.isNotBlank()
    // The summary card leads the apps tab, except in search results.
    val firstRow = if (searching) 0 else 1
    val returnTo = remember { focusOnReturn }
    val returnFocus = remember { FocusRequester() }
    // Select apps: the menu went with the search bar, so focus goes to the first app row on screen
    // (see FocusWhenPending).
    var selectFocusAt by remember { mutableStateOf<Int?>(null) }
    val selectFocus = remember { FocusRequester() }
    FocusWhenPending(selectFocusAt != null, selectFocus) { selectFocusAt = null }
    FocusRowOnReturn(
        index = returnTo?.let { request -> state.apps.indexOfFirst { it.app.packageName == request.packageName } }
            ?.takeIf { it >= 0 && tab == ManagedTab.Apps }?.let { it + firstRow },
        listState = appsListState,
        requester = returnFocus,
        onDone = { returnTo?.onDone() },
    )

    // A new query or order is a new list: start at its top (only on an actual change, as in the
    // app list).
    val listKey = "${state.query}\n${state.preferences}"
    var shownListKey by rememberSaveable { mutableStateOf(listKey) }
    LaunchedEffect(listKey) {
        if (listKey != shownListKey) {
            shownListKey = listKey
            appsListState.scrollToItem(0)
            activityListState.scrollToItem(0)
        }
    }

    val selectionStarted by viewModel.managedSelection.started.collectAsStateWithLifecycle()
    val selecting = selectionStarted || selection.isNotEmpty()
    var showBatchPicker by rememberSaveable { mutableStateOf(false) }
    var confirmClearLog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { viewModel.managedSelection.clear() }
    HideNavIsland(selecting)
    val dpad = isDpadDevice()
    // The lists run on behind the island and the system bar; their ends clear them (or end above
    // the batch actions while selecting).
    val listEnd = 16.dp + if (selecting && !dpad) 0.dp else bottomChrome()
    val batchActions = @Composable { underTopBar: Boolean ->
        SelectionActionsBar(
            count = selection.size,
            onFollowSystem = { viewModel.applyToSelection(viewModel.managedSelection, emptyList()) },
            onChangeLanguage = { showBatchPicker = true },
            underTopBar = underTopBar,
        )
    }

    if (showBatchPicker && selection.isNotEmpty()) {
        BatchLocalePickerDialog(
            viewModel = viewModel,
            count = selection.size,
            onApply = { chain -> viewModel.applyToSelection(viewModel.managedSelection, chain) },
            onDismiss = { showBatchPicker = false },
        )
    }
    if (confirmClearLog) {
        ConfirmDialog(
            question = stringResource(R.string.confirm_clear_activity),
            confirmLabel = stringResource(R.string.action_clear_activity),
            onConfirm = viewModel::clearActivity,
            onDismiss = { confirmClearLog = false },
        )
    }

    Scaffold(
        modifier = Modifier.safeArea(top = false, bottom = false),
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        // Everything above the lists stays put and they scroll on behind it, frosted.
        FrostedBarsLayout(
            footer = if (selecting && !dpad) { { batchActions(false) } } else null,
            modifier = Modifier.padding(padding).fillMaxSize(),
            tint = if (selecting) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
            header = {
                if (selecting) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = viewModel.managedSelection::clear,
                        onSelectAll = { viewModel.managedSelection.selectAll(state.apps.map { it.app.packageName }) },
                    )
                    if (dpad) batchActions(true)
                } else {
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(if (tab == ManagedTab.Apps) R.string.search_hint else R.string.activity_search_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        when (tab) {
                            ManagedTab.Apps -> AppsMenu(
                                state = state,
                                onPreferencesChange = { viewModel.updateManagedOrder(it.sort, it.reverseOrder) },
                                onSelectApps = {
                                    selectFocusAt = appsListState.firstVisibleItemIndex.coerceAtLeast(firstRow)
                                    viewModel.managedSelection.start()
                                },
                            )
                            ManagedTab.Activity -> if (state.hasActivity) {
                                IconButton(onClick = { confirmClearLog = true }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_clear_log))
                                }
                            }
                        }
                    }
                }
                ShizukuStatusCard(
                    state = state.shizuku,
                    onRequestPermission = viewModel::requestShizukuPermission,
                    onRetry = viewModel::retryShizuku,
                    shizukuLaunchIntent = viewModel::shizukuLaunchIntent,
                )
                SegmentedTabRow(
                    labels = ManagedTab.entries.map { stringResource(it.label) },
                    selected = tab.ordinal,
                    onSelect = { onTabChange(ManagedTab.entries[it]) },
                    // The selection lives in the apps tab; it is finished or dropped there first. A
                    // search leaves both tabs open: it filters the log as well.
                    enabled = !selecting,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            },
        ) { headerHeight, footerHeight ->
            val listPadding = PaddingValues(top = headerHeight, bottom = listEnd + footerHeight)
            val listContent = @Composable {
                when (tab) {
                    ManagedTab.Apps -> LazyColumn(
                        state = appsListState,
                        contentPadding = listPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (!searching) {
                            item(key = "summary") {
                                SummaryCard(
                                    state = state,
                                    onCheckNow = viewModel::checkNow,
                                    onShowUninstalled = onShowUninstalled,
                                    uninstalledEntryFocus = uninstalledEntryFocus,
                                )
                            }
                        }
                        if (state.apps.isEmpty()) {
                            item(key = "empty") {
                                Hint(stringResource(if (searching) R.string.list_no_results else R.string.managed_empty))
                            }
                        }
                        itemsIndexed(state.apps, key = { _, row -> row.app.packageName }) { index, row ->
                            val packageName = row.app.packageName
                            ManagedAppItem(
                                row = row,
                                modifier = when {
                                    packageName == returnTo?.packageName -> Modifier.focusRequester(returnFocus)
                                    index + firstRow == selectFocusAt -> Modifier.focusRequester(selectFocus)
                                    else -> Modifier
                                },
                                selected = packageName == selectedPackage,
                                checked = if (selecting) packageName in selection else null,
                                onClick = {
                                    if (selecting) viewModel.managedSelection.toggle(packageName) else onOpenApp(packageName)
                                },
                                onLongClick = { viewModel.managedSelection.toggle(packageName) },
                            )
                        }
                    }
                    ManagedTab.Activity -> LazyColumn(
                        state = activityListState,
                        contentPadding = listPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.activity.isEmpty()) {
                            item(key = "empty") {
                                Hint(stringResource(if (searching) R.string.list_no_results else R.string.managed_activity_empty))
                            }
                        }
                        items(state.activity.take(MAX_ACTIVITY_SHOWN)) { ActivityItem(it) }
                    }
                }
            }
            // Pull to refresh checks now; D-pad devices use the summary card's button instead (see
            // AppListPane for why they have none).
            if (dpad) {
                listContent()
            } else {
                FrostedPullToRefresh(
                    refreshing = state.checking,
                    onRefresh = viewModel::checkNow,
                    headerHeight = headerHeight,
                ) { listContent() }
            }
        }
    }
}

/** Order of the apps tab, and Select apps for long press, as in the app list's menu (it has no filters). */
@Composable
private fun AppsMenu(state: ManagedState, onPreferencesChange: (ListPreferences) -> Unit, onSelectApps: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = FieldShape) {
            SortMenuItems(preferences = state.preferences, onChange = onPreferencesChange, onPicked = { menuOpen = false })
            if (state.configuredCount > 0) {
                HorizontalDivider()
                ActionItem(R.string.action_select_apps) {
                    menuOpen = false
                    onSelectApps()
                }
            }
        }
    }
}

/**
 * Counts, the last check and Check now; below them, while there are any, the entry to the records of
 * uninstalled apps.
 */
@Composable
private fun SummaryCard(
    state: ManagedState,
    onCheckNow: () -> Unit,
    onShowUninstalled: () -> Unit,
    uninstalledEntryFocus: FocusRequester,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.managed_summary_count, state.configuredCount, state.configuredCount),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.mismatchCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.managed_summary_attention, state.mismatchCount, state.mismatchCount),
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
        if (state.uninstalled.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(uninstalledEntryFocus)
                    .clickable(onClick = onShowUninstalled)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.managed_uninstalled, state.uninstalled.size, state.uninstalled.size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Like an app list row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManagedAppItem(
    row: AppRow,
    selected: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlighted = selected || checked == true
    val revealer = rememberRevealer()
    ListItem(
        modifier = modifier
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
        colors = if (highlighted) {
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
            { LocaleBadge(row.locales, row.status, onHighlight = highlighted) }
        } else {
            null
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
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

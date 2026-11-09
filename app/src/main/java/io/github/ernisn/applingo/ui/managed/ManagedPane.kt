package io.github.ernisn.applingo.ui.managed

import io.github.ernisn.applingo.ui.OnBackToTop
import io.github.ernisn.applingo.ui.OnScrollToTop
import io.github.ernisn.applingo.ui.ConfiguredLanguage
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.theme.RowShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import android.text.format.DateUtils
import io.github.ernisn.applingo.ui.PageBackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import io.github.ernisn.applingo.ui.components.FrostedDropdownMenu
import io.github.ernisn.applingo.ui.components.FrostedPullToRefresh
import io.github.ernisn.applingo.ui.components.PackageLine
import io.github.ernisn.applingo.ui.components.SearchField
import io.github.ernisn.applingo.ui.components.SegmentedTabRow
import io.github.ernisn.applingo.ui.components.SelectionActionsBar
import io.github.ernisn.applingo.ui.components.SelectionTopBar
import io.github.ernisn.applingo.ui.components.appRow
import io.github.ernisn.applingo.ui.components.groupedRowColors
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.components.translucentButtonColors
import io.github.ernisn.applingo.ui.components.rememberRevealer
import io.github.ernisn.applingo.ui.components.revealable
import io.github.ernisn.applingo.ui.picker.BatchLocalePickerDialog
import io.github.ernisn.applingo.ui.HideNavIsland
import io.github.ernisn.applingo.ui.bottomChrome
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.theme.ControlShape
import kotlinx.coroutines.delay

private const val MAX_ACTIVITY_SHOWN = 100
private const val UNINSTALLED_CLOSE_DELAY_MS = 500L

private enum class ManagedTab(val label: Int) {
    Apps(R.string.managed_section_apps),
    Languages(R.string.managed_section_languages),
    Activity(R.string.managed_section_activity),
}

/** Content types of the rows D-pad focus can land on: apps (in the Apps and Languages tabs) and language headers. */
private const val APP_ROW = "app"
private const val LANGUAGE_HEADER = "language"

/** A language group's key, for its header's list key and its place in the open groups ("" for the system language). */
private val ConfiguredLanguage.key: String get() = tag.orEmpty()

/**
 * Every installed app Applingo keeps a language for with the result of the last check, the same apps
 * by language, and recent activity, as tabs like the app list's: one list after the other meant
 * scrolling past every app to reach the activity. The search field on top filters whichever tab is
 * shown (the log too), and pull to refresh checks now. Long press (or Select apps) selects apps for a
 * batch change, as in the app list.
 *
 * The Languages tab groups the apps by the first language of their chain, the one they show in, so
 * each app is in one group: a group per language a chain can start with would list most apps
 * twice. Each group's header gives the language and how many apps there are, the groups with the
 * most apps first, and opens or closes the group. They all start closed: the tab opens on the
 * languages in use, one short row each, rather than on a long list where a language further down
 * took a long scroll to reach. The apps keep the order chosen in the menu within each group and
 * leave out the language badge, which the header already gives. A search opens every group (its
 * results are what was asked for), as does a single group (there is nothing to choose between).
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
    // One per tab, so each keeps its scroll position while another is shown, and kept while the
    // uninstalled apps are.
    val appsListState = rememberLazyListState()
    val languagesListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    // The Languages tab's open groups (see ConfiguredLanguage.key), likewise kept.
    val openLanguages = rememberSaveable { mutableStateOf(emptySet<String>()) }
    // The island's Configured double-tapped: back to the start of the tab shown (the first tap
    // already closed the uninstalled apps, see below).
    OnScrollToTop {
        when (tab) {
            ManagedTab.Apps -> appsListState
            ManagedTab.Languages -> languagesListState
            ManagedTab.Activity -> activityListState
        }.animateScrollToItem(0)
    }
    // The field owns its text, as in the app list; the view model may have lost it with the process.
    var queryText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.onManagedQueryChange(queryText) }

    var showUninstalled by rememberSaveable { mutableStateOf(false) }
    // The island's Configured tapped again: back to the list from the uninstalled apps.
    OnBackToTop { showUninstalled = false }
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
        languagesListState = languagesListState,
        activityListState = activityListState,
        openLanguages = openLanguages,
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
    languagesListState: LazyListState,
    activityListState: LazyListState,
    openLanguages: MutableState<Set<String>>,
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
    // Language groups the user can't open or close: all of them in search results, or the only one.
    val languagesFixed = searching || state.languages.size == 1
    fun isOpen(language: ConfiguredLanguage) = languagesFixed || language.key in openLanguages.value
    // Taken as a request comes (with the pane, or while it waits behind the upright island), and
    // kept once it is done (see FocusRowOnReturn). The app's language group opens with it: it may
    // have moved to a closed one (its language changed), and its row has to be there to take focus.
    // Written here, before the rows read it, rather than in an effect a frame later, when focus
    // would have found no row to go to.
    var returnTo by remember { mutableStateOf(focusOnReturn) }
    if (focusOnReturn != null && focusOnReturn !== returnTo) {
        returnTo = focusOnReturn
        state.languages.firstOrNull { language -> language.apps.any { it.app.packageName == focusOnReturn.packageName } }
            ?.let { openLanguages.value += it.key }
    }
    val returnFocus = remember { FocusRequester() }
    // Select apps: the menu went with the search bar, so focus goes to the first app row (or
    // language header) on screen (see FocusWhenPending), by its list key.
    var selectFocusKey by remember { mutableStateOf<String?>(null) }
    val selectFocus = remember { FocusRequester() }
    FocusWhenPending(selectFocusKey != null, selectFocus) { selectFocusKey = null }
    FocusRowOnReturn(
        index = returnTo?.packageName?.let { packageName ->
            when (tab) {
                ManagedTab.Apps -> state.apps.indexOfFirst { it.app.packageName == packageName }.takeIf { it >= 0 }?.let { it + firstRow }
                ManagedTab.Languages -> state.languages.rowIndex(packageName, ::isOpen)
                ManagedTab.Activity -> null
            }
        },
        listState = if (tab == ManagedTab.Languages) languagesListState else appsListState,
        requester = returnFocus,
        onDone = { returnTo?.onDone() },
    )

    // The Languages tab's rows by list key, for the group pinned under the bars (see pinnedLanguage).
    // Only a change of group recomposes; how far it is pushed up is read as it is placed.
    val groupOf = remember(state.languages) {
        buildMap<Any, ConfiguredLanguage> {
            for (language in state.languages) {
                put(headerKey(language), language)
                language.apps.forEach { put(it.app.packageName, language) }
            }
        }
    }
    val currentGroupOf by rememberUpdatedState(groupOf)
    val pinnedGroup by remember {
        derivedStateOf(referentialEqualityPolicy()) { pinnedLanguage(languagesListState, currentGroupOf)?.language }
    }
    val scope = rememberCoroutineScope()

    /** Closes [language] from its pinned header, with its header brought to the top, where the pinned one was. */
    fun closePinned(language: ConfiguredLanguage) {
        scope.launch {
            languagesListState.scrollToItem(state.languages.headerIndex(language, ::isOpen))
            openLanguages.value -= language.key
        }
    }

    // A new query or order is a new list: start at its top (only on an actual change, as in the
    // app list).
    val listKey = "${state.query}\n${state.preferences}"
    var shownListKey by rememberSaveable { mutableStateOf(listKey) }
    LaunchedEffect(listKey) {
        if (listKey != shownListKey) {
            shownListKey = listKey
            appsListState.scrollToItem(0)
            languagesListState.scrollToItem(0)
            activityListState.scrollToItem(0)
        }
    }

    val selectionStarted by viewModel.managedSelection.started.collectAsStateWithLifecycle()
    val selecting = selectionStarted || selection.isNotEmpty()
    var showBatchPicker by rememberSaveable { mutableStateOf(false) }
    var confirmClearLog by rememberSaveable { mutableStateOf(false) }
    PageBackHandler(enabled = selecting) { viewModel.managedSelection.clear() }
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
            pinned = pinnedGroup?.takeIf { tab == ManagedTab.Languages }?.let { language ->
                {
                    LanguageHeader(
                        language = language,
                        open = true,
                        onToggle = if (languagesFixed) null else { { closePinned(language) } },
                        pinned = true,
                    )
                }
            },
            pinnedOffset = { pinnedLanguage(languagesListState, currentGroupOf)?.offset ?: 0 },
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
                        placeholder = stringResource(if (tab == ManagedTab.Activity) R.string.activity_search_hint else R.string.search_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        when (tab) {
                            ManagedTab.Apps, ManagedTab.Languages -> AppsMenu(
                                state = state,
                                onPreferencesChange = { viewModel.updateManagedOrder(it.sort, it.reverseOrder) },
                                onSelectApps = {
                                    selectFocusKey = firstRowOnScreen(if (tab == ManagedTab.Apps) appsListState else languagesListState)
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
                    // The selection lives in the tab it started in (Apps or Languages); it is finished
                    // or dropped there first. A search leaves every tab open: it filters the log as
                    // well.
                    enabled = !selecting,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            },
        ) { headerHeight, footerHeight ->
            val listPadding = PaddingValues(top = headerHeight, bottom = listEnd + footerHeight)
            // The same row in the Apps and Languages tabs, the latter without the language badge.
            val appItem = @Composable { row: AppRow, showLocales: Boolean, modifier: Modifier ->
                val packageName = row.app.packageName
                ManagedAppItem(
                    row = row,
                    showLocales = showLocales,
                    modifier = modifier.then(
                        when (packageName) {
                            returnTo?.packageName -> Modifier.focusRequester(returnFocus)
                            selectFocusKey -> Modifier.focusRequester(selectFocus)
                            else -> Modifier
                        },
                    ),
                    selected = packageName == selectedPackage,
                    checked = if (selecting) packageName in selection else null,
                    onClick = {
                        if (selecting) viewModel.managedSelection.toggle(packageName) else onOpenApp(packageName)
                    },
                    onLongClick = { viewModel.managedSelection.toggle(packageName) },
                )
            }
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
                        // Not before the app list has loaded: until then there are no apps to show.
                        if (state.loaded && state.apps.isEmpty()) {
                            item(key = "empty") {
                                Hint(stringResource(if (searching) R.string.list_no_results else R.string.managed_empty))
                            }
                        }
                        items(state.apps, key = { it.app.packageName }, contentType = { APP_ROW }) { appItem(it, true, Modifier) }
                    }
                    ManagedTab.Languages -> LazyColumn(
                        state = languagesListState,
                        contentPadding = listPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.loaded && state.apps.isEmpty()) {
                            item(key = "empty") {
                                Hint(stringResource(if (searching) R.string.list_no_results else R.string.managed_empty))
                            }
                        }
                        // Rows slide aside for a group opening, and close up after one closing.
                        for (language in state.languages) {
                            val headerKey = headerKey(language)
                            val open = isOpen(language)
                            item(key = headerKey, contentType = LANGUAGE_HEADER) {
                                LanguageHeader(
                                    language = language,
                                    open = open,
                                    onToggle = if (languagesFixed) null else { { openLanguages.value = openLanguages.value.toggled(language.key) } },
                                    modifier = Modifier
                                        .animateItem()
                                        .then(if (headerKey == selectFocusKey) Modifier.focusRequester(selectFocus) else Modifier),
                                )
                            }
                            if (open) {
                                items(language.apps, key = { it.app.packageName }, contentType = { APP_ROW }) {
                                    appItem(it, false, Modifier.animateItem())
                                }
                            }
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
        FrostedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                    colors = translucentButtonColors(),
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

/** The list key of a language group's header in the Languages tab (app rows are keyed by package, which has no colon). */
private fun headerKey(language: ConfiguredLanguage) = "language:${language.key}"

private fun Set<String>.toggled(key: String): Set<String> = if (key in this) this - key else this + key

/** Index of [language]'s header in the Languages tab, after the headers of the groups before it and the apps of the open ones. */
private fun List<ConfiguredLanguage>.headerIndex(language: ConfiguredLanguage, isOpen: (ConfiguredLanguage) -> Boolean): Int {
    var index = 0
    for (other in this) {
        if (other.key == language.key) return index
        index += 1 + if (isOpen(other)) other.apps.size else 0
    }
    return 0
}

/**
 * Index of [packageName]'s row in the Languages tab, where each group's header comes before its apps
 * and a closed group ([isOpen] false) has only its header; null if it isn't there.
 */
private fun List<ConfiguredLanguage>.rowIndex(packageName: String, isOpen: (ConfiguredLanguage) -> Boolean): Int? {
    var index = 0
    for (language in this) {
        index++
        if (!isOpen(language)) continue
        val at = language.apps.indexOfFirst { it.app.packageName == packageName }
        if (at >= 0) return index + at
        index += language.apps.size
    }
    return null
}

/** The list key of the first app row or language header laid out in [list] (the summary card and hints left out). */
private fun firstRowOnScreen(list: LazyListState): String? =
    list.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == APP_ROW || it.contentType == LANGUAGE_HEADER }?.key as? String

/** Rotation of a language header's arrow: down while closed, up while open. */
private const val OPEN_ARROW_DEGREES = 180f

/**
 * An open group whose header scrolled up out of sight while its apps still show in the Languages
 * tab, pinned under the bars instead (see FrostedBarsLayout): [offset] (pixels, not above 0) is how
 * far the next group's header has pushed it up.
 */
private class PinnedLanguage(val language: ConfiguredLanguage, val offset: Int)

/**
 * What the Languages tab pins from where [list] stands: the group of the row at the top edge (just
 * under the bars), unless that row is its header, still in full view, or the header of a closed
 * group, which has nothing below to stay for. [groupOf] has each row's group by list key.
 */
private fun pinnedLanguage(list: LazyListState, groupOf: Map<Any, ConfiguredLanguage>): PinnedLanguage? {
    val items = list.layoutInfo.visibleItemsInfo
    // Offsets count from the end of the list's top padding, under the bars.
    val topIndex = items.indexOfFirst { it.offset + it.size > 0 }
    val top = items.getOrNull(topIndex) ?: return null
    val language = groupOf[top.key] ?: return null
    if (top.contentType == LANGUAGE_HEADER) {
        if (top.offset >= 0) return null
        if (items.getOrNull(topIndex + 1)?.let { groupOf[it.key]?.key } != language.key) return null
    }
    // A header as tall as the pinned one coming up under it pushes it away.
    val next = items.drop(topIndex + 1).firstOrNull { it.contentType == LANGUAGE_HEADER }
    return PinnedLanguage(language, next?.let { (it.offset - it.size).coerceAtMost(0) } ?: 0)
}

/**
 * A group's header in the Languages tab: the language, in itself as on the apps' badges, how many
 * apps it has, and an arrow for opening or closing it ([onToggle]; none for groups that stay open).
 * Its text lines up with the app icons, and it is drawn and focused as a row, as the apps are.
 *
 * As the [pinned] copy over the list, it takes neither focus nor accessibility services' attention:
 * the header in the list does both, and focus moving onto a copy that changes with the scroll
 * position, under the next press, lost its place.
 */
@Composable
private fun LanguageHeader(
    language: ConfiguredLanguage,
    open: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
) {
    val name = language.tag?.let { LocaleTags.nativeName(it) } ?: stringResource(R.string.locale_follow_system)
    val arrow by animateFloatAsState(if (open) OPEN_ARROW_DEGREES else 0f, label = "arrow")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RowShape)
            .then(if (pinned) Modifier.focusProperties { canFocus = false } else Modifier)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .then(
                if (pinned) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        heading()
                        if (onToggle != null) {
                            if (open) collapse { onToggle(); true } else expand { onToggle(); true }
                        }
                    }
                },
            )
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = language.apps.size.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
        if (onToggle != null) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).graphicsLayer { rotationZ = arrow },
            )
        }
    }
}

/**
 * Like an app list row. Without [showLocales] (under its language's header) the language badge is
 * left out, and only a warning stays where the system has something else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManagedAppItem(
    row: AppRow,
    selected: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLocales: Boolean = true,
) {
    val highlighted = selected || checked == true
    val revealer = rememberRevealer()
    ListItem(
        modifier = modifier
            .appRow(highlighted)
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
        trailingContent = when {
            showLocales && row.locales.isNotEmpty() -> {
                { LocaleBadge(row.locales, row.status, onHighlight = highlighted) }
            }
            !showLocales && row.status == LocaleStatus.Mismatch -> {
                {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.status_mismatch, chain(row.systemLocales.orEmpty())),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> null
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
    // In from the pane's edges as far as the app rows in the other tab (see appRow).
    ListItem(
        modifier = Modifier.padding(horizontal = 16.dp),
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
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

package io.github.ernisn.applingo.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LanguageGroup
import io.github.ernisn.applingo.core.LocaleCatalog
import io.github.ernisn.applingo.core.LocaleOption
import io.github.ernisn.applingo.core.Search
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.components.ActionPair
import io.github.ernisn.applingo.ui.components.EditablePriorityList
import io.github.ernisn.applingo.ui.components.FavoriteButton
import io.github.ernisn.applingo.ui.components.FrostedBarsLayout
import io.github.ernisn.applingo.ui.components.frostedTopAppBarColors
import io.github.ernisn.applingo.ui.components.GroupHeader
import io.github.ernisn.applingo.ui.components.GroupPosition
import io.github.ernisn.applingo.ui.components.groupedRow
import io.github.ernisn.applingo.ui.components.groupedRowColors
import io.github.ernisn.applingo.ui.components.SearchField
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.components.translucentButtonColors
import io.github.ernisn.applingo.ui.bottomChrome
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.theme.ControlShape
import kotlinx.coroutines.launch

private val chainSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/** Picker for one app, shown as the extra pane next to (or instead of) its detail pane. */
@Composable
fun LocalePickerPane(
    viewModel: MainViewModel,
    packageName: String,
    closeAsBack: Boolean,
    onClose: () -> Unit,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val row = rows[packageName]
    val initial = remember(packageName) { row?.locales.orEmpty() }
    val declared by produceState<List<String>?>(null, packageName) {
        value = viewModel.extras(packageName).declaredLocales
    }
    LocalePickerContent(
        viewModel = viewModel,
        stateKey = packageName,
        title = stringResource(R.string.picker_title, row?.app?.label ?: packageName),
        initial = initial,
        declared = declared,
        closeAsBack = closeAsBack,
        onApply = { chain ->
            viewModel.applyLocales(packageName, chain)
            onClose()
        },
        onClose = onClose,
    )
}

/** Full-screen picker that applies one chain to the [count] apps selected in a list. */
@Composable
fun BatchLocalePickerDialog(
    viewModel: MainViewModel,
    count: Int,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LocalePickerContent(
                viewModel = viewModel,
                stateKey = "batch",
                title = pluralStringResource(R.plurals.picker_title_batch, count, count),
                initial = emptyList(),
                declared = null,
                closeAsBack = false,
                batch = true,
                onApply = { chain ->
                    onApply(chain)
                    onDismiss()
                },
                onClose = onDismiss,
            )
        }
    }
}

/**
 * Edits an ordered locale chain: an app uses the first locale it has resources for, then the next,
 * and finally the system languages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalePickerContent(
    viewModel: MainViewModel,
    stateKey: String,
    title: String,
    initial: List<String>,
    declared: List<String>?,
    closeAsBack: Boolean,
    onApply: (List<String>) -> Unit,
    onClose: () -> Unit,
    /** Applies to several apps: nothing to compare against, but an empty chain must not reset them all. */
    batch: Boolean = false,
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteLocales.collectAsStateWithLifecycle()
    val chain = rememberSaveable(stateKey, saver = chainSaver) { initial.toMutableStateList() }
    var query by rememberSaveable(stateKey) { mutableStateOf("") }
    val expanded = remember(stateKey) { mutableStateListOf<String>() }
    val uiLocale = LocalConfiguration.current.locales[0]
    val systemOptions = remember(uiLocale) { viewModel.systemLocales.map { LocaleCatalog.option(it, uiLocale) } }
    val declaredOptions = remember(declared, uiLocale) { declared.orEmpty().map { LocaleCatalog.option(it, uiLocale) } }
    val favoriteOptions = remember(favorites, uiLocale) { favorites.map { LocaleCatalog.option(it, uiLocale) } }

    fun toggle(tag: String) {
        if (!chain.remove(tag)) chain.add(tag)
    }

    // Every language row: ticking it adds it to the chain, its star keeps it under Favorites.
    @Composable
    fun Option(option: LocaleOption, position: GroupPosition, indent: Boolean = false, group: LanguageGroup? = null) {
        OptionRow(
            option = option,
            position = position,
            selected = option.tag in chain,
            onClick = { toggle(option.tag) },
            favorite = option.tag in favorites,
            onFavoriteToggle = { viewModel.toggleFavoriteLocale(option.tag) },
            expandable = group != null && group.variants.isNotEmpty(),
            expanded = option.tag in expanded,
            onExpandToggle = { if (!expanded.remove(option.tag)) expanded.add(option.tag) },
            indent = indent,
        )
    }

    // Searching brings the field to the top, so the keyboard covers results rather than the field
    // and what is right below it; it stays there while the results change.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var searchFocused by remember { mutableStateOf(false) }
    fun showSearchAtTop() {
        scope.launch { listState.animateScrollToItem(SEARCH_ITEM_INDEX) }
    }
    LaunchedEffect(query) {
        if (searchFocused) listState.scrollToItem(SEARCH_ITEM_INDEX)
    }

    // At the bottom for touch, as the actions on selected apps are; next to the title on a D-pad
    // device, where Up from the top of the list reaches them.
    val dpad = isDpadDevice()
    val applyEnabled = if (batch) chain.isNotEmpty() else chain.toList() != initial
    val chainActions: @Composable () -> Unit = {
        TextButton(onClick = { chain.clear() }, enabled = chain.isNotEmpty(), shape = ControlShape) {
            Text(stringResource(R.string.action_follow_system))
        }
        Button(
            onClick = { onApply(chain.toList()) },
            enabled = applyEnabled,
            shape = ControlShape,
            colors = translucentButtonColors(),
        ) { Text(stringResource(R.string.action_apply)) }
    }

    Scaffold(
        modifier = Modifier.safeArea(top = false, bottom = false),
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        // The list scrolls on behind the top bar and the buttons, frosted, except with a D-pad, where
        // it stays between them (see FrostedHeaderLayout). The buttons are the only bar at the bottom
        // (the island leaves secondary pages), down behind the system bar and above it and the
        // keyboard. Without them (D-pad) the list itself clears the system bar and the keyboard.
        FrostedBarsLayout(
            modifier = Modifier.padding(padding).fillMaxSize(),
            footer = if (dpad) {
                null
            } else {
                {
                    ActionPair(
                        secondaryLabel = stringResource(R.string.action_follow_system),
                        onSecondary = { chain.clear() },
                        primaryLabel = stringResource(R.string.action_apply),
                        onPrimary = { onApply(chain.toList()) },
                        secondaryEnabled = chain.isNotEmpty(),
                        primaryEnabled = applyEnabled,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            },
            header = {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    colors = frostedTopAppBarColors(),
                    title = {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            if (closeAsBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            } else {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                    },
                    actions = {
                        if (dpad) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 8.dp),
                            ) { chainActions() }
                        }
                    },
                )
            },
        ) { headerHeight, footerHeight ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // The last card ends clear of the bar, as the other lists' last rows do.
                contentPadding = PaddingValues(top = headerHeight, bottom = 16.dp + if (dpad) bottomChrome() else footerHeight),
            ) {
                // The chain comes before the search field: SEARCH_ITEM_INDEX counts it. A card like the
                // detail page's language card, and every section below one card each.
                item(key = "chain") {
                    Column {
                        GroupHeader(stringResource(R.string.picker_selected))
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(
                                        when {
                                            chain.isEmpty() -> R.string.picker_selected_empty
                                            !dpad -> R.string.picker_order_hint
                                            else -> R.string.picker_order_hint_dpad
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                EditablePriorityList(chain = chain, systemLocales = viewModel.systemLocales)
                            }
                        }
                    }
                }
                item(key = "search") {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.picker_search_hint),
                        onFocused = ::showSearchAtTop,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                            .onFocusChanged { searchFocused = it.hasFocus },
                    )
                }

                val groups = catalog
                val keywords = Search.keywords(query)
                when {
                    groups == null -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    keywords.isNotEmpty() -> {
                        val matches = groups.asSequence()
                            .flatMap { sequenceOf(it.language) + it.variants }
                            .mapNotNull { option -> Search.score(keywords, option.searchTarget)?.let { option to it } }
                            // Plain languages before their regional variants when equally relevant.
                            .sortedWith(compareByDescending<Pair<LocaleOption, Int>> { it.second }.thenBy { it.first.tag.length })
                            .map { it.first }
                            .take(MAX_RESULTS)
                            .toList()
                        if (matches.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.list_no_results),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                )
                            }
                        } else {
                            item(key = "search-top") { Spacer(Modifier.height(12.dp)) }
                        }
                        itemsIndexed(matches, key = { _, option -> "search:${option.tag}" }) { index, option ->
                            Option(option, GroupPosition.of(index, matches.size))
                        }
                        // Room to scroll the field to the top even when only a few languages match.
                        item(key = "search-room") { Spacer(Modifier.fillParentMaxHeight()) }
                    }
                    else -> {
                        // First, right below the field: the point is not having to search for them again.
                        // Apps can declare dozens of languages, which would push them far down.
                        sectionHeader(R.string.picker_section_favorites)
                        if (favoriteOptions.isEmpty()) {
                            item(key = "favorites-hint") {
                                Text(
                                    text = stringResource(R.string.picker_favorites_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .groupedRow(GroupPosition.Only)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                )
                            }
                        }
                        options("favorite", favoriteOptions) { option, position -> Option(option, position) }
                        if (declaredOptions.isNotEmpty()) {
                            sectionHeader(R.string.picker_section_declared)
                            options("declared", declaredOptions) { option, position -> Option(option, position) }
                        }
                        sectionHeader(R.string.picker_section_system)
                        options("system", systemOptions) { option, position -> Option(option, position) }
                        sectionHeader(R.string.picker_section_all)
                        // Languages and their shown regions make one card.
                        val all = groups.flatMap { group ->
                            listOf(group.language to group) +
                                if (group.language.tag in expanded) group.variants.map { it to null } else emptyList()
                        }
                        itemsIndexed(all, key = { _, (option, _) -> "all:${option.tag}" }) { index, (option, group) ->
                            Option(option, GroupPosition.of(index, all.size), indent = group == null, group = group)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: Int) {
    item(key = "header:$title") { GroupHeader(stringResource(title)) }
}

/** One section's languages as one card. */
private fun LazyListScope.options(
    section: String,
    options: List<LocaleOption>,
    row: @Composable (LocaleOption, GroupPosition) -> Unit,
) {
    itemsIndexed(options, key = { _, option -> "$section:${option.tag}" }) { index, option ->
        row(option, GroupPosition.of(index, options.size))
    }
}

@Composable
private fun OptionRow(
    option: LocaleOption,
    position: GroupPosition,
    selected: Boolean,
    onClick: () -> Unit,
    favorite: Boolean,
    onFavoriteToggle: () -> Unit,
    expandable: Boolean,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    indent: Boolean,
) {
    ListItem(
        modifier = Modifier
            .groupedRow(position)
            .clickable(onClick = onClick)
            .padding(start = if (indent) 24.dp else 0.dp),
        colors = groupedRowColors(),
        leadingContent = { Checkbox(checked = selected, onCheckedChange = null) },
        headlineContent = { Text(option.nativeName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${option.localizedName} · ${option.tag}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        // The star at the very end, so the stars line up in every section whether or not a row has
        // regions to show.
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expandable) {
                    IconButton(onClick = onExpandToggle) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                        )
                    }
                }
                FavoriteButton(favorite = favorite, onToggle = onFavoriteToggle)
            }
        },
    )
}

private const val MAX_RESULTS = 100

/** Position of the search field in the picker's list. */
private const val SEARCH_ITEM_INDEX = 1

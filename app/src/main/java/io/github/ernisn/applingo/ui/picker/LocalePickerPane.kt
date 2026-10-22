package io.github.ernisn.applingo.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.HorizontalDivider
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
import io.github.ernisn.applingo.ui.components.EditablePriorityList
import io.github.ernisn.applingo.ui.components.FavoriteButton
import io.github.ernisn.applingo.ui.components.SearchField
import io.github.ernisn.applingo.ui.components.isDpadDevice
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
    fun Option(option: LocaleOption, indent: Boolean = false, group: LanguageGroup? = null) {
        OptionRow(
            option = option,
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

    // At the bottom for touch; next to the title on a D-pad device, where Up from the top of the list
    // reaches them.
    val dpad = isDpadDevice()
    val chainActions: @Composable () -> Unit = {
        TextButton(onClick = { chain.clear() }, enabled = chain.isNotEmpty(), shape = ControlShape) {
            Text(stringResource(R.string.action_follow_system))
        }
        Button(
            onClick = { onApply(chain.toList()) },
            enabled = if (batch) chain.isNotEmpty() else chain.toList() != initial,
            shape = ControlShape,
        ) { Text(stringResource(R.string.action_apply)) }
    }

    Scaffold(
        modifier = Modifier.safeArea(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
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
        bottomBar = {
            if (!dpad) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { chainActions() }
                }
            }
        },
    ) { padding ->
        // The bars' heights go outside the list, not into its content padding: a list reaching behind
        // the top bar starts above the back button, so D-pad focus search doesn't count it as below
        // the button and Down skipped it (likewise Up from below).
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // Header, chain and divider come before the search field: SEARCH_ITEM_INDEX counts them.
            sectionHeader(R.string.picker_selected)
            item(key = "chain") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(
                            when {
                                chain.isEmpty() -> R.string.picker_selected_empty
                                !dpad -> R.string.picker_order_hint
                                else -> R.string.picker_order_hint_dpad
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    EditablePriorityList(chain = chain, systemLocales = viewModel.systemLocales)
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item(key = "search") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.picker_search_hint),
                    onFocused = ::showSearchAtTop,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(matches, key = { "search:${it.tag}" }) { option -> Option(option) }
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
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(favoriteOptions, key = { "favorite:${it.tag}" }) { option -> Option(option) }
                    if (declaredOptions.isNotEmpty()) {
                        sectionHeader(R.string.picker_section_declared)
                        items(declaredOptions, key = { "declared:${it.tag}" }) { option -> Option(option) }
                    }
                    sectionHeader(R.string.picker_section_system)
                    items(systemOptions, key = { "system:${it.tag}" }) { option -> Option(option) }
                    sectionHeader(R.string.picker_section_all)
                    groups.forEach { group ->
                        val language = group.language
                        item(key = "all:${language.tag}") { Option(language, group = group) }
                        if (language.tag in expanded) {
                            items(group.variants, key = { "all:${it.tag}" }) { option -> Option(option, indent = true) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: Int) {
    item(key = "header:$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun OptionRow(
    option: LocaleOption,
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
            .clickable(onClick = onClick)
            .padding(start = if (indent) 24.dp else 0.dp),
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
private const val SEARCH_ITEM_INDEX = 3

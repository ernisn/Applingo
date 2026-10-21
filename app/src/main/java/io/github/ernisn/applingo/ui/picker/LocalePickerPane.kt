package io.github.ernisn.applingo.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleCatalog
import io.github.ernisn.applingo.core.LocaleOption
import io.github.ernisn.applingo.core.Search
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.components.EditablePriorityList
import io.github.ernisn.applingo.ui.components.focusableOnlyAfterTouch
import io.github.ernisn.applingo.ui.safeArea

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

/** Full-screen picker that applies one chain to every app selected in the list. */
@Composable
fun BatchLocalePickerDialog(viewModel: MainViewModel, count: Int, onDismiss: () -> Unit) {
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
                    viewModel.applyToSelection(chain)
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
    val chain = rememberSaveable(stateKey, saver = chainSaver) { initial.toMutableStateList() }
    var query by rememberSaveable(stateKey) { mutableStateOf("") }
    val expanded = remember(stateKey) { mutableStateListOf<String>() }
    val uiLocale = LocalConfiguration.current.locales[0]
    val systemOptions = remember(uiLocale) { viewModel.systemLocales.map { LocaleCatalog.option(it, uiLocale) } }
    val declaredOptions = remember(declared, uiLocale) { declared.orEmpty().map { LocaleCatalog.option(it, uiLocale) } }

    fun toggle(tag: String) {
        if (!chain.remove(tag)) chain.add(tag)
    }

    Scaffold(
        modifier = Modifier.safeArea(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        if (closeAsBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        } else {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TextButton(onClick = { chain.clear() }, enabled = chain.isNotEmpty()) {
                        Text(stringResource(R.string.action_follow_system))
                    }
                    Button(
                        onClick = { onApply(chain.toList()) },
                        enabled = if (batch) chain.isNotEmpty() else chain.toList() != initial,
                    ) { Text(stringResource(R.string.action_apply)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            sectionHeader(R.string.picker_selected)
            item(key = "chain") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(if (chain.isEmpty()) R.string.picker_selected_empty else R.string.picker_order_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    EditablePriorityList(chain = chain, systemLocales = viewModel.systemLocales)
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                LocaleSearchField(query = query, onQueryChange = { query = it.replace(Regex("[\\r\\n]+"), " ") })
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
                    items(matches, key = { "search:${it.tag}" }) { option ->
                        OptionRow(option, selected = option.tag in chain, onClick = { toggle(option.tag) })
                    }
                }
                else -> {
                    if (declaredOptions.isNotEmpty()) {
                        sectionHeader(R.string.picker_section_declared)
                        items(declaredOptions, key = { "declared:${it.tag}" }) { option ->
                            OptionRow(option, selected = option.tag in chain, onClick = { toggle(option.tag) })
                        }
                    }
                    sectionHeader(R.string.picker_section_system)
                    items(systemOptions, key = { "system:${it.tag}" }) { option ->
                        OptionRow(option, selected = option.tag in chain, onClick = { toggle(option.tag) })
                    }
                    sectionHeader(R.string.picker_section_all)
                    groups.forEach { group ->
                        val language = group.language
                        item(key = "all:${language.tag}") {
                            OptionRow(
                                option = language,
                                selected = language.tag in chain,
                                onClick = { toggle(language.tag) },
                                expandable = group.variants.isNotEmpty(),
                                expanded = language.tag in expanded,
                                onExpandToggle = {
                                    if (!expanded.remove(language.tag)) expanded.add(language.tag)
                                },
                            )
                        }
                        if (language.tag in expanded) {
                            items(group.variants, key = { "all:${it.tag}" }) { option ->
                                OptionRow(
                                    option = option,
                                    selected = option.tag in chain,
                                    onClick = { toggle(option.tag) },
                                    indent = true,
                                )
                            }
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
private fun LocaleSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.picker_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusableOnlyAfterTouch(),
    )
}

@Composable
private fun OptionRow(
    option: LocaleOption,
    selected: Boolean,
    onClick: () -> Unit,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
    indent: Boolean = false,
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
        trailingContent = if (expandable) {
            {
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    )
                }
            }
        } else {
            null
        },
    )
}

private const val MAX_RESULTS = 100

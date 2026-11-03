package io.github.ernisn.applingo.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.BACKGROUND_CHECK_HOURS
import io.github.ernisn.applingo.data.SEED_COLORS
import io.github.ernisn.applingo.data.ThemeMode
import io.github.ernisn.applingo.shizuku.ShizukuState
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.bottomChrome
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.components.AppTypeFilterRow
import io.github.ernisn.applingo.ui.components.FrostedHeaderLayout
import io.github.ernisn.applingo.ui.components.GroupHeader
import io.github.ernisn.applingo.ui.components.frostedTopAppBarColors
import io.github.ernisn.applingo.ui.components.GroupPosition
import io.github.ernisn.applingo.ui.components.groupedRow
import io.github.ernisn.applingo.ui.components.groupedRowColors
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.theme.ControlShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val REPOSITORY_URL = "https://github.com/ernisn/Applingo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Nothing to open it with on this device.
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    val ownLocale = remember { viewModel.ownLocale() }
    if (showLanguageDialog) {
        OwnLanguageDialog(
            options = viewModel.ownLocaleOptions,
            current = ownLocale,
            onSelect = { tag ->
                showLanguageDialog = false
                if (tag != ownLocale) viewModel.setOwnLocale(tag)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    // The list scrolls on behind the title, frosted (see FrostedHeaderLayout).
    FrostedHeaderLayout(
        modifier = Modifier.safeArea(top = false, bottom = false).fillMaxSize(),
        header = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                windowInsets = WindowInsets(0),
                colors = frostedTopAppBarColors(),
            )
        },
    ) { headerHeight ->
        LazyColumn(
            contentPadding = PaddingValues(top = headerHeight, bottom = 16.dp + bottomChrome()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ShizukuStatusCard(
                    state = listState.shizuku,
                    onRequestPermission = viewModel::requestShizukuPermission,
                    onRetry = viewModel::retryShizuku,
                    shizukuLaunchIntent = viewModel::shizukuLaunchIntent,
                )
            }
            (listState.shizuku as? ShizukuState.Connected)?.let { connected ->
                group(
                    title = null,
                    rows = listOf(
                        GroupRow("shizuku") { modifier ->
                            ListItem(
                                modifier = modifier,
                                colors = groupedRowColors(),
                                headlineContent = { Text(stringResource(R.string.shizuku_title)) },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.settings_shizuku_connected,
                                            if (connected.uid == 0) "root" else "adb",
                                        ),
                                    )
                                },
                            )
                        },
                    ),
                )
            }

            group(
                R.string.settings_section_appearance,
                buildList {
                    add(
                        GroupRow("theme") { modifier ->
                            ListItem(
                                modifier = modifier,
                                colors = groupedRowColors(),
                                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                                supportingContent = {
                                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        val modes = ThemeMode.entries
                                        modes.forEachIndexed { index, mode ->
                                            SegmentedButton(
                                                selected = settings.themeMode == mode,
                                                onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                                                shape = SegmentedButtonDefaults.itemShape(index, modes.size, baseShape = ControlShape),
                                                icon = {},
                                            ) {
                                                Text(
                                                    stringResource(
                                                        when (mode) {
                                                            ThemeMode.System -> R.string.settings_theme_system
                                                            ThemeMode.Light -> R.string.settings_theme_light
                                                            ThemeMode.Dark -> R.string.settings_theme_dark
                                                        },
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    )
                    add(
                        GroupRow("dynamic_color") { modifier ->
                            SwitchRow(
                                modifier = modifier,
                                title = stringResource(R.string.settings_dynamic_color),
                                summary = stringResource(R.string.settings_dynamic_color_summary),
                                checked = settings.dynamicColor,
                                onCheckedChange = { value -> viewModel.updateSettings { it.copy(dynamicColor = value) } },
                            )
                        },
                    )
                    if (!settings.dynamicColor) {
                        add(
                            GroupRow("seed_color") { modifier ->
                                ListItem(
                                    modifier = modifier,
                                    colors = groupedRowColors(),
                                    headlineContent = { Text(stringResource(R.string.settings_seed_color)) },
                                    supportingContent = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            SEED_COLORS.forEach { seed ->
                                                ColorSwatch(
                                                    color = Color(seed),
                                                    selected = settings.seedColor == seed,
                                                    onClick = { viewModel.updateSettings { it.copy(seedColor = seed) } },
                                                )
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                    add(
                        GroupRow("pure_black") { modifier ->
                            SwitchRow(
                                modifier = modifier,
                                title = stringResource(R.string.settings_pure_black),
                                summary = stringResource(R.string.settings_pure_black_summary),
                                checked = settings.pureBlack,
                                onCheckedChange = { value -> viewModel.updateSettings { it.copy(pureBlack = value) } },
                            )
                        },
                    )
                    add(
                        GroupRow("system_font") { modifier ->
                            SwitchRow(
                                modifier = modifier,
                                title = stringResource(R.string.settings_system_font),
                                summary = stringResource(R.string.settings_system_font_summary),
                                checked = settings.systemFont,
                                onCheckedChange = { value -> viewModel.updateSettings { it.copy(systemFont = value) } },
                            )
                        },
                    )
                },
            )

            group(
                R.string.settings_section_keeping,
                buildList {
                    add(
                        GroupRow("auto_repair") { modifier ->
                            SwitchRow(
                                modifier = modifier,
                                title = stringResource(R.string.settings_auto_repair),
                                summary = stringResource(R.string.settings_auto_repair_summary),
                                checked = settings.autoRepair,
                                onCheckedChange = { value -> viewModel.updateSettings { it.copy(autoRepair = value) } },
                            )
                        },
                    )
                    add(
                        GroupRow("background_check") { modifier ->
                            SwitchRow(
                                modifier = modifier,
                                title = stringResource(R.string.settings_background_check),
                                summary = stringResource(R.string.settings_background_check_summary),
                                checked = settings.backgroundCheck,
                                onCheckedChange = { value -> viewModel.updateSettings { it.copy(backgroundCheck = value) } },
                            )
                        },
                    )
                    if (settings.backgroundCheck) {
                        add(
                            GroupRow("background_interval") { modifier ->
                                ListItem(
                                    modifier = modifier,
                                    colors = groupedRowColors(),
                                    headlineContent = { Text(stringResource(R.string.settings_background_interval)) },
                                    supportingContent = {
                                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            BACKGROUND_CHECK_HOURS.forEachIndexed { index, hours ->
                                                SegmentedButton(
                                                    selected = settings.backgroundCheckHours == hours,
                                                    onClick = { viewModel.updateSettings { it.copy(backgroundCheckHours = hours) } },
                                                    shape = SegmentedButtonDefaults.itemShape(index, BACKGROUND_CHECK_HOURS.size, baseShape = ControlShape),
                                                    icon = {},
                                                ) { Text(stringResource(R.string.settings_hours, hours)) }
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )

            group(
                R.string.settings_section_app_list,
                listOf(
                    GroupRow("start_filter") { modifier ->
                        ListItem(
                            modifier = modifier,
                            colors = groupedRowColors(),
                            headlineContent = { Text(stringResource(R.string.settings_start_filter)) },
                            supportingContent = {
                                AppTypeFilterRow(
                                    selected = settings.startFilter,
                                    onSelect = { filter -> viewModel.updateSettings { it.copy(startFilter = filter) } },
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            },
                        )
                    },
                    GroupRow("refresh_on_launch") { modifier ->
                        SwitchRow(
                            modifier = modifier,
                            title = stringResource(R.string.settings_refresh_on_launch),
                            // D-pad devices have no pull to refresh (see AppListPane).
                            summary = stringResource(
                                if (isDpadDevice()) R.string.settings_refresh_on_launch_summary_dpad else R.string.settings_refresh_on_launch_summary,
                            ),
                            checked = settings.refreshOnLaunch,
                            onCheckedChange = { value -> viewModel.updateSettings { it.copy(refreshOnLaunch = value) } },
                        )
                    },
                ),
            )

            group(
                R.string.settings_section_data,
                listOf(
                    GroupRow("export") { modifier ->
                        ClickRow(
                            modifier = modifier,
                            title = stringResource(R.string.settings_export),
                            summary = stringResource(R.string.settings_export_summary),
                            onClick = {
                                val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                                exportLauncher.launch("applingo-locales-$date.json")
                            },
                        )
                    },
                    GroupRow("import") { modifier ->
                        ClickRow(
                            modifier = modifier,
                            title = stringResource(R.string.settings_import),
                            summary = stringResource(R.string.settings_import_summary),
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        )
                    },
                ),
            )

            group(
                R.string.settings_section_about,
                listOf(
                    GroupRow("own_language") { modifier ->
                        ClickRow(
                            modifier = modifier,
                            title = stringResource(R.string.settings_own_language),
                            summary = ownLocale?.let(LocaleTags::nativeName) ?: stringResource(R.string.locale_follow_system),
                            onClick = { showLanguageDialog = true },
                        )
                    },
                    GroupRow("source") { modifier ->
                        ClickRow(
                            modifier = modifier,
                            title = stringResource(R.string.settings_source),
                            summary = REPOSITORY_URL.removePrefix("https://"),
                            onClick = { open(Intent(Intent.ACTION_VIEW, REPOSITORY_URL.toUri())) },
                        )
                    },
                    GroupRow("version") { modifier ->
                        ListItem(
                            modifier = modifier,
                            colors = groupedRowColors(),
                            headlineContent = { Text(stringResource(R.string.settings_version)) },
                            supportingContent = { Text(viewModel.versionName + " · " + stringResource(R.string.settings_license)) },
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun OwnLanguageDialog(
    options: List<String>,
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_own_language)) },
        text = {
            // Scrolls when the dialog is shorter than the list (landscape), instead of squeezing the rows.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<String?>(null) + options).forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { onSelect(tag) },
                    ) {
                        RadioButton(selected = tag == current, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(tag?.let(LocaleTags::nativeName) ?: stringResource(R.string.locale_follow_system))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, shape = ControlShape) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
        colors = groupedRowColors(),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun ClickRow(title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = groupedRowColors(),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
    }
}

/** One setting; [content] gets the modifier that draws its slice of the group's card. */
private class GroupRow(val key: String, val content: @Composable (Modifier) -> Unit)

/** A titled group of settings drawn as one card, like the cards of the detail page. */
private fun LazyListScope.group(title: Int?, rows: List<GroupRow>) {
    item(key = "header:${title ?: rows.first().key}") {
        if (title != null) GroupHeader(stringResource(title)) else Spacer(Modifier.height(8.dp))
    }
    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
        row.content(Modifier.groupedRow(GroupPosition.of(index, rows.size)))
    }
}

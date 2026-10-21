package io.github.ernisn.applingo.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.AppExtras
import io.github.ernisn.applingo.ui.AppIcon
import io.github.ernisn.applingo.ui.AppRow
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.components.LocalePriorityList
import io.github.ernisn.applingo.ui.safeArea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailPane(
    viewModel: MainViewModel,
    packageName: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onEditLanguages: () -> Unit,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val row = rows[packageName]
    val extras by produceState<AppExtras?>(null, packageName, row?.app?.lastUpdateTime) {
        value = viewModel.extras(packageName)
    }

    Scaffold(
        modifier = Modifier.safeArea(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(row?.app?.label ?: packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (row == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_not_installed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { HeaderCard(row) }
            item {
                LanguageCard(
                    row = row,
                    systemLocales = viewModel.systemLocales,
                    onEdit = onEditLanguages,
                    onReset = { viewModel.applyLocales(packageName, emptyList()) },
                )
            }
            item { ActionsCard(viewModel, row, extras) }
            item { InfoCard(row, extras) }
        }
    }
}

@Composable
fun EmptyDetailPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.detail_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderCard(row: AppRow) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            AppIcon(row.app.packageName, row.app.lastUpdateTime, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(row.app.label, style = MaterialTheme.typography.titleLarge)
                Text(row.app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.app.versionName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(row: AppRow, systemLocales: List<String>, onEdit: () -> Unit, onReset: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_language), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.detail_order_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalePriorityList(chain = row.locales, systemLocales = systemLocales)
            val (statusText, isProblem) = when (row.status) {
                LocaleStatus.None -> stringResource(R.string.status_none) to false
                LocaleStatus.Applied -> stringResource(R.string.status_applied) to false
                LocaleStatus.Pending -> stringResource(R.string.status_pending) to false
                LocaleStatus.Mismatch -> stringResource(R.string.status_mismatch, chainText(row.systemLocales.orEmpty())) to true
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isProblem) Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                if (row.locales.isNotEmpty()) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset_to_system)) }
                }
                Button(onClick = onEdit) { Text(stringResource(R.string.action_change_language)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsCard(viewModel: MainViewModel, row: AppRow, extras: AppExtras?) {
    val context = LocalContext.current
    fun launch(intent: Intent?) {
        if (intent == null) return
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Settings screen not available on this device; nothing sensible to fall back to.
        }
    }
    val packageName = row.app.packageName
    val launchIntent = viewModel.launchIntent(packageName)
    Card(Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            if (launchIntent != null) {
                OutlinedButton(onClick = { launch(launchIntent) }) { Text(stringResource(R.string.action_open)) }
                OutlinedButton(onClick = { viewModel.restartApp(packageName) }) {
                    Text(stringResource(R.string.action_restart_app))
                }
            }
            OutlinedButton(onClick = { viewModel.forceStop(packageName) }) {
                Text(stringResource(R.string.action_force_stop))
            }
            OutlinedButton(onClick = { launch(viewModel.appInfoIntent(packageName)) }) {
                Text(stringResource(R.string.action_app_info))
            }
            if (extras?.declaredLocales != null) {
                OutlinedButton(onClick = { launch(viewModel.systemLanguageIntent(packageName)) }) {
                    Text(stringResource(R.string.action_system_language))
                }
            }
        }
    }
}

@Composable
private fun InfoCard(row: AppRow, extras: AppExtras?) {
    val context = LocalContext.current
    val app = row.app
    fun date(millis: Long) = if (millis <= 0) "—" else
        DateFormat.getMediumDateFormat(context).format(millis) + " " + DateFormat.getTimeFormat(context).format(millis)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            InfoRow(stringResource(R.string.info_version), "${app.versionName ?: "—"} (${app.versionCode})")
            InfoRow(stringResource(R.string.info_sdk), stringResource(R.string.info_sdk_value, app.targetSdk, app.minSdk))
            InfoRow(stringResource(R.string.info_installed), date(app.firstInstallTime))
            InfoRow(stringResource(R.string.info_updated), date(app.lastUpdateTime))
            InfoRow(stringResource(R.string.info_installer), extras?.installer ?: "—")
            InfoRow(
                stringResource(R.string.info_type),
                stringResource(
                    when {
                        app.isUpdatedSystem -> R.string.info_type_updated_system
                        app.isSystem -> R.string.info_type_system
                        else -> R.string.info_type_user
                    },
                ),
            )
            InfoRow("UID", app.uid.toString())
            InfoRow(
                stringResource(R.string.info_declared_locales),
                when {
                    extras == null -> "…"
                    extras.declaredLocales == null -> stringResource(R.string.info_declared_none)
                    else -> pluralStringResource(
                        R.plurals.info_declared_count,
                        extras.declaredLocales.size,
                        extras.declaredLocales.size,
                    )
                },
            )
            InfoRow(
                stringResource(R.string.info_system_value),
                row.systemLocales?.let { chainText(it) } ?: stringResource(R.string.info_system_value_unknown),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun chainText(locales: List<String>): String =
    if (locales.isEmpty()) stringResource(R.string.locale_follow_system)
    else locales.joinToString(", ") { LocaleTags.nativeName(it) }

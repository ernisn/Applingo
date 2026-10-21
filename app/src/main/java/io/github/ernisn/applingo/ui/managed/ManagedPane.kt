package io.github.ernisn.applingo.ui.managed

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleTags
import io.github.ernisn.applingo.data.ActivityEntry
import io.github.ernisn.applingo.data.ActivityKind
import io.github.ernisn.applingo.shizuku.ShizukuState
import io.github.ernisn.applingo.ui.AppIcon
import io.github.ernisn.applingo.ui.LocaleStatus
import io.github.ernisn.applingo.ui.MainViewModel
import io.github.ernisn.applingo.ui.safeArea
import io.github.ernisn.applingo.ui.theme.ControlShape
import io.github.ernisn.applingo.ui.ManagedApp
import io.github.ernisn.applingo.ui.ManagedState
import io.github.ernisn.applingo.ui.applist.LocaleBadge
import io.github.ernisn.applingo.ui.components.PackageLine
import io.github.ernisn.applingo.ui.components.ShizukuStatusCard

private const val MAX_ACTIVITY_SHOWN = 100

/** Every app Applingo keeps a language for, the result of the last check, and recent activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedPane(viewModel: MainViewModel, selectedPackage: String?, onOpenApp: (String) -> Unit) {
    val state by viewModel.managedState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.safeArea(),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.managed_title)) }, windowInsets = WindowInsets(0)) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ShizukuStatusCard(
                    state = state.shizuku,
                    onRequestPermission = viewModel::requestShizukuPermission,
                    onRetry = viewModel::retryShizuku,
                    shizukuLaunchIntent = viewModel::shizukuLaunchIntent,
                )
            }
            item { SummaryCard(state, onCheckNow = viewModel::checkNow) }

            sectionHeader(R.string.managed_section_apps)
            if (state.apps.isEmpty()) {
                item { Hint(stringResource(R.string.managed_empty)) }
            }
            items(state.apps, key = { "app:${it.packageName}" }) { managed ->
                ManagedAppItem(
                    managed = managed,
                    selected = managed.packageName == selectedPackage,
                    onOpen = { onOpenApp(managed.packageName) },
                    onForget = { viewModel.forget(managed.packageName) },
                )
            }

            item(key = "activity-header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.managed_section_activity),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.activity.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearActivity, shape = ControlShape) {
                            Text(stringResource(R.string.action_clear_activity))
                        }
                    }
                }
            }
            if (state.activity.isEmpty()) {
                item { Hint(stringResource(R.string.managed_activity_empty)) }
            }
            items(state.activity.take(MAX_ACTIVITY_SHOWN)) { ActivityItem(it) }
        }
    }
}

@Composable
private fun SummaryCard(state: ManagedState, onCheckNow: () -> Unit) {
    val attention = state.apps.count { it.status == LocaleStatus.Mismatch || it.row == null }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.managed_summary_count, state.apps.size, state.apps.size),
                style = MaterialTheme.typography.titleMedium,
            )
            if (attention > 0) {
                Text(
                    text = pluralStringResource(R.plurals.managed_summary_attention, attention, attention),
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
    }
}

@Composable
private fun ManagedAppItem(managed: ManagedApp, selected: Boolean, onOpen: () -> Unit, onForget: () -> Unit) {
    val installed = managed.row != null
    ListItem(
        modifier = if (installed) Modifier.clickable(onClick = onOpen) else Modifier,
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            val row = managed.row
            if (row != null) {
                AppIcon(row.app.packageName, row.app.lastUpdateTime, dimmed = !row.app.isEnabled)
            } else {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            }
        },
        headlineContent = { Text(managed.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val row = managed.row
            if (row != null) {
                PackageLine(row.app)
            } else {
                Text(
                    text = stringResource(R.string.managed_not_installed) + " · " + managed.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            if (installed) {
                if (managed.locales.isNotEmpty()) LocaleBadge(managed.locales, managed.status, onHighlight = selected)
            } else {
                IconButton(onClick = onForget) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_forget))
                }
            }
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
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

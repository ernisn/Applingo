package io.github.ernisn.applingo.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.shizuku.ShizukuState

private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

/** Explains what is missing for Shizuku and offers the one action that fixes it. Hidden when connected. */
@Composable
fun ShizukuStatusCard(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    shizukuLaunchIntent: () -> Intent?,
) {
    val context = LocalContext.current
    AnimatedVisibility(visible = state !is ShizukuState.Connected && state != ShizukuState.Checking) {
        val isError = state is ShizukuState.Failed || state == ShizukuState.Unsupported
        Card(
            colors = if (isError) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.shizuku_title), style = MaterialTheme.typography.titleSmall)
                Text(statusText(state), style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val openShizuku = shizukuLaunchIntent()
                    when (state) {
                        ShizukuState.Connecting -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        ShizukuState.NotInstalled -> FilledTonalButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, SHIZUKU_DOWNLOAD_URL.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text(stringResource(R.string.shizuku_action_get)) }
                        ShizukuState.PermissionRequired -> FilledTonalButton(onClick = onRequestPermission) {
                            Text(stringResource(R.string.shizuku_action_grant))
                        }
                        else -> {
                            if (state !is ShizukuState.Failed && openShizuku != null) {
                                TextButton(onClick = { context.startActivity(openShizuku) }) {
                                    Text(stringResource(R.string.shizuku_action_open))
                                }
                            }
                            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(state: ShizukuState): String = when (state) {
    ShizukuState.NotInstalled -> stringResource(R.string.shizuku_not_installed)
    ShizukuState.NotRunning -> stringResource(R.string.shizuku_not_running)
    ShizukuState.Unsupported -> stringResource(R.string.shizuku_unsupported)
    ShizukuState.PermissionRequired -> stringResource(R.string.shizuku_permission_required)
    ShizukuState.PermissionDenied -> stringResource(R.string.shizuku_permission_denied)
    ShizukuState.Connecting, ShizukuState.Checking -> stringResource(R.string.shizuku_connecting)
    is ShizukuState.Failed -> stringResource(R.string.shizuku_failed, state.message)
    is ShizukuState.Connected -> ""
}

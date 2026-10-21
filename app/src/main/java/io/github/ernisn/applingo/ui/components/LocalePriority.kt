package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.core.LocaleCatalog
import io.github.ernisn.applingo.core.LocaleTags

/**
 * Read-only view of an app's locale chain: numbered by priority, ending with the system language as
 * the fixed, lowest-priority fallback.
 */
@Composable
fun LocalePriorityList(chain: List<String>, systemLocales: List<String>, modifier: Modifier = Modifier) {
    val uiLocale = LocalConfiguration.current.locales[0]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chain.forEachIndexed { index, tag ->
            val option = remember(tag, uiLocale) { LocaleCatalog.option(tag, uiLocale) }
            PriorityRow(number = index + 1, title = option.nativeName, subtitle = "${option.localizedName} · ${option.tag}")
        }
        SystemFallbackRow(systemLocales)
    }
}

/**
 * Editable locale chain: drag the handle to reorder (with move up/down accessibility actions for
 * screen readers and keyboards), remove with ×. The system language stays last and can't be moved.
 */
@Composable
fun EditablePriorityList(chain: SnapshotStateList<String>, systemLocales: List<String>, modifier: Modifier = Modifier) {
    val uiLocale = LocalConfiguration.current.locales[0]
    val haptics = LocalHapticFeedback.current
    var dragged by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    fun move(tag: String, by: Int) {
        val from = chain.indexOf(tag)
        val to = from + by
        if (from >= 0 && to in chain.indices) chain.add(to, chain.removeAt(from))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chain.forEachIndexed { index, tag ->
            key(tag) {
                var rowHeight by remember { mutableIntStateOf(0) }
                val isDragged = dragged == tag
                val option = remember(tag, uiLocale) { LocaleCatalog.option(tag, uiLocale) }
                PriorityRow(
                    number = index + 1,
                    title = option.nativeName,
                    subtitle = "${option.localizedName} · ${option.tag}",
                    modifier = Modifier
                        .onSizeChanged { rowHeight = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) offset else 0f
                            shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                            shape = RoundedCornerShape(12.dp)
                            clip = isDragged
                        }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveUp) { move(tag, -1); true },
                                CustomAccessibilityAction(moveDown) { move(tag, 1); true },
                            )
                        },
                    highlighted = isDragged,
                ) {
                    IconButton(onClick = { chain.remove(tag) }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_remove))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(tag) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragged = tag
                                        offset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = { dragged = null; offset = 0f },
                                    onDragCancel = { dragged = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val height = rowHeight.toFloat()
                                    val position = chain.indexOf(tag)
                                    // Swap with a neighbour once dragged past half its height.
                                    if (height > 0f && offset > height / 2 && position < chain.lastIndex) {
                                        move(tag, 1)
                                        offset -= height
                                    } else if (height > 0f && offset < -height / 2 && position > 0) {
                                        move(tag, -1)
                                        offset += height
                                    }
                                }
                            },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_drag_handle),
                            contentDescription = stringResource(R.string.action_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        SystemFallbackRow(systemLocales)
    }
}

@Composable
private fun PriorityRow(
    number: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** The system language: always the last fallback, shown dimmed so it doesn't read as a choice. */
@Composable
private fun SystemFallbackRow(systemLocales: List<String>) {
    val names = systemLocales.joinToString(", ") { LocaleTags.nativeName(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // Outlined rather than filled: cards already use the container colors.
            modifier = Modifier
                .size(28.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            Icon(
                painterResource(R.drawable.ic_translate),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                stringResource(R.string.locale_follow_system),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.locale_system_fallback, names),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

package io.github.ernisn.applingo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ernisn.applingo.ui.components.Backdrop
import io.github.ernisn.applingo.ui.components.FrostBlur
import io.github.ernisn.applingo.ui.components.backdropBlur
import io.github.ernisn.applingo.ui.components.frostedBorder
import io.github.ernisn.applingo.ui.theme.TRANSLUCENT_FILL_ALPHA
import io.github.ernisn.applingo.ui.theme.TRANSLUCENT_SURFACE_ALPHA
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Top-level navigation as a floating island instead of a bar across the screen. On narrow windows it
 * floats just above the system navigation bar, over the content, which runs edge to edge behind it
 * and is seen through it, blurred: it leaves for secondary pages (an app's detail, the language
 * picker, the uninstalled apps) and for a selection's actions. On wide windows it stands upright at
 * the start, beside the content rather than over it, as the rail did.
 */

/** Corners of the island: about the same ratio to its height as the controls' (see Theme.kt). */
private val IslandShape = RoundedCornerShape(24.dp)

/**
 * The selection indicator inside it, a little in from its item so the island's edge shows round it:
 * [IslandInset] + [IndicatorInset] from the island's edge, with the island's corners less that, so
 * the two stay concentric.
 */
private val IslandInset = 4.dp
private val IndicatorInset = 2.dp
private val IndicatorRadius = 18.dp

/** Between the floating island and the system bar, cutout or screen edge below it (above and below the upright one). */
val IslandGap = 4.dp

/**
 * Items are wider lying side by side than stacked. Stacked, the width comes off the content, so they
 * are narrow and tall instead.
 */
private val RowItemWidth = 92.dp
private val ColumnItemWidth = 72.dp
private val ColumnItemPadding = 18.dp
private val ColumnItemSpacing = 8.dp

/** The indicator moving to a newly selected item. */
private val SettleSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/** The indicator catching up with a finger held on the island: quick, so it keeps under it. */
private val FollowSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)

/** What the island's host knows of it, shared with the panes below (see [LocalNavIsland]). */
class NavIslandState {
    private val hideRequests = mutableStateListOf<Any>()

    /** Floating at the bottom (narrow windows) rather than upright beside the content. */
    var atBottom by mutableStateOf(true)

    /** Last measured height, kept while the island is hidden. */
    var heightPx by mutableIntStateOf(0)

    /** Shown at the bottom: on top-level pages, unless a secondary page or a selection asked it away. */
    val shown: Boolean get() = atBottom && hideRequests.isEmpty()

    /** Height the island takes from the bottom of the window, gap included, while shown. */
    fun reservedPx(gapPx: Int): Int = if (shown) heightPx + gapPx else 0

    internal fun request(token: Any) {
        hideRequests += token
    }

    internal fun release(token: Any) {
        hideRequests -= token
    }
}

val LocalNavIsland = staticCompositionLocalOf<NavIslandState?> { null }

/** Makes the island leave while [hide] is true and this is composed: a secondary page, a selection. */
@Composable
fun HideNavIsland(hide: Boolean = true) {
    val island = LocalNavIsland.current ?: return
    if (!hide) return
    DisposableEffect(island) {
        val token = Any()
        island.request(token)
        onDispose { island.release(token) }
    }
}

/**
 * Space at the bottom of the window that floating chrome covers: the system navigation bar (or the
 * keyboard, if higher) and, on top-level pages of narrow windows, the island. Lists that run to the
 * bottom of the window pad their end by it, so the last row can be scrolled clear.
 */
@Composable
fun bottomChrome(): Dp {
    val density = LocalDensity.current
    val island = LocalNavIsland.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(density)
    val keyboard = WindowInsets.ime.getBottom(density)
    val reserved = island?.reservedPx(with(density) { IslandGap.roundToPx() }) ?: 0
    return with(density) { maxOf(keyboard, bars + reserved).toDp() }
}

class NavIslandItem(val label: String, val selected: Boolean, val onClick: () -> Unit, val icon: @Composable () -> Unit)

/**
 * The island itself: a row of items at the bottom, or a column beside the content ([vertical]).
 *
 * One highlight only: a single indicator slides to the selected item, and the item under a finger
 * gives way slightly rather than showing a ripple on top of the indicator. The indicator follows a
 * finger held on the island, and letting go selects the item nearest to it; each item's colour
 * follows the indicator as it passes. With a D-pad the focused item gets an outline, as the search
 * field does.
 *
 * Lying over the content it is translucent, with the content behind it blurred ([backdrop]);
 * beside the content it is opaque.
 */
@Composable
fun NavIsland(items: List<NavIslandItem>, vertical: Boolean, modifier: Modifier = Modifier, backdrop: Backdrop? = null) {
    val count = items.size
    val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selected)
    // Where the indicator is, counted in items: whole on an item, fractional between two.
    val position = remember { Animatable(selected.toFloat()) }
    // The point under a finger held on the island, in the same units; null when there is none.
    var held by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected) {
        if (held == null) position.animateTo(selected.toFloat(), SettleSpring)
    }

    val scheme = MaterialTheme.colorScheme
    // Over the content: the window background first, so only the blurred copy shows through and not
    // the sharp content where it has no background of its own (headers, gaps between cards), then
    // the copy, then a light tint of the island's colour. The indicator is translucent too, or it
    // sat on the frosted island like a solid patch.
    val container = if (backdrop != null) scheme.surfaceContainerHigh.copy(alpha = TRANSLUCENT_SURFACE_ALPHA) else scheme.surfaceContainerHigh
    val indicatorColor = if (backdrop != null) scheme.secondaryContainer.copy(alpha = TRANSLUCENT_FILL_ALPHA) else scheme.secondaryContainer
    val base = scheme.background
    // Lying side by side, the items follow the reading direction.
    val mirrored = !vertical && LocalLayoutDirection.current == LayoutDirection.Rtl
    val spacing = if (vertical) ColumnItemSpacing else 0.dp

    val indicator = Modifier.drawBehind {
        val gap = spacing.toPx()
        val inset = IndicatorInset.toPx()
        val radius = CornerRadius(IndicatorRadius.toPx())
        val at = if (mirrored) count - 1 - position.value else position.value
        if (vertical) {
            val itemHeight = (size.height - gap * (count - 1)) / count
            drawRoundRect(
                indicatorColor,
                Offset(inset, at * (itemHeight + gap) + inset),
                Size(size.width - 2 * inset, itemHeight - 2 * inset),
                radius,
            )
        } else {
            val itemWidth = size.width / count
            drawRoundRect(
                indicatorColor,
                Offset(at * itemWidth + inset, inset),
                Size(itemWidth - 2 * inset, size.height - 2 * inset),
                radius,
            )
        }
    }

    // Touch is handled here for the whole island, edge included, before the items see it: a press
    // brings the indicator under the finger, it follows the finger along the island, and letting go
    // selects the nearest item. The items' own click handling stays for D-pad, keyboard and
    // accessibility services, which don't go through touch.
    val drag = Modifier.pointerInput(count, vertical, mirrored) {
        val inset = IslandInset.toPx()
        val gap = spacing.toPx()

        /** The point along the island under [offset], in items, from the first item's centre. */
        fun itemAt(offset: Offset): Float {
            val length = (if (vertical) size.height else size.width) - 2 * inset
            val pitch = (length + gap) / count
            val along = (if (vertical) offset.y else offset.x) - inset
            val at = ((along - (pitch - gap) / 2) / pitch).coerceIn(0f, count - 1f)
            return if (mirrored) count - 1 - at else at
        }

        fun follow(to: Float) {
            held = to
            scope.launch { position.animateTo(to, FollowSpring) }
        }

        fun settle(to: Int) {
            held = null
            scope.launch { position.animateTo(to.toFloat(), SettleSpring) }
        }

        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            down.consume()
            follow(itemAt(down.position))
            var released = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // Already consumed: the gesture was cancelled (by the system, or the island
                        // leaving) rather than ended by lifting the finger.
                        if (!change.isConsumed) {
                            change.consume()
                            val target = (held ?: currentSelected.toFloat()).roundToInt()
                            settle(target)
                            released = true
                            currentItems[target].onClick()
                        }
                        break
                    }
                    if (change.positionChanged()) {
                        change.consume()
                        follow(itemAt(change.position))
                    }
                }
            } finally {
                if (!released) settle(currentSelected)
            }
        }
    }

    val surface = modifier
        .clip(IslandShape)
        .then(if (backdrop != null) Modifier.drawBehind { drawRect(base) }.backdropBlur(backdrop, FrostBlur) else Modifier)
        .drawBehind { drawRect(container) }
        .border(frostedBorder(), IslandShape)
        .then(drag)
        .padding(IslandInset)
        .then(indicator)
        .selectableGroup()
    // How much an item is under the indicator, from 0 (not at all) to 1 (right under it).
    val emphasis = { index: Int -> (1f - abs(position.value - index)).coerceIn(0f, 1f) }
    // Only a change of item recomposes, not every move of the finger.
    val pressedIndex by remember { derivedStateOf { held?.roundToInt() } }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(ColumnItemSpacing), modifier = surface) {
            items.forEachIndexed { index, item ->
                IslandItem(
                    item = item,
                    pressed = index == pressedIndex,
                    emphasis = { emphasis(index) },
                    modifier = Modifier.width(ColumnItemWidth).padding(vertical = ColumnItemPadding),
                )
            }
        }
    } else {
        Row(surface) {
            items.forEachIndexed { index, item ->
                IslandItem(
                    item = item,
                    pressed = index == pressedIndex,
                    emphasis = { emphasis(index) },
                    modifier = Modifier.width(RowItemWidth).padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IslandItem(item: NavIslandItem, pressed: Boolean, emphasis: () -> Float, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val content = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSecondaryContainer, emphasis())
    val focusColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            // On the indicator's outline, so focus and selection line up.
            .drawBehind {
                if (!focused) return@drawBehind
                val inset = IndicatorInset.toPx()
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    focusColor,
                    Offset(inset + stroke / 2, inset + stroke / 2),
                    Size(size.width - 2 * inset - stroke, size.height - 2 * inset - stroke),
                    CornerRadius(IndicatorRadius.toPx() - stroke / 2),
                    style = Stroke(stroke),
                )
            }
            .selectable(
                selected = item.selected,
                onClick = item.onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .then(modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { item.icon() }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

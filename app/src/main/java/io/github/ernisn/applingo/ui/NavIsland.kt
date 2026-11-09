package io.github.ernisn.applingo.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.ernisn.applingo.ui.theme.highlight
import io.github.ernisn.applingo.ui.theme.highlightAlpha
import io.github.ernisn.applingo.ui.theme.onHighlight
import io.github.ernisn.applingo.ui.theme.TRANSLUCENT_SURFACE_ALPHA
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Top-level navigation as a floating island instead of a bar across the screen, over the content,
 * which runs edge to edge behind it and is seen through it, blurred. On narrow windows it floats just
 * above the system navigation bar and leaves for secondary pages (an app's detail, the language
 * picker, the uninstalled apps) and for a selection's actions. On wide windows it stands upright at
 * the start, where the rail was: content keeps clear of it at rest, and slides behind it.
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

/** Width of the upright island, which content keeps clear of at rest. */
val UprightIslandWidth = ColumnItemWidth + IslandInset * 2

/** The indicator (and the pages with it) catching up with a finger held on the island: quick, so it keeps under it. */
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

/**
 * Makes the island leave while [hide] is true and this is composed on the page shown (not one sliding
 * past it): a secondary page, a selection.
 */
@Composable
fun HideNavIsland(hide: Boolean = true) {
    val island = LocalNavIsland.current ?: return
    if (!hide || !LocalPageActive.current) return
    DisposableEffect(island) {
        val token = Any()
        island.request(token)
        onDispose { island.release(token) }
    }
}

/**
 * How many times the island's item for this destination was tapped while it was already selected.
 * Each time, its page goes back to its top level ([OnBackToTop]).
 */
val LocalBackToTop = compositionLocalOf { 0 }

/**
 * Runs [action] each time the island's item for this destination is tapped again while selected:
 * back from deeper levels (an app's detail, the picker, the uninstalled apps) to the top one.
 */
@Composable
fun OnBackToTop(action: () -> Unit) {
    val requests = LocalBackToTop.current
    val currentAction by rememberUpdatedState(action)
    // Requests from before this was composed are not for it.
    var handled by remember { mutableIntStateOf(requests) }
    LaunchedEffect(requests) {
        if (requests != handled) {
            handled = requests
            currentAction()
        }
    }
}

/**
 * How many times the island's item for this destination was tapped twice in a row, quickly. Each
 * time, its page scrolls back to the top of its list ([OnScrollToTop]).
 */
val LocalScrollToTop = compositionLocalOf { 0 }

/**
 * Runs [action] each time the island's item for this destination is double-tapped: scrolls the list
 * shown back to its start. The first tap of the two already took the page back to its top level
 * ([OnBackToTop]), or to the destination. A double tap during the scroll starts it over.
 */
@Composable
fun OnScrollToTop(action: suspend () -> Unit) {
    val requests = LocalScrollToTop.current
    val currentAction by rememberUpdatedState(action)
    // Requests from before this was composed are not for it.
    var handled by remember { mutableIntStateOf(requests) }
    LaunchedEffect(requests) {
        if (requests != handled) {
            handled = requests
            currentAction()
        }
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
 * gives way slightly rather than showing a ripple on top of the indicator. The indicator stands at
 * [position], which the pages share: they slide as it moves, and it slides as they are swiped. A
 * press takes it to the item pressed; a finger then dragged along the island takes it along, the
 * pages with it, and letting go selects the item nearest to it. Each item's colour follows the
 * indicator as it passes. With a D-pad the focused item gets an outline, as the search field does.
 *
 * Letting go on the item selected only clicks it again (back to the top of its page) after a tap; a
 * finger that wandered off and came back just puts the indicator back ([onReturn]), as does a
 * gesture the system cancels.
 *
 * Without [dragToSwitch] (Settings → Navigation bar dragging) the items are plain buttons: a press
 * moves nothing, letting go selects the item pressed, and a finger that moves off first selects
 * nothing.
 *
 * Translucent, with the content behind it blurred ([backdrop]); opaque without one.
 */
@Composable
fun NavIsland(
    items: List<NavIslandItem>,
    position: PagePosition,
    vertical: Boolean,
    onReturn: () -> Unit,
    dragToSwitch: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val count = items.size
    val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnReturn by rememberUpdatedState(onReturn)
    // The point under a finger held on the island, counted in items like [position]; null when
    // there is none.
    var held by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()

    val scheme = MaterialTheme.colorScheme
    // Over the content: the window background first, so only the blurred copy shows through and not
    // the sharp content where it has no background of its own (headers, gaps between cards), then
    // the copy, then a light tint of the island's colour. The indicator is translucent too, but less
    // so, and the item under it drawn in the opposite of the others (see highlight in Theme.kt).
    val container = if (backdrop != null) scheme.surfaceContainerHigh.copy(alpha = TRANSLUCENT_SURFACE_ALPHA) else scheme.surfaceContainerHigh
    val indicatorColor = scheme.highlight.copy(alpha = scheme.highlightAlpha)
    val base = scheme.background
    // Lying side by side, the items follow the reading direction.
    val mirrored = !vertical && LocalLayoutDirection.current == LayoutDirection.Rtl
    val spacing = if (vertical) ColumnItemSpacing else 0.dp
    // Where the indicator shows: the pages may be pulled a little past the first or last one (see
    // PageStrip.kt), the indicator stays on the island's items.
    val shownAt = { position.value.coerceIn(0f, count - 1f) }

    val indicator = Modifier.drawBehind {
        val gap = spacing.toPx()
        val inset = IndicatorInset.toPx()
        val radius = CornerRadius(IndicatorRadius.toPx())
        val at = if (mirrored) count - 1 - shownAt() else shownAt()
        if (at <= -1f || at >= count.toFloat()) return@drawBehind
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
    // brings the indicator to the item under the finger, a drag takes it along the island, and
    // letting go selects the nearest item, whose click settles the indicator (and the pages) on it.
    // The items' own click handling stays for D-pad, keyboard and accessibility services, which
    // don't go through touch.
    //
    // A press goes to the middle of the item, wherever on it the finger lands: following the finger
    // from the start sent the pages off towards a neighbour and back whenever the middle item was
    // tapped off centre (the end items have nothing beyond them, so there it hardly showed).
    val drag = Modifier.pointerInput(count, vertical, mirrored, dragToSwitch) {
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

        /** Lets go on [to], whose click (selecting it, or the item already selected) settles the indicator. */
        fun select(to: Int) {
            held = null
            currentItems[to].onClick()
        }

        /** Lets go without choosing: the indicator goes back to the item selected. */
        fun putBack() {
            held = null
            currentOnReturn()
        }

        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            down.consume()
            val pressed = itemAt(down.position).roundToInt().toFloat()
            if (dragToSwitch) follow(pressed) else held = pressed
            var dragging = false
            var released = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // Already consumed: the gesture was cancelled (by the system, or the island
                        // leaving) rather than ended by lifting the finger.
                        if (!change.isConsumed) {
                            change.consume()
                            released = true
                            val target = (held ?: currentSelected.toFloat()).roundToInt()
                            if (dragging && (!dragToSwitch || target == currentSelected)) putBack() else select(target)
                        }
                        break
                    }
                    if (change.positionChanged()) {
                        change.consume()
                        val moved = change.position - down.position
                        if (!dragging && abs(if (vertical) moved.y else moved.x) > viewConfiguration.touchSlop) dragging = true
                        if (dragging && dragToSwitch) follow(itemAt(change.position))
                    }
                }
            } finally {
                if (!released) putBack()
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
    // Where the indicator stands from each item, in items, as drawn (lying side by side in a
    // right-to-left layout, the items run from the right).
    val offsetFrom = { index: Int -> if (mirrored) index - shownAt() else shownAt() - index }
    // Only a change of item recomposes, not every move of the finger.
    val pressedIndex by remember { derivedStateOf { held?.roundToInt() } }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(ColumnItemSpacing), modifier = surface) {
            items.forEachIndexed { index, item ->
                IslandItem(
                    item = item,
                    pressed = index == pressedIndex,
                    indicatorOffset = { offsetFrom(index) },
                    vertical = true,
                    width = ColumnItemWidth,
                    padding = ColumnItemPadding,
                    spacing = ColumnItemSpacing,
                )
            }
        }
    } else {
        Row(surface) {
            items.forEachIndexed { index, item ->
                IslandItem(
                    item = item,
                    pressed = index == pressedIndex,
                    indicatorOffset = { offsetFrom(index) },
                    vertical = false,
                    width = RowItemWidth,
                    padding = 8.dp,
                    spacing = 0.dp,
                )
            }
        }
    }
}

/**
 * An item: its icon and label drawn twice, in the island's colour and, cut to where the indicator
 * covers it, in the indicator's. Halfway between two items each label is then half one colour and
 * half the other, readable on both; a colour blended between the two (as before) was mid-grey on a
 * mid-tone, readable on neither. [indicatorOffset] is where the indicator stands from this item, in
 * items; [spacing] lies between items.
 */
@Composable
private fun IslandItem(
    item: NavIslandItem,
    pressed: Boolean,
    indicatorOffset: () -> Float,
    vertical: Boolean,
    width: Dp,
    padding: Dp,
    spacing: Dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val scheme = MaterialTheme.colorScheme
    val focusColor = scheme.primary
    val pressScale = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    Box(
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
            .width(width),
    ) {
        IslandItemContent(item, scheme.onSurfaceVariant, Modifier.fillMaxWidth().padding(vertical = padding).then(pressScale))
        IslandItemContent(
            item,
            scheme.onHighlight,
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .drawWithContent {
                    val at = indicatorOffset()
                    if (at <= -1f || at >= 1f) return@drawWithContent
                    val inset = IndicatorInset.toPx()
                    if (vertical) {
                        val top = at * (size.height + spacing.toPx()) + inset
                        clipRect(left = inset, top = top, right = size.width - inset, bottom = top + size.height - 2 * inset) {
                            this@drawWithContent.drawContent()
                        }
                    } else {
                        val left = at * size.width + inset
                        clipRect(left = left, top = inset, right = left + size.width - 2 * inset, bottom = size.height - inset) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                .padding(vertical = padding)
                .then(pressScale),
        )
    }
}

@Composable
private fun IslandItemContent(item: NavIslandItem, color: Color, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        CompositionLocalProvider(LocalContentColor provides color) { item.icon() }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

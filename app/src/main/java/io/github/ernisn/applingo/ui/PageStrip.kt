package io.github.ernisn.applingo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.invalidateFocusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/*
 * Pages side by side that slide as one: the destinations (sideways under the island at the bottom,
 * up and down beside the upright one, along the island either way), and an app list's levels (list,
 * an app's detail, the language picker), sideways. Where they stand is one number, in pages, that the
 * island's indicator, a finger on the content and the back gesture all move, so whatever moves it,
 * everything follows.
 */

/** Pages settling: quick, and only a trace of overshoot, which would show a sliver of the next page. */
val PageSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/** How long the strip stands still before pages out of view are composed ahead ([PageStrip]'s prefetch). */
private const val PREFETCH_DELAY_MS = 300L

/**
 * Marks a page that is not one of those shown ([inert]): composed ahead out of view, or showing only
 * behind the upright island. It then takes no touches (a swipe across it still moves the strip),
 * focus doesn't go into it, and accessibility services don't see it. One node either way, flipped
 * rather than added and removed: swapping a modifier chain on two whole pages took part of a slow
 * tablet's first frame of every switch of destination.
 */
private fun Modifier.inert(inert: Boolean): Modifier = this then InertElement(inert)

private data class InertElement(val inert: Boolean) : ModifierNodeElement<InertNode>() {
    override fun create() = InertNode(inert)

    override fun update(node: InertNode) = node.update(inert)

    override fun InspectorInfo.inspectableProperties() {
        name = "inert"
        value = inert
    }
}

private class InertNode(var inert: Boolean) :
    Modifier.Node(),
    PointerInputModifierNode,
    SemanticsModifierNode,
    FocusPropertiesModifierNode {

    fun update(value: Boolean) {
        if (value == inert) return
        inert = value
        invalidateSemantics()
        invalidateFocusProperties()
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (inert && pass == PointerEventPass.Initial) {
            pointerEvent.changes.forEach { if (it.changedToDown()) it.consume() }
        }
    }

    override fun onCancelPointerInput() {}

    override val shouldClearDescendantSemantics: Boolean get() = inert

    override fun SemanticsPropertyReceiver.applySemantics() {}

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        if (inert) focusProperties.onEnter = { cancelFocusChange() }
    }
}

/** Where pages composed ahead are laid out: this many strip lengths off to their side. */
private const val FAR_OFF = 4

/**
 * Pulled past the strip's first or last page, the pages give a little and stiffen as they go: about
 * half the finger's way at first, never more than this, in pages. They used to stop dead and, let
 * go, spring out by the speed of the swipe and back, a bounce that came only after the finger lifted.
 */
private const val OVERSCROLL_LIMIT = 0.12f
private const val OVERSCROLL_GIVE = 0.55f

/** How far pages show pulled [beyond] (in pages, of the finger's way) past an end. */
private fun overscroll(beyond: Float): Float = OVERSCROLL_LIMIT * (1f - 1f / (1f + beyond * OVERSCROLL_GIVE / OVERSCROLL_LIMIT))

/** A swipe released faster than this goes on to the next page, however little it moved. */
private val FlingVelocity = 400.dp

/**
 * How far into a page counts as showing it. Settling leaves the position a hair off whole pages, and
 * composing a page for that would cost a whole page for nothing.
 */
private const val PAGE_SLACK = 0.02f

/**
 * Where a [PageStrip] stands, in pages from the first: whole on a page, fractional between two.
 * Animated (a tap on the island, a page settling), dragged (a finger on the content, the back
 * gesture) or animated after a finger held on the island. Read in layout and drawing only, so moving
 * it recomposes nothing but pages coming into or leaving view.
 */
@Stable
class PagePosition(initial: Float) {
    private val animatable = Animatable(initial)
    private var dragged by mutableStateOf<Float?>(null)

    // Where the finger has taken it, before holding back at the ends (see [dragBy]).
    private var fingerAt: Float? = null

    val value: Float get() = dragged ?: animatable.value

    /** A finger (or the back gesture) is moving it rather than an animation. */
    val isDragged: Boolean get() = dragged != null

    /** Moving, or about to: dragged, animating, or getting ready to ([prepare]). */
    val isMoving: Boolean get() = dragged != null || animatable.isRunning || upcoming != null

    /** Where it is about to go ([prepare]): the pages there are composed, off the screen, first. */
    var upcoming by mutableStateOf<Float?>(null)
        private set

    /**
     * Has the pages at [target] composed and laid out (off the screen) before moving there, then
     * returns: the frame that does it, and the one after. Moving at once, the first frame of the
     * slide was the one composing a page that was not there yet (an app's detail opened from the
     * list): on a slow tablet a third of a second, most of the slide, so the page seemed to jump in.
     * Not while dragged: the pages a finger or the back gesture brought in are there already.
     */
    suspend fun prepare(target: Float) {
        if (isDragged) return
        upcoming = target
        withFrameNanos {}
        withFrameNanos {}
    }

    /**
     * Hands the position to a finger, where it is now, then stops any animation. In that order:
     * stopping may suspend, and the first moves of a quick swipe, applied meanwhile, were lost when
     * the position was taken only afterwards.
     */
    suspend fun startDrag() {
        if (dragged == null) dragged = animatable.value
        fingerAt = dragged
        upcoming = null
        animatable.stop()
    }

    /**
     * Moves by [pages] within [range]. Past an end of [range] that is also an end of the strip
     * ([bounds]), it gives a little ([overscroll]); past one with pages still beyond it (a level the
     * swipe may not go to), it stops, without showing them.
     */
    fun dragBy(pages: Float, range: ClosedFloatingPointRange<Float>, bounds: ClosedFloatingPointRange<Float>) {
        val low = range.start
        val high = range.endInclusive
        var at = (fingerAt ?: value) + pages
        if (at < low && low > bounds.start) at = low
        if (at > high && high < bounds.endInclusive) at = high
        fingerAt = at
        dragged = when {
            at < low -> low - overscroll(low - at)
            at > high -> high + overscroll(at - high)
            else -> at
        }
    }

    fun dragTo(at: Float) {
        fingerAt = null
        dragged = at
    }

    /** From wherever it is, a drag included, keeping the speed of an animation it interrupts. */
    suspend fun animateTo(target: Float, spec: AnimationSpec<Float> = PageSpring, velocity: Float? = null) {
        val from = dragged
        if (from != null) {
            animatable.snapTo(from)
            dragged = null
            fingerAt = null
        }
        animatable.animateTo(target, spec, velocity ?: if (from != null) 0f else animatable.velocity)
        if (upcoming == target) upcoming = null
    }

    suspend fun snapTo(target: Float) {
        dragged = null
        fingerAt = null
        animatable.snapTo(target)
    }
}

/** The pages of a [PageStrip] taking up its length ([main]), and those on screen at all ([onScreen]). */
private data class PagesInView(val main: IntRange, val onScreen: IntRange)

/**
 * [count] pages side by side, sideways or, [vertical], one above the other, each a [visible]th of the
 * strip's length, standing at [position]. Nothing clips them: a page on its way out runs on past the
 * strip's start, behind the upright island and off the screen, and where the strip shows that far
 * ([reveal]: the island's width and what lies beside it), the page before those shown stays there at
 * rest, its end showing behind the island (see [inert]).
 *
 * The strip itself starts beside the island rather than under it, and only its pages reach behind:
 * D-pad focus searches the pages' focus groups by where they lie first, and a page reaching under
 * the island did not count as to the right of it, so Right from the island went nowhere.
 *
 * Only pages on screen are composed; the others keep their saved state under their [key] until they
 * come back, as a pane of the adaptive scaffold did while hidden. Up to [prefetchBefore] pages
 * before and [prefetchAfter] after are composed too, once the strip has stood still for a moment,
 * laid out off the screen (see [inert]): a page composed only as it came into view made the slide
 * stall there. On a slow tablet, composing the language picker as a swipe began took long enough
 * that the touch events piled up and the swipe lost most of its movement and all of its speed; Apps
 * to Settings caught on Configured, composed halfway. Pages where [position] is about to go
 * ([PagePosition.prepare]) are composed the same way.
 *
 * Only the pages taking up the length are the ones shown ([LocalPageActive]).
 */
@Composable
fun PageStrip(
    count: Int,
    position: PagePosition,
    key: (Int) -> String,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    visible: Int = 1,
    reveal: Dp = 0.dp,
    prefetchBefore: Int = 0,
    prefetchAfter: Int = 0,
    page: @Composable (Int) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    // From the last layout, for which pages show behind the island.
    var revealPx by remember { mutableIntStateOf(0) }
    var pageLengthPx by remember { mutableIntStateOf(0) }
    val inView by remember(count, visible) {
        derivedStateOf {
            val at = position.value
            val first = floor(at + PAGE_SLACK).toInt().coerceIn(0, count - 1)
            val last = (ceil(at - PAGE_SLACK).toInt() + visible - 1).coerceIn(first, count - 1)
            var onScreen = first
            // The page before [onScreen] ends (onScreen - at) pages from the strip's start, and shows
            // while that is less than [reveal] before it.
            while (onScreen > 0 && pageLengthPx > 0 && revealPx + (onScreen - at) * pageLengthPx >= 1f) onScreen--
            PagesInView(first..last, onScreen..last)
        }
    }
    // The pages on screen when the strip last stood still, round which pages are composed ahead.
    var restingAt by remember { mutableStateOf<IntRange?>(null) }
    LaunchedEffect(prefetchBefore, prefetchAfter) {
        if (prefetchBefore <= 0 && prefetchAfter <= 0) {
            restingAt = null
            return@LaunchedEffect
        }
        // Only once it stands still: the pages on screen can stay the same through most of a slide
        // (the list and the detail, all the way from the one to the other), and the language picker,
        // composed ahead as the slide was still going, stalled it for half a second.
        snapshotFlow { if (position.isMoving) null else inView.onScreen }.collectLatest { range ->
            if (range == null) return@collectLatest
            delay(PREFETCH_DELAY_MS)
            restingAt = range
        }
    }
    val pages = inView
    var first = pages.onScreen.first
    var last = pages.onScreen.last
    restingAt?.let {
        first = minOf(first, it.first - prefetchBefore)
        last = maxOf(last, it.last + prefetchAfter)
    }
    position.upcoming?.let { at ->
        first = minOf(first, floor(at + PAGE_SLACK).toInt())
        last = maxOf(last, ceil(at - PAGE_SLACK).toInt() + visible - 1)
    }
    val composed = first.coerceAtLeast(0)..last.coerceAtMost(count - 1)
    val active = LocalPageActive.current
    val focusManager = LocalFocusManager.current
    Layout(
        modifier = modifier,
        content = {
            for (index in composed) {
                val pageKey = key(index)
                val shown = index in pages.main
                key(pageKey) {
                    holder.SaveableStateProvider(pageKey) {
                        CompositionLocalProvider(LocalPageActive provides (active && shown)) {
                            // A page leaving view with D-pad focus in it lets go of it: kept composed
                            // off the screen (see prefetch), it kept the focus there, and keys moved it
                            // about unseen (Back to Apps from inside Configured). As when the page
                            // went away altogether, the next key starts afresh from what is shown.
                            var holdsFocus by remember { mutableStateOf(false) }
                            LaunchedEffect(shown) {
                                if (!shown && holdsFocus) focusManager.clearFocus(force = true)
                            }
                            Box(
                                propagateMinConstraints = true,
                                modifier = Modifier.onFocusChanged { holdsFocus = it.hasFocus }.inert(!shown).focusGroup(),
                            ) { page(index) }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val length = if (vertical) height else width
        val pageLength = (length / visible).coerceAtLeast(0)
        val shownBefore = reveal.roundToPx()
        if (revealPx != shownBefore) revealPx = shownBefore
        if (pageLengthPx != pageLength) pageLengthPx = pageLength
        val pageConstraints = if (vertical) Constraints.fixed(width, pageLength) else Constraints.fixed(pageLength, height)
        val placeables = measurables.map { it.measure(pageConstraints) }
        layout(width, height) {
            val at = position.value
            val onScreen = inView.onScreen
            placeables.forEachIndexed { i, placeable ->
                val index = composed.first + i
                var offset = ((index - at) * pageLength).roundToInt()
                // Composed ahead: placed too, since panes measure their content as they are placed
                // (see safeArea), but far off the screen on its side. Just past the strip's edge
                // was not enough: a level composed ahead showed as the destination page holding it
                // slid aside.
                if (index !in onScreen) offset = if (index < at) -FAR_OFF * length else FAR_OFF * length
                // Sideways, pages follow the reading direction, as the island's items do.
                if (vertical) placeable.place(0, offset) else placeable.placeRelative(offset, 0)
            }
        }
    }
}

/**
 * Sideways swipes move [position] within [range] (giving a little past the ends of the strip,
 * [bounds]; see [PagePosition.dragBy]) and, once let go, [onSettle] gets the page to settle on (the
 * nearest, or the next one in the direction of a fling) and the speed, in pages per second: none
 * when the swipe was heading away from that page, past an end, so the pages ease back rather than
 * fly on and bounce. [pageLength] is a page's width in pixels. Vertical scrolling inside still
 * works: a swipe only starts once it has gone further sideways than the touch slop.
 */
@Composable
fun Modifier.pageSwipe(
    position: PagePosition,
    range: ClosedFloatingPointRange<Float>,
    bounds: ClosedFloatingPointRange<Float>,
    pageLength: () -> Float,
    enabled: Boolean,
    onSettle: (target: Int, velocity: Float) -> Unit,
): Modifier {
    // Finger to the right is back a page, unless pages run from the right.
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val fling = with(LocalDensity.current) { FlingVelocity.toPx() }
    val currentRange by rememberUpdatedState(range)
    val currentBounds by rememberUpdatedState(bounds)
    val currentSettle by rememberUpdatedState(onSettle)
    val currentLength by rememberUpdatedState(pageLength)
    val state = rememberDraggableState { delta ->
        val length = currentLength()
        if (length > 0f) position.dragBy(sign * delta / length, currentRange, currentBounds)
    }
    return draggable(
        state = state,
        orientation = Orientation.Horizontal,
        enabled = enabled,
        onDragStarted = { position.startDrag() },
        onDragStopped = { velocity ->
            val length = currentLength().coerceAtLeast(1f)
            val pages = sign * velocity / length
            val at = position.value
            val target = when {
                sign * velocity > fling -> ceil(at)
                sign * velocity < -fling -> floor(at)
                else -> at.roundToInt().toFloat()
            }.coerceIn(currentRange)
            val outward = (pages > 0f && at >= target) || (pages < 0f && at <= target)
            currentSettle(target.roundToInt(), if (outward) 0f else pages)
        },
    )
}

/**
 * Whether this page is the one shown, rather than a neighbour composed while the pages slide (a
 * swipe, a finger held on the island). Neighbours neither send the island away nor take Back.
 */
val LocalPageActive = compositionLocalOf { true }

/** [BackHandler] for something on a page: only while the page is the one shown. */
@Composable
fun PageBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled && LocalPageActive.current, onBack = onBack)
}

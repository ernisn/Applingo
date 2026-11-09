package io.github.ernisn.applingo.ui

import androidx.compose.runtime.mutableStateMapOf
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ernisn.applingo.R
import io.github.ernisn.applingo.ui.applist.AppListPane
import io.github.ernisn.applingo.ui.components.FocusOnReturnRequest
import io.github.ernisn.applingo.ui.components.FrostedSnackbar
import io.github.ernisn.applingo.ui.components.LocalWindowBackdrop
import io.github.ernisn.applingo.ui.components.backdropSource
import io.github.ernisn.applingo.ui.components.isDpadDevice
import io.github.ernisn.applingo.ui.components.rememberBackdrop
import io.github.ernisn.applingo.ui.detail.AppDetailPane
import io.github.ernisn.applingo.ui.detail.EmptyDetailPane
import io.github.ernisn.applingo.ui.managed.ManagedPane
import io.github.ernisn.applingo.ui.picker.LocalePickerPane
import io.github.ernisn.applingo.ui.settings.SettingsScreen
import io.github.ernisn.applingo.ui.theme.ControlShape
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class Destination(val label: Int) { Apps(R.string.nav_apps), Managed(R.string.nav_managed), Settings(R.string.nav_settings) }

/** From this width on (landscape phones, unfolded foldables, tablets) navigation moves to a side rail. */
private const val RAIL_MIN_WIDTH_DP = 600

/** Space every pane keeps between its content and its left and right edges. */
private val PaneMargin = 16.dp

/** Space between content and a system bar or cutout beside it. */
private val BarGap = 12.dp

/**
 * The upright island's place between the bar or cutout on its side and the first pane: [RailBarGap]
 * from the bar, [RailPaneGap] from the pane, whose own [PaneMargin] then follows before its content.
 * A 3-button bar's glyphs sit about 12 dp in from its inner edge, so that leaves about as much empty
 * space on either side of the island, 18 dp. (4 dp from the bar and 4 from the pane looked off
 * centre, 16 dp to the glyphs against 20 dp to the content.)
 */
private val RailBarGap = 6.dp
private val RailPaneGap = 2.dp

/** The island's way out with a secondary page or selection, and back: about as long as the pane's. */
private const val ISLAND_ENTER_MS = 300
private const val ISLAND_EXIT_MS = 200

/** Allowance for the second press of a double tap on the island (see MainScreen's tap). */
private const val DOUBLE_TAP_PRESS_MS = 150L

/** Two destinations at once (Apps to Settings): a little longer than one, so the page between doesn't flash past. */
private val PageJumpSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 250f)

/**
 * The content area already keeps clear of the bars and cutout at its sides (beside the upright
 * island), so [safeArea] leaves them alone: counting them by position squeezed panes as they slid
 * past the screen edge.
 */
private val LocalSidesClear = staticCompositionLocalOf { false }

/**
 * Top-level navigation: a floating island at the bottom of narrow screens, an upright one at the
 * start of wider ones (see NavIsland.kt), translucent over the content either way. Everything is
 * drawn edge to edge; each piece of content keeps clear of system bars, the display cutout and the
 * keyboard only where it actually overlaps them (see [safeArea]), so the background shows through
 * instead of black bars, and lists run on behind the bottom bars with their ends padded clear (see
 * [bottomChrome]).
 *
 * The destinations are pages side by side (see PageStrip.kt), sideways under the island at the
 * bottom and up and down beside the upright one, standing where the island's indicator stands:
 * holding a finger on the island slides them along with it, and under the island at the bottom
 * a sideways swipe on the content moves both (Settings → Swipe navigation). Each destination keeps
 * its own state (scroll position, open app) while out of view.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Apps) }
    val snackbarHostState = remember { SnackbarHostState() }
    val island = remember { NavIslandState() }
    val useRail = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() >= RAIL_MIN_WIDTH_DP.dp
    }
    island.atBottom = !useRail
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // No touch on D-pad devices, and so no swipes.
    val swipe = settings.swipeNavigation && !isDpadDevice()

    // Where the destinations' pages stand, in pages, shared with the island's indicator.
    val scope = rememberCoroutineScope()
    val pages = remember { PagePosition(destination.ordinal.toFloat()) }
    fun select(target: Destination, velocity: Float? = null) {
        destination = target
        val to = target.ordinal.toFloat()
        val spec = if (abs(to - pages.value) > 1.5f) PageJumpSpring else PageSpring
        scope.launch { pages.animateTo(to, spec, velocity) }
    }

    // How many times each destination's item was tapped while already selected (see OnBackToTop),
    // and tapped twice in a row (see OnScrollToTop).
    val backToTop = remember { mutableStateMapOf<Destination, Int>() }
    val scrollToTop = remember { mutableStateMapOf<Destination, Int>() }
    // The last tap, to tell a double tap. Not read while composing, so changing it recomposes nothing.
    val lastTap = remember { mutableStateOf<Pair<Destination, Long>?>(null) }
    // Taps count as clicks when the finger lifts, so the second one's press comes on top of the
    // system's double-tap timeout, which ends as the finger comes down.
    val doubleTapMs = LocalViewConfiguration.current.doubleTapTimeoutMillis + DOUBLE_TAP_PRESS_MS

    /**
     * An item of the island tapped, or clicked with a D-pad or by an accessibility service: the one
     * already selected takes its page back to its top level, and a second tap soon after the first
     * scrolls it back to the start of its list.
     */
    fun tap(target: Destination) {
        val now = SystemClock.uptimeMillis()
        val previous = lastTap.value
        if (previous != null && previous.first == target && now - previous.second <= doubleTapMs) {
            scrollToTop[target] = (scrollToTop[target] ?: 0) + 1
            lastTap.value = null
        } else {
            lastTap.value = target to now
        }
        if (target == destination) backToTop[target] = (backToTop[target] ?: 0) + 1
        select(target)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message.text, withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }

    val undoableChange by viewModel.undoableChange.collectAsStateWithLifecycle()
    // Kept while the countdown fades out after it ends.
    var lastUndoableChange by remember { mutableStateOf<UndoableChange?>(null) }
    if (undoableChange != null && undoableChange != lastUndoableChange) lastUndoableChange = undoableChange
    // A countdown takes the place of whatever message is showing.
    LaunchedEffect(undoableChange?.id) {
        if (undoableChange != null) snackbarHostState.currentSnackbarData?.dismiss()
    }

    // While a change can be undone, D-pad Down from the bottom of a pane goes on to its Undo, the only
    // way to reach it: the snackbar lies over the panes, beside rather than below their controls.
    val undoFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var undoFocused by remember { mutableStateOf(false) }
    // Once the countdown is over, undone or not, focus on Undo goes back up into the pane rather than
    // nowhere when the snackbar leaves.
    LaunchedEffect(undoableChange == null) {
        if (undoableChange == null && undoFocused) focusManager.moveFocus(FocusDirection.Up)
    }
    val downToUndo = if (undoableChange == null) {
        Modifier
    } else {
        Modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) return@onKeyEvent false
            if (!focusManager.moveFocus(FocusDirection.Down)) undoFocus.requestFocus()
            true
        }
    }

    // Back from another destination returns to the app list before leaving Applingo.
    BackHandler(enabled = destination != Destination.Apps) { select(Destination.Apps) }

    val islandItems = Destination.entries.map { item ->
        NavIslandItem(
            label = stringResource(item.label),
            selected = item == destination,
            onClick = { tap(item) },
            icon = { DestinationIcon(item) },
        )
    }

    // What the pages show, recorded for the frosted surfaces over them (see LocalWindowBackdrop): the
    // island, the messages below, and menus and dialogs, which open in windows of their own. Those in
    // this window stay out of the recording, since they draw it.
    val windowBackdrop = rememberBackdrop()

    // The island stays put behind the keyboard instead of riding on top of it and taking room from
    // the content (a search with the keyboard open showed only a few results).
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val direction = LocalLayoutDirection.current

    // Beside the upright island the content keeps clear of it at rest, where the rail used to end:
    // the island sits midway between the bar or cutout on its side and the content (see
    // [RailBarGap]), or, with nothing beside it (gesture navigation, a TV), as far from the screen
    // edge as from the content, rather than hugging the edge. Pages sliding out pass behind it.
    val startInset = barInsets.only(WindowInsetsSides.Start).asPaddingValues().calculateStartPadding(direction)
    val edgeGap = if (startInset > 0.dp) RailBarGap else RailPaneGap + PaneMargin
    val leading = if (useRail) startInset + edgeGap + UprightIslandWidth + RailPaneGap else 0.dp
    // The bar or cutout at the far edge is kept out of the panes' width, so side-by-side panes split
    // only the usable width and come out equally wide. The last pane's content then sits [BarGap]
    // from it, as the island does from the bar or cutout on its side: the pane's own margin partly
    // lies over the bar (see [safeArea]) instead of adding to it, which left a wide empty strip.
    val farEdge = WindowInsets.safeDrawing.only(WindowInsetsSides.End).asPaddingValues().calculateEndPadding(direction)
    val trailing = if (useRail) (farEdge - (PaneMargin - BarGap)).coerceAtLeast(0.dp) else 0.dp

    // Sideways swipes switch destinations under the island at the bottom, on top-level pages only:
    // with the island away (a secondary page, a selection) there is nothing to switch to.
    var contentWidth by remember { mutableIntStateOf(0) }
    val tabSwipe = Modifier
        .onSizeChanged { contentWidth = it.width }
        .pageSwipe(
            position = pages,
            range = 0f..(Destination.entries.size - 1f),
            bounds = 0f..(Destination.entries.size - 1f),
            pageLength = { contentWidth.toFloat() },
            enabled = swipe && island.shown,
        ) { target, velocity -> select(Destination.entries[target], velocity) }

    CompositionLocalProvider(
        LocalNavIsland provides island,
        LocalWindowBackdrop provides windowBackdrop,
        LocalSidesClear provides useRail,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (useRail) {
                    // Ahead of the content, as the rail was (D-pad focus starts there), but drawn
                    // over it. Above and below, [IslandGap] from the bars ([BarGap] on a TV, clear
                    // of a bezel that may crop the picture).
                    val endsGap = if (isDpadDevice()) BarGap else IslandGap
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .zIndex(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(barInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                            .padding(start = edgeGap, top = endsGap, bottom = endsGap),
                    ) {
                        NavIsland(
                            islandItems,
                            position = pages,
                            vertical = true,
                            onReturn = { select(destination) },
                            dragToSwitch = settings.islandDrag,
                            backdrop = windowBackdrop,
                            modifier = Modifier.keepVerticalFocus(enabled = true),
                        )
                    }
                }
                Box(Modifier.fillMaxSize().then(downToUndo).then(if (useRail) Modifier else tabSwipe)) {
                    Box(Modifier.fillMaxSize().backdropSource(windowBackdrop)) {
                        PageStrip(
                            count = Destination.entries.size,
                            position = pages,
                            key = { Destination.entries[it].name },
                            // Up and down beside the upright island: sideways is the app lists'
                            // levels' way there, and a destination sliding sideways took the level
                            // showing behind the island along with it.
                            vertical = useRail,
                            // All three, once at rest: Apps to Settings caught on Configured,
                            // composed on the way.
                            prefetchBefore = Destination.entries.size - 1,
                            prefetchAfter = Destination.entries.size - 1,
                            // Beside the upright island, not under it (see PageStrip): pages and
                            // levels leaving still pass behind it, drawn past the strip's start.
                            modifier = Modifier.fillMaxSize().padding(start = leading),
                        ) { index ->
                            val page = Destination.entries[index]
                            CompositionLocalProvider(
                                LocalPageActive provides (page == destination),
                                LocalBackToTop provides (backToTop[page] ?: 0),
                                LocalScrollToTop provides (scrollToTop[page] ?: 0),
                            ) {
                                when (page) {
                                    Destination.Apps -> AppsListDetail(viewModel, useRail, leading, trailing, swipe) { selected, focusOnReturn, onOpen ->
                                        AppListPane(viewModel, selected, focusOnReturn, onOpen)
                                    }
                                    Destination.Managed -> AppsListDetail(viewModel, useRail, leading, trailing, swipe) { selected, focusOnReturn, onOpen ->
                                        ManagedPane(viewModel, selected, focusOnReturn, onOpen)
                                    }
                                    Destination.Settings -> Box(
                                        Modifier.fillMaxSize().padding(end = trailing).keepVerticalFocus(useRail),
                                    ) {
                                        SettingsScreen(viewModel)
                                    }
                                }
                            }
                        }
                    }
                    // Messages keep clear of whatever covers the bottom of the content: the system bar
                    // or the keyboard, and the island while it shows. They follow it as it leaves and
                    // comes back. Beside the upright island, they centre on the panes.
                    val density = LocalDensity.current
                    val bars = barInsets.getBottom(density)
                    val keyboard = WindowInsets.ime.getBottom(density)
                    val islandSpace = if (island.shown) island.heightPx + with(density) { IslandGap.roundToPx() } else 0
                    val snackbarBottom by animateDpAsState(
                        with(density) { maxOf(keyboard, bars + islandSpace).toDp() },
                        label = "snackbarBottom",
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .then(
                                if (useRail) {
                                    Modifier.padding(start = leading, end = trailing)
                                } else {
                                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                },
                            )
                            .padding(bottom = snackbarBottom),
                    ) {
                        AnimatedVisibility(
                            visible = undoableChange != null,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut(),
                        ) {
                            lastUndoableChange?.let {
                                UndoCountdownSnackbar(
                                    change = it,
                                    onUndo = viewModel::undoChange,
                                    undoModifier = Modifier.focusRequester(undoFocus).onFocusChanged { state -> undoFocused = state.isFocused },
                                )
                            }
                        }
                        // Messages that arrive during a countdown wait until it is over.
                        if (undoableChange == null) {
                            SnackbarHost(hostState = snackbarHostState) { data -> FrostedSnackbar(data) }
                        }
                    }
                }
                if (!useRail) {
                    // Always there on top-level pages (lists end clear of it, see bottomChrome); it
                    // only leaves for secondary pages and selections, easing down out of sight and
                    // back up.
                    AnimatedVisibility(
                        visible = island.shown,
                        enter = slideInVertically(tween(ISLAND_ENTER_MS, easing = LinearOutSlowInEasing)) { it } +
                            fadeIn(tween(ISLAND_ENTER_MS)),
                        exit = slideOutVertically(tween(ISLAND_EXIT_MS, easing = FastOutLinearInEasing)) { it } +
                            fadeOut(tween(ISLAND_EXIT_MS, easing = FastOutLinearInEasing)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(barInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(bottom = IslandGap),
                    ) {
                        NavIsland(
                            islandItems,
                            position = pages,
                            vertical = false,
                            onReturn = { select(destination) },
                            dragToSwitch = settings.islandDrag,
                            backdrop = windowBackdrop,
                            modifier = Modifier.onSizeChanged { island.heightPx = it.height },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps content inside the safe drawing area (system bars, display cutout, keyboard), but only by as
 * much as this element actually overlaps them: a pane in the middle of the screen gets no padding,
 * a pane at the left edge clears the cutout there, and so on.
 *
 * The element's position in the window is only known while it is being placed, so the content is
 * measured there, at the size left over after the overlaps. (Foundation's `fitInside` with the
 * SafeDrawing rulers moved the content down by the status bar but kept its full height, pushing the
 * bottom of every pane off screen.)
 *
 * Only the part of the element inside the window counts: a pane sliding in or out is partly off
 * screen, and counting that part as overlapping the bar on that side squeezed its content into a
 * narrow strip that widened as the pane slid in. Beside the upright island the sides are left alone
 * altogether ([LocalSidesClear]): the content area keeps clear of them already, and panes now slide
 * on past it, behind the island and off the screen.
 *
 * At the sides, every pane keeps its content [PaneMargin] from its edges anyway. Content only needs
 * to stay [BarGap] clear of a bar or cutout, so the rest of that margin may lie over it (only
 * background shows there) and is not taken off again.
 *
 * Panes leave the bottom to their content ([bottom] false): lists run on behind the system bar and
 * the floating island, ending in [bottomChrome] padding, and bottom bars reach down behind the
 * system bar with their buttons above it. They leave the top to their header ([top] false), which
 * keeps clear of the status bar itself while the list runs on behind both (see FrostedHeaderLayout).
 */
@Composable
fun Modifier.safeArea(top: Boolean = true, bottom: Boolean = true): Modifier {
    val keepTop = top
    val keepBottom = bottom
    val keepSides = !LocalSidesClear.current
    val insets = WindowInsets.safeDrawing
    return layout measure@{ measurable, constraints ->
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@measure layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val direction = layoutDirection
        layout(width, height) {
            val coords = coordinates
            var left = 0
            var top = 0
            var right = 0
            var bottom = 0
            if (coords != null) {
                val window = coords.findRootCoordinates().size
                val origin = coords.positionInWindow()
                val x = origin.x.roundToInt()
                val y = origin.y.roundToInt()
                val spare = (PaneMargin - BarGap).roundToPx()
                if (keepSides) {
                    left = (overlap(x, x + width, 0, insets.getLeft(this@measure, direction)) - spare).coerceAtLeast(0)
                    right = (overlap(x, x + width, window.width - insets.getRight(this@measure, direction), window.width) - spare)
                        .coerceAtLeast(0)
                }
                if (keepTop) top = overlap(y, y + height, 0, insets.getTop(this@measure))
                if (keepBottom) bottom = overlap(y, y + height, window.height - insets.getBottom(this@measure), window.height)
            }
            val placeable = measurable.measure(
                Constraints.fixed(
                    (width - left - right).coerceAtLeast(0),
                    (height - top - bottom).coerceAtLeast(0),
                ),
            )
            placeable.place(left, top)
        }
    }
}

/** Length of the overlap between the ranges [start, end) and [barStart, barEnd). */
private fun overlap(start: Int, end: Int, barStart: Int, barEnd: Int): Int =
    (minOf(end, barEnd) - maxOf(start, barStart)).coerceAtLeast(0)

/**
 * With the side rail (TV, wide screens), D-pad Up and Down stay within a pane, or the rail: past the
 * first or last control they jumped sideways to whatever lay further up or down, in the rail or the
 * other pane. Left and Right still move between them.
 */
private fun Modifier.keepVerticalFocus(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        focusProperties {
            onExit = {
                if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                    cancelFocusChange()
                }
            }
        }.focusGroup()
    }

/**
 * [value] while this pane stays in view, and as it was when it started leaving ([leaving]), so its
 * top bar (back arrow or close button) doesn't change, and shift the title, while it slides away.
 */
@Composable
private fun keptWhileLeaving(value: Boolean, leaving: Boolean): Boolean {
    var kept by remember { mutableStateOf(value) }
    if (!leaving) kept = value
    return kept
}

/**
 * A change just made: a ring running down with the seconds left to undo it, what happened, and Undo.
 * Times come from the uptime clock, so the countdown stays right across rotation (the timer itself
 * runs in the ViewModel).
 */
@Composable
private fun UndoCountdownSnackbar(
    change: UndoableChange,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    undoModifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(change.id) {
        while (true) withFrameMillis { now = SystemClock.uptimeMillis() }
    }
    val remaining = (change.startedAt + change.durationMillis - now).coerceIn(0, change.durationMillis)
    FrostedSnackbar(
        modifier = modifier,
        action = {
            TextButton(onClick = onUndo, shape = ControlShape, modifier = undoModifier) {
                Text(stringResource(R.string.action_undo))
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Announcing every tick would drown out the message itself.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).clearAndSetSemantics {}) {
                CircularProgressIndicator(
                    progress = { remaining.toFloat() / change.durationMillis },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                )
                Text(
                    text = ((remaining + 999) / 1000).toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(change.text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination) = when (destination) {
    Destination.Apps -> Icon(painterResource(R.drawable.ic_apps), contentDescription = null)
    Destination.Managed -> Icon(painterResource(R.drawable.ic_translate), contentDescription = null)
    Destination.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
}

/**
 * The back gesture slides [strip] back from where it [standing] as it goes, and on if cancelled; the
 * 3-button Back (no progress) just slides it back ([onBack]). On the page shown only: reading that
 * here rather than in [AppsListDetail] keeps the switch of destination from recomposing the whole
 * app list there, which took a slow tablet's first frame of the switch.
 */
@Composable
private fun LevelBackHandler(enabled: Boolean, strip: PagePosition, standing: () -> Float, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = enabled && LocalPageActive.current) { progress ->
        val from = standing()
        try {
            progress.collect { event -> strip.dragTo(from - event.progress) }
            onBack()
        } catch (e: CancellationException) {
            scope.launch { strip.animateTo(from) }
            throw e
        }
    }
}

/** A detail swapped in place: a short fade, and a little way in from the side the list is on the other side of. */
private const val SWAP_IN_MS = 220
private val SwapInOffset = 24.dp

/**
 * [content], fading in (and sliding the last [SwapInOffset] in) once composed if [fadeIn] was true
 * when it first was; [onDone] once it is in. Starts a frame after it is composed, so a long first
 * frame doesn't take up the fade.
 */
@Composable
private fun FadeInWhen(fadeIn: Boolean, onDone: () -> Unit, content: @Composable () -> Unit) {
    val shown = remember { Animatable(if (fadeIn) 0f else 1f) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        if (shown.value < 1f) {
            withFrameNanos {}
            shown.animateTo(1f, tween(SWAP_IN_MS, easing = LinearOutSlowInEasing))
            currentOnDone()
        }
    }
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown.value
                translationX = sign * (1f - shown.value) * SwapInOffset.toPx()
            },
    ) { content() }
}

/** An app list's levels, in order: the list, an app's detail, its language picker. */
private const val LEVELS = 3

/**
 * List | app detail | language picker, as pages side by side (see PageStrip.kt): one in view at a
 * time on narrow screens and portrait tablets, two from expanded widths (landscape phones, unfolded
 * foldables, landscape tablets), the list and the detail, or the detail and the picker. Opening the
 * next level slides the row along by a page; the page leaving passes behind the upright island and
 * on off the screen rather than being cut off beside it, and stays there, its end showing behind
 * the island. Back, and predictive back, slide it the other way, following the gesture.
 *
 * Beside the upright island (where two levels can be in view at once, and the island is not swiped
 * along), a sideways swipe goes back or forward a level instead of switching destinations: forward
 * opens the next level for the app open last. Under the island at the bottom, where a swipe on the
 * list switches destinations, one on a deeper level only goes back: forward would have to guess at
 * the next level from nothing on screen.
 *
 * Two panes share the width equally. There is no spacer between them: each pane already keeps its
 * content 16 dp from its edges, and a spacer on top of that left a gap twice as wide as the outer
 * margins, making the second pane look narrower.
 *
 * A level leaving the screen saves its state and gets it back when it returns. Each app's detail and
 * picker are saved apart, or one app's unsaved picker choices (and search, and scroll position)
 * turned up in the next app's.
 *
 * D-pad focus follows the panes back (see FocusOnReturn): to the app's row when the list returns in
 * place of its detail, to Change language when the picker closes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppsListDetail(
    viewModel: MainViewModel,
    rail: Boolean,
    reveal: Dp,
    trailing: Dp,
    swipe: Boolean,
    listPane: @Composable (selected: String?, focusOnReturn: FocusOnReturnRequest?, onOpen: (String) -> Unit) -> Unit,
) {
    val panes = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).maxHorizontalPartitions.coerceIn(1, 2)
    val scope = rememberCoroutineScope()
    // How deep the user went: 0 the list, 1 an app's detail, 2 its picker.
    var depth by rememberSaveable { mutableIntStateOf(0) }
    // Kept after going back, so the detail goes on showing the app while it slides away, instead of
    // flashing an empty pane, and the next level forward is that app's.
    var lastPackage by rememberSaveable { mutableStateOf<String?>(null) }

    /** Where the row stands at [depth]: the deepest level in view as the last of [panes]. */
    fun standing(depth: Int) = (depth - (panes - 1)).coerceAtLeast(0)
    val target = standing(depth)
    val strip = remember { PagePosition(target.toFloat()) }
    val levelFocus = remember { List(LEVELS) { FocusRequester() } }
    val inputModeManager = LocalInputModeManager.current
    // Another layout for the same window (resized): straight there.
    LaunchedEffect(panes) {
        val at = standing(depth).toFloat()
        if (!strip.isDragged && strip.value != at) strip.snapTo(at)
    }

    /**
     * To level [to]. Let go of a swipe ([velocity]), the pages are on screen already and settle at
     * once; otherwise (a tap, Back) those it brings in are composed first (see PagePosition.prepare).
     */
    fun go(to: Int, velocity: Float? = null) {
        val wasAt = standing(depth)
        val forward = to > depth
        depth = to
        val at = standing(to)
        // Forward with a D-pad or keyboard, the level coming into view takes focus once in place, as
        // the adaptive scaffold's panes did: the picker opened by Change language beside the detail,
        // an app's detail opened alone. One already in view (the detail beside the list) leaves focus
        // where it is; back, focus returns to where it came from instead (see FocusOnReturn).
        val entering = if (forward) (at until at + panes).lastOrNull { it !in wasAt until wasAt + panes } else null
        scope.launch {
            if (velocity == null) strip.prepare(at.toFloat())
            strip.animateTo(at.toFloat(), PageSpring, velocity)
            if (entering != null && inputModeManager.inputMode == InputMode.Keyboard) {
                try {
                    levelFocus[entering].requestFocus()
                } catch (_: IllegalStateException) {
                    // Gone meanwhile (another app opened); focus stays put.
                }
            }
        }
    }

    // Another app opened while its detail is in view already (beside the list): the new one fades in
    // where the old one was, rather than replacing it from one frame to the next.
    var swappedIn by remember { mutableStateOf<String?>(null) }

    /** Back a level, if that brings anything into view (side by side, the list and a detail are both the top). */
    fun back() {
        val from = depth
        if (panes == 2) {
            if (from == 2) go(1)
        } else if (from >= 1) {
            go(from - 1)
        }
    }
    val canGoBack = if (panes == 2) depth == 2 else depth >= 1

    fun inView(level: Int) = level >= target && level < target + panes
    val listShown = inView(0)
    val detailShown = inView(1)
    val pickerShown = inView(2)
    val selected = lastPackage.takeIf { depth >= 1 }
    // One pane at a time, an app's detail or the picker is a secondary page: the island makes way, as
    // the pane starts moving in rather than once it is in place.
    HideNavIsland(!listShown)

    // Only after the list made way for the detail alone: when it made way for the picker beside the
    // detail, focus goes to Change language instead.
    var listLeftForDetail by rememberSaveable { mutableStateOf(false) }
    if (!listShown && detailShown && !pickerShown) listLeftForDetail = true
    val focusOnReturn = lastPackage?.takeIf { listLeftForDetail && listShown }
        ?.let { FocusOnReturnRequest(it) { listLeftForDetail = false } }

    // Opening an app from the list, the same function throughout: a new one with each change of level
    // made every row on screen recompose for the one row whose highlight changed (its click captured
    // it), a tenth of a second of a slow tablet's frame before the detail could slide in.
    val open by rememberUpdatedState { packageName: String ->
        if (detailShown && packageName != lastPackage) swappedIn = packageName
        lastPackage = packageName
        go(maxOf(depth, 1))
    }
    val openApp = remember { { packageName: String -> open(packageName) } }

    LevelBackHandler(canGoBack, strip, standing = { standing(depth).toFloat() }, onBack = { back() })
    // The destination's island item tapped again: back to the list (beside the detail, when two fit).
    OnBackToTop { go(if (panes == 2) minOf(depth, 1) else 0) }

    // Beside the upright island, forward reaches the deepest level for the app open last (with none,
    // there is nowhere to go); under the island at the bottom, a swipe only goes back a level.
    val last = standing(LEVELS - 1).toFloat()
    val reach = if (lastPackage == null) 0f else last
    val swipeRange = if (rail) 0f..reach else (target - 1).coerceAtLeast(0).toFloat()..target.toFloat()
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width }
            .pageSwipe(
                position = strip,
                range = swipeRange,
                bounds = 0f..last,
                pageLength = { (width - with(density) { trailing.toPx() }) / panes },
                enabled = swipe && swipeRange.endInclusive > swipeRange.start,
            ) { at, velocity ->
                go(if (panes == 1) at else if (at >= 1) 2 else minOf(depth, 1), velocity)
            },
    ) {
        PageStrip(
            count = LEVELS,
            position = strip,
            key = { level ->
                when (level) {
                    0 -> "list"
                    1 -> "detail:$lastPackage"
                    else -> "picker:$lastPackage"
                }
            },
            visible = panes,
            // The levels a swipe may bring in: both ways beside the upright island, back only under
            // the island at the bottom.
            prefetchBefore = if (swipe) 1 else 0,
            prefetchAfter = if (rail && swipe) 1 else 0,
            reveal = reveal,
            modifier = Modifier.fillMaxSize().padding(end = trailing),
        ) { level ->
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(levelFocus[level])
                    .then(if (rail) Modifier.keepVerticalFocus(enabled = true) else Modifier.focusGroup()),
            ) {
                when (level) {
                    0 -> listPane(selected, focusOnReturn, openApp)
                    1 -> {
                        val packageName = lastPackage
                        if (packageName == null) {
                            EmptyDetailPane()
                        } else {
                            FadeInWhen(packageName == swappedIn, onDone = { swappedIn = null }) {
                                AppDetailPane(
                                    viewModel = viewModel,
                                    packageName = packageName,
                                    showBack = keptWhileLeaving(!listShown, leaving = !detailShown),
                                    pickerOpen = pickerShown,
                                    onBack = { back() },
                                    onEditLanguages = { go(2) },
                                )
                            }
                        }
                    }
                    else -> lastPackage?.let { packageName ->
                        LocalePickerPane(
                            viewModel = viewModel,
                            packageName = packageName,
                            closeAsBack = keptWhileLeaving(!detailShown, leaving = !pickerShown),
                            onClose = { back() },
                        )
                    }
                }
            }
        }
    }
}

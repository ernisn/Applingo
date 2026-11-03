package io.github.ernisn.applingo.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.AnimatedPaneScope
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

/**
 * Top-level navigation: a floating island at the bottom of narrow screens, an upright one at the
 * start of wider ones (see NavIsland.kt). Everything is drawn edge to edge; each piece of content
 * keeps clear of system bars, the display cutout and the keyboard only where it actually overlaps
 * them (see [safeArea]), so the background shows through instead of black bars, and lists run on
 * behind the bottom bars with their ends padded clear (see [bottomChrome]). Each destination keeps
 * its own state (scroll position, open app) while switching.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Apps) }
    val stateHolder = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val island = remember { NavIslandState() }
    val useRail = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() >= RAIL_MIN_WIDTH_DP.dp
    }
    island.atBottom = !useRail

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
    BackHandler(enabled = destination != Destination.Apps) { destination = Destination.Apps }

    val islandItems = Destination.entries.map { item ->
        NavIslandItem(
            label = stringResource(item.label),
            selected = item == destination,
            onClick = { destination = item },
            icon = { DestinationIcon(item) },
        )
    }

    // What the pages show, recorded for the frosted surfaces over them (see LocalWindowBackdrop): the
    // island at the bottom, the messages below, and menus and dialogs, which open in windows of
    // their own. Those in this window stay out of the recording, since they draw it.
    val windowBackdrop = rememberBackdrop()

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.then(downToUndo)) {
            Box(Modifier.fillMaxSize().backdropSource(windowBackdrop)) {
                stateHolder.SaveableStateProvider(destination.name) {
                    when (destination) {
                        Destination.Apps -> AppsListDetail(viewModel, keepVertical = useRail) { selected, focusOnReturn, onOpen ->
                            AppListPane(viewModel, selected, focusOnReturn, onOpen)
                        }
                        Destination.Managed -> AppsListDetail(viewModel, keepVertical = useRail) { selected, focusOnReturn, onOpen ->
                            ManagedPane(viewModel, selected, focusOnReturn, onOpen)
                        }
                        Destination.Settings -> Box(Modifier.fillMaxSize().keepVerticalFocus(useRail)) {
                            SettingsScreen(viewModel)
                        }
                    }
                }
            }
            // Messages keep clear of whatever covers the bottom of the content: the system bar or the
            // keyboard, and the island while it shows. They follow it as it leaves and comes back.
            val density = LocalDensity.current
            val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(density)
            val keyboard = WindowInsets.ime.getBottom(density)
            val islandSpace = if (island.shown) island.heightPx + with(density) { IslandGap.roundToPx() } else 0
            val snackbarBottom by animateDpAsState(
                with(density) { maxOf(keyboard, bars + islandSpace).toDp() },
                label = "snackbarBottom",
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
    }

    // The island stays put behind the keyboard instead of riding on top of it and taking room from
    // the content (a search with the keyboard open showed only a few results).
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    CompositionLocalProvider(LocalNavIsland provides island, LocalWindowBackdrop provides windowBackdrop) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (useRail) {
                Row(Modifier.fillMaxSize()) {
                    // Beside the content, not over it, midway between the bar or cutout on its side
                    // and the content (see [RailBarGap]). With nothing beside it (gesture navigation,
                    // a TV) it keeps as far from the screen edge as from the content, rather than
                    // hugging the edge. Above and below, [IslandGap] from the bars ([BarGap] on a
                    // TV, clear of a bezel that may crop the picture).
                    val startInset = barInsets.only(WindowInsetsSides.Start).asPaddingValues()
                        .calculateStartPadding(LocalLayoutDirection.current)
                    val edgeGap = if (startInset > 0.dp) RailBarGap else RailPaneGap + PaneMargin
                    val endsGap = if (isDpadDevice()) BarGap else IslandGap
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(barInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                            .padding(start = edgeGap, top = endsGap, bottom = endsGap),
                    ) {
                        NavIsland(islandItems, vertical = true, modifier = Modifier.keepVerticalFocus(enabled = true))
                    }
                    // The bar or cutout at the far edge is kept out at this level, so side-by-side
                    // panes split only the usable width and come out equally wide. The last pane's
                    // content then sits [BarGap] from it, as the island does from the bar or cutout
                    // on its side: the pane's own margin partly lies over the bar (see [safeArea])
                    // instead of adding to it, which left a wide empty strip.
                    val farEdge = WindowInsets.safeDrawing.only(WindowInsetsSides.End).asPaddingValues()
                        .calculateEndPadding(LocalLayoutDirection.current)
                    content(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = RailPaneGap, end = (farEdge - (PaneMargin - BarGap)).coerceAtLeast(0.dp)),
                    )
                }
            } else {
                // Always there on top-level pages (lists end clear of it, see bottomChrome); it only
                // leaves for secondary pages and selections, easing down out of sight and back up.
                Box(Modifier.fillMaxSize()) {
                    content(Modifier.fillMaxSize())
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
                            vertical = false,
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
 * narrow strip that widened as the pane slid in.
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
    val insets = WindowInsets.safeDrawing
    return layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
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
                left = (overlap(x, x + width, 0, insets.getLeft(this@layout, direction)) - spare).coerceAtLeast(0)
                if (keepTop) top = overlap(y, y + height, 0, insets.getTop(this@layout))
                right = (overlap(x, x + width, window.width - insets.getRight(this@layout, direction), window.width) - spare)
                    .coerceAtLeast(0)
                if (keepBottom) bottom = overlap(y, y + height, window.height - insets.getBottom(this@layout), window.height)
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
 * [value] as it was when this pane started animating out, so its top bar (back arrow or close
 * button) doesn't change, and shift the title, while the pane is still sliding away.
 */
@Composable
private fun AnimatedPaneScope.heldWhileExiting(value: Boolean): Boolean {
    var held by remember { mutableStateOf(value) }
    if (transition.targetState != EnterExitState.PostExit) held = value
    return held
}

/**
 * A change just made: a ring running down with the seconds left to undo it, what happened, and Undo.
 * Times come from the uptime clock, so the countdown stays right across rotation (the timer itself
 * runs in the ViewModel).
 */
@Composable
private fun UndoCountdownSnackbar(change: UndoableChange, onUndo: () -> Unit, undoModifier: Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(change.id) {
        while (true) withFrameMillis { now = SystemClock.uptimeMillis() }
    }
    val remaining = (change.startedAt + change.durationMillis - now).coerceIn(0, change.durationMillis)
    FrostedSnackbar(
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
 * List | app detail | language picker. One pane at a time on phones, list + detail side by side from
 * medium widths (landscape, tablets, unfolded foldables); back and predictive back are handled by the
 * scaffold navigator.
 *
 * A pane saves its state while hidden and gets it back when shown again, whichever app it then
 * shows: each app's detail and picker therefore get their own keyed subtree, or one app's unsaved
 * picker choices (and search, and scroll position) turned up in the next app's.
 *
 * Two panes share the width equally. There is no spacer between them: each pane already keeps its
 * content 16 dp from its edges, and the default 24 dp spacer on top of that left a gap twice as wide
 * as the outer margins, making the second pane look narrower.
 *
 * D-pad focus follows the panes back (see FocusOnReturn): to the app's row when the list returns in
 * place of its detail, to Change language when the picker closes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppsListDetail(
    viewModel: MainViewModel,
    keepVertical: Boolean,
    listPane: @Composable (selected: String?, focusOnReturn: FocusOnReturnRequest?, onOpen: (String) -> Unit) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
            .copy(horizontalPartitionSpacerSize = 0.dp),
    )
    val expansion = rememberPaneExpansionState(
        anchors = listOf(PaneExpansionAnchor.Proportion(0.5f)),
        initialAnchoredIndex = 0,
    )
    val scope = rememberCoroutineScope()

    // Keep showing the last app while panes animate away, instead of flashing an empty pane.
    var lastPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = navigator.currentDestination?.contentKey
    if (selected != null && selected != lastPackage) lastPackage = selected

    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val detailHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden
    val pickerHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Extra] == PaneAdaptedValue.Hidden
    // One pane at a time, an app's detail or the picker is a secondary page: the island makes way,
    // as the pane starts moving in (the target) rather than once it is in place (scaffoldValue).
    HideNavIsland(navigator.scaffoldState.targetState[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden)

    // Only after the list made way for the detail alone: when it made way for the picker beside the
    // detail, focus goes to Change language instead.
    var listLeftForDetail by rememberSaveable { mutableStateOf(false) }
    if (listHidden && !detailHidden && pickerHidden) listLeftForDetail = true
    val focusOnReturn = lastPackage?.takeIf { listLeftForDetail && !listHidden }
        ?.let { FocusOnReturnRequest(it) { listLeftForDetail = false } }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        paneExpansionState = expansion,
        listPane = {
            AnimatedPane(Modifier.keepVerticalFocus(keepVertical)) {
                listPane(selected, focusOnReturn) { packageName ->
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, packageName) }
                }
            }
        },
        detailPane = {
            AnimatedPane(Modifier.keepVerticalFocus(keepVertical)) {
                val packageName = lastPackage
                if (packageName == null) {
                    EmptyDetailPane()
                } else {
                    // Keyed by app, as explained in this function's comment.
                    key(packageName) {
                        AppDetailPane(
                            viewModel = viewModel,
                            packageName = packageName,
                            showBack = heldWhileExiting(listHidden),
                            pickerOpen = !pickerHidden,
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onEditLanguages = {
                                scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, packageName) }
                            },
                        )
                    }
                }
            }
        },
        extraPane = {
            AnimatedPane(Modifier.keepVerticalFocus(keepVertical)) {
                lastPackage?.let { packageName ->
                    key(packageName) {
                        LocalePickerPane(
                            viewModel = viewModel,
                            packageName = packageName,
                            closeAsBack = heldWhileExiting(detailHidden),
                            onClose = { scope.launch { navigator.navigateBack() } },
                        )
                    }
                }
            }
        },
    )
}

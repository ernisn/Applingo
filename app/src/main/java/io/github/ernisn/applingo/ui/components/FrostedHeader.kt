package io.github.ernisn.applingo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ernisn.applingo.ui.theme.TRANSLUCENT_PANEL_ALPHA

/**
 * A pane's header (its top bar, and what stays put under it: the Shizuku card, the tabs) laid over
 * its scrolling content, which runs on up behind it, and behind the status bar, and shows through
 * frosted, as behind the navigation island. [content] gets the header's height, to start its list
 * below it; the header itself keeps clear of the status bar and cutout. [tint] is the header's
 * colour (the background, or the selection bar's while selecting).
 *
 * Not on D-pad devices: there the content stays below the header. A scrolling container reaching up
 * behind the top bar starts above its buttons, so focus search doesn't count it as below them and
 * Down skipped the whole page.
 */
@Composable
fun FrostedHeaderLayout(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.background,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable (headerHeight: Dp) -> Unit,
) {
    FrostedBarsLayout(modifier, tint, header, footer = null) { headerHeight, _ -> content(headerHeight) }
}

/**
 * [FrostedHeaderLayout] with a bar at the bottom too ([footer], the language picker's buttons),
 * frosted the same way over the content running on behind it, and behind the system bar, whose
 * height (and the keyboard's) it keeps its own content clear of. [content] gets both bars'
 * heights. [footerTint] is the bar's colour.
 */
@Composable
fun FrostedBarsLayout(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.background,
    header: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)?,
    footerTint: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable (headerHeight: Dp, footerHeight: Dp) -> Unit,
) {
    val topInsets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    val bottomInsets = Modifier.bottomBarInsets()
    if (isDpadDevice()) {
        Column(modifier) {
            Column(Modifier.fillMaxWidth().background(tint).then(topInsets), content = header)
            Box(Modifier.weight(1f)) { content(0.dp, 0.dp) }
            if (footer != null) {
                Column(Modifier.fillMaxWidth().background(footerTint).then(bottomInsets), content = footer)
            }
        }
        return
    }
    val backdrop = rememberBackdrop()
    val base = MaterialTheme.colorScheme.background
    // The window background first, so only the frosted copy shows through and not the sharp
    // content where it has no background of its own (see NavIsland).
    @Composable
    fun Modifier.frosted(tint: Color) = this
        .fillMaxWidth()
        .clipToBounds()
        .drawBehind { drawRect(base) }
        .backdropBlur(backdrop, FrostBlur)
        .drawBehind { drawRect(tint.copy(alpha = TRANSLUCENT_PANEL_ALPHA)) }
    // The bars are measured first and the content composed with their heights in the same frame.
    // Heights measured after the fact (onSizeChanged into state) reached the content a frame late:
    // every page's first frame showed its list from the very top, behind the header, before it
    // jumped into place, a flash each time a page opened.
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val barConstraints = constraints.copy(minHeight = 0)
        val headerPlaceables = subcompose(FrostedSlot.Header) {
            Column(Modifier.frosted(tint).then(topInsets), content = header)
        }.map { it.measure(barConstraints) }
        val footerPlaceables = if (footer == null) {
            emptyList()
        } else {
            subcompose(FrostedSlot.Footer) {
                Column(Modifier.frosted(footerTint).then(bottomInsets), content = footer)
            }.map { it.measure(barConstraints) }
        }
        val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
        val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0
        val contentPlaceables = subcompose(FrostedSlot.Content) {
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                content(headerHeight.toDp(), footerHeight.toDp())
            }
        }.map { it.measure(constraints) }
        // In drawing order: the content first, recording what the bars then draw blurred.
        layout(width, height) {
            contentPlaceables.forEach { it.place(0, 0) }
            headerPlaceables.forEach { it.place(0, 0) }
            footerPlaceables.forEach { it.place(0, height - footerHeight) }
        }
    }
}

private enum class FrostedSlot { Header, Footer, Content }

/**
 * Pull to refresh for a list under a [FrostedHeaderLayout]: the indicator comes down from under the
 * header rather than from the top of the pane, where the header would hide it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostedPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    headerHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = headerHeight),
            )
        },
        content = content,
    )
}

/** Top bars in a [FrostedHeaderLayout]: the header paints their background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun frostedTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

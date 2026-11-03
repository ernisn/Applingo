package io.github.ernisn.applingo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/*
 * Frosted glass for something floating over the content (the navigation island). Compose can't blur
 * what lies behind an element, so the content records what it draws into a layer
 * ([backdropSource]) and the floating element draws that layer a second time, through a blur and
 * lined up with its own position ([backdropBlur]). The layer refers to the content's own render
 * nodes, so the blur follows scrolling without the content being drawn twice per frame.
 */

/**
 * How much frosted surfaces blur what lies behind them: enough that their own text stays readable,
 * little enough that the content behind can still be made out (24 dp hid it altogether).
 */
val FrostBlur = 6.dp

class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the recorded content sits in the window. */
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this element's drawing for a [backdropBlur] over it, and draws it as usual. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Draws what the [backdrop] has under this element, blurred by [radius], behind this element's own
 * content. Goes after the element's clip: the blur reads content a little beyond the element's
 * edges, so they don't fade to the clamped edge colour, and the clip cuts it back to shape.
 */
@Composable
fun Modifier.backdropBlur(backdrop: Backdrop, radius: Dp): Modifier {
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val radiusPx = radius.toPx()
            val margin = ceil(radiusPx * 2)
            val offset = origin - backdrop.origin
            blurred.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            blurred.record(IntSize(ceil(size.width + 2 * margin).toInt(), ceil(size.height + 2 * margin).toInt())) {
                translate(margin - offset.x, margin - offset.y) { drawLayer(backdrop.layer) }
            }
            translate(-margin, -margin) { drawLayer(blurred) }
        }
}

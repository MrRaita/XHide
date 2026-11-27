package dev.hfw.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.hfw.data.Caps

/** The two graphics layers a glass top bar needs: one records the list, the other shows it blurred. */
class GlassLayers(val source: GraphicsLayer, val blurred: GraphicsLayer) {
    /** Position in the window of the node recorded into [source]; lets another surface find its own place inside it. */
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassLayers(): GlassLayers {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    if (Caps.glass) {
        val effect = remember(blurred) { BlurEffect(80f, 80f, TileMode.Clamp) }
        blurred.renderEffect = effect
    }
    return remember(source, blurred) { GlassLayers(source, blurred) }
}

/** Modifier for the screen's list: records it so the top bar can blur what scrolls underneath. */
@Composable
fun Modifier.glassSource(layers: GlassLayers): Modifier =
    if (Caps.glass) this.onPlaced { layers.origin = it.positionInRoot() }.recordInto(layers.source) else this

/**
 * Large collapsing top bar shared by all tabs. Transparent while expanded; on Android 14+ it becomes a blurred glass
 * pane with rounded bottom corners once the list scrolls under it. The title is a little larger than the M3 default.
 */
@Composable
fun HfwTopBar(
    title: String,
    scroll: TopAppBarScrollBehavior,
    layers: GlassLayers,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bg = MaterialTheme.colorScheme.background
    val barColor = if (Caps.glass) Color.Transparent else bg
    LargeTopAppBar(
        title = {
            val base = LocalTextStyle.current
            Text(
                text = title,
                style = if (base.fontSize.isSpecified && base.lineHeight.isSpecified) {
                    base.copy(fontSize = base.fontSize * 1.2f, lineHeight = base.lineHeight * 1.2f)
                } else {
                    base
                },
            )
        },
        actions = actions,
        collapsedHeight = 56.dp,
        expandedHeight = 116.dp,
        modifier = if (Caps.glass) {
            Modifier.glassHeader(layers.source, layers.blurred, bg) { scroll.state.collapsedFraction }
        } else {
            Modifier
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = barColor),
        scrollBehavior = scroll,
    )
}

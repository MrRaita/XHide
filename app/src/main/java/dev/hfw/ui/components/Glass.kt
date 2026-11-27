package dev.hfw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.hfw.data.Caps

/**
 * A card that looks like frosted glass on Android 14+: translucent tint, a soft top sheen and a light edge.
 * Below Android 14 it is an ordinary opaque surface, so callers never need to branch.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tintAlpha: Float = 0.55f,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!Caps.glass) {
        if (onClick != null) {
            Surface(onClick = onClick, shape = shape, color = tint, modifier = modifier, content = content)
        } else {
            Surface(shape = shape, color = tint, modifier = modifier, content = content)
        }
        return
    }
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.55f), MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), Color.White.copy(alpha = 0.25f)),
    )
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent))
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = tintAlpha))
            .background(sheen)
            .border(1.dp, edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColorFor(tint)) { content() }
    }
}

/** Soft color glows behind the content so the glass cards have something to refract. Only drawn on Android 14+. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val a = scheme.primary
    val b = scheme.tertiary
    val c = scheme.secondary
    Canvas(modifier.fillMaxSize()) {
        fun blob(color: Color, x: Float, y: Float, r: Float) {
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.20f), Color.Transparent), center = center, radius = r),
                radius = r,
                center = center,
            )
        }
        blob(a, size.width * 0.15f, size.height * 0.12f, size.width * 0.75f)
        blob(b, size.width * 0.95f, size.height * 0.38f, size.width * 0.70f)
        blob(c, size.width * 0.20f, size.height * 0.78f, size.width * 0.80f)
    }
}

/**
 * Records the content drawn by this modifier's node into [layer] while still drawing it normally.
 * The recorded copy is what [glassHeader] blurs to get a real "frosted" look.
 */
fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Top bar glass (Android 14+): invisible while expanded; as it collapses (`fraction` 0..1) it fades in as a pane with
 * rounded bottom corners that shows a blurred copy of the list scrolling underneath ([source], recorded with
 * [recordInto]) plus a light tint and sheen. [blurred] is a helper layer that carries the blur effect.
 * Everything is read in the draw phase, so scrolling does not recompose anything.
 */
fun Modifier.glassHeader(
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    tint: Color,
    fraction: () -> Float,
): Modifier = this
    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    .drawBehind {
        val f = fraction().coerceIn(0f, 1f)
        if (f < 0.02f) return@drawBehind
        blurred.record { drawLayer(source) }
        blurred.alpha = f
        drawLayer(blurred)
        drawRect(tint.copy(alpha = 0.70f * f))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f * f), Color.Transparent)))
    }

/**
 * Bottom bar surface. On Android 14+ it shows a blurred copy of what scrolls underneath ([layers].source holds the whole
 * page area, recorded with [glassSource]), with a light tint, a sheen and a soft edge; the app content stays sharp on top.
 * Below Android 14 it is an opaque pill with a thin outline.
 */
@Composable
fun Modifier.navGlass(layers: GlassLayers?, shape: Shape = androidx.compose.foundation.shape.CircleShape): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    if (!Caps.glass || layers == null) {
        return this.clip(shape).background(base).border(1.5.dp, scheme.outline.copy(alpha = 0.55f), shape)
    }
    var pos by remember { mutableStateOf(Offset.Zero) }
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), scheme.outline.copy(alpha = 0.22f)))
    return this
        .onPlaced { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            val d = pos - layers.origin
            layers.blurred.record { translate(-d.x, -d.y) { drawLayer(layers.source) } }
            layers.blurred.alpha = 1f
            drawLayer(layers.blurred)
            drawRect(base.copy(alpha = 0.58f))
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)))
        }
        .border(1.dp, edge, shape)
}

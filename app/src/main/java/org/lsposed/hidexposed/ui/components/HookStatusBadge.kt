package org.lsposed.hidexposed.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

private fun polygonFor(state: Boolean?): RoundedPolygon = when (state) {
    true -> MaterialShapes.Cookie9Sided
    false -> MaterialShapes.Burst
    null -> MaterialShapes.Sunny
}.normalized()

private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        var first = true
        for (c in morph.asCubics(progress)) {
            if (first) {
                path.moveTo(c.anchor0X, c.anchor0Y)
                first = false
            }
            path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        }
        path.close()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Status badge built from the official Material Expressive shapes. It morphs between
 * them when the hook state changes (checking -> active / inactive) and slowly rotates.
 */
@Composable
fun HookStatusBadge(
    state: Boolean?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    var shown by remember { mutableStateOf(state) }
    var morph by remember { mutableStateOf(Morph(polygonFor(state), polygonFor(state))) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(state) {
        if (state != shown) {
            morph = Morph(polygonFor(shown), polygonFor(state))
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            shown = state
        }
    }

    val rotation = rememberInfiniteTransition(label = "badgeSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "badgeRotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation.value }
            .background(color, MorphShape(morph, progress.value))
    )
}

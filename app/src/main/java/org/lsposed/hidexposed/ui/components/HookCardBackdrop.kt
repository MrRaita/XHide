package org.lsposed.hidexposed.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One drifting eye. Position/velocity are plain fields, only read from the draw phase. */
private class Eye(
    val sizeDp: Dp,
    val baseSpeedDp: Float,
    val startX: Float,
    val startY: Float,
    val angle: Float
) {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var ready = false
}

private fun newEyes() = listOf(
    Eye(26.dp, 10f, 0.10f, 0.15f, 0.6f),
    Eye(20.dp, 13f, 0.35f, 0.80f, 2.3f),
    Eye(30.dp, 8f, 0.60f, 0.20f, 4.0f),
    Eye(22.dp, 12f, 0.82f, 0.70f, 5.4f),
    Eye(18.dp, 14f, 0.95f, 0.10f, 1.4f)
)

/**
 * Soft blurred glows in the card corners plus a few slashed-eye icons that
 * bounce around inside the card: every wall hit gives them a small kick of
 * speed that then eases back to cruising speed. Purely decorative.
 */
@Composable
fun HookCardBackdrop(
    accent: Color,
    eyeColor: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val density = LocalDensity.current.density
    val eyes = remember { newEyes() }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                val w = width
                val h = height
                if (w > 0f && h > 0f) {
                    for (eye in eyes) {
                        val s = eye.sizeDp.value * density
                        val maxX = max(0f, w - s)
                        val maxY = max(0f, h - s)
                        val base = eye.baseSpeedDp * density
                        if (!eye.ready) {
                            eye.x = eye.startX * maxX
                            eye.y = eye.startY * maxY
                            eye.vx = cos(eye.angle) * base
                            eye.vy = sin(eye.angle) * base
                            eye.ready = true
                        }
                        eye.x += eye.vx * dt
                        eye.y += eye.vy * dt
                        var hit = false
                        if (eye.x < 0f) { eye.x = 0f; eye.vx = kotlin.math.abs(eye.vx); hit = true }
                        if (eye.x > maxX) { eye.x = maxX; eye.vx = -kotlin.math.abs(eye.vx); hit = true }
                        if (eye.y < 0f) { eye.y = 0f; eye.vy = kotlin.math.abs(eye.vy); hit = true }
                        if (eye.y > maxY) { eye.y = maxY; eye.vy = -kotlin.math.abs(eye.vy); hit = true }
                        if (hit) {
                            // Kick on impact.
                            eye.vx *= 1.25f
                            eye.vy *= 1.25f
                        }
                        // Ease back to cruising speed.
                        val speed = hypot(eye.vx, eye.vy)
                        if (speed > base) {
                            val f = max(base / speed, 1f - 0.8f * dt)
                            eye.vx *= f
                            eye.vy *= f
                        } else if (speed < base * 0.9f && speed > 0.001f) {
                            val f = min(base / speed, 1f + 1.0f * dt)
                            eye.vx *= f
                            eye.vy *= f
                        }
                    }
                    // Eye-to-eye collisions: elastic bounce, so the outgoing direction and speed
                    // depend on the angle and speed of the impact (mass ~ area).
                    for (i in eyes.indices) {
                        for (j in i + 1 until eyes.size) {
                            val a = eyes[i]
                            val b = eyes[j]
                            val ra = a.sizeDp.value * density / 2f
                            val rb = b.sizeDp.value * density / 2f
                            val dx = (b.x + rb) - (a.x + ra)
                            val dy = (b.y + rb) - (a.y + ra)
                            val dist = hypot(dx, dy)
                            val minDist = ra + rb
                            if (dist < minDist && dist > 0.001f) {
                                val nx = dx / dist
                                val ny = dy / dist
                                val ma = ra * ra
                                val mb = rb * rb
                                val closing = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                                if (closing < 0f) {
                                    val impulse = -2f * closing / (1f / ma + 1f / mb)
                                    a.vx -= impulse / ma * nx
                                    a.vy -= impulse / ma * ny
                                    b.vx += impulse / mb * nx
                                    b.vy += impulse / mb * ny
                                }
                                val overlap = minDist - dist
                                a.x -= nx * overlap * mb / (ma + mb)
                                a.y -= ny * overlap * mb / (ma + mb)
                                b.x += nx * overlap * ma / (ma + mb)
                                b.y += ny * overlap * ma / (ma + mb)
                            }
                        }
                    }
                }
                tick = now
            }
        }
    }

    val breathe = if (active) rememberBreathe() else 1f

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
    ) {
        // Blurred corner glows ("soft edges").
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset((-36).dp, (-36).dp)
                .size(104.dp)
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .blur(26.dp, BlurredEdgeTreatment.Unbounded)
                .background(accent.copy(alpha = 0.30f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(36.dp, 36.dp)
                .size(120.dp)
                .graphicsLayer {
                    val s = 2f - breathe
                    scaleX = s
                    scaleY = s
                }
                .blur(28.dp, BlurredEdgeTreatment.Unbounded)
                .background(accent.copy(alpha = 0.26f), CircleShape)
        )

        // Bouncing slashed-eye icons.
        for (eye in eyes) {
            Icon(
                painter = painterResource(id = org.lsposed.hidexposed.R.drawable.ic_eye_off),
                contentDescription = null,
                tint = eyeColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(eye.sizeDp)
                    .graphicsLayer {
                        @Suppress("UNUSED_VARIABLE") val t = tick // redraw every frame
                        translationX = eye.x
                        translationY = eye.y
                        rotationZ = (eye.vx / (eye.baseSpeedDp * density)) * 8f
                    }
            )
        }
    }
}

@Composable
private fun rememberBreathe(): Float {
    val transition = rememberInfiniteTransition(label = "hookBackdrop")
    val breathe by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    return breathe
}

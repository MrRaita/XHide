package dev.hfw.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The "Reduce animations" setting; effect-producing modifiers read it. */
val LocalReduceMotion = compositionLocalOf { false }

/** Bottom gap of lists: grows so the last rows do not end up under the floating bar. */
val LocalListBottomPadding = compositionLocalOf { 24.dp }

/** Top gap of lists: the header overlays the list, so the first row starts below the header. */
val LocalListTopPadding = compositionLocalOf { 0.dp }

/** Current height of the header in pixels (it shrinks while scrolling); read it only in the layout phase. */
val LocalHeaderHeight = compositionLocalOf<() -> Int> { { 0 } }

/**
 * Entry effect for list rows: only when the list first opens (while not scrolling) do the rows slide up one after another
 * and fade in. No effect is applied to rows while scrolling.
 */
@Composable
fun Modifier.staggerIn(index: Int, listState: ScrollableState): Modifier {
    val reduce = LocalReduceMotion.current
    val animate = !reduce && !listState.isScrollInProgress
    val entry = remember { Animatable(if (animate) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (animate) {
            if (index < 8) delay(index * 25L)
            entry.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
    }
    return this.graphicsLayer {
        val e = entry.value
        if (e != 0f) {
            translationY = e * 26.dp.toPx()
            alpha = 1f - e.coerceIn(0f, 1f) * 0.8f
        }
    }
}

/** Springy shrink effect while pressed. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/**
 * Fade + rise entrance (for plain scrolling columns), delayed by [index]. Plays only when [enabled] was true at the first
 * composition; otherwise the content is shown at once.
 */
@Composable
fun Modifier.staggeredEnter(index: Int, enabled: Boolean = true, stepMillis: Long = 55L, riseFrom: Dp = 28.dp): Modifier {
    val reduce = LocalReduceMotion.current
    val play = remember { enabled && !reduce }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            delay(index * stepMillis)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow))
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * riseFrom.toPx()
    }
}

/** Counts up from 0 only when [animateFromZero] is set; otherwise shows the value at once. */
@Composable
fun rememberCountUp(target: Int, animateFromZero: Boolean, startDelayMillis: Long = 0L, durationMillis: Int = 600): Int {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableIntStateOf(if (animateFromZero && !reduce) 0 else target) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        if (first && animateFromZero && !reduce) delay(startDelayMillis)
        first = false
        shown = target
    }
    val value by animateIntAsState(
        targetValue = shown,
        animationSpec = if (reduce) tween(0) else tween(durationMillis, easing = LinearOutSlowInEasing),
        label = "countUp",
    )
    return value
}

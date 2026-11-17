package org.lsposed.hidexposed.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Springy "squish" while the given source is pressed. State is read in the graphics layer only. */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** clickable + pressScale in one step for plain tappable tiles. */
fun Modifier.bouncyClickable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressScale(source).clickable(
        interactionSource = source,
        indication = LocalIndication.current,
        onClick = onClick
    )
}

/**
 * Fade + rise entrance, delayed by [index]. Only plays when [enabled] was true
 * at the first composition; otherwise the content is shown immediately.
 */
fun Modifier.staggeredEnter(
    index: Int,
    enabled: Boolean = true,
    stepMillis: Long = 55L,
    riseFrom: Dp = 28.dp
): Modifier = composed {
    val play = remember { enabled }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            delay(index * stepMillis)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
            )
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * riseFrom.toPx()
    }
}

/**
 * Counts up from 0 only when [animateFromZero] is set (first app launch); otherwise it shows
 * the value right away. [startDelayMillis] lets the count begin once the card is visible.
 * Later target changes animate from the current value.
 */
@Composable
fun rememberCountUp(
    target: Int,
    animateFromZero: Boolean,
    startDelayMillis: Long = 0L,
    durationMillis: Int = 600
): Int {
    var shown by remember { mutableIntStateOf(if (animateFromZero) 0 else target) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        if (first && animateFromZero) delay(startDelayMillis)
        first = false
        shown = target
    }
    val value by animateIntAsState(
        targetValue = shown,
        animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing),
        label = "countUp"
    )
    return value
}

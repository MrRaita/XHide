package dev.hfw.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Header shared by all tabs. Wide at the top of a list: the big app name with the tab name below. When the list scrolls
 * the app name is pulled up and the tab name takes its place; the lower edge follows the scroll movement.
 * [collapse] is 0 (wide) .. 1 (narrow) and is read only in the layout / draw phases.
 */
@Composable
fun AppHeader(
    appName: String,
    title: String,
    collapse: () -> Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onExpandedHeight: (Int) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
    val collapsedBandPx = with(density) { 56.dp.toPx() }
    val bigTitle = MaterialTheme.typography.displaySmall
    val compactTitle = bigTitle.copy(fontSize = 30.sp, lineHeight = 36.sp)

    // One card: the title block (which narrows while scrolling) and, optionally, content below it (search, filters)
    // that stays in place. The card's height simply follows its children.
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .clipToBounds(),
    ) {
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val c = collapse().coerceIn(0f, 1f)
                val collapsedH = statusPx + collapsedBandPx
                val h = (placeable.height + (collapsedH - placeable.height) * c).roundToInt()
                layout(placeable.width, h) { placeable.place(0, 0) }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { onExpandedHeight(it.height) }
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            // Wide view: app name with the small tab name below; fades out as it is pulled up.
            androidx.compose.foundation.layout.Column(
                Modifier
                    .padding(top = 20.dp, bottom = 14.dp, end = 120.dp)
                    .graphicsLayer {
                        val c = collapse()
                        translationY = -c * 46.dp.toPx()
                        alpha = (1f - c * 1.7f).coerceIn(0f, 1f)
                    },
            ) {
                Text(appName, style = bigTitle, color = cs.primary)
                TabTitle(title, MaterialTheme.typography.titleMedium, cs.onSurfaceVariant, reduceMotion)
            }
            // Narrow view: the tab name rises from below and takes the place of the app name, centred in the narrow band.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .height(56.dp)
                    .padding(end = 120.dp)
                    .graphicsLayer {
                        val c = collapse()
                        alpha = ((c - 0.45f) * 2f).coerceIn(0f, 1f)
                        translationY = (1f - c) * 26.dp.toPx()
                    },
            ) {
                Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
                    TabTitle(title, compactTitle, cs.primary, reduceMotion)
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp)
                    .graphicsLayer { translationY = -collapse() * 16.dp.toPx() },
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
    bottomContent?.invoke()
    }
}

@Composable
private fun TabTitle(title: String, style: TextStyle, color: Color, reduceMotion: Boolean) {
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
        },
        label = "title",
    ) { t ->
        Text(t, style = style, color = color, maxLines = 1)
    }
}

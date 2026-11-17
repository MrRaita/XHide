package org.lsposed.hidexposed.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Full-area wavy loading state (replaces the plain circular spinner). */
@Composable
fun WavyLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(modifier = Modifier.size(56.dp))
    }
}

/**
 * Pull-to-refresh indicator: a floating disc with a wavy ring. While pulling the ring
 * fills with the pull distance, while refreshing it runs the indeterminate wave.
 */
@Composable
fun BoxScope.WavyPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean
) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .size(48.dp)
            .graphicsLayer {
                val fraction = state.distanceFraction.coerceAtLeast(0f)
                translationY = fraction * 72.dp.toPx() - size.height
                val visible = fraction.coerceIn(0f, 1f)
                alpha = visible
                val s = 0.6f + 0.4f * visible
                scaleX = s
                scaleY = s
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isRefreshing) {
                CircularWavyProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                CircularWavyProgressIndicator(
                    progress = { state.distanceFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

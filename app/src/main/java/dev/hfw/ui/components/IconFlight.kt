package dev.hfw.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Where an [AppIcon] reports its position to the icon flight (the icon that travels between list row and detail page). */
enum class IconTrack { NONE, ROW, DETAIL }

/**
 * Bookkeeping for the icon that travels between an Apps row and the detail page. Rows and the detail icon report their
 * bounds here; while a flight is running ([flying] is the app key) both real icons hide and the shell draws one icon on top.
 */
class IconFlight {
    private class Entry(val owner: Any, val rect: Rect)

    private val rows = HashMap<String, Entry>()
    var detailRect by mutableStateOf<Rect?>(null)
    var flying by mutableStateOf<String?>(null)

    fun rowRect(key: String): Rect? = rows[key]?.rect
    fun setRow(key: String, owner: Any, rect: Rect) { rows[key] = Entry(owner, rect) }
    fun clearRow(key: String, owner: Any) { if (rows[key]?.owner === owner) rows.remove(key) }
}

val LocalIconFlight = staticCompositionLocalOf { IconFlight() }

@Composable
internal fun Modifier.flightTrack(key: String, track: IconTrack): Modifier {
    if (track == IconTrack.NONE) return this
    val flight = LocalIconFlight.current
    val owner = remember { Any() }
    if (track == IconTrack.ROW) {
        DisposableEffect(key) { onDispose { flight.clearRow(key, owner) } }
    }
    return this
        .onGloballyPositioned {
            if (track == IconTrack.ROW) flight.setRow(key, owner, it.boundsInRoot())
            // DETAIL: the detail page computes its own (scroll-dependent) rect and writes flight.detailRect itself.
        }
        .graphicsLayer { alpha = if (flight.flying == key) 0f else 1f }
}

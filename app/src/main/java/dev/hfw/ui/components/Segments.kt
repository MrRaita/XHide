package dev.hfw.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner shape for item [index] of [count] in a group that looks cut from one block: big outer corners on the first
 * and last item, small corners between neighbours.
 */
fun segmentShape(index: Int, count: Int, big: Dp = 28.dp, small: Dp = 6.dp): Shape {
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Hands out positions 0..count-1 to the rows of a group, in the order they are first composed. */
class SegmentCounter(val count: Int) {
    private var next = 0
    fun take(): Int = next++
}

val LocalSegments = compositionLocalOf<SegmentCounter?> { null }

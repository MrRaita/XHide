package org.lsposed.hidexposed.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

enum class CollapsePart { TITLE, CHIPS }

/**
 * Two-stage collapsing header: scrolling the list up first slides the title away,
 * then the filter chips (the search bar stays pinned). Scrolling down brings the chips
 * back right away; the title only comes back once the list is at the top.
 */
@Stable
class CollapsingHeaderState {
    var titleHeight by mutableFloatStateOf(0f)
    var chipsHeight by mutableFloatStateOf(0f)

    /** 0 = fully expanded, -(title + chips) = fully collapsed. */
    var offset by mutableFloatStateOf(0f)

    private val total: Float get() = titleHeight + chipsHeight

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f || total <= 0f) return Offset.Zero
            return if (dy < 0f) {
                val next = (offset + dy).coerceAtLeast(-total)
                val used = next - offset
                offset = next
                Offset(0f, used)
            } else {
                val limit = -titleHeight
                if (offset < limit) {
                    val next = (offset + dy).coerceAtMost(limit)
                    val used = next - offset
                    offset = next
                    Offset(0f, used)
                } else {
                    Offset.Zero
                }
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy > 0f && offset < 0f) {
                val next = (offset + dy).coerceAtMost(0f)
                val used = next - offset
                offset = next
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val t = titleHeight
            val c = chipsHeight
            val target = when {
                offset > -t * 0.5f -> 0f
                offset > -t - c * 0.5f -> -t
                else -> -(t + c)
            }
            if (target != offset) {
                animate(
                    initialValue = offset,
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { value, _ -> offset = value }
            }
            return Velocity.Zero
        }
    }
}

/** Shrinks and slides this block away according to the header state (layout-phase only). */
fun Modifier.collapsible(state: CollapsingHeaderState, part: CollapsePart): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val full = placeable.height.toFloat()
            val hidden = when (part) {
                CollapsePart.TITLE -> (-state.offset).coerceIn(0f, full)
                CollapsePart.CHIPS -> (-state.offset - state.titleHeight).coerceIn(0f, full)
            }
            val height = (full - hidden).roundToInt().coerceAtLeast(0)
            layout(placeable.width, height) {
                placeable.placeRelative(0, -hidden.roundToInt())
            }
        }

package dev.hfw.ui.components

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The icon mask of the running system (circle, squircle, teardrop...), the same one the launcher uses for adaptive
 * icons. Using it for every icon in the app keeps the list consistent with the home screen. Falls back to a rounded
 * square if the platform will not hand out its mask.
 */
object SystemIconShape : Shape {
    private const val UNIT = 1000

    private val mask: android.graphics.Path? by lazy {
        try {
            val icon = AdaptiveIconDrawable(ColorDrawable(0), ColorDrawable(0))
            icon.setBounds(0, 0, UNIT, UNIT)
            android.graphics.Path(icon.iconMask)
        } catch (_: Throwable) {
            null
        }
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val source = mask ?: return RoundedCornerShape(30).createOutline(size, layoutDirection, density)
        val path = Path()
        path.addPath(source.asComposePath())
        path.transform(Matrix().apply { scale(size.width / UNIT, size.height / UNIT) })
        return Outline.Generic(path)
    }
}

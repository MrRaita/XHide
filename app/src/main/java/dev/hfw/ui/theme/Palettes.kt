package dev.hfw.ui.theme

import androidx.annotation.StringRes
import dev.hfw.R

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Ready-made color palette: the scheme is generated from a main hue and a saturation multiplier. */
data class Palette(
    @StringRes val nameRes: Int,
    val hue: Float,
    /** 0 = fully neutral, 1 = fully colored */
    val sat: Float = 1f,
    /** If given, the tertiary (accent) color uses this hue and is vivid */
    val tertiaryHue: Float? = null
)

object Palettes {
    val list = listOf(
        Palette(R.string.pal_ocean, 212f),
        Palette(R.string.pal_turquoise, 178f),
        Palette(R.string.pal_forest, 140f),
        Palette(R.string.pal_olive, 78f),
        Palette(R.string.pal_amber, 38f),
        Palette(R.string.pal_coral, 10f),
        Palette(R.string.pal_rose, 338f),
        Palette(R.string.pal_plum, 305f),
        Palette(R.string.pal_lavender, 265f),
        Palette(R.string.pal_night, 235f, 0.7f),
        Palette(R.string.pal_nothing, 0f, 0f, 4f),
        Palette(R.string.pal_graphite, 210f, 0.2f)
    )

    /** Index stored in the settings for the user's own color. */
    const val CUSTOM = -1

    /** Hue and saturation of an ARGB color (HSL), as a palette. Lightness is not used: the scheme sets its own. */
    fun fromColor(argb: Int): Palette {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val l = (max + min) / 2f
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Palette(R.string.set_palette_custom, (h + 360f) % 360f, (s / 0.75f).coerceIn(0f, 1f))
    }

    private fun c(h: Float, s: Float, l: Float): Color =
        Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

    private fun onColor(bg: Color): Color =
        if (bg.luminance() > 0.179f) Color.Black else Color.White

    /** Sample color shown in the picker */
    fun swatch(p: Palette): Color =
        if (p.tertiaryHue != null) c(p.tertiaryHue, 0.85f, 0.50f) else c(p.hue, 0.30f + 0.35f * p.sat, 0.45f)

    fun scheme(p: Palette, dark: Boolean): ColorScheme {
        val h = p.hue
        val k = p.sat
        val h2 = h + 25f
        val th = p.tertiaryHue ?: (h + 60f)
        val sP = 0.62f * k
        val sS = 0.30f * k
        val sT = if (p.tertiaryHue != null) 0.80f else 0.40f * k
        val n = 0.04f + 0.06f * k // slight tint of the neutral surfaces
        val primary = c(h, sP, 0.38f)
        val secondary = c(h2, sS, 0.38f)
        val tertiary = c(th, sT, 0.40f)
        return if (!dark) lightColorScheme(
            primary = primary, onPrimary = onColor(primary),
            primaryContainer = c(h, 0.75f * k, 0.88f), onPrimaryContainer = c(h, 0.70f * k + 0.1f, 0.14f),
            secondary = secondary, onSecondary = onColor(secondary),
            secondaryContainer = c(h2, 0.40f * k, 0.88f), onSecondaryContainer = c(h2, 0.45f * k + 0.1f, 0.14f),
            tertiary = tertiary, onTertiary = onColor(tertiary),
            tertiaryContainer = c(th, sT + 0.10f, 0.88f), onTertiaryContainer = c(th, sT + 0.15f, 0.14f),
            background = c(h, n, 0.98f), onBackground = c(h, n, 0.10f),
            surface = c(h, n, 0.98f), onSurface = c(h, n, 0.10f),
            surfaceVariant = c(h, n + 0.06f, 0.90f), onSurfaceVariant = c(h, n + 0.04f, 0.30f),
            surfaceTint = c(h, sP, 0.38f),
            inverseSurface = c(h, n + 0.02f, 0.20f), inverseOnSurface = c(h, n, 0.95f),
            inversePrimary = c(h, 0.65f * k, 0.78f),
            outline = c(h, n, 0.46f), outlineVariant = c(h, n + 0.04f, 0.80f),
            surfaceBright = c(h, n, 0.98f), surfaceDim = c(h, n + 0.02f, 0.87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = c(h, n, 0.96f),
            surfaceContainer = c(h, n + 0.02f, 0.94f),
            surfaceContainerHigh = c(h, n + 0.03f, 0.92f),
            surfaceContainerHighest = c(h, n + 0.04f, 0.90f)
        ) else darkColorScheme(
            primary = c(h, 0.60f * k + 0.05f, 0.80f), onPrimary = c(h, 0.70f * k, 0.18f),
            primaryContainer = c(h, 0.55f * k, 0.28f), onPrimaryContainer = c(h, 0.75f * k, 0.90f),
            secondary = c(h2, 0.35f * k, 0.80f), onSecondary = c(h2, 0.40f * k, 0.18f),
            secondaryContainer = c(h2, 0.30f * k, 0.28f), onSecondaryContainer = c(h2, 0.45f * k, 0.90f),
            tertiary = c(th, sT + 0.10f, 0.80f), onTertiary = c(th, sT, 0.18f),
            tertiaryContainer = c(th, sT, 0.28f), onTertiaryContainer = c(th, sT + 0.10f, 0.90f),
            background = c(h, n, 0.08f), onBackground = c(h, n, 0.90f),
            surface = c(h, n, 0.08f), onSurface = c(h, n, 0.90f),
            surfaceVariant = c(h, n + 0.04f, 0.28f), onSurfaceVariant = c(h, n + 0.04f, 0.80f),
            surfaceTint = c(h, 0.60f * k + 0.05f, 0.80f),
            inverseSurface = c(h, n, 0.90f), inverseOnSurface = c(h, n, 0.20f),
            inversePrimary = c(h, sP, 0.38f),
            outline = c(h, n, 0.60f), outlineVariant = c(h, n + 0.02f, 0.28f),
            surfaceBright = c(h, n, 0.24f), surfaceDim = c(h, n, 0.06f),
            surfaceContainerLowest = c(h, n, 0.04f),
            surfaceContainerLow = c(h, n, 0.10f),
            surfaceContainer = c(h, n, 0.12f),
            surfaceContainerHigh = c(h, n, 0.16f),
            surfaceContainerHighest = c(h, n, 0.20f)
        )
    }
}

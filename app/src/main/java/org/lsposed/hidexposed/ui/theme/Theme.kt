package org.lsposed.hidexposed.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DefaultDarkScheme = darkColorScheme()
private val DefaultLightScheme = lightColorScheme()

@Composable
fun HideXposedTheme(
    themeMode: String = "SYSTEM",
    amoledBlack: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DefaultDarkScheme
        else -> DefaultLightScheme
    }

    val targetScheme = if (darkTheme && amoledBlack) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF141414),
            surfaceVariant = Color(0xFF161618),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF101012),
            surfaceContainerHigh = Color(0xFF141416),
            surfaceContainerHighest = Color(0xFF18181A)
        )
    } else {
        baseScheme
    }

    // Theme / palette / AMOLED changes cross-fade instead of snapping.
    val colorScheme = animateColorScheme(targetScheme)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

@Composable
private fun animatedColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320),
        label = "schemeColor"
    )
    return color
}

@Composable
private fun animateColorScheme(t: ColorScheme): ColorScheme = t.copy(
    primary = animatedColor(t.primary),
    onPrimary = animatedColor(t.onPrimary),
    primaryContainer = animatedColor(t.primaryContainer),
    onPrimaryContainer = animatedColor(t.onPrimaryContainer),
    inversePrimary = animatedColor(t.inversePrimary),
    secondary = animatedColor(t.secondary),
    onSecondary = animatedColor(t.onSecondary),
    secondaryContainer = animatedColor(t.secondaryContainer),
    onSecondaryContainer = animatedColor(t.onSecondaryContainer),
    tertiary = animatedColor(t.tertiary),
    onTertiary = animatedColor(t.onTertiary),
    tertiaryContainer = animatedColor(t.tertiaryContainer),
    onTertiaryContainer = animatedColor(t.onTertiaryContainer),
    background = animatedColor(t.background),
    onBackground = animatedColor(t.onBackground),
    surface = animatedColor(t.surface),
    onSurface = animatedColor(t.onSurface),
    surfaceVariant = animatedColor(t.surfaceVariant),
    onSurfaceVariant = animatedColor(t.onSurfaceVariant),
    surfaceTint = animatedColor(t.surfaceTint),
    inverseSurface = animatedColor(t.inverseSurface),
    inverseOnSurface = animatedColor(t.inverseOnSurface),
    error = animatedColor(t.error),
    onError = animatedColor(t.onError),
    errorContainer = animatedColor(t.errorContainer),
    onErrorContainer = animatedColor(t.onErrorContainer),
    outline = animatedColor(t.outline),
    outlineVariant = animatedColor(t.outlineVariant),
    scrim = animatedColor(t.scrim),
    surfaceBright = animatedColor(t.surfaceBright),
    surfaceDim = animatedColor(t.surfaceDim),
    surfaceContainer = animatedColor(t.surfaceContainer),
    surfaceContainerHigh = animatedColor(t.surfaceContainerHigh),
    surfaceContainerHighest = animatedColor(t.surfaceContainerHighest),
    surfaceContainerLow = animatedColor(t.surfaceContainerLow),
    surfaceContainerLowest = animatedColor(t.surfaceContainerLowest)
)

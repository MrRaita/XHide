package org.lsposed.hidexposed.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

    val targetScheme = remember(darkTheme, amoledBlack, dynamicColor, context) {
        val baseScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DefaultDarkScheme
            else -> DefaultLightScheme
        }

        if (darkTheme && amoledBlack) {
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
    }

    // Theme / palette / AMOLED changes cross-fade instead of snapping.
    val colorScheme = animateColorScheme(targetScheme)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

/**
 * Cross-fades between colour schemes with a single progress value instead of one
 * animation state per colour, so a theme change costs one animation, not 36.
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        // Start from whatever is on screen right now, so an interrupted fade stays smooth.
        from = lerpScheme(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 320))
    }

    val fraction = progress.value
    return if (fraction >= 1f) to else lerpScheme(from, to, fraction)
}

private fun lerpScheme(a: ColorScheme, b: ColorScheme, f: Float): ColorScheme = a.copy(
    primary = lerp(a.primary, b.primary, f),
    onPrimary = lerp(a.onPrimary, b.onPrimary, f),
    primaryContainer = lerp(a.primaryContainer, b.primaryContainer, f),
    onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, f),
    inversePrimary = lerp(a.inversePrimary, b.inversePrimary, f),
    secondary = lerp(a.secondary, b.secondary, f),
    onSecondary = lerp(a.onSecondary, b.onSecondary, f),
    secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, f),
    onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, f),
    tertiary = lerp(a.tertiary, b.tertiary, f),
    onTertiary = lerp(a.onTertiary, b.onTertiary, f),
    tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, f),
    onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, f),
    background = lerp(a.background, b.background, f),
    onBackground = lerp(a.onBackground, b.onBackground, f),
    surface = lerp(a.surface, b.surface, f),
    onSurface = lerp(a.onSurface, b.onSurface, f),
    surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, f),
    onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, f),
    surfaceTint = lerp(a.surfaceTint, b.surfaceTint, f),
    inverseSurface = lerp(a.inverseSurface, b.inverseSurface, f),
    inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, f),
    error = lerp(a.error, b.error, f),
    onError = lerp(a.onError, b.onError, f),
    errorContainer = lerp(a.errorContainer, b.errorContainer, f),
    onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, f),
    outline = lerp(a.outline, b.outline, f),
    outlineVariant = lerp(a.outlineVariant, b.outlineVariant, f),
    scrim = lerp(a.scrim, b.scrim, f),
    surfaceBright = lerp(a.surfaceBright, b.surfaceBright, f),
    surfaceDim = lerp(a.surfaceDim, b.surfaceDim, f),
    surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, f),
    surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, f),
    surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, f),
    surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, f),
    surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, f)
)

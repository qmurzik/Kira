package com.qmurzik.animetv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnimeTvDarkColors = darkColorScheme(
    primary = TvPrimary,
    onPrimary = TvOnBackground,
    primaryContainer = TvPrimaryDim,
    onPrimaryContainer = TvOnBackground,
    secondary = TvAccent,
    onSecondary = TvBackground,
    background = TvBackground,
    onBackground = TvOnBackground,
    surface = TvSurface,
    onSurface = TvOnBackground,
    surfaceVariant = TvSurfaceVariant,
    onSurfaceVariant = TvOnSurfaceVariant,
    surfaceContainerHigh = TvSurfaceContainerHigh,
    error = TvError,
    onError = TvOnBackground,
    outline = TvOnSurfaceMuted,
)

/**
 * Always-dark, TV-first theme. [textScale] is threaded from Settings -> Appearance ->
 * "text size"; [animationsEnabled] is read by components that offer a reduced-motion path.
 */
@Composable
fun AnimeTvTheme(
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    // Deliberately ignores the platform day/night setting: the brief calls for a consistent
    // dark, cinematic look regardless of platform theme - this is a streaming app, not a
    // general-purpose utility.
    MaterialTheme(
        colorScheme = AnimeTvDarkColors,
        typography = tvTypography(textScale),
        content = content,
    )
}

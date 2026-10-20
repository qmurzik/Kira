package app.pulse.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import app.pulse.client.data.settings.ThemeMode

// Own palette (indigo/violet accent) — deliberately not VK's blue.
private val Accent = Color(0xFF5B4FE0)
private val AccentDark = Color(0xFF8E85FF)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E3FF),
    onPrimaryContainer = Color(0xFF241A8C),
    secondary = Color(0xFF0FA18A),
    onSecondary = Color.White,
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF15161A),
    surface = Color.White,
    onSurface = Color(0xFF15161A),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = Color(0xFF75777F),
    surfaceContainer = Color(0xFFF7F7FA),
    outline = Color(0xFFDCDDE3),
    outlineVariant = Color(0xFFE8E9EE),
    error = Color(0xFFE5484D),
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF14105A),
    primaryContainer = Color(0xFF332B9E),
    onPrimaryContainer = Color(0xFFE6E3FF),
    secondary = Color(0xFF3FD2B8),
    onSecondary = Color(0xFF00382F),
    background = Color(0xFF141418),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF1C1C21),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFF9A9AA4),
    surfaceContainer = Color(0xFF222227),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF2C2C33),
    error = Color(0xFFFF6369),
)

/** Extra colors Material3 has no slot for. */
@Immutable
data class ExtraColors(
    val online: Color,
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val unreadBadge: Color,
    val mutedBadge: Color,
    val like: Color,
)

private val LightExtra = ExtraColors(
    online = Color(0xFF2EB86E),
    incomingBubble = Color(0xFFFFFFFF),
    outgoingBubble = Color(0xFFDCD8FF),
    onOutgoingBubble = Color(0xFF15161A),
    unreadBadge = Accent,
    mutedBadge = Color(0xFFA3A5AD),
    like = Color(0xFFE5484D),
)

private val DarkExtra = ExtraColors(
    online = Color(0xFF3FD27F),
    incomingBubble = Color(0xFF26262C),
    outgoingBubble = Color(0xFF3A3290),
    onOutgoingBubble = Color(0xFFF1F0FF),
    unreadBadge = AccentDark,
    mutedBadge = Color(0xFF5C5C66),
    like = Color(0xFFFF6369),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

object AppTheme {
    val extra: ExtraColors
        @Composable get() = LocalExtraColors.current
}

@Composable
fun PulseTheme(
    mode: ThemeMode = ThemeMode.System,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalExtraColors provides if (dark) DarkExtra else LightExtra,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}

package com.yumi.launcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Glacier, the Yumi palette, copied value for value from Yumi's `design` module so the launcher and
 * the VPN client read as one family. The launcher opens on the light scheme: its home screen is a
 * daytime sky, and the dark scheme would sit on it like a hole.
 *
 * Sand (secondary) and mint (tertiary) are the two quiet colours next to the ice; coral is kept
 * for failure — here, the "stop the game" state.
 */

private val IceDark = Color(0xFF2B5CAB)
private val Ice = Color(0xFFA8C7FF)

val YumiLightColors = lightColorScheme(
    primary = IceDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E4FA),
    onPrimaryContainer = Color(0xFF08203F),
    inversePrimary = Ice,

    secondary = Color(0xFF7A5514),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE0B4),
    onSecondaryContainer = Color(0xFF2A1C03),

    tertiary = Color(0xFF1F6B54),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB9EFDC),
    onTertiaryContainer = Color(0xFF002018),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD3),
    onErrorContainer = Color(0xFF410800),

    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0E1319),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF0E1319),
    surfaceVariant = Color(0xFFDFE4EC),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceTint = IceDark,

    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8DDE6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF1F7),
    surfaceContainer = Color(0xFFE7EBF2),
    surfaceContainerHigh = Color(0xFFE1E6EE),
    surfaceContainerHighest = Color(0xFFDAE0E9),

    inverseSurface = Color(0xFF1A1F27),
    inverseOnSurface = Color(0xFFF0F3F8),

    outline = Color(0xFF77839A),
    outlineVariant = Color(0xFFC5CCD8),
    scrim = Color.Black,
)

val YumiDarkColors = darkColorScheme(
    primary = Ice,
    onPrimary = Color(0xFF07203F),
    primaryContainer = Color(0xFF24314A),
    onPrimaryContainer = Color(0xFFD5E3FF),
    inversePrimary = IceDark,

    secondary = Color(0xFFF0BE73),
    onSecondary = Color(0xFF402B08),
    secondaryContainer = Color(0xFF5A4118),
    onSecondaryContainer = Color(0xFFFFDEB0),

    tertiary = Color(0xFF8FD6C0),
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFF06503C),
    onTertiaryContainer = Color(0xFFA9F2DC),

    error = Color(0xFFFF9885),
    onError = Color(0xFF5C1508),
    errorContainer = Color(0xFF7E2415),
    onErrorContainer = Color(0xFFFFDAD3),

    background = Color(0xFF090B0F),
    onBackground = Color(0xFFE7ECF4),
    surface = Color(0xFF090B0F),
    onSurface = Color(0xFFE7ECF4),
    surfaceVariant = Color(0xFF29313D),
    onSurfaceVariant = Color(0xFF9AA5B6),
    surfaceTint = Ice,

    surfaceBright = Color(0xFF2C333F),
    surfaceDim = Color(0xFF090B0F),
    surfaceContainerLowest = Color(0xFF05070A),
    surfaceContainerLow = Color(0xFF0F131A),
    surfaceContainer = Color(0xFF151A22),
    surfaceContainerHigh = Color(0xFF1C222C),
    surfaceContainerHighest = Color(0xFF232A35),

    inverseSurface = Color(0xFFE7ECF4),
    inverseOnSurface = Color(0xFF1A1F27),

    outline = Color(0xFF4E5867),
    outlineVariant = Color(0xFF29313D),
    scrim = Color.Black,
)

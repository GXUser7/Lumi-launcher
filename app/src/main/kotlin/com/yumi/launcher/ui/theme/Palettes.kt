package com.yumi.launcher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yumi.launcher.settings.Palette
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The colour scheme for [palette] in the light or dark theme; built once, then reused. */
fun colorScheme(palette: Palette, dark: Boolean): ColorScheme = schemes.getOrPut(palette to dark) {
    when (palette) {
        Palette.Original -> if (dark) YumiDarkColors else YumiLightColors
        // Mint with pistachio and apricot.
        Palette.Green -> pastel(Hues(primary = 155f, secondary = 115f, tertiary = 60f), dark)
        // Sakura with lilac and mint.
        Palette.Pink -> pastel(Hues(primary = 355f, secondary = 320f, tertiary = 175f), dark)
        // Sky with lavender and butter.
        Palette.Blue -> pastel(Hues(primary = 240f, secondary = 285f, tertiary = 75f), dark)
        // Lavender with periwinkle and rose.
        Palette.Lavender -> pastel(Hues(primary = 300f, secondary = 265f, tertiary = 350f), dark)
        // Cherry with raspberry and leaf green.
        Palette.Cherry -> pastel(Hues(primary = 15f, secondary = 350f, tertiary = 150f), dark)
        // Peach with coral and sea.
        Palette.Peach -> pastel(Hues(primary = 45f, secondary = 25f, tertiary = 195f), dark)
        // Amber with caramel and sage: warm browns in the dark, like a lamp-lit room.
        Palette.Amber -> pastel(Hues(primary = 70f, secondary = 50f, tertiary = 135f), dark)
        // Sea glass with steel blue and sand.
        Palette.Teal -> pastel(Hues(primary = 195f, secondary = 225f, tertiary = 60f), dark)
        // Near-grey with a breath of steel: for those who want the island to be the only colour.
        Palette.Graphite -> pastel(Hues(primary = 255f, secondary = 230f, tertiary = 60f, chroma = 0.14f), dark)
    }
}

private val schemes = java.util.concurrent.ConcurrentHashMap<Pair<Palette, Boolean>, ColorScheme>()

/**
 * Hues in OKLCH degrees: the accent, and the two quiet colours beside it. [chroma] scales every
 * colour's chroma — below 1 the palette greys out while keeping the same lightness steps.
 */
private class Hues(val primary: Float, val secondary: Float, val tertiary: Float, val chroma: Float = 1f)

/**
 * A pastel scheme built from three hues. Built in OKLCH rather than picked by hand, so every
 * palette has the same lightness steps — the same contrast for text on buttons and on containers —
 * and only the hue changes. Pastel here means soft, dusty accents and containers, and surfaces
 * tinted with the accent instead of plain grey.
 */
private fun pastel(h: Hues, dark: Boolean): ColorScheme {
    fun tone(l: Float, c: Float, hue: Float) = oklch(l, c * h.chroma, hue)
    // Surfaces take a trace of the accent; the text on them stays close to neutral.
    fun neutral(l: Float, c: Float = 0.012f) = tone(l, c, h.primary)
    val error = if (dark) YumiDarkColors else YumiLightColors
    return if (!dark) {
        lightColorScheme(
            // White on the accent, as in Glacier: the Yumi mark, the play button, picked buttons. The
            // accent is deep enough for that, and for text in it — links, the greeting — on the surface.
            primary = tone(0.60f, 0.115f, h.primary),
            onPrimary = Color.White,
            primaryContainer = tone(0.925f, 0.045f, h.primary),
            onPrimaryContainer = tone(0.30f, 0.070f, h.primary),
            inversePrimary = tone(0.84f, 0.080f, h.primary),

            secondary = tone(0.62f, 0.080f, h.secondary),
            onSecondary = tone(0.22f, 0.045f, h.secondary),
            secondaryContainer = tone(0.925f, 0.040f, h.secondary),
            onSecondaryContainer = tone(0.30f, 0.055f, h.secondary),

            tertiary = tone(0.62f, 0.090f, h.tertiary),
            onTertiary = tone(0.22f, 0.050f, h.tertiary),
            tertiaryContainer = tone(0.925f, 0.045f, h.tertiary),
            onTertiaryContainer = tone(0.30f, 0.060f, h.tertiary),

            error = error.error,
            onError = error.onError,
            errorContainer = error.errorContainer,
            onErrorContainer = error.onErrorContainer,

            background = neutral(0.975f),
            onBackground = neutral(0.21f, 0.015f),
            surface = neutral(0.975f),
            onSurface = neutral(0.21f, 0.015f),
            surfaceVariant = neutral(0.905f, 0.020f),
            onSurfaceVariant = neutral(0.46f, 0.030f),
            surfaceTint = tone(0.60f, 0.115f, h.primary),

            surfaceBright = Color.White,
            surfaceDim = neutral(0.88f, 0.016f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutral(0.960f, 0.014f),
            surfaceContainer = neutral(0.945f, 0.016f),
            surfaceContainerHigh = neutral(0.925f, 0.018f),
            surfaceContainerHighest = neutral(0.905f, 0.020f),

            inverseSurface = neutral(0.26f, 0.015f),
            inverseOnSurface = neutral(0.96f),

            outline = neutral(0.60f, 0.030f),
            outlineVariant = neutral(0.85f, 0.022f),
            scrim = Color.Black,
        )
    } else {
        darkColorScheme(
            primary = tone(0.84f, 0.085f, h.primary),
            onPrimary = tone(0.26f, 0.055f, h.primary),
            primaryContainer = tone(0.34f, 0.060f, h.primary),
            onPrimaryContainer = tone(0.91f, 0.045f, h.primary),
            inversePrimary = tone(0.62f, 0.110f, h.primary),

            secondary = tone(0.82f, 0.065f, h.secondary),
            onSecondary = tone(0.25f, 0.045f, h.secondary),
            secondaryContainer = tone(0.33f, 0.045f, h.secondary),
            onSecondaryContainer = tone(0.90f, 0.040f, h.secondary),

            tertiary = tone(0.83f, 0.075f, h.tertiary),
            onTertiary = tone(0.25f, 0.050f, h.tertiary),
            tertiaryContainer = tone(0.34f, 0.050f, h.tertiary),
            onTertiaryContainer = tone(0.91f, 0.045f, h.tertiary),

            error = error.error,
            onError = error.onError,
            errorContainer = error.errorContainer,
            onErrorContainer = error.onErrorContainer,

            background = neutral(0.15f, 0.010f),
            onBackground = neutral(0.93f, 0.012f),
            surface = neutral(0.15f, 0.010f),
            onSurface = neutral(0.93f, 0.012f),
            surfaceVariant = neutral(0.31f, 0.018f),
            onSurfaceVariant = neutral(0.74f, 0.022f),
            surfaceTint = tone(0.84f, 0.085f, h.primary),

            surfaceBright = neutral(0.31f, 0.016f),
            surfaceDim = neutral(0.15f, 0.010f),
            surfaceContainerLowest = neutral(0.12f, 0.008f),
            surfaceContainerLow = neutral(0.18f, 0.012f),
            surfaceContainer = neutral(0.205f, 0.014f),
            surfaceContainerHigh = neutral(0.235f, 0.016f),
            surfaceContainerHighest = neutral(0.265f, 0.018f),

            inverseSurface = neutral(0.93f, 0.012f),
            inverseOnSurface = neutral(0.22f, 0.012f),

            outline = neutral(0.52f, 0.022f),
            outlineVariant = neutral(0.32f, 0.018f),
            scrim = Color.Black,
        )
    }
}

/**
 * OKLCH → sRGB. Lightness 0–1, chroma about 0–0.3, hue in degrees. A colour outside sRGB loses
 * chroma until it fits, rather than being clipped channel by channel — clipping shifts the hue.
 */
internal fun oklch(l: Float, c: Float, hue: Float): Color {
    var chroma = c
    while (true) {
        val rgb = toLinearSrgb(l, chroma, hue)
        if (rgb.all { it in -0.0001f..1.0001f } || chroma <= 0f) {
            val (r, g, b) = rgb.map { encode(it.coerceIn(0f, 1f)) }
            return Color(r, g, b)
        }
        chroma -= 0.002f
    }
}

internal fun toLinearSrgb(l: Float, c: Float, hue: Float): List<Float> {
    val h = Math.toRadians(hue.toDouble())
    val a = c * cos(h).toFloat()
    val b = c * sin(h).toFloat()
    val l1 = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
    val m1 = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
    val s1 = (l - 0.0894841775f * a - 1.2914855480f * b).pow(3)
    return listOf(
        4.0767416621f * l1 - 3.3077115913f * m1 + 0.2309699292f * s1,
        -1.2684380046f * l1 + 2.6097574011f * m1 - 0.3413193965f * s1,
        -0.0041960863f * l1 - 0.7034186147f * m1 + 1.7076147010f * s1,
    )
}

/** Linear light → the sRGB transfer curve. */
internal fun encode(x: Float): Float = if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f

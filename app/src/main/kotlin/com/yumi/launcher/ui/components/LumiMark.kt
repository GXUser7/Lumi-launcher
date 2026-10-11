package com.yumi.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The Lumi mark on a round plate: the launcher's icon, a pixel lantern, in the palette's colours.
 *
 * The frame takes [glyph], so the mark follows the palette and the theme as the buttons do; the
 * light inside stays warm in every palette — it is the lantern's light, not an accent. The lantern
 * is drawn once at one texel a pixel and scaled by a whole number without filtering: drawn as
 * rectangles at 1.7 px a pixel it would blur, and its rows would show seams.
 */
@Composable
fun LumiMark(plate: Color, glyph: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val lantern = remember(glyph) { lanternImage(glyph) }
    Canvas(modifier.size(size)) {
        drawCircle(plate)
        // Two thirds to four fifths of the plate, in whole screen pixels per lantern pixel; rounded
        // down, the lantern's corners stay inside the circle at any scale.
        val unit = (this.size.minDimension * 0.8f / LANTERN.size).toInt().coerceAtLeast(1)
        val side = LANTERN.size * unit
        drawImage(
            lantern,
            dstOffset = IntOffset(((this.size.width - side) / 2).roundToInt(), ((this.size.height - side) / 2).roundToInt()),
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * The lantern on a 14×14 grid, the same as `scripts/make-icon.py` draws for the app icon:
 * c handle, I frame, i its lit rims, g the glass's edge, y the light, w its core.
 */
internal val LANTERN = listOf(
    ".....cccc.....",
    "....c....c....",
    "....c....c....",
    "....IIIIII....",
    "...IiiiiiiI...",
    "...IIIIIIII...",
    "...IgyyyygI...",
    "...IyywwyyI...",
    "...IywwwwyI...",
    "...IywwwwyI...",
    "...IyywwyyI...",
    "...IgyyyygI...",
    "...IIIIIIII...",
    "....iiiiii....",
)

/** A lantern pixel's colour with the frame in [glyph]; null where the plate shows. */
internal fun lanternColor(cell: Char, glyph: Color): Color? = when (cell) {
    'I' -> glyph
    'c', 'i' -> glyph.copy(alpha = glyph.alpha * 0.7f)
    'g' -> Color(0xFFF2A23A)
    'y' -> Color(0xFFFFC85A)
    'w' -> Color(0xFFFFF2B8)
    else -> null
}

private fun lanternImage(glyph: Color): ImageBitmap {
    val image = ImageBitmap(LANTERN.size, LANTERN.size)
    val canvas = Canvas(image)
    val paint = Paint().apply { isAntiAlias = false }
    LANTERN.forEachIndexed { y, row ->
        row.forEachIndexed { x, cell ->
            lanternColor(cell, glyph)?.let {
                paint.color = it
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
            }
        }
    }
    return image
}

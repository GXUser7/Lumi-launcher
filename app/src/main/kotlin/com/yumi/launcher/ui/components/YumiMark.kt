package com.yumi.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Yumi mark on a round plate — the launcher's version of the icon.
 *
 * The geometry is YumiDesktop's `YumiMark`; only the plate changed. In the launcher it sits inside
 * a fully round glass badge, and a square tile there broke the concentric circle the badge draws.
 * The glyph fits the circle untouched: its farthest corner is 157 units from the centre of a
 * 183-unit radius.
 *
 * The three glyph shapes are filled one at a time, as in the original: merged into one path their
 * opposite windings cancel where the staircase crosses the upper right tile and punch a hole in it.
 */
@Composable
fun YumiMark(plate: Color, glyph: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val marks = remember { markPaths() }
    Canvas(modifier.size(size)) {
        drawCircle(plate)
        withTransform({
            scale(this@Canvas.size.width / SIDE, this@Canvas.size.height / SIDE, pivot = Offset.Zero)
        }) {
            marks.forEach { drawPath(it, glyph) }
        }
    }
}

private const val SIDE = 366f

private fun markPaths(): List<Path> = listOf(
    Path().apply { addRoundRect(RoundRect(81f, 65f, 160f, 144f, CornerRadius(11f))) },
    Path().apply { addRoundRect(RoundRect(208.219f, 65.3521f, 287.219f, 144.3521f, CornerRadius(11f))) },
    PathParser().parsePathString(STAIRCASE).toPath(),
)

internal const val STAIRCASE =
    "M130.219 252.352C130.219 258.427 135.144 263.352 141.219 263.352H169.219V291.352C169.219 " +
        "297.427 164.294 302.352 158.219 302.352H101.219C95.144 302.352 90.2191 297.427 90.2191 " +
        "291.352V234.352C90.2191 228.277 95.144 223.352 101.219 223.352H130.219V252.352ZM169.219 " +
        "212.352C169.219 218.427 174.144 223.352 180.219 223.352H209.219V252.352C209.219 258.427 " +
        "204.294 263.352 198.219 263.352H169.219V234.352C169.219 228.277 164.294 223.352 158.219 " +
        "223.352H130.219V195.352C130.219 189.277 135.144 184.352 141.219 184.352H169.219V212.352Z" +
        "M208.219 173.352C208.219 179.427 213.144 184.352 219.219 184.352H248.219V212.352C248.219 " +
        "218.427 243.294 223.352 237.219 223.352H209.219V195.352C209.219 189.277 204.294 184.352 " +
        "198.219 184.352H169.219V155.352C169.219 149.277 174.144 144.352 180.219 144.352H208.219V" +
        "173.352ZM276.219 105.352C282.294 105.352 287.219 110.277 287.219 116.352L287.219 173.352" +
        "C287.219 179.427 282.294 184.352 276.219 184.352H248.219V155.352C248.219 149.277 243.294 " +
        "144.352 237.219 144.352H208.219V116.352C208.219 110.277 213.144 105.352 219.219 105.352H" +
        "276.219Z"

package com.yumi.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * An 8×8 skin face — a stand-in until the avatar is cut from the player's real skin texture,
 * whose face is the same 8×8 square at (8, 8).
 */
@Composable
fun PixelFace(modifier: Modifier = Modifier, offline: Boolean = false) {
    val c = if (offline) OfflineFace else DefaultFace
    Canvas(modifier) {
        val px = size.width / 8
        fun cell(x: Int, y: Int, w: Int, h: Int, color: Color) =
            drawRect(color, Offset(x * px, y * px), Size(w * px + 0.5f, h * px + 0.5f))
        cell(0, 0, 8, 8, c.skin)
        cell(0, 0, 8, 2, c.hair)
        cell(0, 2, 1, 1, c.hair)
        cell(7, 2, 1, 1, c.hair)
        cell(1, 4, 1, 1, c.white)
        cell(2, 4, 1, 1, c.iris)
        cell(5, 4, 1, 1, c.iris)
        cell(6, 4, 1, 1, c.white)
        cell(3, 6, 2, 1, c.mouth)
        cell(0, 7, 8, 1, c.shade)
    }
}

private class FaceColors(val skin: Color, val hair: Color, val white: Color, val iris: Color, val mouth: Color, val shade: Color)

private val DefaultFace = FaceColors(Color(0xFFE2AE80), Color(0xFF5A3322), Color(0xFFF4F4F4), Color(0xFF3A6FD6), Color(0xFFA8674D), Color(0xFFCF9467))
private val OfflineFace = FaceColors(Color(0xFF8D5A3B), Color(0xFF1F1F24), Color(0xFFF4F4F4), Color(0xFF2F9E5A), Color(0xFF5C3522), Color(0xFF7A4B30))

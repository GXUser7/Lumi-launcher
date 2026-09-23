package com.yumi.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * A player's face cut from their skin: the 8×8 face at (8, 8) and the hat layer over it at
 * (40, 8), scaled up without smoothing so the pixels stay pixels. Until the skin arrives — or
 * when there is none — the stand-in [PixelFace].
 */
@Composable
fun SkinHead(skinUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val skin by produceState(skinUrl?.let(ImageCache::memory), skinUrl) {
        if (skinUrl != null && value == null) value = ImageCache.load(skinUrl, disk = false)
    }
    Box(
        modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = skin
        if (bitmap == null) {
            PixelFace(Modifier.fillMaxSize())
            return@Box
        }
        Canvas(Modifier.size(size * 0.62f)) {
            // HD skins are 128 or more wide; the layout is the same, only scaled.
            val unit = (bitmap.width / 64).coerceAtLeast(1)
            val face = this.size.width.toInt()
            drawImage(
                bitmap,
                srcOffset = IntOffset(8 * unit, 8 * unit),
                srcSize = IntSize(8 * unit, 8 * unit),
                dstSize = IntSize(face, face),
                filterQuality = FilterQuality.None,
            )
            // The hat sits a half-pixel proud of the face, as it does in the game.
            val hat = face * 9 / 8
            val inset = (hat - face) / 2
            drawImage(
                bitmap,
                srcOffset = IntOffset(40 * unit, 8 * unit),
                srcSize = IntSize(8 * unit, 8 * unit),
                dstOffset = IntOffset(-inset, -inset),
                dstSize = IntSize(hat, hat),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

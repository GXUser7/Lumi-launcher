package com.yumi.launcher.scene

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.yumi.launcher.settings.SceneMotion
import com.yumi.launcher.settings.SceneQuality
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/**
 * `--scene-shot <folder> [quality…]`: the island backdrop without a window — frames drawn on the CPU
 * into PNGs, by day and by night, over the whole island and from the smithy, at each quality asked
 * for (Ultra and High by default). For looking at the sky and the clouds while working on them; the
 * window draws the same frames on the GPU.
 */
internal fun sceneShot(args: List<String>): Int {
    val folder = File(args.firstOrNull() ?: return 2.also { println("Использование: --scene-shot <папка> [качество…]") }).apply { mkdirs() }
    val qualities = args.drop(1).mapNotNull { name -> SceneQuality.entries.firstOrNull { it.name.equals(name, true) } }
        .ifEmpty { listOf(SceneQuality.Ultra, SceneQuality.High) }
    val scene = islandScene()
    val width = 1280
    val height = 800
    for (quality in qualities) for (night in listOf(false, true)) for (spot in listOf(SceneSpot.Overview, SceneSpot.Forge)) {
        val started = System.currentTimeMillis()
        val renderer = VoxelRenderer(scene)
        renderer.options = SceneOptions(quality, SceneMotion.Mouse, 60)
        renderer.night = if (night) 1f else 0f
        renderer.spot = spot
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val parallax = Parallax()
        // Two frames: the first sets the camera down, the second is some seconds on — clouds moved, stars twinkled.
        for (millis in listOf(0L, 90L, 180L)) {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                renderer.draw(this, millis, parallax)
            }
        }
        val name = "${quality.name.lowercase()}-${if (night) "night" else "day"}-${spot.name.lowercase()}.png"
        val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: continue
        folder.resolve(name).writeBytes(png.bytes)
        println("$name за ${System.currentTimeMillis() - started} мс")
    }
    return 0
}

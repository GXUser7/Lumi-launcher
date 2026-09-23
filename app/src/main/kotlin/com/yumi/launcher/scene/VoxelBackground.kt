package com.yumi.launcher.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import com.yumi.launcher.settings.SceneMotion
import com.yumi.launcher.settings.SceneQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import org.jetbrains.skia.BlendMode as SkiaBlendMode

/** Where the pointer is over the window, -1..1 on both axes. Written by input, read per frame. */
class Parallax {
    var x = 0f
    var y = 0f
}

class SceneOptions(val quality: SceneQuality, val motion: SceneMotion, val fps: Int)

/**
 * The live 3D backdrop. The scene is built off the main thread — shadows are baked then, which
 * takes a moment — and until it is ready the sky alone is drawn, so the window never opens blank.
 *
 * The canvas redraws from a frame clock, not from recomposition, capped at the player's frame
 * rate. [running] stops the clock outright — while the window is minimised or the game is up.
 * [night] follows the theme: the island turns to its night look over about a second.
 */
@Composable
fun VoxelBackground(parallax: Parallax, night: Boolean, options: SceneOptions, modifier: Modifier = Modifier, running: Boolean = true) {
    val renderer by produceState<VoxelRenderer?>(null) {
        value = withContext(Dispatchers.Default) { VoxelRenderer(islandScene()) }
    }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running, options.fps) {
        if (!running) return@LaunchedEffect
        val frame = (1000L / options.fps.coerceIn(15, 240)) - 1
        var last = 0L
        while (true) withFrameMillis { now -> if (now - last >= frame) { last = now; clock.longValue = now } }
    }
    Canvas(modifier) {
        val millis = clock.longValue
        val r = renderer
        if (r == null) {
            drawRect(Brush.verticalGradient(if (night) NightFallback else DayFallback))
        } else {
            r.night = if (night) 1f else 0f
            r.options = options
            r.draw(this, millis, parallax)
        }
    }
}

private val DayFallback = listOf(Color(0xFF3F8CEB), Color(0xFF7FBBF8), Color(0xFFC4E1FF))
private val NightFallback = listOf(Color(0xFF04070D), Color(0xFF0B1628), Color(0xFF1A2B47))

/** Faces handed to Skia per `drawVertices` call; the arrays are fixed, so the tail is padded. */
private const val CHUNK = 512

/**
 * Projects the scene's faces and paints them far to near — the painter's algorithm, which is
 * enough for axis-aligned blocks of equal size.
 *
 * Faces go to Skia as triangles in a handful of `drawVertices` calls, lit per corner: the colour
 * interpolates across each face, which is what turns baked occlusion and shadow samples into
 * smooth lighting instead of flat squares. Light is worked out every frame from the stored
 * factors, so day and night can blend and the quality can change without rebuilding anything.
 */
internal class VoxelRenderer(private val scene: Scene) {
    /** Where the look is heading: 0 day, 1 night. Set by the composable; the renderer eases to it. */
    var night = 0f
    var options = SceneOptions(SceneQuality.Ultra, SceneMotion.Both, 60)

    private val faces = scene.faces
    private val screen = FloatArray(faces.size * 8)
    private val keys = LongArray(faces.size)
    private val argb = IntArray(faces.size * 4)
    private val positions = FloatArray(CHUNK * 12)
    private val colors = IntArray(CHUNK * 6)
    private val paint = Paint().apply { color = 0xFFFFFFFF.toInt() }
    private val rays = Path()

    private val petals = Array(scene.petals.count) { Petal(Rng(5 + it), scene.petals) }
    private val stars = Array(320) { Star(Rng(900 + it)) }

    private var origin = -1L
    private var last = 0L
    private var px = 0f
    private var py = 0f
    private var blend = -1f

    // Camera basis for the frame being drawn.
    private val cam = FloatArray(3)
    private val fwd = FloatArray(3)
    private val right = FloatArray(3)
    private val up = FloatArray(3)
    private var focal = 1f
    private var ox = 0f
    private var oy = 0f
    private var horizonY = 0f

    fun draw(scope: DrawScope, millis: Long, parallax: Parallax) = with(scope) {
        if (origin < 0) { origin = millis; last = millis }
        val t = (millis - origin) / 1000f
        val dt = (millis - last).coerceIn(0, 100) / 1000f
        last = millis
        px += (parallax.x - px) * 0.06f
        py += (parallax.y - py) * 0.06f
        // The first frame takes the theme as it is; after that a change eases in over ~a second.
        blend = if (blend < 0f) night else blend + (night - blend) * (1 - exp(-dt * 3.2f))
        if (kotlin.math.abs(night - blend) < 0.002f) blend = night

        val c = scene.camera
        val w = size.width
        val h = size.height
        val (yaw, pitch) = when (options.motion) {
            SceneMotion.Rotate -> c.yaw + t * c.yawSpeed to c.pitch
            SceneMotion.Mouse -> c.yaw + px * 0.8f to c.pitch + py * 0.16f
            SceneMotion.Both -> c.yaw + t * c.yawSpeed + px * c.parallax to c.pitch + py * c.parallax * 0.4f
        }
        cam[0] = c.target[0] + c.distance * cos(pitch) * sin(yaw)
        cam[1] = c.target[1] + c.distance * sin(pitch)
        cam[2] = c.target[2] + c.distance * cos(pitch) * cos(yaw)
        setNormalized(fwd, c.target[0] - cam[0], c.target[1] - cam[1], c.target[2] - cam[2])
        // right = fwd × up(0,1,0), up = right × fwd
        setNormalized(right, -fwd[2], 0f, fwd[0])
        up[0] = right[1] * fwd[2] - right[2] * fwd[1]
        up[1] = right[2] * fwd[0] - right[0] * fwd[2]
        up[2] = right[0] * fwd[1] - right[1] * fwd[0]
        focal = (h / 2) / tan((c.fov * PI / 360).toFloat())
        ox = w * (0.5f + c.offsetX)
        oy = h * 0.5f
        horizonY = direction(fwd[0], 0f, fwd[2])?.y ?: (h / 2)

        val quality = options.quality
        drawSky(h)
        drawStars(t, h)
        drawLights(t, h, quality)
        // Behind the island, over the sky and the far clouds: haze in front of the island reads as a stripe across it.
        if (quality == SceneQuality.Ultra) drawHaze(h)
        drawFaces(t, w, h, quality)
        drawPetals(t, dt)
    }

    private fun DrawScope.drawSky(h: Float) {
        val hf = (horizonY / h).coerceIn(0.08f, 1f)
        val day = scene.day.sky
        val night = scene.night.sky
        val sky = List(3) { lerp(day[it], night[it], blend) }
        drawRect(Brush.verticalGradient(0f to sky[0], hf * 0.55f to sky[1], hf to sky[2], 1f to sky[2], startY = 0f, endY = h))
    }

    private fun DrawScope.drawStars(t: Float, h: Float) {
        if (blend <= 0.01f) return
        val size = max(1f, h / 700f)
        for (s in stars) {
            val at = direction(s.x, s.y, s.z) ?: continue
            val twinkle = 0.55f + 0.45f * sin(t * 1.3f + s.phase)
            drawRect(Color.White, Offset(at.x, at.y), Size(size * s.scale, size * s.scale), alpha = blend * twinkle * (0.3f + 0.6f * s.y))
        }
    }

    /** The sun by day and the moon by night — each where its light comes from — with a halo and, on Ultra, light shafts. */
    private fun DrawScope.drawLights(t: Float, h: Float, quality: SceneQuality) {
        val glow = quality != SceneQuality.Low
        val sunAlpha = 1 - blend
        if (sunAlpha > 0.01f) direction(scene.sun[0], scene.sun[1], scene.sun[2])?.let { at ->
            val s = 0.07f * h
            if (quality == SceneQuality.Ultra) shafts(at, h, t, Color(0xFFFFF4D6), 0.16f * sunAlpha)
            if (glow) halo(at, s * 6, Color(0xFFFFF6DC), 0.6f * sunAlpha)
            drawRect(Color(0xFFFFFBE8), Offset(at.x - s / 2, at.y - s / 2), Size(s, s), alpha = sunAlpha)
            drawRect(Color.White, Offset(at.x - s * 0.3f, at.y - s * 0.3f), Size(s * 0.6f, s * 0.6f), alpha = sunAlpha)
        }
        if (blend > 0.01f) direction(scene.moon[0], scene.moon[1], scene.moon[2])?.let { at ->
            val m = 0.06f * h
            if (quality == SceneQuality.Ultra) shafts(at, h, t, Color(0xFFA8C7FF), 0.07f * blend)
            if (glow) halo(at, m * 5, Color(0xFFA8C7FF), 0.35f * blend)
            drawRect(Color(0xFFE6ECF8), Offset(at.x - m / 2, at.y - m / 2), Size(m, m), alpha = blend)
            // Two craters are enough to make a square read as the Minecraft moon.
            drawRect(Color(0xFFC3CBDD), Offset(at.x - m * 0.3f, at.y - m * 0.22f), Size(m * 0.22f, m * 0.22f), alpha = blend)
            drawRect(Color(0xFFC3CBDD), Offset(at.x + m * 0.08f, at.y + m * 0.1f), Size(m * 0.26f, m * 0.2f), alpha = blend)
        }
    }

    private fun DrawScope.halo(at: Offset, radius: Float, color: Color, alpha: Float) {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.25f), color.copy(alpha = 0f)), center = at, radius = radius),
            radius = radius, center = at, blendMode = BlendMode.Plus,
        )
    }

    /** Slowly turning wedges of light fanning out from [at]; they fade to nothing well before the screen edge. */
    private fun DrawScope.shafts(at: Offset, h: Float, t: Float, color: Color, alpha: Float) {
        val length = h * 1.1f
        val brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = at, radius = length)
        for (i in 0 until 12) {
            val angle = i * (2 * PI / 12).toFloat() + t * 0.02f + sin(t * 0.3f + i * 1.7f) * 0.05f
            val spread = 0.035f + 0.025f * (0.5f + 0.5f * sin(t * 0.5f + i * 2.3f))
            rays.reset()
            rays.moveTo(at.x, at.y)
            rays.lineTo(at.x + cos(angle - spread) * length, at.y + sin(angle - spread) * length)
            rays.lineTo(at.x + cos(angle + spread) * length, at.y + sin(angle + spread) * length)
            rays.close()
            drawPath(rays, brush, blendMode = BlendMode.Plus)
        }
    }

    /** Ultra: a band of the fog colour lying on the horizon, so the sea of clouds fades into distance. */
    private fun DrawScope.drawHaze(h: Float) {
        val fog = lerp(Color(0xFFC4E1FF), Color(0xFF1A2B47), blend)
        drawRect(
            Brush.verticalGradient(
                0f to fog.copy(alpha = 0f),
                0.6f to fog.copy(alpha = 0.32f),
                1f to fog.copy(alpha = 0.12f),
                startY = horizonY - h * 0.25f,
                endY = horizonY + h * 0.35f,
            ),
            topLeft = Offset(0f, horizonY - h * 0.25f),
            size = Size(size.width, h * 0.6f),
        )
    }

    private fun DrawScope.drawFaces(t: Float, w: Float, h: Float, quality: SceneQuality) {
        val smooth = quality != SceneQuality.Low
        val shimmer = quality == SceneQuality.Ultra
        val dayLook = scene.day
        val nightLook = scene.night
        val dl = dayLook.light
        val nl = nightLook.light
        val n = blend
        val d = 1 - n
        val fogStart = dayLook.fog.start * d + nightLook.fog.start * n
        val fogSpan = (dayLook.fog.end * d + nightLook.fog.end * n) - fogStart
        val fogMax = dayLook.fog.max * d + nightLook.fog.max * n
        val fogR = dayLook.fog.color[0] * d + nightLook.fog.color[0] * n
        val fogG = dayLook.fog.color[1] * d + nightLook.fog.color[1] * n
        val fogB = dayLook.fog.color[2] * d + nightLook.fog.color[2] * n
        val drift = scene.drift
        val shift = if (drift != null) t * drift.speed else 0f
        var count = 0
        for (i in faces.indices) {
            val f = faces[i]
            var dx = 0f
            if (drift != null && f.drifts) {
                val moved = f.driftBase + shift
                dx = ((moved - drift.min) % drift.width + drift.width) % drift.width + drift.min - f.driftBase
            }
            val cx = f.cx + dx
            val nx = f.normal[0]
            val ny = f.normal[1]
            val nz = f.normal[2]
            if ((cam[0] - cx) * nx + (cam[1] - f.cy) * ny + (cam[2] - f.cz) * nz <= 0f) continue
            val o = i * 8
            val p = f.corners
            if (!project(p[0] + dx, p[1], p[2], o) || !project(p[3] + dx, p[4], p[5], o + 2) ||
                !project(p[6] + dx, p[7], p[8], o + 4) || !project(p[9] + dx, p[10], p[11], o + 6)
            ) continue
            val minX = min(min(screen[o], screen[o + 2]), min(screen[o + 4], screen[o + 6]))
            val maxX = max(max(screen[o], screen[o + 2]), max(screen[o + 4], screen[o + 6]))
            val minY = min(min(screen[o + 1], screen[o + 3]), min(screen[o + 5], screen[o + 7]))
            val maxY = max(max(screen[o + 1], screen[o + 3]), max(screen[o + 5], screen[o + 7]))
            if (maxX < 0 || minX > w || maxY < 0 || minY > h) continue

            val ex = cx - cam[0]
            val ey = f.cy - cam[1]
            val ez = f.cz - cam[2]
            val dist = sqrt(ex * ex + ey * ey + ez * ez)
            val haze = ((dist - fogStart) / fogSpan).coerceIn(0f, 1f) * fogMax * (1 - 0.7f * f.emit)
            val clear = 1 - haze

            // Light from each look, per corner: ambient darkened by occlusion, direct light cut by shadow.
            val underside = if (ny < -0.5f) 0.72f else 1f
            val sunFacing = max(0f, nx * dl.direction[0] + ny * dl.direction[1] + nz * dl.direction[2])
            val moonFacing = max(0f, nx * nl.direction[0] + ny * nl.direction[1] + nz * nl.direction[2])
            val dayEmit = f.emit * dayLook.cloudGlow
            val nightEmit = f.emit * nightLook.cloudGlow
            val ripple = if (shimmer && f.water) 0.07f * sin(t * 1.8f + cx * 0.9f + f.cz * 0.6f) else 0f
            for (k in 0 until 4) {
                val ao = if (smooth) f.ao[k] else 1f
                val sunLit = if (smooth) f.sun[k] else 1f
                val moonLit = if (smooth) f.moon[k] else 1f
                val vd = (dl.ambient * ao + dl.diffuse * sunFacing * sunLit) * f.shade * underside
                val vn = (nl.ambient * ao + nl.diffuse * moonFacing * moonLit) * f.shade * underside
                val gain = 1 + ripple * (if (k % 2 == 0) 1f else -1f)
                var r = 0f
                var g = 0f
                var b = 0f
                for (ch in 0 until 3) {
                    val dayC = f.base[ch] * (vd * dl.tint[ch] * (1 - dayEmit) + 1.12f * dayEmit)
                    val nightC = f.base[ch] * (vn * nl.tint[ch] * (1 - nightEmit) + 1.12f * nightEmit)
                    val lit = (dayC * d + nightC * n) * gain
                    when (ch) {
                        0 -> r = lit * clear + fogR * haze
                        1 -> g = lit * clear + fogG * haze
                        else -> b = lit * clear + fogB * haze
                    }
                }
                argb[i * 4 + k] = (channel(f.alpha * 255) shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            }
            keys[count++] = ((Int.MAX_VALUE - dist.toRawBits()).toLong() shl 32) or i.toLong()
        }
        java.util.Arrays.sort(keys, 0, count)

        val canvas = drawContext.canvas.nativeCanvas
        var filled = 0
        for (k in 0 until count) {
            val i = (keys[k] and 0xFFFFFFFFL).toInt()
            val f = faces[i]
            // Split the quad along the diagonal that keeps the occlusion gradient symmetric — the
            // same flip Minecraft does, without it dark corners smear into a stripe.
            val flip = smooth && f.ao[0] + f.ao[2] < f.ao[1] + f.ao[3]
            val order = if (flip) FLIPPED else STRAIGHT
            val v = filled * 12
            val cv = filled * 6
            for (j in 0 until 6) {
                val corner = order[j]
                positions[v + j * 2] = screen[i * 8 + corner * 2]
                positions[v + j * 2 + 1] = screen[i * 8 + corner * 2 + 1]
                colors[cv + j] = argb[i * 4 + corner]
            }
            if (++filled == CHUNK) {
                canvas.drawVertices(VertexMode.TRIANGLES, positions, colors, null, null, SkiaBlendMode.MODULATE, paint)
                filled = 0
            }
        }
        if (filled > 0) {
            // Zero-area triangles for the unused tail: the arrays cannot be passed with a length.
            positions.fill(0f, filled * 12)
            colors.fill(0, filled * 6)
            canvas.drawVertices(VertexMode.TRIANGLES, positions, colors, null, null, SkiaBlendMode.MODULATE, paint)
        }
    }

    private fun DrawScope.drawPetals(t: Float, dt: Float) {
        val box = scene.petals.box
        val light = scene.day.petalLight * (1 - blend) + scene.night.petalLight * blend
        for (p in petals) {
            p.x += (p.vx + sin(t * 1.4f + p.phase) * 0.5f) * dt
            p.y += p.vy * dt
            p.z += (p.vz + cos(t * 1.1f + p.phase) * 0.3f) * dt
            if (p.x < box[0]) p.x = box[3] else if (p.x > box[3]) p.x = box[0]
            if (p.y < box[1]) p.y = box[4] else if (p.y > box[4]) p.y = box[1]
            if (p.z < box[2]) p.z = box[5] else if (p.z > box[5]) p.z = box[2]
            val ex = p.x - cam[0]
            val ey = p.y - cam[1]
            val ez = p.z - cam[2]
            val z = ex * fwd[0] + ey * fwd[1] + ez * fwd[2]
            if (z < 0.5f) continue
            val sx = ox + (ex * right[0] + ey * right[1] + ez * right[2]) / z * focal
            val sy = oy - (ex * up[0] + ey * up[1] + ez * up[2]) / z * focal
            val s = max(1f, p.size * focal / z)
            drawRect(lerp(Color(0xFF1A2B47), p.color, light), topLeft = Offset(sx - s / 2, sy - s / 2), size = Size(s, s), alpha = 0.95f)
        }
    }

    /** Projects a world point into [screen] at [o]; false when it is behind the near plane. */
    private fun project(x: Float, y: Float, z: Float, o: Int): Boolean {
        val ex = x - cam[0]
        val ey = y - cam[1]
        val ez = z - cam[2]
        val d = ex * fwd[0] + ey * fwd[1] + ez * fwd[2]
        if (d < 0.2f) return false
        screen[o] = ox + (ex * right[0] + ey * right[1] + ez * right[2]) / d * focal
        screen[o + 1] = oy - (ex * up[0] + ey * up[1] + ez * up[2]) / d * focal
        return true
    }

    /** A direction at infinity on screen — the sun, the horizon, a star — or null when it is behind us. */
    private fun direction(x: Float, y: Float, z: Float): Offset? {
        val d = x * fwd[0] + y * fwd[1] + z * fwd[2]
        if (d < 0.05f) return null
        return Offset(ox + (x * right[0] + y * right[1] + z * right[2]) / d * focal, oy - (x * up[0] + y * up[1] + z * up[2]) / d * focal)
    }

    private companion object {
        val STRAIGHT = intArrayOf(0, 1, 2, 0, 2, 3)
        val FLIPPED = intArrayOf(1, 2, 3, 1, 3, 0)
    }
}

private class Petal(rng: Rng, spec: Petals) {
    var x = spec.box[0] + rng.next() * (spec.box[3] - spec.box[0])
    var y = spec.box[1] + rng.next() * (spec.box[4] - spec.box[1])
    var z = spec.box[2] + rng.next() * (spec.box[5] - spec.box[2])
    val vx = (rng.next() - 0.5f) * 0.2f
    val vy = (rng.next() - 0.5f) * 0.08f - 0.55f
    val vz = (rng.next() - 0.5f) * 0.2f
    val phase = rng.next() * 6.28f
    val color = spec.colors[(rng.next() * spec.colors.size).toInt().coerceAtMost(spec.colors.size - 1)]
    val size = spec.size * (0.6f + rng.next() * 0.8f)
}

/** A point on the upper sky dome. */
private class Star(rng: Rng) {
    val y = 0.05f + rng.next() * 0.95f
    private val a = rng.next() * 6.2832f
    private val r = sqrt(1 - y * y)
    val x = cos(a) * r
    val z = sin(a) * r
    val phase = rng.next() * 6.28f
    val scale = 0.6f + rng.next() * 1.2f
}

private fun setNormalized(out: FloatArray, x: Float, y: Float, z: Float) {
    val l = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: 1f
    out[0] = x / l; out[1] = y / l; out[2] = z / l
}

private fun channel(v: Float): Int = v.toInt().coerceIn(0, 255)

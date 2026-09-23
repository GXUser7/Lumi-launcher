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
import androidx.compose.ui.graphics.skiaCanvas
import com.yumi.launcher.settings.SceneMotion
import com.yumi.launcher.settings.SceneQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
 * The live 3D backdrop. The scene is built off the main thread — textures painted, light and
 * shadows baked, which takes a moment — and until it is ready the sky alone is drawn, so the window
 * never opens blank.
 *
 * The canvas redraws from a frame clock, not from recomposition, capped at the player's frame
 * rate. [running] stops the clock outright — while the window is minimised or the game is up.
 * [night] follows the theme: the island turns to its night look over about a second. [spot] is the
 * place the camera flies to: the whole island, or the part of it the open section is about.
 */
@Composable
fun VoxelBackground(
    parallax: Parallax,
    night: Boolean,
    options: SceneOptions,
    spot: SceneSpot,
    modifier: Modifier = Modifier,
    running: Boolean = true,
) {
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
            r.spot = spot
            r.draw(this, millis, parallax)
        }
    }
}

private val DayFallback = listOf(Color(0xFF3F8CEB), Color(0xFF7FBBF8), Color(0xFFC4E1FF))
private val NightFallback = listOf(Color(0xFF04070D), Color(0xFF0B1628), Color(0xFF1A2B47))

/** Triangles handed to Skia per `drawVertices` call. */
private const val BATCH = 3072

/** How far in front of the eye geometry is cut, in blocks. */
private const val NEAR = 0.15f

/**
 * Projects the scene and paints it far to near.
 *
 * The island is a grid of unit cells, and for a grid there is an exact far-to-near order from any
 * eye: by the number of cell steps between a block and the eye's own cell. A ray leaving the eye
 * only ever steps away from it, so anything in front along a ray is fewer steps away than what is
 * behind it — which is what lets the camera fly in among the buildings without faces drawing in
 * the wrong order. Faces go to Skia as textured triangles, lit per corner: the light interpolates
 * across each face, which turns the baked occlusion, sky, torch and shadow samples into smooth
 * lighting instead of flat squares.
 */
internal class VoxelRenderer(private val scene: Scene) {
    /** Where the look is heading: 0 day, 1 night. Set by the composable; the renderer eases to it. */
    var night = 0f
    var options = SceneOptions(SceneQuality.Ultra, SceneMotion.Both, 60)
    var spot = SceneSpot.Overview

    private val world = scene.world
    private val faces = scene.faces
    private val textured = Paint().apply {
        shader = scene.atlas.image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE), null)
    }
    private val flatPaint = Paint().apply { color = 0xFFFFFFFF.toInt() }
    private val additive = Paint().apply { color = 0xFFFFFFFF.toInt(); blendMode = SkiaBlendMode.PLUS }

    // Per-face scratch: screen corners, camera depths, corner colours, sort keys.
    private val maxFaces = max(faces.size, max(scene.clouds.size, scene.far.size))
    private val sx = FloatArray(maxFaces * 4)
    private val sy = FloatArray(maxFaces * 4)
    private val depth = FloatArray(maxFaces * 4)
    private val argb = IntArray(maxFaces * 4)
    private val keys = LongArray(maxFaces)

    // The batch being filled.
    private val positions = FloatArray(BATCH * 6)
    private val colors = IntArray(BATCH * 3)
    private val texCoords = FloatArray(BATCH * 6)
    private var triangles = 0

    private val petals = Array(scene.petals.count) { Petal(Rng(5 + it), scene.petals) }
    private val stars = Array(640) { Star(Rng(900 + it)) }
    private val particles = Particles()
    private val fireflies = Array(46) { Firefly(Rng(3000 + it)) }
    private val rays = Path()

    private var origin = -1L
    private var last = 0L
    private var px = 0f
    private var py = 0f
    private var blend = -1f
    private var now = 0f

    // The camera: the pose it flies from, the one it flies to, and how far along it is.
    private var from: Pose? = null
    private var to: Pose? = null
    private var flight = 1f
    private var heading: SceneSpot? = null
    private var orbitYaw = 0f
    private val pose = Pose()

    // Camera basis for the frame being drawn.
    private val cam = FloatArray(3)
    private val fwd = FloatArray(3)
    private val right = FloatArray(3)
    private val up = FloatArray(3)
    private var focal = 1f
    private var ox = 0f
    private var oy = 0f
    private var horizonY = 0f

    /** Flicker of each light source this frame, by its index in the world. */
    private val flicker = FloatArray(world.lights.size)

    fun draw(scope: DrawScope, millis: Long, parallax: Parallax) = with(scope) {
        if (origin < 0) { origin = millis; last = millis }
        val t = (millis - origin) / 1000f
        now = t
        val dt = (millis - last).coerceIn(0, 100) / 1000f
        last = millis
        px += (parallax.x - px) * 0.06f
        py += (parallax.y - py) * 0.06f
        // The first frame takes the theme as it is; after that a change eases in over ~a second.
        blend = if (blend < 0f) night else blend + (night - blend) * (1 - exp(-dt * 3.2f))
        if (abs(night - blend) < 0.002f) blend = night

        val w = size.width
        val h = size.height
        camera(t, dt, w, h)
        for (i in flicker.indices) {
            val l = world.lights[i]
            flicker[i] = if (l.flickers) 0.86f + 0.08f * sin(t * 9.1f + l.phase) + 0.06f * sin(t * 17.3f + l.phase * 1.7f) else 1f
        }

        val quality = options.quality
        drawSky(h)
        drawStars(t, h, quality)
        if (quality == SceneQuality.Ultra) drawAurora(t)
        drawLights(t, h, quality)
        if (quality >= SceneQuality.High) drawFar(t)
        // Behind the island, over the sky and the far clouds: haze in front of the island reads as a stripe across it.
        if (quality == SceneQuality.Ultra) drawHaze(h)
        drawClouds(t, w, h, quality)
        drawIsland(t, w, h, quality)
        if (quality == SceneQuality.Ultra) drawGlints(t)
        if (quality >= SceneQuality.High) drawGlows(t, quality)
        if (quality >= SceneQuality.Medium) drawParticles(t, dt, quality)
        if (quality == SceneQuality.Ultra) drawBirds(t)
    }

    // ---------- camera ----------

    private class Pose {
        val target = FloatArray(3)
        var distance = 0f
        var yaw = 0f
        var pitch = 0f
        var fov = 48f
        var offsetX = 0.2f

        fun set(o: Pose) {
            o.target.copyInto(target)
            distance = o.distance; yaw = o.yaw; pitch = o.pitch; fov = o.fov; offsetX = o.offsetX
        }

        fun copy() = Pose().also { it.set(this) }
    }

    private fun poseOf(spot: SceneSpot): Pose {
        val c = scene.spots.getValue(spot)
        return Pose().apply {
            c.target.copyInto(target)
            distance = c.distance
            yaw = c.yaw + if (c.orbit) orbitYaw else 0f
            pitch = c.pitch
            fov = c.fov
            offsetX = c.offsetX
        }
    }

    /**
     * Works out this frame's camera. Changing the spot starts a flight from wherever the camera is
     * to the new place: target, angles and distance ease together, and half-way the camera pulls
     * back and up, so it arcs over the island instead of cutting through the trees.
     */
    private fun camera(t: Float, dt: Float, w: Float, h: Float) {
        val target = spot
        val config = scene.spots.getValue(target)
        val motion = options.motion
        if (config.orbit && flight >= 1f && motion != SceneMotion.Mouse) orbitYaw += dt * 0.05f
        if (heading != target) {
            val first = heading == null
            from = if (first) poseOf(target) else pose.copy()
            heading = target
            flight = if (first) 1f else 0f
        }
        to = poseOf(target)
        val start = from ?: to!!
        val end = to!!
        if (flight < 1f) flight = min(1f, flight + dt / 1.9f)
        val e = flight * flight * flight * (flight * (flight * 6 - 15) + 10)
        val arc = sin(PI.toFloat() * e)
        for (i in 0..2) pose.target[i] = start.target[i] + (end.target[i] - start.target[i]) * e
        var dy = end.yaw - start.yaw
        dy = ((dy + PI.toFloat()) % (2 * PI.toFloat()) + 2 * PI.toFloat()) % (2 * PI.toFloat()) - PI.toFloat()
        pose.yaw = start.yaw + dy * e
        val travel = sqrt((0..2).sumOf { ((end.target[it] - start.target[it]) * (end.target[it] - start.target[it])).toDouble() }).toFloat()
        pose.distance = start.distance + (end.distance - start.distance) * e + arc * min(14f, travel * 0.45f)
        pose.pitch = start.pitch + (end.pitch - start.pitch) * e + arc * 0.16f
        pose.fov = start.fov + (end.fov - start.fov) * e
        pose.offsetX = start.offsetX + (end.offsetX - start.offsetX) * e

        // On top of the pose: a slow breath for the places, the pointer for all of them.
        var yaw = pose.yaw
        var pitch = pose.pitch
        val parallaxAmount = if (config.orbit) 0.12f else 0.07f
        if (!config.orbit && motion != SceneMotion.Mouse) {
            yaw += sin(t * 0.21f) * 0.035f
            pitch += sin(t * 0.27f) * 0.012f
        }
        when (motion) {
            SceneMotion.Rotate -> Unit
            SceneMotion.Mouse -> { yaw += px * (if (config.orbit) 0.8f else 0.25f); pitch += py * (if (config.orbit) 0.16f else 0.06f) }
            SceneMotion.Both -> { yaw += px * parallaxAmount; pitch += py * parallaxAmount * 0.4f }
        }
        val tgt = pose.target
        cam[0] = tgt[0] + pose.distance * cos(pitch) * sin(yaw)
        cam[1] = tgt[1] + pose.distance * sin(pitch)
        cam[2] = tgt[2] + pose.distance * cos(pitch) * cos(yaw)
        setNormalized(fwd, tgt[0] - cam[0], tgt[1] - cam[1], tgt[2] - cam[2])
        // right = fwd × up(0,1,0), up = right × fwd
        setNormalized(right, -fwd[2], 0f, fwd[0])
        up[0] = right[1] * fwd[2] - right[2] * fwd[1]
        up[1] = right[2] * fwd[0] - right[0] * fwd[2]
        up[2] = right[0] * fwd[1] - right[1] * fwd[0]
        focal = (h / 2) / tan((pose.fov * PI / 360).toFloat())
        ox = w * (0.5f + pose.offsetX)
        oy = h * 0.5f
        horizonY = direction(fwd[0], 0f, fwd[2])?.y ?: (h / 2)
    }

    // ---------- sky ----------

    private fun DrawScope.drawSky(h: Float) {
        val hf = (horizonY / h).coerceIn(0.08f, 1f)
        val day = scene.day.sky
        val night = scene.night.sky
        val sky = List(3) { lerp(day[it], night[it], blend) }
        drawRect(Brush.verticalGradient(0f to sky[0], hf * 0.55f to sky[1], hf to sky[2], 1f to sky[2], startY = 0f, endY = h))
    }

    private fun DrawScope.drawStars(t: Float, h: Float, quality: SceneQuality) {
        if (blend <= 0.01f) return
        val size = max(1f, h / 700f)
        val count = when (quality) {
            SceneQuality.Low -> 120
            SceneQuality.Medium -> 260
            SceneQuality.High -> 360
            SceneQuality.Ultra -> stars.size
        }
        for (i in 0 until count) {
            val s = stars[i]
            val at = direction(s.x, s.y, s.z) ?: continue
            val twinkle = if (quality >= SceneQuality.High) 0.55f + 0.45f * sin(t * 1.3f + s.phase) else 0.8f
            drawRect(Color.White, Offset(at.x, at.y), Size(size * s.scale, size * s.scale), alpha = blend * twinkle * (0.3f + 0.6f * s.y))
        }
        // Now and then a shooting star, on Ultra.
        if (quality == SceneQuality.Ultra) {
            val period = 7f
            val k = floor(t / period).toInt()
            val phase = (t - k * period) / 1.1f
            if (phase < 1f) {
                val a = hash(k, 1, 91) * 6.28f
                val y0 = 0.45f + hash(k, 2, 91) * 0.4f
                val dir0 = normalize(cos(a) * sqrt(1 - y0 * y0), y0, sin(a) * sqrt(1 - y0 * y0))
                val dir1 = normalize(dir0[0] + cos(a + 1.6f) * 0.25f, dir0[1] - 0.12f, dir0[2] + sin(a + 1.6f) * 0.25f)
                val p0 = direction(dir0[0], dir0[1], dir0[2])
                val p1 = direction(dir1[0], dir1[1], dir1[2])
                if (p0 != null && p1 != null) {
                    val head = Offset(p0.x + (p1.x - p0.x) * phase, p0.y + (p1.y - p0.y) * phase)
                    val tail = Offset(p0.x + (p1.x - p0.x) * max(0f, phase - 0.35f), p0.y + (p1.y - p0.y) * max(0f, phase - 0.35f))
                    val fade = blend * sin(phase * PI.toFloat())
                    drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.9f * fade)), tail, head), tail, head, strokeWidth = max(1.5f, h / 500f))
                }
            }
        }
    }

    /**
     * Ultra, at night: curtains of light over the sky, green at the foot fading to violet, slowly
     * folding. Each curtain is a strip of narrow columns lit per vertex, so its rays shimmer smoothly
     * rather than stepping from one flat panel to the next, and it fades out at both ends.
     */
    private fun DrawScope.drawAurora(t: Float) {
        if (blend <= 0.05f) return
        val canvas = drawContext.canvas.skiaCanvas
        val rows = AURORA_ROWS.size
        for ((band, curtain) in AURORA.withIndex()) {
            var n = 0
            var previous = false
            for (i in 0..AURORA_COLUMNS) {
                val s = i / AURORA_COLUMNS.toFloat()
                val a = curtain[0] + curtain[1] * s
                val foot = curtain[2] + sin(a * 3.1f + t * 0.35f + band * 1.7f) * 0.025f + sin(a * 9.3f - t * 0.6f) * 0.008f
                val height = 0.13f + sin(a * 5f + t * 0.5f + band) * 0.04f
                // Rays: a quick ripple over a slow swell, both drifting along the curtain.
                val rays = (0.55f + 0.45f * sin(a * 41f + t * 1.3f + band)) * (0.6f + 0.4f * sin(a * 11f - t * 0.45f + band * 2f))
                val strength = rays * smoothstep(0f, 0.22f, s) * smoothstep(1f, 0.78f, s) * blend * curtain[3]
                val column = columns[i % 2]
                var ok = true
                for (row in 0 until rows) {
                    val at = direction(cos(a), foot + height * AURORA_ROWS[row], sin(a))
                    if (at == null) { ok = false; break }
                    column.x[row] = at.x
                    column.y[row] = at.y
                    column.c[row] = argbOf(AURORA_ALPHA[row] * strength, AURORA_TINT[row * 3], AURORA_TINT[row * 3 + 1], AURORA_TINT[row * 3 + 2])
                }
                if (ok && previous) {
                    val last = columns[(i + 1) % 2]
                    for (row in 0 until rows - 1) {
                        n = auroraVertex(n, last, row); n = auroraVertex(n, column, row); n = auroraVertex(n, column, row + 1)
                        n = auroraVertex(n, last, row); n = auroraVertex(n, column, row + 1); n = auroraVertex(n, last, row + 1)
                    }
                }
                previous = ok
            }
            if (n == 0) continue
            auroraPos.fill(0f, n * 2)
            auroraCol.fill(0, n)
            canvas.drawVertices(VertexMode.TRIANGLES, auroraPos, auroraCol, null, null, SkiaBlendMode.MODULATE, additive)
        }
    }

    /** One column of a curtain on screen: a point and a colour per row. The current and the last are kept. */
    private class AuroraColumn(rows: Int) {
        val x = FloatArray(rows)
        val y = FloatArray(rows)
        val c = IntArray(rows)
    }

    private val columns = Array(2) { AuroraColumn(AURORA_ROWS.size) }
    private val auroraPos = FloatArray(AURORA_COLUMNS * (AURORA_ROWS.size - 1) * 12)
    private val auroraCol = IntArray(AURORA_COLUMNS * (AURORA_ROWS.size - 1) * 6)

    private fun auroraVertex(n: Int, column: AuroraColumn, row: Int): Int {
        auroraPos[n * 2] = column.x[row]
        auroraPos[n * 2 + 1] = column.y[row]
        auroraCol[n] = column.c[row]
        return n + 1
    }

    /** The sun by day and the moon by night — each where its light comes from — with a halo and, on Ultra, light shafts. */
    private fun DrawScope.drawLights(t: Float, h: Float, quality: SceneQuality) {
        val glow = quality >= SceneQuality.Medium
        val sunAlpha = 1 - blend
        // The discs sit low over the horizon, at the light's own bearing: at the light's own height
        // they would be above the top of the frame, since the camera looks down on the island.
        val sun = normalize(scene.sun[0], 0.1f, scene.sun[2])
        val moon = normalize(scene.moon[0], 0.07f, scene.moon[2])
        if (sunAlpha > 0.01f) direction(sun[0], sun[1], sun[2])?.let { at ->
            val s = 0.07f * h
            if (quality == SceneQuality.Ultra) shafts(at, h, t, Color(0xFFFFF4D6), 0.16f * sunAlpha)
            if (glow) halo(at, s * 6, Color(0xFFFFF6DC), 0.6f * sunAlpha)
            drawRect(Color(0xFFFFFBE8), Offset(at.x - s / 2, at.y - s / 2), Size(s, s), alpha = sunAlpha)
            drawRect(Color.White, Offset(at.x - s * 0.3f, at.y - s * 0.3f), Size(s * 0.6f, s * 0.6f), alpha = sunAlpha)
        }
        if (blend > 0.01f) direction(moon[0], moon[1], moon[2])?.let { at ->
            val m = 0.06f * h
            if (quality == SceneQuality.Ultra) shafts(at, h, t, Color(0xFFA8C7FF), 0.05f * blend)
            if (glow) halo(at, m * 5, Color(0xFFA8C7FF), 0.35f * blend)
            drawRect(Color(0xFFE6ECF8), Offset(at.x - m / 2, at.y - m / 2), Size(m, m), alpha = blend)
            // Two craters are enough to make a square read as the Minecraft moon.
            drawRect(Color(0xFFC3CBDD), Offset(at.x - m * 0.3f, at.y - m * 0.22f), Size(m * 0.22f, m * 0.22f), alpha = blend)
            drawRect(Color(0xFFC3CBDD), Offset(at.x + m * 0.08f, at.y + m * 0.1f), Size(m * 0.26f, m * 0.2f), alpha = blend)
        }
    }

    private fun DrawScope.halo(at: Offset, radius: Float, color: Color, alpha: Float) {
        if (radius <= 0.5f) return
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

    // ---------- flat layers: far islands and clouds ----------

    private fun fogAt(dist: Float, emitShare: Float): Float {
        val d = scene.day.fog
        val n = scene.night.fog
        val start = d.start + (n.start - d.start) * blend
        val end = d.end + (n.end - d.end) * blend
        val most = d.max + (n.max - d.max) * blend
        return ((dist - start) / (end - start)).coerceIn(0f, 1f) * most * (1 - 0.7f * emitShare)
    }

    private fun fogColor(ch: Int): Float {
        val d = scene.day.fog.color[ch]
        val n = scene.night.fog.color[ch]
        return d + (n - d) * blend
    }

    /** High and up: the islands out in the haze, bobbing slowly. */
    private fun DrawScope.drawFar(t: Float) {
        val list = scene.far
        var count = 0
        for (island in scene.islands) {
            val bob = sin(t * 0.25f + island.phase) * 0.8f
            for (i in island.from until island.to) {
                val f = list[i]
                if (!facing(f, 0f, bob)) continue
                if (!projectFace(f, i, 0f, bob)) continue
                val dist = distance(f.cx, f.cy + bob, f.cz)
                flatColors(f, i, dist, cloud = false)
                keys[count++] = ((Int.MAX_VALUE - dist.toRawBits()).toLong() shl 32) or i.toLong()
            }
        }
        emitSorted(list, count, textures = false)
    }

    private fun DrawScope.drawClouds(t: Float, w: Float, h: Float, quality: SceneQuality) {
        val list = scene.clouds
        val drift = scene.drift
        val shift = t * drift.speed
        var count = 0
        for (i in list.indices) {
            val f = list[i]
            // Low keeps only the sea of clouds under the island.
            if (quality == SceneQuality.Low && f.cy > 0f) continue
            val moved = f.driftBase + shift
            val dx = ((moved - drift.min) % drift.width + drift.width) % drift.width + drift.min - f.driftBase
            if (!facing(f, dx, 0f)) continue
            if (!projectFace(f, i, dx, 0f)) continue
            if (offscreen(i, w, h)) continue
            val dist = distance(f.cx + dx, f.cy, f.cz)
            flatColors(f, i, dist, cloud = true)
            keys[count++] = ((Int.MAX_VALUE - dist.toRawBits()).toLong() shl 32) or i.toLong()
        }
        emitSorted(list, count, textures = false)
    }

    private fun flatColors(f: Face, i: Int, dist: Float, cloud: Boolean) {
        val day = scene.day
        val nightLook = scene.night
        val n = blend
        val d = 1 - n
        val dl = day.light
        val nl = nightLook.light
        val nx = f.normal[0]
        val ny = f.normal[1]
        val nz = f.normal[2]
        val sunFacing = max(0f, nx * dl.direction[0] + ny * dl.direction[1] + nz * dl.direction[2])
        val moonFacing = max(0f, nx * nl.direction[0] + ny * nl.direction[1] + nz * nl.direction[2])
        val emit = if (cloud) f.emit else 0f
        val haze = fogAt(dist, emit)
        val clear = 1 - haze
        val underside = if (ny < -0.5f) 0.72f else 1f
        val base = f.color
        for (k in 0 until 4) {
            val ao = f.ao[k]
            val vd = (dl.ambient * ao + dl.diffuse * sunFacing) * underside
            val vn = (nl.ambient * ao + nl.diffuse * moonFacing) * underside
            val dayEmit = emit * day.cloudGlow
            val nightEmit = emit * nightLook.cloudGlow
            var r = 0f
            var g = 0f
            var b = 0f
            for (ch in 0 until 3) {
                val c = ((base shr (16 - ch * 8)) and 0xFF).toFloat()
                val dayC = c * (vd * dl.tint[ch] * (1 - dayEmit) + 1.12f * dayEmit)
                val nightC = c * (vn * nl.tint[ch] * (1 - nightEmit) + 1.12f * nightEmit)
                val lit = (dayC * d + nightC * n) * clear + fogColor(ch) * haze
                when (ch) { 0 -> r = lit; 1 -> g = lit; else -> b = lit }
            }
            argb[i * 4 + k] = (channel(f.alpha * 255) shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
    }

    // ---------- the island ----------

    private fun DrawScope.drawIsland(t: Float, w: Float, h: Float, quality: SceneQuality) {
        val textures = quality >= SceneQuality.Medium
        val smooth = quality >= SceneQuality.Medium
        val shadows = quality >= SceneQuality.High
        val animated = quality >= SceneQuality.Medium
        val sway = quality == SceneQuality.Ultra
        val cloudy = quality == SceneQuality.Ultra && blend < 1f
        val day = scene.day
        val nightLook = scene.night
        val dl = day.light
        val nl = nightLook.light
        val n = blend
        val d = 1 - n
        val torches = (day.torches * d + nightLook.torches * n) * if (smooth) 1f else 0f
        val ex = floor(cam[0]).toInt()
        val ey = floor(cam[1]).toInt()
        val ez = floor(cam[2]).toInt()
        var count = 0
        for (i in faces.indices) {
            val f = faces[i]
            val wave = if (sway && (f.anim == Anim.Leaves || f.anim == Anim.Plant)) 1f else 0f
            val bob = if (f.anim == Anim.Bob) sin(t * 1.6f) * 0.08f + 0.05f else 0f
            if (!f.twoSided && !facing(f, 0f, bob)) continue
            if (!projectIsland(f, i, t, wave, bob)) continue
            if (offscreen(i, w, h)) continue

            // Light from each look, per corner: ambient scaled by the sky reaching the corner and its
            // occlusion, direct light cut by shadow, and the torches' own colour on top.
            val nx = f.normal[0]
            val ny = f.normal[1]
            val nz = f.normal[2]
            val sunFacing = if (f.twoSided) 0.35f else max(0f, nx * dl.direction[0] + ny * dl.direction[1] + nz * dl.direction[2])
            val moonFacing = if (f.twoSided) 0.35f else max(0f, nx * nl.direction[0] + ny * nl.direction[1] + nz * nl.direction[2])
            val under = ny < -0.5f
            val ripple = if (quality == SceneQuality.Ultra && (f.anim == Anim.Water)) 0.06f * sin(t * 1.8f + f.cx * 0.9f + f.cz * 0.6f) else 0f
            for (k in 0 until 4) {
                val ao = if (smooth) f.ao[k] else 1f
                // The underside sees the bright sea of clouds below: it never goes fully dark.
                val skyLevel = if (smooth) (if (under) max(f.sky[k], 0.55f) else f.sky[k]) else 1f
                val sky = SKY_CURVE[(skyLevel * 15).toInt().coerceIn(0, 15)]
                val exposure = min(1f, skyLevel * 1.4f)
                val sunLit = if (shadows) f.sun[k] else exposure
                val moonLit = if (shadows) f.moon[k] else exposure
                val overcast = if (cloudy && sunFacing > 0f && sunLit > 0f) cloudShadow(f.corners[k * 3], f.corners[k * 3 + 2], t) else 1f
                val vd = (dl.ambient * sky * ao + dl.diffuse * sunFacing * sunLit * overcast) * f.shade
                val vn = (nl.ambient * sky * ao + nl.diffuse * moonFacing * moonLit) * f.shade
                val torch = if (torches > 0f) TORCH_CURVE[(f.torch[k] * 15).toInt().coerceIn(0, 15)] * torches * ao else 0f
                val source = f.source[k]
                val glow = if (source >= 0) world.lights[source].glow else Glow.Warm
                val flick = if (source >= 0) flicker[source] else 1f
                val gain = 1 + ripple * (if (k % 2 == 0) 1f else -1f)
                var r = ((vd * dl.tint[0]) * d + (vn * nl.tint[0]) * n + torch * glow.r * flick) * gain
                var g = ((vd * dl.tint[1]) * d + (vn * nl.tint[1]) * n + torch * glow.g * flick) * gain
                var b = ((vd * dl.tint[2]) * d + (vn * nl.tint[2]) * n + torch * glow.b * flick) * gain
                if (f.emit > 0f) {
                    r = max(r, f.emit)
                    g = max(g, f.emit)
                    b = max(b, f.emit)
                }
                if (!textures) {
                    val c = f.color
                    r *= ((c shr 16) and 0xFF) / 255f
                    g *= ((c shr 8) and 0xFF) / 255f
                    b *= (c and 0xFF) / 255f
                }
                argb[i * 4 + k] = (channel(f.alpha * 255) shl 24) or (channel(r * 255) shl 16) or (channel(g * 255) shl 8) or channel(b * 255)
            }
            // Far to near by cell steps from the eye, then by distance within a cell.
            val steps = abs(f.cellX - ex) + abs(f.cellY - ey) + abs(f.cellZ - ez)
            val dist = distance(f.cx, f.cy + bob, f.cz)
            keys[count++] = ((0x7FFF - steps.coerceAtMost(0x7FFF)).toLong() shl 48) or ((0xFFFF - (dist * 64).toInt().coerceIn(0, 0xFFFF)).toLong() shl 32) or i.toLong()
        }
        emitSorted(faces, count, textures = textures, animated = animated, t = t)
    }

    /**
     * A tiling map of cloud cover for the shadows the clouds cast on Ultra: soft-edged blobs that
     * wrap at the edges, so the pattern can drift over the island forever without a seam.
     */
    private val cloudMap = FloatArray(CLOUD_MAP * CLOUD_MAP) { i ->
        val x = (i % CLOUD_MAP).toFloat()
        val z = (i / CLOUD_MAP).toFloat()
        val n = tiledNoise(x / 8f, z / 8f, CLOUD_MAP / 8, 77) * 0.65f + tiledNoise(x / 4f, z / 4f, CLOUD_MAP / 4, 78) * 0.35f
        smoothstep(0.5f, 0.64f, n)
    }

    /** How much sunlight reaches a point under the drifting clouds, 1 in the clear. */
    private fun cloudShadow(x: Float, z: Float, t: Float): Float {
        val u = (x - t * scene.drift.speed) / CLOUD_TEXEL
        val v = z / CLOUD_TEXEL
        val u0 = floor(u).toInt()
        val v0 = floor(v).toInt()
        val fu = u - u0
        val fv = v - v0
        val i0 = Math.floorMod(u0, CLOUD_MAP)
        val i1 = (i0 + 1) % CLOUD_MAP
        val j0 = Math.floorMod(v0, CLOUD_MAP) * CLOUD_MAP
        val j1 = (j0 + CLOUD_MAP) % (CLOUD_MAP * CLOUD_MAP)
        val a = cloudMap[j0 + i0] + (cloudMap[j0 + i1] - cloudMap[j0 + i0]) * fu
        val b = cloudMap[j1 + i0] + (cloudMap[j1 + i1] - cloudMap[j1 + i0]) * fu
        return 1f - 0.8f * (a + (b - a) * fv)
    }

    /** Whether [f]'s front faces the eye; drifting and bobbing move it first. */
    private fun facing(f: Face, dx: Float, dy: Float): Boolean =
        (cam[0] - f.cx - dx) * f.normal[0] + (cam[1] - f.cy - dy) * f.normal[1] + (cam[2] - f.cz) * f.normal[2] > 0f

    private fun distance(x: Float, y: Float, z: Float): Float {
        val ex = x - cam[0]
        val ey = y - cam[1]
        val ez = z - cam[2]
        return sqrt(ex * ex + ey * ey + ez * ez)
    }

    /** Projects the four corners of a flat face; false if all are behind the eye. */
    private fun projectFace(f: Face, i: Int, dx: Float, dy: Float): Boolean {
        var behind = 0
        for (k in 0 until 4) {
            if (!project(f.corners[k * 3] + dx, f.corners[k * 3 + 1] + dy, f.corners[k * 3 + 2], i * 4 + k)) behind++
        }
        return behind < 4
    }

    /** The island's faces also sway (leaves, grass) and bob (the enchanted book). */
    private fun projectIsland(f: Face, i: Int, t: Float, wave: Float, bob: Float): Boolean {
        var behind = 0
        for (k in 0 until 4) {
            var x = f.corners[k * 3]
            val y = f.corners[k * 3 + 1] + bob
            var z = f.corners[k * 3 + 2]
            if (wave > 0f) {
                // Offsets from the corner's own position, so corners shared by neighbours move together.
                val lift = if (f.anim == Anim.Plant) (y - floor(y - 0.01f)).coerceIn(0f, 1f) else 1f
                val amount = if (f.anim == Anim.Plant) 0.09f else 0.035f
                x += sin(t * 1.3f + x * 0.9f + y * 0.35f) * amount * lift
                z += cos(t * 1.1f + z * 0.8f + y * 0.3f) * amount * lift
            }
            if (!project(x, y, z, i * 4 + k)) behind++
        }
        return behind < 4
    }

    private fun offscreen(i: Int, w: Float, h: Float): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (k in 0 until 4) {
            val j = i * 4 + k
            if (depth[j] < NEAR) return false
            minX = min(minX, sx[j]); maxX = max(maxX, sx[j]); minY = min(minY, sy[j]); maxY = max(maxY, sy[j])
        }
        return maxX < 0 || minX > w || maxY < 0 || minY > h
    }

    /** Sorts the first [count] keys and draws the faces they name, far to near. */
    private fun DrawScope.emitSorted(list: List<Face>, count: Int, textures: Boolean, animated: Boolean = false, t: Float = 0f) {
        java.util.Arrays.sort(keys, 0, count)
        val canvas = drawContext.canvas.skiaCanvas
        triangles = 0
        for (k in 0 until count) {
            val i = (keys[k] and 0xFFFFFFFFL).toInt()
            val f = list[i]
            val scroll = if (animated) scrollOf(f.anim, t) else 0f
            emitFace(canvas, f, i, textures, scroll)
        }
        flush(canvas, textures)
    }

    /** How far down its two-tile strip an animated texture has moved, in pixels. */
    private fun scrollOf(anim: Anim, t: Float): Float = when (anim) {
        Anim.Water -> (t * 1.6f) % 16f
        Anim.Fall -> (16f - (t * 20f) % 16f) % 16f
        Anim.Lava -> (t * 0.9f) % 16f
        Anim.Portal -> (t * 5f) % 16f
        Anim.Fire -> (t * 14f) % 16f
        else -> 0f
    }

    private val clipX = FloatArray(8)
    private val clipY = FloatArray(8)
    private val clipZ = FloatArray(8)
    private val clipU = FloatArray(8)
    private val clipV = FloatArray(8)
    private val clipC = IntArray(8)

    /**
     * One face into the batch. Split along the diagonal that keeps the occlusion gradient symmetric —
     * the same flip Minecraft does, without it dark corners smear into a stripe. A face crossing the
     * near plane is cut there instead of dropped, so the ground under a close camera has no holes.
     */
    private fun emitFace(canvas: org.jetbrains.skia.Canvas, f: Face, i: Int, textures: Boolean, scroll: Float) {
        val base = i * 4
        var cut = false
        for (k in 0 until 4) if (depth[base + k] < NEAR) cut = true
        if (!cut) {
            val flip = f.ao[0] + f.ao[2] < f.ao[1] + f.ao[3]
            val order = if (flip) FLIPPED else STRAIGHT
            for (j in 0 until 6) {
                val k = order[j]
                vertex(sx[base + k], sy[base + k], argb[base + k], f.uv[k * 2], f.uv[k * 2 + 1] + scroll, textures, canvas)
            }
            return
        }
        // Sutherland–Hodgman against the near plane, in camera space, then projected.
        var n = 0
        for (k in 0 until 4) {
            val k2 = (k + 1) % 4
            val d1 = depth[base + k]
            val d2 = depth[base + k2]
            if (d1 >= NEAR) {
                clipX[n] = f.corners[k * 3]; clipY[n] = f.corners[k * 3 + 1]; clipZ[n] = f.corners[k * 3 + 2]
                clipU[n] = f.uv[k * 2]; clipV[n] = f.uv[k * 2 + 1]; clipC[n] = argb[base + k]; n++
            }
            if ((d1 >= NEAR) != (d2 >= NEAR)) {
                val s = (NEAR - d1) / (d2 - d1)
                clipX[n] = f.corners[k * 3] + (f.corners[k2 * 3] - f.corners[k * 3]) * s
                clipY[n] = f.corners[k * 3 + 1] + (f.corners[k2 * 3 + 1] - f.corners[k * 3 + 1]) * s
                clipZ[n] = f.corners[k * 3 + 2] + (f.corners[k2 * 3 + 2] - f.corners[k * 3 + 2]) * s
                clipU[n] = f.uv[k * 2] + (f.uv[k2 * 2] - f.uv[k * 2]) * s
                clipV[n] = f.uv[k * 2 + 1] + (f.uv[k2 * 2 + 1] - f.uv[k * 2 + 1]) * s
                clipC[n] = mixColor(argb[base + k], argb[base + k2], s)
                n++
            }
        }
        if (n < 3) return
        for (j in 1 until n - 1) {
            for (v in intArrayOf(0, j, j + 1)) {
                val ex = clipX[v] - cam[0]
                val ey = clipY[v] - cam[1]
                val ez = clipZ[v] - cam[2]
                val dd = max(NEAR, ex * fwd[0] + ey * fwd[1] + ez * fwd[2])
                val x = ox + (ex * right[0] + ey * right[1] + ez * right[2]) / dd * focal
                val y = oy - (ex * up[0] + ey * up[1] + ez * up[2]) / dd * focal
                vertex(x, y, clipC[v], clipU[v], clipV[v] + scroll, textures, canvas)
            }
        }
    }

    private var pending = 0

    private fun vertex(x: Float, y: Float, color: Int, u: Float, v: Float, textures: Boolean, canvas: org.jetbrains.skia.Canvas) {
        val at = triangles * 3 + pending
        positions[at * 2] = x
        positions[at * 2 + 1] = y
        colors[at] = color
        texCoords[at * 2] = u
        texCoords[at * 2 + 1] = v
        if (++pending == 3) {
            pending = 0
            if (++triangles == BATCH) flush(canvas, textures)
        }
    }

    private fun flush(canvas: org.jetbrains.skia.Canvas, textures: Boolean) {
        if (triangles == 0) return
        // Degenerate triangles for the unused tail: the arrays cannot be passed with a length.
        if (triangles < BATCH) {
            positions.fill(0f, triangles * 6)
            colors.fill(0, triangles * 3)
            texCoords.fill(0f, triangles * 6)
        }
        if (textures) canvas.drawVertices(VertexMode.TRIANGLES, positions, colors, texCoords, null, SkiaBlendMode.MODULATE, textured)
        else canvas.drawVertices(VertexMode.TRIANGLES, positions, colors, null, null, SkiaBlendMode.MODULATE, flatPaint)
        triangles = 0
    }

    // ---------- glows and particles ----------

    /** High and up: a soft halo over each light, stronger at night, hidden behind whatever blocks it. */
    private fun DrawScope.drawGlows(t: Float, quality: SceneQuality) {
        val strength = 0.22f + 0.78f * blend
        for (g in scene.glows) {
            val at = point(g.x, g.y, g.z) ?: continue
            val dist = distance(g.x, g.y, g.z)
            if (dist > 70f) continue
            if (world.hidden(cam[0], cam[1], cam[2], g.x, g.y, g.z)) continue
            val flick = if (g.source in flicker.indices) flicker[g.source] else 1f
            val pulse = if (g.glow == Glow.Portal) 0.85f + 0.15f * sin(t * 2.2f) else 1f
            val radius = g.radius * focal / dist * (if (quality == SceneQuality.Ultra) 1.25f else 1f)
            val color = Color(g.glow.r, g.glow.g, g.glow.b)
            halo(at, radius * 2.2f, color, 0.22f * strength * flick * pulse)
            halo(at, radius * 0.7f, lerp(color, Color.White, 0.5f), 0.35f * strength * flick * pulse)
        }
    }

    /** The still water's top faces, where Ultra puts glints of the sun or the moon. */
    private val waterTops = faces.indices.filter { faces[it].anim == Anim.Water && faces[it].normal[1] > 0.5f }.toIntArray()

    /** Ultra: short glints on the water, two spots per face, each flashing on its own beat. */
    private fun DrawScope.drawGlints(t: Float) {
        val color = lerp(Color(0xFFFFF8E0), Color(0xFFB8D0FF), blend)
        val strength = 1f - 0.45f * blend
        for (i in waterTops) {
            val f = faces[i]
            for (spot in 0 until 2) {
                val rate = 1.7f + hash(i, spot, 51) * 1.6f
                val flash = sin(t * rate + hash(i, spot, 52) * 6.28f)
                if (flash < 0.8f) continue
                val light = ((flash - 0.8f) / 0.2f).pow(2f) * strength
                val x = f.cx - 0.35f + hash(i, spot, 53) * 0.7f
                val z = f.cz - 0.35f + hash(i, spot, 54) * 0.7f
                val at = point(x, f.cy + 0.02f, z) ?: continue
                val dist = distance(x, f.cy, z)
                if (world.hidden(cam[0], cam[1], cam[2], x, f.cy + 0.05f, z)) continue
                val s = max(1.5f, 0.06f * focal / dist)
                halo(at, s * 4f, color, 0.5f * light)
                drawRect(color, Offset(at.x - s * 1.6f, at.y - s * 0.2f), Size(s * 3.2f, s * 0.4f), alpha = light, blendMode = BlendMode.Plus)
                drawRect(color, Offset(at.x - s * 0.2f, at.y - s * 1.6f), Size(s * 0.4f, s * 3.2f), alpha = light, blendMode = BlendMode.Plus)
            }
        }
    }

    /** Ultra, by day: a small flock circling the island, wings beating. */
    private fun DrawScope.drawBirds(t: Float) {
        val day = 1f - blend
        if (day <= 0.05f) return
        val color = Color(0xFF2E3A48)
        for (bird in 0 until 7) {
            val phase = hash(bird, 0, 61)
            val angle = t * 0.11f + bird * 0.22f + phase * 0.3f
            val radius = 24f + hash(bird, 1, 61) * 6f
            val x = cos(angle) * radius
            val y = 13f + hash(bird, 2, 61) * 5f + sin(t * 0.4f + bird) * 0.8f
            val z = sin(angle) * radius
            val at = point(x, y, z) ?: continue
            if (world.hidden(cam[0], cam[1], cam[2], x, y, z)) continue
            val span = 0.55f * focal / distance(x, y, z)
            if (span < 2f) continue
            val beat = sin(t * 7f + phase * 20f)
            val tip = at.y - span * (0.25f + 0.35f * beat)
            val stroke = max(1.2f, span * 0.14f)
            drawLine(color, at, Offset(at.x - span, tip), strokeWidth = stroke, alpha = 0.8f * day)
            drawLine(color, at, Offset(at.x + span, tip), strokeWidth = stroke, alpha = 0.8f * day)
        }
    }

    private fun DrawScope.drawParticles(t: Float, dt: Float, quality: SceneQuality) {
        drawPetals(t, dt, quality)
        if (quality < SceneQuality.High) return
        particles.update(scene, dt, quality, blend)
        val check = quality >= SceneQuality.High
        for (p in particles.live()) {
            val at = point(p.x, p.y, p.z) ?: continue
            val dist = distance(p.x, p.y, p.z)
            // Right in front of the lens a particle is only a blot.
            if (dist < 2f) continue
            if (check && world.hidden(cam[0], cam[1], cam[2], p.x, p.y, p.z)) continue
            val s = min(max(1.2f, p.size * focal / dist), 48f)
            val fade = p.alpha() * ((dist - 2f) / 2f).coerceIn(0f, 1f)
            when (p.kind) {
                Emitter.Kind.Smoke -> {
                    val shade = lerp(Color(0xFFD0D0D0), Color(0xFF6A7488), blend)
                    drawRect(shade, Offset(at.x - s / 2, at.y - s / 2), Size(s, s), alpha = 0.28f * fade)
                }
                Emitter.Kind.Mist -> drawRect(Color.White, Offset(at.x - s / 2, at.y - s / 2), Size(s, s), alpha = 0.35f * fade)
                else -> {
                    val color = when (p.kind) {
                        Emitter.Kind.Embers -> Color(1f, 0.55f + p.seed * 0.35f, 0.15f)
                        Emitter.Kind.Portal -> Color(0.62f + p.seed * 0.2f, 0.3f, 1f)
                        else -> Color(0.75f, 0.9f, 1f)
                    }
                    drawRect(color, Offset(at.x - s / 2, at.y - s / 2), Size(s, s), alpha = fade)
                    halo(at, s * 2.5f, color, 0.3f * fade)
                }
            }
        }
        // Fireflies over the grass at night.
        if (blend > 0.05f) {
            for (fly in fireflies) {
                fly.update(t)
                val at = point(fly.x, fly.y, fly.z) ?: continue
                if (world.hidden(cam[0], cam[1], cam[2], fly.x, fly.y, fly.z)) continue
                val dist = distance(fly.x, fly.y, fly.z)
                val light = (0.5f + 0.5f * sin(t * 2.3f + fly.phase)).pow(2f) * blend
                val s = max(1.5f, 0.07f * focal / dist)
                val color = Color(0.85f, 1f, 0.45f)
                halo(at, s * 5f, color, 0.4f * light)
                drawRect(color, Offset(at.x - s / 2, at.y - s / 2), Size(s, s), alpha = light)
            }
        }
    }

    private fun DrawScope.drawPetals(t: Float, dt: Float, quality: SceneQuality) {
        val box = scene.petals.box
        val light = scene.day.petalLight * (1 - blend) + scene.night.petalLight * blend
        val check = quality >= SceneQuality.High
        for (p in petals) {
            p.x += (p.vx + sin(t * 1.4f + p.phase) * 0.5f) * dt
            p.y += p.vy * dt
            p.z += (p.vz + cos(t * 1.1f + p.phase) * 0.3f) * dt
            if (p.x < box[0]) p.x = box[3] else if (p.x > box[3]) p.x = box[0]
            if (p.y < box[1]) p.y = box[4] else if (p.y > box[4]) p.y = box[1]
            if (p.z < box[2]) p.z = box[5] else if (p.z > box[5]) p.z = box[2]
            val at = point(p.x, p.y, p.z) ?: continue
            if (check && world.solid(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())) continue
            if (check && world.hidden(cam[0], cam[1], cam[2], p.x, p.y, p.z)) continue
            val dist = distance(p.x, p.y, p.z)
            if (dist < 2.5f) continue
            val s = min(max(1f, p.size * focal / dist), 24f)
            drawRect(lerp(Color(0xFF1A2B47), p.color, light), topLeft = Offset(at.x - s / 2, at.y - s / 2), size = Size(s, s), alpha = 0.95f)
        }
    }

    // ---------- projection ----------

    /** Projects a world point into slot [o]; false when it is behind the near plane. */
    private fun project(x: Float, y: Float, z: Float, o: Int): Boolean {
        val ex = x - cam[0]
        val ey = y - cam[1]
        val ez = z - cam[2]
        val d = ex * fwd[0] + ey * fwd[1] + ez * fwd[2]
        depth[o] = d
        if (d < NEAR) return false
        sx[o] = ox + (ex * right[0] + ey * right[1] + ez * right[2]) / d * focal
        sy[o] = oy - (ex * up[0] + ey * up[1] + ez * up[2]) / d * focal
        return true
    }

    /** A world point on screen, or null when it is behind the eye. */
    private fun point(x: Float, y: Float, z: Float): Offset? {
        val ex = x - cam[0]
        val ey = y - cam[1]
        val ez = z - cam[2]
        val d = ex * fwd[0] + ey * fwd[1] + ez * fwd[2]
        if (d < 0.3f) return null
        return Offset(ox + (ex * right[0] + ey * right[1] + ez * right[2]) / d * focal, oy - (ex * up[0] + ey * up[1] + ez * up[2]) / d * focal)
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

        /** Sky light level to brightness: the game's own curve, gentle near full and steep in the dark. */
        val SKY_CURVE = FloatArray(16) { level -> 0.12f + 0.88f * 0.82f.pow(15 - level) }

        /** Torch light level to brightness: a torch's pool with a soft edge. */
        val TORCH_CURVE = FloatArray(16) { level -> (level / 15f).pow(1.7f) }

        /**
         * The aurora's curtains: first azimuth, span, height of the foot, brightness. Two stretches of
         * sky, so the orbiting camera meets the lights on most of its turn; the second curtain of each
         * pair hangs behind and above the first.
         */
        val AURORA = listOf(
            floatArrayOf(-2.8f, 1.9f, 0.035f, 0.75f),
            floatArrayOf(-2.3f, 1.5f, 0.065f, 0.5f),
            floatArrayOf(0.5f, 1.9f, 0.04f, 0.75f),
            floatArrayOf(1.0f, 1.4f, 0.07f, 0.5f),
        )
        const val AURORA_COLUMNS = 120

        /** The cloud-shadow map: texels a side, and blocks per texel. */
        const val CLOUD_MAP = 64
        const val CLOUD_TEXEL = 2.5f

        /** Rows up a curtain, as shares of its height: the foot, the bright line, the violet middle, the top. */
        val AURORA_ROWS = floatArrayOf(0f, 0.12f, 0.45f, 1f)
        val AURORA_ALPHA = floatArrayOf(0f, 1f, 0.35f, 0f)
        val AURORA_TINT = floatArrayOf(0.3f, 1f, 0.6f, 0.35f, 1f, 0.62f, 0.45f, 0.55f, 1f, 0.7f, 0.35f, 1f)
    }
}

/** A cloud of short-lived particles fed by the scene's emitters. */
private class Particles {
    class P {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var age = 0f; var life = 1f; var size = 0.1f
        var seed = 0f
        var kind = Emitter.Kind.Smoke
        fun alpha(): Float {
            val k = age / life
            return (if (k < 0.15f) k / 0.15f else 1f - (k - 0.15f) / 0.85f).coerceIn(0f, 1f)
        }
    }

    private val pool = Array(700) { P() }
    private var alive = 0
    private val rng = Rng(4242)
    private val carry = HashMap<Emitter, Float>()

    fun live(): List<P> = pool.asList().subList(0, alive)

    fun update(scene: Scene, dt: Float, quality: SceneQuality, night: Float) {
        var i = 0
        while (i < alive) {
            val p = pool[i]
            p.age += dt
            if (p.age >= p.life) {
                alive--
                val tmp = pool[i]; pool[i] = pool[alive]; pool[alive] = tmp
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            when (p.kind) {
                Emitter.Kind.Smoke -> { p.size += dt * 0.12f; p.vx += dt * 0.08f }
                Emitter.Kind.Portal -> { p.vx *= 0.985f; p.vz *= 0.985f }
                Emitter.Kind.Enchant -> { p.vx *= 0.97f; p.vz *= 0.97f }
                else -> Unit
            }
            i++
        }
        val scale = if (quality == SceneQuality.Ultra) 1.6f else 1f
        for (e in scene.emitters) {
            val rate = when (e.kind) {
                Emitter.Kind.Smoke -> 5f
                Emitter.Kind.Embers -> 7f * (0.6f + night)
                Emitter.Kind.Portal -> 14f
                Emitter.Kind.Enchant -> if (quality == SceneQuality.Ultra) 6f else 0f
                Emitter.Kind.Mist -> if (quality == SceneQuality.Ultra) 22f else 0f
            } * scale
            var due = (carry[e] ?: 0f) + rate * dt
            while (due >= 1f && alive < pool.size) {
                spawn(e)
                due -= 1f
            }
            carry[e] = due.coerceAtMost(4f)
        }
    }

    private fun spawn(e: Emitter) {
        val p = pool[alive++]
        val r = { rng.next() - 0.5f }
        p.kind = e.kind
        p.age = 0f
        p.seed = rng.next()
        when (e.kind) {
            Emitter.Kind.Smoke -> {
                p.x = e.x + r() * e.spread; p.y = e.y; p.z = e.z + r() * e.spread
                p.vx = r() * 0.15f; p.vy = 0.55f + rng.next() * 0.35f; p.vz = r() * 0.15f
                p.life = 3.5f + rng.next() * 2f; p.size = 0.18f + rng.next() * 0.12f
            }
            Emitter.Kind.Embers -> {
                p.x = e.x + r() * e.spread; p.y = e.y; p.z = e.z + r() * e.spread
                p.vx = r() * 0.4f; p.vy = 0.8f + rng.next() * 1.2f; p.vz = r() * 0.4f
                p.life = 0.8f + rng.next() * 0.9f; p.size = 0.045f + rng.next() * 0.04f
            }
            Emitter.Kind.Portal -> {
                val a = rng.next() * 6.28f
                val d = 1f + rng.next() * e.spread
                p.x = e.x + cos(a) * d; p.y = e.y + r() * 3f; p.z = e.z + sin(a) * d * 0.6f
                p.vx = (e.x - p.x) * 0.45f; p.vy = r() * 0.3f; p.vz = (e.z - p.z) * 0.45f
                p.life = 1.6f + rng.next() * 1.2f; p.size = 0.06f + rng.next() * 0.05f
            }
            Emitter.Kind.Enchant -> {
                val a = rng.next() * 6.28f
                p.x = e.x + cos(a) * e.spread; p.y = e.y + 0.8f + rng.next() * 0.8f; p.z = e.z + sin(a) * e.spread
                p.vx = (e.x - p.x) * 0.7f; p.vy = -0.35f; p.vz = (e.z - p.z) * 0.7f
                p.life = 1.4f; p.size = 0.05f
            }
            Emitter.Kind.Mist -> {
                p.x = e.x + r() * 0.9f; p.y = e.y - rng.next() * 6f; p.z = e.z + r() * 1.2f
                p.vx = r() * 0.5f + 0.3f; p.vy = -2f - rng.next() * 2f; p.vz = r() * 0.5f
                p.life = 1.2f + rng.next(); p.size = 0.12f + rng.next() * 0.1f
            }
        }
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

/** A firefly wandering over the island's grass. */
private class Firefly(rng: Rng) {
    private val cx = (rng.next() - 0.5f) * 26f
    private val cz = (rng.next() - 0.5f) * 26f
    private val cy = 3.2f + rng.next() * 3.5f
    val phase = rng.next() * 6.28f
    private val speed = 0.2f + rng.next() * 0.3f
    var x = cx
    var y = cy
    var z = cz

    fun update(t: Float) {
        x = cx + sin(t * speed + phase) * 1.6f + sin(t * speed * 2.3f) * 0.4f
        y = cy + sin(t * speed * 1.7f + phase * 2) * 0.6f
        z = cz + cos(t * speed * 0.9f + phase) * 1.6f
    }
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

private fun argbOf(a: Float, r: Float, g: Float, b: Float): Int =
    (channel(a * 255) shl 24) or (channel(r * 255) shl 16) or (channel(g * 255) shl 8) or channel(b * 255)

private fun mixColor(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = (((a ushr s) and 0xFF) * (1 - t) + ((b ushr s) and 0xFF) * t).toInt().coerceIn(0, 255)
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/** Value noise whose lattice repeats every [period] cells, so a map sampled from it tiles. */
private fun tiledNoise(x: Float, y: Float, period: Int, seed: Int): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = x - x0
    val fy = y - y0
    val u = fx * fx * (3 - 2 * fx)
    val v = fy * fy * (3 - 2 * fy)
    fun at(i: Int, j: Int) = hash(Math.floorMod(i, period), Math.floorMod(j, period), seed)
    val a = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * u
    val b = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * u
    return a + (b - a) * v
}

package com.yumi.launcher.scene

import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Everything the renderer needs to draw one scene. Directions are unit vectors; [Camera.offsetX]
 * shifts the projection centre so the subject sits beside the UI rather than behind it.
 *
 * The geometry is shared and the lighting is not: [day] and [night] are two looks over the same
 * faces, and the renderer blends between them, so the theme switch is a sunset rather than a cut.
 */
internal class Scene(
    val faces: List<Face>,
    val drift: Drift?,
    val camera: Camera,
    val day: Look,
    val night: Look,
    val sun: FloatArray,
    val moon: FloatArray,
    val petals: Petals,
)

internal class Camera(
    val target: FloatArray, val distance: Float, val pitch: Float, val yaw: Float,
    val yawSpeed: Float, val fov: Float, val offsetX: Float, val parallax: Float,
)

/** One time of day: sky ramp top to horizon, light, haze, how much the clouds glow, how lit the petals are. */
internal class Look(
    val sky: List<Color>,
    val light: Light,
    val fog: Fog,
    val cloudGlow: Float,
    val petalLight: Float,
)

internal class Light(val direction: FloatArray, val ambient: Float, val diffuse: Float, val tint: FloatArray = floatArrayOf(1f, 1f, 1f))
internal class Fog(val color: FloatArray, val start: Float, val end: Float, val max: Float)
internal class Petals(val count: Int, val colors: List<Color>, val size: Float, val box: FloatArray)

internal fun normalize(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z)
    return floatArrayOf(x / l, y / l, z / l)
}

/**
 * The home screen's scene: a floating cherry-grove island with a pond, a waterfall off its east
 * edge and a few rocks around it, over a sea of clouds. Built once, from fixed seeds, so it is the
 * same island on every launch.
 */
internal fun islandScene(): Scene {
    val world = World()
    val rng = Rng(7)
    val radius = 8.5f
    val pond = floatArrayOf(-3f, 2f)
    val tops = HashMap<Pair<Int, Int>, Int>()
    fun top(x: Int, z: Int) = (1 + fbm(x * 0.18f + 2, z * 0.18f + 5, 3) * 3).roundToInt()

    for (x in -11..11) for (z in -11..11) {
        val d = hypot(x.toFloat(), z.toFloat())
        val edge = radius + (valueNoise(x * 0.3f + 9, z * 0.3f, 8) - 0.5f) * 2.5f
        if (d > edge) continue
        val inPond = hypot(x - pond[0], z - pond[1]) < 2.3f
        val h = if (inPond) 1 else top(x, z)
        val depth = ((1 - d / edge).pow(0.8f) * 11 + valueNoise(x * 0.4f, z * 0.4f, 4) * 2).roundToInt() + 1
        for (y in -depth..h) {
            world[x, y, z] = when {
                inPond && y == h -> Block.Water
                inPond && y == h - 1 -> Block.Sand
                y == h -> Block.Grass
                y >= h - 2 -> Block.Dirt
                y < -depth * 0.55f -> Block.Deep
                else -> Block.Stone
            }
        }
        tops[x to z] = h
    }

    // The waterfall: a stream cut into the east rim, then a column of water falling past the rock.
    var east = 0
    while (tops[(east + 1) to 0] != null) east++
    val rim = tops.getValue(east to 0)
    world[east, rim, 0] = Block.Water
    for (y in rim downTo -18) world[east + 1, y, 0] = Block.Water

    world.cherry(2, tops[2 to -2] ?: 2, -2, rng)
    world.cherry(-2, tops[-2 to -5] ?: 2, -5, rng)
    world.oak(-5, tops[-5 to -1] ?: 2, -1, rng)

    for ((cx, cy, cz, r) in listOf(
        Rock(-15, 3, -6, 2.6f), Rock(13, -4, 9, 2.2f), Rock(-10, -9, 12, 1.8f), Rock(11, 6, -11, 1.6f),
    )) {
        for (ix in -3..3) for (iz in -3..3) {
            val dd = hypot(ix.toFloat(), iz.toFloat())
            if (dd > r) continue
            val depth = ((1 - dd / r) * 4).roundToInt() + 1
            for (iy in -depth..0) {
                world[cx + ix, cy + iy, cz + iz] = when (iy) { 0 -> Block.Grass; -1 -> Block.Dirt; else -> Block.Stone }
            }
        }
    }

    val faces = ArrayList<Face>(12_000)
    // The underside darkens toward its tip, which is most of what makes the island read as hanging.
    world.faces(faces, shade = { cy -> ((0.62f + (cy + 12) / 14 * 0.38f)).coerceIn(0.62f, 1f) }, occlusion = true)
    world.castShadows(faces, SUN, MOON)
    val drift = clouds(faces, seed = 11, cells = 24, height = -22f, threshold = 0.47f)
    // Sparse and high: this layer passes behind the top bar, where a solid sheet of white would swallow it.
    clouds(faces, seed = 23, cells = 24, height = 17f, threshold = 0.7f)

    return Scene(
        faces = faces,
        drift = drift,
        camera = Camera(
            target = floatArrayOf(0f, -1f, 0f), distance = 38f, pitch = 0.16f, yaw = 0.6f,
            yawSpeed = 0.05f, fov = 48f, offsetX = 0.2f, parallax = 0.12f,
        ),
        // The day sky is kept a step bluer than the glass panels so a white card never sits on white.
        day = Look(
            sky = listOf(Color(0xFF3F8CEB), Color(0xFF7FBBF8), Color(0xFFC4E1FF)),
            light = Light(SUN, ambient = 0.62f, diffuse = 0.5f, tint = floatArrayOf(1.05f, 0.99f, 0.92f)),
            fog = Fog(floatArrayOf(196f, 225f, 255f), start = 30f, end = 85f, max = 0.55f),
            cloudGlow = 1f,
            petalLight = 1f,
        ),
        // Moonlight: cold, low, with the clouds dimmed to shapes and Yumi's near-black blue for the sky.
        night = Look(
            sky = listOf(Color(0xFF04070D), Color(0xFF0B1628), Color(0xFF1A2B47)),
            light = Light(MOON, ambient = 0.26f, diffuse = 0.4f, tint = floatArrayOf(0.74f, 0.84f, 1.12f)),
            fog = Fog(floatArrayOf(22f, 37f, 62f), start = 28f, end = 80f, max = 0.6f),
            cloudGlow = 0.22f,
            petalLight = 0.5f,
        ),
        sun = SUN,
        moon = MOON,
        petals = Petals(
            count = 80,
            colors = listOf(Color(0xFFF7B3D0), Color(0xFFF39CC2), Color(0xFFFFD1E3)),
            size = 0.14f,
            box = floatArrayOf(-13f, -8f, -13f, 13f, 9f, 13f),
        ),
    )
}

/**
 * Where the light comes from, day and night — the same direction the disc is drawn in, so shadows
 * fall away from the sun you can see. Both sit low, a late-afternoon sun: high up they would be
 * above the frame, since the camera looks down on the island, and a low light draws the long
 * shadows that make the blocks read as solid.
 */
private val SUN = normalize(0.55f, 0.24f, -0.65f)
private val MOON = normalize(-0.6f, 0.26f, -0.55f)

private data class Rock(val x: Int, val y: Int, val z: Int, val radius: Float)

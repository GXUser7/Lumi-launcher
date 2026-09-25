package com.yumi.launcher.ui.theme

import com.yumi.launcher.settings.Palette
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.stream.IntStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The title screen's backdrop in the game-menu pack. Minecraft spins a cube map behind its menus;
 * this paints one — a sky in the palette's hues over a sea of the game's own blocky clouds, with a
 * sparse layer overhead and a hazy one far below showing through the gaps. The day look goes with
 * the light theme: a low square sun and a warm glow along the horizon. The night look goes with the
 * dark one: stars, a square moon and an aurora in the palette's third colour.
 *
 * Every pixel is traced from its own direction, so the six faces meet without a seam whichever
 * way the game turns them. The faces follow the game's layout — 0 ahead, 1 right, 2 behind,
 * 3 left, 4 up, 5 down — with the up face's bottom edge and the down face's top edge on the face
 * ahead. The game looks 10° down with an 85° lens, so the horizon sits a little above the middle
 * of the screen and the clouds fill the lower half, under the buttons.
 */
internal object MenuPanorama {
    const val SIZE = 1024

    /** The six faces as PNG files, in the game's order. */
    fun faces(palette: Palette, dark: Boolean, size: Int = SIZE): List<ByteArray> {
        val sky = Sky(palette, dark)
        return (0 until 6).map { face -> png(sky.face(face, size), size) }
    }

    private fun png(pixels: IntArray, size: Int): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, size, size, pixels, 0, size)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}

/**
 * The hues a sky is made of, in OKLCH degrees: the top of the sky, the band along the horizon, the
 * warm glow around the sun, and the aurora. Picked per palette rather than taken from its three
 * accent hues as they are — a mint accent makes a pleasant button and a sickly sky — but from the
 * same family, so each sky reads as its palette.
 */
private class SkyHues(val zenith: Float, val horizon: Float, val glow: Float, val aurora: Float)

private fun skyHues(palette: Palette) = when (palette) {
    // Glacier's ice over its sand, with a mint aurora.
    Palette.Original -> SkyHues(zenith = 258f, horizon = 235f, glow = 75f, aurora = 165f)
    // Teal fading to pistachio, an apricot sunrise.
    Palette.Green -> SkyHues(zenith = 200f, horizon = 150f, glow = 60f, aurora = 140f)
    // Lilac over sakura, a peach glow, a mint aurora.
    Palette.Pink -> SkyHues(zenith = 315f, horizon = 355f, glow = 45f, aurora = 175f)
    // Sky over lavender, a butter-yellow sun, a lavender aurora.
    Palette.Blue -> SkyHues(zenith = 245f, horizon = 285f, glow = 85f, aurora = 290f)
    // Violet dusk over lavender, a rose glow, an aqua aurora.
    Palette.Lavender -> SkyHues(zenith = 290f, horizon = 320f, glow = 60f, aurora = 200f)
    // Plum over cherry blossom, a gold sun, a green aurora.
    Palette.Cherry -> SkyHues(zenith = 320f, horizon = 10f, glow = 65f, aurora = 150f)
    // A clear blue over a peach horizon, a sea-green aurora.
    Palette.Peach -> SkyHues(zenith = 255f, horizon = 45f, glow = 60f, aurora = 185f)
    // Evening blue over an amber horizon, a sage aurora.
    Palette.Amber -> SkyHues(zenith = 245f, horizon = 65f, glow = 70f, aurora = 140f)
    // Deep sea over sea glass, a sandy sun.
    Palette.Teal -> SkyHues(zenith = 225f, horizon = 190f, glow = 80f, aurora = 165f)
    // Steel over pale grey-blue: the calmest sky.
    Palette.Graphite -> SkyHues(zenith = 250f, horizon = 235f, glow = 80f, aurora = 175f)
}

/** One layer of blocky clouds: a slab between [bottom] and [top], in square cells [cell] blocks wide. */
private class CloudLayer(val bottom: Float, val top: Float, val cell: Float, val cover: Float, val seed: Int, val haze: Float) {
    /** Whether the cell at [i], [j] holds a cloud: soft noise cut at a level, as the game's cloud map is. */
    fun solid(i: Int, j: Int): Boolean {
        val n = noise(i * 0.21f, j * 0.21f, seed) * 0.6f + noise(i * 0.55f, j * 0.55f, seed + 1) * 0.4f
        return n > 1f - cover
    }
}

private class Hit(var distance: Float = 0f, var normal: Int = 0, var height: Float = 0f)

/** Face normals of a hit: up, down, and the four sides. */
private const val UP = 0
private const val DOWN = 1
private const val EAST = 2
private const val WEST = 3
private const val SOUTH = 4
private const val NORTH = 5

private const val FAR = 3200f

private class Sky(palette: Palette, private val dark: Boolean) {
    private val hues = skyHues(palette)

    // By day the sun sits low ahead and to the right; by night the moon is higher on the left.
    private val sun = direction(azimuth = 0.62f, elevation = 0.12f)
    private val moon = direction(azimuth = -0.85f, elevation = 0.27f)
    private val light = if (dark) moon else sun
    private val lightFlat = normalize(light[0], 0f, light[2])

    private val gradient = gradient()
    private val glow = linear(0.93f, 0.10f, hues.glow)

    // The eye flies well above the sea, as from a plane: the clouds break into islands with the
    // blue depths between them, and a second sea far below shows through the gaps.
    private val layers = listOf(
        CloudLayer(bottom = 110f, top = 114f, cell = 12f, cover = 0.24f, seed = 31, haze = 1400f),
        CloudLayer(bottom = -64f, top = -60f, cell = 12f, cover = 0.4f, seed = 7, haze = 1300f),
        CloudLayer(bottom = -150f, top = -146f, cell = 12f, cover = 0.46f, seed = 19, haze = 420f),
    )

    private val top = if (dark) linear(0.34f, 0.035f, hues.zenith) else linear(0.975f, 0.012f, hues.horizon)
    private val lit = if (dark) linear(0.31f, 0.045f, hues.horizon) else linear(0.95f, 0.035f, hues.glow)
    private val shade = if (dark) linear(0.22f, 0.035f, hues.zenith) else linear(0.82f, 0.035f, hues.zenith)
    // Seen from below, a cloud is lit by the sea of cloud under it, not dark.
    private val bottom = if (dark) linear(0.2f, 0.03f, hues.zenith) else linear(0.87f, 0.03f, hues.zenith)
    private val auroraLow = linear(0.82f, 0.16f, hues.aurora)
    private val auroraHigh = linear(0.62f, 0.15f, hues.aurora + 70f)
    private val moonFace = linear(0.94f, 0.02f, hues.zenith)
    private val moonCrater = linear(0.80f, 0.03f, hues.zenith)
    private val moonHalo = linear(0.70f, 0.06f, hues.zenith)

    fun face(face: Int, size: Int): IntArray {
        val out = IntArray(size * size)
        IntStream.range(0, size).parallel().forEach { row ->
            val hit = Hit()
            val rgb = FloatArray(3)
            val sum = FloatArray(3)
            for (col in 0 until size) {
                sum.fill(0f)
                // Four rays a pixel, for the hard edges of the cloud blocks.
                for (s in 0 until 4) {
                    val u = (col + 0.25f + 0.5f * (s and 1)) / size
                    val v = (row + 0.25f + 0.5f * (s shr 1)) / size
                    val d = faceDirection(face, u, v)
                    trace(d, hit, rgb)
                    sum[0] += rgb[0]; sum[1] += rgb[1]; sum[2] += rgb[2]
                }
                out[row * size + col] = pack(sum[0] / 4, sum[1] / 4, sum[2] / 4)
            }
        }
        return out
    }

    private fun trace(d: FloatArray, hit: Hit, out: FloatArray) {
        // Upward rays can only meet the high layer; downward ones the sea, then the haze under it.
        val candidates = if (d[1] > 0f) 0..0 else 1..2
        for (i in candidates) {
            val layer = layers[i]
            if (march(layer, d, hit)) {
                cloud(layer, d, hit, out)
                return
            }
        }
        background(d, out)
    }

    /**
     * Walks the ray through the layer's slab cell by cell. Returns true at the first solid cell,
     * with the face it came in through: the slab's top or bottom, or a cell's side.
     */
    private fun march(layer: CloudLayer, d: FloatArray, hit: Hit): Boolean {
        val dy = d[1]
        if (abs(dy) < 1e-5f) return false
        val enter = if (dy < 0f) layer.top else layer.bottom
        val exit = if (dy < 0f) layer.bottom else layer.top
        val t0 = enter / dy
        val t1 = exit / dy
        if (t0 < 0f || t0 > FAR) return false
        val c = layer.cell
        var x = d[0] * t0 / c
        var z = d[2] * t0 / c
        var i = floor(x).toInt()
        var j = floor(z).toInt()
        if (layer.solid(i, j)) {
            hit.distance = t0; hit.normal = if (dy < 0f) UP else DOWN; hit.height = if (dy < 0f) 1f else 0f
            return true
        }
        val stepI = if (d[0] > 0f) 1 else -1
        val stepJ = if (d[2] > 0f) 1 else -1
        val dxc = d[0] / c
        val dzc = d[2] / c
        val tDeltaI = if (dxc != 0f) abs(1f / dxc) else Float.MAX_VALUE
        val tDeltaJ = if (dzc != 0f) abs(1f / dzc) else Float.MAX_VALUE
        var tNextI = if (dxc != 0f) t0 + ((if (stepI > 0) i + 1 - x else x - i) * tDeltaI) else Float.MAX_VALUE
        var tNextJ = if (dzc != 0f) t0 + ((if (stepJ > 0) j + 1 - z else z - j) * tDeltaJ) else Float.MAX_VALUE
        val end = min(t1, FAR)
        while (true) {
            val t: Float
            val normal: Int
            if (tNextI < tNextJ) {
                t = tNextI; i += stepI; tNextI += tDeltaI
                normal = if (stepI > 0) WEST else EAST
            } else {
                t = tNextJ; j += stepJ; tNextJ += tDeltaJ
                normal = if (stepJ > 0) NORTH else SOUTH
            }
            if (t > end) return false
            if (layer.solid(i, j)) {
                hit.distance = t
                hit.normal = normal
                hit.height = (d[1] * t - layer.bottom) / (layer.top - layer.bottom)
                return true
            }
        }
    }

    private fun cloud(layer: CloudLayer, d: FloatArray, hit: Hit, out: FloatArray) {
        val base: FloatArray
        var k = 1f
        when (hit.normal) {
            UP -> base = top
            DOWN -> base = bottom
            else -> {
                val nx = when (hit.normal) { EAST -> 1f; WEST -> -1f; else -> 0f }
                val nz = when (hit.normal) { SOUTH -> 1f; NORTH -> -1f; else -> 0f }
                val facing = max(0f, nx * lightFlat[0] + nz * lightFlat[2])
                base = FloatArray(3) { shade[it] + (lit[it] - shade[it]) * (0.2f + 0.8f * facing) }
                // A block's side darkens a little toward its foot, as under Minecraft's own clouds.
                k = 0.84f + 0.16f * hit.height.coerceIn(0f, 1f)
            }
        }
        // Tops toward the sun catch its glow.
        val warm = if (!dark && hit.normal == UP) 0.18f * pow4(max(0f, dot(d, sun))) else 0f
        skyBase(d, out)
        val haze = 1f - exp(-hit.distance / layer.haze)
        for (ch in 0..2) {
            val c = base[ch] * k + glow[ch] * warm
            out[ch] = c + (out[ch] - c) * haze
        }
    }

    /** The sky itself: the gradient and the glow, which is also what distance fades the clouds into. */
    private fun skyBase(d: FloatArray, out: FloatArray) {
        val at = ((d[1] + 1f) * 0.5f * (GRADIENT - 1)).coerceIn(0f, GRADIENT - 1f)
        val i = at.toInt().coerceAtMost(GRADIENT - 2)
        val f = at - i
        for (ch in 0..2) out[ch] = gradient[i * 3 + ch] + (gradient[(i + 1) * 3 + ch] - gradient[i * 3 + ch]) * f
        if (!dark) {
            val toward = max(0f, dot(d, sun))
            // A wide warm wash round the sun, strongest low down, and a tight bright halo.
            val band = exp(-abs(d[1] - sun[1]) * 7f)
            val g = min(0.85f, 0.42f * pow4(toward) * band + 0.7f * pow(toward, 90) + 0.14f * band * (0.4f + 0.6f * toward))
            for (ch in 0..2) out[ch] += (glow[ch] - out[ch]) * g
        } else {
            val toward = max(0f, dot(d, moon))
            val g = 0.35f * pow(toward, 60) + 0.18f * pow(toward, 8)
            for (ch in 0..2) out[ch] += moonHalo[ch] * g
        }
    }

    /** What an open ray sees: the sky, and by day the sun, by night the stars, the moon and the aurora. */
    private fun background(d: FloatArray, out: FloatArray) {
        skyBase(d, out)
        if (!dark) {
            val sq = square(d, sun, 0.042f)
            if (sq != null) {
                val inner = max(abs(sq.first), abs(sq.second)) < 0.62f
                val c = if (inner) SUN_CORE else SUN_RIM
                for (ch in 0..2) out[ch] = c[ch]
            }
            return
        }
        stars(d, out)
        aurora(d, out)
        square(d, moon, 0.034f)?.let { (u, v) ->
            // Two craters are enough to make a square read as the Minecraft moon.
            val crater = (u in -0.62f..-0.1f && v in 0.05f..0.55f) || (u in 0.18f..0.62f && v in -0.6f..-0.2f)
            val c = if (crater) moonCrater else moonFace
            for (ch in 0..2) out[ch] = c[ch]
        }
    }

    private fun stars(d: FloatArray, out: FloatArray) {
        if (d[1] < 0f) return
        val px = d[0] * 260f
        val py = d[1] * 260f
        val pz = d[2] * 260f
        val ix = floor(px).toInt()
        val iy = floor(py).toInt()
        val iz = floor(pz).toInt()
        if (hash(ix, iy, iz, 5) > 0.006f) return
        val cx = ix + 0.3f + 0.4f * hash(ix, iy, iz, 6)
        val cy = iy + 0.3f + 0.4f * hash(ix, iy, iz, 7)
        val cz = iz + 0.3f + 0.4f * hash(ix, iy, iz, 8)
        val r = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz))
        val radius = 0.16f + 0.26f * hash(ix, iy, iz, 9)
        if (r > radius) return
        val light = (1f - r / radius) * (0.45f + 0.55f * hash(ix, iy, iz, 10)) * smoothstep(0f, 0.2f, d[1]) * 1.6f
        for (ch in 0..2) out[ch] += light
    }

    /**
     * Curtains of light low over the sky: a bright foot fading upward into the palette's next hue,
     * in narrow rays, with two stretches of sky so the spinning view meets them twice a turn.
     */
    private fun aurora(d: FloatArray, out: FloatArray) {
        if (d[1] < 0.02f || d[1] > 0.6f) return
        val a = atan2(d[0], d[2])
        var strength = 0f
        var share = 0f
        for (curtain in AURORA) {
            val span = angleBetween(a, curtain[0])
            if (abs(span) > curtain[1]) continue
            val s = span / curtain[1]
            val foot = curtain[2] + 0.03f * sin(a * 2.3f + curtain[0]) + 0.012f * sin(a * 7.1f)
            val h = (d[1] - foot) / 0.2f
            if (h < 0f || h > 1f) continue
            val profile = if (h < 0.06f) h / 0.06f else exp(-(h - 0.06f) * 3.4f)
            val rays = (0.55f + 0.45f * sin(a * 41f + 1.3f * sin(a * 5f))) * (0.7f + 0.3f * sin(a * 13f + curtain[0]))
            val ends = 1f - smoothstep(0.55f, 1f, abs(s))
            val k = profile * rays * ends * curtain[3]
            if (k > strength) { strength = k; share = h }
        }
        if (strength <= 0f) return
        for (ch in 0..2) out[ch] += (auroraLow[ch] + (auroraHigh[ch] - auroraLow[ch]) * share) * strength * 0.55f
    }

    /** Where [d] falls on a square of half-size [half] (radians, near enough) facing the eye around [center], or null. */
    private fun square(d: FloatArray, center: FloatArray, half: Float): Pair<Float, Float>? {
        val facing = dot(d, center)
        if (facing < 0.9f) return null
        val right = normalize(center[2], 0f, -center[0])
        val up = cross(center, right)
        val u = dot(d, right) / facing / half
        val v = dot(d, up) / facing / half
        return if (abs(u) <= 1f && abs(v) <= 1f) u to v else null
    }

    /** The sky from straight down to straight up, by the sine of the elevation, in linear light. */
    private fun gradient(): FloatArray {
        val stops = if (!dark) listOf(
            -90f to linear(0.74f, 0.07f, hues.zenith),
            -12f to linear(0.87f, 0.05f, hues.horizon),
            0f to linear(0.93f, 0.045f, hues.horizon),
            12f to linear(0.81f, 0.075f, (hues.zenith + hues.horizon) / 2),
            35f to linear(0.67f, 0.10f, hues.zenith),
            90f to linear(0.58f, 0.11f, hues.zenith),
        ) else listOf(
            -90f to linear(0.10f, 0.03f, hues.zenith),
            -12f to linear(0.24f, 0.05f, hues.horizon),
            0f to linear(0.30f, 0.06f, hues.horizon),
            12f to linear(0.21f, 0.055f, (hues.zenith + hues.horizon) / 2),
            35f to linear(0.15f, 0.04f, hues.zenith),
            90f to linear(0.12f, 0.03f, hues.zenith),
        )
        val out = FloatArray(GRADIENT * 3)
        for (i in 0 until GRADIENT) {
            val y = i / (GRADIENT - 1f) * 2f - 1f
            val e = Math.toDegrees(asin(y.coerceIn(-1f, 1f).toDouble())).toFloat()
            val k = stops.indexOfFirst { it.first >= e }.coerceIn(1, stops.size - 1)
            val (e0, c0) = stops[k - 1]
            val (e1, c1) = stops[k]
            val f = smoothstep(0f, 1f, ((e - e0) / (e1 - e0)).coerceIn(0f, 1f))
            for (ch in 0..2) out[i * 3 + ch] = c0[ch] + (c1[ch] - c0[ch]) * f
        }
        return out
    }

    private companion object {
        const val GRADIENT = 2048
        val SUN_CORE = floatArrayOf(1.25f, 1.2f, 1.05f)
        val SUN_RIM = floatArrayOf(1.1f, 0.98f, 0.72f)

        /** The aurora's curtains: azimuth of the middle, half-width, height of the foot, brightness. */
        val AURORA = listOf(
            floatArrayOf(-1.5f, 1.1f, 0.08f, 1f),
            floatArrayOf(-1.2f, 0.8f, 0.14f, 0.55f),
            floatArrayOf(2.1f, 0.9f, 0.1f, 0.85f),
        )
    }
}

/**
 * The direction through a point of a face, in the game's layout, with y up. [u] runs left to
 * right and [v] top to bottom across the image.
 */
private fun faceDirection(face: Int, u: Float, v: Float): FloatArray {
    val a = 2f * u - 1f
    val b = 2f * v - 1f
    // The cube's corners as the game lays each face's image over them; its y points down.
    val (x, y, z) = when (face) {
        0 -> Triple(a, b, 1f)
        1 -> Triple(1f, b, -a)
        2 -> Triple(-a, b, -1f)
        3 -> Triple(-1f, b, a)
        4 -> Triple(a, -1f, b)
        else -> Triple(a, 1f, -b)
    }
    return normalize(x, -y, z)
}

private fun direction(azimuth: Float, elevation: Float) =
    floatArrayOf(sin(azimuth) * cos(elevation), sin(elevation), cos(azimuth) * cos(elevation))

private fun linear(l: Float, c: Float, hue: Float): FloatArray = toLinearSrgb(l, c, hue).map { it.coerceIn(0f, 1f) }.toFloatArray()

private val SRGB = IntArray(4097) { (encode(it / 4096f) * 255f + 0.5f).toInt().coerceIn(0, 255) }

private fun pack(r: Float, g: Float, b: Float): Int {
    fun ch(x: Float) = SRGB[(x.coerceIn(0f, 1f) * 4096f).toInt()]
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

private fun normalize(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z)
    return floatArrayOf(x / l, y / l, z / l)
}

private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
)

private fun pow4(x: Float): Float { val x2 = x * x; return x2 * x2 }

private fun pow(x: Float, n: Int): Float {
    var r = 1f
    var b = x
    var e = n
    while (e > 0) { if (e and 1 == 1) r *= b; b *= b; e = e shr 1 }
    return r
}

/** Signed angle from [b] to [a], within -π..π. */
private fun angleBetween(a: Float, b: Float): Float {
    var x = a - b
    while (x > Math.PI) x -= (2 * Math.PI).toFloat()
    while (x < -Math.PI) x += (2 * Math.PI).toFloat()
    return x
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

private fun hash(x: Int, y: Int, z: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

/** Smooth value noise, 0..1. */
private fun noise(x: Float, y: Float, seed: Int): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = x - x0
    val fy = y - y0
    val u = fx * fx * (3 - 2 * fx)
    val v = fy * fy * (3 - 2 * fy)
    val a = hash(x0, y0, 0, seed) + (hash(x0 + 1, y0, 0, seed) - hash(x0, y0, 0, seed)) * u
    val b = hash(x0, y0 + 1, 0, seed) + (hash(x0 + 1, y0 + 1, 0, seed) - hash(x0, y0 + 1, 0, seed)) * u
    return a + (b - a) * v
}

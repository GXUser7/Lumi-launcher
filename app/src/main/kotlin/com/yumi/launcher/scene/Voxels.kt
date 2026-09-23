package com.yumi.launcher.scene

import kotlin.math.floor
import kotlin.math.sqrt

/*
 * The block world behind the home screen: a sparse voxel map turned once into a flat list of
 * visible faces. Nothing here runs per frame — the renderer only projects what this produces.
 */

// ---------- noise ----------

internal fun hash(x: Int, y: Int, seed: Int): Float {
    var h = (x * 374761393) xor (y * 668265263) xor ((seed + 1) * 1442695041)
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
}

internal fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3 - 2 * xf)
    val v = yf * yf * (3 - 2 * yf)
    val a = hash(xi, yi, seed)
    val b = hash(xi + 1, yi, seed)
    val c = hash(xi, yi + 1, seed)
    val d = hash(xi + 1, yi + 1, seed)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

internal fun fbm(x: Float, y: Float, seed: Int): Float =
    valueNoise(x, y, seed) * 0.55f + valueNoise(x * 2.1f, y * 2.1f, seed + 7) * 0.3f +
        valueNoise(x * 4.3f, y * 4.3f, seed + 13) * 0.15f

/** mulberry32: small, seedable, and the same world on every launch. */
internal class Rng(seed: Int) {
    private var state = seed
    fun next(): Float {
        state += 0x6D2B79F5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

// ---------- blocks ----------

private fun rgb(argb: Long) = floatArrayOf(((argb shr 16) and 0xFF).toFloat(), ((argb shr 8) and 0xFF).toFloat(), (argb and 0xFF).toFloat())

/**
 * A block's look. [sideTop] is the strip of grass colour along the top of a grass block's side,
 * which is most of what makes a flat-shaded cube read as Minecraft grass.
 */
internal enum class Block(
    top: Long,
    side: Long = top,
    bottom: Long = side,
    sideTop: Long? = null,
    val alpha: Float = 1f,
    val emit: Float = 0f,
    val jitter: Float = 0.07f,
    val seeThrough: Boolean = false,
) {
    Grass(0xFF78B04A, 0xFF8A6642, 0xFF7A5A3A, sideTop = 0xFF699C40),
    Dirt(0xFF8A6642),
    Stone(0xFF8B8E92),
    Deep(0xFF565861),
    Sand(0xFFE0D39E),
    Log(0xFFB08A58, 0xFF6D4F31),
    CherryLog(0xFF8F5B54, 0xFF4A2F2C),
    Leaves(0xFF3F7F30, jitter = 0.16f, seeThrough = true),
    Cherry(0xFFF3A8C8, jitter = 0.1f, seeThrough = true),
    CherryDeep(0xFFE888B4, jitter = 0.1f, seeThrough = true),
    Water(0xFF3B76E0, alpha = 0.74f, jitter = 0.02f, seeThrough = true),
    Cloud(0xFFFFFFFF, alpha = 0.9f, emit = 0.55f, jitter = 0f, seeThrough = true);

    val top = rgb(top)
    val side = rgb(side)
    val bottom = rgb(bottom)
    val sideTop = sideTop?.let(::rgb)

    /** Whether the block darkens corners next to it and stops sunlight. Water and cloud do neither. */
    val occludes: Boolean get() = this != Water && this != Cloud
}

// ---------- world ----------

internal class World {
    private val cells = HashMap<Long, Block>()

    private fun key(x: Int, y: Int, z: Int): Long = ((x + 512L) * 1024 + (y + 512)) * 1024 + (z + 512)

    operator fun get(x: Int, y: Int, z: Int): Block? = cells[key(x, y, z)]
    operator fun set(x: Int, y: Int, z: Int, block: Block) { cells[key(x, y, z)] = block }

    fun forEach(action: (x: Int, y: Int, z: Int, block: Block) -> Unit) {
        for ((k, b) in cells) action((k / 1048576).toInt() - 512, ((k / 1024) % 1024).toInt() - 512, (k % 1024).toInt() - 512, b)
    }
}

/**
 * One visible quad. [corners] are four points in world space. Light is kept per corner so the
 * renderer can shade smoothly across a face: [ao] is how enclosed each corner is, [sun] and [moon]
 * how much of each light reaches it. Clouds carry a [driftBase] so a whole cell slides together.
 */
internal class Face(
    val corners: FloatArray,
    val normal: FloatArray,
    val base: FloatArray,
    val alpha: Float,
    val emit: Float,
) {
    val cx = (corners[0] + corners[3] + corners[6] + corners[9]) / 4
    val cy = (corners[1] + corners[4] + corners[7] + corners[10]) / 4
    val cz = (corners[2] + corners[5] + corners[8] + corners[11]) / 4
    var shade = 1f
    var drifts = false
    var driftBase = 0f
    var water = false
    val ao = floatArrayOf(1f, 1f, 1f, 1f)
    val sun = floatArrayOf(1f, 1f, 1f, 1f)
    val moon = floatArrayOf(1f, 1f, 1f, 1f)
}

private class Dir(val normal: FloatArray, val quad: IntArray)

private val DIRS = arrayOf(
    Dir(floatArrayOf(0f, 1f, 0f), intArrayOf(0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1)),
    Dir(floatArrayOf(0f, -1f, 0f), intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0)),
    Dir(floatArrayOf(1f, 0f, 0f), intArrayOf(1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1)),
    Dir(floatArrayOf(-1f, 0f, 0f), intArrayOf(0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0)),
    Dir(floatArrayOf(0f, 0f, 1f), intArrayOf(0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1)),
    Dir(floatArrayOf(0f, 0f, -1f), intArrayOf(0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0)),
)

/**
 * Every face of [world] that something could see: a face against an opaque neighbour, or against
 * its own kind, is dropped. Corners are pushed out by [inflate] so that neighbouring quads overlap
 * a hair — without it the sky shows through the seams between them as a hairline grid.
 */
internal fun World.faces(
    out: MutableList<Face>,
    scale: FloatArray = floatArrayOf(1f, 1f, 1f),
    offset: FloatArray = floatArrayOf(0f, 0f, 0f),
    inflate: Float = 0.03f,
    drift: Boolean = false,
    shade: ((Float) -> Float)? = null,
    occlusion: Boolean = false,
) {
    forEach { x, y, z, block ->
        val tint = 1 + (hash(x * 3 + 1, z * 5 - y * 7, 17) - 0.5f) * 2 * block.jitter
        for ((d, dir) in DIRS.withIndex()) {
            val n = this[x + dir.normal[0].toInt(), y + dir.normal[1].toInt(), z + dir.normal[2].toInt()]
            if (n != null && (n == block || !n.seeThrough)) continue
            val base = when (d) { 0 -> block.top; 1 -> block.bottom; else -> block.side }
            val strips = if (d >= 2 && block.sideTop != null) listOf(Triple(0f, 0.8f, base), Triple(0.8f, 1f, block.sideTop)) else listOf(Triple(0f, 1f, base))
            for ((y0, y1, colour) in strips) {
                val p = FloatArray(12)
                for (i in 0 until 12 step 3) {
                    val qy = if (d >= 2) y0 + dir.quad[i + 1] * (y1 - y0) else dir.quad[i + 1].toFloat()
                    p[i] = (x + dir.quad[i]) * scale[0] + offset[0]
                    p[i + 1] = (y + qy) * scale[1] + offset[1]
                    p[i + 2] = (z + dir.quad[i + 2]) * scale[2] + offset[2]
                }
                inflateQuad(p, inflate)
                val face = Face(p, dir.normal, floatArrayOf(colour[0] * tint, colour[1] * tint, colour[2] * tint), block.alpha, block.emit)
                if (drift) { face.drifts = true; face.driftBase = x * scale[0] + offset[0] }
                if (shade != null) face.shade = shade(face.cy)
                face.water = block == Block.Water
                if (occlusion) for (k in 0 until 4) {
                    val qx = dir.quad[k * 3]
                    val qz = dir.quad[k * 3 + 2]
                    face.ao[k] = if (d >= 2) {
                        // A side strip's corner sits part-way up the block: blend the corners below and above.
                        val t = y0 + dir.quad[k * 3 + 1] * (y1 - y0)
                        cornerLight(x, y, z, dir.normal, qx, 0, qz) * (1 - t) + cornerLight(x, y, z, dir.normal, qx, 1, qz) * t
                    } else cornerLight(x, y, z, dir.normal, qx, dir.quad[k * 3 + 1], qz)
                }
                out += face
            }
        }
    }
}

/** Minecraft's smooth-lighting levels for zero to three enclosing neighbours. */
private val AO_LEVELS = floatArrayOf(0.5f, 0.68f, 0.84f, 1f)

/**
 * How lit a face corner is by its surroundings, the way Minecraft's smooth lighting does it: the
 * two blocks beside the corner and the one diagonal to it, in the layer the face looks into. Two
 * sides closed means the corner is fully enclosed whatever the diagonal says.
 */
private fun World.cornerLight(x: Int, y: Int, z: Int, normal: FloatArray, qx: Int, qy: Int, qz: Int): Float {
    val nx = normal[0].toInt()
    val ny = normal[1].toInt()
    val nz = normal[2].toInt()
    // The two axes along the face, pointing towards this corner.
    val sx = if (nx != 0) 0 else if (qx == 1) 1 else -1
    val sy = if (ny != 0) 0 else if (qy == 1) 1 else -1
    val sz = if (nz != 0) 0 else if (qz == 1) 1 else -1
    val bx = x + nx
    val by = y + ny
    val bz = z + nz
    fun solid(dx: Int, dy: Int, dz: Int) = this[bx + dx, by + dy, bz + dz]?.occludes == true
    val side1: Boolean
    val side2: Boolean
    when {
        nx != 0 -> { side1 = solid(0, sy, 0); side2 = solid(0, 0, sz) }
        ny != 0 -> { side1 = solid(sx, 0, 0); side2 = solid(0, 0, sz) }
        else -> { side1 = solid(sx, 0, 0); side2 = solid(0, sy, 0) }
    }
    val corner = solid(sx, sy, sz)
    val level = if (side1 && side2) 0 else 3 - (if (side1) 1 else 0) - (if (side2) 1 else 0) - (if (corner) 1 else 0)
    return AO_LEVELS[level]
}

/**
 * Shadows, baked once per light: from each corner of each face — pulled a little towards the
 * centre so neighbouring faces do not share a sample — a ray walks the voxel grid towards the
 * light. Per-corner results give soft edges for free, since the renderer blends them across the face.
 * Only [faces] belonging to this world are shaded; drifting clouds are left out, a cast shadow
 * would not follow them.
 */
internal fun World.castShadows(faces: List<Face>, sun: FloatArray, moon: FloatArray) {
    val top = maxY() + 1
    for (f in faces) {
        for (k in 0 until 4) {
            val px = f.cx + (f.corners[k * 3] - f.cx) * 0.7f + f.normal[0] * 0.02f
            val py = f.cy + (f.corners[k * 3 + 1] - f.cy) * 0.7f + f.normal[1] * 0.02f
            val pz = f.cz + (f.corners[k * 3 + 2] - f.cz) * 0.7f + f.normal[2] * 0.02f
            if (f.normal[0] * sun[0] + f.normal[1] * sun[1] + f.normal[2] * sun[2] > 0f && blocked(px, py, pz, sun, top)) f.sun[k] = 0f
            if (f.normal[0] * moon[0] + f.normal[1] * moon[1] + f.normal[2] * moon[2] > 0f && blocked(px, py, pz, moon, top)) f.moon[k] = 0f
        }
    }
}

private fun World.maxY(): Int {
    var max = Int.MIN_VALUE
    forEach { _, y, _, _ -> if (y > max) max = y }
    return max
}

/** Amanatides–Woo grid walk from a point along [dir]; true if an occluding block is in the way. */
private fun World.blocked(ox: Float, oy: Float, oz: Float, dir: FloatArray, top: Int): Boolean {
    var x = floor(ox).toInt()
    var y = floor(oy).toInt()
    var z = floor(oz).toInt()
    val stepX = if (dir[0] > 0) 1 else -1
    val stepY = if (dir[1] > 0) 1 else -1
    val stepZ = if (dir[2] > 0) 1 else -1
    val inf = Float.MAX_VALUE
    val deltaX = if (dir[0] != 0f) kotlin.math.abs(1f / dir[0]) else inf
    val deltaY = if (dir[1] != 0f) kotlin.math.abs(1f / dir[1]) else inf
    val deltaZ = if (dir[2] != 0f) kotlin.math.abs(1f / dir[2]) else inf
    var maxX = if (dir[0] > 0) (x + 1 - ox) * deltaX else if (dir[0] < 0) (ox - x) * deltaX else inf
    var maxY = if (dir[1] > 0) (y + 1 - oy) * deltaY else if (dir[1] < 0) (oy - y) * deltaY else inf
    var maxZ = if (dir[2] > 0) (z + 1 - oz) * deltaZ else if (dir[2] < 0) (oz - z) * deltaZ else inf
    repeat(96) {
        if (maxX < maxY && maxX < maxZ) { x += stepX; maxX += deltaX }
        else if (maxY < maxZ) { y += stepY; maxY += deltaY }
        else { z += stepZ; maxZ += deltaZ }
        if (dir[1] > 0 && y > top) return false
        if (this[x, y, z]?.occludes == true) return true
    }
    return false
}

private fun inflateQuad(p: FloatArray, amount: Float) {
    if (amount == 0f) return
    val cx = (p[0] + p[3] + p[6] + p[9]) / 4
    val cy = (p[1] + p[4] + p[7] + p[10]) / 4
    val cz = (p[2] + p[5] + p[8] + p[11]) / 4
    for (i in 0 until 12 step 3) {
        val dx = p[i] - cx
        val dy = p[i + 1] - cy
        val dz = p[i + 2] - cz
        val l = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 0f } ?: 1f
        p[i] += dx / l * amount
        p[i + 1] += dy / l * amount
        p[i + 2] += dz / l * amount
    }
}

// ---------- shapes ----------

internal fun World.oak(x: Int, y: Int, z: Int, rng: Rng) {
    val trunk = 4 + (rng.next() * 2).toInt()
    for (i in 1..trunk) this[x, y + i, z] = Block.Log
    val top = y + trunk
    for (ly in top - 2..top + 1) {
        val r = if (ly <= top - 1) 2 else 1
        for (dx in -r..r) for (dz in -r..r) {
            val corner = kotlin.math.abs(dx) == r && kotlin.math.abs(dz) == r
            if (corner && (if (r == 1) ly == top + 1 else rng.next() < 0.6f)) continue
            if (dx == 0 && dz == 0 && ly <= top) continue
            if (this[x + dx, ly, z + dz] == null) this[x + dx, ly, z + dz] = Block.Leaves
        }
    }
}

internal fun World.cherry(x: Int, y: Int, z: Int, rng: Rng) {
    val trunk = 4 + (rng.next() * 2).toInt()
    for (i in 1..trunk) this[x + if (i > 3) 1 else 0, y + i, z] = Block.CherryLog
    val cx = x + 1
    val top = y + trunk + 1
    for (ly in top - 2..top + 1) {
        val r = when (ly) { top + 1 -> 1.6f; top - 2 -> 2.2f; else -> 3.2f }
        for (dx in -4..4) for (dz in -4..4) {
            if (sqrt((dx * dx + dz * dz).toFloat()) > r + (rng.next() - 0.5f) * 0.6f) continue
            if (this[cx + dx, ly, z + dz] == null) this[cx + dx, ly, z + dz] = if (rng.next() < 0.55f) Block.Cherry else Block.CherryDeep
        }
    }
    repeat(7) {
        val a = rng.next() * Math.PI * 2
        val hx = Math.round(cx + kotlin.math.cos(a) * 2.6).toInt()
        val hz = Math.round(z + kotlin.math.sin(a) * 2.6).toInt()
        if (this[hx, top - 3, hz] == null) this[hx, top - 3, hz] = Block.CherryDeep
    }
}

/** A flat layer of cloud cells, 4×4 blocks each, that drifts and wraps as one sheet. */
internal fun clouds(out: MutableList<Face>, seed: Int, cells: Int, height: Float, threshold: Float): Drift {
    val world = World()
    for (i in 0 until cells) for (k in 0 until cells) {
        if (fbm(i * 0.22f, k * 0.22f, seed) > threshold) world[i, 0, k] = Block.Cloud
    }
    val size = 4f
    val half = cells * size / 2
    world.faces(out, floatArrayOf(size, 1.3f, size), floatArrayOf(-half, height, -half), inflate = 0.05f, drift = true)
    return Drift(min = -half, width = cells * size, speed = 0.7f)
}

internal class Drift(val min: Float, val width: Float, val speed: Float)

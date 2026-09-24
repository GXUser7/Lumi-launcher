package com.yumi.launcher.scene

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the camera can be sent: the whole island, or one of the places on it. */
enum class SceneSpot { Overview, Forge, Library, Portal, Campfire }

/**
 * Everything the renderer needs to draw one scene. The island's faces are textured and lit per
 * corner; [far] and [clouds] are flat-coloured and hazed with distance. The geometry is shared and
 * the lighting is not: [day] and [night] are two looks over the same faces, and the renderer blends
 * between them, so the theme switch is a sunset rather than a cut.
 */
internal class Scene(
    val world: World,
    val atlas: Atlas,
    val faces: List<Face>,
    val clouds: List<Face>,
    val drift: Drift,
    val far: List<Face>,
    val spots: Map<SceneSpot, CameraPose>,
    val day: Look,
    val night: Look,
    val sun: FloatArray,
    val moon: FloatArray,
    val petals: Petals,
    val emitters: List<Emitter>,
    val glows: List<GlowPoint>,
    val islands: List<FarIsland>,
)

/**
 * A place for the camera: it orbits [target] at [distance], [yaw] around and [pitch] up, with the
 * projection centre moved right by [offsetX] of the width so the subject sits beside the UI card.
 * [orbit] turns it slowly; otherwise it drifts a little, like a camera on a slider. A flight comes
 * in along the place's own line of sight, from [pull] blocks behind the eye and [rise] above it —
 * through a room's open side rather than its roof — and leaves the same way.
 */
internal class CameraPose(
    val target: FloatArray,
    val distance: Float,
    val yaw: Float,
    val pitch: Float,
    val fov: Float = 48f,
    val offsetX: Float = 0.2f,
    val orbit: Boolean = false,
    val pull: Float = 6f,
    val rise: Float = 3f,
)

/** A camera standing at [eye] and looking at [at]. */
internal fun lookFrom(eye: FloatArray, at: FloatArray, fov: Float, pull: Float, rise: Float, offsetX: Float = 0.2f): CameraPose {
    val dx = eye[0] - at[0]
    val dy = eye[1] - at[1]
    val dz = eye[2] - at[2]
    val distance = sqrt(dx * dx + dy * dy + dz * dz)
    return CameraPose(at, distance, yaw = atan2(dx, dz), pitch = asin(dy / distance), fov = fov, offsetX = offsetX, pull = pull, rise = rise)
}

/** One time of day: sky ramp top to horizon, light, haze, how much the clouds glow, how lit the petals are. */
internal class Look(
    val sky: List<Color>,
    val light: Light,
    val fog: Fog,
    val cloudGlow: Float,
    val petalLight: Float,
    /** How strongly torches and lanterns light what is around them. */
    val torches: Float,
)

/**
 * Direct light from [direction] in the colour [tint], and the light of the open sky that fills the
 * shadows, in the colour [sky]: a warm sun over cool blue shadows is most of what makes a sunny
 * scene read as sunny rather than just bright.
 */
internal class Light(
    val direction: FloatArray,
    val ambient: Float,
    val diffuse: Float,
    val tint: FloatArray = floatArrayOf(1f, 1f, 1f),
    val sky: FloatArray = tint,
)
internal class Fog(val color: FloatArray, val start: Float, val end: Float, val max: Float)
internal class Petals(val count: Int, val colors: List<Color>, val size: Float, val box: FloatArray)

/** A source of particles at a place on the island. */
internal class Emitter(val kind: Kind, val x: Float, val y: Float, val z: Float, val spread: Float = 0.3f) {
    enum class Kind { Smoke, Embers, Portal, Enchant, Mist }
}

/** A halo drawn over a light source; [radius] in blocks. */
internal class GlowPoint(val x: Float, val y: Float, val z: Float, val glow: Glow, val radius: Float, val source: Int)

/** A small island far out in the haze, drawn flat; it bobs a little. */
internal class FarIsland(val from: Int, val to: Int, val phase: Float)

internal fun normalize(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z)
    return floatArrayOf(x / l, y / l, z / l)
}

/**
 * Where the light comes from, day and night — the same bearing the disc is drawn at, so shadows
 * fall away from the sun you can see. An afternoon sun: high enough that the ground under a tree
 * or a roof goes clearly darker, low enough that the shadows still reach out and the blocks' sides
 * split into a lit and a dark one.
 */
private val SUN = normalize(0.55f, 0.46f, -0.65f)
private val MOON = normalize(-0.6f, 0.32f, -0.55f)

/** Ground level of the flat places built on: the buildings stand on it. */
private const val G = 2

/** Steps round the island in which [Builder.grow] works out how far the rim has to reach. */
private const val BEARINGS = 360

/**
 * The launcher's island: a cherry grove on a floating rock, with a pond and a waterfall off its east
 * edge, and a place for each part of the launcher — a smithy for mods, a library for packs, a
 * portal for servers, a campfire by the pond for settings. Paths with lanterns join them. Built
 * once, from fixed seeds, so it is the same island on every launch.
 */
internal fun islandScene(): Scene {
    val atlas = paintAtlas()
    val world = World(-34, 34, -34, 28, -34, 34)
    val b = Builder(world, Rng(7))
    b.terrain()
    b.pond(5, 4, 2.7f)
    b.waterfall(5, 4)
    b.forge(-12, 4)
    b.library(-13, -12)
    b.portal(8, -8)
    b.campfire(1, 9)
    b.grow()
    b.paths()
    b.trees()
    b.farm(-3, -13)
    b.plants()
    b.underside()
    b.rocks()
    world.bakeLight()

    val faces = ArrayList<Face>(40_000)
    world.mesh(faces, atlas = atlas)
    // The underside darkens toward its tip, which is most of what makes the island read as hanging.
    for (f in faces) if (f.cy < G - 2) f.shade = (0.66f + (f.cy + 22) / 22 * 0.34f).coerceIn(0.66f, 1f)
    world.castShadows(faces, SUN, MOON)

    val clouds = ArrayList<Face>(4000)
    val drift = clouds(clouds, seed = 11, cells = 28, height = -30f, threshold = 0.47f)
    // Sparse and high: this layer passes behind the top bar, where a solid sheet of white would swallow it.
    clouds(clouds, seed = 23, cells = 28, height = 24f, threshold = 0.7f)

    val far = ArrayList<Face>(6000)
    val islands = farIslands(far, atlas)

    return Scene(
        world = world,
        atlas = atlas,
        faces = faces,
        clouds = clouds,
        drift = drift,
        far = far,
        spots = mapOf(
            SceneSpot.Overview to CameraPose(floatArrayOf(0f, -3f, 0f), distance = 64f, yaw = 0.6f, pitch = 0.2f, fov = 46f, orbit = true, pull = 0f, rise = 6f),
            // In the smithy's open corner, looking across the room: the hearth and its lava at the
            // back, the anvil in the middle, the furnaces on the right, the lantern hanging over it
            // all. It comes in under the beam, past the corner post.
            SceneSpot.Forge to lookFrom(floatArrayOf(-6.4f, G + 3.3f, 9.2f), floatArrayOf(-10f, 3.7f, 5.8f), fov = 68f, pull = 7f, rise = 1f),
            // On the path, looking in through the library's open south side — between its corner
            // posts, at the enchanting table and the shelves behind; it comes in craning down.
            SceneSpot.Library to lookFrom(floatArrayOf(-9f, b.eye(-9f, -0.8f), -0.8f), floatArrayOf(-9.2f, 4.1f, -8.6f), fov = 56f, pull = 2.5f, rise = 5f),
            // Low on the ground in front of the portal, looking up at it.
            SceneSpot.Portal to lookFrom(floatArrayOf(9f, b.eye(9f, 1.5f) - 0.6f, 1.5f), floatArrayOf(9f, 6f, -7.5f), fov = 50f, pull = 5f, rise = 4f),
            SceneSpot.Campfire to CameraPose(floatArrayOf(3f, 3.6f, 7.5f), distance = 11f, yaw = -0.25f, pitch = 0.26f, fov = 50f),
        ),
        // The day sky is kept a step bluer than the glass panels so a white card never sits on white.
        day = Look(
            sky = listOf(Color(0xFF3F8CEB), Color(0xFF7FBBF8), Color(0xFFC4E1FF)),
            // Sunlit tops stay under full brightness, and the shadows are half as bright and bluish,
            // so the day has depth instead of washing out.
            light = Light(SUN, ambient = 0.46f, diffuse = 0.72f, tint = floatArrayOf(1.12f, 1f, 0.84f), sky = floatArrayOf(0.84f, 0.92f, 1.1f)),
            fog = Fog(floatArrayOf(196f, 225f, 255f), start = 40f, end = 170f, max = 0.8f),
            cloudGlow = 1f,
            petalLight = 1f,
            torches = 0.35f,
        ),
        // Moonlight: cold, low, with the clouds dimmed to shapes and Yumi's near-black blue for the sky.
        night = Look(
            sky = listOf(Color(0xFF04070D), Color(0xFF0B1628), Color(0xFF1A2B47)),
            light = Light(MOON, ambient = 0.22f, diffuse = 0.36f, tint = floatArrayOf(0.7f, 0.82f, 1.12f)),
            fog = Fog(floatArrayOf(22f, 37f, 62f), start = 36f, end = 160f, max = 0.85f),
            cloudGlow = 0.2f,
            petalLight = 0.45f,
            torches = 1.25f,
        ),
        sun = SUN,
        moon = MOON,
        petals = Petals(
            count = 90,
            colors = listOf(Color(0xFFF7B3D0), Color(0xFFF39CC2), Color(0xFFFFD1E3)),
            size = 0.14f,
            // Around the big cherry tree's canopy, where the petals would fall from.
            box = floatArrayOf(-5f, 2f, -7f, 7f, 13f, 5f),
        ),
        emitters = b.emitters,
        glows = glowsOf(world),
        islands = islands,
    )
}

/** Halos for every light that deserves one; small lights get small halos. */
private fun glowsOf(world: World): List<GlowPoint> = world.lights.mapIndexedNotNull { i, light ->
    val block = world[light.x, light.y, light.z] ?: return@mapIndexedNotNull null
    val radius = when (block) {
        Block.Lava, Block.LavaCauldron -> 1.6f
        Block.Portal -> 2.2f
        Block.Campfire -> 2.0f
        Block.Lantern, Block.SoulLantern -> 1.3f
        Block.Torch -> 0.9f
        Block.Furnace -> 1.1f
        Block.GlowVine -> 0.55f
        Block.CryingObsidian -> 0.5f
        Block.Glowstone -> 1.6f
        else -> return@mapIndexedNotNull null
    }
    val y = when (block) {
        Block.Torch -> light.y + 0.62f
        Block.Lantern, Block.SoulLantern -> light.y + 0.25f
        Block.LavaCauldron -> light.y + 1.0f
        Block.Campfire -> light.y + 0.5f
        else -> light.y + 0.5f
    }
    GlowPoint(light.x + 0.5f, y, light.z + 0.5f, light.glow, radius, i)
}

/** The island's builder: terrain, then each place, then the finishing touches. */
private class Builder(val w: World, val rng: Rng) {
    val emitters = ArrayList<Emitter>()

    /** Columns of the island's top: height of the grass, and whether something was built there. */
    val tops = HashMap<Long, Int>()
    val reserved = HashSet<Long>()

    fun key(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    fun top(x: Int, z: Int) = tops[key(x, z)]

    /** Where the eye of someone standing on the ground at [x], [z] would be. */
    fun eye(x: Float, z: Float): Float = (top(floor(x).toInt(), floor(z).toInt()) ?: G) + 2.8f

    fun edge(x: Int, z: Int): Float {
        val angle = atan2(z.toFloat(), x.toFloat())
        return 14.8f + (valueNoise(cos(angle) * 2.2f + 9, sin(angle) * 2.2f, 8) - 0.5f) * 4.2f
    }

    fun terrain() {
        for (x in -20..20) for (z in -20..20) {
            val d = hypot(x.toFloat(), z.toFloat())
            val edge = edge(x, z)
            if (d <= edge) column(x, z, height(x, z), depth(x, z, d, edge), Block.Grass)
        }
    }

    /** Rolling ground, with a mound in the middle for the cherry tree. */
    fun height(x: Int, z: Int): Int {
        val mound = (1 - (hypot(x.toFloat(), z + 1f) / 6f)).coerceAtLeast(0f) * 2.2f
        return (1.4f + fbm(x * 0.16f + 2, z * 0.16f + 5, 3) * 2.4f + mound).roundToInt()
    }

    /** How far the rock hangs under a column: deepest in the middle, tapering to the rim at [edge]. */
    fun depth(x: Int, z: Int, d: Float, edge: Float): Int =
        ((1 - d / edge).coerceAtLeast(0f).pow(0.75f) * 17 + valueNoise(x * 0.35f, z * 0.35f, 4) * 3).roundToInt() + 2

    /** A column of island from [depth] below zero up to [top], filling only what is empty. */
    fun column(x: Int, z: Int, top: Int, depth: Int, surface: Block) {
        for (y in -depth..top) {
            if (w[x, y, z] != null) continue
            w[x, y, z] = when {
                y == top -> surface
                y >= top - 2 -> Block.Dirt
                y < -depth * 0.5f -> Block.Deep
                else -> Block.Stone
            }
        }
        tops[key(x, z)] = top
    }

    /** Pad columns past the rim, with the level and surface [pad] wanted there; [grow] decides on them. */
    val beyond = HashMap<Long, Pair<Int, Block>>()

    /**
     * Levels a patch to [y]: its top becomes [surface], anything above goes, anything missing below
     * is filled. The paving frays at its edge — grass takes back some of the outer row and a few
     * of the next — so a place does not sit on a ruled rectangle.
     */
    fun pad(x0: Int, z0: Int, x1: Int, z1: Int, y: Int, surface: Block) {
        for (x in x0..x1) for (z in z0..z1) {
            val ring = minOf(x - x0, x1 - x, z - z0, z1 - z)
            val fray = when (ring) {
                0 -> 0.5f
                1 -> 0.15f
                else -> 0f
            }
            val block = if (surface != Block.Grass && hash(x, z, 97) < fray) Block.Grass else surface
            val t = top(x, z)
            if (t == null) {
                beyond[key(x, z)] = y to block
                continue
            }
            for (yy in y + 1..t + 1) w[x, yy, z] = null
            for (yy in y - 3 until y) if (w[x, yy, z] == null) w[x, yy, z] = Block.Dirt
            w[x, y, z] = block
            tops[key(x, z)] = y
            reserved += key(x, z)
        }
    }

    /**
     * Pushes the rim out under whatever a place built past it — a wall, a lantern post. The island
     * bulges there: the rim swells out to a few blocks past the furthest of them and eases back over
     * the bearings around, and the new ground has the terrain's rolling top and an underside that
     * keeps tapering to the tip, so it reads as the island's own shape rather than a shelf stuck on.
     * Paving only goes where something stands on it or well inside the new rim; near the rim the
     * place's patch turns back into grass, so no straight paved edge runs along the drop.
     */
    fun grow() {
        val built = beyond.keys.filter { k -> occupied((k shr 32).toInt(), k.toInt()) }
        if (built.isEmpty()) return
        val reach = FloatArray(BEARINGS)
        for (k in built) {
            val x = (k shr 32).toInt()
            val z = k.toInt()
            val b = bearing(x, z)
            reach[b] = max(reach[b], hypot(x.toFloat(), z.toFloat()) + 3f)
        }
        // A block less for every 4° away from what needs holding.
        val rim = FloatArray(BEARINGS) { b -> (-60..60).maxOf { o -> reach[Math.floorMod(b + o, BEARINGS)] - abs(o) / 4f } }
        for (x in -26..26) for (z in -26..26) {
            val d = hypot(x.toFloat(), z.toFloat())
            val natural = edge(x, z)
            val angle = atan2(z.toFloat(), x.toFloat())
            val edge = max(natural, rim[bearing(x, z)] + (valueNoise(cos(angle) * 3f + 4, sin(angle) * 3f, 23) - 0.5f) * 2f)
            if (edge <= natural || d > edge) continue
            val depth = depth(x, z, d, edge)
            val wanted = beyond.remove(key(x, z))
            val top = top(x, z)
            when {
                wanted != null && (d <= edge - 2.5f || occupied(x, z)) -> {
                    column(x, z, wanted.first, depth, wanted.second)
                    reserved += key(x, z)
                }
                top == null -> column(x, z, height(x, z), depth, Block.Grass)
                else -> {
                    // Ground that was here already: more rock under it, so the underside stays smooth.
                    var bottom = w.minY
                    while (bottom < top && w[x, bottom, z] == null) bottom++
                    for (y in -depth until bottom) w[x, y, z] = if (y < -depth * 0.5f) Block.Deep else Block.Stone
                }
            }
        }
    }

    /** Whether a place built anything on a column, from its floor up. */
    fun occupied(x: Int, z: Int) = (G..G + 4).any { w[x, it, z] != null }

    fun bearing(x: Int, z: Int): Int =
        Math.floorMod((atan2(z.toFloat(), x.toFloat()) / (2 * PI.toFloat()) * BEARINGS).roundToInt(), BEARINGS)

    /** A lantern hanging at [y] on a chain that runs up to whatever is above it — a beam, the roof. */
    fun hang(x: Int, y: Int, z: Int) {
        w[x, y, z] = Block.Lantern
        var c = y + 1
        while (c <= y + 8 && w[x, c, z] == null) w[x, c++, z] = Block.Chain
    }

    fun fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, block: Block?, state: Int = 0) {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) for (y in minOf(y0, y1)..maxOf(y0, y1)) for (z in minOf(z0, z1)..maxOf(z0, z1)) {
            if (block == null) w[x, y, z] = null else w.put(x, y, z, block, state)
        }
    }

    fun pond(cx: Int, cz: Int, radius: Float) {
        for (x in cx - 4..cx + 4) for (z in cz - 4..cz + 4) {
            val d = hypot((x - cx).toFloat(), (z - cz).toFloat())
            val t = top(x, z) ?: continue
            if (d > radius + 1.2f) continue
            if (d <= radius) {
                for (y in G..t + 1) w[x, y, z] = null
                w[x, G - 1, z] = Block.Water
                w[x, G - 2, z] = if (d > radius - 0.8f) Block.Sand else Block.Gravel
                tops[key(x, z)] = G - 1
            } else {
                // A sandy shore.
                for (y in G + 1..t + 1) w[x, y, z] = null
                w[x, G, z] = Block.Sand
                tops[key(x, z)] = G
            }
            reserved += key(x, z)
        }
        // Lily pads and reeds.
        w[cx - 1, G, cz + 1] = Block.LilyPad
        w[cx + 1, G, cz - 1] = Block.LilyPad
        w[cx - 2, G, cz - 1] = Block.Reeds
        w[cx - 2, G + 1, cz - 1] = Block.Reeds
    }

    /** A stream from the pond to the east rim, then a column of water falling past the rock. */
    fun waterfall(cx: Int, cz: Int) {
        var x = cx + 3
        while (top(x, cz) != null) {
            for (z in cz..cz) {
                val t = top(x, z) ?: break
                for (y in G..t + 1) w[x, y, z] = null
                w[x, G - 1, z] = Block.Water
                w[x, G - 2, z] = Block.Stone
                for (side in intArrayOf(z - 1, z + 1)) {
                    val s = top(x, side) ?: continue
                    if (s > G) for (y in G + 1..s) w[x, y, side] = null
                    if (s >= G) { w[x, G, side] = if (rng.next() < 0.5f) Block.MossyCobble else Block.Stone; tops[key(x, side)] = G }
                    reserved += key(x, side)
                }
                tops[key(x, z)] = G - 1
                reserved += key(x, z)
            }
            x++
        }
        for (y in G - 1 downTo -30) w[x, y, cz] = Block.Fall
        emitters += Emitter(Emitter.Kind.Mist, x + 0.5f, G - 0.5f, cz + 0.5f, 0.6f)
    }

    /**
     * The smithy — the mods place: open at the front and on the east, a stone back wall with the
     * hearth and its chimney, lava glowing in the hearth, furnaces, an anvil in the middle, and a
     * steep spruce roof. [x0], [z0] is its north-west corner.
     */
    fun forge(x0: Int, z0: Int) {
        val x1 = x0 + 7
        val z1 = z0 + 5
        pad(x0 - 1, z0 - 1, x1 + 1, z1 + 2, G, Block.Cobble)
        fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1, Block.StoneBricks)
        // Corner posts.
        for ((x, z) in listOf(x0 to z0, x1 to z0, x0 to z1, x1 to z1)) fill(x, G + 1, z, x, G + 4, z, Block.OakLog)
        // Back wall and west wall of cobble, part mossy.
        for (x in x0 + 1 until x1) for (y in G + 1..G + 3) w[x, y, z0] = if (rng.next() < 0.25f) Block.MossyCobble else Block.Cobble
        for (z in z0 + 1 until z1) for (y in G + 1..G + 3) w[x0, y, z] = if (rng.next() < 0.25f) Block.MossyCobble else Block.Cobble
        w[x0, G + 2, z0 + 3] = null
        w.put(x0, G + 2, z0 + 3, Block.Glass, 1)
        // The hearth: lava in brick basins against the back wall, a hood and a chimney above.
        fill(x0 + 1, G + 1, z0 + 1, x0 + 2, G + 1, z0 + 1, Block.LavaCauldron)
        fill(x0 + 1, G + 3, z0 + 1, x0 + 2, G + 3, z0 + 1, Block.Bricks)
        fill(x0 + 1, G + 1, z0, x0 + 2, G + 9, z0, Block.Bricks)
        fill(x0 + 1, G + 2, z0 + 1, x0 + 2, G + 2, z0 + 1, null)
        emitters += Emitter(Emitter.Kind.Smoke, x0 + 2f, G + 10f, z0 + 0.5f, 0.5f)
        emitters += Emitter(Emitter.Kind.Embers, x0 + 2f, G + 2.1f, z0 + 1.5f, 0.8f)
        // Furnaces and a blast furnace look-alike stacked on the back wall.
        w.put(x0 + 4, G + 1, z0 + 1, Block.Furnace, Facing.SOUTH)
        w.put(x0 + 5, G + 1, z0 + 1, Block.Furnace, Facing.SOUTH)
        w.put(x0 + 4, G + 2, z0 + 1, Block.Furnace, Facing.SOUTH)
        w[x0 + 6, G + 1, z0 + 1] = Block.Barrel
        w[x0 + 6, G + 2, z0 + 1] = Block.Barrel
        // Anvil in the middle, the tables on the west side, a chest and hay outside.
        w.put(x0 + 3, G + 1, z0 + 3, Block.Anvil, 1)
        w[x0 + 1, G + 1, z0 + 3] = Block.Smithing
        w[x0 + 1, G + 1, z0 + 4] = Block.Crafting
        w[x1 - 1, G + 1, z1 - 1] = Block.Chest
        w[x1 + 1, G + 1, z1] = Block.Hay
        w[x1 + 1, G + 1, z1 + 1] = Block.Hay
        w[x1 + 1, G + 2, z1] = Block.Hay
        // Torches at the entrance, on fence posts.
        for (x in intArrayOf(x0 - 1, x1 + 1)) {
            w[x, G + 1, z1 + 2] = Block.Fence
            w[x, G + 2, z1 + 2] = Block.Torch
        }
        roofAlongX(x0 - 1, x1 + 1, z0 - 1, z1 + 1, G + 5, Block.SpruceStairs, Block.SprucePlanks)
        // Beams along the top of the walls hold the roof.
        fill(x0, G + 4, z0, x1, G + 4, z0, Block.OakLog, 1)
        fill(x0, G + 4, z1, x1, G + 4, z1, Block.OakLog, 1)
        fill(x0, G + 4, z0 + 1, x0, G + 4, z1 - 1, Block.OakLog, 2)
        fill(x1, G + 4, z0 + 1, x1, G + 4, z1 - 1, Block.OakLog, 2)
        // A lantern on a chain from the ridge, over the smithing table's side of the room — where
        // it lights the tables without hanging in front of the view from the open corner.
        hang(x0 + 2, G + 3, z0 + 3)
        reserve(x0 - 2, z0 - 2, x1 + 2, z1 + 3)
    }

    /**
     * A gable roof running east–west over [x0]..[x1]: stair rows climbing from both eaves to a ridge
     * of planks, the back of each stair towards the ridge.
     */
    fun roofAlongX(x0: Int, x1: Int, z0: Int, z1: Int, y: Int, stairs: Block, ridge: Block) {
        var north = z0
        var south = z1
        var level = y
        while (south - north > 1) {
            fill(x0, level, north, x1, level, north, stairs, Facing.SOUTH)
            fill(x0, level, south, x1, level, south, stairs, Facing.NORTH)
            north++
            south--
            level++
        }
        fill(x0, level, north, x1, level, south, ridge)
    }

    fun roofAlongZ(x0: Int, x1: Int, z0: Int, z1: Int, y: Int, stairs: Block, ridge: Block) {
        var west = x0
        var east = x1
        var level = y
        while (east - west > 1) {
            fill(west, level, z0, west, level, z1, stairs, Facing.EAST)
            fill(east, level, z0, east, level, z1, stairs, Facing.WEST)
            west++
            east--
            level++
        }
        fill(west, level, z0, east, level, z1, ridge)
    }

    /**
     * The library — the packs place: walls of bookshelves on the north and east, open to the west
     * and the south, an enchanting table with its book floating over a red carpet, lanterns, and a
     * cherry roof. [x0], [z0] is its north-west corner.
     */
    fun library(x0: Int, z0: Int) {
        val x1 = x0 + 7
        val z1 = z0 + 7
        pad(x0 - 3, z0 - 1, x1 + 1, z1 + 2, G, Block.StoneBricks)
        fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, Block.OakPlanks)
        for ((x, z) in listOf(x0 to z0, x1 to z0, x0 to z1, x1 to z1)) fill(x, G + 1, z, x, G + 4, z, Block.CherryLog)
        for (z in z0 + 1 until z1) for (y in G + 1..G + 3) w[x1, y, z] = Block.Bookshelf
        for (x in x0 + 1 until x1) for (y in G + 1..G + 3) w[x, y, z0] = Block.Bookshelf
        // A window in each wall.
        w.put(x1, G + 2, z0 + 4, Block.Glass, 1)
        w.put(x0 + 3, G + 2, z0, Block.Glass, 0)
        for (x in x0 + 3..x0 + 5) for (z in z0 + 3..z0 + 5) w[x, G + 1, z] = Block.RedCarpet
        w[x0 + 4, G + 1, z0 + 4] = Block.Enchanting
        w[x0 + 4, G + 2, z0 + 4] = Block.Book
        emitters += Emitter(Emitter.Kind.Enchant, x0 + 4.5f, G + 2.4f, z0 + 4.5f, 2.2f)
        w[x1 - 1, G + 1, z0 + 1] = Block.Chest
        w[x1 - 1, G + 1, z1 - 1] = Block.Barrel
        w[x1 - 1, G + 2, z1 - 1] = Block.Lantern
        w[x0 + 1, G + 1, z0 + 1] = Block.Bookshelf
        w[x0 + 1, G + 2, z0 + 1] = Block.Lantern
        // Beams, then a pink roof over it all.
        fill(x0, G + 4, z0, x0, G + 4, z1, Block.OakLog, 2)
        fill(x1, G + 4, z0, x1, G + 4, z1, Block.OakLog, 2)
        fill(x0 + 1, G + 4, z0, x1 - 1, G + 4, z0, Block.OakLog, 1)
        fill(x0 + 1, G + 4, z1, x1 - 1, G + 4, z1, Block.OakLog, 1)
        roofAlongZ(x0 - 1, x1 + 1, z0 - 1, z1 + 1, G + 5, Block.CherryStairs, Block.CherryPlanks)
        hang(x0 + 4, G + 3, z0 + 2)
        // Lanterns on posts on the open side.
        for (z in intArrayOf(z0 + 1, z1 - 1)) {
            w[x0 - 2, G + 1, z] = Block.Fence
            w[x0 - 2, G + 2, z] = Block.Lantern
        }
        reserve(x0 - 4, z0 - 2, x1 + 2, z1 + 3)
    }

    /**
     * The portal — the servers place: a raised blackstone platform near the north-east rim with an
     * obsidian frame and a lit portal in it, crying obsidian, and soul lanterns on either side.
     */
    fun portal(cx: Int, cz: Int) {
        pad(cx - 4, cz - 3, cx + 5, cz + 3, G, Block.BlackstoneBricks)
        fill(cx - 3, G + 1, cz - 1, cx + 4, G + 1, cz + 1, Block.Blackstone)
        fill(cx - 2, G + 1, cz + 2, cx + 3, G + 1, cz + 2, Block.BrickSlab)
        val x0 = cx - 1
        val x1 = cx + 2
        val y0 = G + 2
        val y1 = G + 6
        fill(x0, y0, cz, x1, y0, cz, Block.Obsidian)
        fill(x0, y1, cz, x1, y1, cz, Block.Obsidian)
        fill(x0, y0, cz, x0, y1, cz, Block.Obsidian)
        fill(x1, y0, cz, x1, y1, cz, Block.Obsidian)
        w[x0, y0 + 2, cz] = Block.CryingObsidian
        w[x1, y1, cz] = Block.CryingObsidian
        w[x0 + 1, y0, cz] = Block.CryingObsidian
        fill(x0 + 1, y0 + 1, cz, x1 - 1, y1 - 1, cz, Block.Portal, 0)
        emitters += Emitter(Emitter.Kind.Portal, cx + 0.5f, G + 4.5f, cz + 0.5f, 1.4f)
        for (x in intArrayOf(cx - 3, cx + 4)) {
            w[x, G + 2, cz + 1] = Block.Fence
            w[x, G + 3, cz + 1] = Block.SoulLantern
        }
        // A broken-off pillar and loose obsidian, as if the portal was found rather than built.
        fill(cx + 4, G + 2, cz - 1, cx + 4, G + 3, cz - 1, Block.Obsidian)
        w[cx - 3, G + 2, cz - 1] = Block.CryingObsidian
        reserve(cx - 5, cz - 4, cx + 6, cz + 4)
    }

    /** The campfire by the pond — the settings place: a fire, logs to sit on, a lantern. */
    fun campfire(cx: Int, cz: Int) {
        pad(cx - 2, cz - 2, cx + 2, cz + 2, G, Block.Grass)
        w[cx, G, cz] = Block.Gravel
        w[cx, G + 1, cz] = Block.Campfire
        emitters += Emitter(Emitter.Kind.Smoke, cx + 0.5f, G + 1.8f, cz + 0.5f, 0.2f)
        emitters += Emitter(Emitter.Kind.Embers, cx + 0.5f, G + 1.5f, cz + 0.5f, 0.3f)
        fill(cx - 1, G + 1, cz + 2, cx + 1, G + 1, cz + 2, Block.OakLog, 1)
        fill(cx - 2, G + 1, cz - 1, cx - 2, G + 1, cz + 1, Block.OakLog, 2)
        w[cx + 2, G + 1, cz - 2] = Block.Barrel
        w[cx + 2, G + 2, cz - 2] = Block.Lantern
        reserve(cx - 3, cz - 3, cx + 3, cz + 3)
    }

    fun reserve(x0: Int, z0: Int, x1: Int, z1: Int) {
        for (x in x0..x1) for (z in z0..z1) reserved += key(x, z)
    }

    /** Paths of trodden dirt from the middle to each place, lanterns on posts along them. */
    fun paths() {
        val routes = listOf(
            listOf(0f to 3f, -3f to 7f, -8f to 11f),
            listOf(0f to -2f, -4f to -3f, -9f to -3f),
            listOf(2f to -3f, 5f to -4f, 8f to -4f),
            listOf(1f to 3f, 1f to 6f),
        )
        for (route in routes) {
            for (i in 0 until route.size - 1) {
                val (ax, az) = route[i]
                val (bx, bz) = route[i + 1]
                val steps = (hypot(bx - ax, bz - az) * 3).toInt().coerceAtLeast(1)
                for (s in 0..steps) {
                    val t = s / steps.toFloat()
                    val px = ax + (bx - ax) * t
                    val pz = az + (bz - az) * t
                    for ((dx, dz) in listOf(0 to 0, 1 to 0, 0 to 1)) {
                        val x = px.roundToInt() + dx
                        val z = pz.roundToInt() + dz
                        val top = top(x, z) ?: continue
                        if (w[x, top, z] == Block.Grass) {
                            w[x, top, z] = if (rng.next() < 0.1f) Block.Gravel else Block.Path
                            reserved += key(x, z)
                        }
                    }
                }
            }
        }
        for ((x, z) in listOf(-2 to 4, -1 to -6, 5 to -2, 2 to 7)) lanternPost(x, z)
    }

    fun lanternPost(x: Int, z: Int) {
        val t = top(x, z) ?: return
        w[x, t + 1, z] = Block.Fence
        w[x, t + 2, z] = Block.Fence
        w[x, t + 3, z] = Block.Lantern
        reserved += key(x, z)
    }

    fun trees() {
        // Placed around the camera's views of the places, not in them.
        cherry(0, -1, big = true)
        cherry(7, 12, big = false)
        oak(12, 7)
        oak(-13, 1)
        birch(10, 10)
        birch(-15, -5)
        for ((x, z) in listOf(5 to 13, -6 to 1)) bush(x, z)
    }

    fun cherry(x: Int, z: Int, big: Boolean) {
        val y = top(x, z) ?: return
        val trunk = if (big) 6 else 4
        for (i in 1..trunk) w[x + if (i > trunk - 2) 1 else 0, y + i, z] = Block.CherryLog
        val cx = x + 1
        val top = y + trunk + 1
        val reach = if (big) 1.35f else 1f
        for (ly in top - 2..top + 2) {
            val r = when (ly) {
                top + 2 -> 1.8f
                top + 1 -> 3.2f
                top - 2 -> 2.6f
                else -> 4.2f
            } * reach
            for (dx in -6..6) for (dz in -6..6) {
                if (sqrt((dx * dx + dz * dz).toFloat()) > r + (rng.next() - 0.5f) * 0.9f) continue
                if (w[cx + dx, ly, z + dz] == null) w[cx + dx, ly, z + dz] = Block.Cherry
            }
        }
        // Blossom hanging from the wide layer, where it overhangs the narrower one under it.
        val wide = top - 1
        for (dx in -6..6) for (dz in -6..6) {
            val hx = cx + dx
            val hz = z + dz
            if (w[hx, wide, hz] != Block.Cherry || w[hx, wide - 1, hz] != null || rng.next() > 0.3f) continue
            w[hx, wide - 1, hz] = Block.Cherry
            if (rng.next() < 0.35f && w[hx, wide - 2, hz] == null) w[hx, wide - 2, hz] = Block.Cherry
        }
        // Fallen petals on the grass around it.
        for (dx in -5..5) for (dz in -5..5) {
            val gx = cx + dx
            val gz = z + dz
            val t = top(gx, gz) ?: continue
            if (w[gx, t, gz] == Block.Grass && w[gx, t + 1, gz] == null && rng.next() < 0.3f) w[gx, t + 1, gz] = Block.Petals
        }
        reserved += key(x, z)
        reserved += key(x + 1, z)
    }

    fun oak(x: Int, z: Int) {
        val y = top(x, z) ?: return
        if (key(x, z) in reserved) return
        val trunk = 4 + (rng.next() * 2).toInt()
        for (i in 1..trunk) w[x, y + i, z] = Block.OakLog
        val top = y + trunk
        for (ly in top - 2..top + 1) {
            val r = if (ly <= top - 1) 2 else 1
            for (dx in -r..r) for (dz in -r..r) {
                val corner = abs(dx) == r && abs(dz) == r
                if (corner && (if (r == 1) ly == top + 1 else rng.next() < 0.6f)) continue
                if (dx == 0 && dz == 0 && ly <= top) continue
                if (w[x + dx, ly, z + dz] == null) w[x + dx, ly, z + dz] = Block.Leaves
            }
        }
        reserved += key(x, z)
    }

    fun birch(x: Int, z: Int) {
        val y = top(x, z) ?: return
        if (key(x, z) in reserved) return
        val trunk = 5 + (rng.next() * 2).toInt()
        for (i in 1..trunk) w[x, y + i, z] = Block.BirchLog
        val top = y + trunk
        for (ly in top - 2..top + 1) {
            val r = if (ly <= top - 1) 2 else 1
            for (dx in -r..r) for (dz in -r..r) {
                val corner = abs(dx) == r && abs(dz) == r
                if (corner && rng.next() < 0.7f) continue
                if (dx == 0 && dz == 0 && ly <= top) continue
                if (w[x + dx, ly, z + dz] == null) w[x + dx, ly, z + dz] = Block.BirchLeaves
            }
        }
        reserved += key(x, z)
    }

    fun bush(x: Int, z: Int) {
        val y = top(x, z) ?: return
        if (key(x, z) in reserved) return
        w[x, y + 1, z] = Block.Leaves
        if (rng.next() < 0.6f && w[x + 1, y + 1, z] == null && top(x + 1, z) == y) w[x + 1, y + 1, z] = Block.Leaves
        if (rng.next() < 0.5f) w[x, y + 2, z] = Block.Leaves
        reserved += key(x, z)
    }

    /** A little wheat field with a hay bale and pumpkins. */
    fun farm(x0: Int, z0: Int) {
        for (x in x0..x0 + 4) for (z in z0..z0 + 2) {
            val t = top(x, z) ?: continue
            if (key(x, z) in reserved) continue
            for (y in t + 1..t + 2) w[x, y, z] = null
            if (x == x0 + 2 && z == z0 + 1) {
                w[x, t, z] = Block.Water
            } else {
                w[x, t, z] = Block.Farmland
                w[x, t + 1, z] = Block.Wheat
            }
            reserved += key(x, z)
        }
        for ((x, z) in listOf(x0 - 1 to z0 + 1, x0 + 5 to z0)) {
            val t = top(x, z) ?: continue
            if (key(x, z) in reserved) continue
            w[x, t + 1, z] = Block.Pumpkin
            reserved += key(x, z)
        }
    }

    /** Grass tufts and flowers over the untouched grass. */
    fun plants() {
        val flowers = listOf(Block.Poppy, Block.Dandelion, Block.Cornflower, Block.Allium)
        for ((k, t) in tops) {
            if (k in reserved) continue
            val x = (k shr 32).toInt()
            val z = k.toInt()
            if (w[x, t, z] != Block.Grass || w[x, t + 1, z] != null) continue
            val n = rng.next()
            val patch = valueNoise(x * 0.3f, z * 0.3f, 41)
            w[x, t + 1, z] = when {
                n < 0.16f -> Block.TallGrass
                n < 0.16f + 0.1f * patch -> flowers[(valueNoise(x * 0.2f, z * 0.2f, 43) * flowers.size).toInt().coerceIn(0, 3)]
                n > 0.985f -> Block.Mushroom
                else -> null
            }
        }
    }

    /**
     * The hanging side: ores in the exposed stone, moss, roots, and glow berries whose lights are
     * what the island's underside shows at night.
     */
    fun underside() {
        val ores = listOf(Block.CoalOre, Block.CoalOre, Block.IronOre, Block.CopperOre, Block.DiamondOre, Block.Amethyst)
        for (x in -20..20) for (z in -20..20) {
            if (top(x, z) == null) continue
            var low = Int.MAX_VALUE
            for (y in -30..G) if (w[x, y, z] != null) { low = y; break }
            if (low == Int.MAX_VALUE) continue
            // Ores on the surface of the rock.
            for (y in low..G - 3) {
                val block = w[x, y, z]
                if (block != Block.Stone && block != Block.Deep) continue
                val exposed = w[x + 1, y, z] == null || w[x - 1, y, z] == null || w[x, y, z + 1] == null || w[x, y, z - 1] == null || w[x, y - 1, z] == null
                if (exposed && rng.next() < 0.045f) w[x, y, z] = ores[(rng.next() * ores.size).toInt().coerceAtMost(ores.size - 1)]
                else if (exposed && block == Block.Stone && rng.next() < 0.04f) w[x, y, z] = Block.Moss
            }
            val r = rng.next()
            when {
                r < 0.07f -> {
                    val length = 1 + (rng.next() * 3).toInt()
                    for (i in 1..length) w[x, low - i, z] = Block.GlowVine
                }
                r < 0.15f -> w[x, low - 1, z] = Block.Roots
            }
        }
    }

    /** Floating rocks around the island, each with a patch of grass. */
    fun rocks() {
        for ((cx, cy, cz, r) in listOf(
            Rock(-24, 5, -9, 2.8f), Rock(21, -4, 13, 2.4f), Rock(-15, -10, 21, 2f), Rock(18, 8, -18, 1.8f), Rock(-5, -14, -24, 1.6f),
        )) {
            for (ix in -4..4) for (iz in -4..4) {
                val dd = hypot(ix.toFloat(), iz.toFloat())
                if (dd > r) continue
                val depth = ((1 - dd / r) * 4.5f).roundToInt() + 1
                for (iy in -depth..0) {
                    w[cx + ix, cy + iy, cz + iz] = when {
                        iy == 0 -> Block.Grass
                        iy == -1 -> Block.Dirt
                        iy < -depth + 1 && rng.next() < 0.2f -> Block.CoalOre
                        else -> Block.Stone
                    }
                }
                if (dd < r - 0.8f && rng.next() < 0.3f) w[cx + ix, cy + 1, cz + iz] = if (rng.next() < 0.5f) Block.TallGrass else Block.Dandelion
            }
            // Under the deepest point: 4.5 blocks of rock at the centre, plus the grass layer.
            if (rng.next() < 0.8f) w[cx, cy - 7, cz] = Block.GlowVine
        }
    }
}

private data class Rock(val x: Int, val y: Int, val z: Int, val radius: Float)

/**
 * Islands far out in the haze: small, low-detail, flat-coloured, each with a tree or two. They
 * give the sky depth — the launcher's island is one of many.
 */
private fun farIslands(out: MutableList<Face>, atlas: Atlas): List<FarIsland> {
    // Scattered all the way round and high enough to stand against the sky, not the cloud sea.
    val placed = listOf(
        floatArrayOf(-100f, 26f, -55f, 7f),
        floatArrayOf(55f, 32f, -115f, 8f),
        floatArrayOf(-35f, 18f, -135f, 6f),
        floatArrayOf(125f, 22f, 15f, 7f),
        floatArrayOf(-125f, 30f, 55f, 8f),
        floatArrayOf(35f, 14f, 125f, 6f),
        floatArrayOf(95f, 38f, -70f, 5f),
    )
    val islands = ArrayList<FarIsland>()
    for ((i, spec) in placed.withIndex()) {
        val (ox, oy, oz, radius) = spec
        val r = radius.toInt() + 2
        val world = World(-r, r, -14, 10, -r, r)
        val rng = Rng(100 + i)
        for (x in -r..r) for (z in -r..r) {
            val d = hypot(x.toFloat(), z.toFloat())
            val edge = radius + (valueNoise(x * 0.4f + i * 7, z * 0.4f, 9) - 0.5f) * 2f
            if (d > edge) continue
            val h = (fbm(x * 0.25f + i, z * 0.25f, 5) * 2f).roundToInt()
            val depth = ((1 - d / edge).pow(0.8f) * 10).roundToInt() + 1
            for (y in -depth..h) world[x, y, z] = when {
                y == h -> Block.Grass
                y >= h - 1 -> Block.Dirt
                else -> Block.Stone
            }
        }
        repeat(1 + (rng.next() * 2).toInt()) {
            val tx = ((rng.next() - 0.5f) * radius).roundToInt()
            val tz = ((rng.next() - 0.5f) * radius).roundToInt()
            val ty = world.top(tx, tz) ?: return@repeat
            val leaves = if (i % 2 == 0) Block.Cherry else Block.Leaves
            for (y in 1..4) world[tx, ty + y, tz] = if (i % 2 == 0) Block.CherryLog else Block.OakLog
            for (dx in -2..2) for (dz in -2..2) for (dy in 3..5) if (dx * dx + dz * dz + (dy - 4) * (dy - 4) <= 5 && world[tx + dx, ty + dy, tz + dz] == null) world[tx + dx, ty + dy, tz + dz] = leaves
        }
        world.bakeLight()
        val from = out.size
        world.mesh(out, textured = false, atlas = atlas)
        for (j in from until out.size) {
            val f = out[j]
            for (k in 0 until 12 step 3) {
                f.corners[k] += ox
                f.corners[k + 1] += oy
                f.corners[k + 2] += oz
            }
            out[j] = f.moved()
        }
        islands += FarIsland(from, out.size, i * 1.7f)
    }
    return islands
}

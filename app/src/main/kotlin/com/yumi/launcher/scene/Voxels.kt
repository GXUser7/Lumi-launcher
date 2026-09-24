package com.yumi.launcher.scene

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/*
 * The block world behind the launcher: a voxel grid turned once into a flat list of visible faces,
 * each with its texture coordinates and its light worked out per corner the way the game does it —
 * smooth light from the sky and from torches, corners darkened where blocks meet, shadows from the
 * sun and the moon. Nothing here runs per frame; the renderer only projects what this produces.
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

/** How a block is built out of quads. */
internal enum class Shape { Cube, Cross, Slab, Stairs, Torch, Lantern, Chain, Fence, Pane, Anvil, Flat, Campfire, Chest, Book, Log }

/** What moves on a face: scrolling liquids and fire, swaying leaves and plants, the floating book. */
internal enum class Anim { None, Water, Fall, Lava, Portal, Fire, Leaves, Plant, Bob }

/** The colour a light source tints what it lights. */
internal enum class Glow(val r: Float, val g: Float, val b: Float) {
    Warm(1.0f, 0.72f, 0.42f),
    Fire(1.0f, 0.6f, 0.3f),
    Portal(0.72f, 0.42f, 1.0f),
    Soul(0.45f, 0.9f, 1.0f),
    Crystal(0.8f, 0.6f, 1.0f),
}

/**
 * A block type. Tiles are listed top, bottom, then the sides; [light] is the level it gives off
 * (15 like a torch at the source), [emit] how bright its own faces stay in the dark.
 */
internal enum class Block(
    top: Int,
    side: Int = top,
    bottom: Int = top,
    val shape: Shape = Shape.Cube,
    val alpha: Float = 1f,
    val light: Int = 0,
    val glow: Glow = Glow.Warm,
    val emit: Float = 0f,
    val anim: Anim = Anim.None,
    /** Faces touching the same block are not drawn: water in water, glass in glass. */
    val merges: Boolean = false,
    /** Light passes, but loses this much more on the way: leaves and water dim it. */
    val dims: Int = 0,
) {
    Grass(Tile.GRASS_TOP, Tile.GRASS_SIDE, Tile.DIRT),
    Dirt(Tile.DIRT),
    Path(Tile.PATH_TOP, Tile.PATH_SIDE, Tile.DIRT),
    Farmland(Tile.FARMLAND, Tile.DIRT, Tile.DIRT),
    Stone(Tile.STONE),
    Deep(Tile.DEEPSLATE),
    Cobble(Tile.COBBLE),
    MossyCobble(Tile.MOSSY_COBBLE),
    Sand(Tile.SAND),
    Gravel(Tile.GRAVEL),
    Moss(Tile.MOSS),
    CoalOre(Tile.COAL_ORE),
    IronOre(Tile.IRON_ORE),
    CopperOre(Tile.COPPER_ORE),
    DiamondOre(Tile.DIAMOND_ORE),
    Amethyst(Tile.AMETHYST, light = 5, glow = Glow.Crystal, emit = 0.25f),
    OakLog(Tile.OAK_LOG_TOP, Tile.OAK_LOG, Tile.OAK_LOG_TOP, shape = Shape.Log),
    OakPlanks(Tile.OAK_PLANKS),
    SprucePlanks(Tile.SPRUCE_PLANKS),
    CherryLog(Tile.CHERRY_LOG_TOP, Tile.CHERRY_LOG, Tile.CHERRY_LOG_TOP, shape = Shape.Log),
    CherryPlanks(Tile.CHERRY_PLANKS),
    BirchLog(Tile.BIRCH_LOG, shape = Shape.Log),
    Leaves(Tile.OAK_LEAVES, anim = Anim.Leaves, merges = true, dims = 1),
    BirchLeaves(Tile.BIRCH_LEAVES, anim = Anim.Leaves, merges = true, dims = 1),
    Cherry(Tile.CHERRY_LEAVES, anim = Anim.Leaves, merges = true, dims = 1),
    Water(Tile.WATER, alpha = 0.72f, anim = Anim.Water, merges = true, dims = 2),
    Fall(Tile.WATER_FLOW, alpha = 0.78f, anim = Anim.Fall, merges = true, dims = 2),
    Lava(Tile.LAVA, light = 15, glow = Glow.Fire, emit = 1f, anim = Anim.Lava, merges = true),
    Obsidian(Tile.OBSIDIAN),
    CryingObsidian(Tile.CRYING_OBSIDIAN, light = 6, glow = Glow.Portal, emit = 0.3f),
    Portal(Tile.PORTAL, shape = Shape.Pane, light = 11, glow = Glow.Portal, emit = 1f, anim = Anim.Portal, merges = true),
    StoneBricks(Tile.STONE_BRICKS),
    MossyBricks(Tile.MOSSY_BRICKS),
    Bricks(Tile.BRICKS),
    Blackstone(Tile.BLACKSTONE),
    BlackstoneBricks(Tile.BLACKSTONE_BRICKS),
    SmoothStone(Tile.SMOOTH_STONE),
    Glass(Tile.GLASS, shape = Shape.Pane, merges = true),
    Bookshelf(Tile.OAK_PLANKS, Tile.BOOKSHELF, Tile.OAK_PLANKS),
    Crafting(Tile.CRAFTING_TOP, Tile.CRAFTING_SIDE, Tile.OAK_PLANKS),
    Smithing(Tile.SMITHING_TOP, Tile.CRAFTING_SIDE, Tile.OAK_PLANKS),
    Furnace(Tile.FURNACE_TOP, Tile.FURNACE_SIDE, Tile.FURNACE_TOP, light = 13, glow = Glow.Fire),
    Anvil(Tile.ANVIL, shape = Shape.Anvil),
    Lantern(Tile.LANTERN, shape = Shape.Lantern, light = 15, emit = 1f),
    SoulLantern(Tile.SOUL_LANTERN, shape = Shape.Lantern, light = 12, glow = Glow.Soul, emit = 1f),
    Torch(Tile.TORCH, shape = Shape.Torch, light = 14, glow = Glow.Fire),
    Chain(Tile.CHAIN, shape = Shape.Chain),
    Fence(Tile.OAK_PLANKS, shape = Shape.Fence),
    Hay(Tile.HAY_TOP, Tile.HAY_SIDE, Tile.HAY_TOP),
    Barrel(Tile.BARREL_TOP, Tile.BARREL_SIDE, Tile.BARREL_TOP),
    Chest(Tile.CHEST, shape = Shape.Chest),
    TallGrass(Tile.TALL_GRASS, shape = Shape.Cross, anim = Anim.Plant),
    Poppy(Tile.POPPY, shape = Shape.Cross, anim = Anim.Plant),
    Dandelion(Tile.DANDELION, shape = Shape.Cross, anim = Anim.Plant),
    Cornflower(Tile.CORNFLOWER, shape = Shape.Cross, anim = Anim.Plant),
    Allium(Tile.ALLIUM, shape = Shape.Cross, anim = Anim.Plant),
    Wheat(Tile.WHEAT, shape = Shape.Cross, anim = Anim.Plant),
    Mushroom(Tile.MUSHROOM, shape = Shape.Cross),
    Reeds(Tile.REEDS, shape = Shape.Cross, anim = Anim.Plant),
    Roots(Tile.HANGING_ROOTS, shape = Shape.Cross),
    GlowVine(Tile.GLOW_VINE, shape = Shape.Cross, light = 9, emit = 0.55f),
    Petals(Tile.PINK_PETALS, shape = Shape.Flat),
    LilyPad(Tile.LILY_PAD, shape = Shape.Flat),
    RedCarpet(Tile.RED_WOOL, shape = Shape.Flat),
    Enchanting(Tile.ENCHANT_TOP, Tile.ENCHANT_SIDE, Tile.OBSIDIAN, shape = Shape.Slab, light = 7, glow = Glow.Crystal),
    Book(Tile.BOOK, shape = Shape.Book, anim = Anim.Bob),
    Campfire(Tile.CAMPFIRE_LOG, shape = Shape.Campfire, light = 15, glow = Glow.Fire),
    Pumpkin(Tile.PUMPKIN_TOP, Tile.PUMPKIN, Tile.PUMPKIN_TOP),
    Glowstone(Tile.GLOWSTONE, light = 15, emit = 1f),
    LavaCauldron(Tile.LAVA, Tile.CAULDRON, Tile.CAULDRON, light = 15, glow = Glow.Fire),
    RedWool(Tile.RED_WOOL),
    WhiteWool(Tile.WHITE_WOOL),
    CobbleSlab(Tile.COBBLE, shape = Shape.Slab),
    OakSlab(Tile.OAK_PLANKS, shape = Shape.Slab),
    CherrySlab(Tile.CHERRY_PLANKS, shape = Shape.Slab),
    SpruceSlab(Tile.SPRUCE_PLANKS, shape = Shape.Slab),
    BrickSlab(Tile.STONE_BRICKS, shape = Shape.Slab),
    CobbleStairs(Tile.COBBLE, shape = Shape.Stairs),
    SpruceStairs(Tile.SPRUCE_PLANKS, shape = Shape.Stairs),
    CherryStairs(Tile.CHERRY_PLANKS, shape = Shape.Stairs),
    BrickStairs(Tile.STONE_BRICKS, shape = Shape.Stairs),
    Cloud(Tile.CLOUD, alpha = 0.9f, emit = 0.55f, merges = true);

    val top = top
    val side = side
    val bottom = bottom

    /** A whole opaque cube: hides the faces behind it, darkens corners, stops light and shadows. */
    val solid: Boolean = (shape == Shape.Cube || shape == Shape.Log) && alpha >= 1f && anim != Anim.Lava

    /** Lets light through, however dimmed. */
    val clear: Boolean get() = !solid || anim == Anim.Leaves

    /**
     * Hides a glow or a spark behind it. A roof of stairs lets light and shadow rays slip through
     * the gaps a half block leaves, but nobody should see a lantern's halo through it.
     */
    val screens: Boolean get() = solid || shape == Shape.Stairs || shape == Shape.Slab
}

// ---------- world ----------

/**
 * A box of cells with a block and a small state per cell — the facing of stairs and furnaces, the
 * axis of logs and portals. Dense: lookups happen millions of times while light and shadows bake.
 */
internal class World(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val minZ: Int, val maxZ: Int) {
    val sx = maxX - minX + 1
    val sy = maxY - minY + 1
    val sz = maxZ - minZ + 1
    private val blocks = ByteArray(sx * sy * sz)
    private val states = ByteArray(sx * sy * sz)
    val sky = ByteArray(sx * sy * sz)
    val torch = ByteArray(sx * sy * sz)

    /** Which light set [torch] in each cell, so each source can flicker and tint on its own. */
    val source = ShortArray(sx * sy * sz) { -1 }
    val lights = ArrayList<LightSource>()

    fun inside(x: Int, y: Int, z: Int) = x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    fun index(x: Int, y: Int, z: Int) = ((x - minX) * sy + (y - minY)) * sz + (z - minZ)

    operator fun get(x: Int, y: Int, z: Int): Block? {
        if (!inside(x, y, z)) return null
        val id = blocks[index(x, y, z)].toInt()
        return if (id == 0) null else BLOCKS[id - 1]
    }

    operator fun set(x: Int, y: Int, z: Int, block: Block?) {
        if (!inside(x, y, z)) return
        blocks[index(x, y, z)] = (block?.let { it.ordinal + 1 } ?: 0).toByte()
        states[index(x, y, z)] = 0
    }

    fun state(x: Int, y: Int, z: Int): Int = if (inside(x, y, z)) states[index(x, y, z)].toInt() else 0

    fun put(x: Int, y: Int, z: Int, block: Block, state: Int) {
        this[x, y, z] = block
        if (inside(x, y, z)) states[index(x, y, z)] = state.toByte()
    }

    fun solid(x: Int, y: Int, z: Int): Boolean = this[x, y, z]?.solid == true

    inline fun forEach(action: (x: Int, y: Int, z: Int, block: Block) -> Unit) {
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            val block = this[x, y, z] ?: continue
            action(x, y, z, block)
        }
    }

    fun top(x: Int, z: Int): Int? {
        for (y in maxY downTo minY) if (this[x, y, z] != null) return y
        return null
    }

    private companion object {
        val BLOCKS = Block.entries
    }
}

internal class LightSource(val x: Int, val y: Int, val z: Int, val glow: Glow, val flickers: Boolean) {
    val phase = hash(x, y * 31 + z, 77) * 100f
}

/** Facings, for stairs, furnaces and the like: which way the front looks. */
internal object Facing {
    const val NORTH = 0 // -z
    const val EAST = 1 // +x
    const val SOUTH = 2 // +z
    const val WEST = 3 // -x
}

// ---------- faces ----------

/**
 * One visible quad. [corners] are four points in world space, [uv] where they fall in the atlas.
 * Light is kept per corner so the renderer can shade smoothly across the face: [ao] how enclosed the
 * corner is, [sky] and [torch] the light levels reaching it, [sun] and [moon] whether direct light
 * does. [cell] packs the block the face belongs to, which is what the renderer orders faces by.
 */
internal class Face(
    val corners: FloatArray,
    val uv: FloatArray,
    val normal: FloatArray,
    val tile: Int,
    val alpha: Float,
    val emit: Float,
    var anim: Anim,
    val twoSided: Boolean,
    val cellX: Int,
    val cellY: Int,
    val cellZ: Int,
) {
    val cx = (corners[0] + corners[3] + corners[6] + corners[9]) / 4
    val cy = (corners[1] + corners[4] + corners[7] + corners[10]) / 4
    val cz = (corners[2] + corners[5] + corners[8] + corners[11]) / 4
    val ao = floatArrayOf(1f, 1f, 1f, 1f)
    val sky = floatArrayOf(1f, 1f, 1f, 1f)
    val torch = floatArrayOf(0f, 0f, 0f, 0f)
    val source = intArrayOf(-1, -1, -1, -1)
    val sun = floatArrayOf(1f, 1f, 1f, 1f)
    val moon = floatArrayOf(1f, 1f, 1f, 1f)

    /** The flat colour of faces without a texture — clouds, far islands, the lowest quality. */
    var color = 0xFFFFFFFF.toInt()
    var shade = 1f
    var drifts = false
    var driftBase = 0f
}

private class Dir(val nx: Int, val ny: Int, val nz: Int, val quad: IntArray)

/** The six sides: top, bottom, east, west, south, north — corners counter-clockwise seen from outside. */
private val DIRS = arrayOf(
    Dir(0, 1, 0, intArrayOf(0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0)),
    Dir(0, -1, 0, intArrayOf(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1)),
    Dir(1, 0, 0, intArrayOf(1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1)),
    Dir(-1, 0, 0, intArrayOf(0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0)),
    Dir(0, 0, 1, intArrayOf(0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1)),
    Dir(0, 0, -1, intArrayOf(1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0)),
)

/** Minecraft's smooth-lighting levels for zero to three enclosing neighbours. */
private val AO_LEVELS = floatArrayOf(0.5f, 0.68f, 0.84f, 1f)

/**
 * Turns [world] into faces. Cubes lose the faces hidden against their neighbours and get smooth
 * light per corner; the smaller shapes — slabs, torches, lanterns, plants — are built from boxes and
 * crossed quads and take the light of their own cell.
 */
internal fun World.mesh(out: MutableList<Face>, textured: Boolean = true, inflate: Float = 0.02f, atlas: Atlas? = null) {
    val builder = Mesher(this, out, inflate, atlas, textured)
    forEach { x, y, z, block -> builder.block(x, y, z, block) }
}

private class Mesher(val world: World, val out: MutableList<Face>, val inflate: Float, val atlas: Atlas?, val textured: Boolean) {
    fun block(x: Int, y: Int, z: Int, block: Block) {
        val state = world.state(x, y, z)
        when (block.shape) {
            Shape.Cube -> cube(x, y, z, block, state)
            Shape.Log -> cube(x, y, z, block, state)
            Shape.Cross -> cross(x, y, z, block)
            Shape.Slab -> {
                val high = if (block == Block.Enchanting) 12 else 8
                if (state and 4 != 0) box(x, y, z, block, 0, 16 - high, 0, 16, 16, 16) else box(x, y, z, block, 0, 0, 0, 16, high, 16)
            }
            Shape.Stairs -> stairs(x, y, z, block, state)
            Shape.Torch -> {
                box(x, y, z, block, 7, 0, 7, 9, 8, 9, tile = Tile.TORCH, uvFrom = intArrayOf(7, 6))
                box(x, y, z, block, 7, 8, 7, 9, 10, 9, tile = Tile.TORCH, uvFrom = intArrayOf(7, 2), emit = 1f)
            }
            Shape.Lantern -> {
                box(x, y, z, block, 5, 0, 5, 11, 7, 11, uvFrom = intArrayOf(5, 5), emit = 1f)
                box(x, y, z, block, 6, 7, 6, 10, 9, 10, tile = Tile.ANVIL)
                if (world[x, y + 1, z] != null) box(x, y, z, block, 7, 9, 7, 9, 16, 9, tile = Tile.CHAIN, uvFrom = intArrayOf(6, 0))
            }
            Shape.Chain -> box(x, y, z, block, 7, 0, 7, 9, 16, 9, uvFrom = intArrayOf(6, 0))
            Shape.Fence -> fence(x, y, z, block)
            Shape.Pane -> pane(x, y, z, block, state)
            Shape.Anvil -> {
                val alongX = state % 2 == 1
                if (alongX) {
                    box(x, y, z, block, 2, 0, 2, 14, 4, 14)
                    box(x, y, z, block, 4, 4, 5, 12, 5, 11)
                    box(x, y, z, block, 5, 5, 6, 11, 10, 10)
                    box(x, y, z, block, 0, 10, 3, 16, 16, 13)
                } else {
                    box(x, y, z, block, 2, 0, 2, 14, 4, 14)
                    box(x, y, z, block, 5, 4, 4, 11, 5, 12)
                    box(x, y, z, block, 6, 5, 5, 10, 10, 11)
                    box(x, y, z, block, 3, 10, 0, 13, 16, 16)
                }
            }
            Shape.Flat -> {
                val lift = if (block == Block.LilyPad) 14.8f else 0.25f
                flat(x, y, z, block, lift)
            }
            Shape.Campfire -> {
                box(x, y, z, block, 1, 0, 3, 15, 4, 7, tile = Tile.CAMPFIRE_LOG)
                box(x, y, z, block, 1, 0, 9, 15, 4, 13, tile = Tile.CAMPFIRE_LOG)
                box(x, y, z, block, 3, 3, 1, 7, 7, 15, tile = Tile.CAMPFIRE_LOG)
                box(x, y, z, block, 9, 3, 1, 13, 7, 15, tile = Tile.CAMPFIRE_LOG)
                cross(x, y, z, block, tile = Tile.FIRE, emit = 1f, anim = Anim.Fire, lift = 3f)
            }
            Shape.Chest -> box(x, y, z, block, 1, 0, 1, 15, 14, 15)
            Shape.Book -> {
                box(x, y, z, block, 4, 3, 6, 8, 4, 10, tile = Tile.BOOK)
                box(x, y, z, block, 8, 3, 6, 12, 4, 10, tile = Tile.BOOK)
            }
        }
    }

    private fun tileFor(block: Block, d: Int, state: Int): Int {
        if (block.shape == Shape.Log) {
            // Logs lie along an axis: 0 upright, 1 east–west, 2 north–south; the rings face the ends.
            val axis = state % 3
            val end = when (axis) {
                1 -> d == 2 || d == 3
                2 -> d == 4 || d == 5
                else -> d == 0 || d == 1
            }
            return if (end) block.top else block.side
        }
        return when (d) {
            0 -> block.top
            1 -> block.bottom
            else -> block.side
        }
    }

    fun cube(x: Int, y: Int, z: Int, block: Block, state: Int) {
        for ((d, dir) in DIRS.withIndex()) {
            val n = world[x + dir.nx, y + dir.ny, z + dir.nz]
            if (n != null && (n.solid || (n == block && block.merges))) continue
            // Water under a lily pad, or beside the falling column, still shows its own surface.
            if (n != null && block == Block.Water && n == Block.Fall) continue
            val p = FloatArray(12)
            for (i in 0 until 12 step 3) {
                p[i] = (x + dir.quad[i]).toFloat()
                p[i + 1] = (y + dir.quad[i + 1]).toFloat()
                p[i + 2] = (z + dir.quad[i + 2]).toFloat()
            }
            inflateQuad(p, inflate)
            val tile = tileFor(block, d, state)
            val front = block == Block.Furnace && d >= 2 && facingOf(d) == state
            val face = face(p, dir, block, if (front) Tile.FURNACE_FRONT else tile, x, y, z, cubeUv(d), emit = if (block == Block.LavaCauldron && d == 0) 1f else block.emit)
            if (block == Block.LavaCauldron && d == 0) face.anim = Anim.Lava
            for (k in 0 until 4) {
                val qx = dir.quad[k * 3]
                val qy = dir.quad[k * 3 + 1]
                val qz = dir.quad[k * 3 + 2]
                smooth(face, k, x, y, z, dir, qx, qy, qz)
            }
            out += face
            if (front) decal(face, Tile.FIRE_WINDOW)
        }
    }

    private fun facingOf(d: Int) = when (d) {
        5 -> Facing.NORTH
        2 -> Facing.EAST
        4 -> Facing.SOUTH
        else -> Facing.WEST
    }

    /** A glowing copy of [face] a hair in front of it — the fire behind a furnace's door. */
    private fun decal(face: Face, tile: Int) {
        val p = face.corners.copyOf()
        for (i in 0 until 12 step 3) {
            p[i] += face.normal[0] * 0.01f
            p[i + 1] += face.normal[1] * 0.01f
            p[i + 2] += face.normal[2] * 0.01f
        }
        val uv = FloatArray(8)
        for (k in 0 until 4) {
            uv[k * 2] = face.uv[k * 2] - Tile.x(face.tile) + Tile.x(tile)
            uv[k * 2 + 1] = face.uv[k * 2 + 1] - Tile.y(face.tile) + Tile.y(tile)
        }
        val glow = Face(p, uv, face.normal, tile, 1f, 1f, Anim.None, false, face.cellX, face.cellY, face.cellZ)
        glow.color = atlas?.average(tile) ?: glow.color
        for (k in 0 until 4) { glow.sky[k] = face.sky[k]; glow.torch[k] = 1f }
        out += glow
    }

    private fun cubeUv(d: Int): FloatArray = when (d) {
        // u, v for the four corners, in tile units; v runs down the tile.
        0 -> floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f)
        1 -> floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        else -> floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
    }

    private fun face(p: FloatArray, dir: Dir, block: Block, tile: Int, x: Int, y: Int, z: Int, uv01: FloatArray, emit: Float = block.emit, twoSided: Boolean = false): Face {
        val uv = FloatArray(8) { i -> if (i % 2 == 0) tileU(tile, uv01[i]) else tileV(tile, uv01[i]) }
        val face = Face(p, uv, floatArrayOf(dir.nx.toFloat(), dir.ny.toFloat(), dir.nz.toFloat()), tile, block.alpha, emit, block.anim, twoSided, x, y, z)
        face.color = if (block == Block.Cloud) 0xFFFFFFFF.toInt() else atlas?.average(tile) ?: 0xFF888888.toInt()
        return face
    }

    /**
     * Minecraft's smooth light for one corner: the cell the face looks into and the three around the
     * corner in that layer. Two closed sides close the corner whatever the diagonal says.
     */
    private fun smooth(face: Face, k: Int, x: Int, y: Int, z: Int, dir: Dir, qx: Int, qy: Int, qz: Int) {
        val sx = if (dir.nx != 0) 0 else if (qx == 1) 1 else -1
        val sy = if (dir.ny != 0) 0 else if (qy == 1) 1 else -1
        val sz = if (dir.nz != 0) 0 else if (qz == 1) 1 else -1
        val bx = x + dir.nx
        val by = y + dir.ny
        val bz = z + dir.nz
        val a: IntArray
        val b: IntArray
        when {
            dir.nx != 0 -> { a = intArrayOf(0, sy, 0); b = intArrayOf(0, 0, sz) }
            dir.ny != 0 -> { a = intArrayOf(sx, 0, 0); b = intArrayOf(0, 0, sz) }
            else -> { a = intArrayOf(sx, 0, 0); b = intArrayOf(0, sy, 0) }
        }
        val side1 = world.solid(bx + a[0], by + a[1], bz + a[2])
        val side2 = world.solid(bx + b[0], by + b[1], bz + b[2])
        val corner = world.solid(bx + a[0] + b[0], by + a[1] + b[1], bz + a[2] + b[2])
        val level = if (side1 && side2) 0 else 3 - (if (side1) 1 else 0) - (if (side2) 1 else 0) - (if (corner) 1 else 0)
        face.ao[k] = AO_LEVELS[level]

        var skySum = 0f
        var torchSum = 0f
        var count = 0
        var best = -1
        var bestLevel = -1
        fun sample(cx: Int, cy: Int, cz: Int) {
            if (!world.inside(cx, cy, cz)) { skySum += 15f; count++; return }
            if (world.solid(cx, cy, cz)) return
            val i = world.index(cx, cy, cz)
            skySum += world.sky[i]
            val t = world.torch[i].toInt()
            torchSum += t
            if (t > bestLevel) { bestLevel = t; best = world.source[i].toInt() }
            count++
        }
        sample(bx, by, bz)
        if (!side1) sample(bx + a[0], by + a[1], bz + a[2])
        if (!side2) sample(bx + b[0], by + b[1], bz + b[2])
        if (!(side1 && side2) && !corner) sample(bx + a[0] + b[0], by + a[1] + b[1], bz + a[2] + b[2])
        if (count == 0) { face.sky[k] = 0f; face.torch[k] = 0f; return }
        face.sky[k] = skySum / count / 15f
        face.torch[k] = torchSum / count / 15f
        face.source[k] = best
    }

    /** Light for the smaller shapes: their own cell's, and a torch lights itself fully. */
    private fun ownLight(face: Face, x: Int, y: Int, z: Int, block: Block) {
        var sky = 15
        var torch = 0
        var source = -1
        for ((dx, dy, dz) in OWN) {
            val cx = x + dx
            val cy = y + dy
            val cz = z + dz
            if (!world.inside(cx, cy, cz) || world.solid(cx, cy, cz)) continue
            val i = world.index(cx, cy, cz)
            if (dx == 0 && dy == 0 && dz == 0) sky = world.sky[i].toInt() else sky = maxOf(sky, world.sky[i] - 1)
            if (world.torch[i] > torch) { torch = world.torch[i].toInt(); source = world.source[i].toInt() }
        }
        if (block.light > 0) torch = 15
        for (k in 0 until 4) {
            face.sky[k] = sky / 15f
            face.torch[k] = torch / 15f
            face.source[k] = source
        }
    }

    /**
     * An axis-aligned box inside the cell, in sixteenths. Each side maps the matching stretch of the
     * tile, so a torch shows the torch's pixels and a slab half its block — [uvFrom] shifts where
     * the stretch starts for tiles painted off-centre.
     */
    fun box(
        x: Int, y: Int, z: Int, block: Block,
        x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int,
        tile: Int? = null, uvFrom: IntArray? = null, emit: Float = block.emit,
    ) {
        for ((d, dir) in DIRS.withIndex()) {
            // A side flush with the cell's edge is hidden by a whole block there.
            val flush = when (d) {
                0 -> y1 == 16
                1 -> y0 == 0
                2 -> x1 == 16
                3 -> x0 == 0
                4 -> z1 == 16
                else -> z0 == 0
            }
            if (flush) {
                val n = world[x + dir.nx, y + dir.ny, z + dir.nz]
                if (n != null && (n.solid || (n == block && block.merges))) continue
            }
            val lo = floatArrayOf(x0 / 16f, y0 / 16f, z0 / 16f)
            val hi = floatArrayOf(x1 / 16f, y1 / 16f, z1 / 16f)
            val p = FloatArray(12)
            for (i in 0 until 12 step 3) {
                p[i] = x + if (dir.quad[i] == 1) hi[0] else lo[0]
                p[i + 1] = y + if (dir.quad[i + 1] == 1) hi[1] else lo[1]
                p[i + 2] = z + if (dir.quad[i + 2] == 1) hi[2] else lo[2]
            }
            inflateQuad(p, inflate * 0.5f)
            val t = tile ?: when (d) { 0 -> block.top; 1 -> block.bottom; else -> block.side }
            // The face's own extent, as fractions of the tile.
            val (ua, ub, va, vb) = when (d) {
                0, 1 -> floatArrayOf(lo[0], hi[0], lo[2], hi[2])
                2, 3 -> floatArrayOf(lo[2], hi[2], 1 - hi[1], 1 - lo[1])
                else -> floatArrayOf(lo[0], hi[0], 1 - hi[1], 1 - lo[1])
            }
            val shiftU = uvFrom?.let { it[0] / 16f - ua } ?: 0f
            val shiftV = uvFrom?.let { it[1] / 16f - va } ?: 0f
            val u0 = ua + shiftU
            val u1 = ub + shiftU
            val v0 = va + shiftV
            val v1 = vb + shiftV
            val uv01 = when (d) {
                0 -> floatArrayOf(u0, v0, u0, v1, u1, v1, u1, v0)
                1 -> floatArrayOf(u0, v0, u1, v0, u1, v1, u0, v1)
                else -> floatArrayOf(u0, v1, u1, v1, u1, v0, u0, v0)
            }
            val face = face(p, dir, block, t, x, y, z, uv01, emit)
            ownLight(face, x, y, z, block)
            if (d == 1) for (k in 0 until 4) face.ao[k] = 0.85f
            out += face
        }
    }

    /** Stairs: a bottom slab and the back half above it, the back to the [state]'s facing. */
    private fun stairs(x: Int, y: Int, z: Int, block: Block, state: Int) {
        box(x, y, z, block, 0, 0, 0, 16, 8, 16)
        when (state % 4) {
            Facing.NORTH -> box(x, y, z, block, 0, 8, 0, 16, 16, 8)
            Facing.EAST -> box(x, y, z, block, 8, 8, 0, 16, 16, 16)
            Facing.SOUTH -> box(x, y, z, block, 0, 8, 8, 16, 16, 16)
            else -> box(x, y, z, block, 0, 8, 0, 8, 16, 16)
        }
    }

    private fun fence(x: Int, y: Int, z: Int, block: Block) {
        box(x, y, z, block, 6, 0, 6, 10, 16, 10)
        fun joins(dx: Int, dz: Int): Boolean {
            val n = world[x + dx, y, z + dz] ?: return false
            return n == Block.Fence || n.solid
        }
        for (rail in intArrayOf(6, 12)) {
            if (joins(1, 0)) box(x, y, z, block, 10, rail, 7, 16, rail + 3, 9)
            if (joins(-1, 0)) box(x, y, z, block, 0, rail, 7, 6, rail + 3, 9)
            if (joins(0, 1)) box(x, y, z, block, 7, rail, 10, 9, rail + 3, 16)
            if (joins(0, -1)) box(x, y, z, block, 7, rail, 0, 9, rail + 3, 6)
        }
    }

    /** A thin sheet across the cell: glass in a window, the portal's surface. [state] 0 runs east–west, 1 north–south. */
    private fun pane(x: Int, y: Int, z: Int, block: Block, state: Int) {
        val thin = if (block == Block.Portal) 6 else 7
        if (state % 2 == 0) box(x, y, z, block, 0, 0, thin, 16, 16, 16 - thin)
        else box(x, y, z, block, thin, 0, 0, 16 - thin, 16, 16)
    }

    /** Two quads crossing on the diagonals, drawn from both sides: grass, flowers, vines, fire. */
    fun cross(x: Int, y: Int, z: Int, block: Block, tile: Int = block.top, emit: Float = block.emit, anim: Anim = block.anim, lift: Float = 0f) {
        val jitterX = (hash(x, z, 3) - 0.5f) * 0.3f
        val jitterZ = (hash(z, x, 5) - 0.5f) * 0.3f
        val bottom = y + lift / 16f
        val top = y + 1f
        val a = 0.14f
        val b = 0.86f
        val quads = arrayOf(
            floatArrayOf(x + a, bottom, z + a, x + b, bottom, z + b, x + b, top, z + b, x + a, top, z + a),
            floatArrayOf(x + a, bottom, z + b, x + b, bottom, z + a, x + b, top, z + a, x + a, top, z + b),
        )
        for ((i, p) in quads.withIndex()) {
            for (k in 0 until 12 step 3) { p[k] += jitterX; p[k + 2] += jitterZ }
            val normal = if (i == 0) floatArrayOf(0.7071f, 0f, -0.7071f) else floatArrayOf(0.7071f, 0f, 0.7071f)
            val uv01 = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
            val uv = FloatArray(8) { j -> if (j % 2 == 0) tileU(tile, uv01[j]) else tileV(tile, uv01[j]) }
            val face = Face(p, uv, normal, tile, block.alpha, emit, anim, true, x, y, z)
            face.color = atlas?.average(tile) ?: face.color
            ownLight(face, x, y, z, block)
            out += face
        }
    }

    /** A sheet lying on the block below: petals, a lily pad on the water, a carpet. */
    private fun flat(x: Int, y: Int, z: Int, block: Block, lift: Float) {
        val h = y + lift / 16f
        val p = floatArrayOf(x.toFloat(), h, z.toFloat(), x.toFloat(), h, z + 1f, x + 1f, h, z + 1f, x + 1f, h, z.toFloat())
        val tile = block.top
        val uv01 = floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f)
        val uv = FloatArray(8) { j -> if (j % 2 == 0) tileU(tile, uv01[j]) else tileV(tile, uv01[j]) }
        val face = Face(p, uv, floatArrayOf(0f, 1f, 0f), tile, 1f, block.emit, Anim.None, false, x, y, z)
        face.color = atlas?.average(tile) ?: face.color
        ownLight(face, x, y, z, block)
        out += face
    }

    private companion object {
        val OWN = listOf(Triple(0, 0, 0), Triple(0, 1, 0), Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 0, 1), Triple(0, 0, -1))
    }
}

// ---------- light ----------

/**
 * Light the way the game spreads it. Sky light pours straight down each column at full strength
 * until something stops it, then seeps sideways and under overhangs, one level per block; torch
 * light spreads the same from every source. Each cell remembers which source lit it brightest, so
 * that source's colour and flicker carry to the faces around it.
 */
internal fun World.bakeLight() {
    java.util.Arrays.fill(sky, 0)
    java.util.Arrays.fill(torch, 0)
    java.util.Arrays.fill(source, -1)
    lights.clear()
    val queue = IntArray(sx * sy * sz)
    var head = 0
    var tail = 0

    // Sky: straight down first.
    for (x in minX..maxX) for (z in minZ..maxZ) {
        var level = 15
        for (y in maxY downTo minY) {
            val block = this[x, y, z]
            if (block != null) {
                if (!block.clear) break
                level -= block.dims
                if (level <= 0) break
            }
            val i = index(x, y, z)
            sky[i] = level.toByte()
            queue[tail++] = i
        }
    }
    head = spread(sky, queue, head, tail, null)

    // Torches and every other source.
    tail = 0
    head = 0
    forEach { x, y, z, block ->
        if (block.light > 0) {
            val i = index(x, y, z)
            val id = lights.size
            lights += LightSource(x, y, z, block.glow, block == Block.Torch || block == Block.Campfire || block == Block.Lantern || block == Block.Furnace)
            torch[i] = block.light.toByte()
            source[i] = id.toShort()
            queue[tail++] = i
        }
    }
    spread(torch, queue, head, tail, source)
}

/** Breadth-first spread of [levels] from the queued cells, one level down per step. */
private fun World.spread(levels: ByteArray, queue: IntArray, start: Int, end: Int, owner: ShortArray?): Int {
    var head = start
    var tail = end
    val strideX = sy * sz
    val strideY = sz
    while (head < tail) {
        val i = queue[head++]
        val level = levels[i].toInt()
        if (level <= 1) continue
        val xi = i / strideX
        val yi = (i / strideY) % sy
        val zi = i % sz
        for (d in 0 until 6) {
            val nx = xi + NX[d]
            val ny = yi + NY[d]
            val nz = zi + NZ[d]
            if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue
            val j = (nx * sy + ny) * sz + nz
            val block = this[nx + minX, ny + minY, nz + minZ]
            if (block != null && !block.clear) continue
            val next = level - 1 - (block?.dims ?: 0)
            if (next <= levels[j]) continue
            levels[j] = next.toByte()
            owner?.let { it[j] = it[i] }
            if (tail < queue.size) queue[tail++] = j
        }
    }
    return head
}

private val NX = intArrayOf(1, -1, 0, 0, 0, 0)
private val NY = intArrayOf(0, 0, 1, -1, 0, 0)
private val NZ = intArrayOf(0, 0, 0, 0, 1, -1)

// ---------- shadows ----------

/**
 * Shadows, baked once per light: from each corner of each face — pulled a little towards the
 * centre so neighbouring faces do not share a sample — a ray walks the grid towards the light.
 * Per-corner results give soft edges for free, since the renderer blends them across the face.
 */
internal fun World.castShadows(faces: List<Face>, sun: FloatArray, moon: FloatArray) {
    for (f in faces) {
        for (k in 0 until 4) {
            val px = f.cx + (f.corners[k * 3] - f.cx) * 0.7f + f.normal[0] * 0.05f
            val py = f.cy + (f.corners[k * 3 + 1] - f.cy) * 0.7f + f.normal[1] * 0.05f
            val pz = f.cz + (f.corners[k * 3 + 2] - f.cz) * 0.7f + f.normal[2] * 0.05f
            val facingSun = f.twoSided || f.normal[0] * sun[0] + f.normal[1] * sun[1] + f.normal[2] * sun[2] > 0f
            val facingMoon = f.twoSided || f.normal[0] * moon[0] + f.normal[1] * moon[1] + f.normal[2] * moon[2] > 0f
            if (facingSun && blocked(px, py, pz, sun)) f.sun[k] = 0f
            if (facingMoon && blocked(px, py, pz, moon)) f.moon[k] = 0f
        }
    }
}

/** Amanatides–Woo grid walk from a point along [dir]; true if a solid block is in the way. */
internal fun World.blocked(ox: Float, oy: Float, oz: Float, dir: FloatArray, maxSteps: Int = 96, skipFirst: Boolean = false): Boolean {
    var x = floor(ox).toInt()
    var y = floor(oy).toInt()
    var z = floor(oz).toInt()
    val stepX = if (dir[0] > 0) 1 else -1
    val stepY = if (dir[1] > 0) 1 else -1
    val stepZ = if (dir[2] > 0) 1 else -1
    val inf = Float.MAX_VALUE
    val deltaX = if (dir[0] != 0f) abs(1f / dir[0]) else inf
    val deltaY = if (dir[1] != 0f) abs(1f / dir[1]) else inf
    val deltaZ = if (dir[2] != 0f) abs(1f / dir[2]) else inf
    var maxX = if (dir[0] > 0) (x + 1 - ox) * deltaX else if (dir[0] < 0) (ox - x) * deltaX else inf
    var maxY = if (dir[1] > 0) (y + 1 - oy) * deltaY else if (dir[1] < 0) (oy - y) * deltaY else inf
    var maxZ = if (dir[2] > 0) (z + 1 - oz) * deltaZ else if (dir[2] < 0) (oz - z) * deltaZ else inf
    if (!skipFirst && solid(x, y, z)) return true
    repeat(maxSteps) {
        if (maxX < maxY && maxX < maxZ) { x += stepX; maxX += deltaX }
        else if (maxY < maxZ) { y += stepY; maxY += deltaY }
        else { z += stepZ; maxZ += deltaZ }
        if (!inside(x, y, z)) return false
        if (solid(x, y, z)) return true
    }
    return false
}

/** Whether the straight line from the eye to a point crosses a block that [screens][Block.screens] it — for glows and particles. */
internal fun World.hidden(ex: Float, ey: Float, ez: Float, px: Float, py: Float, pz: Float): Boolean {
    val dx = px - ex
    val dy = py - ey
    val dz = pz - ez
    val length = sqrt(dx * dx + dy * dy + dz * dz)
    if (length < 0.5f) return false
    val dir = floatArrayOf(dx / length, dy / length, dz / length)
    var x = floor(px).toInt()
    var y = floor(py).toInt()
    var z = floor(pz).toInt()
    // Walk from the point back towards the eye; the point's own cell does not count.
    val back = floatArrayOf(-dir[0], -dir[1], -dir[2])
    val stepX = if (back[0] > 0) 1 else -1
    val stepY = if (back[1] > 0) 1 else -1
    val stepZ = if (back[2] > 0) 1 else -1
    val inf = Float.MAX_VALUE
    val deltaX = if (back[0] != 0f) abs(1f / back[0]) else inf
    val deltaY = if (back[1] != 0f) abs(1f / back[1]) else inf
    val deltaZ = if (back[2] != 0f) abs(1f / back[2]) else inf
    var maxX = if (back[0] > 0) (x + 1 - px) * deltaX else if (back[0] < 0) (px - x) * deltaX else inf
    var maxY = if (back[1] > 0) (y + 1 - py) * deltaY else if (back[1] < 0) (py - y) * deltaY else inf
    var maxZ = if (back[2] > 0) (z + 1 - pz) * deltaZ else if (back[2] < 0) (pz - z) * deltaZ else inf
    var travelled = 0f
    while (travelled < length - 0.5f) {
        if (maxX < maxY && maxX < maxZ) { x += stepX; travelled = maxX; maxX += deltaX }
        else if (maxY < maxZ) { y += stepY; travelled = maxY; maxY += deltaY }
        else { z += stepZ; travelled = maxZ; maxZ += deltaZ }
        if (!inside(x, y, z)) return false
        if (travelled < length - 0.5f && this[x, y, z]?.screens == true) return true
    }
    return false
}

internal fun inflateQuad(p: FloatArray, amount: Float) {
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

// ---------- clouds ----------

/** A flat layer of cloud cells, 4×4 blocks each, that drifts and wraps as one sheet. */
internal fun clouds(out: MutableList<Face>, seed: Int, cells: Int, height: Float, threshold: Float): Drift {
    val world = World(0, cells - 1, 0, 0, 0, cells - 1)
    for (i in 0 until cells) for (k in 0 until cells) {
        if (fbm(i * 0.22f, k * 0.22f, seed) > threshold) world[i, 0, k] = Block.Cloud
    }
    val size = 4f
    val half = cells * size / 2
    val start = out.size
    world.mesh(out, textured = false, inflate = 0.05f)
    for (i in start until out.size) {
        val f = out[i]
        for (k in 0 until 12 step 3) {
            f.corners[k] = f.corners[k] * size - half
            f.corners[k + 1] = f.corners[k + 1] * 1.3f + height
            f.corners[k + 2] = f.corners[k + 2] * size - half
        }
        f.drifts = true
        f.driftBase = f.cellX * size - half
        for (k in 0 until 4) { f.sky[k] = 1f; f.ao[k] = 1f }
    }
    // The corners moved after the centres were taken; clouds are sorted by centre, so rebuild them.
    for (i in start until out.size) out[i] = out[i].moved()
    return Drift(min = -half, width = cells * size, speed = 0.7f)
}

/** The same face with its centre worked out again, after its corners were moved. */
internal fun Face.moved(): Face {
    val f = Face(corners, uv, normal, tile, alpha, emit, anim, twoSided, cellX, cellY, cellZ)
    f.color = color
    f.shade = shade
    f.drifts = drifts
    f.driftBase = driftBase
    for (k in 0 until 4) {
        f.ao[k] = ao[k]; f.sky[k] = sky[k]; f.torch[k] = torch[k]; f.source[k] = source[k]; f.sun[k] = sun[k]; f.moon[k] = moon[k]
    }
    return f
}

internal class Drift(val min: Float, val width: Float, val speed: Float)

package com.yumi.launcher.scene

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/*
 * The block textures, painted in code at start-up: sixteen-pixel tiles in the spirit of the game's
 * own, drawn from fixed seeds so the island looks the same on every launch. Nothing is loaded from
 * disk — the launcher ships no Mojang art.
 *
 * Tiles live in one 256×256 atlas. A face samples its tile nearest-neighbour, so pixels stay crisp
 * however close the camera flies. Animated tiles (water, lava, the portal) are two tiles tall with
 * the pattern repeated, and scroll through the first by moving their texture coordinates.
 */

internal object Tile {
    const val SIZE = 16
    const val COLUMNS = 16
    const val ATLAS = SIZE * COLUMNS

    const val GRASS_TOP = 0
    const val GRASS_SIDE = 1
    const val DIRT = 2
    const val STONE = 3
    const val DEEPSLATE = 4
    const val COBBLE = 5
    const val MOSSY_COBBLE = 6
    const val SAND = 7
    const val GRAVEL = 8
    const val OAK_LOG = 9
    const val OAK_LOG_TOP = 10
    const val OAK_PLANKS = 11
    const val CHERRY_LOG = 12
    const val CHERRY_LOG_TOP = 13
    const val CHERRY_PLANKS = 14
    const val OAK_LEAVES = 15
    const val CHERRY_LEAVES = 16
    const val OBSIDIAN = 17
    const val STONE_BRICKS = 18
    const val BRICKS = 19
    const val GLASS = 20
    const val BOOKSHELF = 21
    const val CRAFTING_TOP = 22
    const val CRAFTING_SIDE = 23
    const val FURNACE_FRONT = 24
    const val FURNACE_SIDE = 25
    const val FURNACE_TOP = 26
    const val ANVIL = 27
    const val LANTERN = 28
    const val TORCH = 29
    const val CHAIN = 30
    const val HAY_TOP = 31
    const val HAY_SIDE = 32
    const val BARREL_SIDE = 33
    const val BARREL_TOP = 34
    const val CHEST = 35
    const val TALL_GRASS = 36
    const val POPPY = 37
    const val DANDELION = 38
    const val CORNFLOWER = 39
    const val ALLIUM = 40
    const val PINK_PETALS = 41
    const val WHEAT = 42
    const val FARMLAND = 43
    const val PATH_TOP = 44
    const val PATH_SIDE = 45
    const val COAL_ORE = 46
    const val IRON_ORE = 47
    const val DIAMOND_ORE = 48
    const val COPPER_ORE = 49
    const val AMETHYST = 50
    const val GLOW_VINE = 51
    const val MOSS = 52
    const val LILY_PAD = 53
    const val ENCHANT_TOP = 54
    const val ENCHANT_SIDE = 55
    const val BOOK = 56
    const val CRYING_OBSIDIAN = 57
    const val MOSSY_BRICKS = 58
    const val SMOOTH_STONE = 59
    const val BIRCH_LOG = 60
    const val BIRCH_LEAVES = 61
    const val RED_WOOL = 62
    const val BLACKSTONE = 63
    const val BLACKSTONE_BRICKS = 64
    const val SOUL_LANTERN = 65
    const val CLOUD = 66
    const val FIRE_WINDOW = 67
    const val CAMPFIRE_LOG = 68
    const val MUSHROOM = 69
    const val REEDS = 70
    const val PUMPKIN = 71
    const val PUMPKIN_TOP = 72
    const val HANGING_ROOTS = 73
    const val GLOWSTONE = 74
    const val WHITE_WOOL = 75
    const val SMITHING_TOP = 76
    const val CAULDRON = 77
    const val LECTERN = 78
    const val SPRUCE_PLANKS = 79
    const val SPRUCE_LEAVES = 80
    const val VERDIGRIS_COPPER = 81

    // Two tiles tall each: the pattern is painted twice, one above the other, and scrolls.
    const val WATER = 96
    const val WATER_FLOW = 97
    const val LAVA = 98
    const val PORTAL = 99
    const val FIRE = 100

    /** Pixel corner of a tile in the atlas. */
    fun x(tile: Int) = tile % COLUMNS * SIZE
    fun y(tile: Int) = tile / COLUMNS * SIZE
}

/** The painted atlas, and each tile's average colour — what the lowest quality draws faces with. */
internal class Atlas(val image: Image, private val average: IntArray) {
    fun average(tile: Int): Int = average[tile]
}

internal fun paintAtlas(): Atlas {
    val size = Tile.ATLAS
    val px = IntArray(size * size)
    val painter = Painter(px, size)
    painter.paintAll()
    val average = IntArray(Tile.COLUMNS * Tile.COLUMNS) { tile -> averageOf(px, size, Tile.x(tile), Tile.y(tile)) }
    val bytes = ByteArray(size * size * 4)
    for (i in px.indices) {
        val c = px[i]
        val a = c ushr 24
        bytes[i * 4] = ((c shr 16) and 0xFF).toByte()
        bytes[i * 4 + 1] = ((c shr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = (c and 0xFF).toByte()
        bytes[i * 4 + 3] = a.toByte()
    }
    val image = Image.makeRaster(ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), bytes, size * 4)
    return Atlas(image, average)
}

private fun averageOf(px: IntArray, stride: Int, ox: Int, oy: Int): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (y in 0 until Tile.SIZE) for (x in 0 until Tile.SIZE) {
        val c = px[(oy + y) * stride + ox + x]
        if (c ushr 24 < 128) continue
        r += (c shr 16) and 0xFF
        g += (c shr 8) and 0xFF
        b += c and 0xFF
        n++
    }
    if (n == 0L) return 0
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

private fun rgb(v: Int) = v or (0xFF shl 24)

private fun shade(c: Int, f: Float): Int {
    fun ch(v: Int) = (v * f).toInt().coerceIn(0, 255)
    return (c and 0xFF000000.toInt()) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
}

private const val CLEAR = 0

/** Paints tiles into the atlas; every tile gets its own seeded random source. */
private class Painter(val px: IntArray, val stride: Int) {
    private var ox = 0
    private var oy = 0
    private var rng = Rng(1)
    private var tile = 0

    fun r() = rng.next()
    fun pick(palette: IntArray) = palette[(r() * palette.size).toInt().coerceAtMost(palette.size - 1)]

    operator fun set(x: Int, y: Int, c: Int) {
        if (x !in 0 until Tile.SIZE || y !in 0 until Tile.SIZE) return
        px[(oy + y) * stride + ox + x] = c
    }

    operator fun get(x: Int, y: Int): Int = px[(oy + y) * stride + ox + (x and 15)]

    fun tile(id: Int, paint: Painter.() -> Unit) {
        tile = id
        ox = Tile.x(id)
        oy = Tile.y(id)
        rng = Rng(id * 7919 + 13)
        paint()
    }

    /** A two-tile-tall animated strip: [paint] draws one 16×16 frame, repeated below itself for scrolling. */
    fun strip(id: Int, paint: Painter.() -> Unit) {
        tile(id, paint)
        for (y in 0 until Tile.SIZE) for (x in 0 until Tile.SIZE) {
            px[(oy + y + Tile.SIZE) * stride + ox + x] = px[(oy + y) * stride + ox + x]
        }
    }

    fun each(f: (x: Int, y: Int) -> Int) {
        for (y in 0 until Tile.SIZE) for (x in 0 until Tile.SIZE) this[x, y] = f(x, y)
    }

    /** Noise that wraps at the tile's edges, so neighbouring blocks join without a seam. */
    fun tiled(x: Int, y: Int, cell: Int, seed: Int): Float {
        val period = Tile.SIZE / cell
        val fx = x.toFloat() / cell
        val fy = y.toFloat() / cell
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        fun h(i: Int, j: Int) = hash(((i % period) + period) % period, ((j % period) + period) % period, seed + tile * 31)
        val u = tx * tx * (3 - 2 * tx)
        val v = ty * ty * (3 - 2 * ty)
        val a = h(x0, y0)
        val b = h(x0 + 1, y0)
        val c = h(x0, y0 + 1)
        val d = h(x0 + 1, y0 + 1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    fun noisy(palette: IntArray, cell: Int = 4, grain: Float = 0.45f) = each { x, y ->
        val n = tiled(x, y, cell, 3) * (1 - grain) + r() * grain
        palette[(n * palette.size).toInt().coerceIn(0, palette.size - 1)]
    }

    fun speckle(colors: IntArray, density: Float) {
        for (y in 0 until Tile.SIZE) for (x in 0 until Tile.SIZE) if (r() < density) this[x, y] = pick(colors)
    }

    fun blob(cx: Int, cy: Int, size: Int, colors: IntArray) {
        for (dy in -size..size) for (dx in -size..size) {
            if (dx * dx + dy * dy > size * size + 1 || r() < 0.2f) continue
            this[cx + dx, cy + dy] = pick(colors)
        }
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        for (y in y0..y1) for (x in x0..x1) this[x, y] = c
    }

    fun paintAll() {
        val grass = intArrayOf(rgb(0x4E8A2E), rgb(0x5A9A35), rgb(0x66A83C), rgb(0x71B444), rgb(0x7DBF4C))
        val dirt = intArrayOf(rgb(0x5E4128), rgb(0x6D4B30), rgb(0x7A5536), rgb(0x86603D), rgb(0x946B45))
        val stone = intArrayOf(rgb(0x6C6C6E), rgb(0x767678), rgb(0x808082), rgb(0x8A8A8C), rgb(0x949496))
        val deep = intArrayOf(rgb(0x3F3F46), rgb(0x47474F), rgb(0x505058), rgb(0x585861))
        val oakBark = intArrayOf(rgb(0x5A4329), rgb(0x654C2F), rgb(0x6F5434), rgb(0x7A5D3A))
        val oakPlank = intArrayOf(rgb(0x9C7A48), rgb(0xA8854F), rgb(0xB28F57), rgb(0xA07E4B))
        val leaves = intArrayOf(rgb(0x2B5C1E), rgb(0x336B24), rgb(0x3C7A2A), rgb(0x4A8C33), rgb(0x559A3B))
        val cherry = intArrayOf(rgb(0xE283AD), rgb(0xEE95BB), rgb(0xF2A7C6), rgb(0xF7BDD6), rgb(0xFBD0E2))

        tile(Tile.GRASS_TOP) { noisy(grass, cell = 4, grain = 0.6f); speckle(intArrayOf(rgb(0x437A27), rgb(0x86C956)), 0.06f) }
        tile(Tile.DIRT) { noisy(dirt, cell = 4, grain = 0.65f); speckle(intArrayOf(rgb(0xA07A52), rgb(0x4E3520)), 0.07f) }
        tile(Tile.GRASS_SIDE) {
            noisy(dirt, cell = 4, grain = 0.65f)
            speckle(intArrayOf(rgb(0xA07A52), rgb(0x4E3520)), 0.06f)
            for (x in 0 until 16) {
                val drip = 2 + (r() * 3).toInt() + if (r() < 0.25f) 1 else 0
                for (y in 0 until drip) this[x, y] = pick(grass)
            }
        }
        tile(Tile.STONE) {
            each { x, y ->
                val n = tiled(x, y, 8, 5) * 0.55f + tiled(x, y, 4, 9) * 0.3f + r() * 0.15f
                stone[(n * stone.size).toInt().coerceIn(0, stone.size - 1)]
            }
            speckle(intArrayOf(rgb(0x5E5E60)), 0.05f)
        }
        tile(Tile.DEEPSLATE) {
            each { x, y ->
                val n = tiled(x, y / 2, 4, 5) * 0.5f + r() * 0.3f + (if (y % 4 == 0) 0.2f else 0f)
                deep[(n * deep.size).toInt().coerceIn(0, deep.size - 1)]
            }
        }
        cobble(Tile.COBBLE, stone, moss = false)
        cobble(Tile.MOSSY_COBBLE, stone, moss = true)
        tile(Tile.SAND) { noisy(intArrayOf(rgb(0xCFC290), rgb(0xD5C897), rgb(0xDBCFA3), rgb(0xE3D8AE)), cell = 4, grain = 0.7f) }
        tile(Tile.GRAVEL) {
            noisy(intArrayOf(rgb(0x6F6A66), rgb(0x7E746D), rgb(0x8A8580), rgb(0x9A958F), rgb(0xA8A39C)), cell = 2, grain = 0.8f)
        }
        tile(Tile.OAK_LOG) {
            each { x, y -> shade(oakBark[(hash(x, 0, 11) * oakBark.size).toInt().coerceAtMost(3)], 0.9f + r() * 0.2f) }
            for (x in 0 until 16) if (r() < 0.35f) { val y = (r() * 14).toInt(); this[x, y] = rgb(0x4A3621); this[x, y + 1] = rgb(0x4A3621) }
        }
        rings(Tile.OAK_LOG_TOP, rgb(0xB08A58), rgb(0x9C7849), rgb(0x5A4329))
        planks(Tile.OAK_PLANKS, oakPlank, rgb(0x6F5530))
        tile(Tile.CHERRY_LOG) {
            val bark = intArrayOf(rgb(0x3B2523), rgb(0x4A2F2C), rgb(0x5A3A36), rgb(0x6E4541))
            each { x, y -> shade(bark[(hash(x, 0, 12) * bark.size).toInt().coerceAtMost(3)], 0.88f + r() * 0.24f) }
        }
        rings(Tile.CHERRY_LOG_TOP, rgb(0xE3B3AC), rgb(0xD29A92), rgb(0x4A2F2C))
        planks(Tile.CHERRY_PLANKS, intArrayOf(rgb(0xE3B3AC), rgb(0xD8A39B), rgb(0xEDC3BD), rgb(0xDDAAA2)), rgb(0xB0776F))
        planks(Tile.SPRUCE_PLANKS, intArrayOf(rgb(0x6E5132), rgb(0x7A5A38), rgb(0x654A2E), rgb(0x735536)), rgb(0x4E3822))
        tile(Tile.OAK_LEAVES) { noisy(leaves, cell = 2, grain = 0.7f); speckle(intArrayOf(rgb(0x1F4A16)), 0.1f) }
        tile(Tile.BIRCH_LEAVES) { noisy(intArrayOf(rgb(0x5E8A3A), rgb(0x6A9A42), rgb(0x78A84C), rgb(0x86B556)), cell = 2, grain = 0.7f); speckle(intArrayOf(rgb(0x4A7230)), 0.1f) }
        tile(Tile.SPRUCE_LEAVES) {
            noisy(intArrayOf(rgb(0x193B22), rgb(0x214C2C), rgb(0x2A5B36), rgb(0x336A40)), cell = 2, grain = 0.7f)
            speckle(intArrayOf(rgb(0x122B18), rgb(0x3D784A)), 0.08f)
        }
        tile(Tile.CHERRY_LEAVES) { noisy(cherry, cell = 2, grain = 0.75f); speckle(intArrayOf(rgb(0xFFE4EF), rgb(0xD06E9C)), 0.08f) }
        tile(Tile.VERDIGRIS_COPPER) {
            val copper = intArrayOf(rgb(0x398B7C), rgb(0x449B8A), rgb(0x50AA97), rgb(0x327B6D))
            val seam = rgb(0x266156)
            for (y in 0 until 16) for (x in 0 until 16) {
                val row = y / 4
                val offset = if (row % 2 == 0) 0 else 4
                this[x, y] = if (y % 4 == 3 || (x + offset) % 8 == 7) seam
                else shade(copper[(hash((x + offset) / 8, row, 19) * copper.size).toInt().coerceAtMost(copper.size - 1)], 0.92f + r() * 0.16f)
            }
        }
        tile(Tile.BIRCH_LOG) {
            each { _, _ -> if (r() < 0.9f) rgb(0xD9D5CC) else rgb(0xC8C4BA) }
            repeat(6) {
                val x = (r() * 13).toInt()
                val y = (r() * 15).toInt()
                val w = 2 + (r() * 3).toInt()
                for (i in 0 until w) this[x + i, y] = rgb(0x2B2B2B)
            }
        }
        tile(Tile.OBSIDIAN) {
            noisy(intArrayOf(rgb(0x0F0A18), rgb(0x150F21), rgb(0x1D1530), rgb(0x241A3A)), cell = 4, grain = 0.5f)
            speckle(intArrayOf(rgb(0x3B2A5C), rgb(0x4A3670)), 0.05f)
        }
        tile(Tile.CRYING_OBSIDIAN) {
            noisy(intArrayOf(rgb(0x0F0A18), rgb(0x150F21), rgb(0x1D1530), rgb(0x241A3A)), cell = 4, grain = 0.5f)
            repeat(5) {
                val x = (r() * 15).toInt()
                val y = (r() * 10).toInt()
                val len = 2 + (r() * 5).toInt()
                for (i in 0 until len) this[x, y + i] = if (i == 0) rgb(0xC98BFF) else rgb(0x8A3CFF)
            }
        }
        bricks(Tile.STONE_BRICKS, stone, rgb(0x55555A), rowHeight = 8, moss = false)
        bricks(Tile.MOSSY_BRICKS, stone, rgb(0x55555A), rowHeight = 8, moss = true)
        tile(Tile.BRICKS) {
            val brick = intArrayOf(rgb(0x8E4033), rgb(0x9C4A3A), rgb(0xA85442), rgb(0x94463A))
            val mortar = rgb(0xB8B0A6)
            for (y in 0 until 16) for (x in 0 until 16) {
                val row = y / 4
                val offset = if (row % 2 == 0) 0 else 4
                this[x, y] = if (y % 4 == 3 || (x + offset) % 8 == 7) mortar else shade(brick[((hash((x + offset) / 8, row, 3)) * 4).toInt().coerceAtMost(3)], 0.9f + r() * 0.18f)
            }
        }
        tile(Tile.BLACKSTONE) { noisy(intArrayOf(rgb(0x221F22), rgb(0x2A2629), rgb(0x332E32), rgb(0x3B363A)), cell = 4, grain = 0.55f) }
        bricks(Tile.BLACKSTONE_BRICKS, intArrayOf(rgb(0x2A2629), rgb(0x332E32), rgb(0x3B363A), rgb(0x423C41)), rgb(0x151215), rowHeight = 8, moss = false)
        tile(Tile.SMOOTH_STONE) {
            noisy(intArrayOf(rgb(0x9A9A9C), rgb(0xA2A2A4), rgb(0xA8A8AA)), cell = 8, grain = 0.3f)
            for (i in 0 until 16) { this[i, 0] = rgb(0x8A8A8C); this[i, 15] = rgb(0x7A7A7C); this[0, i] = rgb(0x8E8E90); this[15, i] = rgb(0x7E7E80) }
        }
        tile(Tile.GLASS) {
            each { _, _ -> (0x30 shl 24) or 0xDCEEFF }
            for (i in 0 until 16) { this[i, 0] = rgb(0xE8F3FF); this[i, 15] = rgb(0xC8DCEE); this[0, i] = rgb(0xE8F3FF); this[15, i] = rgb(0xC8DCEE) }
            for (i in 3..6) this[i + 2, 9 - i] = (0xB0 shl 24) or 0xFFFFFF
            for (i in 0..1) this[11 + i, 5 - i] = (0xB0 shl 24) or 0xFFFFFF
        }
        tile(Tile.BOOKSHELF) {
            planks(oakPlank, rgb(0x6F5530))
            val books = intArrayOf(rgb(0x8E2A2A), rgb(0x2A4E8E), rgb(0x2E7A3A), rgb(0x8E6A2A), rgb(0x6A2A7A), rgb(0xD8C8A0), rgb(0x2A7A7A))
            for (shelf in 0..1) {
                val top = 1 + shelf * 8
                var x = 1
                while (x < 15) {
                    val w = if (r() < 0.3f) 2 else 1
                    val c = pick(books)
                    val h = 5 + (r() * 2).toInt()
                    for (i in 0 until w) for (y in top + (6 - h) until top + 6) this[x + i, y] = if (y == top + 6 - h) shade(c, 1.25f) else c
                    x += w + if (r() < 0.2f) 1 else 0
                }
                for (i in 0 until 16) this[i, top + 6] = rgb(0x5A4329)
            }
        }
        tile(Tile.CRAFTING_TOP) {
            planks(oakPlank, rgb(0x6F5530))
            for (i in 0 until 16) { this[i, 0] = rgb(0x5A4329); this[i, 15] = rgb(0x5A4329); this[0, i] = rgb(0x5A4329); this[15, i] = rgb(0x5A4329) }
            for (i in 2..13) { this[i, 5] = rgb(0x6F5530); this[i, 10] = rgb(0x6F5530); this[5, i] = rgb(0x6F5530); this[10, i] = rgb(0x6F5530) }
        }
        tile(Tile.CRAFTING_SIDE) {
            planks(oakPlank, rgb(0x6F5530))
            for (i in 0 until 16) this[i, 0] = rgb(0x5A4329)
            // A saw and a hammer hung on the side, the way the table shows its trade.
            for (i in 3..11) this[i, 4] = rgb(0x9A9A9A)
            for (i in 3..11 step 2) this[i, 5] = rgb(0x7A7A7A)
            for (i in 6..12) this[12, i] = rgb(0x5A4329)
            rect(11, 5, 14, 6, rgb(0x6A6A6A))
        }
        tile(Tile.FURNACE_SIDE) { noisy(stone, cell = 8, grain = 0.35f); for (i in 0 until 16) { this[i, 0] = rgb(0x5E5E60); this[i, 15] = rgb(0x5E5E60) } }
        tile(Tile.FURNACE_TOP) { noisy(stone, cell = 8, grain = 0.3f) }
        tile(Tile.FURNACE_FRONT) {
            noisy(stone, cell = 8, grain = 0.35f)
            for (i in 0 until 16) { this[i, 0] = rgb(0x5E5E60); this[i, 15] = rgb(0x5E5E60) }
            rect(3, 8, 12, 13, rgb(0x2A2A2C))
            for (i in 3..12) this[i, 7] = rgb(0x4A4A4C)
        }
        tile(Tile.FIRE_WINDOW) {
            each { x, y ->
                val heat = (y - 8) / 6f + r() * 0.35f
                when {
                    x !in 3..12 || y !in 8..13 -> CLEAR
                    heat > 0.75f -> rgb(0xFFE38A)
                    heat > 0.4f -> rgb(0xFFB23C)
                    else -> rgb(0xE8661E)
                }
            }
        }
        tile(Tile.ANVIL) {
            noisy(intArrayOf(rgb(0x3C3C3C), rgb(0x444444), rgb(0x4C4C4C), rgb(0x545454)), cell = 4, grain = 0.5f)
            repeat(4) { val y = (r() * 16).toInt(); val x = (r() * 10).toInt(); for (i in 0..4) this[x + i, y] = rgb(0x5E5E5E) }
        }
        lantern(Tile.LANTERN, intArrayOf(rgb(0xFFD37A), rgb(0xFFB84D), rgb(0xFFE7A8)))
        lantern(Tile.SOUL_LANTERN, intArrayOf(rgb(0x7FE7FF), rgb(0x4ACBE8), rgb(0xB8F4FF)))
        tile(Tile.TORCH) {
            each { x, y -> if (x in 7..8 && y >= 6) (if (y > 7) rgb(0x6B4A2E) else rgb(0x8A6038)) else CLEAR }
            rect(7, 3, 8, 5, rgb(0xFFB23C))
            this[7, 2] = rgb(0xFFE38A); this[8, 2] = rgb(0xFFD35A); this[7, 3] = rgb(0xFFF3C0)
        }
        tile(Tile.CHAIN) {
            each { x, y -> if ((x in 6..9) && (y % 4 != 3) && (x in 6..7 == (y / 4 % 2 == 0))) rgb(0x3A3F4A) else CLEAR }
        }
        tile(Tile.HAY_TOP) {
            noisy(intArrayOf(rgb(0xB8912A), rgb(0xC9A233), rgb(0xD8B33E), rgb(0xE2BE4A)), cell = 2, grain = 0.8f)
            for (i in 0 until 16) { this[i, 0] = rgb(0x9A7620); this[i, 15] = rgb(0x9A7620); this[0, i] = rgb(0x9A7620); this[15, i] = rgb(0x9A7620) }
        }
        tile(Tile.HAY_SIDE) {
            each { x, _ -> shade(intArrayOf(rgb(0xB8912A), rgb(0xC9A233), rgb(0xD8B33E))[(hash(x, 1, 5) * 3).toInt().coerceAtMost(2)], 0.9f + r() * 0.2f) }
            for (x in 0 until 16) { this[x, 3] = rgb(0x8E5A2A); this[x, 12] = rgb(0x8E5A2A) }
        }
        tile(Tile.BARREL_SIDE) {
            each { x, _ -> shade(if ((x / 4) % 2 == 0) rgb(0x8A6238) else rgb(0x7A5530), 0.92f + r() * 0.14f) }
            for (x in 0 until 16) { this[x, 2] = rgb(0x4A4A4A); this[x, 13] = rgb(0x4A4A4A) }
            for (y in 0 until 16) if ((y + 1) % 4 == 0) this[0, y] = rgb(0x5A3E22)
        }
        tile(Tile.BARREL_TOP) {
            planks(intArrayOf(rgb(0x8A6238), rgb(0x7A5530), rgb(0x94693E)), rgb(0x5A3E22))
            rect(5, 5, 10, 10, rgb(0x3A2A18))
            for (i in 0 until 16) { this[i, 0] = rgb(0x4A4A4A); this[i, 15] = rgb(0x4A4A4A); this[0, i] = rgb(0x4A4A4A); this[15, i] = rgb(0x4A4A4A) }
        }
        tile(Tile.CHEST) {
            planks(intArrayOf(rgb(0xA8743A), rgb(0x9A6A34), rgb(0xB07B40)), rgb(0x6A4420))
            for (i in 0 until 16) { this[i, 0] = rgb(0x4A2E14); this[i, 15] = rgb(0x4A2E14); this[0, i] = rgb(0x4A2E14); this[15, i] = rgb(0x4A2E14); this[i, 6] = rgb(0x4A2E14) }
            rect(7, 5, 8, 8, rgb(0xC8C8C8))
        }
        plant(Tile.TALL_GRASS) {
            repeat(9) {
                val x = 1 + (r() * 14).toInt()
                val h = 6 + (r() * 9).toInt()
                val c = pick(grass)
                for (i in 0 until h) this[x + if (i > h / 2 && r() < 0.3f) 1 else 0, 15 - i] = c
            }
        }
        flower(Tile.POPPY, intArrayOf(rgb(0xD8282A), rgb(0xB81E20), rgb(0xE84040)), center = rgb(0x2A1A10))
        flower(Tile.DANDELION, intArrayOf(rgb(0xF2D23A), rgb(0xE8B82A), rgb(0xFFE35A)), center = rgb(0xD89A1A))
        flower(Tile.CORNFLOWER, intArrayOf(rgb(0x4A6FE0), rgb(0x6A8CF0), rgb(0x3A5AC8)), center = rgb(0x2A3A8A))
        flower(Tile.ALLIUM, intArrayOf(rgb(0xB870E0), rgb(0x9A50C8), rgb(0xCB8AEE)), center = rgb(0x7A38A8))
        plant(Tile.PINK_PETALS) {
            repeat(7) {
                val cx = 2 + (r() * 12).toInt()
                val cy = 2 + (r() * 12).toInt()
                for ((dx, dy) in listOf(0 to 0, 1 to 0, 0 to 1, -1 to 0, 0 to -1)) this[cx + dx, cy + dy] = pick(cherry)
                this[cx, cy] = rgb(0xFFE35A)
            }
        }
        plant(Tile.WHEAT) {
            for (x in 1 until 15 step 2) {
                val h = 10 + (r() * 5).toInt()
                for (i in 0 until h) this[x, 15 - i] = if (i > h - 5) pick(intArrayOf(rgb(0xD8B84A), rgb(0xC8A83A), rgb(0xE8C85A))) else rgb(0x8A9A3A)
            }
        }
        tile(Tile.FARMLAND) {
            noisy(intArrayOf(rgb(0x3E2A1A), rgb(0x4A3220), rgb(0x553A25)), cell = 2, grain = 0.7f)
            for (y in 0 until 16) if (y % 4 == 1) for (x in 0 until 16) this[x, y] = rgb(0x2E1F12)
        }
        tile(Tile.PATH_TOP) { noisy(intArrayOf(rgb(0x8A6D42), rgb(0x94774A), rgb(0x9E8052), rgb(0xA8895A)), cell = 4, grain = 0.7f) }
        tile(Tile.PATH_SIDE) {
            noisy(dirt, cell = 4, grain = 0.65f)
            // No see-through top row: the path is a whole cube here, so a slit would show whatever is behind it.
            for (x in 0 until 16) { this[x, 0] = rgb(0x7E6340); this[x, 1] = pick(intArrayOf(rgb(0x94774A), rgb(0x9E8052))) }
        }
        ore(Tile.COAL_ORE, stone, intArrayOf(rgb(0x1E1E1E), rgb(0x2E2E2E), rgb(0x3A3A3A)))
        ore(Tile.IRON_ORE, stone, intArrayOf(rgb(0xD8AF93), rgb(0xC8987A), rgb(0xE8C8B0)))
        ore(Tile.DIAMOND_ORE, stone, intArrayOf(rgb(0x4AEDD9), rgb(0x2ACBB8), rgb(0xA8FFF4)))
        ore(Tile.COPPER_ORE, stone, intArrayOf(rgb(0xE07A3A), rgb(0x4AA88A), rgb(0xC8603A)))
        tile(Tile.AMETHYST) {
            noisy(intArrayOf(rgb(0x6A40B0), rgb(0x8A5BD6), rgb(0xA47AE8), rgb(0xC8A8F8)), cell = 4, grain = 0.5f)
            repeat(6) { val x = (r() * 14).toInt(); val y = (r() * 14).toInt(); this[x, y] = rgb(0xF0E0FF); this[x + 1, y + 1] = rgb(0xE0C8FF) }
        }
        plant(Tile.GLOW_VINE) {
            for (x in intArrayOf(4, 11)) {
                for (y in 0 until 16) this[x + if (y % 6 < 3) 0 else 1, y] = pick(leaves)
                for (y in intArrayOf(3, 9, 14)) { this[x - 1, y] = rgb(0xFF9D2E); this[x + 1, y] = rgb(0xFFC45A); this[x, y + 1] = rgb(0xFF8A1E) }
            }
        }
        plant(Tile.HANGING_ROOTS) {
            repeat(8) {
                val x = 1 + (r() * 14).toInt()
                val h = 5 + (r() * 11).toInt()
                for (i in 0 until h) this[x + if (i > h / 2 && r() < 0.25f) 1 else 0, i] = pick(intArrayOf(rgb(0xA8845A), rgb(0x94714C), rgb(0xB8966A)))
            }
        }
        tile(Tile.MOSS) { noisy(intArrayOf(rgb(0x4E6A24), rgb(0x5A7A2A), rgb(0x6A8A32), rgb(0x78983A)), cell = 2, grain = 0.7f) }
        plant(Tile.LILY_PAD) {
            each { x, y ->
                val d = hypot(x - 7.5, y - 7.5)
                val a = atan2(y - 7.5, x - 7.5)
                if (d < 7 && !(a > -0.35 && a < 0.1 && d > 2)) pick(intArrayOf(rgb(0x2E6A1E), rgb(0x3A7A26), rgb(0x2A5A1A))) else CLEAR
            }
        }
        tile(Tile.ENCHANT_TOP) {
            noisy(intArrayOf(rgb(0x7A1E24), rgb(0x8E242C), rgb(0xA02A30)), cell = 4, grain = 0.4f)
            for ((cx, cy) in listOf(1 to 1, 13 to 1, 1 to 13, 13 to 13)) rect(cx, cy, cx + 1, cy + 1, rgb(0x4AEDD9))
            for (i in 0 until 16) { this[i, 0] = rgb(0x150F21); this[i, 15] = rgb(0x150F21); this[0, i] = rgb(0x150F21); this[15, i] = rgb(0x150F21) }
        }
        tile(Tile.ENCHANT_SIDE) {
            noisy(intArrayOf(rgb(0x0F0A18), rgb(0x150F21), rgb(0x1D1530)), cell = 4, grain = 0.5f)
            for (y in 0..3) for (x in 0 until 16) this[x, y] = if (y == 3 && x % 3 == 0) rgb(0x150F21) else pick(intArrayOf(rgb(0x7A1E24), rgb(0x8E242C)))
        }
        tile(Tile.BOOK) {
            each { _, y -> if (y < 3 || y > 12) rgb(0x6A3A1E) else rgb(0xE8DDB5) }
            for (x in 0 until 16) { this[x, 7] = rgb(0xC8B890) }
        }
        tile(Tile.RED_WOOL) { noisy(intArrayOf(rgb(0x8E2424), rgb(0xA02A2A), rgb(0xB03232)), cell = 2, grain = 0.6f) }
        tile(Tile.WHITE_WOOL) { noisy(intArrayOf(rgb(0xDADADA), rgb(0xE4E4E4), rgb(0xEEEEEE)), cell = 2, grain = 0.6f) }
        tile(Tile.CLOUD) { each { _, _ -> rgb(0xFFFFFF) } }
        tile(Tile.CAMPFIRE_LOG) {
            each { x, y -> if (y < 3 || y > 12) rgb(0x3A2A1A) else shade(oakBark[(hash(x, y / 4, 2) * 4).toInt().coerceAtMost(3)], 0.8f + r() * 0.2f) }
            repeat(6) { this[(r() * 16).toInt(), 3 + (r() * 10).toInt()] = rgb(0x1E1410) }
        }
        plant(Tile.MUSHROOM) {
            rect(7, 9, 8, 15, rgb(0xD8D0C0))
            each { x, y -> if (y in 4..9 && hypot(x - 7.5, (y - 9) * 1.6) < 5.5) (if (r() < 0.15f) rgb(0xF0F0F0) else rgb(0xC82A2A)) else this[x, y] }
        }
        plant(Tile.REEDS) {
            for (x in intArrayOf(3, 7, 12)) {
                for (y in 0 until 16) this[x, y] = if (y % 5 == 0) rgb(0x6A9A3A) else rgb(0x8AC04A)
                this[x + 1, 4 + (r() * 8).toInt()] = rgb(0x5A8A2A)
            }
        }
        tile(Tile.PUMPKIN) {
            each { x, _ -> shade(if (x % 4 == 0) rgb(0xC8741A) else rgb(0xE08A1E), 0.9f + r() * 0.15f) }
        }
        tile(Tile.PUMPKIN_TOP) {
            each { x, y -> shade(if (hypot(x - 7.5, y - 7.5) > 6.5) rgb(0xC8741A) else rgb(0xE08A1E), 0.92f + r() * 0.12f) }
            rect(7, 6, 8, 9, rgb(0x5A6A2A))
        }
        tile(Tile.GLOWSTONE) {
            noisy(intArrayOf(rgb(0xC8903A), rgb(0xE8B04A), rgb(0xFFD878), rgb(0xFFEBA8)), cell = 4, grain = 0.6f)
        }
        tile(Tile.SMITHING_TOP) {
            noisy(intArrayOf(rgb(0x2A2A30), rgb(0x333338), rgb(0x3C3C42)), cell = 4, grain = 0.5f)
            for (i in 0 until 16) { this[i, 0] = rgb(0x6A4A2A); this[i, 15] = rgb(0x6A4A2A); this[0, i] = rgb(0x6A4A2A); this[15, i] = rgb(0x6A4A2A) }
        }
        tile(Tile.CAULDRON) {
            noisy(intArrayOf(rgb(0x3A3A40), rgb(0x44444A), rgb(0x4E4E54)), cell = 4, grain = 0.4f)
            for (i in 0 until 16) { this[i, 0] = rgb(0x28282C); this[0, i] = rgb(0x28282C); this[15, i] = rgb(0x28282C) }
            // The gap between the legs is shadow, not a hole: the cauldron is a whole cube to the
            // mesher, so the wall behind it is never drawn and would show the sky through here.
            rect(4, 12, 11, 15, rgb(0x16161A))
        }
        tile(Tile.LECTERN) {
            planks(oakPlank, rgb(0x6F5530))
            rect(3, 2, 12, 8, rgb(0xE8DDB5))
            for (i in 3..12) this[i, 5] = rgb(0xC8B890)
        }

        strip(Tile.WATER) {
            each { x, y ->
                val w = sin((x * 0.8f + y * 0.4f).toDouble()).toFloat() * 0.5f + 0.5f
                val n = tiled(x, y, 4, 7) * 0.6f + w * 0.25f + r() * 0.15f
                when {
                    n > 0.78f -> rgb(0x6A9BF2)
                    n > 0.55f -> rgb(0x3F78E6)
                    n > 0.3f -> rgb(0x3469DA)
                    else -> rgb(0x2A56C2)
                }
            }
        }
        strip(Tile.WATER_FLOW) {
            each { x, y ->
                val streak = tiled(x * 4, y, 4, 9)
                when {
                    streak > 0.7f -> rgb(0x7AA8F4)
                    streak > 0.45f -> rgb(0x4A82EA)
                    else -> rgb(0x2F62D2)
                }
            }
        }
        strip(Tile.LAVA) {
            each { x, y ->
                val n = tiled(x, y, 4, 11) * 0.7f + tiled(x, y, 8, 3) * 0.3f
                when {
                    n > 0.72f -> rgb(0xFFD35A)
                    n > 0.55f -> rgb(0xFFA02E)
                    n > 0.35f -> rgb(0xF0701E)
                    else -> rgb(0xC8421A)
                }
            }
        }
        strip(Tile.PORTAL) {
            each { x, y ->
                val a = atan2(y - 7.5, x - 7.5)
                val d = hypot(x - 7.5, y - 7.5)
                val swirl = sin(a * 3 + d * 0.9) * 0.5 + 0.5
                val n = (swirl * 0.6 + tiled(x, y, 4, 13) * 0.4).toFloat()
                when {
                    n > 0.75f -> (0xE0 shl 24) or 0xC37BFF
                    n > 0.5f -> (0xD0 shl 24) or 0x9B4DFF
                    n > 0.3f -> (0xC0 shl 24) or 0x7B2FE0
                    else -> (0xB0 shl 24) or 0x5A1BB0
                }
            }
        }
        strip(Tile.FIRE) {
            each { x, y ->
                val flame = (15 - y) / 15f
                val width = (1 - flame) * 7 + 1
                val wave = sin(y * 0.9 + x * 0.3) * 1.2
                val dx = abs(x - 7.5 - wave)
                when {
                    dx > width || tiled(x, y, 4, 17) + flame * 0.6f > 1.15f -> CLEAR
                    dx < width * 0.35f && flame < 0.5f -> rgb(0xFFF3C0)
                    dx < width * 0.7f -> rgb(0xFFB23C)
                    else -> rgb(0xE8661E)
                }
            }
        }
    }

    private fun cobble(id: Int, stone: IntArray, moss: Boolean) = tile(id) {
        // Stones as Voronoi cells over the tile, wrapped so the cells meet across block edges.
        val points = List(9) { floatArrayOf(r() * 16, r() * 16, r()) }
        each { x, y ->
            var best = Float.MAX_VALUE
            var second = Float.MAX_VALUE
            var shadeOf = 0f
            for (p in points) for (ox in -1..1) for (oy in -1..1) {
                val d = hypot((x + 0.5f - p[0] - ox * 16).toDouble(), (y + 0.5f - p[1] - oy * 16).toDouble()).toFloat()
                if (d < best) { second = best; best = d; shadeOf = p[2] } else if (d < second) second = d
            }
            if (second - best < 1.1f) rgb(0x4A4A4C) else shade(stone[(shadeOf * stone.size).toInt().coerceIn(0, stone.size - 1)], 1.05f - (best / 9f) * 0.25f + r() * 0.08f)
        }
        if (moss) repeat(5) { blob((r() * 16).toInt(), (r() * 16).toInt(), 2 + (r() * 2).toInt(), intArrayOf(rgb(0x4E6A24), rgb(0x5A7A2A), rgb(0x6A8A32))) }
    }

    private fun bricks(id: Int, face: IntArray, mortar: Int, rowHeight: Int, moss: Boolean) = tile(id) {
        for (y in 0 until 16) for (x in 0 until 16) {
            val row = y / rowHeight
            val offset = if (row % 2 == 0) 0 else 4
            val edge = y % rowHeight == rowHeight - 1 || (x + offset) % 8 == 7
            this[x, y] = if (edge) mortar else shade(face[(hash((x + offset) / 8, row, 7) * face.size).toInt().coerceAtMost(face.size - 1)], 0.9f + r() * 0.16f)
        }
        if (moss) repeat(4) { blob((r() * 16).toInt(), (r() * 16).toInt(), 2, intArrayOf(rgb(0x4E6A24), rgb(0x5A7A2A), rgb(0x6A8A32))) }
    }

    private fun rings(id: Int, light: Int, dark: Int, bark: Int) = tile(id) {
        each { x, y ->
            val d = max(abs(x - 7.5f), abs(y - 7.5f))
            when {
                d > 6.5f -> shade(bark, 0.9f + r() * 0.2f)
                (d.toInt() % 2) == 0 -> shade(light, 0.95f + r() * 0.1f)
                else -> shade(dark, 0.95f + r() * 0.1f)
            }
        }
    }

    private fun planks(id: Int, palette: IntArray, seam: Int) = tile(id) { planks(palette, seam) }

    private fun planks(palette: IntArray, seam: Int) {
        for (row in 0 until 4) {
            val base = palette[(r() * palette.size).toInt().coerceAtMost(palette.size - 1)]
            val cut = 2 + (r() * 12).toInt()
            for (y in row * 4 until row * 4 + 4) for (x in 0 until 16) {
                this[x, y] = when {
                    y == row * 4 + 3 -> seam
                    x == cut -> shade(seam, 1.15f)
                    else -> shade(base, 0.93f + r() * 0.12f)
                }
            }
        }
    }

    private fun lantern(id: Int, glow: IntArray) = tile(id) {
        each { x, y ->
            when {
                x == 0 || x == 15 || y == 0 || y == 15 -> rgb(0x2E2E35)
                x in 3..12 && y in 3..12 -> pick(glow)
                x == 7 || x == 8 || y == 7 || y == 8 -> rgb(0x3A3A42)
                else -> shade(glow[0], 0.85f)
            }
        }
    }

    private fun ore(id: Int, stone: IntArray, colors: IntArray) = tile(id) {
        each { x, y ->
            val n = tiled(x, y, 8, 5) * 0.55f + tiled(x, y, 4, 9) * 0.3f + r() * 0.15f
            stone[(n * stone.size).toInt().coerceIn(0, stone.size - 1)]
        }
        repeat(4) { blob(2 + (r() * 12).toInt(), 2 + (r() * 12).toInt(), 1, colors) }
    }

    /** A tile that is mostly air — plants, flowers, the vines — cut out along its alpha. */
    private fun plant(id: Int, paint: Painter.() -> Unit) = tile(id) {
        each { _, _ -> CLEAR }
        paint()
    }

    private fun flower(id: Int, petals: IntArray, center: Int) = plant(id) {
        for (y in 7..15) this[7 + if (y > 11) 0 else (if (y % 3 == 0) 1 else 0), y] = rgb(0x3A7A26)
        this[6, 11] = rgb(0x4A8C33); this[5, 10] = rgb(0x4A8C33); this[9, 12] = rgb(0x4A8C33)
        for (dy in -3..3) for (dx in -3..3) if (dx * dx + dy * dy <= 8) this[7 + dx, 5 + dy] = pick(petals)
        this[7, 5] = center
    }
}

/** Where a point of a face's tile sits in the atlas; the edges are pulled in a hair so sampling never reaches the next tile. */
internal fun tileU(tile: Int, u: Float) = Tile.x(tile) + (u.coerceIn(0.002f, 0.998f)) * Tile.SIZE
internal fun tileV(tile: Int, v: Float) = Tile.y(tile) + (v.coerceIn(0.002f, 0.998f)) * Tile.SIZE

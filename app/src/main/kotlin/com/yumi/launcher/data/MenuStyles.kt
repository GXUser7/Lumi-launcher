package com.yumi.launcher.data

import com.yumi.launcher.core.LauncherJson
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.VersionEntry
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.ui.theme.MenuPack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/** How a pack's game menus look when the launcher dresses them: see [MenuStyles]. */
@Serializable
data class MenuStyle(val palette: Palette = Palette.Original, val dark: Boolean = false, val font: Boolean = false)

/**
 * The launcher's look in the game's menus: before each start it writes the game-menu pack
 * ([MenuPack]) into the pack's `resourcepacks` as one file, whatever its colours, and switches it
 * on the first time. After that the game's own list is in charge — the player can move it or turn
 * it off there, and a later start only refreshes the file. Turned off in the launcher, the file
 * and its line in `options.txt` go.
 */
internal object MenuStyles {
    private const val FILE = "Yumi.zip"
    private const val ID = "file/$FILE"

    /** Bumped whenever what [MenuPack] draws changes, so the cached packs are built again. */
    private const val GENERATION = 1

    /** Whether the version's menus are made of the sprites the pack paints: 1.20.2 and its snapshots on. */
    fun supports(entry: VersionEntry?): Boolean = entry == null || entry.releaseTime >= FIRST_SPRITES

    /** The first snapshot with GUI sprites, 23w31a. */
    private const val FIRST_SPRITES = "2023-08-01"

    /** Puts the pack for [style] into [gameDir], or takes it out when [style] is null or [clientJar]'s game predates it. */
    fun apply(gameDir: File, style: MenuStyle?, clientJar: File) {
        val file = gameDir.resolve("resourcepacks").resolve(FILE)
        val format = resourceFormat(clientJar)
        if (style == null || format == null || format < MenuPack.MIN_FORMAT) {
            if (file.isFile && ours(file)) file.delete()
            editResourcePacks(gameDir) { packs -> packs - ID }
            return
        }
        val bytes = cached(style, maxOf(format, MenuPack.KNOWN_FORMAT))
        if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        // Added once, on top; never moved back there if the player has put it somewhere else.
        editResourcePacks(gameDir) { packs -> if (ID in packs) packs else packs + ID }
    }

    /** Building a pack paints six faces of sky, about a second of work: one copy per look is kept. */
    private fun cached(style: MenuStyle, maxFormat: Int): ByteArray {
        val name = "${style.palette.name.lowercase()}-${if (style.dark) "dark" else "light"}${if (style.font) "-font" else ""}-f$maxFormat-g$GENERATION.zip"
        val file = LauncherPaths.cache.resolve("menu-packs").resolve(name)
        if (file.isFile) return file.readBytes()
        return MenuPack.build(style.palette, style.dark, style.font, maxFormat).also { bytes ->
            file.parentFile.mkdirs()
            val part = File(file.path + ".part")
            part.writeBytes(bytes)
            part.renameTo(file)
        }
    }

    private fun ours(file: File) = runCatching { ZipFile(file).use { it.comment == "Yumi Launcher" } }.getOrDefault(false)

    /**
     * The resource pack format of the game in [clientJar], from the `version.json` inside it: a
     * number until 1.20.1, then an object with the resource and data formats. Null for a game
     * before 1.14, which has no such file.
     */
    private fun resourceFormat(clientJar: File): Int? = runCatching {
        ZipFile(clientJar).use { zip ->
            val entry = zip.getEntry("version.json") ?: return null
            val json = LauncherJson.parseToJsonElement(zip.getInputStream(entry).use { it.readBytes() }.decodeToString()).jsonObject
            when (val pack = json["pack_version"]) {
                is JsonPrimitive -> pack.intOrNull
                is JsonObject -> (pack["resource_major"] ?: pack["resource"])?.jsonPrimitive?.int
                else -> null
            }
        }
    }.getOrNull()
}

/** The resource packs switched on in the game at [gameDir], bottom to top, as `options.txt` lists them. */
internal fun resourcePacks(gameDir: File): List<String> {
    val line = gameDir.resolve("options.txt").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("resourcePacks:") } ?: return listOf("vanilla")
    return runCatching { LauncherJson.parseToJsonElement(line.removePrefix("resourcePacks:")).jsonArray.map { it.jsonPrimitive.content } }.getOrElse { listOf("vanilla") }
}

/**
 * Rewrites the `resourcePacks` line of the game's `options.txt` through [change], leaving every
 * other line as it was. A game that has not run yet gets a file with just that line.
 */
internal fun editResourcePacks(gameDir: File, change: (List<String>) -> List<String>) {
    val file = gameDir.resolve("options.txt")
    val lines = if (file.isFile) file.readLines() else emptyList()
    val at = lines.indexOfFirst { it.startsWith("resourcePacks:") }
    val packs = resourcePacks(gameDir)
    val changed = change(packs)
    if (changed == packs) return
    val line = "resourcePacks:" + JsonArray(changed.map(::JsonPrimitive))
    val updated = if (at < 0) lines + line else lines.toMutableList().also { it[at] = line }
    file.parentFile.mkdirs()
    file.writeText(updated.joinToString("\n", postfix = "\n"))
}

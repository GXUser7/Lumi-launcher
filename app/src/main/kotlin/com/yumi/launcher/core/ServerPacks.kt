package com.yumi.launcher.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** A mod a Forge server lists in its ping; no version for a mod only the server needs. */
data class ForgeMod(val id: String, val version: String?)

/** What a server's ping gives away about the mods it wants. */
sealed interface ModHints {
    /** Run from Lumi Launcher: the exact pack. */
    data class Yumi(val pack: SharedPack) : ModHints

    /** Forge lists its mods by id and version — no downloads; [truncated] when it cut the list short. */
    data class Forge(val mods: List<ForgeMod>, val truncated: Boolean) : ModHints

    /** NeoForge says only that it runs mods, not which. */
    data object Modded : ModHints
}

/** A mod to download for a server's pack, with what the mods screen shows for it. */
internal data class PlannedMod(
    val fileName: String,
    val url: String,
    val sha1: String?,
    val size: Long?,
    val source: ModSource,
    val projectId: String,
    val title: String,
    val iconUrl: String?,
    val version: String,
    /** False when the server's exact version was not found and the newest for the game version stands in. */
    val exact: Boolean = true,
)

/** What joining a server takes: the game, the loader, the mods — and what could not be found. */
internal data class ServerPlan(
    val kind: Kind,
    val minecraft: String,
    val loader: Loader,
    val loaderVersion: String?,
    val mods: List<PlannedMod>,
    val missing: List<String>,
    /** How many mods the server named, found or not. */
    val total: Int,
) {
    enum class Kind { Yumi, Forge, Modded, Unknown }
}

/**
 * Reads what a server's ping says about its mods, and turns it into a pack. A server from Lumi
 * Launcher names its exact pack ([PackShare]); Forge lists mod ids and versions, which are looked up
 * on Modrinth, then on CurseForge; NeoForge, Fabric and plain servers say nothing about mods, so
 * only the game version and the loader can be matched.
 */
internal object ServerPacks {
    /** Entries of Forge's list that are Forge and the game themselves. */
    private val BUILT_IN = setOf("minecraft", "forge", "fml", "mcp", "neoforge", "forgeclient", "javafml", "lowcodefml")

    /** Forge marks a mod the client need not have with this instead of a version. */
    private const val SERVER_ONLY = "OHNOES"

    fun hints(status: JsonObject, shared: SharedPack?): ModHints? {
        if (shared != null) return ModHints.Yumi(shared)
        status.obj("forgeData")?.let { forge ->
            val encoded = forge.str("d")
            if (encoded != null) return decodeForge(encoded)
            val mods = forge["mods"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                .mapNotNull { m -> m.str("modId")?.let { ForgeMod(it, m.str("modmarker")?.takeUnless { v -> v.startsWith(SERVER_ONLY) }) } }
            return ModHints.Forge(mods, forge.bool("truncated"))
        }
        // FML before 1.13.
        status.obj("modinfo")?.let { info ->
            val mods = info["modList"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                .mapNotNull { m -> m.str("modid")?.let { ForgeMod(it, m.str("version")) } }
            return ModHints.Forge(mods, false)
        }
        if ((status["isModded"] as? JsonPrimitive)?.contentOrNull == "true") return ModHints.Modded
        return null
    }

    /**
     * Forge 1.18 and later pack the list into `d`: fifteen bits a character, the byte count in the
     * first two. The bytes: truncated flag, mod count, then per mod a varint whose low bit marks
     * a server-only mod (no version follows) and the rest counts its channels, the id, the version,
     * and the channels — whose version is a string up to 1.20.1 and a varint after, so both are tried.
     */
    private fun decodeForge(encoded: String): ModHints.Forge? {
        if (encoded.length < 2) return null
        val bytes = unpack(encoded)
        for (stringVersions in listOf(true, false)) {
            val parsed = runCatching { readForge(bytes, stringVersions) }.getOrNull() ?: continue
            if (parsed.mods.all { MOD_ID.matches(it.id) }) return parsed
        }
        return null
    }

    private val MOD_ID = Regex("^[a-z][a-z0-9_.-]{0,63}$")

    private fun unpack(s: String): ByteArray {
        val size = s[0].code or (s[1].code shl 15)
        val out = ByteArray(size)
        var at = 0
        var buffer = 0
        var bits = 0
        var i = 2
        while (i < s.length) {
            while (bits >= 8 && at < size) {
                out[at++] = buffer.toByte()
                buffer = buffer ushr 8
                bits -= 8
            }
            buffer = buffer or ((s[i].code and 0x7FFF) shl bits)
            bits += 15
            i++
        }
        while (at < size) {
            out[at++] = buffer.toByte()
            buffer = buffer ushr 8
        }
        return out
    }

    private fun readForge(bytes: ByteArray, stringVersions: Boolean): ModHints.Forge {
        val r = Reader(bytes)
        val truncated = r.bool()
        val count = r.ushort()
        val mods = List(count) {
            val flag = r.varInt()
            val id = r.string()
            val version = if (flag and 1 != 0) null else r.string()
            repeat(flag ushr 1) { r.string(); if (stringVersions) r.string() else r.varInt(); r.bool() }
            ForgeMod(id, version)
        }
        repeat(r.varInt()) { r.string(); if (stringVersions) r.string() else r.varInt(); r.bool() }
        return ModHints.Forge(mods, truncated)
    }

    private class Reader(private val b: ByteArray) {
        private var p = 0
        fun bool() = b[p++] != 0.toByte()
        fun ushort() = ((b[p++].toInt() and 0xFF) shl 8) or (b[p++].toInt() and 0xFF)
        fun varInt(): Int {
            var value = 0
            var shift = 0
            while (true) {
                val x = b[p++].toInt()
                value = value or ((x and 0x7F) shl shift)
                if (x and 0x80 == 0) return value
                shift += 7
                if (shift > 28) throw IOException("varint")
            }
        }
        fun string(): String {
            val n = varInt()
            if (n < 0 || p + n > b.size) throw IOException("string")
            return String(b, p, n, Charsets.UTF_8).also { p += n }
        }
    }

    /**
     * The pack for a server that answered [ping]. [lookup] tells a real game version from a
     * server's fancy version name; a CurseForge [key] lets CurseForge fill Modrinth's gaps.
     */
    suspend fun plan(ping: PingResult, lookup: (String) -> VersionEntry?, key: String): ServerPlan {
        val curse = key.trim().takeIf { it.isNotEmpty() }?.let(::CurseForge)
        return when (val hints = ping.mods) {
            is ModHints.Yumi -> fromShared(hints.pack, lookup, curse)
            is ModHints.Forge -> fromForge(hints, gameVersion(ping, lookup), curse)
            ModHints.Modded -> ServerPlan(ServerPlan.Kind.Modded, gameVersion(ping, lookup), Loader.NeoForge, null, emptyList(), emptyList(), 0)
            null -> ServerPlan(ServerPlan.Kind.Unknown, gameVersion(ping, lookup), Loader.Vanilla, null, emptyList(), emptyList(), 0)
        }
    }

    /**
     * The game version in the server's version name — "1.21.1", "Paper 1.21.1", "Velocity
     * 1.8.x-1.21.x" — as a real one; the newest when it names several.
     */
    private fun gameVersion(ping: PingResult, lookup: (String) -> VersionEntry?): String =
        Regex("""\b\d+\.\d+(?:\.\d+)?\b""").findAll(ping.version).map { it.value }.lastOrNull { lookup(it) != null }
            ?: throw IOException("Не понять, на какой версии сервер: «${ping.version}»")

    private suspend fun fromShared(pack: SharedPack, lookup: (String) -> VersionEntry?, curse: CurseForge?): ServerPlan {
        if (lookup(pack.minecraft) == null) throw IOException("Сервер на неизвестной версии ${pack.minecraft}")
        val loaderVersion = pack.loaderVersion?.takeIf { SAFE_VERSION.matches(it) }
        val mods = mutableListOf<PlannedMod>()
        val missing = pack.missing.map { it.take(80) }.toMutableList()

        val versions = Modrinth.versions(pack.modrinth)
        val projects = runCatching { Modrinth.projects(versions.mapNotNull { it.str("project_id") }) }.getOrDefault(emptyList()).associateBy { it.str("id") }
        for (v in versions) {
            val project = projects[v.str("project_id")]
            modrinthFile(v, project?.str("title"), project?.str("icon_url"), exact = true)?.let(mods::add)
        }
        val found = versions.mapNotNull { it.str("id") }.toSet()
        pack.modrinth.filter { it !in found }.forEach { missing += "Modrinth $it" }

        if (pack.curseForge.isNotEmpty()) {
            if (curse == null) {
                missing += "${pack.curseForge.size} с CurseForge — добавьте ключ CurseForge в настройках"
            } else {
                val files = curse.files(pack.curseForge.map { it.file })
                val titles = runCatching { curse.mods(files.mapNotNull { it.long("modId") }) }.getOrDefault(emptyList()).associateBy { it.long("id") }
                for (f in files) {
                    val title = titles[f.long("modId")]
                    curseFile(f, title?.str("name"), title?.obj("logo")?.str("thumbnailUrl"), exact = true)?.let(mods::add)
                        ?: run { missing += f.str("fileName") ?: "CurseForge ${f.long("id")}" }
                }
            }
        }
        return ServerPlan(ServerPlan.Kind.Yumi, pack.minecraft, pack.loader, loaderVersion, mods, missing, pack.modrinth.size + pack.curseForge.size + pack.missing.size)
    }

    private val SAFE_VERSION = Regex("^[A-Za-z0-9._+-]{1,40}$")

    private suspend fun fromForge(hints: ModHints.Forge, minecraft: String, curse: CurseForge?): ServerPlan {
        val forgeVersion = hints.mods.firstOrNull { it.id == "forge" }?.version
        val loaderVersion = forgeVersion?.takeIf { v -> runCatching { Loaders.versions(Loader.Forge, minecraft).any { it.id == v } }.getOrDefault(false) }
        val wanted = hints.mods.filter { it.version != null && it.id.lowercase() !in BUILT_IN }.distinctBy { it.id }
        val bySlug = runCatching { slugGuesses(wanted, minecraft) }.getOrDefault(emptyMap())
        val gate = Semaphore(6)
        val results = coroutineScope {
            wanted.map { mod -> async { gate.withPermit { mod to runCatching { findForge(mod, bySlug[mod.id], minecraft, curse) }.getOrNull() } } }.awaitAll()
        }
        val mods = results.mapNotNull { it.second }.distinctBy { it.fileName }
        val missing = results.filter { it.second == null }.map { (mod, _) -> "${mod.id} ${mod.version.orEmpty()}".trim() }.toMutableList()
        if (hints.truncated) missing += "список модов сервер отдал не целиком"
        return ServerPlan(ServerPlan.Kind.Forge, minecraft, Loader.Forge, loaderVersion, mods, missing, wanted.size)
    }

    /**
     * Modrinth projects for the mods' ids, from guesses at their slugs: the id itself, with
     * underscores as hyphens, split into two words at every place — `farmersdelight` is
     * `farmers-delight` — and each of those with `-forge` after. All in a few requests: the bulk
     * endpoint takes slugs as well as ids. Only a Forge mod for this game version counts.
     */
    private suspend fun slugGuesses(mods: List<ForgeMod>, minecraft: String): Map<String, JsonObject> {
        val guesses = mods.associate { mod ->
            val id = mod.id.lowercase()
            val base = listOf(id, id.replace('_', '-')) + (1 until id.length).filter { id[it - 1].isLetter() && id[it].isLetterOrDigit() }.map { id.substring(0, it) + "-" + id.substring(it) }
            mod.id to (base + base.map { "$it-forge" }).distinct()
        }
        val found = Modrinth.projects(guesses.values.flatten()).filter { p ->
            p.str("project_type") == "mod" &&
                p["loaders"]?.jsonArray.orEmpty().any { it.jsonPrimitive.contentOrNull == "forge" } &&
                p["game_versions"]?.jsonArray.orEmpty().any { it.jsonPrimitive.contentOrNull == minecraft }
        }.associateBy { it.str("slug") }
        return guesses.mapNotNull { (id, slugs) -> slugs.firstNotNullOfOrNull { found[it] }?.takeIf { sameMod(id, it.str("slug"), it.str("title")) }?.let { id to it } }.toMap()
    }

    /** A Forge mod by its id: Modrinth first — a guessed slug, then a search — then CurseForge the same way. */
    private suspend fun findForge(mod: ForgeMod, guessed: JsonObject?, minecraft: String, curse: CurseForge?): PlannedMod? {
        val project = guessed ?: Modrinth.searchRaw(mod.id, minecraft, Loader.Forge).firstOrNull { sameMod(mod.id, it.str("slug"), it.str("title")) }
        val projectId = project?.let { it.str("id") ?: it.str("project_id") }
        if (project != null && projectId != null) {
            val versions = Modrinth.projectVersions(projectId, minecraft, Loader.Forge)
            val exact = versions.firstOrNull { matches(it.str("version_number"), mod.version) }
            val pick = exact ?: versions.firstOrNull { it.str("version_type") == "release" } ?: versions.firstOrNull()
            if (pick != null) return modrinthFile(pick, project.str("title"), project.str("icon_url"), exact != null)
        }
        if (curse == null) return null
        val cfProject = curse.bySlug(mod.id).firstOrNull()
            ?: curse.searchRaw(mod.id, minecraft, Loader.Forge).firstOrNull { sameMod(mod.id, it.str("slug"), it.str("name")) }
            ?: return null
        val modId = cfProject.long("id") ?: return null
        val files = curse.projectFiles(modId, minecraft, Loader.Forge).ifEmpty { curse.projectFiles(modId, minecraft, null) }
            .filter { "fabric" !in it.str("fileName").orEmpty().lowercase() }
        val exact = files.firstOrNull { matches(it.str("displayName"), mod.version) || matches(it.str("fileName"), mod.version) }
        val pick = exact ?: files.firstOrNull { it.long("releaseType") == 1L } ?: files.firstOrNull() ?: return null
        return curseFile(pick, cfProject.str("name"), cfProject.obj("logo")?.str("thumbnailUrl"), exact != null)
    }

    /** Whether a catalogue's project is the mod with this id: the id is its slug or its name, give or take "-forge" or "api". */
    private fun sameMod(id: String, slug: String?, title: String?): Boolean {
        val want = normal(id)
        return listOfNotNull(slug, title).map(::normal).any { it == want || it.removeSuffix("forge") == want || it.removeSuffix("api") == want || it.removeSuffix("mod") == want }
    }

    private fun normal(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** A version name that carries the server's mod version: "1.20.1-15.2.0.27" for "15.2.0.27". */
    private fun matches(name: String?, version: String?): Boolean {
        if (name == null || version.isNullOrBlank() || version.startsWith("0.0NONE")) return false
        return version.lowercase() in name.lowercase()
    }

    private fun modrinthFile(v: JsonObject, title: String?, icon: String?, exact: Boolean): PlannedMod? {
        val files = v["files"]?.jsonArray.orEmpty().map { it.jsonObject }
        val file = files.firstOrNull { it.bool("primary") } ?: files.firstOrNull() ?: return null
        val name = file.str("filename")?.takeIf(::safeName) ?: return null
        val url = file.str("url")?.takeIf { it.startsWith("https://") } ?: return null
        return PlannedMod(name, url, file.obj("hashes")?.str("sha1"), file.long("size"), ModSource.Modrinth,
            v.str("project_id") ?: return null, title ?: name.removeSuffix(".jar"), icon, v.str("version_number").orEmpty(), exact)
    }

    private fun curseFile(f: JsonObject, title: String?, icon: String?, exact: Boolean): PlannedMod? {
        val name = f.str("fileName")?.takeIf(::safeName) ?: return null
        // Null when the author allows downloads only from CurseForge's own site.
        val url = f.str("downloadUrl")?.takeIf { it.startsWith("https://") } ?: return null
        val sha1 = f["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value")
        return PlannedMod(name, url, sha1, f.long("fileLength"), ModSource.CurseForge, f.long("modId")?.toString() ?: return null,
            title ?: name.removeSuffix(".jar"), icon, f.str("displayName").orEmpty(), exact)
    }

    private fun safeName(name: String) = name.endsWith(".jar") && '/' !in name && '\\' !in name && ".." !in name && ':' !in name
}

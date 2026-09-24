package com.yumi.launcher.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

enum class ModSource(val label: String) { Modrinth("Modrinth"), CurseForge("CurseForge") }

/** A search result, the same shape whichever catalogue it came from. */
data class ModHit(
    val source: ModSource,
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val iconUrl: String?,
    val downloads: Long,
    val pageUrl: String,
)

/**
 * The file to install for one pack. [url] is null when the author forbids downloads through other
 * launchers — CurseForge lets them — and the file has to be fetched from the site by hand.
 */
data class ModFile(
    val fileName: String,
    val url: String?,
    val sha1: String?,
    val size: Long?,
    val version: String,
    /** Required dependencies, as ids in the same catalogue. */
    val requires: List<String>,
)

/** What a mod's own page says beyond the search result: the long description, pictures, tags. */
data class ModDetails(
    val body: String,
    val gallery: List<String>,
    val categories: List<String>,
    val updated: String,
    /** Where the mod is needed — "required", "optional" or "unsupported"; null when the catalogue does not say. */
    val client: String?,
    val server: String?,
    val license: String?,
)

internal interface ModCatalog {
    suspend fun search(query: String, minecraft: String, loader: Loader, offset: Int): List<ModHit>

    /** The newest file of [projectId] for this game version and loader, or null if there is none. */
    suspend fun file(projectId: String, minecraft: String, loader: Loader): ModFile?

    /** Title and icon of a project, for dependencies pulled in by id. */
    suspend fun describe(projectId: String): ModHit

    /** The project's page: description, gallery, categories. */
    suspend fun details(projectId: String): ModDetails
}

/** Modrinth's public API: no key, only a User-Agent that says who is asking. */
internal object Modrinth : ModCatalog {
    private const val API = "https://api.modrinth.com/v2"

    private fun loaders(loader: Loader) = when (loader) {
        // Quilt runs Fabric mods, so its search takes both.
        Loader.Quilt -> listOf("quilt", "fabric")
        else -> listOf(loader.name.lowercase())
    }

    override suspend fun search(query: String, minecraft: String, loader: Loader, offset: Int): List<ModHit> {
        val facets = "[[\"project_type:mod\"],[\"versions:$minecraft\"],[${loaders(loader).joinToString(",") { "\"categories:$it\"" }}]]"
        val url = "$API/search?query=${encode(query)}&facets=${encode(facets)}&limit=20&offset=$offset" +
            (if (query.isBlank()) "&index=downloads" else "")
        val hits = LauncherJson.parseToJsonElement(Http.text(url)).jsonObject["hits"]!!.jsonArray
        return hits.map { hit(it.jsonObject, idKey = "project_id") }
    }

    override suspend fun file(projectId: String, minecraft: String, loader: Loader): ModFile? {
        val url = "$API/project/${encode(projectId)}/version?loaders=${encode(loaders(loader).joinToString(",", "[", "]") { "\"$it\"" })}" +
            "&game_versions=${encode("[\"$minecraft\"]")}"
        val versions = LauncherJson.parseToJsonElement(Http.text(url)).jsonArray.map { it.jsonObject }
        // Newest first; a release beats a newer beta.
        val version = versions.firstOrNull { it.str("version_type") == "release" } ?: versions.firstOrNull() ?: return null
        val files = version["files"]!!.jsonArray.map { it.jsonObject }
        val file = files.firstOrNull { it.bool("primary") } ?: files.first()
        return ModFile(
            fileName = file.str("filename")!!,
            url = file.str("url"),
            sha1 = file.obj("hashes")?.str("sha1"),
            size = file.long("size"),
            version = version.str("version_number") ?: "",
            requires = version["dependencies"]?.jsonArray.orEmpty().map { it.jsonObject }
                .filter { it.str("dependency_type") == "required" }
                .mapNotNull { it.str("project_id") },
        )
    }

    override suspend fun describe(projectId: String): ModHit =
        hit(LauncherJson.parseToJsonElement(Http.text("$API/project/${encode(projectId)}")).jsonObject, idKey = "id")

    override suspend fun details(projectId: String): ModDetails {
        val o = LauncherJson.parseToJsonElement(Http.text("$API/project/${encode(projectId)}")).jsonObject
        return ModDetails(
            body = plainText(o.str("body").orEmpty()),
            gallery = o["gallery"]?.jsonArray.orEmpty().map { it.jsonObject }.sortedByDescending { it.bool("featured") }.mapNotNull { it.str("url") },
            categories = o["categories"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
            updated = o.str("updated")?.take(10).orEmpty(),
            client = o.str("client_side"),
            server = o.str("server_side"),
            license = o.obj("license")?.str("id")?.takeIf { it.isNotBlank() && !it.startsWith("LicenseRef") },
        )
    }

    /**
     * Whether each file is wanted on a server — `required`, `optional` or `unsupported` — keyed by
     * the file's SHA-1. Works for any jar Modrinth hosts, however it got into the pack.
     */
    suspend fun serverSides(sha1s: Collection<String>): Map<String, String> {
        if (sha1s.isEmpty()) return emptyMap()
        val body = buildJsonObject {
            put("hashes", JsonArray(sha1s.map { JsonPrimitive(it) }))
            put("algorithm", JsonPrimitive("sha1"))
        }
        val (status, text) = Http.post("$API/version_files", body.toString())
        if (status != 200) return emptyMap()
        val projectByHash = LauncherJson.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonObject.str("project_id")!! }
        if (projectByHash.isEmpty()) return emptyMap()
        val ids = projectByHash.values.toSet().joinToString(",", "[", "]") { "\"$it\"" }
        val sides = LauncherJson.parseToJsonElement(Http.text("$API/projects?ids=${encode(ids)}")).jsonArray
            .associate { it.jsonObject.str("id")!! to (it.jsonObject.str("server_side") ?: "unknown") }
        return projectByHash.mapValues { sides[it.value] ?: "unknown" }
    }

    private fun hit(o: JsonObject, idKey: String) = ModHit(
        source = ModSource.Modrinth,
        id = o.str(idKey)!!,
        title = o.str("title") ?: "",
        author = o.str("author") ?: "",
        description = o.str("description") ?: "",
        iconUrl = o.str("icon_url"),
        downloads = o.long("downloads") ?: 0,
        pageUrl = "https://modrinth.com/mod/${o.str("slug") ?: o.str(idKey)}",
    )
}

/**
 * CurseForge's official API. It needs a key — free, from console.curseforge.com — which the player
 * pastes into the settings; a launcher cannot ship one of its own.
 */
internal class CurseForge(private val key: String) : ModCatalog {
    private val headers = mapOf("x-api-key" to key, "Accept" to "application/json")

    private fun loaderType(loader: Loader) = when (loader) {
        Loader.Forge -> 1
        Loader.Fabric -> 4
        Loader.Quilt -> 5
        Loader.NeoForge -> 6
        Loader.Vanilla -> 0
    }

    override suspend fun search(query: String, minecraft: String, loader: Loader, offset: Int): List<ModHit> {
        val url = "$API/mods/search?gameId=$MINECRAFT&classId=$MODS&searchFilter=${encode(query)}" +
            "&gameVersion=${encode(minecraft)}&modLoaderType=${loaderType(loader)}&sortField=2&sortOrder=desc&pageSize=20&index=$offset"
        return data(url).jsonArray.map { hit(it.jsonObject) }
    }

    override suspend fun file(projectId: String, minecraft: String, loader: Loader): ModFile? {
        val url = "$API/mods/$projectId/files?gameVersion=${encode(minecraft)}&modLoaderType=${loaderType(loader)}&pageSize=20"
        val files = data(url).jsonArray.map { it.jsonObject }
        // releaseType 1 is a release; newest first either way.
        val file = files.firstOrNull { it.long("releaseType") == 1L } ?: files.firstOrNull() ?: return null
        return ModFile(
            fileName = file.str("fileName")!!,
            url = file.str("downloadUrl"),
            sha1 = file["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value"),
            size = file.long("fileLength"),
            version = file.str("displayName") ?: "",
            requires = file["dependencies"]?.jsonArray.orEmpty().map { it.jsonObject }
                .filter { it.long("relationType") == 3L }
                .mapNotNull { it.long("modId")?.toString() },
        )
    }

    override suspend fun describe(projectId: String): ModHit = hit(data("$API/mods/$projectId").jsonObject)

    override suspend fun details(projectId: String): ModDetails {
        val o = data("$API/mods/$projectId").jsonObject
        val html = runCatching { data("$API/mods/$projectId/description").jsonPrimitive.content }.getOrDefault("")
        return ModDetails(
            body = plainText(html),
            gallery = o["screenshots"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.str("url") ?: it.jsonObject.str("thumbnailUrl") },
            categories = o["categories"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.str("name") },
            updated = o.str("dateModified")?.take(10).orEmpty(),
            client = null,
            server = null,
            license = null,
        )
    }

    private suspend fun data(url: String) = LauncherJson.parseToJsonElement(Http.text(url, headers)).jsonObject["data"]!!

    private fun hit(o: JsonObject) = ModHit(
        source = ModSource.CurseForge,
        id = o.long("id").toString(),
        title = o.str("name") ?: "",
        author = o["authors"]?.jsonArray?.firstOrNull()?.jsonObject?.str("name") ?: "",
        description = o.str("summary") ?: "",
        iconUrl = o.obj("logo")?.str("thumbnailUrl"),
        downloads = o["downloadCount"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0,
        pageUrl = o.obj("links")?.str("websiteUrl") ?: "https://www.curseforge.com/minecraft/mc-mods/${o.str("slug")}",
    )

    private companion object {
        const val API = "https://api.curseforge.com/v1"
        const val MINECRAFT = 432
        const val MODS = 6
    }
}

private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

/**
 * A project description — Modrinth's Markdown or CurseForge's HTML — as plain paragraphs: pictures
 * and markup dropped, links reduced to their text, lists to bullets. Enough to read in the launcher;
 * the page itself is one click away for the rest.
 */
internal fun plainText(source: String): String = source
    .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
    .replace(Regex("(?i)<br\\s*/?>|</p>|</h\\d>|</div>|</li>"), "\n")
    .replace(Regex("(?i)<li[^>]*>"), "• ")
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
    .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
    .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
    .replace(Regex("(?<=^|\\s)[*_]([^*_\\n]+?)[*_](?=\\s|$|[.,!?:;)])", RegexOption.MULTILINE), "$1")
    .replace(Regex("(?m)^\\s*([-*_])\\1{2,}\\s*$"), "")
    .replace(Regex("`{1,3}"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    .lines().joinToString("\n") { it.trimEnd() }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

package com.yumi.launcher.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** A modpack in a catalogue's search results: what the list shows before any release is picked. */
internal data class PackHit(
    val id: String,
    val name: String,
    val summary: String,
    val icon: String?,
    val downloads: Long,
    val author: String = "",
    /** Minecraft versions it has releases for, newest first — as far as the search tells. */
    val versions: List<String> = emptyList(),
    val loaders: List<Loader> = emptyList(),
    val pageUrl: String = "",
)

/** Release, beta or alpha — what the author called the build. */
internal enum class ReleaseType(val label: String) { Release("Релиз"), Beta("Бета"), Alpha("Альфа") }

/** One downloadable build of a modpack. */
internal data class PackRelease(
    val id: String,
    val name: String,
    val date: String,
    val url: String,
    val size: Long?,
    val sha1: String?,
    val serverId: Long? = null,
    val gameVersions: List<String> = emptyList(),
    val loaders: List<Loader> = emptyList(),
    val type: ReleaseType = ReleaseType.Release,
    val downloads: Long = 0,
)

/**
 * Ready-made modpacks from Modrinth and CurseForge. Search takes an optional Minecraft version and
 * loader, which both catalogues filter by on their side; a pack's releases come back in full, so
 * the picker can offer every version the pack was made for.
 */
internal object PackCatalog {
    suspend fun search(source: ModSource, query: String, version: String, loader: Loader?, key: String): List<PackHit> = when (source) {
        ModSource.Modrinth -> {
            val facets = buildList {
                add("[\"project_type:modpack\"]")
                if (version.isNotBlank()) add("[\"versions:$version\"]")
                if (loader != null && loader != Loader.Vanilla) add("[\"categories:${loader.name.lowercase()}\"]")
            }.joinToString(",", "[", "]")
            val url = "https://api.modrinth.com/v2/search?query=${enc(query)}&facets=${enc(facets)}&limit=30" +
                if (query.isBlank()) "&index=downloads" else ""
            LauncherJson.parseToJsonElement(Http.text(url)).jsonObject["hits"]!!.jsonArray.map { element ->
                val o = element.jsonObject
                PackHit(
                    id = o.str("project_id")!!,
                    name = o.str("title") ?: "",
                    summary = o.str("description") ?: "",
                    icon = o.str("icon_url"),
                    downloads = o.long("downloads") ?: 0,
                    author = o.str("author") ?: "",
                    versions = o["versions"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.filter(::isRelease).sortedWith(VersionOrder).reversed(),
                    loaders = o["categories"]?.jsonArray.orEmpty().mapNotNull { loaderOf(it.jsonPrimitive.content) }.distinct(),
                    pageUrl = "https://modrinth.com/modpack/${o.str("slug") ?: o.str("project_id")}",
                )
            }
        }
        ModSource.CurseForge -> {
            require(key.isNotBlank()) { "Добавьте ключ CurseForge в настройках" }
            val url = "https://api.curseforge.com/v1/mods/search?gameId=432&classId=4471&searchFilter=${enc(query)}" +
                (if (version.isBlank()) "" else "&gameVersion=${enc(version)}") +
                (if (loader == null || loader == Loader.Vanilla) "" else "&modLoaderType=${curseLoader(loader)}") +
                "&sortField=6&sortOrder=desc&pageSize=30"
            CurseForge.get(url, key)["data"]!!.jsonArray.map { element ->
                val o = element.jsonObject
                val latest = o["latestFilesIndexes"]?.jsonArray.orEmpty().map { it.jsonObject }
                PackHit(
                    id = o.long("id").toString(),
                    name = o.str("name") ?: "",
                    summary = o.str("summary") ?: "",
                    icon = o.obj("logo")?.str("thumbnailUrl"),
                    downloads = o["downloadCount"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0,
                    author = o["authors"]?.jsonArray?.firstOrNull()?.jsonObject?.str("name") ?: "",
                    versions = latest.mapNotNull { it.str("gameVersion") }.filter(::isRelease).distinct().sortedWith(VersionOrder).reversed(),
                    loaders = latest.mapNotNull { index -> index.long("modLoader")?.let(::loaderOfCurse) }.distinct(),
                    pageUrl = o.obj("links")?.str("websiteUrl") ?: "https://www.curseforge.com/minecraft/modpacks/${o.str("slug")}",
                )
            }
        }
    }

    /** Every release of a pack, newest first. */
    suspend fun releases(source: ModSource, projectId: String, key: String): List<PackRelease> = when (source) {
        ModSource.Modrinth -> {
            val url = "https://api.modrinth.com/v2/project/${enc(projectId)}/version"
            LauncherJson.parseToJsonElement(Http.text(url)).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val file = o["files"]?.jsonArray?.map { it.jsonObject }?.let { files ->
                    files.firstOrNull { it.bool("primary") } ?: files.firstOrNull()
                } ?: return@mapNotNull null
                PackRelease(
                    id = o.str("id")!!,
                    name = o.str("name")?.takeIf { it.isNotBlank() } ?: o.str("version_number") ?: "",
                    date = o.str("date_published")?.take(10) ?: "",
                    url = file.str("url") ?: return@mapNotNull null,
                    size = file.long("size"),
                    sha1 = file.obj("hashes")?.str("sha1"),
                    gameVersions = o["game_versions"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.sortedWith(VersionOrder).reversed(),
                    loaders = o["loaders"]?.jsonArray.orEmpty().mapNotNull { loaderOf(it.jsonPrimitive.content) },
                    type = when (o.str("version_type")) {
                        "beta" -> ReleaseType.Beta
                        "alpha" -> ReleaseType.Alpha
                        else -> ReleaseType.Release
                    },
                    downloads = o.long("downloads") ?: 0,
                )
            }
        }
        ModSource.CurseForge -> {
            require(key.isNotBlank()) { "Добавьте ключ CurseForge в настройках" }
            val url = "https://api.curseforge.com/v1/mods/$projectId/files?pageSize=50"
            CurseForge.get(url, key)["data"]!!.jsonArray
                .map { it.jsonObject }
                // A pack's server archive is a file of its own; it is offered with its client release instead.
                .filterNot { it.bool("isServerPack") }
                .mapNotNull { o -> curseRelease(o) }
        }
    }

    suspend fun serverRelease(projectId: String, fileId: Long, key: String): PackRelease {
        val o = CurseForge.get("https://api.curseforge.com/v1/mods/$projectId/files/$fileId", key)["data"]!!.jsonObject
        return curseRelease(o) ?: error("Автор запретил скачивание серверного архива")
    }

    private fun curseRelease(o: JsonObject): PackRelease? {
        val tags = o["gameVersions"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        return PackRelease(
            id = o.long("id").toString(),
            name = o.str("displayName") ?: o.str("fileName") ?: "",
            date = o.str("fileDate")?.take(10) ?: "",
            url = o.str("downloadUrl") ?: return null,
            size = o.long("fileLength"),
            sha1 = o["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value"),
            serverId = o.long("serverPackFileId"),
            gameVersions = tags.filter(::isRelease).sortedWith(VersionOrder).reversed(),
            loaders = tags.mapNotNull(::loaderOf).distinct(),
            type = when (o.long("releaseType")) {
                2L -> ReleaseType.Beta
                3L -> ReleaseType.Alpha
                else -> ReleaseType.Release
            },
            downloads = o["downloadCount"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0,
        )
    }

    /** "1.20.1", "1.21", "26.3" — not snapshots, not CurseForge's "Forge" and "Client" tags. */
    fun isRelease(version: String) = Regex("""^\d+\.\d+(\.\d+)?$""").matches(version)

    /** Numeric, part by part: 1.9 before 1.10. */
    val VersionOrder = Comparator<String> { a, b ->
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        (0 until maxOf(x.size, y.size)).firstNotNullOfOrNull { i -> (x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }).takeIf { it != 0 } } ?: 0
    }

    private fun loaderOf(tag: String): Loader? = when (tag.lowercase()) {
        "fabric" -> Loader.Fabric
        "quilt" -> Loader.Quilt
        "forge" -> Loader.Forge
        "neoforge" -> Loader.NeoForge
        else -> null
    }

    private fun curseLoader(loader: Loader) = when (loader) {
        Loader.Forge -> 1
        Loader.Fabric -> 4
        Loader.Quilt -> 5
        Loader.NeoForge -> 6
        Loader.Vanilla -> 0
    }

    private fun loaderOfCurse(type: Long): Loader? = when (type) {
        1L -> Loader.Forge
        4L -> Loader.Fabric
        5L -> Loader.Quilt
        6L -> Loader.NeoForge
        else -> null
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}

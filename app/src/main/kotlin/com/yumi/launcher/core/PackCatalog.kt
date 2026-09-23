package com.yumi.launcher.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

internal data class PackHit(val id: String, val name: String, val summary: String, val icon: String?, val downloads: Long)
internal data class PackRelease(val id: String, val name: String, val date: String, val url: String, val size: Long?, val sha1: String?, val serverId: Long? = null)

internal object PackCatalog {
    suspend fun search(source: ModSource, query: String, version: String, key: String): List<PackHit> = when (source) {
        ModSource.Modrinth -> {
            val facets = buildList {
                add("[\"project_type:modpack\"]")
                if (version.isNotBlank()) add("[\"versions:$version\"]")
            }.joinToString(",", "[", "]")
            val url = "https://api.modrinth.com/v2/search?query=${enc(query)}&facets=${enc(facets)}&limit=20" +
                if (query.isBlank()) "&index=downloads" else ""
            LauncherJson.parseToJsonElement(Http.text(url)).jsonObject["hits"]!!.jsonArray.map { element ->
                val o = element.jsonObject
                PackHit(o.str("project_id")!!, o.str("title") ?: "", o.str("description") ?: "",
                    o.str("icon_url"), o.long("downloads") ?: 0)
            }
        }
        ModSource.CurseForge -> {
            require(key.isNotBlank()) { "Добавьте ключ CurseForge в настройках" }
            val url = "https://api.curseforge.com/v1/mods/search?gameId=432&classId=4471&searchFilter=${enc(query)}" +
                (if (version.isBlank()) "" else "&gameVersion=${enc(version)}") + "&sortField=6&sortOrder=desc&pageSize=20"
            val data = LauncherJson.parseToJsonElement(Http.text(url, headers(key))).jsonObject["data"]!!.jsonArray
            data.map { element ->
                val o = element.jsonObject
                PackHit(o.long("id").toString(), o.str("name") ?: "", o.str("summary") ?: "",
                    o.obj("logo")?.str("thumbnailUrl"), o.long("downloadCount") ?: 0)
            }
        }
    }

    suspend fun releases(source: ModSource, projectId: String, version: String, key: String): List<PackRelease> = when (source) {
        ModSource.Modrinth -> {
            val url = "https://api.modrinth.com/v2/project/${enc(projectId)}/version" +
                if (version.isBlank()) "" else "?game_versions=${enc("[\"$version\"]")}"
            LauncherJson.parseToJsonElement(Http.text(url)).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val file = o["files"]?.jsonArray?.map { it.jsonObject }?.let { files ->
                    files.firstOrNull { it.bool("primary") } ?: files.firstOrNull()
                } ?: return@mapNotNull null
                PackRelease(o.str("id")!!, o.str("name") ?: o.str("version_number") ?: "", o.str("date_published")?.take(10) ?: "",
                    file.str("url") ?: return@mapNotNull null, file.long("size"), file.obj("hashes")?.str("sha1"))
            }
        }
        ModSource.CurseForge -> {
            require(key.isNotBlank()) { "Добавьте ключ CurseForge в настройках" }
            val url = "https://api.curseforge.com/v1/mods/$projectId/files?pageSize=50" +
                if (version.isBlank()) "" else "&gameVersion=${enc(version)}"
            LauncherJson.parseToJsonElement(Http.text(url, headers(key))).jsonObject["data"]!!.jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                PackRelease(o.long("id").toString(), o.str("displayName") ?: o.str("fileName") ?: "", o.str("fileDate")?.take(10) ?: "",
                    o.str("downloadUrl") ?: return@mapNotNull null, o.long("fileLength"),
                    o["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value"), o.long("serverPackFileId"))
            }
        }
    }

    suspend fun serverRelease(projectId: String, fileId: Long, key: String): PackRelease {
        val o = LauncherJson.parseToJsonElement(Http.text("https://api.curseforge.com/v1/mods/$projectId/files/$fileId", headers(key)))
            .jsonObject["data"]!!.jsonObject
        return PackRelease(fileId.toString(), o.str("displayName") ?: "Серверная сборка", o.str("fileDate")?.take(10) ?: "",
            o.str("downloadUrl") ?: error("Автор запретил скачивание серверного архива"), o.long("fileLength"),
            o["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value"))
    }

    private fun headers(key: String) = mapOf("x-api-key" to key, "Accept" to "application/json")
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}

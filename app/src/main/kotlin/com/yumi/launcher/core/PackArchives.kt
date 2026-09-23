package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipFile

internal data class PackInfo(
    val name: String,
    val version: String,
    val loader: Loader,
    val loaderVersion: String?,
    val source: ModSource,
)

/** Imports the two public modpack archive formats into a pack's game and server folders. */
internal object PackArchives {
    suspend fun inspect(archive: File): PackInfo = withContext(Dispatchers.IO) {
        ZipFile(archive).use { zip ->
            when {
                zip.getEntry("modrinth.index.json") != null -> modrinthInfo(index(zip, "modrinth.index.json"))
                zip.getEntry("manifest.json") != null -> curseForgeInfo(index(zip, "manifest.json"))
                else -> throw IOException("В архиве нет modrinth.index.json или manifest.json")
            }
        }
    }

    suspend fun install(archive: File, packFolder: File, key: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        ZipFile(archive).use { zip ->
            val game = packFolder.resolve("minecraft")
            val server = packFolder.resolve("server")
            val index = if (zip.getEntry("modrinth.index.json") != null) index(zip, "modrinth.index.json") else index(zip, "manifest.json")
            val (downloads, shared) = if (zip.getEntry("modrinth.index.json") != null) modrinthFiles(index, game, server)
                else curseForgeFiles(index, game, key) to emptyList()
            Http.downloadAll(downloads, onProgress = onProgress)
            for ((from, to) in shared) {
                to.parentFile.mkdirs()
                from.copyTo(to, overwrite = true)
            }
            val prefixes = if (zip.getEntry("modrinth.index.json") != null) listOf(
                "overrides/" to game, "client-overrides/" to game,
                "overrides/" to server, "server-overrides/" to server,
            ) else listOf((index.str("overrides") ?: "overrides").trimEnd('/') + "/" to game)
            for ((prefix, root) in prefixes) extract(zip, prefix, root)
        }
    }

    suspend fun installServerArchive(archive: File, packFolder: File) = withContext(Dispatchers.IO) {
        ZipFile(archive).use { zip -> extract(zip, "", packFolder.resolve("server")) }
    }

    private fun index(zip: ZipFile, name: String): JsonObject =
        LauncherJson.parseToJsonElement(zip.getInputStream(zip.getEntry(name)).bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject

    private fun modrinthInfo(index: JsonObject): PackInfo {
        require(index.long("formatVersion") == 1L && index.str("game") == "minecraft") { "Неподдерживаемый формат Modrinth" }
        val deps = index.obj("dependencies") ?: error("В сборке нет версий Minecraft")
        val minecraft = deps.str("minecraft") ?: error("В сборке нет версии Minecraft")
        val loader = listOf("fabric-loader" to Loader.Fabric, "quilt-loader" to Loader.Quilt, "forge" to Loader.Forge, "neoforge" to Loader.NeoForge)
            .firstOrNull { deps.str(it.first) != null }
        return PackInfo(index.str("name")?.takeIf { it.isNotBlank() } ?: "Сборка Modrinth", minecraft,
            loader?.second ?: Loader.Vanilla, loader?.let { deps.str(it.first) }, ModSource.Modrinth)
    }

    private fun curseForgeInfo(index: JsonObject): PackInfo {
        require(index.str("manifestType") == "minecraft" && index.long("manifestVersion") == 1L) { "Неподдерживаемый формат CurseForge" }
        val minecraft = index.obj("minecraft") ?: error("В сборке нет версии Minecraft")
        val loaderId = minecraft["modLoaders"]?.jsonArray?.firstOrNull()?.jsonObject?.str("id")
        val loader = when (loaderId?.substringBefore('-')) {
            "fabric" -> Loader.Fabric
            "quilt" -> Loader.Quilt
            "forge" -> Loader.Forge
            "neoforge" -> Loader.NeoForge
            else -> if (loaderId == null) Loader.Vanilla else error("Неизвестный загрузчик $loaderId")
        }
        return PackInfo(index.str("name")?.takeIf { it.isNotBlank() } ?: "Сборка CurseForge",
            minecraft.str("version") ?: error("В сборке нет версии Minecraft"), loader,
            loaderId?.substringAfter('-', "")?.takeIf { it.isNotBlank() }, ModSource.CurseForge)
    }

    private fun modrinthFiles(index: JsonObject, game: File, server: File): Pair<List<Download>, List<Pair<File, File>>> {
        modrinthInfo(index)
        val shared = mutableListOf<Pair<File, File>>()
        val jobs = index["files"]?.jsonArray.orEmpty().mapNotNull { element ->
            val file = element.jsonObject
            val path = safePath(file.str("path") ?: error("Файл без пути"))
            val url = file["downloads"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: error("Нет ссылки для $path")
            require(url.startsWith("https://")) { "Небезопасная ссылка для $path" }
            val sha1 = file.obj("hashes")?.str("sha1") ?: error("Нет SHA-1 для $path")
            val size = file.long("fileSize")
            val env = file.obj("env")
            val client = env?.str("client") != "unsupported"
            val serverSide = env?.str("server") != "unsupported"
            if (client && serverSide) shared += game.resolve(path) to server.resolve(path)
            when {
                client -> Download(url, game.resolve(path), sha1, size)
                serverSide -> Download(url, server.resolve(path), sha1, size)
                else -> null
            }
        }
        return jobs to shared
    }

    private suspend fun curseForgeFiles(index: JsonObject, game: File, key: String): List<Download> {
        curseForgeInfo(index)
        require(key.isNotBlank() || index["files"]?.jsonArray.isNullOrEmpty()) { "Для сборки CurseForge нужен ключ API в настройках" }
        val headers = mapOf("x-api-key" to key, "Accept" to "application/json")
        return index["files"]?.jsonArray.orEmpty().map { element ->
            val ref = element.jsonObject
            val project = ref.long("projectID") ?: error("Нет ID проекта CurseForge")
            val fileId = ref.long("fileID") ?: error("Нет ID файла CurseForge")
            val record = LauncherJson.parseToJsonElement(Http.text("https://api.curseforge.com/v1/mods/$project/files/$fileId", headers))
                .jsonObject["data"]!!.jsonObject
            val name = safePath(record.str("fileName") ?: error("Файл CurseForge без имени"))
            require('/' !in name) { "Недопустимое имя файла $name" }
            val url = record.str("downloadUrl") ?: error("Автор запретил скачивание $name через лаунчер")
            val hash = record["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value")
            Download(url, game.resolve("mods").resolve(name), hash, record.long("fileLength"))
        }
    }

    private fun extract(zip: ZipFile, prefix: String, root: File) {
        val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }.toList()
        for (entry in entries) {
            val relative = safePath(entry.name.removePrefix(prefix))
            if (root.name == "server" && relative in setOf("eula.txt", "server.properties", "yumi-server.json")) continue
            val target = root.resolve(relative)
            target.parentFile.mkdirs()
            zip.getInputStream(entry).use { input -> Files.newOutputStream(target.toPath()).use { input.copyTo(it) } }
        }
    }

    private fun safePath(raw: String): String {
        require(raw.isNotBlank() && !raw.contains('\\') && !raw.startsWith('/') && ':' !in raw &&
            raw.split('/').all { it.isNotBlank() && it != "." && it != ".." }) { "Небезопасный путь в сборке: $raw" }
        return raw
    }
}

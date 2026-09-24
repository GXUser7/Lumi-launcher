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
        // CurseForge writes "minecraftModpack"; a bare "minecraft" is accepted from older tools.
        require(index.str("manifestType") in setOf("minecraftModpack", "minecraft") && index.long("manifestVersion") == 1L) {
            "Неподдерживаемый формат CurseForge"
        }
        val minecraft = index.obj("minecraft") ?: error("В сборке нет версии Minecraft")
        val loaders = minecraft["modLoaders"]?.jsonArray.orEmpty().map { it.jsonObject }
        val loaderId = (loaders.firstOrNull { it.bool("primary") } ?: loaders.firstOrNull())?.str("id")
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
        val refs = index["files"]?.jsonArray.orEmpty().map { it.jsonObject }
        if (refs.isEmpty()) return emptyList()
        require(key.isNotBlank()) { "Для сборки CurseForge нужен ключ API в настройках" }
        val ids = refs.map { it.long("fileID") ?: error("Нет ID файла CurseForge") }
        val curse = CurseForge(key)
        // All files in a few requests rather than one each: a big pack lists hundreds.
        val records = curse.files(ids)
        val lost = ids.toSet() - records.mapNotNull { it.long("id") }.toSet()
        if (lost.isNotEmpty()) error("CurseForge не нашёл файлы сборки: ${lost.joinToString()}")
        // Packs carry resource packs and shaders too; each goes to its own folder, not into mods.
        val classes = curse.mods(records.mapNotNull { it.long("modId") }).associate { it.long("id") to it.long("classId") }
        val sha1 = { record: JsonObject ->
            record["hashes"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value")
        }
        // Some authors allow downloads only on CurseForge's own site. Modrinth often hosts the very
        // same file — same SHA-1 — and lets launchers have it.
        val blocked = records.filter { it.str("downloadUrl") == null }
        val mirrors = Modrinth.urlsByHash(blocked.mapNotNull(sha1))
        val stuck = blocked.filter { record -> sha1(record)?.let(mirrors::get) == null }
        if (stuck.isNotEmpty()) {
            val names = stuck.joinToString { "«${it.str("displayName") ?: it.str("fileName")}»" }
            throw IOException("Сборку не поставить: $names автор разрешил скачивать только на сайте CurseForge, а на Modrinth такого файла нет")
        }
        return records.map { record ->
            val name = safePath(record.str("fileName") ?: error("Файл CurseForge без имени"))
            require('/' !in name) { "Недопустимое имя файла $name" }
            val folder = when (classes[record.long("modId")]) {
                12L -> "resourcepacks"
                6552L -> "shaderpacks"
                else -> "mods"
            }
            val hash = sha1(record)
            // CurseForge's links keep the file name as it is, spaces included.
            val url = record.str("downloadUrl")?.replace(" ", "%20") ?: mirrors.getValue(hash!!)
            Download(url, game.resolve(folder).resolve(name), hash, record.long("fileLength"))
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

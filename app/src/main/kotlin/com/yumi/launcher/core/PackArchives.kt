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

/**
 * A file of a pack left out on import because it could not be had — a resource pack or shader
 * pack whose author keeps it off launchers: [kind] in words, its file [name], the [folder] it goes
 * in under the game folder, and its [page] to fetch it from by hand.
 */
internal data class SkippedFile(val kind: String, val name: String, val folder: String, val page: String)

/** A CurseForge pack's files: what to download, manual downloads to copy in, and what has to be left out. */
internal class CursePlan(val downloads: List<Download>, val copies: List<Pair<File, File>>, val skipped: List<SkippedFile>)

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

    /** Installs [archive] into [packFolder]; returns the optional files that had to be left out. */
    suspend fun install(archive: File, packFolder: File, key: String, onProgress: (Float) -> Unit): List<SkippedFile> = withContext(Dispatchers.IO) {
        ZipFile(archive).use { zip ->
            val game = packFolder.resolve("minecraft")
            val server = packFolder.resolve("server")
            val index = if (zip.getEntry("modrinth.index.json") != null) index(zip, "modrinth.index.json") else index(zip, "manifest.json")
            val plan = if (zip.getEntry("modrinth.index.json") != null) modrinthFiles(index, game, server).let { (d, s) -> CursePlan(d, s, emptyList()) }
                else curseForgeFiles(index, game, archive.absoluteFile.parentFile, key)
            val downloads = plan.downloads
            val shared = plan.copies
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
            plan.skipped
        }
    }

    /** What installing a CurseForge [archive] would do, asked of CurseForge without downloading anything — for `--pack-test --plan`. */
    suspend fun planCurseForge(archive: File, packFolder: File, key: String): CursePlan = withContext(Dispatchers.IO) {
        ZipFile(archive).use { zip -> curseForgeFiles(index(zip, "manifest.json"), packFolder.resolve("minecraft"), archive.absoluteFile.parentFile, key) }
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

    private suspend fun curseForgeFiles(index: JsonObject, game: File, manualFolder: File, key: String): CursePlan {
        curseForgeInfo(index)
        val refs = index["files"]?.jsonArray.orEmpty().map { it.jsonObject }
        if (refs.isEmpty()) return CursePlan(emptyList(), emptyList(), emptyList())
        require(key.isNotBlank()) { "Для сборки CurseForge нужен ключ API в настройках" }
        val ids = refs.map { it.long("fileID") ?: error("Нет ID файла CurseForge") }
        val curse = CurseForge(key)
        // All files in a few requests rather than one each: a big pack lists hundreds.
        val records = curse.files(ids)
        val lost = ids.toSet() - records.mapNotNull { it.long("id") }.toSet()
        if (lost.isNotEmpty()) error("CurseForge не нашёл файлы сборки: ${lost.joinToString()}")
        val projects = curse.mods(records.mapNotNull { it.long("modId") })
        // Some authors allow downloads only on CurseForge's own site. Modrinth often hosts the
        // very same file — same SHA-1 — and lets launchers have it.
        val blocked = records.filter { it.str("downloadUrl").isNullOrBlank() }
        val mirrors = Modrinth.urlsByHash(blocked.mapNotNull(::curseSha1))
        return curseForgePlan(records, projects, game, manualFolder, mirrors)
    }

    /**
     * Kept separate from API calls so routing and manual downloads can be checked offline. A mod
     * that cannot be had stops the import — the pack would not start without it; a resource pack
     * or shader pack is only looks, and the pack is installed without it, as [CursePlan.skipped].
     */
    internal fun curseForgePlan(records: List<JsonObject>, projects: List<JsonObject>, game: File,
        manualFolder: File, mirrors: Map<String, String>): CursePlan {
        val byId = projects.associateBy { it.long("id") }
        val downloads = mutableListOf<Download>()
        val copies = mutableListOf<Pair<File, File>>()
        val missing = mutableListOf<String>()
        val skipped = mutableListOf<SkippedFile>()
        for (record in records) {
            val name = safePath(record.str("fileName") ?: error("Файл CurseForge без имени"))
            require('/' !in name) { "Недопустимое имя файла $name" }
            val project = byId[record.long("modId")]
            // ZIP is not a type: resource packs and shader packs both use it.
            val folder = when (project?.long("classId")) {
                12L -> "resourcepacks"
                6552L -> "shaderpacks"
                6L -> "mods"
                else -> throw IOException("Не удалось определить тип файла «$name» — повторите импорт позже")
            }
            val target = game.resolve(folder).resolve(name)
            val hash = curseSha1(record)
            val size = record.long("fileLength")
            val url = record.str("downloadUrl")?.takeIf { it.isNotBlank() } ?: mirrors[hash]
            if (url != null) {
                // CurseForge's links keep the file name as it is, spaces included.
                downloads += Download(url.replace(" ", "%20"), target, hash, size)
                continue
            }
            // A manual download must be the exact release, not merely a ZIP with the same name.
            val local = listOf(target, manualFolder.resolve(folder).resolve(name), manualFolder.resolve(name))
                .firstOrNull { it.isFile && hash != null && (size == null || it.length() == size) && sha1(it).equals(hash, ignoreCase = true) }
            if (local != null) {
                if (local != target) copies += local to target
                continue
            }
            val kind = when (folder) {
                "resourcepacks" -> "Текстурпак"
                "shaderpacks" -> "Шейдерпак"
                else -> "Мод"
            }
            val page = project.obj("links")?.str("websiteUrl")?.let { "$it/files/${record.long("id")}" }.orEmpty()
            if (folder == "mods") missing += "$kind «$name» → $folder. $page" else skipped += SkippedFile(kind, name, folder, page)
        }
        if (missing.isNotEmpty()) throw IOException("CurseForge не выдал ссылки на моды, а точных копий на Modrinth нет. " +
            "Скачайте их с сайта и положите без распаковки рядом с архивом сборки в «${manualFolder.absolutePath}», затем повторите импорт. " +
            "Уже скачанные файлы должны совпадать по SHA-1.\n${missing.joinToString("\n")}")
        return CursePlan(downloads, copies, skipped)
    }

    private fun curseSha1(record: JsonObject): String? = record["hashes"]?.jsonArray.orEmpty()
        .map { it.jsonObject }.firstOrNull { it.long("algo") == 1L }?.str("value")

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

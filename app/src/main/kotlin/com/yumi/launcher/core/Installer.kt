package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

enum class Stage(val label: String) {
    Loader("Загрузчик модов"),
    Game("Файлы игры"),
    Assets("Ресурсы игры"),
    Java("Java"),
    Account("Вход в аккаунт"),
    Starting("Запуск"),
}

/** Everything [GameLauncher] needs, all of it already on disk. */
internal class Prepared(
    val profile: VersionProfile,
    val classpath: List<File>,
    val natives: File,
    val java: File,
    /** The pre-1.7 "virtual" asset tree when the version wants one, else null. */
    val virtualAssets: File?,
    val loggingArgument: String?,
)

/**
 * Brings one version to the point where it can start: its JSON, client jar and libraries, unpacked
 * natives, the asset objects and — unless the player chose their own — the Java runtime Mojang
 * ships for it. Everything is shared between packs and skipped when already present, so a second
 * launch of the same version costs a directory walk, not a download.
 */
internal class Installer(private val report: (Stage, Float) -> Unit) {

    /**
     * [versionId] may be a loader version; [lookup] finds a vanilla version's entry in Mojang's
     * list, for whichever version is inherited from.
     */
    suspend fun prepare(versionId: String, lookup: (String) -> VersionEntry?, gameDir: File, java: File?): Prepared {
        report(Stage.Game, 0f)
        val profile = VersionProfile(resolveProfile(versionId, lookup))
        val versionDir = LauncherPaths.versions.resolve(profile.id)
        val clientJar = LauncherPaths.versions.resolve(profile.jar).resolve("${profile.jar}.jar")
        val libraries = profile.resolveLibraries()

        val jobs = mutableListOf<Download>()
        profile.client?.let { jobs += Download(it.str("url")!!, clientJar, it.str("sha1"), it.long("size")) }
        libraries.filter { it.url.isNotEmpty() }.forEach { jobs += Download(it.url, LauncherPaths.libraries.resolve(it.path), it.sha1, it.size) }
        val loggingFile = profile.logging?.obj("file")?.let { file ->
            LauncherPaths.assets.resolve("log_configs").resolve(file.str("id")!!).also {
                jobs += Download(file.str("url")!!, it, file.str("sha1"), file.long("size"))
            }
        }
        Http.downloadAll(jobs) { report(Stage.Game, it) }

        val natives = versionDir.resolve("natives")
        withContext(Dispatchers.IO) { unpackNatives(profile, libraries, natives) }

        // A loader version runs on its own copy of the vanilla jar, named after itself, as Mojang's
        // launcher does it. NeoForge relies on that: it recognises the game jar by `${version_name}.jar`
        // and, when the name differs, loads Minecraft a second time from the classpath.
        val gameJar = if (profile.jar == profile.id) clientJar else versionDir.resolve("${profile.id}.jar").also { copy ->
            withContext(Dispatchers.IO) {
                if (!copy.isFile || copy.length() != clientJar.length()) clientJar.copyTo(copy, overwrite = true)
            }
        }

        report(Stage.Assets, 0f)
        val virtualAssets = assets(profile, gameDir)

        val javaExe = java ?: run {
            report(Stage.Java, 0f)
            JavaRuntimes.ensure(profile.javaComponent) { report(Stage.Java, it) }
        }

        val classpath = libraries.filter { it.kind != Library.Kind.LegacyNative }.map { LauncherPaths.libraries.resolve(it.path) } + gameJar
        val logging = if (loggingFile != null) profile.logging?.str("argument")?.replace("\${path}", loggingFile.path) else null
        return Prepared(profile, classpath, natives, javaExe, virtualAssets, logging)
    }

    /**
     * The version's JSON, merged down its `inheritsFrom` chain. Vanilla ones come from Mojang
     * when missing or stale; loader ones were written by [Loaders] and are only ever read.
     */
    suspend fun resolveProfile(versionId: String, lookup: (String) -> VersionEntry?): JsonObject = withContext(Dispatchers.IO) {
        val file = LauncherPaths.versions.resolve(versionId).resolve("$versionId.json")
        val entry = lookup(versionId)
        val fresh = file.isFile && (entry?.sha1 == null || sha1(file) == entry.sha1)
        if (!fresh) {
            entry ?: error("Нет связи с Mojang, а версия $versionId ещё не скачана")
            Http.downloadAll(listOf(Download(entry.url, file, entry.sha1, null))) {}
        }
        val json = LauncherJson.parseToJsonElement(file.readText()).jsonObject
        val parent = json.str("inheritsFrom") ?: return@withContext json
        mergeProfiles(json, resolveProfile(parent, lookup))
    }

    /**
     * Native libraries go where the version's own `-Djava.library.path` points — modern versions
     * ask for a `java` folder inside the natives directory, older ones for the directory itself.
     * Files are flattened out of the jars' folders, which is how `java.library.path` looks for them.
     */
    private fun unpackNatives(profile: VersionProfile, libraries: List<Library>, natives: File) {
        val libraryPath = profile.jvmArguments
            ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?.firstOrNull { it.startsWith("-Djava.library.path=") }
            ?.removePrefix("-Djava.library.path=")
            ?.replace("\${natives_directory}", natives.path)
        val target = File(libraryPath ?: natives.path)
        target.mkdirs()
        for (lib in libraries) {
            if (lib.kind == Library.Kind.Classpath) continue
            val jar = LauncherPaths.libraries.resolve(lib.path)
            if (!jar.isFile) continue
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || entry.name.startsWith("META-INF")) continue
                    if (lib.excludes.any { entry.name.startsWith(it) }) continue
                    val name = entry.name.substringAfterLast('/')
                    if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib") && !name.endsWith(".jnilib")) continue
                    val out = target.resolve(name)
                    if (out.isFile && out.length() == entry.size) continue
                    runCatching { zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } } }
                }
            }
        }
    }

    /**
     * The asset index and its objects. Versions from before 1.7 read assets by name rather than by
     * hash, from a "virtual" tree or from the game folder's `resources`, so those get copies.
     */
    private suspend fun assets(profile: VersionProfile, gameDir: File): File? {
        val ref = profile.assetIndex ?: return null
        val id = ref.str("id")!!
        val indexFile = LauncherPaths.assets.resolve("indexes").resolve("$id.json")
        Http.downloadAll(listOf(Download(ref.str("url")!!, indexFile, ref.str("sha1"), ref.long("size")))) {}
        val index = withContext(Dispatchers.IO) { LauncherJson.parseToJsonElement(indexFile.readText()).jsonObject }
        val objects = index.obj("objects") ?: return null
        val objectsDir = LauncherPaths.assets.resolve("objects")
        val jobs = objects.map { (_, value) ->
            val o = value.jsonObject
            val hash = o.str("hash")!!
            Download(Mojang.RESOURCES_URL + hash.take(2) + "/" + hash, objectsDir.resolve(hash.take(2)).resolve(hash), hash, o.long("size"))
        }
        Http.downloadAll(jobs) { report(Stage.Assets, it) }

        val mapToResources = index.bool("map_to_resources")
        val virtual = index.bool("virtual")
        if (!mapToResources && !virtual) return null
        val virtualDir = LauncherPaths.assets.resolve("virtual").resolve(id)
        withContext(Dispatchers.IO) {
            for ((name, value) in objects) {
                val hash = value.jsonObject.str("hash")!!
                val source = objectsDir.resolve(hash.take(2)).resolve(hash)
                if (virtual) copyIfMissing(source, virtualDir.resolve(name))
                if (mapToResources) copyIfMissing(source, gameDir.resolve("resources").resolve(name))
            }
        }
        return if (virtual) virtualDir else null
    }

    private fun copyIfMissing(source: File, target: File) {
        if (target.isFile && target.length() == source.length()) return
        target.parentFile.mkdirs()
        source.copyTo(target, overwrite = true)
    }
}

/**
 * The Java runtimes Mojang publishes for its own launcher, one per `javaVersion.component`: Java 8
 * for old versions up to Java 25 for the newest. Downloading them means a player never has to know
 * which Java a version wants.
 */
internal object JavaRuntimes {
    suspend fun ensure(component: String, onProgress: (Float) -> Unit): File {
        val dir = LauncherPaths.runtimes.resolve(component)
        val exe = dir.resolve("bin").resolve(Platform.javaExecutable)
        val marker = dir.resolve(".yumi-installed")

        val index = try {
            LauncherJson.parseToJsonElement(Http.text(Mojang.RUNTIMES_URL)).jsonObject
        } catch (e: Exception) {
            // Offline: whatever was installed before is good enough.
            if (exe.isFile) return exe else throw e
        }
        val release = index.obj(Platform.runtimeKey)?.get(component)?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("Mojang не выпускает Java «$component» для этой системы — укажите свою в настройках")
        val manifestRef = release.obj("manifest")!!
        val manifestSha = manifestRef.str("sha1")
        if (exe.isFile && marker.isFile && marker.readText() == manifestSha) return exe

        val files = LauncherJson.parseToJsonElement(Http.text(manifestRef.str("url")!!)).jsonObject.obj("files") ?: JsonObject(emptyMap())
        val jobs = mutableListOf<Download>()
        val executables = mutableListOf<File>()
        for ((path, value) in files) {
            val entry = value.jsonObject
            val target = dir.resolve(path)
            when (entry.str("type")) {
                "directory" -> target.mkdirs()
                "file" -> {
                    val raw = entry.obj("downloads")?.obj("raw") ?: continue
                    jobs += Download(raw.str("url")!!, target, raw.str("sha1"), raw.long("size"))
                    if (entry.bool("executable")) executables += target
                }
            }
        }
        Http.downloadAll(jobs, onProgress = onProgress)
        executables.forEach { it.setExecutable(true) }
        if (!exe.isFile) error("В загруженной Java нет ${exe.name}")
        marker.writeText(manifestSha ?: "")
        return exe
    }
}

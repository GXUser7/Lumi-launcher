package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Dedicated servers. Vanilla is Mojang's `server.jar`; Fabric publishes a ready launcher jar that
 * fetches its libraries on first start; Quilt, Forge and NeoForge run their own installers in
 * server mode, the way their instructions tell a server owner to.
 */
internal object ServerInstaller {
    private const val QUILT_INSTALLER = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer"

    /**
     * The Java the version wants — Mojang's runtime for it, unless the player set their own — and
     * the vanilla version itself, whose JSON says which Java and where the server jar is.
     */
    suspend fun java(minecraft: String, lookup: (String) -> VersionEntry?, custom: File?, onProgress: (Float) -> Unit): File {
        if (custom != null) return custom
        val profile = VersionProfile(Installer { _, _ -> }.resolveProfile(minecraft, lookup))
        return JavaRuntimes.ensure(profile.javaComponent, onProgress)
    }

    /**
     * Puts a [loader] server for [minecraft] into [dir] and returns what starts it — the arguments
     * after `java` and the memory flags: `-jar server.jar`, or Forge's `@libraries/…/win_args.txt`.
     */
    suspend fun install(
        dir: File,
        minecraft: String,
        loader: Loader,
        loaderVersion: String?,
        lookup: (String) -> VersionEntry?,
        java: File,
        log: File,
        onProgress: (Float) -> Unit,
    ): List<String> {
        dir.mkdirs()
        log.parentFile.mkdirs()
        val profile = VersionProfile(Installer { _, _ -> }.resolveProfile(minecraft, lookup))
        val server = profile.server ?: error("Mojang не выпускал сервер для $minecraft")
        // Every loader but Forge and NeoForge wants the vanilla jar beside it as `server.jar`.
        suspend fun vanilla() = Http.downloadAll(
            listOf(Download(server.str("url")!!, dir.resolve("server.jar"), server.str("sha1"), server.long("size"))),
            onProgress = onProgress,
        )
        return when (loader) {
            Loader.Vanilla -> {
                vanilla()
                listOf("-jar", "server.jar")
            }
            Loader.Fabric -> {
                vanilla()
                val installers = LauncherJson.parseToJsonElement(Http.text("${Loaders.FABRIC}/versions/installer")).jsonArray.map { it.jsonObject }
                val installer = (installers.firstOrNull { it.bool("stable") } ?: installers.first()).str("version")!!
                val url = "${Loaders.FABRIC}/versions/loader/${Loaders.encode(minecraft)}/${Loaders.encode(loaderVersion!!)}/${Loaders.encode(installer)}/server/jar"
                // Named once and replaced on reinstall, so an old loader's launcher never lingers beside the new one.
                val jar = dir.resolve("fabric-server-launch.jar")
                jar.delete()
                Http.downloadAll(listOf(Download(url, jar, null, null))) {}
                listOf("-jar", jar.name)
            }
            Loader.Quilt -> {
                val version = Loaders.mavenVersions("$QUILT_INSTALLER/maven-metadata.xml").last()
                val jar = Loaders.cachedInstaller("$QUILT_INSTALLER/$version/quilt-installer-$version.jar")
                run(java, log, listOf("-jar", jar.path, "install", "server", minecraft, loaderVersion!!, "--download-server", "--install-dir=${dir.path}"))
                if (!dir.resolve("quilt-server-launch.jar").isFile) error("Установщик Quilt не создал quilt-server-launch.jar — подробности в ${log.name}")
                listOf("-jar", "quilt-server-launch.jar")
            }
            Loader.Forge, Loader.NeoForge -> {
                val jar = Loaders.cachedInstaller(Loaders.installerUrl(loader, minecraft, loaderVersion!!))
                // The script and the jar from a previous loader version would be mistaken for the new one's.
                dir.resolve("run.bat").delete()
                dir.resolve("run.sh").delete()
                run(java, log, listOf("-jar", jar.path, "--installServer", dir.path))
                forgeLaunch(dir, loader, loaderVersion)
            }
        }
    }

    /** Runs an installer; cancelling the start takes the installer down with it. */
    private suspend fun run(java: File, log: File, args: List<String>) {
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(listOf(java.path) + args)
                .directory(log.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        }
        val code = try {
            runInterruptible(Dispatchers.IO) { process.waitFor() }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        if (code != 0) error("Установщик завершился с кодом $code — подробности в ${log.name}")
    }

    /**
     * What Forge's installer set up. Since 1.17 it writes a `run.bat` whose `java` line is the whole
     * answer — an argument file, or a small launcher jar in newer versions — while older ones leave
     * a single runnable jar in the folder.
     */
    private fun forgeLaunch(dir: File, loader: Loader, version: String): List<String> {
        val script = dir.resolve(if (Platform.os == "windows") "run.bat" else "run.sh")
        val line = if (script.isFile) script.readLines().map { it.trim() }.firstOrNull { it.startsWith("java ") } else null
        if (line != null) {
            return line.removePrefix("java ").split(Regex("\\s+"))
                .map { it.trim('"') }
                // Memory comes from the launcher; the script's own extras are the player's to add.
                .filter { it.isNotEmpty() && it != "%*" && it != "$@" && it != "@user_jvm_args.txt" }
        }
        val name = if (loader == Loader.NeoForge) "neoforge" else "forge"
        val jar = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".jar") && it.name.startsWith("$name-") && "installer" !in it.name && version in it.name }
            .maxByOrNull { it.length() }
            ?: error("Установщик ${loader.label} отработал, но запускать нечего")
        return listOf("-jar", jar.name)
    }
}

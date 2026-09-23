package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URLEncoder

enum class Loader(val label: String) {
    Vanilla("Ванилла"),
    Fabric("Fabric"),
    Quilt("Quilt"),
    Forge("Forge"),
    NeoForge("NeoForge"),
}

/** One loader release for a Minecraft version; [stable] marks what the loader itself recommends. */
data class LoaderVersion(val id: String, val stable: Boolean)

/**
 * Mod loaders. Fabric and Quilt publish ready launcher profiles, so installing one is writing a
 * JSON file. Forge and NeoForge patch the game while installing, so their own installers run —
 * headless, straight into the launcher's folder, which has the layout they expect.
 */
internal object Loaders {
    const val FABRIC = "https://meta.fabricmc.net/v2"
    const val QUILT = "https://meta.quiltmc.org/v3"
    const val FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"
    const val FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"

    /** Newest first. Empty when the loader does not support [minecraft]. */
    suspend fun versions(loader: Loader, minecraft: String): List<LoaderVersion> = when (loader) {
        Loader.Vanilla -> emptyList()
        Loader.Fabric -> meta("$FABRIC/versions/loader/${encode(minecraft)}")
        Loader.Quilt -> meta("$QUILT/versions/loader/${encode(minecraft)}")
        Loader.Forge -> {
            val promos = runCatching { LauncherJson.parseToJsonElement(Http.text(FORGE_PROMOS)).jsonObject.obj("promos") }.getOrNull()
            val recommended = promos?.str("$minecraft-recommended")
            mavenVersions("$FORGE_MAVEN/maven-metadata.xml")
                .filter { it.startsWith("$minecraft-") }
                .map { it.removePrefix("$minecraft-") }
                .reversed()
                .map { LoaderVersion(it, stable = it == recommended) }
        }
        Loader.NeoForge -> {
            // NeoForge numbers itself after the game: 1.21.1 → 21.1.x, 1.21 → 21.0.x, 26.3 → 26.3.0.x.
            val parts = (if (minecraft.startsWith("1.")) minecraft.removePrefix("1.") else minecraft).split('.').toMutableList()
            val prefix = if (minecraft.startsWith("1.")) {
                if (parts.size < 2) parts += "0"
                parts.take(2).joinToString(".") + "."
            } else {
                while (parts.size < 3) parts += "0"
                parts.take(3).joinToString(".") + "."
            }
            mavenVersions("$NEOFORGE_MAVEN/maven-metadata.xml")
                .filter { it.startsWith(prefix) }
                .reversed()
                .map { LoaderVersion(it, stable = !it.contains("beta") && !it.contains("alpha")) }
        }
    }

    /** The version id to launch, installing the loader first if it is not there yet. */
    suspend fun install(loader: Loader, minecraft: String, version: String, java: File, log: File): String = when (loader) {
        Loader.Vanilla -> minecraft
        Loader.Fabric -> profile("$FABRIC/versions/loader/${encode(minecraft)}/${encode(version)}/profile/json")
        Loader.Quilt -> profile("$QUILT/versions/loader/${encode(minecraft)}/${encode(version)}/profile/json")
        Loader.Forge -> installer(expectedId = "$minecraft-forge-$version", url = installerUrl(loader, minecraft, version), minecraft = minecraft, java = java, log = log)
        Loader.NeoForge -> installer(expectedId = "neoforge-$version", url = installerUrl(loader, minecraft, version), minecraft = minecraft, java = java, log = log)
    }

    /** Forge's and NeoForge's installer, which sets up a client or — with `--installServer` — a server. */
    fun installerUrl(loader: Loader, minecraft: String, version: String): String = when (loader) {
        Loader.Forge -> "$FORGE_MAVEN/$minecraft-$version/forge-$minecraft-$version-installer.jar"
        Loader.NeoForge -> "$NEOFORGE_MAVEN/$version/neoforge-$version-installer.jar"
        else -> error("У ${loader.label} нет установщика")
    }

    /** The newest stable release of [loader] for [minecraft], which a pack then keeps. */
    suspend fun newestStable(loader: Loader, minecraft: String): String {
        val available = versions(loader, minecraft)
        return (available.firstOrNull { it.stable } ?: available.firstOrNull())?.id
            ?: error("${loader.label} не поддерживает Minecraft $minecraft")
    }

    private suspend fun meta(url: String): List<LoaderVersion> =
        LauncherJson.parseToJsonElement(Http.text(url)).jsonArray.map { entry ->
            val loader = entry.jsonObject.obj("loader")!!
            LoaderVersion(loader.str("version")!!, stable = loader.bool("stable") || loader.str("version")?.contains("beta") == false)
        }

    suspend fun mavenVersions(url: String): List<String> =
        Regex("<version>([^<]+)</version>").findAll(Http.text(url)).map { it.groupValues[1] }.toList()

    /** Fabric and Quilt: their profile is the whole installation. */
    private suspend fun profile(url: String): String {
        val text = Http.text(url)
        val id = LauncherJson.parseToJsonElement(text).jsonObject.str("id") ?: error("Профиль загрузчика без id")
        withContext(Dispatchers.IO) {
            val file = LauncherPaths.versions.resolve(id).resolve("$id.json")
            file.parentFile.mkdirs()
            file.writeText(text)
        }
        return id
    }

    /**
     * Forge and NeoForge: the official installer, run with `--installClient` against the launcher
     * folder. It wants the vanilla version already in place — the caller has prepared it — and a
     * `launcher_profiles.json`, which it only checks for and appends to.
     */
    private suspend fun installer(expectedId: String, url: String, minecraft: String, java: File, log: File): String = withContext(Dispatchers.IO) {
        val root = LauncherPaths.root
        if (root.resolve("versions/$expectedId/$expectedId.json").isFile) return@withContext expectedId
        val profiles = root.resolve("launcher_profiles.json")
        if (!profiles.isFile) profiles.writeText("""{"profiles":{}}""")

        val jar = cachedInstaller(url)
        val before = root.resolve("versions").list().orEmpty().toSet()
        log.parentFile.mkdirs()
        val process = ProcessBuilder(java.path, "-jar", jar.path, "--installClient", root.path)
            .directory(jar.parentFile)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        val code = process.waitFor()
        if (code != 0) error("Установщик завершился с кодом $code — подробности в ${log.name}")

        if (root.resolve("versions/$expectedId/$expectedId.json").isFile) return@withContext expectedId
        // Naming has shifted between installer generations; take whatever it just created for this game version.
        root.resolve("versions").listFiles().orEmpty()
            .filter { it.name !in before && it.resolve("${it.name}.json").isFile }
            .firstOrNull { runCatching { LauncherJson.parseToJsonElement(it.resolve("${it.name}.json").readText()).jsonObject.str("inheritsFrom") }.getOrNull() == minecraft }
            ?.name ?: error("Установщик отработал, но версия не появилась")
    }

    /** Installers are kept: a second pack or a server on the same loader version needs the same one. */
    suspend fun cachedInstaller(url: String): File {
        val jar = LauncherPaths.cache.resolve("installers").resolve(url.substringAfterLast('/'))
        Http.downloadAll(listOf(Download(url, jar, null, null))) {}
        return jar
    }

    fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}

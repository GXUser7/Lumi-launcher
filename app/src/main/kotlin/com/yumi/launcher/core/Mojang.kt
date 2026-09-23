package com.yumi.launcher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val LauncherJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

internal object Mojang {
    const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    const val RUNTIMES_URL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
    const val RESOURCES_URL = "https://resources.download.minecraft.net/"
    const val LIBRARIES_URL = "https://libraries.minecraft.net/"
}

@Serializable
data class VersionManifest(val latest: Latest, val versions: List<VersionEntry>) {
    @Serializable
    data class Latest(val release: String, val snapshot: String)
}

/** One line of Mojang's version list: `release`, `snapshot`, `old_beta` or `old_alpha`. */
@Serializable
data class VersionEntry(val id: String, val type: String, val url: String, val releaseTime: String, val sha1: String? = null)

/** The version list, fresh when the network allows and the last good copy when it does not. */
internal suspend fun loadManifest(): VersionManifest {
    val cache = LauncherPaths.cache.resolve("version_manifest_v2.json")
    return try {
        val text = Http.text(Mojang.MANIFEST_URL)
        val manifest = LauncherJson.decodeFromString<VersionManifest>(text)
        cache.parentFile.mkdirs()
        cache.writeText(text)
        manifest
    } catch (e: Exception) {
        if (cache.isFile) LauncherJson.decodeFromString(cache.readText()) else throw e
    }
}

/**
 * A version's own JSON: what to download and how to start it. Read as a tree — its shape varies
 * across a decade of releases. Loader versions (Fabric, Forge…) arrive already merged onto the
 * vanilla version they inherit from; [jar] then still names the vanilla client they run on.
 */
internal class VersionProfile(val json: JsonObject) {
    val id: String = json.str("id") ?: error("Версия без id")
    val jar: String = json.str("jar") ?: id
    val type: String = json.str("type") ?: "release"
    val mainClass: String = json.str("mainClass") ?: error("Версия без mainClass")
    val assetIndex: JsonObject? = json.obj("assetIndex")
    val client: JsonObject? = json.obj("downloads")?.obj("client")
    val server: JsonObject? = json.obj("downloads")?.obj("server")
    val javaComponent: String = json.obj("javaVersion")?.str("component") ?: "jre-legacy"
    val javaMajor: Int = json.obj("javaVersion")?.get("majorVersion")?.jsonPrimitive?.intOrNull ?: 8
    val logging: JsonObject? = json.obj("logging")?.obj("client")
    val gameArguments: JsonArray? = json.obj("arguments")?.get("game")?.jsonArray
    val jvmArguments: JsonArray? = json.obj("arguments")?.get("jvm")?.jsonArray
    val legacyArguments: String? = json.str("minecraftArguments")
    val libraries: JsonArray = json["libraries"]?.jsonArray ?: JsonArray(emptyList())
}

/**
 * Mojang's rule list: each rule that applies overrides the ones before it, and nothing is
 * allowed until one says so. [features] are the switches a launcher opts into — custom
 * resolution, demo mode, quick play.
 */
internal fun allowed(rules: JsonArray?, features: Set<String> = emptySet()): Boolean {
    if (rules == null) return true
    var result = false
    for (element in rules) {
        val rule = element.jsonObject
        var applies = true
        rule.obj("os")?.let { os ->
            os.str("name")?.let { if (it != Platform.os) applies = false }
            os.str("arch")?.let { if (it != Platform.ruleArch) applies = false }
            os.str("version")?.let { if (!Regex(it).containsMatchIn(System.getProperty("os.version"))) applies = false }
        }
        rule.obj("features")?.forEach { (name, value) -> if ((name in features) != value.jsonPrimitive.boolean) applies = false }
        if (applies) result = rule.str("action") == "allow"
    }
    return result
}

internal class Library(val path: String, val url: String, val sha1: String?, val size: Long?, val kind: Kind, val excludes: List<String> = emptyList()) {
    enum class Kind {
        /** On the classpath. */
        Classpath,

        /** A modern natives jar: on the classpath and unpacked. */
        Native,

        /** A pre-1.19 natives classifier: unpacked only. */
        LegacyNative,
    }
}

/**
 * Everything this machine needs from the profile's library list. One copy per artifact: a loader
 * profile lists its libraries before the vanilla ones it inherits, so where both carry, say, ASM,
 * the loader's version is the one kept.
 */
internal fun VersionProfile.resolveLibraries(): List<Library> {
    val out = mutableListOf<Library>()
    val seen = HashSet<String>()
    for (element in libraries) {
        val lib = element.jsonObject
        if (!allowed(lib["rules"]?.jsonArray)) continue
        // Pre-1.13 Forge marks server-only libraries this way.
        if (lib.str("clientreq") == "false") continue
        val name = lib.str("name") ?: continue
        if (!seen.add(artifactKey(name))) continue
        val classifier = name.split(':').getOrNull(3)
        val downloads = lib.obj("downloads")
        val artifact = downloads?.obj("artifact")
        if (artifact != null) {
            val native = classifier?.startsWith("natives-") == true
            // An empty url is a file a Forge installer generated locally: nothing to fetch, but it belongs on the classpath.
            if (!(native && Platform.foreignNative(classifier!!))) {
                out += Library(
                    path = artifact.str("path") ?: mavenPath(name),
                    url = artifact.str("url").orEmpty(),
                    sha1 = artifact.str("sha1"),
                    size = artifact.long("size"),
                    kind = if (native) Library.Kind.Native else Library.Kind.Classpath,
                )
            }
        } else if (downloads == null && lib.obj("natives") == null) {
            // Old-style entry: a repository base instead of a download block.
            val path = mavenPath(name)
            out += Library(path, (lib.str("url") ?: Mojang.LIBRARIES_URL).trimEnd('/') + "/" + path, null, null, Library.Kind.Classpath)
        }
        val nativeKey = lib.obj("natives")?.str(Platform.os) ?: continue
        val classifierKey = nativeKey.replace("\${arch}", if (Platform.is32) "32" else "64")
        val file = downloads?.obj("classifiers")?.obj(classifierKey) ?: continue
        out += Library(
            path = file.str("path") ?: continue,
            url = file.str("url") ?: continue,
            sha1 = file.str("sha1"),
            size = file.long("size"),
            kind = Library.Kind.LegacyNative,
            excludes = lib.obj("extract")?.get("exclude")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
    }
    return out.distinctBy { it.path }
}

/** `group:artifact[:classifier]` — a library's identity without its version. */
private fun artifactKey(name: String): String {
    val parts = name.substringBefore('@').split(':')
    return listOfNotNull(parts.getOrNull(0), parts.getOrNull(1), parts.getOrNull(3)).joinToString(":")
}

/**
 * A loader profile laid over the version it `inheritsFrom`, the way Mojang's launcher does it:
 * the child's fields win, its libraries come first, and its arguments are appended to the parent's.
 */
internal fun mergeProfiles(child: JsonObject, parent: JsonObject): JsonObject {
    val merged = parent.toMutableMap()
    for ((key, value) in child) {
        when (key) {
            "inheritsFrom" -> Unit
            "libraries" -> merged[key] = JsonArray(value.jsonArray + (parent["libraries"]?.jsonArray ?: emptyList()))
            "arguments" -> {
                val mine = value.jsonObject
                val theirs = parent.obj("arguments")
                merged[key] = JsonObject(
                    listOf("game", "jvm").associateWith { part ->
                        JsonArray((theirs?.get(part)?.jsonArray ?: emptyList()) + (mine[part]?.jsonArray ?: emptyList()))
                    },
                )
            }
            else -> merged[key] = value
        }
    }
    if (child["jar"] == null) merged["jar"] = parent["jar"] ?: parent["id"]!!
    return JsonObject(merged)
}

/** `group:artifact:version[:classifier][@ext]` → `group/path/artifact/version/artifact-version[-classifier].ext`. */
internal fun mavenPath(name: String): String {
    val (coordinates, extension) = name.split('@').let { it[0] to (it.getOrNull(1) ?: "jar") }
    val parts = coordinates.split(':')
    val (group, artifact, version) = Triple(parts[0], parts[1], parts[2])
    val classifier = parts.getOrNull(3)?.let { "-$it" } ?: ""
    return "${group.replace('.', '/')}/$artifact/$version/$artifact-$version$classifier.$extension"
}

internal fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.long(key: String): Long? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
internal fun JsonObject.bool(key: String): Boolean = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull == "true"

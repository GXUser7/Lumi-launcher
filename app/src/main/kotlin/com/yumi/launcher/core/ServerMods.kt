package com.yumi.launcher.core

import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipFile

/**
 * A pack's mods carried over to its server. Client-only mods — shaders, minimaps, Sodium — stop a
 * server from starting at all, so they stay behind: Modrinth knows each file's side by its hash,
 * and for the rest a Fabric or Quilt mod declares it in its own metadata.
 *
 * The server part a modpack brought is checked the same way, not taken on trust: some client packs
 * mark every mod as wanted on the server — Fabulously Optimized does — and would fill it with Iris
 * and Sodium.
 */
internal object ServerMods {
    class Result(val copied: List<String>, val clientOnly: List<File>)

    /**
     * Copies [from]'s enabled mods into [to], except client-only ones. [previously] are the files an
     * earlier sync put there: those the pack no longer has are taken out again, while mods added to
     * the server by hand, and ones switched off there, are left alone.
     */
    suspend fun sync(from: File, to: File, previously: Set<String>, staged: File? = null): Result {
        val jars = from.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".jar") }
        val stagedJars = staged?.resolve("mods")?.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".jar") }
        val clientOnly = clientOnly(jars + stagedJars)
        to.mkdirs()
        val copied = mutableListOf<String>()
        for (jar in jars) {
            if (jar in clientOnly) continue
            copied += jar.name
            val target = to.resolve(jar.name)
            if (to.resolve(jar.name + ".disabled").exists()) continue
            if (!target.isFile || target.length() != jar.length()) jar.copyTo(target, overwrite = true)
        }
        staged?.takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.forEach { file ->
            val path = file.relativeTo(staged).invariantSeparatorsPath
            if (path in setOf("eula.txt", "server.properties", "yumi-server.json")) return@forEach
            if (file in clientOnly) return@forEach
            val target = to.parentFile.resolve(path)
            target.parentFile.mkdirs()
            if (!target.isFile || (path.startsWith("mods/") && target.length() != file.length())) {
                file.copyTo(target, overwrite = true)
            }
            if (path.startsWith("mods/") && file.name.endsWith(".jar")) copied += file.name
        }
        for (name in previously - copied.toSet()) {
            to.resolve(name).delete()
            to.resolve("$name.disabled").delete()
        }
        return Result(copied.distinct(), clientOnly.distinctBy { it.name }.sortedBy { it.name.lowercase() })
    }

    private suspend fun clientOnly(jars: List<File>): Set<File> {
        val hashes = jars.associateWith { sha1(it) }
        val sides = runCatching { Modrinth.serverSides(hashes.values) }.getOrDefault(emptyMap())
        return jars.filter { jar ->
            modId(jar) in FOR_OPEN_TO_LAN || when (sides[hashes[jar]]) {
                "unsupported" -> true
                null, "unknown" -> declaresClientOnly(jar)
                else -> false
            }
        }.toSet()
    }

    /**
     * Mods that share a singleplayer world with friends by tunnelling the game's own built-in
     * server. They claim to work on a server, and do nothing on a dedicated one but get in the way —
     * e4mc 6.2 crashes a 26.2 server the moment a player joins.
     */
    private val FOR_OPEN_TO_LAN = setOf("e4mc", "world-host")

    /** The mod's id from fabric.mod.json or quilt.mod.json. */
    private fun modId(jar: File): String? = runCatching {
        ZipFile(jar).use { zip ->
            zip.getEntry("fabric.mod.json")?.let { entry ->
                return@use LauncherJson.parseToJsonElement(zip.getInputStream(entry).reader().readText()).jsonObject.str("id")
            }
            zip.getEntry("quilt.mod.json")?.let { entry ->
                return@use LauncherJson.parseToJsonElement(zip.getInputStream(entry).reader().readText()).jsonObject.obj("quilt_loader")?.str("id")
            }
            null
        }
    }.getOrNull()

    /** `"environment": "client"` in fabric.mod.json, or the same under `minecraft` in quilt.mod.json. */
    private fun declaresClientOnly(jar: File): Boolean = runCatching {
        ZipFile(jar).use { zip ->
            zip.getEntry("fabric.mod.json")?.let { entry ->
                val json = LauncherJson.parseToJsonElement(zip.getInputStream(entry).reader().readText()).jsonObject
                return@use json.str("environment") == "client"
            }
            zip.getEntry("quilt.mod.json")?.let { entry ->
                val json = LauncherJson.parseToJsonElement(zip.getInputStream(entry).reader().readText()).jsonObject
                return@use json.obj("minecraft")?.str("environment") == "client"
            }
            false
        }
    }.getOrDefault(false)
}

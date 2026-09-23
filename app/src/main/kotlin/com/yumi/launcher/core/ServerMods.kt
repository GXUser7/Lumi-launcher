package com.yumi.launcher.core

import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipFile

/**
 * A pack's mods carried over to its server. Client-only mods — shaders, minimaps, Sodium — stop a
 * server from starting at all, so they stay behind: Modrinth knows each file's side by its hash,
 * and for the rest a Fabric or Quilt mod declares it in its own metadata.
 */
internal object ServerMods {
    class Result(val copied: List<String>, val clientOnly: List<File>)

    /**
     * Copies [from]'s enabled mods into [to], except client-only ones. [previously] are the files an
     * earlier sync put there: those the pack no longer has are taken out again, while mods added to
     * the server by hand, and ones switched off there, are left alone.
     */
    suspend fun sync(from: File, to: File, previously: Set<String>): Result {
        val jars = from.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".jar") }
        val clientOnly = clientOnly(jars)
        to.mkdirs()
        val copied = mutableListOf<String>()
        for (jar in jars) {
            if (jar in clientOnly) continue
            copied += jar.name
            val target = to.resolve(jar.name)
            if (to.resolve(jar.name + ".disabled").exists()) continue
            if (!target.isFile || target.length() != jar.length()) jar.copyTo(target, overwrite = true)
        }
        for (name in previously - copied.toSet()) {
            to.resolve(name).delete()
            to.resolve("$name.disabled").delete()
        }
        return Result(copied, clientOnly.sortedBy { it.name.lowercase() })
    }

    private suspend fun clientOnly(jars: List<File>): Set<File> {
        val hashes = jars.associateWith { sha1(it) }
        val sides = runCatching { Modrinth.serverSides(hashes.values) }.getOrDefault(emptyMap())
        return jars.filter { jar ->
            when (sides[hashes[jar]]) {
                "unsupported" -> true
                null, "unknown" -> declaresClientOnly(jar)
                else -> false
            }
        }.toSet()
    }

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

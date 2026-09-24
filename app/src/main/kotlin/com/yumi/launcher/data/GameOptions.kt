package com.yumi.launcher.data

import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.VersionEntry
import java.io.File

/**
 * Minecraft's own settings — keys, mouse, sound, video, language, chat — shared between packs.
 *
 * Every pack has its own game folder and so its own `options.txt`. Before a start the shared
 * values are written into it; after the game, what the player changed there goes back into the
 * shared copy for the other packs. Only the changes go back, not the whole file: a pack on another
 * version drops the settings it does not know and falls back to defaults for values it cannot
 * read, and neither should overwrite what the other packs use.
 *
 * The changes are found against a copy of the file as it was at the start. That copy waits on disk
 * until the next start of any pack, not only until the game exits: the launcher may have closed
 * itself while the game ran, and the player's changes must still reach the other packs before
 * this pack's file is overwritten again.
 *
 * Before 1.13 the game names keys by LWJGL 2 codes rather than by name, so those versions share
 * among themselves ([Era.Legacy]). A pack's resource packs, window size and full screen are never
 * shared: the first are its content, the others its own launch settings.
 */
internal object GameOptions {
    enum class Era { Legacy, Modern }

    /** Kept per pack: see the class comment. `version` is the data version the file was written in. */
    private val OWN = setOf(
        "version", "resourcePacks", "incompatibleResourcePacks",
        "fullscreen", "fullscreenResolution", "overrideWidth", "overrideHeight",
    )

    /** 18w22a, the first snapshot that names keys, with LWJGL 3. */
    private const val NAMED_KEYS = "2018-05-30"

    private val folder: File get() = LauncherPaths.config.resolve("game-options")
    private fun sharedFile(era: Era) = folder.resolve("${era.name.lowercase()}.txt")
    private fun pendingFile(instance: Instance) = folder.resolve("pending").resolve("${instance.id}.txt")

    fun era(entry: VersionEntry?): Era = if (entry != null && entry.releaseTime < NAMED_KEYS) Era.Legacy else Era.Modern

    /**
     * Before [instance] starts: first takes in whatever earlier games changed, then — when the pack
     * shares — writes the shared settings into its `options.txt` and remembers the file as started.
     */
    fun beforeStart(store: InstanceStore, instance: Instance, share: Boolean, eraOf: (Instance) -> Era) {
        settle(store)
        if (!share) return
        val era = eraOf(instance)
        if (!sharedFile(era).isFile) seed(store, era, eraOf)
        val shared = read(sharedFile(era))
        val file = store.gameDir(instance).resolve("options.txt")
        val lines = if (file.isFile) file.readLines() else emptyList()
        val written = merge(lines, shared)
        if (written != lines) {
            file.parentFile.mkdirs()
            file.writeText(written.joinToString("\n", postfix = "\n"))
        }
        pendingFile(instance).apply { parentFile.mkdirs() }.writeText((listOf("#era:${era.name}") + written).joinToString("\n", postfix = "\n"))
    }

    /** Sends every started pack's changes into the shared settings of its era. */
    fun settle(store: InstanceStore) {
        val pending = folder.resolve("pending").listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
        for (snapshot in pending) {
            val instance = store[snapshot.nameWithoutExtension]
            val lines = snapshot.readLines()
            val era = lines.firstOrNull()?.removePrefix("#era:")?.let { runCatching { Era.valueOf(it) }.getOrNull() }
            val now = instance?.let { store.gameDir(it).resolve("options.txt") }
            if (era != null && now != null && now.isFile) {
                val before = parse(lines)
                val after = parse(now.readLines())
                val changed = after.filter { (key, value) -> key !in OWN && before[key] != value }
                // With nothing shared yet — the very first game, in a folder with no settings to
                // start from — the whole file is the start.
                val first = !sharedFile(era).isFile
                if (changed.isNotEmpty() || first) {
                    val shared = read(sharedFile(era)).toMutableMap()
                    shared += if (first) after.filterKeys { it !in OWN } else changed
                    newer(after["version"], shared["version"])?.let { shared["version"] = it }
                    write(sharedFile(era), shared)
                }
            }
            snapshot.delete()
        }
    }

    /**
     * The first shared settings of an era: those of the pack played last, so switching this on
     * starts from the settings the player is used to rather than from the game's defaults.
     */
    private fun seed(store: InstanceStore, era: Era, eraOf: (Instance) -> Era) {
        val source = store.all
            .filter { it.syncOptions && eraOf(it) == era && store.gameDir(it).resolve("options.txt").isFile }
            .maxByOrNull { it.lastPlayed ?: it.created } ?: return
        val options = parse(store.gameDir(source).resolve("options.txt").readLines())
        write(sharedFile(era), options.filterKeys { it !in OWN || it == "version" })
    }

    /**
     * The pack's file with the shared values in: its own lines keep their place, shared keys it
     * lacks go at the end. A file without a data version — none yet, or one the game has only
     * read — gets the shared one: without it the game takes the values for ones from before 1.13
     * and "upgrades" the key names into nonsense.
     */
    private fun merge(lines: List<String>, shared: Map<String, String>): List<String> {
        val seen = HashSet<String>()
        val out = lines.map { line ->
            val key = keyOf(line) ?: return@map line
            seen += key
            val value = shared[key]
            if (value == null || key in OWN) line else "$key:$value"
        }.toMutableList()
        if ("version" !in seen) shared["version"]?.let { out.add(0, "version:$it") }
        for ((key, value) in shared) if (key !in OWN && key !in seen) out += "$key:$value"
        return out
    }

    private fun keyOf(line: String): String? = line.indexOf(':').takeIf { it > 0 && !line.startsWith("#") }?.let { line.substring(0, it) }

    private fun parse(lines: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in lines) {
            val key = keyOf(line) ?: continue
            out[key] = line.substring(key.length + 1)
        }
        return out
    }

    private fun read(file: File): Map<String, String> = if (file.isFile) parse(file.readLines()) else emptyMap()

    private fun write(file: File, options: Map<String, String>) {
        file.parentFile.mkdirs()
        val part = File(file.path + ".part")
        part.writeText(options.entries.joinToString("\n", postfix = "\n") { "${it.key}:${it.value}" })
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    /** The newer of two data versions, when [candidate] is it. */
    private fun newer(candidate: String?, current: String?): String? {
        val c = candidate?.toIntOrNull() ?: return null
        val now = current?.toIntOrNull() ?: return candidate
        return if (c > now) candidate else null
    }
}

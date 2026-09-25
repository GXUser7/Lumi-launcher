package com.yumi.launcher.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.READ

/**
 * A world in a pack's `saves`, as the game's own world list shows it: its [name] from `level.dat` —
 * which two worlds may share — and the [folder] that tells them apart, when it was last played,
 * the game mode (0 survival, 1 creative, 2 adventure, 3 spectator), and the version it was saved in.
 */
internal data class SavedWorld(
    val folder: String,
    val name: String,
    val lastPlayed: Long,
    val mode: Int?,
    val hardcore: Boolean,
    val cheats: Boolean,
    val version: String?,
    val icon: File?,
)

/** Singleplayer saves are copied whole: dimensions, datapacks and mod data belong to the world. */
internal object SavedWorlds {
    /**
     * The worlds in [gameDir], described from their `level.dat`, the last played first — as the
     * game lists them. A `level.dat` that cannot be read still lists its world, by its folder.
     */
    fun describe(gameDir: File): List<SavedWorld> = list(gameDir).map { folder ->
        val dir = gameDir.resolve("saves").resolve(folder)
        val level = dir.resolve("level.dat")
        val data = runCatching { Nbt.read(level)["Data"] as? Map<*, *> }.getOrNull()
        SavedWorld(
            folder = folder,
            name = (data?.get("LevelName") as? String)?.takeIf { it.isNotBlank() } ?: folder,
            lastPlayed = (data?.get("LastPlayed") as? Long)?.takeIf { it > 0 } ?: level.lastModified(),
            mode = data?.get("GameType") as? Int,
            hardcore = (data?.get("hardcore") as? Byte)?.toInt() == 1,
            cheats = (data?.get("allowCommands") as? Byte)?.toInt() == 1,
            version = ((data?.get("Version") as? Map<*, *>)?.get("Name") as? String)?.takeIf { it.isNotBlank() },
            icon = dir.resolve("icon.png").takeIf { it.isFile },
        )
    }.sortedByDescending { it.lastPlayed }

    fun list(gameDir: File): List<String> {
        val saves = gameDir.resolve("saves")
        if (!saves.exists()) return emptyList()
        val folders = saves.listFiles() ?: error("Не удалось прочитать миры сборки — проверьте доступ к папке saves")
        return folders.filter { Files.isDirectory(it.toPath(), NOFOLLOW_LINKS) && it.resolve("level.dat").isFile }
            .map { it.name }.sortedBy { it.lowercase() }
    }

    suspend fun copy(gameDir: File, name: String, target: File) {
        require(name in list(gameDir)) { "Мир больше не найден в сборке — выберите его заново" }
        val source = gameDir.resolve("saves").resolve(name).toPath()
        val context = currentCoroutineContext()
        suspend fun copyFiles() {
            Files.walk(source).use { paths ->
                for (path in paths) {
                    context.ensureActive()
                    val relative = source.relativize(path)
                    // The new server owns its lock; the original must never be copied or removed.
                    if (relative.toString() == "session.lock") continue
                    require(!Files.isSymbolicLink(path) && path.toRealPath().startsWith(source.toRealPath())) {
                        "В мире есть ссылка на другую папку — перенесите её содержимое в мир"
                    }
                    val destination = target.toPath().resolve(relative)
                    if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.createDirectories(destination)
                    else Files.copy(path, destination)
                }
            }
        }
        val lock = source.resolve("session.lock")
        if (Files.exists(lock)) {
            FileChannel.open(lock, READ).use { channel ->
                val held = runCatching { channel.tryLock(0, Long.MAX_VALUE, true) }.getOrNull()
                    ?: error("Мир открыт в игре — закройте его и повторите создание сервера")
                held.use { copyFiles() }
            }
        } else copyFiles()
    }
}

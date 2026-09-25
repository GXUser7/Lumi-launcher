package com.yumi.launcher

import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.SavedWorlds
import com.yumi.launcher.core.ServerMods
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ServerAuth
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.WRITE
import java.util.prefs.Preferences

/** `--world-test <folder>`: server creation on isolated saves, without downloading or starting Minecraft. */
internal fun worldTest(args: List<String>): Int = runBlocking {
    val parent = File(args.firstOrNull() ?: return@runBlocking 2.also { println("Использование: --world-test <тестовая папка>") })
        .absoluteFile.apply { mkdirs() }
    val root = Files.createTempDirectory(parent.toPath(), "worlds-").toFile()
    LauncherPaths.root = root
    LauncherPaths.config = root.resolve("config")
    val prefs = Preferences.userRoot().node("com/yumi/launcher-world-test-${System.nanoTime()}")
    try {
        withContext(Dispatchers.Main) {
            val settings = LauncherSettings(prefs).also { it.gameDir = root.path }
            // No background downloads, account refreshes or friends requests in a filesystem check.
            val model = LauncherModel(settings, CoroutineScope(Job().also { it.cancel() } + Dispatchers.Main))
            val first = model.instances.create("Первая сборка", "1.21.1", 0)
            val second = model.instances.create("Вторая сборка", "1.21.1", 0)
            val gameDir = model.instances.gameDir(first)
            val source = gameDir.resolve("saves/Выживание с друзьями")
            val contents = listOf("level.dat", "level.dat_old", "region/r.0.0.mca", "DIM-1/region/r.0.0.mca",
                "DIM1/region/r.0.0.mca", "dimensions/example/moon/data/map.dat", "datapacks/example/pack.mcmeta", "playerdata/player.dat")
            withContext(Dispatchers.IO) {
                contents.forEachIndexed { index, name ->
                    source.resolve(name).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(index.toByte(), 0, -1, 42)) }
                }
                source.resolve("session.lock").writeText("original lock")
                gameDir.resolve("saves/Not a world").mkdirs()
                model.instances.gameDir(second).resolve("saves/Другой мир/level.dat").apply { parentFile.mkdirs(); writeText("other") }
                check(SavedWorlds.list(gameDir) == listOf(source.name))
                check(SavedWorlds.list(model.instances.gameDir(second)) == listOf("Другой мир"))
            }
            println("OK    Миры ограничены выбранной сборкой, посторонние папки пропущены")

            val server = model.servers.create("Копия мира", first, 25565, ServerAuth.Offline, 2048, source.name)
            val copy = model.servers.folder(server).resolve("world")
            withContext(Dispatchers.IO) {
                contents.forEach { name -> check(copy.resolve(name).readBytes().contentEquals(source.resolve(name).readBytes())) }
                check(!copy.resolve("session.lock").exists())
                check(source.resolve("session.lock").readText() == "original lock")
                copy.resolve("level.dat").writeText("server changed its copy")
                check(source.resolve("level.dat").readBytes().contentEquals(byteArrayOf(0, 0, -1, 42)))
                check(model.servers.properties(server)["level-name"] == "world")
                check(model.servers.folder(server).resolve("yumi-server.json").isFile)
            }
            println("OK    Измерения и данные скопированы, исходный мир не изменён, session.lock не перенесён")

            withContext(Dispatchers.IO) {
                val staged = root.resolve("staged")
                staged.resolve("world/region/r.99.99.mca").apply { parentFile.mkdirs(); writeText("another world") }
                staged.resolve("config/example.cfg").apply { parentFile.mkdirs(); writeText("pack config") }
                ServerMods.sync(gameDir.resolve("mods"), model.servers.modsDir(server), emptySet(), staged)
                check(!copy.resolve("region/r.99.99.mca").exists())
                check(model.servers.folder(server).resolve("config/example.cfg").isFile)
            }
            println("OK    Синхронизация сборки не смешивает выбранный мир с шаблоном сервера")

            val fresh = model.servers.create("Новый мир", first, 25566, ServerAuth.Offline, 2048)
            check(!model.servers.folder(fresh).resolve("world").exists())
            println("OK    Новый мир оставлен для генерации сервером")

            val count = model.servers.all.size
            for (name in listOf("Удалённый мир", "Другой мир", "../Выживание с друзьями")) {
                check(runCatching { model.servers.create("Ошибка", first, 25567, ServerAuth.Offline, 2048, name) }.isFailure)
                check(model.servers.all.size == count)
                check(model.servers.root.listFiles().orEmpty().size == count)
            }
            println("OK    Недоступные миры отклонены, незавершённые серверы удалены")

            FileChannel.open(source.resolve("session.lock").toPath(), WRITE).use { channel ->
                channel.lock().use {
                    val failure = runCatching { model.servers.create("Занятый мир", first, 25567, ServerAuth.Offline, 2048, source.name) }.exceptionOrNull()
                    check(failure?.message?.contains("Мир открыт") == true)
                    check(model.servers.all.size == count)
                    check(model.servers.root.listFiles().orEmpty().size == count)
                }
            }
            println("OK    Открытый в игре мир отклонён без частичной копии")
            model.servers.reload()
            check(model.servers.all.size == count)
            check(model.servers[server.id] != null)
            println("OK    Созданные серверы читаются после перезагрузки")
        }
        println("Проверки пройдены: $root")
        0
    } catch (e: Exception) {
        e.printStackTrace()
        1
    } finally {
        prefs.removeNode()
    }
}

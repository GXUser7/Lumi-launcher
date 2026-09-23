package com.yumi.launcher

import com.yumi.launcher.core.AuthlibInjector
import com.yumi.launcher.core.Download
import com.yumi.launcher.core.GameAccount
import com.yumi.launcher.core.GameLauncher
import com.yumi.launcher.core.Http
import com.yumi.launcher.core.Installer
import com.yumi.launcher.core.LaunchOptions
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.Loaders
import com.yumi.launcher.core.Modrinth
import com.yumi.launcher.core.Stage
import com.yumi.launcher.core.loadManifest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * `--launch-test <version> <folder> [loader] [modrinth-project…] [--join=host:port] [--injector]`:
 * the whole download-and-start path without the window, into a throwaway folder. With a loader it
 * installs that too, and any Modrinth projects named after it. Waits for the game to build its
 * texture atlas — proof it got as far as rendering with its mods loaded — then closes it; with
 * `--join`, waits for it to connect to that server instead. `--injector` starts it under
 * authlib-injector pointed at Ely.by, as an Ely.by account would. Exits 0 on success.
 */
internal fun launchTest(all: List<String>): Int = runBlocking {
    val join = all.firstOrNull { it.startsWith("--join=") }?.removePrefix("--join=")
    val withInjector = "--injector" in all
    val args = all.filterNot { it.startsWith("--") }
    val version = args[0]
    val root = File(args[1]).absoluteFile
    val loader = args.getOrNull(2)?.let { name -> Loader.entries.first { it.name.equals(name, true) } } ?: Loader.Vanilla
    val projects = args.drop(3)
    LauncherPaths.root = root
    val versions = loadManifest().versions
    val lookup = { id: String -> versions.firstOrNull { it.id == id } }
    val gameDir = root.resolve("instances").resolve("test-$version-${loader.name.lowercase()}").resolve("minecraft")
    var shown = ""
    val started = System.currentTimeMillis()
    val installer = Installer { stage, fraction ->
        val line = "${stage.label} ${(fraction * 10).toInt() * 10}%"
        if (line != shown) { shown = line; println(line) }
    }
    val launchId = if (loader == Loader.Vanilla) version else {
        val available = Loaders.versions(loader, version)
        val pick = (available.firstOrNull { it.stable } ?: available.first()).id
        println("${loader.label} $pick (из ${available.size})")
        val java = installer.prepare(version, lookup, gameDir, null).java
        Loaders.install(loader, version, pick, java, root.resolve("loader-install.log")).also { println("Профиль: $it") }
    }
    for (project in projects) {
        val file = Modrinth.file(project, version, loader) ?: error("$project: нет файла для $version")
        Http.downloadAll(listOf(Download(file.url!!, gameDir.resolve("mods").resolve(file.fileName), file.sha1, file.size))) {}
        println("Мод: ${file.fileName}; зависимости: ${file.requires}")
    }
    val prepared = installer.prepare(launchId, lookup, gameDir, null)
    println("Готово за ${(System.currentTimeMillis() - started) / 1000} с; Java: ${prepared.java}")

    // A fresh game folder opens on the accessibility screen, and Quick Play waits behind it for a click.
    gameDir.resolve("options.txt").takeUnless { it.isFile }?.apply { parentFile.mkdirs(); writeText("onboardAccessibility:false\n") }
    val log = root.resolve("launch-$version-${loader.name.lowercase()}.log")
    val account = if (withInjector) {
        GameAccount("YumiTester", GameLauncher.offlineUuid("YumiTester"), "0", "mojang", AuthlibInjector.ensure())
    } else {
        GameAccount.offline("YumiTester")
    }
    val command = GameLauncher.command(prepared, LaunchOptions(account, gameDir, 3072, emptyList(), null, null, false, join))
    if (join != null) println("Аргументы входа: " + command.dropWhile { it != "--quickPlayMultiplayer" && it != "--server" }.take(4))
    val game = GameLauncher.start(command, gameDir, log)
    println(Stage.Starting.label + "…")
    var rendered = false
    val deadline = System.currentTimeMillis() + 240_000
    while (System.currentTimeMillis() < deadline && game.isAlive) {
        delay(1000)
        val text = if (log.isFile) log.readText() else ""
        val reached = if (join != null) "Connecting to" in text else "atlas" in text || "Sound engine started" in text
        if (reached) { rendered = true; break }
    }
    // The join itself is confirmed on the server's side; give the handshake time to finish.
    if (rendered && join != null) delay(25_000)
    val alive = game.isAlive
    game.destroy()
    game.waitFor()
    val goal = if (join != null) "начала подключение к $join" else "дошла до отрисовки"
    println(if (rendered) "OK: игра $goal" else "FAIL: alive=$alive exit=${game.exitValue()}")
    if (log.isFile) {
        val lines = log.readLines()
        println("--- про моды ---")
        lines.filter { "mods" in it.lowercase() && ("Loading" in it || "Found" in it) }.take(5).forEach(::println)
        lines.filter { "authlib-injector" in it || "Connecting to" in it }.take(5).forEach(::println)
        println("--- лог (хвост) ---")
        lines.takeLast(12).forEach(::println)
    }
    if (rendered) 0 else 1
}

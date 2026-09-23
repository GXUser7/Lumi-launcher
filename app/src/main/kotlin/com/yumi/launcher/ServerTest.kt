package com.yumi.launcher

import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.Loaders
import com.yumi.launcher.core.ServerInstaller
import com.yumi.launcher.core.ServerPing
import com.yumi.launcher.core.loadManifest
import com.yumi.launcher.data.ServerProperties
import com.yumi.launcher.data.numericProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `--server-test <version> <folder> [loader] [port]`: installs a dedicated server the way the
 * servers screen does, starts it, waits for "Done", pings it, runs `list` through its console and
 * stops it with `stop`. Exits 0 when all of that worked.
 */
internal fun serverTest(args: List<String>): Int = runBlocking {
    val version = args[0]
    val root = File(args[1]).absoluteFile
    val loader = args.getOrNull(2)?.let { name -> Loader.entries.first { it.name.equals(name, true) } } ?: Loader.Vanilla
    val port = args.getOrNull(3)?.toInt() ?: 25565
    LauncherPaths.root = root
    val versions = loadManifest().versions
    val lookup = { id: String -> versions.firstOrNull { it.id == id } }
    val dir = root.resolve("servers").resolve("test-$version-${loader.name.lowercase()}")
    val started = System.currentTimeMillis()

    val java = ServerInstaller.java(version, lookup, null) {}
    val loaderVersion = if (loader == Loader.Vanilla) null else Loaders.newestStable(loader, version)
    println("Java: $java; ${loader.label} ${loaderVersion.orEmpty()}")
    val launch = ServerInstaller.install(dir, version, loader, loaderVersion, lookup, java, dir.resolve("logs/yumi-install.log")) {}
    println("Установлено за ${(System.currentTimeMillis() - started) / 1000} с; запуск: $launch")

    dir.resolve("eula.txt").writeText("eula=true\n")
    ServerProperties(dir.resolve("server.properties")).apply {
        this["server-port"] = port.toString()
        this["online-mode"] = "false"
        // Loopback only: a test server has no business asking Windows to open the firewall.
        this["server-ip"] = "127.0.0.1"
        this["motd"] = "§bYumi §fтест"
        this["level-type"] = if (numericProperties(version)) "FLAT" else "minecraft:flat"
        save()
    }
    val command = listOf(java.path, "-Xmx2G", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8") + launch + "nogui"
    val process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
    val output = StringBuffer()
    val reader = Thread {
        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            output.appendLine(line)
            println("  | $line")
        }
    }.apply { isDaemon = true; start() }

    var done = false
    val deadline = System.currentTimeMillis() + 300_000
    while (System.currentTimeMillis() < deadline && process.isAlive) {
        delay(500)
        if (Regex("""Done \([\d.,]+s\)!""").containsMatchIn(output)) { done = true; break }
    }
    var pinged = false
    var listed = false
    if (done) {
        val ping = runCatching { ServerPing.ping("127.0.0.1:$port") }
        ping.onSuccess {
            pinged = true
            println("PING: «${it.motd.joinToString("") { s -> s.text }}» ${it.version} ${it.online}/${it.max} ${it.latencyMs} мс, иконка: ${it.favicon?.size}")
        }.onFailure { println("PING FAIL: ${it.message}") }
        process.outputStream.write("list\n".toByteArray())
        process.outputStream.flush()
        delay(2000)
        listed = Regex("""There are \d+ (of a max|/)""").containsMatchIn(output) || "players online" in output
        process.outputStream.write("stop\n".toByteArray())
        process.outputStream.flush()
    }
    val exited = process.waitFor(90, TimeUnit.SECONDS)
    if (!exited) process.destroyForcibly()
    reader.join(2000)
    val ok = done && pinged && listed && exited
    println(if (ok) "OK: сервер поднялся, ответил на пинг и на list, остановился по stop (код ${process.exitValue()})" else "FAIL: done=$done ping=$pinged list=$listed exited=$exited")
    if (ok) 0 else 1
}

/** `--ping-test <address>…`: the multiplayer-list ping against real servers. */
internal fun pingTest(args: List<String>): Int = runBlocking {
    var failures = 0
    for (address in args) {
        runCatching { ServerPing.ping(address) }
            .onSuccess { println("$address: «${it.motd.joinToString("") { s -> s.text }.replace('\n', ' ')}» ${it.version} ${it.online}/${it.max} ${it.latencyMs} мс, иконка ${it.favicon?.size ?: 0} б, цветов ${it.motd.mapNotNull { s -> s.color }.distinct().size}") }
            .onFailure { failures++; println("$address: FAIL ${it.message}") }
    }
    failures
}

package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.AuthlibInjector
import com.yumi.launcher.core.LauncherJson
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.Loaders
import com.yumi.launcher.core.ServerInstaller
import com.yumi.launcher.core.ServerMods
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/** Who a server lets in. */
enum class ServerAuth(val label: String, val hint: String) {
    Offline("Все", "Заходит кто угодно под любым ником — для друзей без лицензии. Ники не защищены."),
    ElyBy("Ely.by", "Только аккаунты Ely.by: ник никто не займёт, скины видны всем."),
    Mojang("Лицензия", "Только купленные аккаунты Microsoft."),
}

/**
 * A server made from a pack: its version, loader and mods, in a folder of its own under
 * `servers/<id>` beside this file, `yumi-server.json`. The world and `server.properties` are the
 * server's own; the launcher edits the properties it has fields for and leaves the rest.
 */
@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val packId: String? = null,
    val version: String,
    val loader: Loader = Loader.Vanilla,
    /** Null until the first start picks the newest stable release; pinned from then on. */
    val loaderVersion: String? = null,
    val icon: Int = 0,
    val memoryMb: Int = 2048,
    val jvmArgs: String = "",
    val auth: ServerAuth = ServerAuth.Offline,
    val created: Long = System.currentTimeMillis(),
    val lastStarted: Long? = null,
    /** [signature] of what is installed in the folder; a different one reinstalls on the next start. */
    val installed: String? = null,
    /** What follows `java` and the memory flags to start it, as its installer left it. */
    val launch: List<String> = emptyList(),
    /** Mods a sync copied in from the pack, so the next one can take back those the pack dropped. */
    val syncedMods: List<String> = emptyList(),
) {
    val signature: String get() = "$version|$loader|${loaderVersion.orEmpty()}"
}

/** Minecraft's banned-players.json entry; other fields are left to the server. */
@Serializable
data class BannedPlayer(
    val name: String,
    val reason: String = "",
    val expires: String = "forever",
)

sealed interface ServerStatus {
    data object Stopped : ServerStatus
    data class Installing(val step: String, val fraction: Float) : ServerStatus
    data object Starting : ServerStatus
    data class Running(val since: Long) : ServerStatus
    data object Stopping : ServerStatus
    data class Failed(val message: String) : ServerStatus
}

/** Anything between pressing start and the process being gone. */
val ServerStatus.active: Boolean get() = this !is ServerStatus.Stopped && this !is ServerStatus.Failed

/** One server's live side: the process, its terminal and who is online. */
class ServerSession {
    var status by mutableStateOf<ServerStatus>(ServerStatus.Stopped)
        internal set
    val lines = mutableStateListOf<String>()
    val players = mutableStateListOf<String>()
    internal val history = mutableListOf<String>()
    internal var process: Process? = null
    internal var job: Job? = null
    internal var stopping = false

    /** A known failure spotted in the output, to say instead of an exit code. */
    internal var problem: String? = null

    /** Commands sent, oldest first, for the terminal's arrow keys. */
    val sent: List<String> get() = history

    internal fun append(chunk: List<String>) {
        lines.addAll(chunk)
        if (lines.size > MAX_LINES) lines.removeRange(0, lines.size - MAX_LINES)
        for (line in chunk) {
            when {
                status == ServerStatus.Starting && DONE.containsMatchIn(line) -> status = ServerStatus.Running(System.currentTimeMillis())
                "FAILED TO BIND TO PORT" in line -> problem = "Порт занят — возможно, сервер уже запущен"
                "agree to the EULA" in line -> problem = "Сервер ждёт согласия с EULA"
                else -> {
                    JOINED.find(line)?.let { match -> match.groupValues[1].let { if (it !in players) players += it } }
                    LEFT.find(line)?.let { match -> players.remove(match.groupValues[1]) }
                }
            }
        }
    }

    private companion object {
        const val MAX_LINES = 5000
        val DONE = Regex("""Done \([\d.,]+s\)!""")
        val JOINED = Regex("""]: (\w{1,16}) joined the game""")
        val LEFT = Regex("""]: (\w{1,16}) left the game""")
    }
}

/** An address friends can use, and what network it is on. */
data class LocalAddress(val ip: String, val network: String)

/**
 * The servers this computer hosts: made from packs, installed the way each loader does it, started
 * with the pack's Java, and stopped the way a server owner would — `stop` on its console, so the
 * world is saved — with a forced kill only as the last resort.
 */
class ServerManager(private val model: LauncherModel, private val scope: CoroutineScope) {
    val all = mutableStateListOf<ServerProfile>()
    private val sessions = HashMap<String, ServerSession>()

    /** The id of the server whose mods are being carried over, while that runs. */
    var syncing by mutableStateOf<String?>(null)
        private set

    /** The outcome of the last mods sync, with the id of the server it was for. */
    var modsNotice by mutableStateOf<Pair<String, String>?>(null)

    val root: File get() = LauncherPaths.root.resolve("servers")

    init {
        reload()
        // Closing the launcher must not leave a server running with no console to stop it from.
        Runtime.getRuntime().addShutdownHook(Thread(::shutdown, "yumi-servers-shutdown"))
    }

    fun reload() {
        all.clear()
        all += root.listFiles().orEmpty()
            .mapNotNull { dir -> dir.resolve(FILE).takeIf { it.isFile } }
            .mapNotNull { runCatching { LauncherJson.decodeFromString<ServerProfile>(it.readText()) }.getOrNull() }
            .sortedByDescending { it.lastStarted ?: it.created }
    }

    operator fun get(id: String?): ServerProfile? = all.firstOrNull { it.id == id }

    fun folder(profile: ServerProfile): File = root.resolve(profile.id)
    fun modsDir(profile: ServerProfile): File = folder(profile).resolve("mods")
    fun properties(profile: ServerProfile) = ServerProperties(folder(profile).resolve("server.properties"))
    fun port(profile: ServerProfile): Int = properties(profile).int("server-port", 25565)

    fun bannedPlayers(profile: ServerProfile): List<BannedPlayer> {
        val file = folder(profile).resolve("banned-players.json")
        return if (file.isFile) LauncherJson.decodeFromString(file.readText()) else emptyList()
    }

    fun session(id: String): ServerSession = synchronized(sessions) { sessions.getOrPut(id) { ServerSession() } }

    val anyActive: Boolean get() = all.any { session(it.id).status.active }

    /** A port no other server of ours already has, starting from Minecraft's own. */
    fun freePort(): Int {
        val taken = all.map(::port).toSet()
        return generateSequence(25565) { it + 1 }.first { it !in taken }
    }

    fun create(name: String, pack: Instance, port: Int, auth: ServerAuth, memoryMb: Int): ServerProfile {
        val id = folderId(name, "server") { root.resolve(it).exists() || this[it] != null }
        val profile = ServerProfile(
            id = id, name = name.trim(), packId = pack.id, version = pack.version, loader = pack.loader,
            loaderVersion = pack.loaderVersion, icon = pack.icon, memoryMb = memoryMb, auth = auth,
        )
        val dir = folder(profile)
        dir.mkdirs()
        // Asked in the form that created it; the server refuses to start without this line.
        dir.resolve("eula.txt").writeText("# https://aka.ms/MinecraftEULA — accepted in Yumi Launcher\neula=true\n")
        properties(profile).apply {
            val numeric = numericProperties(profile.version)
            this["server-port"] = port.toString()
            this["motd"] = profile.name
            this["online-mode"] = (auth != ServerAuth.Offline).toString()
            this["max-players"] = "10"
            this["gamemode"] = if (numeric) "0" else GameMode.Survival.key
            this["difficulty"] = if (numeric) "2" else Difficulty.Normal.key
            this["pvp"] = "true"
            this["white-list"] = "false"
            this["spawn-protection"] = "0"
            // Modded worlds fly — jetpacks, gliders — and vanilla's kick for flying catches them.
            if (profile.loader != Loader.Vanilla) this["allow-flight"] = "true"
            // Chat signing needs Mojang's keys, which an offline player does not have.
            this["enforce-secure-profile"] = "false"
            save()
        }
        save(profile)
        if (profile.loader != Loader.Vanilla) syncMods(profile)
        return profile
    }

    fun save(profile: ServerProfile) {
        val dir = folder(profile)
        dir.mkdirs()
        dir.resolve(FILE).writeText(LauncherJson.encodeToString(ServerProfile.serializer(), profile))
        val at = all.indexOfFirst { it.id == profile.id }
        if (at >= 0) all[at] = profile else all.add(0, profile)
    }

    fun delete(profile: ServerProfile) {
        if (session(profile.id).status.active) return
        folder(profile).deleteRecursively()
        all.removeAll { it.id == profile.id }
        synchronized(sessions) { sessions.remove(profile.id) }
    }

    /** Takes the pack's current version, loader and mods; the next start installs them. */
    fun updateFromPack(profile: ServerProfile) {
        val pack = model.instances[profile.packId] ?: return
        val updated = profile.copy(version = pack.version, loader = pack.loader, loaderVersion = pack.loaderVersion)
        save(updated)
        if (updated.loader != Loader.Vanilla) syncMods(updated)
    }

    fun start(id: String) {
        val initial = this[id] ?: return
        val session = session(id)
        if (session.status.active) return
        val clash = all.firstOrNull { it.id != id && session(it.id).status.active && port(it) == port(initial) }
        if (clash != null) {
            session.status = ServerStatus.Failed("Порт ${port(initial)} уже занят сервером «${clash.name}» — смените порт в настройках")
            return
        }
        val settings = model.settings
        val customJava = if (settings.customJava) File(settings.javaPath.trim()) else null
        if (customJava != null && !customJava.isFile) {
            session.status = ServerStatus.Failed("Java не найдена: $customJava")
            return
        }
        session.stopping = false
        session.problem = null
        session.job = scope.launch {
            var profile = initial
            try {
                session.lines.clear()
                session.players.clear()
                session.status = ServerStatus.Installing("Java", 0f)
                val java = ServerInstaller.java(profile.version, model.lookup, customJava) { session.status = ServerStatus.Installing("Java", it) }
                if (profile.installed != profile.signature || profile.launch.isEmpty()) {
                    session.status = ServerStatus.Installing("Сервер", 0f)
                    val loaderVersion = if (profile.loader == Loader.Vanilla) null else profile.loaderVersion ?: Loaders.newestStable(profile.loader, profile.version)
                    val dir = folder(profile)
                    val launch = ServerInstaller.install(
                        dir, profile.version, profile.loader, loaderVersion, model.lookup, java, dir.resolve("logs").resolve("yumi-install.log"),
                    ) { session.status = ServerStatus.Installing("Сервер", it) }
                    profile = profile.copy(loaderVersion = loaderVersion, launch = launch)
                    profile = profile.copy(installed = profile.signature)
                    save(profile)
                }
                val injector = if (profile.auth == ServerAuth.ElyBy) AuthlibInjector.ensure() else null
                withContext(Dispatchers.IO) {
                    properties(profile).apply {
                        this["online-mode"] = (profile.auth != ServerAuth.Offline).toString()
                        save()
                    }
                    val eula = folder(profile).resolve("eula.txt")
                    if (!eula.isFile || "eula=true" !in eula.readText()) eula.writeText("eula=true\n")
                }
                val command = buildList {
                    add(java.path)
                    add("-Xmx${profile.memoryMb}M")
                    add("-Xms${minOf(profile.memoryMb, 1024)}M")
                    // The terminal reads UTF-8; left alone, Java writes the console in the system's code page.
                    addAll(listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"))
                    injector?.let { add(AuthlibInjector.argument(it)) }
                    addAll(profile.jvmArgs.split(Regex("\\s+")).filter { it.isNotBlank() })
                    addAll(profile.launch)
                    add("nogui")
                }
                session.status = ServerStatus.Starting
                session.append(listOf("[Yumi] ${profile.loader.label} ${profile.version}${profile.loaderVersion?.let { " ($it)" }.orEmpty()}, память ${profile.memoryMb} МБ"))
                val process = withContext(Dispatchers.IO) {
                    ProcessBuilder(command).directory(folder(profile)).redirectErrorStream(true).start()
                }
                session.process = process
                profile = profile.copy(lastStarted = System.currentTimeMillis())
                save(profile)
                pump(process, session)
                val code = withContext(Dispatchers.IO) { process.waitFor() }
                session.process = null
                session.players.clear()
                val problem = session.problem
                session.status = when {
                    session.stopping -> ServerStatus.Stopped
                    problem != null -> ServerStatus.Failed(problem)
                    code == 0 -> ServerStatus.Stopped
                    else -> ServerStatus.Failed("Сервер завершился с кодом $code — подробности в терминале")
                }
            } catch (e: CancellationException) {
                session.process?.destroyForcibly()
                session.process = null
                session.status = ServerStatus.Stopped
                throw e
            } catch (e: Exception) {
                session.status = ServerStatus.Failed(e.message ?: e.toString())
            }
        }
    }

    /** `stop` on the console, which saves the world; forced after a minute of no answer. */
    fun stop(id: String) {
        val session = session(id)
        when (session.status) {
            is ServerStatus.Installing -> session.job?.cancel()
            ServerStatus.Starting, is ServerStatus.Running -> {
                session.stopping = true
                session.status = ServerStatus.Stopping
                write(session, "stop")
                val process = session.process ?: return
                scope.launch(Dispatchers.IO) { if (!process.waitFor(60, TimeUnit.SECONDS)) process.destroyForcibly() }
            }
            ServerStatus.Stopping -> kill(id)
            else -> Unit
        }
    }

    /** Ends the process at once. The world keeps what was last saved — usually a few minutes back. */
    fun kill(id: String) {
        val session = session(id)
        session.stopping = true
        session.process?.destroyForcibly()
    }

    fun send(id: String, command: String) {
        val session = session(id)
        val text = command.trim().removePrefix("/")
        if (text.isEmpty() || session.process == null) return
        session.history.remove(text)
        session.history += text
        session.append(listOf("> $text"))
        write(session, text)
    }

    private fun write(session: ServerSession, text: String) {
        val process = session.process ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                process.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8))
                process.outputStream.flush()
            }
        }
    }

    /** Before the launcher closes: every server stopped properly, waiting up to 45 seconds for them. */
    suspend fun stopAll() {
        all.filter { session(it.id).status.active }.forEach { stop(it.id) }
        withTimeoutOrNull(45_000) { while (anyActive) delay(200) }
        synchronized(sessions) { sessions.values.toList() }.forEach { it.process?.destroyForcibly() }
    }

    private fun shutdown() {
        val alive = synchronized(sessions) { sessions.values.mapNotNull { it.process } }.filter { it.isAlive }
        alive.forEach { process ->
            runCatching {
                process.outputStream.write("stop\n".toByteArray())
                process.outputStream.flush()
            }
        }
        alive.forEach { if (!it.waitFor(20, TimeUnit.SECONDS)) it.destroyForcibly() }
    }

    /** Copies the pack's mods in, minus client-only ones, and says what stayed behind. */
    fun syncMods(profile: ServerProfile) {
        val pack = model.instances[profile.packId]
        if (pack == null) {
            modsNotice = profile.id to "Сборка сервера удалена — моды взять неоткуда"
            return
        }
        if (syncing != null) return
        syncing = profile.id
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ServerMods.sync(model.instances.modsDir(pack), modsDir(profile), profile.syncedMods.toSet()) }
                this@ServerManager[profile.id]?.let { save(it.copy(syncedMods = result.copied)) }
                val titles = model.mods.titles(pack)
                val skipped = result.clientOnly.map { titles[it.name] ?: it.name.removeSuffix(".jar") }
                modsNotice = profile.id to buildString {
                    append("Из сборки: ${result.copied.size}")
                    if (skipped.isNotEmpty()) append(". Только для игроков, на сервере не нужны: ${skipped.joinToString(", ")}")
                }
            } catch (e: Exception) {
                modsNotice = profile.id to "Не удалось перенести моды: ${e.message}"
            } finally {
                syncing = null
            }
        }
    }

    /** Reads the process's output into its terminal, a batch at a time so a burst does not flood the UI. */
    private suspend fun pump(process: Process, session: ServerSession) = withContext(Dispatchers.IO) {
        process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val batch = ArrayList<String>()
            while (true) {
                val line = reader.readLine() ?: break
                batch += ANSI.replace(line, "")
                if (batch.size >= 200 || !reader.ready()) {
                    val chunk = batch.toList()
                    batch.clear()
                    withContext(Dispatchers.Main) { session.append(chunk) }
                }
            }
            if (batch.isNotEmpty()) withContext(Dispatchers.Main) { session.append(batch.toList()) }
        }
    }

    private companion object {
        const val FILE = "yumi-server.json"

        /** Colour codes Forge's console prints even into a pipe. */
        val ANSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    }
}

/**
 * This computer's addresses that friends could reach: the local network, and the virtual networks
 * people set up to play together — Radmin VPN, ZeroTier, Hamachi, Tailscale. Virtual machines' and
 * WSL's adapters are left out; nobody connects through those.
 */
fun localAddresses(): List<LocalAddress> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { nif ->
            val name = nif.displayName.lowercase() + " " + nif.name.lowercase()
            val network = when {
                "radmin" in name -> "Radmin VPN"
                "zerotier" in name -> "ZeroTier"
                "hamachi" in name -> "Hamachi"
                "tailscale" in name -> "Tailscale"
                nif.isVirtual || IGNORED.any { it in name } -> return@flatMap emptyList()
                else -> "Локальная сеть"
            }
            nif.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLinkLocalAddress && !it.isLoopbackAddress && !benchmarking(it) }
                .map { LocalAddress(it.hostAddress, network) }
        }
        .distinctBy { it.ip }
        .sortedBy { if (it.network == "Локальная сеть") 0 else 1 }
}.getOrDefault(emptyList())

/** 198.18.0.0/15: reserved for testing, and what proxy and VPN clients give their virtual adapters. */
private fun benchmarking(address: Inet4Address): Boolean {
    val bytes = address.address
    return bytes[0].toInt() and 0xFF == 198 && (bytes[1].toInt() and 0xFE) == 18
}

private val IGNORED = listOf("virtualbox", "vmware", "hyper-v", "vethernet", "wsl", "bluetooth", "loopback", "teredo", "isatap", "docker")

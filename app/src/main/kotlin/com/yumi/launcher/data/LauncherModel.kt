package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.AuthlibInjector
import com.yumi.launcher.core.GameAccount
import com.yumi.launcher.core.GameLauncher
import com.yumi.launcher.core.Installer
import com.yumi.launcher.core.LaunchOptions
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.Loaders
import com.yumi.launcher.core.VersionEntry
import com.yumi.launcher.core.Stage
import com.yumi.launcher.core.VersionManifest
import com.yumi.launcher.core.loadManifest
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.AfterLaunch
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.TimeUnit

sealed interface PlayStatus {
    data object Idle : PlayStatus
    data class Preparing(val stage: Stage, val fraction: Float) : PlayStatus
    data class Running(val instanceId: String, val since: Long) : PlayStatus
    data class Failed(val message: String) : PlayStatus
}

/**
 * The launcher's working state: packs, Mojang's version list, and the one game it runs at a time,
 * from the first download to the process exiting.
 */
class LauncherModel(val settings: LauncherSettings, private val scope: CoroutineScope) {
    val instances: InstanceStore = run {
        settings.gameDir.takeIf { it.isNotBlank() }?.let { LauncherPaths.root = File(it) }
        InstanceStore()
    }

    /** True while the "where should Minecraft go" question is on screen. */
    var askingForFolder by mutableStateOf(false)
        private set
    var movingPacks by mutableStateOf(false)
        private set
    private var afterFolder: (() -> Unit)? = null

    val mods = ModManager(this, scope)
    val servers = ServerManager(this, scope)
    val quickJoin = QuickJoinList(scope)
    val ely = ElyAccount(settings, scope)
    val packImports = PackImporter(this, scope)
    val friends = FriendsModel(this, scope)

    var manifest by mutableStateOf<VersionManifest?>(null)
        private set
    var manifestError by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<PlayStatus>(PlayStatus.Idle)
        private set

    /**
     * The server the running game is on, as `host[:port]`: the one it was started into, then
     * whichever the log says it connects to. Null in the menus' first minutes, in a world of its
     * own, and once the game is closed.
     */
    var gameServer by mutableStateOf<String?>(null)
        private set

    /** The running game's output, newest last, capped so a chatty mod cannot eat the heap. */
    val log = mutableStateListOf<String>()
    var logFile by mutableStateOf<File?>(null)
        private set

    private var process: Process? = null
    private var job: Job? = null
    private var stopping = false

    val selected: Instance? get() = instances[settings.selectedInstance] ?: instances.all.firstOrNull()
    val busy: Boolean get() = status is PlayStatus.Preparing || status is PlayStatus.Running

    /** Finds a vanilla version in Mojang's list, for the installers. */
    val lookup: (String) -> VersionEntry?
        get() {
            val versions = manifest?.versions.orEmpty()
            return { id -> versions.firstOrNull { it.id == id } }
        }

    /** The name the player is seen under with the chosen account; empty when there is none yet. */
    val playerName: String
        get() = when (settings.account) {
            AccountKind.ElyBy -> ely.session?.name.orEmpty()
            else -> settings.nick
        }

    init {
        refreshVersions()
    }

    fun refreshVersions() {
        scope.launch {
            manifestError = null
            try {
                val loaded = loadManifest()
                manifest = loaded
                // A first run gets one pack on the latest release, so "Играть" works straight away.
                if (instances.all.isEmpty()) select(instances.create("Minecraft ${loaded.latest.release}", loaded.latest.release, 0))
            } catch (e: Exception) {
                manifestError = "Нет связи с Mojang: ${e.message}"
            }
        }
    }

    fun select(instance: Instance) {
        settings.selectedInstance = instance.id
    }

    /**
     * Runs [action] once there is a game folder — asking for one first if the player has not
     * picked it yet. Downloads, packs and servers all go there.
     */
    fun withGameFolder(action: () -> Unit) {
        if (settings.gameDir.isNotBlank()) return action()
        afterFolder = action
        askingForFolder = true
    }

    /**
     * Downloads what [pack] — the selected one by default — needs and starts it, straight into the
     * server at [join] if one is given. [onStarted] hands the window its after-launch behaviour;
     * [onExit] is called once the game has closed.
     */
    fun play(onStarted: (AfterLaunch) -> Unit, onExit: () -> Unit, pack: Instance? = null, join: String? = null) {
        if (busy || movingPacks) return
        // The first download asks where it should go; the launch resumes once that is answered.
        if (settings.gameDir.isBlank()) return withGameFolder { play(onStarted, onExit, pack, join) }
        val instance = pack?.let { instances[it.id] } ?: selected ?: return fail("Сначала создайте сборку")
        when (settings.account) {
            AccountKind.Microsoft -> return fail("Вход через Microsoft ещё в разработке — выберите «Офлайн» или Ely.by")
            AccountKind.ElyBy -> if (ely.session == null) return fail("Войдите в аккаунт Ely.by")
            AccountKind.Offline -> if (!settings.nickValid) return fail("Задайте ник: 3–16 символов, латиница, цифры и _")
        }
        val java = if (settings.customJava) File(settings.javaPath.trim()) else null
        if (java != null && !java.isFile) return fail("Java не найдена: $java")
        val lookup = lookup

        stopping = false
        job = scope.launch {
            log.clear()
            gameServer = join
            status = PlayStatus.Preparing(Stage.Game, 0f)
            try {
                val gameDir = instances.gameDir(instance)
                val installer = Installer { stage, fraction -> status = PlayStatus.Preparing(stage, fraction) }
                val launchId = if (instance.loader == Loader.Vanilla) instance.version else loader(instance, installer, lookup, gameDir, java)
                val prepared = installer.prepare(launchId, lookup, gameDir, java)
                val clientJar = LauncherPaths.versions.resolve(prepared.profile.jar).resolve("${prepared.profile.jar}.jar")
                // Only looks: a pack that cannot be written must not keep the game from starting.
                withContext(Dispatchers.IO) { runCatching { MenuStyles.apply(gameDir, instance.menuStyle, clientJar) } }
                val eraOf = { pack: Instance -> GameOptions.era(lookup(pack.version)) }
                withContext(Dispatchers.IO) {
                    runCatching { GameOptions.beforeStart(instances, instance, settings.syncOptions && instance.syncOptions, eraOf) }
                }
                val account = account()
                status = PlayStatus.Preparing(Stage.Starting, 1f)
                val options = LaunchOptions(
                    account = account,
                    gameDir = gameDir,
                    memoryMb = instance.memoryMb ?: settings.memoryMb,
                    jvmArgs = instance.jvmArgs.split(Regex("\\s+")).filter { it.isNotBlank() },
                    width = instance.width,
                    height = instance.height,
                    fullscreen = instance.fullscreen,
                    join = join,
                )
                val file = instances.folder(instance).resolve("logs").resolve("launch.log")
                logFile = file
                val game = withContext(Dispatchers.IO) { GameLauncher.start(GameLauncher.command(prepared, options), gameDir, file) }
                process = game
                val started = System.currentTimeMillis()
                status = PlayStatus.Running(instance.id, started)
                instances[instance.id]?.let { instances.save(it.copy(lastPlayed = started)) }
                onStarted(settings.afterLaunch)

                val tail = launch { tail(file, game) }
                val code = withContext(Dispatchers.IO) { game.waitFor() }
                tail.join()
                process = null
                gameServer = null
                // The player's changes reach the other packs now, not only at the next start.
                withContext(Dispatchers.IO) { runCatching { GameOptions.settle(instances) } }
                val seconds = (System.currentTimeMillis() - started) / 1000
                instances[instance.id]?.let { instances.save(it.copy(playSeconds = it.playSeconds + seconds)) }
                status = if (code == 0 || stopping) PlayStatus.Idle else PlayStatus.Failed("Игра завершилась с кодом $code — подробности в консоли")
                onExit()
            } catch (e: CancellationException) {
                status = PlayStatus.Idle
                throw e
            } catch (e: Exception) {
                status = PlayStatus.Failed(e.message ?: e.toString())
            }
        }
    }

    /**
     * Installs the pack's mod loader if needed and returns the version id to launch. A pack without
     * a pinned loader version gets the newest stable one, and keeps it: a loader that silently
     * updated itself between two evenings of play is how a world stops loading.
     */
    private suspend fun loader(instance: Instance, installer: Installer, lookup: (String) -> VersionEntry?, gameDir: File, java: File?): String {
        status = PlayStatus.Preparing(Stage.Loader, 0f)
        val version = instance.loaderVersion ?: Loaders.newestStable(instance.loader, instance.version).also { pick ->
            instances[instance.id]?.let { instances.save(it.copy(loaderVersion = pick)) }
        }
        // Forge and NeoForge install on top of the vanilla game, and run their installer on its Java.
        val installerJava = if (instance.loader == Loader.Forge || instance.loader == Loader.NeoForge) {
            installer.prepare(instance.version, lookup, gameDir, java).java
        } else {
            java ?: File("java")
        }
        status = PlayStatus.Preparing(Stage.Loader, 0.5f)
        return Loaders.install(instance.loader, instance.version, version, installerJava, instances.folder(instance).resolve("logs").resolve("loader-install.log"))
    }

    /** Who the game runs as: the offline nick, or a checked Ely.by session with authlib-injector. */
    private suspend fun account(): GameAccount = when (settings.account) {
        AccountKind.ElyBy -> {
            status = PlayStatus.Preparing(Stage.Account, 0f)
            val session = ely.fresh()
            val injector = AuthlibInjector.ensure()
            GameAccount(session.name, session.uuid, session.accessToken, "mojang", injector)
        }
        else -> GameAccount.offline(settings.nick)
    }

    /** Cancels a download, or ends the game — forcibly if it has not closed in five seconds. */
    fun stop() {
        when (status) {
            is PlayStatus.Preparing -> {
                job?.cancel()
                status = PlayStatus.Idle
            }
            is PlayStatus.Running -> {
                stopping = true
                process?.let { game ->
                    game.destroy()
                    scope.launch(Dispatchers.IO) { if (!game.waitFor(5, TimeUnit.SECONDS)) game.destroyForcibly() }
                }
            }
            else -> Unit
        }
    }

    /**
     * Makes [folder] the game folder. Packs move along with it — they hold the worlds — while
     * versions, libraries and Java are simply downloaded again there when needed.
     */
    fun useGameFolder(folder: File) {
        askingForFolder = false
        scope.launch {
            movingPacks = true
            try {
                withContext(Dispatchers.IO) {
                    movePacks(LauncherPaths.instances, folder.resolve("instances"))
                    movePacks(servers.root, folder.resolve("servers"))
                }
                LauncherPaths.root = folder
                settings.gameDir = folder.path
                instances.reload(LauncherPaths.instances)
                servers.reload()
            } catch (e: Exception) {
                status = PlayStatus.Failed("Не удалось перенести сборки: ${e.message}")
                afterFolder = null
            } finally {
                movingPacks = false
            }
            afterFolder?.also { afterFolder = null }?.invoke()
        }
    }

    fun cancelFolderQuestion() {
        askingForFolder = false
        afterFolder = null
    }

    private fun movePacks(from: File, to: File) {
        if (!from.isDirectory || from.canonicalPath == to.canonicalPath) return
        to.mkdirs()
        for (pack in from.listFiles().orEmpty()) {
            val target = to.resolve(pack.name)
            if (target.exists()) continue
            try {
                Files.move(pack.toPath(), target.toPath())
            } catch (e: Exception) {
                // Another drive: a move is a copy there.
                pack.copyRecursively(target)
                pack.deleteRecursively()
            }
        }
    }

    fun dismissError() {
        if (status is PlayStatus.Failed) status = PlayStatus.Idle
    }

    fun openFolder(dir: File) {
        scope.launch(Dispatchers.IO) {
            dir.mkdirs()
            runCatching { Desktop.getDesktop().open(dir) }
        }
    }

    private fun fail(message: String) {
        status = PlayStatus.Failed(message)
    }

    /** Follows the game's log file into [log] until the process is gone and the file is read out. */
    private suspend fun tail(file: File, game: Process) = withContext(Dispatchers.IO) {
        var position = 0L
        val pending = StringBuilder()
        while (true) {
            val alive = game.isAlive
            val length = if (file.isFile) file.length() else 0L
            if (length > position) {
                val bytes = RandomAccessFile(file, "r").use { raf ->
                    raf.seek(position)
                    ByteArray((length - position).coerceAtMost(1L shl 20).toInt()).also { raf.readFully(it) }
                }
                position += bytes.size
                pending.append(String(bytes, Charsets.UTF_8))
                val lines = GameLog.drain(pending)
                if (lines.isNotEmpty()) withContext(Dispatchers.Main) { append(lines) }
                continue
            }
            if (!alive) break
            delay(250)
        }
        if (pending.isNotBlank()) withContext(Dispatchers.Main) { append(pending.lines().filter { it.isNotBlank() }) }
    }

    private fun append(lines: List<String>) {
        for (line in lines) {
            CONNECTING.find(line)?.let { m -> gameServer = m.groupValues[1] + if (m.groupValues[2] == "25565") "" else ":" + m.groupValues[2] }
            if ("Starting integrated minecraft server" in line) gameServer = null
        }
        log.addAll(lines)
        if (log.size > MAX_LOG) log.removeRange(0, log.size - MAX_LOG)
    }

    private companion object {
        const val MAX_LOG = 4000

        /** What the game logs as it joins a server, from the multiplayer screen or a direct connect. */
        val CONNECTING = Regex("""Connecting to ([^\s,]+), (\d+)""")
    }
}

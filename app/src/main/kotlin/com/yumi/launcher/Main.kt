package com.yumi.launcher

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.PackArchives
import com.yumi.launcher.core.ServerMods
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PackCheck
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.settings.SceneMotion
import com.yumi.launcher.settings.SceneQuality
import com.yumi.launcher.settings.ThemeMode
import com.yumi.launcher.ui.home.LauncherApp
import com.yumi.launcher.ui.home.LauncherState
import com.yumi.launcher.ui.home.PackRequest
import com.yumi.launcher.ui.home.Section
import com.yumi.launcher.ui.home.ServerPackTarget
import com.yumi.launcher.ui.home.ServerTab
import com.yumi.launcher.ui.home.ServerView
import com.yumi.launcher.ui.home.ServersTab
import com.yumi.launcher.ui.theme.LauncherTheme
import com.yumi.launcher.ui.theme.MenuPack
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "--launch-test" -> exitProcess(launchTest(args.drop(1)))
        "--server-test" -> exitProcess(serverTest(args.drop(1)))
        "--ping-test" -> exitProcess(pingTest(args.drop(1)))
        "--pack-test" -> exitProcess(packTest(args.drop(1)))
        "--menu-packs" -> exitProcess(menuPacks(args.drop(1)))
        "--options-test" -> exitProcess(optionsTest(args.drop(1)))
        "--friends-test" -> exitProcess(friendsTest(args.drop(1)))
    }
    // Native-looking Swing dialogs: the folder chooser is Swing's, and its default look is from 2006.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    launcher(args)
}

/** `--menu-packs <folder>`: every palette and theme of the game-menu pack, as zips to drop into any game's resource packs. */
private fun menuPacks(args: List<String>): Int {
    val folder = File(args.firstOrNull() ?: return 2.also { println("Использование: --menu-packs <папка>") }).apply { mkdirs() }
    for (palette in Palette.entries) for (dark in listOf(false, true)) {
        val started = System.currentTimeMillis()
        val file = folder.resolve(MenuPack.title(palette, dark) + ".zip")
        file.writeBytes(MenuPack.build(palette, dark))
        println("${file.name}: ${file.length() / 1024} КБ за ${System.currentTimeMillis() - started} мс")
    }
    folder.resolve(MenuPack.FONT_TITLE + ".zip").writeBytes(MenuPack.buildFont())
    return 0
}

private fun packTest(args: List<String>): Int = runBlocking {
    if (args.size < 2) {
        println("Использование: --pack-test <архив.mrpack|архив.zip> <тестовая папка> [ключ CurseForge]")
        return@runBlocking 2
    }
    try {
        val archive = File(args[0])
        val folder = File(args[1])
        val info = PackArchives.inspect(archive)
        PackArchives.install(archive, folder, args.getOrNull(2).orEmpty()) {}
        ServerMods.sync(folder.resolve("minecraft/mods"), folder.resolve("hosted/mods"), emptySet(), folder.resolve("server"))
        println("${info.name}: ${info.loader.label} ${info.version}, клиент=${folder.resolve("minecraft").walkTopDown().count { it.isFile }}, сервер=${folder.resolve("hosted").walkTopDown().count { it.isFile }}")
        0
    } catch (e: Exception) {
        System.err.println("Импорт не удался: ${e.message}")
        1
    }
}

private fun launcher(args: Array<String>) = application {
    // As in YumiDesktop: open, photograph the window in each theme, quit. Its own preferences node,
    // so a smoke run never overwrites the theme the user picked.
    val smoke = "--ui-smoke" in args
    val settings = remember {
        LauncherSettings(Preferences.userRoot().node(if (smoke) "com/yumi/launcher-smoke" else "com/yumi/launcher")).also {
            // Nor does it leave packs in the real game folder.
            if (smoke) {
                it.gameDir = File(".local/smoke-data").absolutePath
                LauncherPaths.config = File(".local/smoke-config").absoluteFile
                // A Fabric pack, so the mods screen has a catalogue to show.
                File(it.gameDir, "instances/smoke-fabric").apply { mkdirs() }.resolve("instance.json")
                    .writeText("""{"id":"smoke-fabric","name":"Fabric 1.21.1","version":"1.21.1","loader":"Fabric","menuStyle":{"palette":"Pink","dark":false,"font":true}}""")
                it.selectedInstance = "smoke-fabric"
                // Friends from a running friends server, when the run is given one: "address|name|token".
                it.friendsServer = ""
                it.friendsToken = ""
                System.getenv("YUMI_SMOKE_FRIENDS")?.split('|')?.takeIf { spec -> spec.size == 3 }?.let { (url, name, token) ->
                    it.friendsServer = url
                    it.friendsName = name
                    it.friendsToken = token
                }
                // A server made from it, and two servers to join: one that answers and one that does not.
                File(it.gameDir, "servers/smoke-server").apply { mkdirs() }.apply {
                    resolve("yumi-server.json").writeText(
                        """{"id":"smoke-server","name":"Сервер для друзей","packId":"smoke-fabric","version":"1.21.1","loader":"Fabric","memoryMb":4096}""",
                    )
                    resolve("server.properties").writeText("server-port=25565\nmotd=§bYumi §7— §aвыживание с друзьями\nmax-players=10\n")
                    resolve("banned-players.json").writeText(
                        """[{"uuid":"00000000-0000-0000-0000-000000000001","name":"Griefer228","created":"2026-09-20 18:04:11 +0500","source":"Server","expires":"forever","reason":"Сломал спавн"},""" +
                            """{"uuid":"00000000-0000-0000-0000-000000000002","name":"Spammer","created":"2026-09-22 10:00:00 +0500","source":"Steve","expires":"2026-10-12 14:00:00 +0500","reason":"Banned by an operator."}]""",
                    )
                }
                LauncherPaths.config.resolve("servers.json").apply { parentFile.mkdirs() }.writeText(
                    """[{"id":"hypixel","name":"Hypixel","address":"mc.hypixel.net"},{"id":"friend","name":"Сервер Димы","address":"127.0.0.1:25599"},""" +
                        """{"id":"gone","name":"Старый сервер","address":"127.0.0.1:25598"}]""",
                )
                // Dima's answers, and shares his pack; the old one is gone.
                smokeFriendServer(25599)
            }
        }
    }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp, position = WindowPosition(Alignment.Center))
    val icon = remember(::launcherIcon)
    var smokeState by remember { mutableStateOf<LauncherState?>(null) }
    var smokeModel by remember { mutableStateOf<LauncherModel?>(null) }
    // Alt+F4 and the taskbar's "close" go through the launcher, which may have servers to stop first.
    var closeRequests by remember { mutableIntStateOf(0) }
    Window(onCloseRequest = { closeRequests++ }, state = windowState, title = "Yumi", icon = icon, undecorated = true) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1100, 720)
            if (smoke) {
                window.isAlwaysOnTop = true
                window.toFront()
                val output = File(".local/screenshots").also(File::mkdirs)
                // `--only=server` photographs just the shots whose names contain "server" — quicker while
                // working on one screen; `--only=home-,scene-` takes several.
                val only = args.firstOrNull { it.startsWith("--only=") }?.removePrefix("--only=")?.split(',')
                val state = smokeState!!
                suspend fun shot(name: String, wait: Long, setup: () -> Unit) {
                    if (only != null && only.none { it in name }) return
                    setup()
                    delay(wait)
                    val area = Rectangle(window.contentPane.locationOnScreen, window.contentPane.size)
                    ImageIO.write(Robot().createScreenCapture(area), "png", File(output, "$name.png"))
                }
                // Presses the left button over the window at x, y without moving the real pointer.
                fun click(x: Int, y: Int) {
                    val target = SwingUtilities.getDeepestComponentAt(window.contentPane, x, y) ?: return
                    val at = SwingUtilities.convertPoint(window.contentPane, x, y, target)
                    val time = System.currentTimeMillis()
                    for (id in listOf(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
                        val mask = if (id == MouseEvent.MOUSE_MOVED) 0 else MouseEvent.BUTTON1_DOWN_MASK
                        target.dispatchEvent(MouseEvent(target, id, time, if (id == MouseEvent.MOUSE_RELEASED) 0 else mask, at.x, at.y, 1, false, MouseEvent.BUTTON1))
                    }
                }
                // Turns the wheel over the window at x, y without moving the real pointer.
                fun wheel(x: Int, y: Int, clicks: Int) {
                    val target = SwingUtilities.getDeepestComponentAt(window.contentPane, x, y) ?: return
                    val at = SwingUtilities.convertPoint(window.contentPane, x, y, target)
                    target.dispatchEvent(
                        MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, at.x, at.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, clicks),
                    )
                }
                for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                    val suffix = theme.name.lowercase()
                    settings.theme = theme
                    settings.account = AccountKind.Offline
                    shot("home-$suffix", 4000) { state.section = Section.Home }
                    shot("news-hidden-$suffix", 1200) { settings.newsHidden = true }
                    settings.newsHidden = false
                    // With friends from a friends server (YUMI_SMOKE_FRIENDS): "Зайти к другу" to a Yumi server without its pack.
                    // Only while there is none here — with one, the dialog would go straight into the game.
                    val friends = smokeModel?.friends
                    friends?.view?.friends?.firstOrNull { it.online && it.server != null && friends.servers.checks[it.server] is PackCheck.Missing }?.let { friend ->
                        shot("friend-pack-$suffix", 5000) {
                            state.serverPack = ServerPackTarget.Friend(friend.name, friend.server!!, friend.pack?.name ?: "Сервер ${friend.name}", smokeModel?.selected?.id)
                        }
                        state.serverPack = null
                    }
                    shot("packs-$suffix", 3000) { state.section = Section.Packs }
                    shot("pack-import-$suffix", 3000) { state.section = Section.Packs; state.packRequest = PackRequest.Import }
                    shot("pack-editor-$suffix", 1400) { state.section = Section.Packs; state.packRequest = PackRequest.Edit("smoke-fabric") }
                    // Further down: how the pack starts, and last its game's menus.
                    shot("pack-editor-launch-$suffix", 1200) { wheel(270, 500, 9) }
                    shot("pack-editor-menu-$suffix", 1200) { wheel(270, 500, 40) }
                    shot("mods-$suffix", 5000) { state.section = Section.Mods }
                    shot("settings-$suffix", 3000) { state.section = Section.Settings }
                    // The game group — memory, Java, shared game settings — is at the bottom.
                    shot("settings-game-$suffix", 1200) { wheel(270, 500, 80) }
                    shot("servers-$suffix", 3000) {
                        state.section = Section.Servers
                        state.serversTab = ServersTab.Hosted
                        state.serverView = ServerView.List
                    }
                    shot("server-detail-$suffix", 1500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Detail("smoke-server")
                        state.serverTab = ServerTab.Console
                    }
                    // A server that seems to be up, with a command half-typed: the terminal's suggestions.
                    val session = smokeModel?.servers?.session("smoke-server")
                    shot("server-terminal-$suffix", 1500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Detail("smoke-server")
                        state.serverTab = ServerTab.Console
                        session?.status = ServerStatus.Running(System.currentTimeMillis() - 754_000)
                        session?.lines?.apply { clear(); addAll(SMOKE_LOG) }
                        session?.players?.apply { clear(); addAll(listOf("Steve", "Alex")) }
                        session?.draft = "gam"
                    }
                    session?.status = ServerStatus.Stopped
                    session?.lines?.clear()
                    session?.players?.clear()
                    session?.draft = ""
                    shot("server-settings-$suffix", 1500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Detail("smoke-server")
                        state.serverTab = ServerTab.Settings
                    }
                    shot("server-bans-$suffix", 1500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Detail("smoke-server")
                        state.serverTab = ServerTab.Bans
                    }
                    shot("server-create-$suffix", 1200) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Create
                    }
                    shot("quick-join-$suffix", 6000) {
                        state.section = Section.Servers
                        state.serverView = ServerView.List
                        state.serversTab = ServersTab.QuickJoin
                    }
                    shot("quick-join-pack-$suffix", 5000) {
                        state.section = Section.Servers
                        state.serversTab = ServersTab.QuickJoin
                        state.serverPack = ServerPackTarget.Saved("friend")
                    }
                    // "Создать сборку", once: the pack downloads, the dialog closes, Dima's server gets it.
                    if (theme == ThemeMode.Light) shot("quick-join-packed-$suffix", 12000) { click(800, 484) }
                    state.serverPack = null
                    shot("ely-signin-$suffix", 1500) {
                        state.section = Section.Home
                        settings.account = AccountKind.ElyBy
                        state.elySignIn = true
                    }
                    state.elySignIn = false
                    state.serversTab = ServersTab.Hosted
                    state.serverView = ServerView.List
                }
                // The backdrop at each quality level from one viewpoint — the orbit stands still while
                // the pointer alone moves the camera — then half-way through a flight to the smithy.
                settings.account = AccountKind.Offline
                settings.sceneMotion = SceneMotion.Mouse
                for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                    settings.theme = theme
                    for (quality in SceneQuality.entries) {
                        settings.sceneQuality = quality
                        shot("scene-${quality.name.lowercase()}-${theme.name.lowercase()}", 1500) { state.section = Section.Home }
                    }
                }
                settings.sceneQuality = SceneQuality.Ultra
                settings.sceneMotion = SceneMotion.Both
                shot("scene-flight", 950) { state.section = Section.Mods }
                // Each pastel palette, on the screens that show the most of it.
                for (palette in listOf(Palette.Green, Palette.Pink, Palette.Blue)) {
                    settings.palette = palette
                    for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                        val suffix = "${palette.name.lowercase()}-${theme.name.lowercase()}"
                        settings.theme = theme
                        shot("palette-home-$suffix", 1500) { state.section = Section.Home }
                        shot("palette-settings-$suffix", 1000) { state.section = Section.Settings }
                        shot("palette-server-settings-$suffix", 1500) {
                            state.section = Section.Servers
                            state.serverView = ServerView.Detail("smoke-server")
                            state.serverTab = ServerTab.Settings
                        }
                    }
                }
                Preferences.userRoot().node("com/yumi/launcher-smoke").removeNode()
                exitApplication()
            }
        }
        val dark = when (settings.theme) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        LauncherTheme(dark, settings.palette) {
            LauncherApp(
                windowState, settings, dark, onClose = ::exitApplication, closeRequests = closeRequests,
                onState = { navigation, model -> smokeState = navigation; smokeModel = model },
            )
        }
    }
}

/** What the smoke run's terminal shows: a server that has just come up and two friends joining it. */
private val SMOKE_LOG = listOf(
    "[12:00:01] [Server thread/INFO]: Starting minecraft server version 1.21.1",
    "[12:00:02] [Server thread/INFO]: Loading properties",
    "[12:00:02] [Server thread/INFO]: Default game type: SURVIVAL",
    "[12:00:04] [Server thread/INFO]: Preparing level \"world\"",
    "[12:00:09] [Server thread/INFO]: Done (7.412s)! For help, type \"help\"",
    "[12:03:40] [Server thread/INFO]: Steve joined the game",
    "[12:05:12] [Server thread/INFO]: Alex joined the game",
    "> list",
    "[12:05:30] [Server thread/INFO]: There are 2 of a max of 10 players online: Steve, Alex",
)

/** Same loader as YumiDesktop: the classpath SVG API is deprecated but one file does not justify the resources plugin. */
@Suppress("DEPRECATION")
private fun launcherIcon(): Painter = useResource("icon/yumi.svg") { loadSvgPainter(it, Density(1f)) }

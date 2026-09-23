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
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.settings.ThemeMode
import com.yumi.launcher.ui.home.LauncherApp
import com.yumi.launcher.ui.home.LauncherState
import com.yumi.launcher.ui.home.PackRequest
import com.yumi.launcher.ui.home.Section
import com.yumi.launcher.ui.home.ServerView
import com.yumi.launcher.ui.home.ServersTab
import com.yumi.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import javax.swing.UIManager
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "--launch-test" -> exitProcess(launchTest(args.drop(1)))
        "--server-test" -> exitProcess(serverTest(args.drop(1)))
        "--ping-test" -> exitProcess(pingTest(args.drop(1)))
        "--pack-test" -> exitProcess(packTest(args.drop(1)))
    }
    // Native-looking Swing dialogs: the folder chooser is Swing's, and its default look is from 2006.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    launcher(args)
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
                    .writeText("""{"id":"smoke-fabric","name":"Fabric 1.21.1","version":"1.21.1","loader":"Fabric"}""")
                it.selectedInstance = "smoke-fabric"
                // A server made from it, and two servers to join: one that answers and one that does not.
                File(it.gameDir, "servers/smoke-server").apply { mkdirs() }.apply {
                    resolve("yumi-server.json").writeText(
                        """{"id":"smoke-server","name":"Сервер для друзей","packId":"smoke-fabric","version":"1.21.1","loader":"Fabric","memoryMb":4096}""",
                    )
                    resolve("server.properties").writeText("server-port=25565\nmotd=Yumi\n")
                }
                LauncherPaths.config.resolve("servers.json").apply { parentFile.mkdirs() }.writeText(
                    """[{"id":"hypixel","name":"Hypixel","address":"mc.hypixel.net"},{"id":"friend","name":"Сервер Димы","address":"127.0.0.1:25599"}]""",
                )
            }
        }
    }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp, position = WindowPosition(Alignment.Center))
    val icon = remember(::launcherIcon)
    var smokeState by remember { mutableStateOf<LauncherState?>(null) }
    // Alt+F4 and the taskbar's "close" go through the launcher, which may have servers to stop first.
    var closeRequests by remember { mutableIntStateOf(0) }
    Window(onCloseRequest = { closeRequests++ }, state = windowState, title = "Yumi", icon = icon, undecorated = true) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1100, 720)
            if (smoke) {
                window.isAlwaysOnTop = true
                window.toFront()
                val output = File(".local/screenshots").also(File::mkdirs)
                fun shoot(name: String) {
                    val area = Rectangle(window.contentPane.locationOnScreen, window.contentPane.size)
                    ImageIO.write(Robot().createScreenCapture(area), "png", File(output, "$name.png"))
                }
                val state = smokeState!!
                for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                    val suffix = theme.name.lowercase()
                    settings.theme = theme
                    settings.account = AccountKind.Offline
                    state.section = Section.Home
                    delay(4000)
                    shoot("home-$suffix")
                    state.section = Section.Packs
                    delay(1200)
                    shoot("packs-$suffix")
                    state.packRequest = PackRequest.Import
                    delay(1400)
                    shoot("pack-import-$suffix")
                    state.section = Section.Mods
                    delay(5000)
                    shoot("mods-$suffix")
                    state.section = Section.Settings
                    delay(1200)
                    shoot("settings-$suffix")
                    state.section = Section.Servers
                    state.serversTab = ServersTab.Hosted
                    state.serverView = ServerView.List
                    delay(1200)
                    shoot("servers-$suffix")
                    state.serverView = ServerView.Detail("smoke-server")
                    delay(1500)
                    shoot("server-detail-$suffix")
                    state.serverView = ServerView.Create
                    delay(1200)
                    shoot("server-create-$suffix")
                    state.serverView = ServerView.List
                    state.serversTab = ServersTab.QuickJoin
                    delay(6000)
                    shoot("quick-join-$suffix")
                    state.section = Section.Home
                    settings.account = AccountKind.ElyBy
                    state.elySignIn = true
                    delay(1500)
                    shoot("ely-signin-$suffix")
                    state.elySignIn = false
                }
                // Each pastel palette, on the screens that show the most of it.
                settings.account = AccountKind.Offline
                for (palette in listOf(Palette.Green, Palette.Pink, Palette.Blue)) {
                    settings.palette = palette
                    for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                        val suffix = "${palette.name.lowercase()}-${theme.name.lowercase()}"
                        settings.theme = theme
                        state.section = Section.Home
                        delay(1500)
                        shoot("palette-home-$suffix")
                        state.section = Section.Settings
                        delay(1000)
                        shoot("palette-settings-$suffix")
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
            LauncherApp(windowState, settings, dark, onClose = ::exitApplication, closeRequests = closeRequests, onState = { smokeState = it })
        }
    }
}

/** Same loader as YumiDesktop: the classpath SVG API is deprecated but one file does not justify the resources plugin. */
@Suppress("DEPRECATION")
private fun launcherIcon(): Painter = useResource("icon/yumi.svg") { loadSvgPainter(it, Density(1f)) }

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yumi.launcher.core.ContentType
import com.yumi.launcher.core.LauncherLog
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.PackArchives
import com.yumi.launcher.core.SkippedFile
import com.yumi.launcher.core.ServerMods
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PackCheck
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.scene.sceneShot
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.settings.SavedAccount
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
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
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
        "--world-test" -> exitProcess(worldTest(args.drop(1)))
        "--friends-test" -> exitProcess(friendsTest(args.drop(1)))
        "--content-test" -> exitProcess(contentTest(args.drop(1)))
        "--scene-shot" -> exitProcess(sceneShot(args.drop(1)))
    }
    // The launcher's own log, for "Отправить лог"; a smoke run leaves the real one alone.
    if ("--ui-smoke" !in args) LauncherLog.install()
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
        println("Использование: --pack-test <архив.mrpack|архив.zip> <тестовая папка> [ключ CurseForge — по умолчанию из настроек] [--plan — только спросить CurseForge, что и откуда скачается]")
        return@runBlocking 2
    }
    try {
        val archive = File(args[0])
        val folder = File(args[1])
        val info = PackArchives.inspect(archive)
        // The key from the launcher's settings unless one is given, so it need not show in the command line.
        val key = args.getOrNull(2)?.takeIf { !it.startsWith("--") } ?: LauncherSettings().curseForgeKey.trim()
        if ("--plan" in args) {
            val plan = PackArchives.planCurseForge(archive, folder, key)
            val byFolder = plan.downloads.groupingBy { it.target.parentFile.name }.eachCount()
            println("${info.name}: скачается ${plan.downloads.size} ($byFolder), из ручных загрузок ${plan.copies.size}, пропущено ${plan.skipped.size}")
            plan.skipped.forEach { println("  пропущен: ${it.kind} «${it.name}» → ${it.folder}. ${it.page}") }
            return@runBlocking 0
        }
        val skipped = PackArchives.install(archive, folder, key) {}
        skipped.forEach { println("Пропущен: ${it.kind} «${it.name}» → ${it.folder}. ${it.page}") }
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
                // Saved worlds for the creation form — two of them under one name, as the game allows;
                // smoke never launches Minecraft with them.
                val saves = File(it.gameDir, "instances/smoke-fabric/minecraft/saves")
                val now = System.currentTimeMillis()
                for ((folder, level) in listOf(
                    "Выживание с друзьями" to smokeLevel("Выживание с друзьями", 0, now - 2 * 3_600_000L),
                    "Выживание с друзьями (1)" to smokeLevel("Выживание с друзьями", 0, now - 3 * 86_400_000L, cheats = true),
                    "Постройки" to smokeLevel("Город у моря", 1, now - 10 * 86_400_000L),
                ) + (1..40).map { n -> "Испытание $n" to smokeLevel("Испытание $n", n % 4, now - (20L + n) * 86_400_000L) }
                ) saves.resolve(folder).apply { mkdirs() }.resolve("level.dat").writeBytes(level)
                // The guide is photographed on purpose below, not over every other shot.
                it.guideDone = true
                // A nick in the slot and two accounts waiting in the switcher.
                it.nick = "YumiPlayer"
                it.savedAccounts = listOf(
                    SavedAccount(AccountKind.Offline, "Builder_Alex"),
                    SavedAccount(AccountKind.ElyBy, "Dima", uuid = "00000000000000000000000000000002"),
                )
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
                    resolve("server.properties").writeText("server-port=25565\nmotd=§bYumi §7— §aвыживание с друзьями\nmax-players=10\nwhite-list=true\nenforce-whitelist=true\n")
                    resolve("whitelist.json").writeText(
                        """[{"uuid":"4a2f0bb1-4c7e-3a05-8a7f-2a4d1b0e7c11","name":"YumiPlayer"},{"uuid":"5b3e1cc2-5d8f-3b16-9b80-3b5e2c1f8d22","name":"Builder_Alex"}]""",
                    )
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
    // F11: full screen and back to however the window was before — maximised or not.
    var beforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }
    val fullscreenKey: (KeyEvent) -> Boolean = { event ->
        if (event.key == Key.F11 && event.type == KeyEventType.KeyDown) {
            if (windowState.placement == WindowPlacement.Fullscreen) {
                windowState.placement = beforeFullscreen
            } else {
                beforeFullscreen = windowState.placement
                windowState.placement = WindowPlacement.Fullscreen
            }
            true
        } else {
            false
        }
    }
    Window(
        onCloseRequest = { closeRequests++ },
        state = windowState,
        title = "Yumi",
        icon = icon,
        undecorated = true,
        onPreviewKeyEvent = fullscreenKey,
    ) {
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
                // Synthetic input goes through the event queue, not straight to the component: this code
                // runs inside Compose's own dispatcher, and an event handled on the spot makes Compose run
                // its queued work — this very coroutine among it — re-entrantly, which kills the run.
                val events = Toolkit.getDefaultToolkit().systemEventQueue
                // Presses the left button over the window at x, y without moving the real pointer.
                fun click(x: Int, y: Int) {
                    val target = SwingUtilities.getDeepestComponentAt(window.contentPane, x, y) ?: return
                    val at = SwingUtilities.convertPoint(window.contentPane, x, y, target)
                    val time = System.currentTimeMillis()
                    for (id in listOf(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
                        val mask = if (id == MouseEvent.MOUSE_MOVED) 0 else MouseEvent.BUTTON1_DOWN_MASK
                        events.postEvent(MouseEvent(target, id, time, if (id == MouseEvent.MOUSE_RELEASED) 0 else mask, at.x, at.y, 1, false, MouseEvent.BUTTON1))
                    }
                }
                // Turns the wheel over the window at x, y without moving the real pointer.
                fun wheel(x: Int, y: Int, clicks: Int) {
                    val target = SwingUtilities.getDeepestComponentAt(window.contentPane, x, y) ?: return
                    val at = SwingUtilities.convertPoint(window.contentPane, x, y, target)
                    events.postEvent(
                        MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, at.x, at.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, clicks),
                    )
                }
                for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                    val suffix = theme.name.lowercase()
                    settings.theme = theme
                    settings.account = AccountKind.Offline
                    shot("home-$suffix", 4000) { state.section = Section.Home }
                    // The account switcher under the account on the home screen.
                    shot("accounts-$suffix", 1200) { click(230, 320) }
                    click(1000, 600)
                    delay(400)
                    // The downloads panel under its button, with what finished and what failed.
                    shot("downloads-$suffix", 1200) {
                        smokeModel?.downloads?.apply {
                            clear()
                            record("Iris Shaders", "мод", null)
                            record("Complementary Shaders - Reimagined", "шейдер", null)
                            record("Fresh Animations", "набор ресурсов", "Нет связи с Modrinth")
                        }
                        click(945, 44)
                    }
                    click(1000, 600)
                    delay(400)
                    // The newcomer's guide: its first card, a step on the home screen, one that opens a section, the last.
                    for (step in listOf(0, 1, 3, 5, 9, 11)) shot("guide-$step-$suffix", 1800) { state.guide.step = step }
                    state.guide.step = null
                    state.section = Section.Home
                    shot("news-hidden-$suffix", 1200) { settings.newsHidden = true }
                    // Both corners folded away: only the island and the two pills to bring them back.
                    shot("folded-$suffix", 1200) { settings.friendsHidden = true }
                    settings.newsHidden = false
                    settings.friendsHidden = false
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
                    // A CurseForge pack that came in without two resource packs its authors keep off launchers.
                    shot("pack-skipped-$suffix", 1500) {
                        state.section = Section.Packs
                        smokeModel?.packImports?.apply {
                            finished = smokeModel?.selected
                            skipped = listOf(
                                SkippedFile("Текстурпак", "Deceasedcraft Screen Tweak 1.20.1.zip", "resourcepacks", "https://www.curseforge.com/minecraft/texture-packs/deceasedcraft-screen-tweak/files/7194089"),
                                SkippedFile("Текстурпак", "Itsme64's no potion particles [1.5.1].zip", "resourcepacks", "https://www.curseforge.com/minecraft/texture-packs/no-potion-particles/files/4607236"),
                            )
                        }
                    }
                    smokeModel?.packImports?.apply { finished = null; skipped = emptyList() }
                    shot("pack-editor-$suffix", 1400) { state.section = Section.Packs; state.packRequest = PackRequest.Edit("smoke-fabric") }
                    // Further down: how the pack starts, and last its game's menus.
                    shot("pack-editor-launch-$suffix", 1200) { wheel(270, 500, 9) }
                    shot("pack-editor-menu-$suffix", 1200) { wheel(270, 500, 40) }
                    shot("mods-$suffix", 5000) { state.section = Section.Mods }
                    // Shaders — with the card offering Iris, the smoke pack has none — and resource packs.
                    smokeModel?.let { m ->
                        shot("mods-shaders-$suffix", 5000) { state.section = Section.Mods; m.mods.show(m.selected, ContentType.Shader) }
                        shot("mods-resourcepacks-$suffix", 5000) { state.section = Section.Mods; m.mods.show(m.selected, ContentType.ResourcePack) }
                        m.mods.show(m.selected, ContentType.Mod)
                    }
                    shot("settings-$suffix", 3000) { state.section = Section.Settings }
                    shot("feedback-$suffix", 1200) { smokeModel?.feedback?.open = true }
                    smokeModel?.feedback?.open = false
                    // The game group — memory, Java, shared game settings — is at the bottom.
                    // The island's group, down to "Во время игры".
                    shot("settings-scene-$suffix", 1200) { wheel(270, 500, 6) }
                    shot("settings-game-$suffix", 1200) { wheel(270, 500, 80) }
                    // A pasted upload token where the API key goes: CurseForge refuses it, and the field says why.
                    shot("settings-key-$suffix", 4000) {
                        settings.curseForgeKey = "00000000-0000-0000-0000-000000000000"
                        wheel(270, 500, -4)
                    }
                    settings.curseForgeKey = ""
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
                    shot("server-whitelist-$suffix", 1500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Detail("smoke-server")
                        state.serverTab = ServerTab.Whitelist
                    }
                    shot("server-bans-$suffix", 1500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Detail("smoke-server")
                        state.serverTab = ServerTab.Bans
                    }
                    shot("server-create-$suffix", 2500) {
                        state.section = Section.Servers
                        state.serverView = ServerView.Create
                    }
                    // The second of the two same-named worlds, picked by its card.
                    shot("server-create-selected-$suffix", 1000) { click(300, 640) }
                    // A pack with dozens of worlds: all of them, in the window with the search.
                    shot("server-create-all-worlds-$suffix", 1200) { click(530, 453) }
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
                    shot("ms-home-$suffix", 1200) { settings.account = AccountKind.Microsoft }
                    // Asks Microsoft for a real code: the dialog as the player sees it while it waits.
                    shot("ms-signin-$suffix", 4000) { state.microsoftSignIn = true }
                    state.microsoftSignIn = false
                    smokeModel?.microsoft?.cancel()
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
                // With the camera on the pointer the wheel turns the island: before and after a few notches.
                settings.theme = ThemeMode.Light
                shot("wheel-before", 1500) { state.section = Section.Home }
                shot("wheel-after", 1500) { repeat(6) { wheel(900, 420, 3) } }
                settings.sceneMotion = SceneMotion.Both
                // F11, as the player presses it: the window takes the whole screen, and gives it back.
                if (only == null || only.any { it in "fullscreen" }) {
                    val robot = Robot()
                    shot("fullscreen", 1500) { robot.keyPress(java.awt.event.KeyEvent.VK_F11); robot.keyRelease(java.awt.event.KeyEvent.VK_F11) }
                    robot.keyPress(java.awt.event.KeyEvent.VK_F11)
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F11)
                    delay(1000)
                    println("После второго F11: ${windowState.placement}")
                }
                shot("scene-flight", 950) { state.section = Section.Mods }
                // Each pastel palette, on the screens that show the most of it.
                for (palette in Palette.entries.filter { it != Palette.Original }) {
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

/** A `level.dat` as the game writes it, with what the world list reads from it — for the smoke run's saved worlds. */
private fun smokeLevel(name: String, mode: Int, lastPlayed: Long, cheats: Boolean = false): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(GZIPOutputStream(bytes)).use { out ->
        out.writeByte(10); out.writeUTF("")
        out.writeByte(10); out.writeUTF("Data")
        out.writeByte(8); out.writeUTF("LevelName"); out.writeUTF(name)
        out.writeByte(4); out.writeUTF("LastPlayed"); out.writeLong(lastPlayed)
        out.writeByte(3); out.writeUTF("GameType"); out.writeInt(mode)
        out.writeByte(1); out.writeUTF("hardcore"); out.writeByte(0)
        out.writeByte(1); out.writeUTF("allowCommands"); out.writeByte(if (cheats) 1 else 0)
        out.writeByte(10); out.writeUTF("Version")
        out.writeByte(8); out.writeUTF("Name"); out.writeUTF("1.21.1")
        out.writeByte(0)
        out.writeByte(0)
        out.writeByte(0)
    }
    return bytes.toByteArray()
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

package com.yumi.launcher

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.yumi.launcher.core.AppRelease
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.SkippedFile
import com.yumi.launcher.core.VerifiedUpdate
import com.yumi.launcher.data.AppUpdateState
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.scene.sharedIslandScene
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.settings.SavedAccount
import com.yumi.launcher.settings.ThemeMode
import com.yumi.launcher.ui.home.LauncherContent
import com.yumi.launcher.ui.home.LauncherState
import com.yumi.launcher.ui.home.ModsTab
import com.yumi.launcher.ui.home.PackRequest
import com.yumi.launcher.ui.home.Section
import com.yumi.launcher.ui.home.ServerTab
import com.yumi.launcher.ui.home.ServerView
import com.yumi.launcher.ui.home.ServersTab
import com.yumi.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.prefs.Preferences

/**
 * `--headless-smoke <folder> [--only=sub,…]`: renders every UI screen, theme, glass popover,
 * and palette off-screen with `ImageComposeScene` — zero visible windows or desktop focus stealing.
 */
@Suppress("DEPRECATION")
internal fun headlessSmoke(args: List<String>): Int {
    System.setProperty("java.awt.headless", "true")
    val outDir = File(args.firstOrNull { !it.startsWith("--") } ?: ".local/headless-shots").apply { mkdirs() }
    val only = args.firstOrNull { it.startsWith("--only=") }?.removePrefix("--only=")?.split(',')

    val prefNodeName = "com/yumi/launcher-headless-smoke"
    val prefs = Preferences.userRoot().node(prefNodeName)
    val dataRoot = File(".local/headless-smoke-data").absoluteFile.apply { mkdirs() }
    val configRoot = File(".local/headless-smoke-config").absoluteFile.apply { mkdirs() }

    runCatching { smokeFriendServer(25599) }
    try {
        seedHeadlessSmokeFiles(dataRoot, configRoot)

        val settings = LauncherSettings(prefs).apply {
            gameDir = dataRoot.absolutePath
            LauncherPaths.config = configRoot
            selectedInstance = "smoke-fabric"
            guideDone = true
            nick = "LumiPlayer"
            account = AccountKind.Offline
            theme = ThemeMode.Dark
            palette = Palette.Original
            savedAccounts = listOf(
                SavedAccount(AccountKind.Offline, "Builder_Alex"),
                SavedAccount(AccountKind.ElyBy, "Dima", uuid = "00000000000000000000000000000002"),
            )
        }

        // Pre-warm the shared 3D island mesh and atlas before pumping UI frames.
        sharedIslandScene

        var nav: LauncherState? = null
        var model: LauncherModel? = null
        val scene = ImageComposeScene(width = 1280, height = 800, density = Density(1f)) {
            val dark = settings.theme == ThemeMode.Dark
            LauncherTheme(dark, settings.palette) {
                LauncherContent(
                    settings = settings,
                    dark = dark,
                    shaders = false,
                    startGuide = false,
                    autoCheckUpdates = false,
                    onState = { state, appModel ->
                        nav = state
                        model = appModel
                    },
                )
            }
        }
        try {
            runBlocking {
                var nanoTime = 0L
                fun step(delayMs: Long = 0L) {
                    if (delayMs > 0) Thread.sleep(delayMs)
                    Snapshot.sendApplyNotifications()
                    scene.render(nanoTime).close()
                    nanoTime += 80_000_000L
                }

                // Wait for VoxelBackground's background produceState to receive the renderer and record initial frost.
                repeat(16) {
                    delay(35)
                    step()
                }

                val state = checkNotNull(nav) { "LauncherState did not initialise in headless compose scene" }
                val appModel = checkNotNull(model) { "LauncherModel did not initialise in headless compose scene" }
                val serverSession = appModel.servers.session("smoke-server")

                fun seedRichEvents() {
                    appModel.downloads.apply {
                        clear()
                        record("Iris Shaders", "мод", null)
                        record("Complementary Shaders", "шейдер", null)
                        record("Fresh Animations", "ресурспак", "Нет связи с Modrinth")
                    }
                    appModel.packImports.skipped = listOf(
                        SkippedFile(
                            kind = "Ресурспак",
                            name = "LowShieldPack.zip",
                            folder = "resourcepacks",
                            page = "https://www.curseforge.com/minecraft/texture-packs",
                        ),
                    )
                    serverSession.status = ServerStatus.Running(System.currentTimeMillis() - 620_000L)
                    serverSession.lines.apply { clear(); addAll(SMOKE_LOG) }
                    serverSession.players.apply { clear(); addAll(listOf("Steve", "Alex")) }
                }

                suspend fun shot(name: String, frames: Int = 22, settleDelayMs: Long = 20L, setup: () -> Unit) {
                    if (only != null && only.none { it in name }) return
                    setup()
                    Snapshot.sendApplyNotifications()
                    repeat(frames - 1) {
                        delay(settleDelayMs)
                        Snapshot.sendApplyNotifications()
                        scene.render(nanoTime).close()
                        nanoTime += 80_000_000L
                    }
                    delay(settleDelayMs)
                    Snapshot.sendApplyNotifications()
                    val image = scene.render(nanoTime)
                    nanoTime += 80_000_000L
                    try {
                        val png = image.encodeToData(EncodedImageFormat.PNG)
                            ?: error("Failed to encode PNG for $name")
                        outDir.resolve("$name.png").writeBytes(png.bytes)
                        println("headless-smoke: $name.png")
                    } finally {
                        image.close()
                    }
                }

                seedRichEvents()

                shot("home-dark", frames = 26) {
                    settings.theme = ThemeMode.Dark
                    settings.palette = Palette.Original
                    state.notificationsOpen = false
                    state.downloadsOpen = false
                    state.section = Section.Home
                }
                shot("home-light", frames = 26) {
                    settings.theme = ThemeMode.Light
                    settings.palette = Palette.Original
                    state.section = Section.Home
                }
                val smokeInstaller = File(configRoot, "cache/updates/Lumi-Launcher-1.4-windows-x64-setup.exe").apply {
                    parentFile.mkdirs()
                    writeText("smoke-update-payload")
                }
                val smokeRelease = AppRelease(
                    version = "1.4",
                    tag = "1.4",
                    notes = "Улучшенная библиотека, автоапдейтер с проверкой SHA-256 и свежие штрихи интерфейса.",
                    installerName = smokeInstaller.name,
                    installerUrl = "https://github.com/GXUser7/Lumi-launcher/releases/download/1.4/${smokeInstaller.name}",
                    checksumUrl = null,
                    inlineSha256 = "0".repeat(64),
                    size = smokeInstaller.length(),
                    pageUrl = "https://github.com/GXUser7/Lumi-launcher/releases/tag/1.4",
                )
                shot("home-update-dark", frames = 22) {
                    settings.theme = ThemeMode.Dark
                    settings.palette = Palette.Original
                    state.notificationsOpen = false
                    state.downloadsOpen = false
                    appModel.updater.state = AppUpdateState.Ready(VerifiedUpdate(smokeRelease, smokeInstaller, "0".repeat(64)))
                    state.section = Section.Home
                }
                appModel.updater.state = AppUpdateState.UpToDate(System.currentTimeMillis())
                shot("notifications-dark", frames = 18) {
                    settings.theme = ThemeMode.Dark
                    state.section = Section.Home
                    state.downloadsOpen = false
                    state.notificationsOpen = true
                }
                state.notificationsOpen = false
                shot("downloads-dark", frames = 18) {
                    settings.theme = ThemeMode.Dark
                    state.section = Section.Home
                    state.notificationsOpen = false
                    state.downloadsOpen = true
                }
                state.downloadsOpen = false

                shot("packs-dark", frames = 24) {
                    settings.theme = ThemeMode.Dark
                    state.packRequest = null
                    state.section = Section.Packs
                }
                shot("packs-light", frames = 22) {
                    settings.theme = ThemeMode.Light
                    state.packRequest = null
                    state.section = Section.Packs
                }
                shot("pack-editor-dark", frames = 20) {
                    settings.theme = ThemeMode.Dark
                    state.section = Section.Packs
                    state.packRequest = PackRequest.Edit("smoke-fabric")
                }
                state.packRequest = null

                shot("mods-dark", frames = 26, settleDelayMs = 45L) {
                    settings.theme = ThemeMode.Dark
                    state.modsTab = ModsTab.Catalog
                    state.section = Section.Mods
                }
                shot("mods-installed-dark", frames = 18) {
                    settings.theme = ThemeMode.Dark
                    state.section = Section.Mods
                    state.modsTab = ModsTab.Installed
                }
                state.modsTab = ModsTab.Catalog

                shot("servers-dark", frames = 24) {
                    settings.theme = ThemeMode.Dark
                    state.serversTab = ServersTab.Hosted
                    state.serverView = ServerView.List
                    state.section = Section.Servers
                }
                shot("server-detail-dark", frames = 20) {
                    settings.theme = ThemeMode.Dark
                    state.serversTab = ServersTab.Hosted
                    state.serverView = ServerView.Detail("smoke-server")
                    state.serverTab = ServerTab.Console
                    serverSession.draft = "gam"
                    state.section = Section.Servers
                }
                shot("quick-join-dark", frames = 24, settleDelayMs = 45L) {
                    settings.theme = ThemeMode.Dark
                    state.serverView = ServerView.List
                    state.serversTab = ServersTab.QuickJoin
                    state.section = Section.Servers
                }

                shot("settings-dark", frames = 24) {
                    settings.theme = ThemeMode.Dark
                    settings.palette = Palette.Original
                    state.section = Section.Settings
                }
                shot("settings-light", frames = 24) {
                    settings.theme = ThemeMode.Light
                    settings.palette = Palette.Original
                    state.section = Section.Settings
                }
                shot("settings-updates-dark", frames = 24) {
                    settings.theme = ThemeMode.Dark
                    settings.palette = Palette.Original
                    appModel.updater.state = AppUpdateState.Ready(VerifiedUpdate(smokeRelease, smokeInstaller, "0".repeat(64)))
                    state.section = Section.Settings
                }
                if (state.settingsScroll.maxValue > 0 && (only == null || only.any { it in "settings-updates-dark" })) {
                    state.settingsScroll.scrollTo(state.settingsScroll.maxValue)
                    shot("settings-updates-dark", frames = 8) {
                        settings.theme = ThemeMode.Dark
                        state.section = Section.Settings
                    }
                    state.settingsScroll.scrollTo(0)
                }
                smokeInstaller.delete()
                appModel.updater.state = AppUpdateState.Idle
                shot("palette-pink-light", frames = 24) {
                    settings.theme = ThemeMode.Light
                    settings.palette = Palette.Pink
                    state.section = Section.Home
                }
                shot("palette-amber-dark", frames = 24) {
                    settings.theme = ThemeMode.Dark
                    settings.palette = Palette.Amber
                    state.section = Section.Home
                }
                shot("palette-green-dark", frames = 24) {
                    settings.theme = ThemeMode.Dark
                    settings.palette = Palette.Green
                    state.section = Section.Home
                }
            }
        } finally {
            scene.close()
        }
        return 0
    } finally {
        runCatching { prefs.removeNode() }
    }
}

private fun seedHeadlessSmokeFiles(dataRoot: File, configRoot: File) {
    val fabricDir = File(dataRoot, "instances/smoke-fabric").apply { mkdirs() }
    fabricDir.resolve("instance.json").writeText(
        """{"id":"smoke-fabric","name":"Fabric 1.21.1","version":"1.21.1","loader":"Fabric","menuStyle":{"palette":"Pink","dark":false,"font":true}}""",
    )
    val modsDir = fabricDir.resolve("minecraft/mods").apply { mkdirs() }
    for (modName in listOf(
        "sodium-fabric-0.6.5+mc1.21.1.jar",
        "iris-fabric-1.8.1+mc1.21.1.jar",
        "lithium-fabric-0.14.3+mc1.21.1.jar",
        "fabric-api-0.114.0+1.21.1.jar",
        "modmenu-11.0.3.jar",
        "emotecraft-fabric-2.4.9.jar",
        "voicechat-fabric-1.21.1-2.5.26.jar",
    )) {
        modsDir.resolve(modName).writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06))
    }

    val vanillaDir = File(dataRoot, "instances/vanilla-121").apply { mkdirs() }
    vanillaDir.resolve("instance.json").writeText(
        """{"id":"vanilla-121","name":"Ванилла 1.21.4","version":"1.21.4","loader":"Vanilla"}""",
    )

    val saves = fabricDir.resolve("minecraft/saves")
    val now = System.currentTimeMillis()
    for ((folder, level) in listOf(
        "Выживание с друзьями" to smokeLevel("Выживание с друзьями", 0, now - 2 * 3_600_000L),
        "Постройки" to smokeLevel("Город у моря", 1, now - 10 * 86_400_000L),
    )) {
        saves.resolve(folder).apply { mkdirs() }.resolve("level.dat").writeBytes(level)
    }

    File(dataRoot, "servers/smoke-server").apply { mkdirs() }.apply {
        resolve("yumi-server.json").writeText(
            """{"id":"smoke-server","name":"Сервер для друзей","packId":"smoke-fabric","version":"1.21.1","loader":"Fabric","memoryMb":4096}""",
        )
        resolve("server.properties").writeText(
            "server-port=25565\nmotd=§bLumi §7— §aвыживание с друзьями\nmax-players=10\nwhite-list=true\nenforce-whitelist=true\n",
        )
        resolve("whitelist.json").writeText(
            """[{"uuid":"4a2f0bb1-4c7e-3a05-8a7f-2a4d1b0e7c11","name":"LumiPlayer"},{"uuid":"5b3e1cc2-5d8f-3b16-9b80-3b5e2c1f8d22","name":"Builder_Alex"}]""",
        )
        resolve("banned-players.json").writeText(
            """[{"uuid":"00000000-0000-0000-0000-000000000001","name":"Griefer228","created":"2026-09-20 18:04:11 +0500","source":"Server","expires":"forever","reason":"Сломал спавн"}]""",
        )
    }

    configRoot.resolve("servers.json").apply { parentFile.mkdirs() }.writeText(
        """[{"id":"friend","name":"Сервер Димы","address":"127.0.0.1:25599"},{"id":"gone","name":"Старый сервер","address":"127.0.0.1:25598"}]""",
    )
}

package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PlayStatus
import com.yumi.launcher.data.active
import com.yumi.launcher.scene.Parallax
import com.yumi.launcher.scene.SceneOptions
import com.yumi.launcher.scene.VoxelBackground
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.AfterLaunch
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.settings.SceneMotion
import com.yumi.launcher.ui.components.Avatar
import com.yumi.launcher.ui.components.GlassDropdownMenu
import com.yumi.launcher.ui.components.LumiMark
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.components.ResizeEdges
import com.yumi.launcher.ui.components.SkinHead
import com.yumi.launcher.ui.components.Tip
import com.yumi.launcher.ui.components.WindowControls
import com.yumi.launcher.ui.components.connectedShapes
import com.yumi.launcher.ui.console.ConsoleScreen
import com.yumi.launcher.ui.mods.ModsScreen
import com.yumi.launcher.ui.packs.PacksScreen
import com.yumi.launcher.ui.servers.ServerPackDialog
import com.yumi.launcher.ui.servers.ServersScreen
import com.yumi.launcher.ui.settings.SettingsScreen
import com.yumi.launcher.ui.theme.BackdropGlassState
import com.yumi.launcher.ui.theme.CardGlassAlpha
import com.yumi.launcher.ui.theme.GlassAlpha
import com.yumi.launcher.ui.theme.LocalBackdropGlass
import com.yumi.launcher.ui.theme.LocalInGlass
import com.yumi.launcher.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skiko.GraphicsApi

/**
 * Windowed entry point: wires AWT/Swing window dragging, caption controls, edge resizing, and
 * hardware-shader capability into [LauncherContent].
 */
@Composable
fun FrameWindowScope.LauncherApp(
    windowState: WindowState,
    settings: LauncherSettings,
    dark: Boolean,
    onClose: () -> Unit,
    closeRequests: Int = 0,
    autoCheckUpdates: Boolean = true,
    /** Hands the navigation state and the model out once — for the smoke run, which drives them screen by screen. */
    onState: (LauncherState, LauncherModel) -> Unit = { _, _ -> },
) {
    LauncherContent(
        settings = settings,
        dark = dark,
        minimized = windowState.isMinimized,
        shaders = window.renderApi != GraphicsApi.SOFTWARE_FAST && window.renderApi != GraphicsApi.SOFTWARE_COMPAT,
        onMinimizeWindow = { windowState.isMinimized = true },
        onRestoreWindow = {
            windowState.isMinimized = false
            window.toFront()
        },
        onClose = onClose,
        closeRequests = closeRequests,
        dragArea = { WindowDraggableArea(Modifier.fillMaxWidth().height(88.dp)) { Box(Modifier.fillMaxSize()) } },
        windowControls = { WindowControls(windowState, it) },
        resizeEdges = { ResizeEdges(windowState) },
        autoCheckUpdates = autoCheckUpdates,
        onState = onState,
    )
}

/**
 * Pure Compose root used by both the desktop [Window] and the headless screenshot harness.
 * Renders the 3D island, records its single blurred [BackdropGlassState], and floats all matte
 * glass UI panels above it.
 */
@Composable
fun LauncherContent(
    settings: LauncherSettings,
    dark: Boolean,
    minimized: Boolean = false,
    shaders: Boolean = true,
    onMinimizeWindow: () -> Unit = {},
    onRestoreWindow: () -> Unit = {},
    onClose: () -> Unit = {},
    closeRequests: Int = 0,
    dragArea: @Composable () -> Unit = {},
    windowControls: @Composable (onClose: () -> Unit) -> Unit = {},
    resizeEdges: @Composable () -> Unit = {},
    startGuide: Boolean = true,
    autoCheckUpdates: Boolean = true,
    onState: (LauncherState, LauncherModel) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val model = remember { LauncherModel(settings, scope, autoCheckUpdates = autoCheckUpdates) }
    val state = remember { LauncherState().also { onState(it, model) } }
    val parallax = remember { Parallax() }
    val backdrop = remember { BackdropGlassState() }

    // The first launch shows the newcomer around, once the island is up behind the guide.
    LaunchedEffect(startGuide) {
        if (startGuide && !settings.guideDone) {
            delay(1200)
            state.guide.start(state)
        }
    }

    // A running server has no window of its own: closing the launcher asks first, then stops it properly.
    var confirmClose by remember { mutableStateOf(false) }
    val finishClose = {
        model.updater.installOnExitIfReady()
        onClose()
    }
    val requestClose = { if (model.servers.anyActive) confirmClose = true else finishClose() }
    LaunchedEffect(closeRequests) { if (closeRequests > 0) requestClose() }

    // Only a minimise the launcher did itself is undone when the game closes.
    var minimizedForGame by remember { mutableStateOf(false) }
    val minimize = {
        onMinimizeWindow()
        minimizedForGame = true
    }
    val play: (Instance?, String?) -> Unit = { pack, join ->
        model.play(
            pack = pack,
            join = join,
            onStarted = { after ->
                when (after) {
                    AfterLaunch.Keep -> Unit
                    AfterLaunch.Minimize -> minimize()
                    // The servers this launcher runs would go down with it.
                    AfterLaunch.Close -> if (model.servers.anyActive) minimize() else onClose()
                }
            },
            onExit = {
                if (minimizedForGame) {
                    onRestoreWindow()
                    minimizedForGame = false
                }
            },
        )
    }

    Box(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.isCtrlPressed) {
                    when (event.key) {
                        Key.One -> { state.section = Section.Home; true }
                        Key.Two -> { state.section = Section.Mods; true }
                        Key.Three -> { state.section = Section.Packs; true }
                        Key.Four -> { state.section = Section.Servers; true }
                        Key.Comma -> { state.section = Section.Settings; true }
                        else -> false
                    }
                } else if (event.key == Key.Escape && state.guide.step == null) {
                    when {
                        confirmClose -> { confirmClose = false; true }
                        model.feedback.open -> { model.feedback.dismiss(); true }
                        state.serverPack != null -> { state.serverPack = null; true }
                        state.elySignIn -> { model.ely.reset(); state.elySignIn = false; true }
                        state.microsoftSignIn -> { model.microsoft.cancel(); state.microsoftSignIn = false; true }
                        model.askingForFolder -> { model.cancelFolderQuestion(); true }
                        state.downloadsOpen -> { state.downloadsOpen = false; true }
                        state.notificationsOpen -> { state.notificationsOpen = false; true }
                        state.section == Section.Console || state.section == Section.Settings -> { state.section = Section.Home; true }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                val p = event.changes.first().position
                parallax.x = p.x / size.width * 2 - 1
                parallax.y = p.y / size.height * 2 - 1
            }
            // With the camera on the pointer, the wheel turns the island — where no list has scrolled with it.
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                if (settings.sceneMotion == SceneMotion.Mouse && !change.isConsumed) parallax.spin += change.scrollDelta.y * 0.18f
            },
    ) {
        // Nothing here sits on a Surface, so the content colour has to be set once for the window —
        // left alone it stays Material's default black, which the dark theme cannot survive.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalBackdropGlass provides backdrop,
            LocalInGlass provides true,
        ) {
            // Unless the player wants it alive, the island holds still while the game runs: it would only
            // be competing for the GPU. Minimised, nothing of it is seen, so it always stops.
            // Each section has its place on the island, and the camera flies there when it opens.
            VoxelBackground(
                parallax,
                night = dark,
                options = SceneOptions(settings.sceneQuality, settings.sceneMotion, settings.sceneFps),
                spot = state.section.spot,
                modifier = Modifier.fillMaxSize(),
                running = !minimized && !(settings.scenePausedInGame && model.status is PlayStatus.Running),
                shaders = shaders,
                backdrop = backdrop,
            )
            // Underneath the bar, so the pills keep their clicks and the gaps between them drag the window.
            dragArea()
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                TopBar(model, state, requestClose, onClose, windowControls)
            }
            ScreenCard(model, state, play, onClose, Modifier.padding(start = 16.dp, top = 88.dp, bottom = 16.dp))
            // A catalogue's filters, in their own panel right of the card, while its list is on screen.
            val filters = state.filters?.takeIf { state.section == Section.Mods || state.section == Section.Packs }
            var shownFilters by remember { mutableStateOf(filters) }
            if (filters != null) shownFilters = filters
            AnimatedVisibility(
                filters != null,
                modifier = Modifier.padding(start = 16.dp + CATALOG_WIDTH + FILTERS_GAP, top = 88.dp, bottom = 16.dp),
                enter = fadeIn(tween(260, delayMillis = 120)) + slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 320f)) { -it / 5 },
                exit = fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { -it / 5 },
            ) {
                shownFilters?.let { FiltersPanel(model, state, it) }
            }
            // Only the home screen has news; elsewhere the corner belongs to the island, where the
            // camera has flown to that section's place.
            AnimatedVisibility(
                state.section == Section.Home,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                enter = fadeIn(tween(300, delayMillis = 250)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 3 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 3 },
            ) {
                NewsCarousel(
                    selected = state.news,
                    onSelect = { state.news = it },
                    hidden = settings.newsHidden,
                    onHiddenChange = { settings.newsHidden = it },
                    onAction = { index ->
                        when (index) {
                            0 -> state.section = Section.Mods
                            1 -> state.openPacks(PackRequest.Import)
                            2 -> state.section = Section.Servers
                        }
                    },
                )
            }
            // Friends in the corner above the news, never under them: the card stops where the news begins.
            AnimatedVisibility(
                state.section == Section.Home,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 16.dp, bottom = if (settings.newsHidden) 84.dp else 282.dp),
                enter = fadeIn(tween(300, delayMillis = 200)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { -it / 4 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 4 },
            ) {
                FriendsCard(
                    model,
                    modifier = Modifier.guide(state, GuideAnchor.Friends),
                    hidden = settings.friendsHidden,
                    onHiddenChange = { settings.friendsHidden = it },
                    onJoin = { pack, address -> play(pack, address) },
                    onServerPack = { state.serverPack = it },
                    onOpenSettings = { state.section = Section.Settings },
                )
            }
            if (model.askingForFolder) FolderPrompt(onUse = model::useGameFolder, onCancel = model::cancelFolderQuestion)
            if (state.elySignIn) ElySignIn(model, onClose = { state.elySignIn = false })
            if (state.microsoftSignIn) MicrosoftSignIn(model, onClose = { state.microsoftSignIn = false })
            state.serverPack?.let { ServerPackDialog(model, it, onJoin = { pack, address -> play(pack, address) }, onClose = { state.serverPack = null }) }
            if (model.feedback.open) FeedbackCard(model.feedback)
            if (confirmClose) CloseWithServers(model, onCancel = { confirmClose = false }, onClose = finishClose)
            GuideOverlay(state, settings)
            resizeEdges()
        }
    }
}

@Composable
private fun TopBar(
    model: LauncherModel,
    state: LauncherState,
    onClose: () -> Unit,
    onExitForUpdate: () -> Unit,
    windowControls: @Composable (onClose: () -> Unit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tip("На главную") {
            Box(
                Modifier.size(56.dp)
                    .glass(CircleShape, alpha = GlassAlpha)
                    .clickable(role = Role.Button) { state.section = Section.Home },
                contentAlignment = Alignment.Center,
            ) {
                LumiMark(plate = scheme.primary, glyph = scheme.onPrimary, size = 44.dp)
            }
        }
        Row(
            Modifier.glass(CircleShape, alpha = GlassAlpha).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val items = Section.navigation
            items.forEachIndexed { i, section ->
                ToggleButton(
                    checked = state.section == section,
                    onCheckedChange = { state.section = section },
                    shapes = connectedShapes(i, items.size),
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = scheme.onSurfaceVariant,
                        checkedContainerColor = scheme.primary,
                        checkedContentColor = scheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                    modifier = Modifier.height(44.dp).then(
                        when (section) {
                            Section.Mods -> Modifier.guide(state, GuideAnchor.Mods)
                            Section.Packs -> Modifier.guide(state, GuideAnchor.Packs)
                            Section.Servers -> Modifier.guide(state, GuideAnchor.Servers)
                            else -> Modifier
                        },
                    ),
                ) {
                    Icon(section.icon, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(section.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glass(CircleShape, alpha = GlassAlpha).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { model.feedback.open = true }, Modifier.size(44.dp).guide(state, GuideAnchor.Feedback)) {
                Icon(Icons.Outlined.Feedback, "Обратная связь")
            }
            DownloadsButton(
                model,
                modifier = Modifier.guide(state, GuideAnchor.Downloads),
                open = state.downloadsOpen,
                onOpenChange = {
                    state.downloadsOpen = it
                    if (it) state.notificationsOpen = false
                },
            )
            NotificationsButton(
                model,
                state,
                open = state.notificationsOpen,
                onOpenChange = {
                    state.notificationsOpen = it
                    if (it) state.downloadsOpen = false
                },
                onExit = onExitForUpdate,
            )
            val inSettings = state.section == Section.Settings
            IconButton(
                onClick = { state.section = if (inSettings) Section.Home else Section.Settings },
                modifier = Modifier.size(44.dp).guide(state, GuideAnchor.Settings),
                colors = if (inSettings) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors(),
            ) { Icon(Icons.Outlined.Settings, "Настройки") }
            // The face of the account that plays; pressed, every account to switch to.
            var accounts by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .clickable(role = Role.Button) { accounts = true }
                        .semantics { contentDescription = "Аккаунт: ${model.playerName.ifBlank { "без ника" }}" },
                    contentAlignment = Alignment.Center,
                ) {
                    val ely = model.ely.session.takeIf { model.settings.account == AccountKind.ElyBy }
                    val microsoft = model.microsoft.session.takeIf { model.settings.account == AccountKind.Microsoft }
                    when {
                        ely != null -> SkinHead(model.ely.skin, 40.dp)
                        microsoft != null -> SkinHead(microsoft.skin, 40.dp)
                        else -> Avatar(offline = true, size = 40.dp)
                    }
                }
                GlassDropdownMenu(accounts, onDismissRequest = { accounts = false }) {
                    AccountMenuItems(model, state, onDone = { accounts = false })
                }
            }
        }
        windowControls(onClose)
    }
}

/** The one card every screen lives in; it widens for the screens that list things. */
@Composable
private fun ScreenCard(model: LauncherModel, state: LauncherState, play: (Instance?, String?) -> Unit, onExit: () -> Unit, modifier: Modifier) {
    val target = when (state.section) {
        Section.Home -> 440.dp
        Section.Mods, Section.Packs, Section.Servers -> CATALOG_WIDTH
        else -> 520.dp
    }
    val width by animateDpAsState(target, spring(dampingRatio = 0.8f, stiffness = 380f))
    Box(modifier.width(width).fillMaxHeight().glass(RoundedCornerShape(40.dp), alpha = CardGlassAlpha, elevation = 18.dp).padding(28.dp)) {
        AnimatedContent(
            targetState = state.section,
            transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
        ) { section ->
            when (section) {
                Section.Home -> HomeScreen(model, state, onPlay = { play(null, null) }, onExit = onExit)
                Section.Packs -> PacksScreen(
                    model,
                    state,
                    request = state.packRequest,
                    onRequestHandled = { state.packRequest = null },
                    onPlay = { instance ->
                        model.select(instance)
                        state.section = Section.Home
                        play(null, null)
                    },
                )
                Section.Mods -> ModsScreen(model, state, onEditPack = { state.openPacks(PackRequest.Edit(it.id)) })
                Section.Settings -> SettingsScreen(model, onGuide = { state.guide.start(state) }, scrollState = state.settingsScroll, onExit = onExit)
                Section.Console -> ConsoleScreen(model, onBack = { state.section = Section.Home })
                Section.Servers -> ServersScreen(
                    model,
                    state,
                    onJoin = { pack, address ->
                        state.section = Section.Home
                        play(pack, address)
                    },
                )
            }
        }
    }
}

/** The card's width on the screens that list things; the filters panel starts right of it. */
private val CATALOG_WIDTH = 620.dp

/** Closing with servers up: they are stopped with `stop` — worlds saved — and then the launcher goes. */
@Composable
private fun CloseWithServers(model: LauncherModel, onCancel: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stopping by remember { mutableStateOf(false) }
    val running = model.servers.all.filter { model.servers.session(it.id).status.active }
    ModalCard(width = 460.dp) {
        Text("Серверы ещё работают", style = MaterialTheme.typography.headlineLarge)
        Text(
            when (running.size) {
                0 -> "Все серверы уже остановлены."
                1 -> "«${running.first().name}» запущен. Лаунчер остановит его командой stop — мир сохранится — и закроется."
                else -> "Запущено серверов: ${running.size}. Лаунчер остановит их командой stop — миры сохранятся — и закроется."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stopping) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel, enabled = !stopping) { Text("Отмена") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    stopping = true
                    scope.launch {
                        model.servers.stopAll()
                        onClose()
                    }
                },
                enabled = !stopping,
                shapes = ButtonDefaults.shapes(),
            ) { Text(if (stopping) "Останавливаем…" else "Остановить и выйти") }
        }
    }
}

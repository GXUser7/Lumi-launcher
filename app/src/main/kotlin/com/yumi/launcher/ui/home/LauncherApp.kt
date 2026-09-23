package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.active
import com.yumi.launcher.data.PlayStatus
import com.yumi.launcher.scene.Parallax
import com.yumi.launcher.scene.SceneOptions
import com.yumi.launcher.scene.VoxelBackground
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.AfterLaunch
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.ui.components.Avatar
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.components.SkinHead
import com.yumi.launcher.ui.components.ResizeEdges
import com.yumi.launcher.ui.components.WindowControls
import com.yumi.launcher.ui.components.YumiMark
import com.yumi.launcher.ui.components.connectedShapes
import com.yumi.launcher.ui.console.ConsoleScreen
import com.yumi.launcher.ui.mods.ModsScreen
import com.yumi.launcher.ui.packs.PacksScreen
import com.yumi.launcher.ui.servers.ServersScreen
import com.yumi.launcher.ui.settings.SettingsScreen
import com.yumi.launcher.ui.theme.glass

/**
 * The whole window: the island scene underneath, a floating top bar, the card on the left that
 * every screen lives in, and the news carousel in the corner the island leaves free.
 */
@Composable
fun FrameWindowScope.LauncherApp(
    windowState: WindowState,
    settings: LauncherSettings,
    dark: Boolean,
    onClose: () -> Unit,
    closeRequests: Int = 0,
    /** Hands the navigation state and the model out once — for the smoke run, which drives them screen by screen. */
    onState: (LauncherState, LauncherModel) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val model = remember { LauncherModel(settings, scope) }
    val state = remember { LauncherState().also { onState(it, model) } }
    val parallax = remember { Parallax() }

    // A running server has no window of its own: closing the launcher asks first, then stops it properly.
    var confirmClose by remember { mutableStateOf(false) }
    val requestClose = { if (model.servers.anyActive) confirmClose = true else onClose() }
    LaunchedEffect(closeRequests) { if (closeRequests > 0) requestClose() }

    // Only a minimise the launcher did itself is undone when the game closes.
    var minimizedForGame by remember { mutableStateOf(false) }
    val minimize = {
        windowState.isMinimized = true
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
                    windowState.isMinimized = false
                    window.toFront()
                    minimizedForGame = false
                }
            },
        )
    }

    Box(
        Modifier.fillMaxSize().onPointerEvent(PointerEventType.Move) { event ->
            val p = event.changes.first().position
            parallax.x = p.x / size.width * 2 - 1
            parallax.y = p.y / size.height * 2 - 1
        },
    ) {
        // Nothing here sits on a Surface, so the content colour has to be set once for the window —
        // left alone it stays Material's default black, which the dark theme cannot survive.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            // The island holds still while the game runs: it would only be competing for the GPU.
            // Each section has its place on the island, and the camera flies there when it opens.
            VoxelBackground(
                parallax,
                night = dark,
                options = SceneOptions(settings.sceneQuality, settings.sceneMotion, settings.sceneFps),
                spot = state.section.spot,
                modifier = Modifier.fillMaxSize(),
                running = !windowState.isMinimized && model.status !is PlayStatus.Running,
            )
            // Underneath the bar, so the pills keep their clicks and the gaps between them drag the window.
            WindowDraggableArea(Modifier.fillMaxWidth().height(88.dp)) { Box(Modifier.fillMaxSize()) }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                TopBar(model, state, windowState, requestClose)
            }
            ScreenCard(model, state, play, Modifier.padding(start = 16.dp, top = 88.dp, bottom = 16.dp))
            // Only the home screen has news; elsewhere the corner belongs to the island, where the
            // camera has flown to that section's place.
            AnimatedVisibility(
                state.section == Section.Home,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                enter = fadeIn(tween(300, delayMillis = 250)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 3 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 3 },
            ) {
                NewsCarousel(state.news, { state.news = it })
            }
            if (model.askingForFolder) FolderPrompt(onUse = model::useGameFolder, onCancel = model::cancelFolderQuestion)
            if (state.elySignIn) ElySignIn(model, onClose = { state.elySignIn = false })
            if (confirmClose) CloseWithServers(model, onCancel = { confirmClose = false }, onClose = onClose)
            ResizeEdges(windowState)
        }
    }
}

@Composable
private fun FrameWindowScope.TopBar(model: LauncherModel, state: LauncherState, windowState: WindowState, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            YumiMark(plate = scheme.primary, glyph = scheme.onPrimary, size = 44.dp)
        }
        Row(
            Modifier.glass(CircleShape).padding(6.dp),
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
                    modifier = Modifier.height(44.dp),
                ) {
                    Icon(section.icon, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(section.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glass(CircleShape).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}, Modifier.size(44.dp)) { Icon(Icons.Outlined.Download, "Загрузки") }
            IconButton(onClick = {}, Modifier.size(44.dp)) {
                BadgedBox(badge = { Badge() }) { Icon(Icons.Outlined.Notifications, "Уведомления") }
            }
            val inSettings = state.section == Section.Settings
            IconButton(
                onClick = { state.section = if (inSettings) Section.Home else Section.Settings },
                modifier = Modifier.size(44.dp),
                colors = if (inSettings) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors(),
            ) { Icon(Icons.Outlined.Settings, "Настройки") }
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .clickable(role = Role.Button) { state.section = Section.Home }
                    .semantics { contentDescription = "Профиль: ${model.playerName.ifBlank { "без ника" }}" },
                contentAlignment = Alignment.Center,
            ) {
                if (model.settings.account == AccountKind.ElyBy && model.ely.session != null) SkinHead(model.ely.skin, 40.dp)
                else Avatar(offline = true, size = 40.dp)
            }
        }
        WindowControls(windowState, onClose)
    }
}

/** The one card every screen lives in; it widens for the screens that list things. */
@Composable
private fun ScreenCard(model: LauncherModel, state: LauncherState, play: (Instance?, String?) -> Unit, modifier: Modifier) {
    val target = when (state.section) {
        Section.Home -> 440.dp
        Section.Mods, Section.Packs, Section.Servers -> 620.dp
        else -> 520.dp
    }
    val width by animateDpAsState(target, spring(dampingRatio = 0.8f, stiffness = 380f))
    Box(modifier.width(width).fillMaxHeight().glass(RoundedCornerShape(40.dp)).padding(28.dp)) {
        AnimatedContent(
            targetState = state.section,
            transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
        ) { section ->
            when (section) {
                Section.Home -> HomeScreen(model, state, onPlay = { play(null, null) })
                Section.Packs -> PacksScreen(
                    model,
                    request = state.packRequest,
                    onRequestHandled = { state.packRequest = null },
                    onPlay = { instance ->
                        model.select(instance)
                        state.section = Section.Home
                        play(null, null)
                    },
                )
                Section.Mods -> ModsScreen(model, onEditPack = { state.openPacks(PackRequest.Edit(it.id)) })
                Section.Settings -> SettingsScreen(model)
                Section.Console -> ConsoleScreen(model)
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

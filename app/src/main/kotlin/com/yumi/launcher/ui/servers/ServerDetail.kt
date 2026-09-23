package com.yumi.launcher.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.ServerPing
import com.yumi.launcher.data.BannedPlayer
import com.yumi.launcher.data.Difficulty
import com.yumi.launcher.data.GameMode
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.LocalAddress
import com.yumi.launcher.data.ServerAuth
import com.yumi.launcher.data.ServerProfile
import com.yumi.launcher.data.ServerProperties
import com.yumi.launcher.data.ServerSession
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.data.active
import com.yumi.launcher.data.localAddresses
import com.yumi.launcher.data.numericProperties
import com.yumi.launcher.ui.components.Avatar
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.FormSection
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.Tip
import com.yumi.launcher.ui.components.ToggleRow
import com.yumi.launcher.ui.home.ServerTab
import com.yumi.launcher.ui.packs.MemorySlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One server: who it is and what it is doing, the addresses to give friends, and its terminal, settings, bans and mods. */
@Composable
internal fun ServerDetail(
    model: LauncherModel,
    profile: ServerProfile,
    tab: ServerTab,
    onTab: (ServerTab) -> Unit,
    onBack: () -> Unit,
    onJoin: (Instance?, String) -> Unit,
) {
    val session = model.servers.session(profile.id)
    val tabs = if (profile.loader == Loader.Vanilla) ServerTab.entries - ServerTab.Mods else ServerTab.entries
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeader(model, profile, session, onBack)
        val status = session.status
        if (status is ServerStatus.Installing) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearWavyProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
                Hint("Установка: ${status.step.lowercase()} · ${(status.fraction * 100).toInt()}%")
            }
        }
        if (status is ServerStatus.Failed) FailureBanner(status.message)
        AddressCard(model, profile, session, onJoin)
        ChoiceGroup(tabs, tab.takeIf { it in tabs } ?: ServerTab.Console, { it.label }, onTab, height = 44.dp) {
            Icon(it.icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab.takeIf { it in tabs } ?: ServerTab.Console) {
                ServerTab.Console -> Terminal(model, profile, session)
                ServerTab.Settings -> ServerSettings(model, profile, session, onDeleted = onBack)
                ServerTab.Bans -> BannedPlayers(model, profile, session)
                ServerTab.Mods -> ServerMods(model, profile, session)
            }
        }
    }
}

/**
 * Name and state first, each on its own line and in its own weight; what the server runs comes
 * after, as quiet chips — so "Работает" is never lost in a row of version numbers.
 */
@Composable
private fun DetailHeader(model: LauncherModel, profile: ServerProfile, session: ServerSession, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Spacer(Modifier.width(4.dp))
            ServerIcon(model, profile, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(session)
            }
            Spacer(Modifier.width(8.dp))
            PowerButton(model, profile, session)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip("${profile.loader.label} ${profile.version}", icon = Icons.Rounded.ViewInAr)
            profile.loaderVersion?.let { InfoChip(it, icon = Icons.Rounded.Widgets) }
            InfoChip("Вход: ${profile.auth.label.lowercase()}", icon = Icons.Rounded.Key)
            InfoChip("Порт ${model.servers.port(profile)}", icon = Icons.Rounded.Lan)
            model.instances[profile.packId]?.let { InfoChip("Сборка «${it.name}»", icon = Icons.Rounded.Extension) }
        }
    }
}

/** The state as a tonal pill: its dot breathes while the server is on its way up or down. */
@Composable
internal fun StatusPill(session: ServerSession) {
    val scheme = MaterialTheme.colorScheme
    val status = session.status
    val (container, content) = when (status) {
        ServerStatus.Stopped -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        is ServerStatus.Failed -> scheme.errorContainer to scheme.onErrorContainer
        is ServerStatus.Running -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val dot = when (status) {
        ServerStatus.Stopped -> scheme.outline
        is ServerStatus.Failed -> scheme.error
        is ServerStatus.Running -> Online
        else -> scheme.tertiary
    }
    val transitional = status is ServerStatus.Installing || status == ServerStatus.Starting || status == ServerStatus.Stopping
    val pulse by rememberInfiniteTransition().animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(Modifier.height(28.dp).padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(if (transitional) pulse else 1f).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Text(
                when (status) {
                    ServerStatus.Stopped -> "Выключен"
                    is ServerStatus.Installing -> "Устанавливается · ${(status.fraction * 100).toInt()}%"
                    ServerStatus.Starting -> "Запускается…"
                    is ServerStatus.Running -> "Работает · ${uptime(status.since)} · " +
                        if (session.players.isEmpty()) "никого" else players(session.players.size)
                    ServerStatus.Stopping -> "Сохраняет мир и выключается…"
                    is ServerStatus.Failed -> "Не запустился"
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FailureBanner(message: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.errorContainer, contentColor = scheme.onErrorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PowerButton(model: LauncherModel, profile: ServerProfile, session: ServerSession) {
    val manager = model.servers
    when (session.status) {
        ServerStatus.Stopped, is ServerStatus.Failed -> Button(onClick = { manager.start(profile.id) }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Запустить")
        }
        is ServerStatus.Installing -> OutlinedButton(onClick = { manager.stop(profile.id) }, shapes = ButtonDefaults.shapes()) { Text("Отменить") }
        ServerStatus.Stopping -> OutlinedButton(
            onClick = { manager.kill(profile.id) },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Выключить сразу") }
        else -> FilledTonalButton(onClick = { manager.stop(profile.id) }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Остановить")
        }
    }
}

/**
 * Where friends connect to: a tile per address, copied with a click, beside the way in for the host.
 * A tile keeps its size and its address when copied — only the glyph and the network line change.
 */
@Composable
private fun AddressCard(model: LauncherModel, profile: ServerProfile, session: ServerSession, onJoin: (Instance?, String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val port = model.servers.port(profile)
    val addresses by produceState(emptyList<LocalAddress>()) { value = withContext(Dispatchers.IO) { localAddresses() } }
    var copied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(1500)
            copied = null
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Адрес для друзей", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Hint(
                    when {
                        addresses.isEmpty() -> "компьютер не в сети — подключитесь к роутеру или VPN"
                        addresses.none { it.network != "Локальная сеть" } -> "не в одной сети? Radmin VPN или ZeroTier"
                        else -> "нажмите, чтобы скопировать"
                    },
                    Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    addresses.forEach { address ->
                        val text = if (port == 25565) address.ip else "${address.ip}:$port"
                        AddressTile(address, text, copied == text, Modifier.width(206.dp)) {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                            copied = text
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(
                    onClick = { onJoin(model.instances[profile.packId], if (port == 25565) "localhost" else "localhost:$port") },
                    enabled = session.status is ServerStatus.Running && !model.busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Зайти")
                }
            }
        }
    }
}

@Composable
private fun AddressTile(address: LocalAddress, text: String, copied: Boolean, modifier: Modifier, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val local = address.network == "Локальная сеть"
    Surface(onClick = onCopy, shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainerHighest, modifier = modifier) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeTile(
                if (local) Icons.Rounded.Lan else Icons.Rounded.VpnLock,
                if (local) scheme.primaryContainer else scheme.tertiaryContainer,
                if (local) scheme.onPrimaryContainer else scheme.onTertiaryContainer,
                34.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (copied) "Скопировано" else address.network,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (copied) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            AnimatedContent(copied, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }) { done ->
                Icon(
                    if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    if (done) "Скопировано" else "Скопировать",
                    Modifier.size(18.dp),
                    tint = if (done) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Commands worth one click; those ending in a space wait for a name to be typed after them. */
private val QUICK = listOf(
    "list" to "Кто онлайн",
    "save-all" to "Сохранить мир",
    "time set day" to "День",
    "weather clear" to "Ясно",
    "say " to "Сообщение всем…",
    "op " to "Оператор…",
    "whitelist add " to "В белый список…",
    "gamemode creative " to "Творчество…",
)

@Composable
private fun Terminal(model: LauncherModel, profile: ServerProfile, session: ServerSession) {
    val scheme = MaterialTheme.colorScheme
    val manager = model.servers
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    // Scrolling up to read stops the terminal from jumping to the newest line; scrolling back down resumes it.
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, more) -> if (scrolling) follow = !more }
    }
    LaunchedEffect(session.lines.size) { if (follow && session.lines.isNotEmpty()) list.scrollToItem(session.lines.lastIndex) }
    var input by remember(profile.id) { mutableStateOf(TextFieldValue(session.draft, TextRange(session.draft.length))) }
    // The session keeps the draft; the field keeps the cursor. Edits go to both, and a draft put in
    // from outside — a quick command chip, the smoke run — comes back into the field.
    fun edit(value: TextFieldValue) {
        input = value
        session.draft = value.text
    }
    LaunchedEffect(session.draft) { if (session.draft != input.text) input = TextFieldValue(session.draft, TextRange(session.draft.length)) }
    var back by remember { mutableIntStateOf(0) }
    val live = session.status == ServerStatus.Starting || session.status is ServerStatus.Running
    val focus = remember { FocusRequester() }

    // Suggestions for what is being typed: the selected one is taken with Tab, arrows move through
    // them, Esc hides them until the next keystroke. A command called back from history is not
    // picked apart — the arrows keep walking the history instead.
    val pardon = input.text.trimStart().startsWith("pardon", ignoreCase = true)
    val banned by produceState(emptyList<String>(), profile.id, pardon) {
        if (pardon) value = withContext(Dispatchers.IO) { runCatching { manager.bannedPlayers(profile).map { it.name } }.getOrDefault(emptyList()) }
    }
    val suggestions = remember(input.text, session.players.size, banned) {
        ConsoleCommands.suggest(input.text, profile.loader, session.players.toList(), banned)
    }
    var pick by remember(input.text) { mutableIntStateOf(0) }
    var hidden by remember(input.text) { mutableStateOf(false) }
    var recalled by remember { mutableStateOf<String?>(null) }
    val showing = live && !hidden && suggestions.isNotEmpty() && input.text != recalled
    val take = { suggestion: Suggestion? ->
        suggestion?.completion?.let { edit(TextFieldValue(it, TextRange(it.length))) }
    }

    val send = {
        manager.send(profile.id, input.text)
        edit(TextFieldValue(""))
        back = 0
        recalled = null
    }
    fun recall(steps: Int) {
        val history = session.sent
        if (history.isEmpty()) return
        back = (back + steps).coerceIn(0, history.size)
        val text = if (back == 0) "" else history[history.size - back]
        edit(TextFieldValue(text, TextRange(text.length)))
        recalled = text
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (session.players.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Онлайн:", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                session.players.forEach { name -> PlayerChip(name) { command -> manager.send(profile.id, "$command $name") } }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
                if (session.lines.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        ShapeTile(Icons.Rounded.Terminal, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, 52.dp)
                        Spacer(Modifier.height(10.dp))
                        Hint(if (live) "Сервер запускается…" else "Здесь будет вывод сервера. Запустите его — и сюда же можно писать команды.")
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(state = list, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            itemsIndexed(session.lines) { _, line ->
                                Text(
                                    line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = when {
                                        line.startsWith("> ") || line.startsWith("[Yumi]") -> scheme.primary
                                        "ERROR" in line || "Exception" in line || "FATAL" in line -> scheme.error
                                        "WARN" in line -> scheme.secondary
                                        else -> scheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                showing,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                enter = fadeIn(tween(120)) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(90)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                SuggestionPanel(suggestions, pick) { chosen ->
                    take(chosen)
                    runCatching { focus.requestFocus() }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                value = input,
                onValueChange = ::edit,
                placeholder = if (live) "Начните вводить команду — подскажу, Tab допишет" else "Запустите сервер, чтобы вводить команды",
                icon = Icons.Rounded.ChevronRight,
                enabled = live,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = CircleShape,
                modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val choosable = if (showing) suggestions.size else 0
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter -> { send(); true }
                        // Always ours: in a text field Tab would otherwise walk the focus away.
                        Key.Tab -> {
                            if (live) take(suggestions.getOrNull(pick)?.takeIf { s -> s.completion != null } ?: suggestions.firstOrNull { s -> s.completion != null })
                            true
                        }
                        Key.DirectionUp -> { if (choosable > 1) pick = (pick - 1 + choosable) % choosable else recall(1); true }
                        Key.DirectionDown -> { if (choosable > 1) pick = (pick + 1) % choosable else recall(-1); true }
                        Key.Escape -> if (showing) { hidden = true; true } else false
                        else -> false
                    }
                },
            )
            Tip("Отправить") {
                FilledIconButton(onClick = send, enabled = live && input.text.isNotBlank(), shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Отправить")
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.forEach { (command, label) ->
                AssistChip(
                    onClick = {
                        if (command.endsWith(" ")) {
                            edit(TextFieldValue(command, TextRange(command.length)))
                            runCatching { focus.requestFocus() }
                        } else manager.send(profile.id, command)
                    },
                    enabled = live,
                    label = { Text(label) },
                    shape = CircleShape,
                )
            }
        }
    }
}

/**
 * What the typed command could go on with: the word, what it still takes, and a line on what it
 * does. Six at a time; the window follows the selection.
 */
@Composable
private fun SuggestionPanel(items: List<Suggestion>, pick: Int, onPick: (Suggestion) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val window = 6
    val start = (pick - window + 1).coerceIn(0, (items.size - window).coerceAtLeast(0))
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHighest, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp)) {
            items.subList(start, minOf(items.size, start + window)).forEachIndexed { i, item ->
                val selected = start + i == pick && item.completion != null
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (selected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (item.completion != null) Modifier.clickable { onPick(item) } else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val onRow = if (selected) scheme.onPrimaryContainer else scheme.onSurface
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = if (selected) onRow else scheme.primary, fontWeight = FontWeight.Bold)) { append(item.word.take(item.typed)) }
                            withStyle(SpanStyle(color = onRow, fontWeight = FontWeight.SemiBold)) { append(item.word.drop(item.typed)) }
                            if (item.usage.isNotEmpty()) withStyle(SpanStyle(color = if (selected) onRow.copy(alpha = 0.7f) else scheme.onSurfaceVariant)) { append(" " + item.usage) }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.46f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) onRow else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.54f),
                    )
                }
            }
            val choosable = items.count { it.completion != null }
            if (choosable > 0) {
                Text(
                    (if (items.size > window) "${pick + 1} из ${items.size} · " else "") + "↑↓ выбрать · Tab — дописать · Esc — скрыть",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerChip(name: String, onCommand: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        InputChip(
            selected = open,
            onClick = { open = true },
            label = { Text(name) },
            leadingIcon = { Icon(Icons.Rounded.Person, null, Modifier.size(18.dp)) },
            shape = CircleShape,
        )
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            listOf("op" to "Сделать оператором", "deop" to "Снять оператора", "kick" to "Выгнать", "ban" to "Забанить").forEach { (command, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onCommand(command); open = false })
            }
        }
    }
}

/** Everything the settings tab edits, so the form can tell whether anything differs from what is saved. */
private data class ServerForm(
    val name: String,
    val motd: String,
    val port: String,
    val maxPlayers: String,
    val mode: GameMode,
    val difficulty: Difficulty,
    val auth: ServerAuth,
    val pvp: Boolean,
    val whitelist: Boolean,
    val flight: Boolean,
    val commandBlocks: Boolean,
    val viewDistance: Int,
    val seed: String,
    val memoryMb: Int,
    val jvmArgs: String,
) {
    companion object {
        fun read(profile: ServerProfile, props: ServerProperties) = ServerForm(
            name = profile.name,
            motd = props["motd"] ?: profile.name,
            port = props.int("server-port", 25565).toString(),
            maxPlayers = props.int("max-players", 20).toString(),
            mode = GameMode.of(props["gamemode"]),
            difficulty = Difficulty.of(props["difficulty"]),
            auth = profile.auth,
            pvp = props.bool("pvp", true),
            whitelist = props.bool("white-list", false),
            flight = props.bool("allow-flight", false),
            commandBlocks = props.bool("enable-command-block", false),
            viewDistance = props.int("view-distance", 10),
            seed = props["level-seed"].orEmpty(),
            memoryMb = profile.memoryMb,
            jvmArgs = profile.jvmArgs,
        )
    }
}

/**
 * The properties a server owner changes most, in groups: how the server looks in a friend's list,
 * how to reach it, the rules, the world, and what it runs with. Nothing is written until "Сохранить",
 * which waits in a bar at the bottom as soon as something differs from the file.
 */
@Composable
private fun ServerSettings(model: LauncherModel, profile: ServerProfile, session: ServerSession, onDeleted: () -> Unit) {
    val manager = model.servers
    val scheme = MaterialTheme.colorScheme
    val props = remember(profile.id) { manager.properties(profile) }
    val numeric = numericProperties(profile.version)
    var saved by remember(profile.id) { mutableStateOf(ServerForm.read(profile, props)) }
    var form by remember(profile.id) { mutableStateOf(saved) }
    var justSaved by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val worldExists = manager.folder(profile).resolve(props["level-name"] ?: "world").isDirectory
    val portValue = form.port.toIntOrNull()?.takeIf { it in 1024..65535 }
    val slots = form.maxPlayers.toIntOrNull()
    val pack = model.instances[profile.packId]
    val dirty = form != saved
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(1800)
            justSaved = false
        }
    }
    val save = {
        props["motd"] = form.motd
        props["server-port"] = portValue.toString()
        props["max-players"] = (slots ?: 20).coerceAtLeast(1).toString()
        props["gamemode"] = if (numeric) form.mode.ordinal.toString() else form.mode.key
        props["difficulty"] = if (numeric) form.difficulty.ordinal.toString() else form.difficulty.key
        props["online-mode"] = (form.auth != ServerAuth.Offline).toString()
        props["pvp"] = form.pvp.toString()
        props["white-list"] = form.whitelist.toString()
        props["enforce-whitelist"] = form.whitelist.toString()
        props["allow-flight"] = form.flight.toString()
        props["enable-command-block"] = form.commandBlocks.toString()
        props["view-distance"] = form.viewDistance.toString()
        if (!worldExists) props["level-seed"] = form.seed.trim()
        props.save()
        manager[profile.id]?.let {
            manager.save(it.copy(name = form.name.trim().ifEmpty { it.name }, auth = form.auth, memoryMb = form.memoryMb, jvmArgs = form.jvmArgs.trim()))
        }
        form = form.copy(name = form.name.trim().ifEmpty { profile.name }, maxPlayers = (slots ?: 20).coerceAtLeast(1).toString())
        saved = form
        justSaved = true
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pack != null && (pack.version != profile.version || pack.loader != profile.loader)) {
                Surface(shape = RoundedCornerShape(20.dp), color = scheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Сборка «${pack.name}» теперь на ${pack.loader.label} ${pack.version}, а сервер — на ${profile.loader.label} ${profile.version}. Обновлённый мир назад не откатить.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { manager.updateFromPack(profile) }, enabled = !session.status.active) { Text("Обновить") }
                    }
                }
            }

            FormSection("В списке серверов", Icons.Rounded.TextFields, subtitle = "Так сервер увидят друзья в меню «Сетевая игра»") {
                Field(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it.take(40)) },
                    label = "Название в лаунчере",
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    modifier = Modifier.fillMaxWidth(),
                )
                Field(
                    value = form.motd,
                    onValueChange = { form = form.copy(motd = it.take(59)) },
                    label = "Описание в списке серверов",
                    icon = Icons.Rounded.TextFields,
                    supporting = "Цвета как в игре: §a зелёный, §e жёлтый, §b голубой, §l жирный",
                    modifier = Modifier.fillMaxWidth(),
                )
                ServerListPreview(model, profile, form.name.ifBlank { profile.name }, ServerPing.motdOf(form.motd), slots ?: 20)
            }

            FormSection("Подключение", Icons.Rounded.Lan) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(
                        value = form.port,
                        onValueChange = { form = form.copy(port = it.filter(Char::isDigit).take(5)) },
                        label = "Порт",
                        icon = Icons.Rounded.Lan,
                        isError = portValue == null,
                        supporting = if (portValue == null) "От 1024 до 65535" else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Field(
                        value = form.maxPlayers,
                        onValueChange = { form = form.copy(maxPlayers = it.filter(Char::isDigit).take(4)) },
                        label = "Мест",
                        icon = Icons.Rounded.Groups,
                        isError = slots == null || slots < 1,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailing = {
                            Row {
                                IconButton(onClick = { form = form.copy(maxPlayers = ((slots ?: 2) - 1).coerceAtLeast(1).toString()) }) {
                                    Icon(Icons.Rounded.Remove, "Меньше мест")
                                }
                                IconButton(onClick = { form = form.copy(maxPlayers = ((slots ?: 0) + 1).coerceAtMost(9999).toString()) }) {
                                    Icon(Icons.Rounded.Add, "Больше мест")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Hint(
                    if (portValue == 25565) "25565 — стандартный порт: друзьям не нужно дописывать его к адресу."
                    else "С нестандартным портом друзья вводят адрес вместе с ним: IP:${portValue ?: "порт"}.",
                )
                Label("Кто может зайти")
                ChoiceGroup(ServerAuth.entries, form.auth, { it.label }, { form = form.copy(auth = it) }, height = 44.dp)
                Hint(form.auth.hint)
            }

            FormSection("Правила игры", Icons.Rounded.Gavel) {
                Label("Режим игры")
                ChoiceGroup(GameMode.entries, form.mode, { it.label }, { form = form.copy(mode = it) }, height = 44.dp)
                Label("Сложность")
                ChoiceGroup(Difficulty.entries, form.difficulty, { it.label }, { form = form.copy(difficulty = it) }, height = 44.dp)
                ToggleRow("PvP", form.pvp, { form = form.copy(pvp = it) }, icon = Icons.Rounded.SportsMartialArts, description = "Игроки могут бить друг друга")
                ToggleRow(
                    "Белый список", form.whitelist, { form = form.copy(whitelist = it) }, icon = Icons.Rounded.VerifiedUser,
                    description = if (form.whitelist) "Пускает только добавленных: whitelist add ник в терминале" else "Зайти может любой, кто знает адрес",
                )
                ToggleRow("Разрешить полёт", form.flight, { form = form.copy(flight = it) }, icon = Icons.Rounded.Flight, description = "Без этого сервер выгоняет за полёт — нужно модам с джетпаками")
                ToggleRow("Командные блоки", form.commandBlocks, { form = form.copy(commandBlocks = it) }, icon = Icons.Rounded.Code)
            }

            FormSection("Мир", Icons.Rounded.Public) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Дальность прорисовки", Modifier.weight(1f))
                    Text("${form.viewDistance} чанков", style = MaterialTheme.typography.titleSmall)
                }
                Slider(value = form.viewDistance.toFloat(), onValueChange = { form = form.copy(viewDistance = it.toInt()) }, valueRange = 3f..32f, steps = 28)
                Field(
                    value = form.seed,
                    onValueChange = { form = form.copy(seed = it.take(40)) },
                    label = "Сид мира",
                    placeholder = "случайный",
                    icon = Icons.Rounded.Tag,
                    enabled = !worldExists,
                    supporting = if (worldExists) "Мир уже создан — сид действует только для нового мира" else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection("Запуск", Icons.Rounded.Memory) {
                Label("Память сервера")
                MemorySlider(form.memoryMb) { form = form.copy(memoryMb = it) }
                Field(
                    value = form.jvmArgs,
                    onValueChange = { form = form.copy(jvmArgs = it) },
                    label = "Аргументы Java",
                    placeholder = "-XX:+UseG1GC",
                    icon = Icons.Rounded.Code,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.openFolder(manager.folder(profile)) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Папка сервера")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        if (confirmDelete) {
                            manager.delete(profile)
                            onDeleted()
                        } else confirmDelete = true
                    },
                    enabled = !session.status.active,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (confirmDelete) "Удалить вместе с миром?" else "Удалить сервер")
                }
            }
        }
        AnimatedVisibility(dirty || justSaved, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SaveBar(
                text = when {
                    !dirty -> "Сохранено"
                    session.status.active -> "Сервер запущен — изменения вступят в силу после перезапуска"
                    else -> "Есть несохранённые изменения"
                },
                dirty = dirty,
                canSave = portValue != null && slots != null && slots >= 1,
                onReset = { form = saved },
                onSave = save,
            )
        }
    }
}

/** The snackbar-toned bar that holds "Сохранить" while the form differs from the file. */
@Composable
private fun SaveBar(text: String, dirty: Boolean, canSave: Boolean, onReset: () -> Unit, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.inverseSurface, contentColor = scheme.inverseOnSurface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!dirty) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = scheme.inversePrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.weight(1f))
            if (dirty) {
                TextButton(onClick = onReset, colors = ButtonDefaults.textButtonColors(contentColor = scheme.inversePrimary)) { Text("Отменить") }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.inversePrimary, contentColor = scheme.inverseSurface),
                ) { Text("Сохранить") }
            }
        }
    }
}

/**
 * The server's ban list, read from `banned-players.json` — and re-read whenever the file changes,
 * so a ban given in the terminal shows up here by itself.
 */
@Composable
private fun BannedPlayers(model: LauncherModel, profile: ServerProfile, session: ServerSession) {
    val scheme = MaterialTheme.colorScheme
    val manager = model.servers
    val file = remember(profile.id) { manager.bansFile(profile) }
    var stamp by remember(profile.id) { mutableLongStateOf(-1L) }
    var refresh by remember(profile.id) { mutableIntStateOf(0) }
    LaunchedEffect(profile.id) {
        while (true) {
            val modified = withContext(Dispatchers.IO) { if (file.isFile) file.lastModified() else 0L }
            if (modified != stamp) stamp = modified
            delay(1500)
        }
    }
    val result by produceState<Result<List<BannedPlayer>>?>(null, profile.id, stamp, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { manager.bannedPlayers(profile) } }
    }
    val running = session.status is ServerStatus.Running
    val busy = session.status.active && !running
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Забаненные игроки", style = MaterialTheme.typography.titleMedium)
                Hint(
                    when {
                        busy -> "Сервер запускается или выключается — разбанить можно чуть позже."
                        running -> "Разбан уходит серверу командой pardon."
                        else -> "Сервер выключен — разбан сразу правит список."
                    },
                )
            }
            Tip("Перечитать список") {
                FilledTonalIconButton(onClick = { refresh++ }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Refresh, "Перечитать список") }
            }
        }
        val players = result?.getOrNull()
        when {
            result == null -> Hint("Читаем список блокировок…")
            players == null -> EmptyState(Icons.Rounded.Warning, "Не удалось прочитать список", "Проверьте banned-players.json в папке сервера.")
            players.isEmpty() -> EmptyState(
                Icons.Rounded.Shield,
                "Никто не забанен",
                "Забанить можно во вкладке «Терминал»: нажмите на ник игрока онлайн или введите ban ник.",
            )
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(players, key = { it.name }) { player ->
                    BanRow(player, canPardon = !busy) { manager.pardon(profile, player.name); refresh++ }
                }
            }
        }
    }
}

@Composable
private fun BanRow(player: BannedPlayer, canPardon: Boolean, onPardon: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(offline = true, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    player.reason.takeIf { it.isNotBlank() && it != "Banned by an operator." } ?: "Причина не указана",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val forever = player.expires.equals("forever", ignoreCase = true)
                    InfoChip(
                        if (forever) "Навсегда" else "До ${banDate(player.expires)}",
                        icon = Icons.Rounded.Block,
                        container = if (forever) scheme.errorContainer else scheme.secondaryContainer,
                        content = if (forever) scheme.onErrorContainer else scheme.onSecondaryContainer,
                    )
                    if (player.source.isNotBlank()) InfoChip(if (player.source == "Server") "Консоль" else player.source, icon = Icons.Rounded.Gavel)
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onPardon, enabled = canPardon, shapes = ButtonDefaults.shapes()) { Text("Разбанить") }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShapeTile(icon, scheme.secondaryContainer, scheme.onSecondaryContainer, 64.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Hint(text)
    }
}

/** "2026-10-12 14:00:00 +0300" → "12 окт. 2026, 14:00"; anything else as it came. */
private fun banDate(raw: String): String = runCatching {
    ZonedDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"))
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru")))
}.getOrDefault(raw)

/** The server's own mods folder, and the sync that fills it from the pack. */
@Composable
private fun ServerMods(model: LauncherModel, profile: ServerProfile, session: ServerSession) {
    val manager = model.servers
    val scheme = MaterialTheme.colorScheme
    val dir = manager.modsDir(profile)
    var files by remember { mutableStateOf(listMods(dir)) }
    LaunchedEffect(profile.syncedMods, manager.syncing) { files = listMods(dir) }
    val pack = model.instances[profile.packId]
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Модов на сервере: ${files.count { it.name.endsWith(".jar") }}", style = MaterialTheme.typography.titleMedium)
                Hint("Клиентские — Sodium, Iris, миникарты — на сервер не копируются." + if (session.status.active) " Изменения — после перезапуска." else "")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { manager.syncMods(profile) },
                enabled = pack != null && manager.syncing == null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Взять из сборки")
            }
        }
        if (manager.syncing == profile.id) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        manager.modsNotice?.takeIf { it.first == profile.id }?.let { (_, text) ->
            Surface(shape = RoundedCornerShape(18.dp), color = scheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSecondaryContainer, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    TextButton(onClick = { manager.modsNotice = null }) { Text("Ок") }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(files, key = { it.name }) { file ->
                val enabled = file.name.endsWith(".jar")
                val base = file.name.removeSuffix(".disabled")
                Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShapeTile(Icons.Rounded.Extension, scheme.surfaceContainerHighest, if (enabled) scheme.primary else scheme.outline, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                base.removeSuffix(".jar"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) scheme.onSurface else scheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(if (base in profile.syncedMods) "из сборки" else "добавлен вручную", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { on ->
                            file.renameTo(file.resolveSibling(if (on) base else "$base.disabled"))
                            files = listMods(dir)
                        })
                        IconButton(onClick = {
                            file.delete()
                            files = listMods(dir)
                        }) { Icon(Icons.Outlined.Delete, "Удалить ${base.removeSuffix(".jar")}", tint = scheme.onSurfaceVariant) }
                    }
                }
            }
        }
        TextButton(onClick = { model.openFolder(dir) }) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Открыть папку mods — туда можно положить и свои моды")
        }
    }
}

private fun listMods(dir: File): List<File> = dir.listFiles().orEmpty()
    .filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled")) }
    .sortedBy { it.name.lowercase() }

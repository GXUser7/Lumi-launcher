package com.yumi.launcher.ui.servers

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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yumi.launcher.data.Difficulty
import com.yumi.launcher.data.BannedPlayer
import com.yumi.launcher.data.GameMode
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.LocalAddress
import com.yumi.launcher.data.ServerAuth
import com.yumi.launcher.data.ServerProfile
import com.yumi.launcher.data.ServerSession
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.data.active
import com.yumi.launcher.data.localAddresses
import com.yumi.launcher.data.numericProperties
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.packs.MemorySlider
import com.yumi.launcher.ui.packs.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

private enum class DetailTab(val label: String) { Console("Терминал"), Settings("Настройки"), Bans("Баны"), Mods("Моды") }

/** One server: power, the addresses to give friends, and its terminal, settings and mods. */
@Composable
internal fun ServerDetail(model: LauncherModel, profile: ServerProfile, onBack: () -> Unit, onJoin: (Instance?, String) -> Unit) {
    val session = model.servers.session(profile.id)
    var tab by remember(profile.id) { mutableStateOf(DetailTab.Console) }
    val tabs = if (profile.loader == Loader.Vanilla) listOf(DetailTab.Console, DetailTab.Settings, DetailTab.Bans) else DetailTab.entries
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(profile.name, onBack) { PowerButton(model, profile, session) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PackTile(profile.icon, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "${profile.loader.label} ${profile.version}${profile.loaderVersion?.let { " · $it" }.orEmpty()} · ${profile.auth.label.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusLine(session)
            }
        }
        val status = session.status
        if (status is ServerStatus.Installing) LinearWavyProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
        AddressCard(model, profile, session, onJoin)
        ChoiceGroup(tabs, tab, { it.label }, { tab = it }, height = 40.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                DetailTab.Console -> Terminal(model, profile, session)
                DetailTab.Settings -> ServerSettings(model, profile, session, onDeleted = onBack)
                DetailTab.Bans -> BannedPlayers(model, profile)
                DetailTab.Mods -> ServerMods(model, profile, session)
            }
        }
    }
}

@Composable
private fun BannedPlayers(model: LauncherModel, profile: ServerProfile) {
    val scheme = MaterialTheme.colorScheme
    var refresh by remember(profile.id) { mutableIntStateOf(0) }
    val result by produceState<Result<List<BannedPlayer>>?>(null, profile.id, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { model.servers.bannedPlayers(profile) } }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Забаненные игроки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Обновить")
            }
        }
        val players = result?.getOrNull()
        when {
            result == null -> Hint("Читаем список блокировок…")
            players == null -> Hint("Не удалось прочитать список блокировок — проверьте banned-players.json в папке сервера.")
            players.isEmpty() -> Hint("Забаненных игроков нет.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(players, key = { it.name }) { player ->
                    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(player.name, style = MaterialTheme.typography.titleSmall)
                            Text(player.reason.ifBlank { "Причина не указана" }, style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (player.expires.equals("forever", ignoreCase = true)) "Срок: бессрочно" else "До ${player.expires}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
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

/** Where friends connect to, one click to copy each; and a way in for the host. */
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
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Адрес для друзей", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    addresses.forEach { address ->
                        val text = if (port == 25565) address.ip else "${address.ip}:$port"
                        AssistChip(
                            onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                                copied = text
                            },
                            label = { Text("$text · ${address.network}") },
                            leadingIcon = {
                                Icon(
                                    if (copied == text) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    if (copied == text) "Скопировано" else "Скопировать",
                                    Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
                if (addresses.none { it.network != "Локальная сеть" }) {
                    Hint("Друзья не в вашей сети? Поставьте все Radmin VPN или ZeroTier — адрес в нём появится здесь.")
                }
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(
                onClick = { onJoin(model.instances[profile.packId], if (port == 25565) "localhost" else "localhost:$port") },
                enabled = session.status is ServerStatus.Running && !model.busy,
                shapes = ButtonDefaults.shapes(),
            ) { Text("Зайти") }
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
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var back by remember { mutableIntStateOf(0) }
    val live = session.status == ServerStatus.Starting || session.status is ServerStatus.Running
    val send = {
        manager.send(profile.id, input.text)
        input = TextFieldValue("")
        back = 0
    }
    fun recall(steps: Int) {
        val history = session.sent
        if (history.isEmpty()) return
        back = (back + steps).coerceIn(0, history.size)
        val text = if (back == 0) "" else history[history.size - back]
        input = TextFieldValue(text, TextRange(text.length))
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (session.players.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Онлайн:", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                session.players.forEach { name -> PlayerChip(name) { command -> manager.send(profile.id, "$command $name") } }
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (session.lines.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (live) "Команда без /, например: time set day" else "Запустите сервер, чтобы вводить команды") },
                singleLine = true,
                enabled = live,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = CircleShape,
                modifier = Modifier.weight(1f).onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter -> { send(); true }
                        Key.DirectionUp -> { recall(1); true }
                        Key.DirectionDown -> { recall(-1); true }
                        else -> false
                    }
                },
            )
            IconButton(onClick = send, enabled = live && input.text.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Отправить") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.forEach { (command, label) ->
                AssistChip(
                    onClick = {
                        if (command.endsWith(" ")) input = TextFieldValue(command, TextRange(command.length))
                        else manager.send(profile.id, command)
                    },
                    enabled = live,
                    label = { Text(label) },
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
        )
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            listOf("op" to "Сделать оператором", "deop" to "Снять оператора", "kick" to "Выгнать", "ban" to "Забанить").forEach { (command, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onCommand(command); open = false })
            }
        }
    }
}

/** The properties a server owner changes most, written into `server.properties` on save. */
@Composable
private fun ServerSettings(model: LauncherModel, profile: ServerProfile, session: ServerSession, onDeleted: () -> Unit) {
    val manager = model.servers
    val scheme = MaterialTheme.colorScheme
    val props = remember(profile.id) { manager.properties(profile) }
    val numeric = numericProperties(profile.version)
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var motd by remember(profile.id) { mutableStateOf(props["motd"] ?: profile.name) }
    var port by remember(profile.id) { mutableStateOf(props.int("server-port", 25565).toString()) }
    var maxPlayers by remember(profile.id) { mutableStateOf(props.int("max-players", 20).toString()) }
    var mode by remember(profile.id) { mutableStateOf(GameMode.of(props["gamemode"])) }
    var difficulty by remember(profile.id) { mutableStateOf(Difficulty.of(props["difficulty"])) }
    var auth by remember(profile.id) { mutableStateOf(profile.auth) }
    var pvp by remember(profile.id) { mutableStateOf(props.bool("pvp", true)) }
    var whitelist by remember(profile.id) { mutableStateOf(props.bool("white-list", false)) }
    var flight by remember(profile.id) { mutableStateOf(props.bool("allow-flight", false)) }
    var commandBlocks by remember(profile.id) { mutableStateOf(props.bool("enable-command-block", false)) }
    var viewDistance by remember(profile.id) { mutableIntStateOf(props.int("view-distance", 10)) }
    var seed by remember(profile.id) { mutableStateOf(props["level-seed"].orEmpty()) }
    var memory by remember(profile.id) { mutableIntStateOf(profile.memoryMb) }
    var jvmArgs by remember(profile.id) { mutableStateOf(profile.jvmArgs) }
    var saved by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val worldExists = manager.folder(profile).resolve(props["level-name"] ?: "world").isDirectory
    val portValue = port.toIntOrNull()?.takeIf { it in 1024..65535 }
    val pack = model.instances[profile.packId]
    LaunchedEffect(saved) {
        if (saved) {
            delay(2000)
            saved = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session.status.active) Hint("Сервер запущен — изменения вступят в силу после перезапуска.")
        if (pack != null && (pack.version != profile.version || pack.loader != profile.loader)) {
            Surface(shape = RoundedCornerShape(18.dp), color = scheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
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
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Название в лаунчере") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(motd, { motd = it.take(59) }, label = { Text("Описание в списке серверов") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Порт") }, singleLine = true, isError = portValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                maxPlayers, { maxPlayers = it.filter(Char::isDigit).take(4) }, label = { Text("Мест") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
            )
        }
        Label("Режим игры")
        ChoiceGroup(GameMode.entries, mode, { it.label }, { mode = it }, height = 44.dp)
        Label("Сложность")
        ChoiceGroup(Difficulty.entries, difficulty, { it.label }, { difficulty = it }, height = 44.dp)
        Label("Кто может зайти")
        ChoiceGroup(ServerAuth.entries, auth, { it.label }, { auth = it }, height = 44.dp)
        Hint(auth.hint)
        SwitchRow("PvP", pvp) { pvp = it }
        SwitchRow("Белый список", whitelist) { whitelist = it }
        if (whitelist) Hint("Пускает только тех, кого добавили: whitelist add ник в терминале.")
        SwitchRow("Разрешить полёт", flight) { flight = it }
        SwitchRow("Командные блоки", commandBlocks) { commandBlocks = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Дальность прорисовки", Modifier.weight(1f))
            Text("$viewDistance чанков", style = MaterialTheme.typography.titleSmall)
        }
        Slider(value = viewDistance.toFloat(), onValueChange = { viewDistance = it.toInt() }, valueRange = 3f..32f, steps = 28)
        OutlinedTextField(
            seed, { seed = it.take(40) },
            label = { Text("Сид мира") },
            placeholder = { Text("случайный") },
            singleLine = true,
            enabled = !worldExists,
            supportingText = { if (worldExists) Text("Мир уже создан — сид действует только для нового мира") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Label("Память сервера")
        MemorySlider(memory) { memory = it }
        OutlinedTextField(jvmArgs, { jvmArgs = it }, label = { Text("Аргументы Java") }, placeholder = { Text("-XX:+UseG1GC") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    props["motd"] = motd
                    props["server-port"] = portValue.toString()
                    props["max-players"] = (maxPlayers.toIntOrNull() ?: 20).coerceAtLeast(1).toString()
                    props["gamemode"] = if (numeric) mode.ordinal.toString() else mode.key
                    props["difficulty"] = if (numeric) difficulty.ordinal.toString() else difficulty.key
                    props["online-mode"] = (auth != ServerAuth.Offline).toString()
                    props["pvp"] = pvp.toString()
                    props["white-list"] = whitelist.toString()
                    props["enforce-whitelist"] = whitelist.toString()
                    props["allow-flight"] = flight.toString()
                    props["enable-command-block"] = commandBlocks.toString()
                    props["view-distance"] = viewDistance.toString()
                    if (!worldExists) props["level-seed"] = seed.trim()
                    props.save()
                    manager[profile.id]?.let { manager.save(it.copy(name = name.trim().ifEmpty { it.name }, auth = auth, memoryMb = memory, jvmArgs = jvmArgs.trim())) }
                    saved = true
                },
                enabled = portValue != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(if (saved) "Сохранено" else "Сохранить") }
            OutlinedButton(onClick = { model.openFolder(manager.folder(profile)) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Папка")
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
            ) { Text(if (confirmDelete) "Удалить вместе с миром?" else "Удалить", color = scheme.error) }
        }
    }
}

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
            Text("Модов на сервере: ${files.count { it.name.endsWith(".jar") }}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
        Hint(
            "Моды, которые нужны только игроку — Sodium, Iris, миникарты, — на сервер не копируются: их определяем по Modrinth и по описанию самого мода." +
                if (session.status.active) " Изменения подхватятся после перезапуска." else "",
        )
        if (manager.syncing == profile.id) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        manager.modsNotice?.takeIf { it.first == profile.id }?.let { (_, text) ->
            Surface(shape = RoundedCornerShape(16.dp), color = scheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
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
                Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Extension, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(base.removeSuffix(".jar"), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (base in profile.syncedMods) "из сборки" else "добавлен вручную",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { on ->
                            file.renameTo(file.resolveSibling(if (on) base else "$base.disabled"))
                            files = listMods(dir)
                        })
                        IconButton(onClick = {
                            file.delete()
                            files = listMods(dir)
                        }) { Icon(Icons.Outlined.Delete, "Удалить ${base.removeSuffix(".jar")}") }
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

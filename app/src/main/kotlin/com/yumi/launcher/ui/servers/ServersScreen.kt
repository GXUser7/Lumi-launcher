package com.yumi.launcher.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.SavedWorlds
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ServerAuth
import com.yumi.launcher.data.ServerProfile
import com.yumi.launcher.data.ServerSession
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.data.active
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.FieldShape
import com.yumi.launcher.ui.components.FormSection
import com.yumi.launcher.ui.components.LocalFieldContainer
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.home.LauncherState
import com.yumi.launcher.ui.home.ServerPackTarget
import com.yumi.launcher.ui.home.ServerTab
import com.yumi.launcher.ui.home.ServerView
import com.yumi.launcher.ui.home.ServersTab
import com.yumi.launcher.ui.packs.MemorySlider
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

/**
 * Servers, in two halves. "Мои серверы": servers this computer runs from a pack — created,
 * started and stopped here, with their settings, mods and a live terminal. "Быстрый вход":
 * other people's servers, pinged like the game's multiplayer list and joined in one click.
 */
@Composable
fun ServersScreen(model: LauncherModel, state: LauncherState, onJoin: (Instance?, String) -> Unit) {
    val view = if (state.serversTab == ServersTab.Hosted) state.serverView else ServerView.List
    // A server deleted elsewhere (or never loaded) falls back to the list.
    val detail = (view as? ServerView.Detail)?.let { model.servers[it.id] }
    val key = when {
        view is ServerView.Create -> "create"
        detail != null -> "server:${detail.id}"
        else -> "list"
    }
    AnimatedContent(
        targetState = key,
        transitionSpec = { fadeIn(tween(200, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
    ) { current ->
        when {
            current == "create" -> CreateServer(
                model,
                onBack = { state.serverView = ServerView.List },
                onCreated = {
                    state.serverTab = ServerTab.Console
                    state.serverView = ServerView.Detail(it.id)
                },
            )
            current.startsWith("server:") -> model.servers[current.removePrefix("server:")]?.let { profile ->
                ServerDetail(model, profile, state.serverTab, { state.serverTab = it }, onBack = { state.serverView = ServerView.List }, onJoin = onJoin)
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Серверы", style = MaterialTheme.typography.displaySmall)
                ChoiceGroup(ServersTab.entries, state.serversTab, { it.label }, { state.serversTab = it }, height = 44.dp)
                if (state.serversTab == ServersTab.Hosted) {
                    HostedList(
                        model,
                        onCreate = { state.serverView = ServerView.Create },
                        onOpen = {
                            state.serverTab = ServerTab.Console
                            state.serverView = ServerView.Detail(it.id)
                        },
                    )
                } else {
                    QuickJoinPane(model, onJoin, onServerPack = { state.serverPack = ServerPackTarget.Saved(it.id) })
                }
            }
        }
    }
}

@Composable
private fun HostedList(model: LauncherModel, onCreate: () -> Unit, onOpen: (ServerProfile) -> Unit) {
    val manager = model.servers
    if (manager.all.isEmpty()) {
        EmptyHosted(onCreate)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint("Сервер запускается на этом компьютере — друзья заходят к вам по адресу.", Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Button(onClick = onCreate, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Новый")
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(manager.all, key = { it.id }) { profile ->
            val session = manager.session(profile.id)
            Surface(
                onClick = { onOpen(profile) },
                shape = RoundedCornerShape(24.dp),
                color = if (session.status.active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PackTile(profile.icon, 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${profile.loader.label} ${profile.version} · порт ${manager.port(profile)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusLine(session)
                    }
                    PowerIconButton(model, profile, session)
                }
            }
        }
    }
}

@Composable
private fun EmptyHosted(onCreate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeTile(Icons.Rounded.Dns, scheme.primaryContainer, scheme.onPrimaryContainer, 64.dp)
            Text("Свой сервер для друзей", style = MaterialTheme.typography.headlineSmall)
            Hint(
                "Выберите сборку — лаунчер скачает серверную версию той же игры с тем же загрузчиком и модами. " +
                    "Запускайте и выключайте его отсюда, а командами управляйте в терминале.",
            )
            Button(onClick = onCreate, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Создать сервер")
            }
        }
    }
}

/** Start or stop, as a round button beside the server in the list. */
@Composable
private fun PowerIconButton(model: LauncherModel, profile: ServerProfile, session: ServerSession) {
    val active = session.status.active
    FilledTonalIconButton(
        onClick = { if (active) model.servers.stop(profile.id) else model.servers.start(profile.id) },
        shapes = IconButtonDefaults.shapes(),
        colors = if (active) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.filledIconButtonColors(),
    ) {
        Icon(if (active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (active) "Остановить «${profile.name}»" else "Запустить «${profile.name}»")
    }
}

/** A dot and a few words: what the server is doing right now. */
@Composable
internal fun StatusLine(session: ServerSession, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status = session.status
    val (color, text) = when (status) {
        ServerStatus.Stopped -> scheme.outline to "Выключен"
        is ServerStatus.Installing -> scheme.tertiary to "Установка: ${status.step.lowercase()} · ${(status.fraction * 100).toInt()}%"
        ServerStatus.Starting -> scheme.tertiary to "Запускается…"
        is ServerStatus.Running -> Online to when (val n = session.players.size) {
            0 -> "Работает · ${uptime(status.since)} · никого"
            else -> "Работает · ${uptime(status.since)} · ${players(n)}"
        }
        ServerStatus.Stopping -> scheme.tertiary to "Сохраняет мир и выключается…"
        is ServerStatus.Failed -> scheme.error to status.message
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (status is ServerStatus.Failed) scheme.error else scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal val Online = Color(0xFF3DBA6E)

internal fun players(n: Int): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11 -> "игрок"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "игрока"
        else -> "игроков"
    }
    return "$n $word"
}

/** "1:04:12" since [since], ticking. */
@Composable
internal fun uptime(since: Long): String {
    val now by produceState(System.currentTimeMillis(), since) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun CreateServer(model: LauncherModel, onBack: () -> Unit, onCreated: (ServerProfile) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var packId by remember { mutableStateOf(model.selected?.id) }
    val pack = model.instances[packId]
    var auth by remember { mutableStateOf(ServerAuth.Offline) }
    var port by remember { mutableStateOf(model.servers.freePort().toString()) }
    var memory by remember { mutableIntStateOf(if (pack?.loader == Loader.Vanilla) 2048 else 4096) }
    var eula by remember { mutableStateOf(model.settings.serverEulaAccepted) }
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var world by remember(pack?.id) { mutableStateOf<String?>(null) }
    var worlds by remember(pack?.id) { mutableStateOf<List<String>?>(null) }
    var worldsError by remember(pack?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(pack?.id, model.instances.root, model.busy) {
        worlds = null
        worldsError = null
        try {
            val gameDir = pack?.let { model.instances.gameDir(it) }
            worlds = withContext(Dispatchers.IO) { gameDir?.let(SavedWorlds::list).orEmpty() }
            if (world !in worlds.orEmpty()) world = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            worldsError = e.message ?: "Не удалось прочитать миры сборки"
        }
    }
    val portValue = port.toIntOrNull()?.takeIf { it in 1024..65535 }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Новый сервер", { if (!creating) onBack() })
        if (creating) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LoadingIndicator()
            Text(if (world == null) "Создаём сервер…" else "Копируем мир…", style = MaterialTheme.typography.titleMedium)
            Hint("Дождитесь завершения")
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSection("Сервер", Icons.Rounded.Dns, subtitle = "Повторяет сборку: ту же версию, загрузчик и моды") {
                Field(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = "Название",
                    placeholder = "Сервер для друзей",
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    modifier = Modifier.fillMaxWidth(),
                )
                PackField(model, pack) { packId = it.id }
                Hint(
                    when {
                        pack == null -> "Сначала создайте сборку — сервер повторяет её версию и моды."
                        pack.loader == Loader.Vanilla -> "Ванильный сервер Minecraft ${pack.version}."
                        else -> "Сервер получит Minecraft ${pack.version}, ${pack.loader.label} и моды сборки — кроме тех, что нужны только игроку: Sodium, Iris, миникарты."
                    },
                )
                WorldField(worlds.orEmpty(), world, pack != null && worlds != null) { world = it; error = null }
                Hint(
                    when {
                        worldsError != null -> worldsError!!
                        pack == null -> "Выберите сборку, чтобы увидеть её миры."
                        worlds == null -> "Ищем сохранённые миры…"
                        world != null && model.busy -> "Закройте игру перед копированием мира."
                        world != null -> "Мир скопируется на сервер. Исходный мир останется в сборке."
                        worlds.orEmpty().isEmpty() -> "В этой сборке пока нет сохранённых миров. Новый мир создастся при запуске сервера."
                        else -> "Создайте новый мир или выберите папку сохранения из сборки."
                    },
                )
            }
            FormSection("Подключение", Icons.Rounded.Lan) {
                Label("Кто может зайти")
                ChoiceGroup(ServerAuth.entries, auth, { it.label }, { auth = it }, height = 44.dp)
                Hint(auth.hint)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = "Порт",
                        icon = Icons.Rounded.Lan,
                        isError = portValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(170.dp),
                    )
                    Hint(
                        if (portValue == null) "Порт — число от 1024 до 65535."
                        else "25565 — стандартный: друзьям не нужно дописывать его к адресу. Второму серверу нужен другой.",
                        Modifier.weight(1f),
                    )
                }
            }
            FormSection("Ресурсы", Icons.Rounded.Memory) {
                Label("Память сервера")
                MemorySlider(memory) { memory = it }
                Hint("Ванильному хватает 2 ГБ, сборке с модами — от 4 ГБ.")
            }
            Surface(
                onClick = { eula = !eula },
                shape = RoundedCornerShape(24.dp),
                color = if (eula) scheme.primaryContainer else scheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = eula, onCheckedChange = { eula = it })
                    Text(
                        buildAnnotatedString {
                            append("Я принимаю ")
                            withStyle(SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline)) { append("лицензионное соглашение Minecraft") }
                            append(" — без него сервер не запускается")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).clickable { runCatching { Desktop.getDesktop().browse(URI("https://aka.ms/MinecraftEULA")) } },
                    )
                }
            }
        }
        error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val chosen = pack ?: return@Button
                    val chosenPort = portValue ?: return@Button
                    model.withGameFolder {
                        model.settings.serverEulaAccepted = true
                        creating = true
                        error = null
                        scope.launch {
                            try {
                                onCreated(model.servers.create(name, chosen, chosenPort, auth, memory, world))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = "Не удалось создать сервер: ${e.message ?: "проверьте доступ к папке игры"}"
                            } finally {
                                creating = false
                            }
                        }
                    }
                },
                enabled = !creating && name.isNotBlank() && pack != null && eula && portValue != null &&
                    (world == null || (worlds != null && !model.busy)),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Создать сервер")
            }
            Spacer(Modifier.width(12.dp))
            Hint(
                when {
                    creating -> "Подготавливаем файлы"
                    name.isBlank() -> "Придумайте название"
                    pack == null -> "Нужна сборка"
                    !eula -> "Отметьте согласие с EULA"
                    world != null && model.busy -> "Сначала закройте игру"
                    else -> "Скачается при первом запуске"
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WorldField(worlds: List<String>, selected: String?, enabled: Boolean, onPick: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            enabled = enabled,
            shape = FieldShape,
            color = LocalFieldContainer.current ?: scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Public, null, tint = scheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Мир", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text(selected ?: "Новый мир", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.UnfoldMore, "Выбрать мир", tint = scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp), shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(text = { Text("Новый мир") }, onClick = { onPick(null); open = false })
            worlds.forEach { name ->
                DropdownMenuItem(text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onPick(name); open = false })
            }
        }
    }
}

/** A pack, picked from a menu of all of them. */
@Composable
internal fun PackField(model: LauncherModel, pack: Instance?, onPick: (Instance) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            enabled = model.instances.all.isNotEmpty(),
            shape = FieldShape,
            color = LocalFieldContainer.current ?: scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Row(Modifier.padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pack != null) PackTile(pack, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(pack?.name ?: "Нет сборок", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pack != null) Text("${pack.loader.label} ${pack.version}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.UnfoldMore, "Выбрать сборку", tint = scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            model.instances.all.forEach { instance ->
                DropdownMenuItem(
                    text = { Text("${instance.name} · ${instance.loader.label} ${instance.version}") },
                    leadingIcon = { PackTile(instance, 32.dp) },
                    onClick = { onPick(instance); open = false },
                )
            }
        }
    }
}

@Composable
internal fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

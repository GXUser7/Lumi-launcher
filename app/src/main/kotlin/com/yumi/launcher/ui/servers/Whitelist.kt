package com.yumi.launcher.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ServerAuth
import com.yumi.launcher.data.ServerProfile
import com.yumi.launcher.data.ServerSession
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.data.WhitelistEntry
import com.yumi.launcher.data.active
import com.yumi.launcher.ui.components.Avatar
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.theme.glassContainer
import com.yumi.launcher.ui.theme.glassInnerBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who may join: the switch that makes the server let in only the players on its list, a field to
 * add one by nick, one-tap chips for whoever is on the server right now, the player's friends and
 * the player themselves, and the list with a button to take each off. Read from `whitelist.json`
 * and re-read whenever it changes, so `whitelist add` typed in the terminal shows up here too.
 */
@Composable
internal fun Whitelist(model: LauncherModel, profile: ServerProfile, session: ServerSession) {
    val manager = model.servers
    val scope = rememberCoroutineScope()
    val file = remember(profile.id) { manager.whitelistFile(profile) }
    val props = remember(profile.id) { manager.folder(profile).resolve("server.properties") }
    var stamp by remember(profile.id) { mutableLongStateOf(-1L) }
    var refresh by remember(profile.id) { mutableIntStateOf(0) }
    // A running server writes both files itself a moment after a command; watching them is simpler than guessing when.
    LaunchedEffect(profile.id) {
        while (true) {
            val modified = withContext(Dispatchers.IO) { file.lastModified() + props.lastModified() }
            if (modified != stamp) stamp = modified
            delay(1000)
        }
    }
    val entries by produceState<List<WhitelistEntry>?>(null, profile.id, stamp, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { manager.whitelist(profile) }.getOrDefault(emptyList()) }
    }
    val on by produceState(false, profile.id, stamp, refresh) {
        value = withContext(Dispatchers.IO) { manager.whitelistOn(profile) }
    }
    val running = session.status is ServerStatus.Running
    val busy = session.status.active && !running
    var nick by remember(profile.id) { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listed = entries.orEmpty().map { it.name.lowercase() }.toSet()

    val add: (String) -> Unit = add@{ name ->
        val clean = name.trim()
        if (!NICK.matches(clean)) {
            error = "Ник — 3–16 символов: латиница, цифры и _"
            return@add
        }
        if (clean.lowercase() in listed) {
            error = "«$clean» уже в списке"
            return@add
        }
        adding = true
        error = null
        scope.launch {
            try {
                manager.whitelistAdd(profile, clean)
                nick = ""
            } catch (e: Exception) {
                error = e.message
            } finally {
                adding = false
                refresh++
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (on) scheme.primaryContainer else glassContainer(),
            contentColor = if (on) scheme.onPrimaryContainer else scheme.onSurface,
            border = if (on) null else glassInnerBorder(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (on) "Белый список включён" else "Белый список выключен", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            busy -> "Сервер запускается или выключается — поменять можно чуть позже."
                            on -> "Зайти могут только игроки из списка ниже."
                            else -> "Зайти может любой, кто знает адрес. Включите, чтобы пускать только своих."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = on, onCheckedChange = { manager.setWhitelist(profile, it); refresh++ }, enabled = !busy)
            }
        }

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                value = nick,
                onValueChange = { nick = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16); error = null },
                label = "Ник игрока",
                placeholder = "Например, Steve",
                icon = Icons.Rounded.PersonAdd,
                isError = error != null,
                supporting = error ?: when (profile.auth) {
                    ServerAuth.Offline -> "Ник как в игре — у друзей без лицензии он любой."
                    ServerAuth.ElyBy -> "Ник аккаунта Ely.by — лаунчер проверит, что он есть."
                    ServerAuth.Mojang -> "Ник лицензии — лаунчер проверит, что он есть."
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(nick) }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { add(nick) },
                enabled = nick.isNotBlank() && !adding && !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(56.dp),
            ) {
                if (adding) LoadingIndicator(Modifier.size(20.dp), color = scheme.onPrimary) else Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Добавить")
            }
        }

        // One tap for the people most likely to be added: who is on now, friends, the player.
        val self = model.playerName.takeIf { NICK.matches(it) }
        val online = session.players.filter { it.lowercase() !in listed }
        val friends = model.friends.view?.friends.orEmpty().map { it.name }.filter { NICK.matches(it) && it.lowercase() !in listed && it !in online }
        val suggestions = buildList {
            if (self != null && self.lowercase() !in listed) add(QuickAdd(self, "Вы", Icons.Rounded.VerifiedUser))
            online.forEach { add(QuickAdd(it, "на сервере", Icons.Rounded.Wifi)) }
            friends.forEach { add(QuickAdd(it, "друг", Icons.Rounded.Group)) }
        }
        AnimatedVisibility(suggestions.isNotEmpty() && !busy, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("Быстро добавить")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        AssistChip(
                            onClick = { add(s.name) },
                            enabled = !adding,
                            label = { Text("${s.name} · ${s.who}", maxLines = 1) },
                            leadingIcon = { Icon(s.icon, null, Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        val list = entries
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(if (list.isNullOrEmpty()) "В списке никого" else "В списке · ${list.size}", Modifier.weight(1f))
            if (running) Hint("Изменения сервер применяет сразу")
        }
        when {
            list == null -> Hint("Читаем список…")
            list.isEmpty() -> EmptyState(
                Icons.Rounded.VerifiedUser,
                "Список пуст",
                if (on) "Сейчас на сервер не зайдёт никто — добавьте себя и друзей." else "Добавьте игроков, а потом включите белый список.",
            )
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.name.lowercase() }) { entry ->
                    WhitelistRow(entry, online = entry.name in session.players, canRemove = !busy) {
                        manager.whitelistRemove(profile, entry.name)
                        refresh++
                    }
                }
            }
        }
    }
}

private class QuickAdd(val name: String, val who: String, val icon: ImageVector)

@Composable
private fun WhitelistRow(entry: WhitelistEntry, online: Boolean, canRemove: Boolean, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = glassContainer(), border = glassInnerBorder(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(offline = true, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.uuid.isNotBlank()) Text(entry.uuid, style = MaterialTheme.typography.bodySmall, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (online) InfoChip("В игре", icon = Icons.Rounded.Wifi, container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
            IconButton(onClick = onRemove, enabled = canRemove) { Icon(Icons.Rounded.PersonRemove, "Убрать «${entry.name}» из списка", tint = scheme.onSurfaceVariant) }
        }
    }
}

private val NICK = Regex("^[A-Za-z0-9_]{3,16}$")

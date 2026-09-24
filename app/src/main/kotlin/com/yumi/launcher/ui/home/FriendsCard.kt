package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Friend
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PackCheck
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.SkinHead
import com.yumi.launcher.ui.components.Tip
import com.yumi.launcher.ui.servers.Online
import com.yumi.launcher.ui.theme.glass

/**
 * Friends on the home screen, in the corner above the news: who is on, what they play and where,
 * with "Зайти к другу" for a friend on a server. Before there is a profile, the card offers to make
 * one — nothing is sent anywhere until then. Folds down to its header, like the news.
 */
@Composable
internal fun FriendsCard(
    model: LauncherModel,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
    onJoin: (Instance?, String) -> Unit,
    onServerPack: (ServerPackTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val friends = model.friends
    // Back on the home screen: the hosts may have changed their mods, the player their packs.
    LaunchedEffect(Unit) { friends.servers.refresh() }
    Column(modifier.width(CARD), horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = Color.Transparent, modifier = Modifier.glass(CircleShape)) {
                Row(Modifier.height(40.dp).padding(start = 14.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Group, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Друзья", style = MaterialTheme.typography.titleSmall)
                    if (friends.online > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(Online, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text("${friends.online} в сети", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val label = if (hidden) "Показать друзей" else "Скрыть друзей"
            val turn by animateFloatAsState(if (hidden) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 300f))
            Tip(label) {
                IconButton(
                    onClick = { onHiddenChange(!hidden) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(40.dp).glass(CircleShape),
                ) { Icon(Icons.Rounded.KeyboardArrowDown, label, Modifier.rotate(turn)) }
            }
        }
        AnimatedVisibility(
            !hidden,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 300f), expandFrom = Alignment.Top) + fadeIn(tween(250, delayMillis = 80)),
            exit = shrinkVertically(tween(260), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.padding(top = 10.dp).fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !friends.available -> {
                        Text("Друзья появятся, когда подключится сервер друзей.", style = MaterialTheme.typography.bodyMedium)
                        Hint("Его адрес — в «Настройках», раздел «Друзья».")
                    }
                    !friends.signedIn -> SignUp(model)
                    else -> FriendsList(model, onJoin, onServerPack)
                }
            }
        }
    }
}

private val CARD = 320.dp

/** The first visit: a name that is only yours, the player's nick to begin with. */
@Composable
private fun SignUp(model: LauncherModel) {
    val friends = model.friends
    var name by remember { mutableStateOf(model.settings.nick.takeIf { NAME.matches(it) } ?: "") }
    val valid = NAME.matches(name)
    Text("Добавляйте друзей и смотрите, кто в сети, во что играет и на каком сервере.", style = MaterialTheme.typography.bodyMedium)
    Field(
        value = name,
        onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
        placeholder = "Ваше имя для друзей",
        icon = Icons.Rounded.Person,
        supporting = "По нему вас найдут: 3–16 символов, латиница, цифры и _",
        isError = name.isNotEmpty() && !valid,
        modifier = Modifier.fillMaxWidth(),
    )
    friends.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { friends.register(name) },
        enabled = valid && !friends.working,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        if (friends.working) LoadingIndicator(Modifier.size(24.dp)) else Text("Создать профиль")
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FriendsList(model: LauncherModel, onJoin: (Instance?, String) -> Unit, onServerPack: (ServerPackTarget) -> Unit) {
    val friends = model.friends
    val scheme = MaterialTheme.colorScheme
    val view = friends.view
    val clipboard = LocalClipboardManager.current
    var adding by remember { mutableStateOf(false) }
    var friendName by remember { mutableStateOf("") }
    val me = view?.me?.name ?: model.settings.friendsName

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ваше имя", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(me, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Tip("Скопировать имя — друг добавит вас по нему") {
            IconButton(onClick = { clipboard.setText(AnnotatedString(me)) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.ContentCopy, "Скопировать имя", Modifier.size(20.dp)) }
        }
        Tip("Добавить друга") {
            FilledTonalIconButton(onClick = { adding = !adding }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.PersonAdd, "Добавить друга") }
        }
    }
    if (adding) {
        val send = {
            if (NAME.matches(friendName)) {
                friends.add(friendName)
                friendName = ""
                adding = false
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                value = friendName,
                onValueChange = { friendName = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
                placeholder = "Имя друга",
                icon = Icons.Rounded.Person,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = send, enabled = NAME.matches(friendName) && !friends.working, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Check, "Отправить заявку")
            }
        }
    }
    friends.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
    if (view == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Hint("Загружаем друзей…")
        }
        return
    }
    for (name in view.incoming) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkinHead(friends.skin(name), 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Хочет дружить", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            }
            Tip("Принять") {
                FilledIconButton(onClick = { friends.accept(name) }, enabled = !friends.working, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Check, "Принять", Modifier.size(20.dp))
                }
            }
            Tip("Отклонить") {
                IconButton(onClick = { friends.decline(name) }, enabled = !friends.working, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, "Отклонить", Modifier.size(20.dp))
                }
            }
        }
    }
    if (view.friends.isNotEmpty()) {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(view.friends, key = { it.name }) { friend -> FriendRow(model, friend, onJoin, onServerPack) }
        }
    }
    for (name in view.outgoing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$name — ждём ответа", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Tip("Отменить заявку") {
                IconButton(onClick = { friends.remove(name) }, enabled = !friends.working, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, "Отменить заявку", Modifier.size(16.dp))
                }
            }
        }
    }
    if (view.friends.isEmpty() && view.incoming.isEmpty() && view.outgoing.isEmpty()) {
        Hint("Пока никого. Нажмите на человечка с плюсом и введите имя друга — ему придёт заявка.")
    }
}

@Composable
private fun FriendRow(model: LauncherModel, friend: Friend, onJoin: (Instance?, String) -> Unit, onServerPack: (ServerPackTarget) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val server = friend.server
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            SkinHead(model.friends.skin(friend.name), 36.dp)
            val dot = when {
                friend.online && friend.state == "game" -> Online
                friend.online -> scheme.primary
                else -> scheme.outline
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp).background(dot, CircleShape).border(2.dp, scheme.surface, CircleShape),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status(friend), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (server != null) {
            Tip("Зайти к другу на $server") {
                FilledTonalIconButton(
                    onClick = { join(model, friend, server, onJoin, onServerPack) },
                    enabled = !model.busy,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.AutoMirrored.Rounded.Login, "Зайти к другу", Modifier.size(20.dp)) }
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "Ещё", Modifier.size(18.dp)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text("Удалить из друзей") },
                    leadingIcon = { Icon(Icons.Outlined.PersonRemove, null) },
                    onClick = { menu = false; model.friends.remove(friend.name) },
                )
            }
        }
    }
}

/**
 * "Зайти к другу": straight in, unless the friend is on a server run from Yumi Launcher whose pack
 * no pack here has — or that is still being checked. Then the choice comes first: download the
 * host's pack as a pack of its own, or go in as things are.
 */
private fun join(model: LauncherModel, friend: Friend, server: String, onJoin: (Instance?, String) -> Unit, onServerPack: (ServerPackTarget) -> Unit) {
    when (model.friends.servers.checks[server]) {
        null, is PackCheck.Ready -> onJoin(packFor(model, friend), server)
        else -> onServerPack(ServerPackTarget.Friend(friend.name, server, friend.pack?.name ?: "Сервер ${friend.name}", packFor(model, friend)?.id))
    }
}

/** "Играет в «Выживание» на play.example.net", "В лаунчере", "Не в сети · 5 мин назад". */
private fun status(friend: Friend): String {
    if (!friend.online) return "Не в сети" + if (friend.seen > 0) " · ${ago(friend.seen)}" else ""
    if (friend.state != "game") return "В лаунчере"
    val pack = friend.pack
    val what = pack?.name?.let { "Играет в «$it»" } ?: "В игре"
    return when {
        friend.server != null -> "$what на ${friend.server}"
        pack?.version != null -> "$what · ${listOfNotNull(pack.loader, pack.version).joinToString(" ")}"
        else -> what
    }
}

private fun ago(seen: Long): String {
    val minutes = (System.currentTimeMillis() / 1000 - seen) / 60
    return when {
        minutes < 2 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 60 * 24 -> "${minutes / 60} ч назад"
        else -> "${minutes / (60 * 24)} дн назад"
    }
}

/**
 * The pack to join a friend with: the one that has their Yumi server's pack, then one like theirs —
 * same name and version, or the same game — or the chosen one.
 */
private fun packFor(model: LauncherModel, friend: Friend): Instance? {
    val checked = friend.server?.let { model.friends.servers.checks[it] } as? PackCheck.Ready
    checked?.let { model.instances[it.packId] }?.let { return it }
    val pack = friend.pack
    val all = model.instances.all
    return all.firstOrNull { pack != null && it.name == pack.name && it.version == pack.version }
        ?: all.firstOrNull { pack != null && it.version == pack.version && it.loader.label == pack.loader }
        ?: model.selected
}

private val NAME = Regex("^[A-Za-z0-9_]{3,16}$")

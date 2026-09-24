package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PlayStatus
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.PlayButton
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.SkinHead
import com.yumi.launcher.ui.components.choiceColors
import com.yumi.launcher.ui.components.connectedShapes
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(model: LauncherModel, state: LauncherState, onPlay: () -> Unit) {
    val settings = model.settings
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Добро пожаловать в Yumi", style = type.titleSmall, color = scheme.onSurfaceVariant)
        Text(
            buildAnnotatedString {
                append("Привет,\n")
                withStyle(SpanStyle(color = scheme.primary)) { append(model.playerName.ifBlank { "игрок" }) }
            },
            style = type.displayMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Label("Аккаунт")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween)) {
            val kinds = AccountKind.entries
            kinds.forEachIndexed { i, kind ->
                val checked = settings.account == kind
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { settings.account = kind; model.dismissError() },
                    shapes = connectedShapes(i, kinds.size),
                    colors = choiceColors(),
                    modifier = Modifier.weight(1f).height(48.dp).semantics { role = Role.RadioButton },
                ) {
                    AnimatedVisibility(checked) {
                        Row {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Text(kind.label, style = type.labelLarge)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(80.dp)) {
            AnimatedContent(settings.account, transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(80)) }) { kind ->
                when (kind) {
                    AccountKind.Offline -> NickField(model)
                    AccountKind.Microsoft -> MicrosoftRow(model, onSignIn = { state.microsoftSignIn = true })
                    AccountKind.ElyBy -> ElyRow(model, onSignIn = { state.elySignIn = true })
                }
            }
        }
        Label("Сборка")
        PackSelector(model, state)
        Spacer(Modifier.weight(1f))
        PlayRow(model, state, onPlay)
    }
}

@Composable
private fun NickField(model: LauncherModel) {
    val settings = model.settings
    val scheme = MaterialTheme.colorScheme
    val valid = settings.nickValid
    Field(
        value = settings.nick,
        onValueChange = { settings.nick = it.take(16); model.dismissError() },
        label = "Ник в игре",
        icon = Icons.Rounded.Person,
        isError = settings.nick.isNotEmpty() && !valid,
        supporting = if (settings.nick.isEmpty() || valid) "Под этим ником вас увидят друзья" else "Только латиница, цифры и _, от 3 до 16 символов",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ElyRow(model: LauncherModel, onSignIn: () -> Unit) {
    val session = model.ely.session
    AccountRow(
        signedIn = session != null,
        skin = model.ely.skin,
        title = session?.name ?: "Аккаунт Ely.by",
        subtitle = if (session != null) "Вход выполнен · скин с ely.by" else "Свой скин и ник, который не займут",
        busy = model.busy,
        onSignIn = onSignIn,
        onSignOut = model.ely::signOut,
    )
}

@Composable
private fun MicrosoftRow(model: LauncherModel, onSignIn: () -> Unit) {
    val session = model.microsoft.session
    AccountRow(
        signedIn = session != null,
        skin = session?.skin,
        title = session?.name ?: "Аккаунт Microsoft",
        subtitle = if (session != null) "Лицензия · вход выполнен" else "Лицензия: любые серверы и Realms",
        busy = model.busy,
        onSignIn = onSignIn,
        onSignOut = model.microsoft::signOut,
    )
}

/** An account to sign in to: the player's face and name once signed in, a key and a pitch until then. */
@Composable
private fun AccountRow(
    signedIn: Boolean,
    skin: String?,
    title: String,
    subtitle: String,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (signedIn) SkinHead(skin, 48.dp) else ShapeTile(Icons.Rounded.Key, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (signedIn) {
                TextButton(onClick = onSignOut, enabled = !busy) { Text("Выйти") }
            } else {
                Button(onClick = onSignIn, shapes = ButtonDefaults.shapes()) { Text("Войти") }
            }
        }
    }
}

@Composable
private fun PackSelector(model: LauncherModel, state: LauncherState) {
    val scheme = MaterialTheme.colorScheme
    val selected = model.selected
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (selected == null) state.openPacks(PackRequest.Create) else open = true },
            enabled = !model.busy,
            shape = RoundedCornerShape(22.dp),
            color = scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Row(Modifier.padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selected != null) PackTile(selected, 48.dp) else ShapeTile(Icons.Rounded.Add, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(selected?.name ?: "Создайте сборку", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        selected?.let { "${it.loader.label} ${it.version}" } ?: (model.manifestError ?: "Загружаем список версий…"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected != null) Icon(Icons.Rounded.UnfoldMore, "Выбрать сборку", tint = scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            model.instances.all.forEach { instance ->
                DropdownMenuItem(
                    text = { PackMenuText(instance) },
                    leadingIcon = { PackTile(instance, 36.dp) },
                    trailingIcon = { if (instance.id == selected?.id) Icon(Icons.Rounded.Check, null, tint = scheme.primary) },
                    onClick = { model.select(instance); open = false },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Новая сборка") },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { open = false; state.openPacks(PackRequest.Create) },
            )
            DropdownMenuItem(
                text = { Text("Все сборки") },
                leadingIcon = { Icon(Icons.Rounded.ViewInAr, null) },
                onClick = { open = false; state.openPacks() },
            )
        }
    }
}

@Composable
private fun PackMenuText(instance: Instance) {
    Column(Modifier.width(220.dp)) {
        Text(instance.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${instance.loader.label} ${instance.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayRow(model: LauncherModel, state: LauncherState, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val status = model.status
    val selected = model.selected
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (status is PlayStatus.Preparing) {
            LinearWavyProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val look = when (status) {
                is PlayStatus.Preparing -> PlayState.Launching
                is PlayStatus.Running -> PlayState.Running
                else -> PlayState.Idle
            }
            val title = when (status) {
                is PlayStatus.Preparing -> if (status.stage == com.yumi.launcher.core.Stage.Starting) "Запуск…" else "Загрузка"
                is PlayStatus.Running -> "В игре"
                else -> "Играть"
            }
            PlayButton(look, title, onClick = { if (model.busy) model.stop() else onPlay() })
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                when (status) {
                    is PlayStatus.Preparing -> Caption("${status.stage.label} · ${(status.fraction * 100).toInt()}%  ·  нажмите, чтобы отменить")
                    is PlayStatus.Running -> Caption("${elapsed(status.since)} · нажмите, чтобы остановить")
                    is PlayStatus.Failed -> Text(status.message, style = MaterialTheme.typography.bodySmall, color = scheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    PlayStatus.Idle -> Caption(selected?.let { lastPlayed(it) } ?: "Сначала создайте сборку")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val quiet = IconButtonDefaults.filledTonalIconButtonColors(containerColor = scheme.surfaceContainerHigh, contentColor = scheme.onSurfaceVariant)
                FilledTonalIconButton(
                    onClick = { selected?.let { model.openFolder(model.instances.gameDir(it)) } },
                    enabled = selected != null,
                    modifier = Modifier.size(48.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = quiet,
                ) { Icon(Icons.Outlined.FolderOpen, "Открыть папку сборки") }
                FilledTonalIconButton(
                    onClick = { state.section = Section.Console },
                    modifier = Modifier.size(48.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = quiet,
                ) { Icon(Icons.Outlined.Terminal, "Консоль") }
            }
        }
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)

/** "12:04" since [since], ticking. */
@Composable
private fun elapsed(since: Long): String {
    val now by produceState(System.currentTimeMillis(), since) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun lastPlayed(instance: Instance): String {
    val played = instance.lastPlayed ?: return "Minecraft ${instance.version} · ещё не запускалась"
    val hours = instance.playSeconds / 3600
    val minutes = instance.playSeconds / 60 % 60
    val time = if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
    val days = (System.currentTimeMillis() - played) / 86_400_000
    val ago = when (days) {
        0L -> "сегодня"
        1L -> "вчера"
        else -> "$days дн. назад"
    }
    return "Играли $ago · всего $time"
}

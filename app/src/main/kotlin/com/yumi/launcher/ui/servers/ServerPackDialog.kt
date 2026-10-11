package com.yumi.launcher.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.ModHints
import com.yumi.launcher.core.ServerPacks
import com.yumi.launcher.core.ServerPlan
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PackCheck
import com.yumi.launcher.data.PingState
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.components.PackIcons
import com.yumi.launcher.ui.home.ServerPackTarget
import com.yumi.launcher.ui.packs.ImportProgress
import com.yumi.launcher.ui.packs.countOf
import kotlin.math.absoluteValue

/**
 * A pack for a server, from what its ping gave away: the host's own pack when the server runs in
 * Lumi Launcher, mods found by their ids for Forge, only the game version and loader otherwise.
 * The server is one in the quick-join list — the new pack becomes the one it is joined with — or
 * the one a friend is on, when "Зайти к другу" found no pack here like the host's: then the pack
 * is downloaded and the game goes straight in, or goes in as things are ([onJoin]).
 */
@Composable
internal fun ServerPackDialog(model: LauncherModel, target: ServerPackTarget, onJoin: (Instance?, String) -> Unit, onClose: () -> Unit) {
    val imports = model.packImports
    when (target) {
        is ServerPackTarget.Saved -> {
            val server = model.quickJoin[target.id]
            if (server == null) {
                LaunchedEffect(Unit) { onClose() }
                return
            }
            PackForServer(
                model,
                target,
                title = "Сборка под «${server.name}»",
                about = null,
                ping = model.quickJoin.pings[server.id],
                offline = "Сервер сейчас не отвечает — обновите список и попробуйте ещё раз",
                origin = "server:${server.id}",
                known = null,
                found = null,
                installLabel = "Создать сборку",
                anyway = null,
                onDone = { onClose() },
                onClose = onClose,
            ) { plan, origin ->
                imports.installForServer(server.name, origin, plan, iconFor(server.name)) { made ->
                    model.quickJoin[server.id]?.let { model.quickJoin.update(it.copy(packId = made.id)) }
                }
            }
        }
        is ServerPackTarget.Friend -> {
            val servers = model.friends.servers
            val check = servers.checks[target.address]
            val own = model.instances[target.packId]
            val about = if (check == PackCheck.Checking) {
                "${target.friend} играет на ${target.address}. Смотрим, есть ли у вас сборка как у хозяина этого сервера…"
            } else {
                "${target.friend} играет на ${target.address}, а сборки как у хозяина этого сервера у вас нет. " +
                    (own?.let { "Можно скачать её отдельной сборкой «${target.name}» или зайти так, с «${it.name}»." } ?: "Скачайте её отдельной сборкой «${target.name}».")
            }
            PackForServer(
                model,
                target,
                title = "Сборка для игры с ${target.friend}",
                about = about,
                ping = servers.pings[target.address],
                offline = "Сервер друга сейчас не отвечает — попробуйте чуть позже",
                origin = "friend:${target.address}",
                known = (check as? PackCheck.Missing)?.plan,
                found = (check as? PackCheck.Ready)?.let { model.instances[it.packId] },
                installLabel = "Скачать и зайти",
                anyway = own?.let { pack -> { onJoin(pack, target.address); onClose() } },
                onDone = { pack ->
                    onJoin(pack, target.address)
                    onClose()
                },
                onClose = onClose,
            ) { plan, origin -> imports.installForServer(target.name, origin, plan, iconFor(target.name)) }
        }
    }
}

private fun iconFor(name: String) = name.hashCode().absoluteValue % PackIcons.size

/**
 * [known] is a plan already made from this ping, as the friends card has one; [install] starts the
 * import under [origin], and [onDone] gets the pack it made — or [found], a pack here that turned
 * out to fit after all. [anyway], when there is one, is the way past without a new pack.
 */
@Composable
private fun PackForServer(
    model: LauncherModel,
    target: ServerPackTarget,
    title: String,
    about: String?,
    ping: PingState?,
    offline: String,
    origin: String,
    known: ServerPlan?,
    found: Instance?,
    installLabel: String,
    anyway: (() -> Unit)?,
    onDone: (Instance) -> Unit,
    onClose: () -> Unit,
    install: (ServerPlan, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val result = (ping as? PingState.Online)?.result
    val hints = result?.mods
    val imports = model.packImports
    var plan by remember(target) { mutableStateOf(known) }
    // What the plan was made from: a new ping that says the same about the mods needs no new plan.
    var madeFrom by remember(target) { mutableStateOf(if (known != null) hints else null) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var installing by remember(target) { mutableStateOf(false) }
    val finishedBefore = remember(target) { imports.finished }
    LaunchedEffect(target, hints, result == null) {
        if (result == null) {
            // Still on its way, or gone quiet: a plan already made still holds.
            if (plan == null && ping !is PingState.Loading) error = offline
            return@LaunchedEffect
        }
        if (plan != null && madeFrom == hints) return@LaunchedEffect
        plan = null
        error = null
        runCatching { ServerPacks.plan(result, model.lookup, model.settings.curseForgeKey) }
            .onSuccess {
                plan = it
                madeFrom = hints
            }
            .onFailure { error = it.message ?: "Не удалось разобраться, что нужно серверу" }
    }
    // Done once the pack this dialog started is in.
    LaunchedEffect(imports.finished) {
        val made = imports.finished
        if (installing && made != null && made != finishedBefore) onDone(made)
    }
    LaunchedEffect(found) {
        if (found != null && !installing) onDone(found)
    }
    ModalCard(width = 520.dp) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        about?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
        val ready = plan
        when {
            error != null -> {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Закрыть") }
                    if (anyway != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = anyway, shapes = ButtonDefaults.shapes()) { Text("Зайти так") }
                    }
                }
            }
            ready == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text("Смотрим, что нужно серверу…", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip("${ready.loader.label} ${ready.minecraft}")
                    if (ready.mods.isNotEmpty()) InfoChip(countOf(ready.mods.size, "мод", "мода", "модов"))
                }
                Text(explanation(ready, model.settings.curseForgeKey.isNotBlank()), style = MaterialTheme.typography.bodyMedium)
                val approximate = ready.mods.count { !it.exact }
                if (approximate > 0) {
                    Hint("У ${countOf(approximate, "мода", "модов", "модов")} точной версии не нашлось — будет новейшая для ${ready.minecraft}.")
                }
                if (ready.missing.isNotEmpty()) {
                    val shown = ready.missing.take(8).joinToString(", ")
                    val more = ready.missing.size - 8
                    val lead = if (ready.kind == ServerPlan.Kind.Yumi) "Этих нет ни на Modrinth, ни на CurseForge — попросите файлы у хозяина" else "Не нашлись"
                    Text(
                        "$lead: $shown" + if (more > 0) " и ещё $more" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                    if (ready.kind == ServerPlan.Kind.Forge && model.settings.curseForgeKey.isBlank()) {
                        Hint("С ключом CurseForge в настройках найдётся больше.")
                    }
                }
                if (imports.busy && imports.origin == origin) {
                    ImportProgress(model, Modifier.fillMaxWidth())
                } else {
                    if (installing) imports.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
                    if (imports.busy) Hint("Сейчас ставится другая сборка — дождитесь её.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) { Text("Отмена") }
                        if (anyway != null) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = anyway, shapes = ButtonDefaults.shapes()) { Text("Зайти так") }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                installing = true
                                imports.error = null
                                install(ready, origin)
                            },
                            enabled = !imports.busy,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(installLabel) }
                    }
                }
            }
        }
    }
}

private fun explanation(plan: ServerPlan, curseForge: Boolean): String = when (plan.kind) {
    ServerPlan.Kind.Yumi ->
        "Сервер запущен в Lumi Launcher и раздаёт свою сборку: версия, загрузчик и моды будут те же, что у хозяина."
    ServerPlan.Kind.Forge ->
        "Forge-сервер назвал свои моды — нашлось ${plan.mods.size} из ${plan.total} на Modrinth" + (if (curseForge) " и CurseForge." else ".")
    ServerPlan.Kind.Modded ->
        "Это NeoForge-сервер, но свои моды он не называет. Будет сборка NeoForge ${plan.minecraft} без модов — список узнайте у владельца."
    ServerPlan.Kind.Unknown ->
        "Сервер не сообщает, нужны ли моды. Будет сборка ${plan.minecraft} без модов — большинству таких серверов этого хватает."
}

/** The button under a quick-join server that makes a pack for it; brighter when the server shares its own. */
@Composable
internal fun ServerPackChip(hints: ModHints?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shared = hints is ModHints.Yumi
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (shared) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (shared) scheme.onTertiaryContainer else scheme.onSurface,
    ) {
        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Inventory2, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (shared) "Сборка сервера" else "Сборка под сервер", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

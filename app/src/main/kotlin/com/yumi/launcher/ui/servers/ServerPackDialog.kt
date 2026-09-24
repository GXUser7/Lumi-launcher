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
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PingState
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.components.PackIcons
import com.yumi.launcher.ui.packs.ImportProgress
import com.yumi.launcher.ui.packs.countOf
import kotlin.math.absoluteValue

/**
 * A pack for a server in the quick-join list, from what its ping gave away: the host's own pack
 * when the server runs in Yumi Launcher, mods found by their ids for Forge, only the game version
 * and loader otherwise. "Создать" installs it and makes it the pack that server is joined with.
 */
@Composable
internal fun ServerPackDialog(model: LauncherModel, serverId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val server = model.quickJoin[serverId]
    val result = (model.quickJoin.pings[serverId] as? PingState.Online)?.result
    val imports = model.packImports
    var plan by remember(serverId) { mutableStateOf<ServerPlan?>(null) }
    var error by remember(serverId) { mutableStateOf<String?>(null) }
    var installing by remember(serverId) { mutableStateOf(false) }
    val finishedBefore = remember(serverId) { imports.finished }
    LaunchedEffect(serverId, result) {
        plan = null
        error = null
        if (result == null) {
            error = "Сервер сейчас не отвечает — обновите список и попробуйте ещё раз"
            return@LaunchedEffect
        }
        runCatching { ServerPacks.plan(result, model.lookup, model.settings.curseForgeKey) }
            .onSuccess { plan = it }
            .onFailure { error = it.message ?: "Не удалось разобраться, что нужно серверу" }
    }
    // Done once the pack this dialog started is in; it is already the server's pack by then.
    LaunchedEffect(imports.finished) {
        if (installing && imports.finished != null && imports.finished != finishedBefore) onClose()
    }
    if (server == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    ModalCard(width = 520.dp) {
        Text("Сборка под «${server.name}»", style = MaterialTheme.typography.headlineSmall)
        val ready = plan
        when {
            error != null -> {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onClose) { Text("Закрыть") } }
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
                if (imports.busy && imports.origin == "server:$serverId") {
                    ImportProgress(model, Modifier.fillMaxWidth())
                } else {
                    if (installing) imports.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
                    if (imports.busy) Hint("Сейчас ставится другая сборка — дождитесь её.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                installing = true
                                imports.error = null
                                imports.installForServer(server, ready, server.name.hashCode().absoluteValue % PackIcons.size)
                            },
                            enabled = !imports.busy,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Создать сборку") }
                    }
                }
            }
        }
    }
}

private fun explanation(plan: ServerPlan, curseForge: Boolean): String = when (plan.kind) {
    ServerPlan.Kind.Yumi ->
        "Сервер запущен в Yumi Launcher и раздаёт свою сборку: версия, загрузчик и моды будут те же, что у хозяина."
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

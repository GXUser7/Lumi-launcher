package com.yumi.launcher.ui.servers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.PingResult
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PingState
import com.yumi.launcher.data.SavedServer
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.Tip
import org.jetbrains.skia.Image as SkiaImage

/** Other people's servers: add by address, see who is on, join with a pack in one click. */
@Composable
internal fun QuickJoinPane(model: LauncherModel, onJoin: (Instance?, String) -> Unit) {
    val list = model.quickJoin
    var address by remember { mutableStateOf("") }
    val add = {
        if (address.isNotBlank()) {
            list.add(address, "", model.selected?.id)
            address = ""
        }
    }
    LaunchedEffect(Unit) { list.pingAll() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(
            value = address,
            onValueChange = { address = it.trim().take(120) },
            placeholder = "Адрес сервера: play.example.net",
            icon = Icons.Rounded.Dns,
            shape = CircleShape,
            modifier = Modifier.weight(1f).onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                    add()
                    true
                } else false
            },
        )
        Button(onClick = add, enabled = address.isNotBlank(), shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Добавить")
        }
        Tip("Обновить все") {
            FilledTonalIconButton(onClick = list::pingAll, enabled = list.all.isNotEmpty(), shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, "Обновить все")
            }
        }
    }
    if (list.all.isEmpty()) {
        Hint(
            "Добавьте сервер друзей или любимый публичный — лаунчер покажет, кто онлайн и какой там пинг, " +
                "и зайдёт на него прямо отсюда, без поиска в меню игры.",
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.all, key = { it.id }) { server ->
            SavedServerRow(model, server, list.pings[server.id], onJoin)
        }
    }
}

@Composable
private fun SavedServerRow(model: LauncherModel, server: SavedServer, ping: PingState?, onJoin: (Instance?, String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val list = model.quickJoin
    val pack = model.instances[server.packId] ?: model.selected
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(server.name) { mutableStateOf(server.name) }
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Favicon((ping as? PingState.Online)?.result?.favicon, 52.dp)
                Column(Modifier.weight(1f)) {
                    if (renaming) {
                        Field(
                            value = draft,
                            onValueChange = { draft = it.take(40) },
                            placeholder = "Название",
                            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                                    list.update(server.copy(name = draft.trim().ifEmpty { server.address }))
                                    renaming = false
                                    true
                                } else false
                            },
                        )
                    } else {
                        Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(server.address, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                PingBadge(ping)
                FilledTonalIconButton(
                    onClick = { onJoin(pack, server.address) },
                    enabled = pack != null && !model.busy,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                ) { Icon(Icons.Rounded.PlayArrow, "Играть на «${server.name}»") }
            }
            if (ping is PingState.Online && ping.result.motd.isNotEmpty()) MotdStrip(ping.result.motd)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackChip(model, pack) { list.update(server.copy(packId = it.id)) }
                versionMismatch(ping, pack)?.let { warning ->
                    Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp), tint = scheme.error)
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = scheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } ?: Spacer(Modifier.weight(1f))
                RowMenu(
                    onRename = {
                        draft = server.name
                        renaming = true
                    },
                    onDelete = { list.remove(server) },
                )
            }
        }
    }
}

/** The server's own 64×64 icon, pixel-sharp; a generic tile for servers without one. */
@Composable
private fun Favicon(bytes: ByteArray?, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(bytes) { bytes?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() } }
    if (bitmap == null) {
        ShapeTile(Icons.Rounded.Dns, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, size)
    } else {
        Image(bitmap, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)), filterQuality = FilterQuality.None)
    }
}

/** Players and signal bars, the way the multiplayer list shows them. */
@Composable
private fun PingBadge(ping: PingState?) {
    val scheme = MaterialTheme.colorScheme
    when (ping) {
        null, PingState.Loading -> LoadingIndicator(Modifier.size(28.dp))
        is PingState.Offline -> Text(ping.message, style = MaterialTheme.typography.labelMedium, color = scheme.error, maxLines = 2, modifier = Modifier.width(110.dp))
        is PingState.Online -> Column(horizontalAlignment = Alignment.End) {
            Text("${ping.result.online}/${ping.result.max}", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ping.result.latencyMs} мс", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                SignalBars(ping.result.latencyMs)
            }
        }
    }
}

@Composable
private fun SignalBars(latency: Long) {
    val filled = when {
        latency < 150 -> 5
        latency < 300 -> 4
        latency < 600 -> 3
        latency < 1000 -> 2
        else -> 1
    }
    val on = if (filled >= 3) Online else MaterialTheme.colorScheme.error
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
        val bar = size.width / 5
        for (i in 0 until 5) {
            val h = size.height * (i + 1) / 5
            drawRoundRect(
                if (i < filled) on else off,
                topLeft = Offset(i * bar + bar * 0.15f, size.height - h),
                size = Size(bar * 0.7f, h),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }
    }
}

@Composable
private fun PackChip(model: LauncherModel, pack: Instance?, onPick: (Instance) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pack != null) PackTile(pack.icon, 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(pack?.let { "${it.name} · ${it.version}" } ?: "Нет сборки", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Icon(Icons.Rounded.UnfoldMore, "Сборка для входа", Modifier.size(16.dp))
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            Text("Заходить со сборкой", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            HorizontalDivider()
            model.instances.all.forEach { instance ->
                DropdownMenuItem(
                    text = { Text("${instance.name} · ${instance.loader.label} ${instance.version}") },
                    leadingIcon = { PackTile(instance.icon, 28.dp) },
                    onClick = { onPick(instance); open = false },
                )
            }
        }
    }
}

@Composable
private fun RowMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "Ещё", Modifier.size(20.dp)) }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { open = false; onDelete() })
        }
    }
}

/**
 * "Сервер на 1.20.1" when the server names exactly one version and the pack is on another. Servers
 * that list a range ("1.8–1.21") take many versions, so they get no warning.
 */
private fun versionMismatch(ping: PingState?, pack: Instance?): String? {
    val result: PingResult = (ping as? PingState.Online)?.result ?: return null
    if (pack == null) return null
    val versions = Regex("""\b\d+\.\d+(\.\d+)?\b""").findAll(result.version).map { it.value }.toList()
    if (versions.size != 1 || versions[0] == pack.version) return null
    return "Сервер на ${versions[0]}, сборка — ${pack.version}"
}

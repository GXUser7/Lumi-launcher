package com.yumi.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PlayStatus
import com.yumi.launcher.data.ServerStatus
import com.yumi.launcher.data.active
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.theme.PopGlassAlpha
import com.yumi.launcher.ui.theme.glass
import com.yumi.launcher.ui.theme.glassContainer
import com.yumi.launcher.ui.theme.glassInnerBorder

private class NotificationEntry(
    val id: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val isError: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * Top bar notifications bell: aggregates real launcher events (running or failed servers, launch
 * errors, finished/failed downloads, skipped resource files) and marks them seen when opened.
 */
@Composable
fun NotificationsButton(
    model: LauncherModel,
    state: LauncherState,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val entries = collectNotifications(model, state) { onOpenChange(false) }
    var seenIds by remember { mutableStateOf(emptySet<String>()) }
    val unread = entries.count { it.id !in seenIds }

    Box(modifier) {
        IconButton(
            onClick = {
                val next = !open
                if (next) seenIds = entries.mapTo(HashSet()) { it.id }
                onOpenChange(next)
            },
            modifier = Modifier.size(44.dp),
            colors = if (open) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors(),
        ) {
            BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                Icon(Icons.Outlined.Notifications, "Уведомления")
            }
        }
        if (open) {
            val below = with(LocalDensity.current) { 56.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, below),
                onDismissRequest = { onOpenChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                NotificationsPanel(
                    entries = entries,
                    onClear = {
                        model.downloads.clear()
                        model.packImports.skipped = emptyList()
                        seenIds = emptySet()
                    },
                    hasClearable = model.downloads.recent.isNotEmpty() || model.packImports.skipped.isNotEmpty(),
                    schemeError = scheme.error,
                )
            }
        }
    }
}

@Composable
private fun collectNotifications(
    model: LauncherModel,
    state: LauncherState,
    close: () -> Unit,
): List<NotificationEntry> = buildList {
    val failedPlay = model.status as? PlayStatus.Failed
    if (failedPlay != null) {
        add(
            NotificationEntry(
                id = "play-fail:${failedPlay.message}",
                title = "Не удалось запустить игру",
                detail = failedPlay.message,
                icon = Icons.Rounded.ErrorOutline,
                isError = true,
                onClick = {
                    state.section = Section.Console
                    close()
                },
            ),
        )
    }
    for (server in model.servers.all) {
        val status = model.servers.session(server.id).status
        when {
            status is ServerStatus.Failed -> add(
                NotificationEntry(
                    id = "srv-fail:${server.id}:${status.message}",
                    title = "Сервер «${server.name}» остановился",
                    detail = status.message,
                    icon = Icons.Rounded.WarningAmber,
                    isError = true,
                    onClick = {
                        state.section = Section.Servers
                        state.serversTab = ServersTab.Hosted
                        state.serverView = ServerView.Detail(server.id)
                        close()
                    },
                ),
            )
            status.active -> add(
                NotificationEntry(
                    id = "srv-run:${server.id}",
                    title = "Сервер «${server.name}» работает",
                    detail = "Порт ${model.servers.port(server)} · нажмите, чтобы открыть консоль",
                    icon = Icons.Rounded.Dns,
                    onClick = {
                        state.section = Section.Servers
                        state.serversTab = ServersTab.Hosted
                        state.serverView = ServerView.Detail(server.id)
                        close()
                    },
                ),
            )
        }
    }
    if (model.packImports.skipped.isNotEmpty()) {
        val count = model.packImports.skipped.size
        add(
            NotificationEntry(
                id = "skipped:$count",
                title = "Пропущено файлов при импорте: $count",
                detail = "Некоторые авторы CurseForge запрещают автозагрузку — откройте раздел сборок.",
                icon = Icons.Rounded.WarningAmber,
                onClick = {
                    state.openPacks()
                    close()
                },
            ),
        )
    }
    model.downloads.recent.take(6).forEach { item ->
        add(
            NotificationEntry(
                id = "dl:${item.at}:${item.title}:${item.error != null}",
                title = item.title,
                detail = item.error ?: "${item.what.replaceFirstChar { it.uppercase() }} · установлено",
                icon = if (item.error != null) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                isError = item.error != null,
            ),
        )
    }
}

@Composable
private fun NotificationsPanel(
    entries: List<NotificationEntry>,
    onClear: () -> Unit,
    hasClearable: Boolean,
    schemeError: Color,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(380.dp).glass(RoundedCornerShape(30.dp), alpha = PopGlassAlpha, elevation = 20.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Уведомления", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (hasClearable) TextButton(onClick = onClear) { Text("Очистить") }
        }
        if (entries.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShapeTile(Icons.Rounded.NotificationsNone, scheme.secondaryContainer, scheme.onSecondaryContainer, 44.dp)
                Column {
                    Text("Всё спокойно", style = MaterialTheme.typography.titleSmall)
                    Hint("Здесь появятся события запуска игры, серверов и загрузок.")
                }
            }
        } else {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { entry ->
                    Surface(
                        onClick = { entry.onClick?.invoke() },
                        enabled = entry.onClick != null,
                        shape = RoundedCornerShape(20.dp),
                        color = if (entry.isError) scheme.errorContainer.copy(alpha = 0.86f) else glassContainer(elevated = true),
                        border = if (entry.isError) null else glassInnerBorder(elevated = true),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShapeTile(
                                entry.icon,
                                if (entry.isError) schemeError else scheme.primaryContainer,
                                if (entry.isError) scheme.onError else scheme.onPrimaryContainer,
                                36.dp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (entry.isError) scheme.onErrorContainer else scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    entry.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (entry.isError) scheme.onErrorContainer.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

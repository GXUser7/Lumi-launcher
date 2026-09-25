package com.yumi.launcher.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.PlayStatus
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** The running game's output — the first place to look when a pack will not start. */
@Composable
fun ConsoleScreen(model: LauncherModel) {
    val scheme = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    LaunchedEffect(model.log.size) { if (model.log.isNotEmpty()) list.scrollToItem(model.log.lastIndex) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Консоль", style = MaterialTheme.typography.displaySmall)
                Text(
                    when (val status = model.status) {
                        is PlayStatus.Running -> "Игра запущена"
                        is PlayStatus.Preparing -> "Готовим запуск: ${status.stage.label.lowercase()}"
                        is PlayStatus.Failed -> status.message
                        PlayStatus.Idle -> if (model.log.isEmpty()) "Игра ещё не запускалась" else "Последний запуск"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.status is PlayStatus.Failed) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            TextButton(onClick = { model.feedback.open = true; model.feedback.sendLog() }) {
                Icon(Icons.Outlined.BugReport, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Отправить лог")
            }
            IconButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(model.log.joinToString("\n")), null) }) {
                Icon(Icons.Rounded.ContentCopy, "Скопировать лог")
            }
            IconButton(onClick = { model.logFile?.parentFile?.let(model::openFolder) }, enabled = model.logFile != null) {
                Icon(Icons.Outlined.FolderOpen, "Папка логов")
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (model.log.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("Здесь появится вывод игры, когда она запустится.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            } else {
                SelectionContainer {
                    LazyColumn(state = list, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        itemsIndexed(model.log) { _, line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = when {
                                    "ERROR" in line || "Exception" in line -> scheme.error
                                    "WARN" in line -> scheme.secondary
                                    else -> scheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Support
import com.yumi.launcher.data.Feedback
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.components.ShapeTile

/**
 * Feedback over the window: write to the developer, or send the logs. The logs go the way a
 * player would send any file — the launcher packs them, puts the zip on the clipboard and opens the
 * chat, and the player pastes it there with Ctrl+V.
 */
@Composable
fun FeedbackCard(feedback: Feedback) {
    val scheme = MaterialTheme.colorScheme
    ModalCard(width = 520.dp) {
        Text("Обратная связь", style = MaterialTheme.typography.headlineLarge)
        Hint("Нашли ошибку, придумали что-то или просто хотите сказать спасибо — пишите разработчику в Telegram, @${Support.TELEGRAM}.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Action(
                Icons.Outlined.Chat, "Написать", "идея, вопрос, ошибка",
                scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f),
                onClick = feedback::openChat,
            )
            Action(
                Icons.Outlined.BugReport, "Отправить лог", "если игра или лаунчер упали",
                scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f),
                busy = feedback.packing,
                onClick = feedback::sendLog,
            )
        }
        val bundle = feedback.bundle
        AnimatedVisibility(bundle != null && !feedback.packing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(shape = RoundedCornerShape(22.dp), color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (feedback.copied) "Лог собран и скопирован" else "Лог собран",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        if (feedback.copied) "В чате с @${Support.TELEGRAM} нажмите Ctrl+V и отправьте файл. Если Telegram не открылся — откройте чат сами."
                        else "Перетащите файл ${bundle?.name.orEmpty()} в чат с @${Support.TELEGRAM}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = feedback::reveal, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Показать файл", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = feedback::openChat, shapes = ButtonDefaults.shapes()) { Text("Открыть чат") }
                    }
                }
            }
        }
        feedback.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Hint(
                "В архив попадают логи последней игры, отчёт о сбое, лог лаунчера и сведения о компьютере. Токены, ключи и имя пользователя Windows из них вырезаны.",
                Modifier.weight(1f),
            )
        }
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = feedback::dismiss) { Text("Закрыть") }
        }
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    modifier: Modifier,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = !busy, shape = RoundedCornerShape(26.dp), color = container, contentColor = content, modifier = modifier.height(112.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            if (busy) LoadingIndicator(Modifier.size(34.dp), color = content) else ShapeTile(icon, content, container, 36.dp)
            Column {
                Text(if (busy) "Собираем…" else title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

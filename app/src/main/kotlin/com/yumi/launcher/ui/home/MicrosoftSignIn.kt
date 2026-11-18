package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yumi.launcher.core.MicrosoftCode
import com.yumi.launcher.core.Support
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.theme.glassContainer
import com.yumi.launcher.ui.theme.glassInnerBorder
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Signing in with Microsoft: a code from Microsoft, confirmed on Microsoft's own page in the
 * browser. The launcher only waits for the answer — the password never passes through it.
 */
@Composable
fun MicrosoftSignIn(model: LauncherModel, onClose: () -> Unit) {
    val microsoft = model.microsoft
    val scheme = MaterialTheme.colorScheme
    val begin = {
        microsoft.begin {
            model.settings.account = AccountKind.Microsoft
            model.dismissError()
            onClose()
        }
    }
    val close = {
        microsoft.cancel()
        onClose()
    }
    LaunchedEffect(Unit) { begin() }

    ModalCard(width = 480.dp) {
        Text("Вход через Microsoft", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Аккаунт, на котором куплен Minecraft. Вход подтверждается на сайте Microsoft — пароль лаунчер не видит.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        val code = microsoft.code
        CodePlate(code, failed = microsoft.error != null)
        AnimatedContent(
            targetState = Triple(microsoft.error, microsoft.step, code != null),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(90)) },
        ) { (error, step, waiting) ->
            when {
                error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                step != null -> Progress("$step…")
                waiting -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Hint("1", "Откройте страницу Microsoft — код в ней уже вставлен")
                    Hint("2", "Войдите в аккаунт с Minecraft и подтвердите вход")
                    Spacer(Modifier.height(2.dp))
                    Progress("Ждём подтверждения в браузере")
                }
                else -> Progress("Получаем код у Microsoft")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = close) { Text("Отмена") }
            if (microsoft.error != null) {
                Button(onClick = begin, shapes = ButtonDefaults.shapes()) { Text("Попробовать снова") }
            } else {
                Button(
                    onClick = {
                        code ?: return@Button
                        copy(code.userCode)
                        Support.open(code.link)
                    },
                    enabled = code != null && microsoft.step == null,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Открыть Microsoft")
                }
            }
        }
    }
}

/** The code, large, to read off or copy — for a browser on another device, say. */
@Composable
private fun CodePlate(code: MicrosoftCode?, failed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = glassContainer(), border = glassInnerBorder(), modifier = Modifier.fillMaxWidth().height(84.dp)) {
        Row(Modifier.padding(start = 22.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Код входа", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
                    if (code == null) {
                        if (failed) Text("—", style = MaterialTheme.typography.headlineMedium, color = scheme.onSurfaceVariant)
                        else LoadingIndicator(Modifier.size(28.dp))
                    } else {
                        Text(
                            code.userCode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = 4.sp,
                            color = scheme.primary,
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    code ?: return@IconButton
                    copy(code.userCode)
                    copied = true
                },
                enabled = code != null,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, if (copied) "Скопировано" else "Скопировать код")
            }
        }
    }
}

@Composable
private fun Hint(number: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(50), color = scheme.secondaryContainer, modifier = Modifier.size(22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadingIndicator(Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copy(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

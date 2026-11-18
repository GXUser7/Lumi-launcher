package com.yumi.launcher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.CurseForge
import com.yumi.launcher.ui.theme.glassContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What CurseForge says about a key: asked once the typing stops. */
private sealed interface KeyCheck {
    data object Checking : KeyCheck
    data object Works : KeyCheck
    data class Refused(val why: String) : KeyCheck
    data class Unreachable(val why: String) : KeyCheck
}

/**
 * The CurseForge key, checked with CurseForge as it is typed. The key is hidden by default — but a
 * wrong one pasted in is exactly what hiding makes impossible to notice, so the eye shows it, and
 * the line under the field says whether CurseForge takes it.
 */
@Composable
fun CurseForgeKeyField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String? = null) {
    KeyInput(value, onValueChange, rememberKeyCheck(value), modifier, label, placeholder)
}

/** Where to put a CurseForge key, right where it is missed; [onSave] only once CurseForge has taken it. */
@Composable
fun CurseForgeKeyPrompt(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    val check = rememberKeyCheck(key)
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(26.dp), color = glassContainer(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeTile(Icons.Rounded.Key, scheme.secondaryContainer, scheme.onSecondaryContainer, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text("Нужен ключ CurseForge", style = MaterialTheme.typography.titleLarge)
            }
            Hint(
                "CurseForge пускает лаунчеры только с личным ключом. Он бесплатный: войдите на console.curseforge.com, раздел API Keys, " +
                    "скопируйте ключ — он начинается с \$2a\$10\$ — и вставьте сюда. Modrinth работает без ключа.",
            )
            KeyInput(key, { key = it.trim() }, check, Modifier.fillMaxWidth(), label = "Ключ API", placeholder = null)
            // Offline the key cannot be checked; it is saved as it is, and the first search will tell.
            Button(
                onClick = { onSave(key) },
                enabled = check == KeyCheck.Works || check is KeyCheck.Unreachable,
                shapes = ButtonDefaults.shapes(),
            ) { Text("Сохранить ключ") }
        }
    }
}

@Composable
private fun KeyInput(value: String, onValueChange: (String) -> Unit, check: KeyCheck?, modifier: Modifier, label: String?, placeholder: String?) {
    var shown by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            icon = Icons.Rounded.Key,
            isError = check is KeyCheck.Refused,
            visualTransformation = if (shown || value.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = if (value.isEmpty()) null else {
                {
                    IconButton(onClick = { shown = !shown }) {
                        Icon(if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (shown) "Скрыть ключ" else "Показать ключ")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        KeyStatus(check)
    }
}

@Composable
private fun KeyStatus(check: KeyCheck?) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(check, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(90)) }) { state ->
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                null -> Unit
                KeyCheck.Checking -> {
                    LoadingIndicator(Modifier.size(18.dp))
                    Text("Проверяем ключ у CurseForge…", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                KeyCheck.Works -> {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = scheme.primary)
                    Text("Ключ работает", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
                }
                is KeyCheck.Refused -> {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = scheme.error)
                    Text(state.why, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
                is KeyCheck.Unreachable -> Text(state.why, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Nothing for an empty key; otherwise CurseForge's answer, asked half a second after the last keystroke. */
@Composable
private fun rememberKeyCheck(key: String): KeyCheck? {
    val check by produceState<KeyCheck?>(null, key) {
        value = null
        if (key.isBlank()) return@produceState
        delay(500)
        value = KeyCheck.Checking
        value = try {
            CurseForge.refusal(key.trim())?.let(KeyCheck::Refused) ?: KeyCheck.Works
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KeyCheck.Unreachable("Не удалось проверить ключ — нет связи с CurseForge")
        }
    }
    return check
}

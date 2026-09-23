package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.ElyBy
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.ModalCard
import java.awt.Desktop
import java.net.URI

/**
 * Signing in to Ely.by: login and password, then the authenticator code if the account asks for
 * one. The password goes to Ely.by and nowhere else — the launcher keeps only the token it gets back.
 */
@Composable
fun ElySignIn(model: LauncherModel, onClose: () -> Unit) {
    val ely = model.ely
    val scheme = MaterialTheme.colorScheme
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val codeFocus = remember { FocusRequester() }
    val close = {
        ely.reset()
        onClose()
    }
    val submit = {
        ely.signIn(login, password, code) {
            model.settings.account = AccountKind.ElyBy
            model.dismissError()
            onClose()
        }
    }
    LaunchedEffect(ely.needsCode) { if (ely.needsCode) runCatching { codeFocus.requestFocus() } }

    ModalCard(width = 460.dp) {
        Text("Вход через Ely.by", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Ваш скин в игре и ник, который никто не займёт. Пароль уходит только на Ely.by — лаунчер хранит лишь токен входа.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        val enter = Modifier.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                submit()
                true
            } else false
        }
        Field(
            value = login,
            onValueChange = { login = it.trim() },
            label = "Ник или e-mail",
            icon = Icons.Rounded.Person,
            enabled = !ely.busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().then(enter),
        )
        Field(
            value = password,
            onValueChange = { password = it },
            label = "Пароль",
            icon = Icons.Rounded.Lock,
            enabled = !ely.busy,
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { shown = !shown }) {
                    Icon(if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (shown) "Скрыть пароль" else "Показать пароль")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().then(enter),
        )
        AnimatedVisibility(ely.needsCode) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = "Код из приложения",
                    placeholder = "123456",
                    icon = Icons.Rounded.Pin,
                    enabled = !ely.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().focusRequester(codeFocus).then(enter),
                )
                Text(
                    "На аккаунте включена двухфакторная защита — введите шестизначный код.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        ely.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(ElyBy.REGISTER_URL)) } }) { Text("Регистрация") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = close, enabled = !ely.busy) { Text("Отмена") }
            Button(onClick = submit, enabled = !ely.busy && login.isNotBlank() && password.isNotEmpty(), shapes = ButtonDefaults.shapes()) {
                if (ely.busy) {
                    LoadingIndicator(Modifier.size(20.dp), color = scheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                }
                Text("Войти")
            }
        }
    }
}

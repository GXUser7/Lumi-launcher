package com.yumi.launcher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumi.launcher.data.AccountEntry
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.ui.components.Avatar
import com.yumi.launcher.ui.components.SkinHead

/** An account's face: the skin's for a signed-in account, the launcher's pixel face for a nick. */
@Composable
fun AccountFace(kind: AccountKind, skin: String?, size: Dp) {
    if (kind == AccountKind.Offline) Avatar(offline = true, size = size) else SkinHead(skin, size)
}

/** What an account is, in a few words under its name. */
fun accountKindLine(kind: AccountKind) = when (kind) {
    AccountKind.Offline -> "Офлайн-ник"
    AccountKind.Microsoft -> "Microsoft · лицензия"
    AccountKind.ElyBy -> "Ely.by"
}

/**
 * The accounts as the items of a menu — each with its face and kind, the one playing checked, a
 * cross to forget it — then the three ways to add one. Picking one makes it the account the game
 * starts with; [onDone] closes the menu.
 */
@Composable
fun AccountMenuItems(model: LauncherModel, state: LauncherState, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accounts = model.accounts
    val all = accounts.all
    all.forEach { entry ->
        DropdownMenuItem(
            text = { AccountText(entry) },
            leadingIcon = { AccountFace(entry.kind, entry.skin, 36.dp) },
            trailingIcon = {
                if (entry.active) Icon(Icons.Rounded.Check, "Играете под ним", tint = scheme.primary)
                else IconButton(onClick = { accounts.remove(entry) }, enabled = !model.busy) { Icon(Icons.Rounded.Close, "Забыть «${entry.name}»", tint = scheme.onSurfaceVariant) }
            },
            enabled = !model.busy || entry.active,
            onClick = { accounts.switchTo(entry); onDone() },
        )
    }
    if (all.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
    DropdownMenuItem(
        text = { Text("Добавить офлайн-ник") },
        leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
        enabled = !model.busy,
        onClick = {
            accounts.addOffline()
            state.section = Section.Home
            onDone()
        },
    )
    DropdownMenuItem(
        text = { Text("Войти в Microsoft") },
        leadingIcon = { Icon(Icons.Rounded.VerifiedUser, null) },
        enabled = !model.busy,
        onClick = {
            state.section = Section.Home
            state.microsoftSignIn = true
            onDone()
        },
    )
    DropdownMenuItem(
        text = { Text("Войти в Ely.by") },
        leadingIcon = { Icon(Icons.Rounded.Key, null) },
        enabled = !model.busy,
        onClick = {
            state.section = Section.Home
            state.elySignIn = true
            onDone()
        },
    )
}

@Composable
private fun AccountText(entry: AccountEntry) {
    Column(Modifier.width(180.dp)) {
        Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(accountKindLine(entry.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

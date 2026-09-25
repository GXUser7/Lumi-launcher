package com.yumi.launcher.data

import com.yumi.launcher.core.ElyBy
import com.yumi.launcher.core.ElySession
import com.yumi.launcher.core.MicrosoftSession
import com.yumi.launcher.settings.AccountKind
import com.yumi.launcher.settings.SavedAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One account in the switcher. [saved] is its stored copy when it is not in its kind's slot. */
data class AccountEntry(
    val kind: AccountKind,
    val name: String,
    val uuid: String,
    val skin: String?,
    val active: Boolean,
    val saved: SavedAccount?,
)

/**
 * Several accounts of every kind. The settings keep one account of each kind in a slot — the nick,
 * the Ely.by session, the Microsoft session — and the kind that plays; that is what every launch,
 * sign-in and token refresh already works with. The rest wait in [com.yumi.launcher.settings.LauncherSettings.savedAccounts]:
 * switching to one swaps it with whatever sits in its kind's slot. So only accounts in slots ever
 * have their tokens renewed, and a waiting one keeps the tokens it was put away with.
 */
class Accounts(private val model: LauncherModel, private val scope: CoroutineScope) {
    private val settings get() = model.settings

    /** Every account, in a steady order — by kind, then name — so switching does not reshuffle the list. */
    val all: List<AccountEntry>
        get() = buildList {
            val kind = settings.account
            if (settings.nick.isNotBlank()) add(AccountEntry(AccountKind.Offline, settings.nick, "", null, kind == AccountKind.Offline, null))
            settings.microsoftSession?.let { add(AccountEntry(AccountKind.Microsoft, it.name, it.uuid, it.skin, kind == AccountKind.Microsoft, null)) }
            settings.elySession?.let { add(AccountEntry(AccountKind.ElyBy, it.name, it.uuid, model.ely.skin, kind == AccountKind.ElyBy, null)) }
            settings.savedAccounts.forEach { add(AccountEntry(it.kind, it.name, it.uuid, it.skin, false, it)) }
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))

    /** Makes [entry] the account the game starts with. */
    fun switchTo(entry: AccountEntry) {
        val saved = entry.saved
        if (saved != null) {
            stash(saved.kind)
            settings.savedAccounts = settings.savedAccounts.filterNot { it.same(saved) }
            load(saved)
        }
        settings.account = entry.kind
        model.dismissError()
    }

    /**
     * A new offline account: the nick in the slot, if there is one, is put away and the slot is
     * left empty for the new nick, which the player types on the home screen.
     */
    fun addOffline() {
        stash(AccountKind.Offline)
        settings.account = AccountKind.Offline
        model.dismissError()
    }

    /**
     * Called by a sign-in just before it fills its kind's slot with the account [uuid]: the account
     * there now is put away, unless it is that same account signing in again. An older saved copy of
     * it goes — the fresh tokens replace it.
     */
    fun beforeSignIn(kind: AccountKind, uuid: String) {
        val current = slot(kind)
        if (current != null && current.uuid != uuid) stash(kind)
        settings.savedAccounts = settings.savedAccounts.filterNot { it.kind == kind && it.uuid == uuid }
    }

    /**
     * Forgets [entry]: signs it out if it is in a slot, drops it if it is saved. If it was the one
     * playing and there are others, the first of them takes over.
     */
    fun remove(entry: AccountEntry) {
        val saved = entry.saved
        if (saved != null) {
            settings.savedAccounts = settings.savedAccounts.filterNot { it.same(saved) }
            if (saved.kind == AccountKind.ElyBy && saved.accessToken.isNotEmpty()) {
                scope.launch { runCatching { ElyBy.invalidate(ElySession(saved.accessToken, settings.elyClientToken, saved.uuid, saved.name)) } }
            }
            return
        }
        when (entry.kind) {
            AccountKind.Offline -> settings.nick = ""
            AccountKind.Microsoft -> model.microsoft.signOut()
            AccountKind.ElyBy -> model.ely.signOut()
        }
        if (entry.active) all.firstOrNull()?.let(::switchTo)
    }

    /** What sits in [kind]'s slot, as a saved account; null when the slot is empty. */
    private fun slot(kind: AccountKind): SavedAccount? = when (kind) {
        AccountKind.Offline -> settings.nick.takeIf { it.isNotBlank() }?.let { SavedAccount(kind, it) }
        AccountKind.Microsoft -> settings.microsoftSession?.let {
            SavedAccount(kind, it.name, it.uuid, it.accessToken, it.refreshToken, it.expiresAt, it.xuid, it.skin)
        }
        AccountKind.ElyBy -> settings.elySession?.let { SavedAccount(kind, it.name, it.uuid, it.accessToken, skin = model.ely.skin) }
    }

    /** Moves the account in [kind]'s slot to the saved ones and empties the slot. */
    private fun stash(kind: AccountKind) {
        val current = slot(kind) ?: return
        settings.savedAccounts = settings.savedAccounts.filterNot { it.same(current) } + current
        when (kind) {
            AccountKind.Offline -> settings.nick = ""
            AccountKind.Microsoft -> settings.microsoftSession = null
            // Not signed out: the token must stay good for when the account is switched back to.
            AccountKind.ElyBy -> settings.elySession = null
        }
    }

    private fun load(saved: SavedAccount) {
        when (saved.kind) {
            AccountKind.Offline -> settings.nick = saved.name
            AccountKind.Microsoft -> settings.microsoftSession =
                MicrosoftSession(saved.refreshToken, saved.accessToken, saved.expiresAt, saved.uuid, saved.name, saved.xuid, saved.skin)
            AccountKind.ElyBy -> {
                settings.elySession = ElySession(saved.accessToken, settings.elyClientToken, saved.uuid, saved.name)
                model.ely.refreshSkin()
            }
        }
    }
}

private fun SavedAccount.same(other: SavedAccount) =
    kind == other.kind && if (kind == AccountKind.Offline) name == other.name else uuid == other.uuid

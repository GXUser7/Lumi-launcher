package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.ElyBy
import com.yumi.launcher.core.ElyRefused
import com.yumi.launcher.core.ElySession
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Ely.by account: signing in and out, the two-factor step when the account has one, and a
 * session kept valid for each launch. The session lives in the settings, so it outlasts restarts.
 */
class ElyAccount(private val settings: LauncherSettings, private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Ely.by asked for the code from the authenticator app; the form grows a field for it. */
    var needsCode by mutableStateOf(false)
        private set

    /** The player's skin texture, for their face in the launcher; null is Ely.by's default skin. */
    var skin by mutableStateOf<String?>(null)
        private set

    val session: ElySession? get() = settings.elySession

    init {
        session?.let { loadSkin(it.name) }
    }

    fun signIn(login: String, password: String, code: String, onDone: () -> Unit) {
        if (busy) return
        if (login.isBlank() || password.isEmpty()) {
            error = "Введите логин и пароль"
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val session = ElyBy.authenticate(login, password, code.takeIf { needsCode }, settings.elyClientToken)
                settings.elySession = session
                needsCode = false
                loadSkin(session.name)
                onDone()
            } catch (e: ElyRefused) {
                when {
                    !e.needsCode -> error = e.message
                    needsCode && code.isNotBlank() -> error = "Код не подошёл — возьмите новый из приложения"
                    else -> needsCode = true
                }
            } catch (e: Exception) {
                error = "Нет связи с Ely.by: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Back to the plain form, as when the dialog is closed half-way. */
    fun reset() {
        if (busy) return
        needsCode = false
        error = null
    }

    fun signOut() {
        val current = session ?: return
        settings.elySession = null
        skin = null
        scope.launch { ElyBy.invalidate(current) }
    }

    /**
     * A session the game can use: checked with Ely.by and refreshed if it has expired. Without a
     * network the stored one is tried as it is — the game itself can still start.
     */
    suspend fun fresh(): ElySession {
        val current = session ?: throw ElyRefused("Войдите в аккаунт Ely.by")
        if (runCatching { ElyBy.valid(current) }.getOrDefault(true)) return current
        return try {
            ElyBy.refresh(current).also { settings.elySession = it }
        } catch (e: ElyRefused) {
            settings.elySession = null
            skin = null
            throw ElyRefused("Сессия Ely.by истекла — войдите снова")
        }
    }

    private fun loadSkin(name: String) {
        scope.launch { skin = ElyBy.skinUrl(name) }
    }
}

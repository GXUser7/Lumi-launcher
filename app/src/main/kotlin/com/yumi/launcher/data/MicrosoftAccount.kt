package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.Microsoft
import com.yumi.launcher.core.MicrosoftCode
import com.yumi.launcher.core.MicrosoftRefused
import com.yumi.launcher.core.MicrosoftSession
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * The Microsoft account — the licence. Signing in is a code confirmed in the browser; after that
 * the refresh token in the settings keeps the player signed in, and each launch gets a game token
 * that is still good.
 */
class MicrosoftAccount(private val settings: LauncherSettings, private val scope: CoroutineScope) {
    /** The code to confirm on Microsoft's page, while the launcher waits for that. */
    var code by mutableStateOf<MicrosoftCode?>(null)
        private set

    /** What the sign-in is doing once the code is confirmed: Xbox Live, Minecraft, the profile. */
    var step by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set
    private var job: Job? = null

    val session: MicrosoftSession? get() = settings.microsoftSession

    /** Asks Microsoft for a code and waits — up to its quarter of an hour — for the player to confirm it. */
    fun begin(onDone: () -> Unit) {
        if (busy) return
        error = null
        step = null
        code = null
        busy = true
        job = scope.launch {
            try {
                val fresh = Microsoft.code()
                code = fresh
                settings.microsoftSession = Microsoft.await(fresh) { step = it }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicrosoftRefused) {
                error = e.message
            } catch (e: Exception) {
                error = "Нет связи с Microsoft: ${e.message}"
            } finally {
                // A sign-in cancelled and begun again must not have the old one clear the new one's code.
                if (job === coroutineContext.job) {
                    code = null
                    step = null
                    busy = false
                }
            }
        }
    }

    /** Stops waiting, as when the dialog is closed half-way. */
    fun cancel() {
        job?.cancel()
        job = null
        busy = false
        code = null
        step = null
        error = null
    }

    fun signOut() {
        settings.microsoftSession = null
    }

    /**
     * A session the game can start with: the stored one while its token lasts, else renewed from
     * the refresh token. Without a network a token that has not run out yet is used as it is.
     */
    suspend fun fresh(): MicrosoftSession {
        val current = session ?: throw MicrosoftRefused("Войдите в аккаунт Microsoft", signedOut = true)
        val lasts = current.accessToken.isNotEmpty() && current.expiresAt > System.currentTimeMillis() + RENEW_AHEAD
        if (lasts) return current
        return try {
            Microsoft.refresh(current).also { settings.microsoftSession = it }
        } catch (e: MicrosoftRefused) {
            if (e.signedOut) settings.microsoftSession = null
            throw e
        } catch (e: Exception) {
            if (current.accessToken.isNotEmpty() && current.expiresAt > System.currentTimeMillis()) current
            else throw MicrosoftRefused("Нет связи с Microsoft, а вход нужно обновить: ${e.message}")
        }
    }

    private companion object {
        /** A game session runs for hours; a token with less than this left is renewed before the start. */
        const val RENEW_AHEAD = 60 * 60 * 1000L
    }
}

package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/** A signed-in Ely.by player: what the game needs to join servers as them. */
data class ElySession(val accessToken: String, val clientToken: String, val uuid: String, val name: String)

/** Ely.by said no. [needsCode] when the account has two-factor sign-in and wants its code. */
class ElyRefused(message: String, val needsCode: Boolean = false) : Exception(message)

/**
 * Ely.by — free accounts with skins, and the usual way to play without a Mojang licence and still
 * keep a nick no one else can take. It speaks Mojang's old Yggdrasil protocol, so signing in is a
 * login and password traded for a token; the password itself is never stored.
 */
internal object ElyBy {
    private const val AUTH = "https://authserver.ely.by/auth"
    private const val TEXTURES = "https://skinsystem.ely.by/textures/"

    /** Where authlib-injector points the game — and a server — instead of Mojang. */
    const val INJECTOR_API = "https://authserver.ely.by/api/authlib-injector"
    const val REGISTER_URL = "https://account.ely.by/register"

    /** With two-factor sign-in on, the code travels appended to the password: `password:123456`. */
    suspend fun authenticate(login: String, password: String, code: String?, clientToken: String): ElySession {
        val secret = if (code.isNullOrBlank()) password else "$password:${code.trim()}"
        val json = call("authenticate") {
            put("username", login.trim())
            put("password", secret)
            put("clientToken", clientToken)
            put("requestUser", true)
        }
        return session(json, clientToken)
    }

    /** A fresh token for an expired one; refused once Ely.by has forgotten the session. */
    suspend fun refresh(session: ElySession): ElySession = session(
        call("refresh") {
            put("accessToken", session.accessToken)
            put("clientToken", session.clientToken)
            put("requestUser", true)
        },
        session.clientToken,
    )

    suspend fun valid(session: ElySession): Boolean =
        Http.post("$AUTH/validate", buildJsonObject { put("accessToken", session.accessToken) }.toString()).first == 200

    /** Signs the token out on Ely.by's side too; a failure here changes nothing for the player. */
    suspend fun invalidate(session: ElySession) {
        runCatching {
            Http.post(
                "$AUTH/invalidate",
                buildJsonObject {
                    put("accessToken", session.accessToken)
                    put("clientToken", session.clientToken)
                }.toString(),
            )
        }
    }

    /** The player's skin texture, or null for Ely.by's default one. */
    suspend fun skinUrl(name: String): String? = runCatching {
        val json = LauncherJson.parseToJsonElement(Http.text(TEXTURES + name)).jsonObject
        // The texture links are plain http, and the storage behind them answers on https as well.
        json.obj("SKIN")?.str("url")?.replaceFirst("http://", "https://")
    }.getOrNull()

    private suspend fun call(endpoint: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        val (status, text) = Http.post("$AUTH/$endpoint", buildJsonObject(body).toString())
        val json = runCatching { LauncherJson.parseToJsonElement(text).jsonObject }.getOrNull()
        if (status == 200 && json != null) return json
        if (status == 429) throw ElyRefused("Слишком много попыток — подождите минуту")
        val message = json?.str("errorMessage") ?: "Ely.by ответил $status"
        val lower = message.lowercase()
        throw when {
            "two factor" in lower -> ElyRefused("Нужен код двухфакторной защиты", needsCode = true)
            "invalid credentials" in lower -> ElyRefused("Неверный логин или пароль")
            "token" in lower -> ElyRefused("Сессия Ely.by истекла — войдите снова")
            "suspended" in lower || "banned" in lower -> ElyRefused("Аккаунт заблокирован на Ely.by")
            else -> ElyRefused(message)
        }
    }

    private fun session(json: JsonObject, clientToken: String): ElySession {
        val profile = json.obj("selectedProfile") ?: throw ElyRefused("У аккаунта нет игрового профиля")
        return ElySession(
            accessToken = json.str("accessToken") ?: throw ElyRefused("Ely.by не выдал токен"),
            clientToken = json.str("clientToken") ?: clientToken,
            uuid = profile.str("id")!!,
            name = profile.str("name")!!,
        )
    }
}

/**
 * authlib-injector: a Java agent that swaps Mojang's account servers for another's — here Ely.by —
 * inside the game or a server, so skins show and sign-ins are checked there. One jar for all versions.
 */
internal object AuthlibInjector {
    private const val LATEST = "https://authlib-injector.yushi.moe/artifact/latest.json"

    @Volatile
    private var checked: File? = null

    /** The newest release, downloaded once; offline, whichever one is already here. */
    suspend fun ensure(): File = checked?.takeIf { it.isFile } ?: withContext(Dispatchers.IO) {
        val dir = LauncherPaths.root.resolve("authlib-injector")
        val existing = dir.listFiles { f -> f.name.endsWith(".jar") }?.maxByOrNull { it.lastModified() }
        val latest = try {
            LauncherJson.parseToJsonElement(Http.text(LATEST)).jsonObject
        } catch (e: Exception) {
            existing?.let { return@withContext it }
            throw IOException("Не удалось скачать authlib-injector: ${e.message}", e)
        }
        val jar = dir.resolve("authlib-injector-${latest.str("version")}.jar")
        val expected = latest.obj("checksums")?.str("sha256")
        if (!jar.isFile || (expected != null && sha256(jar) != expected)) {
            jar.delete()
            Http.downloadAll(listOf(Download(latest.str("download_url")!!, jar, null, null))) {}
            if (expected != null && sha256(jar) != expected) {
                jar.delete()
                error("authlib-injector: контрольная сумма не совпала")
            }
        }
        jar
    }.also { checked = it }

    fun argument(jar: File): String = "-javaagent:${jar.path}=${ElyBy.INJECTOR_API}"
}

package com.yumi.launcher.core

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/**
 * A licensed player: the Minecraft token the game starts with, and the Microsoft refresh token that
 * gets a new one once it runs out — a day later. [skin] is the texture of their current skin.
 */
data class MicrosoftSession(
    val refreshToken: String,
    val accessToken: String,
    val expiresAt: Long,
    val uuid: String,
    val name: String,
    val xuid: String,
    val skin: String?,
)

/** A code the player types on Microsoft's page to let the launcher in; [link] already carries it. */
data class MicrosoftCode(val userCode: String, val deviceCode: String, val link: String, val interval: Int, val expiresAt: Long)

/** Microsoft, Xbox or Minecraft said no, with why. [signedOut] when only a new sign-in helps. */
class MicrosoftRefused(message: String, val signedOut: Boolean = false) : Exception(message)

/**
 * Signing in with a Microsoft account, the way the official launcher does: a Microsoft token for
 * Xbox Live, Xbox Live's for Minecraft's services, and those for the game's own token and profile.
 *
 * The first step is the device code flow — the player confirms the sign-in on Microsoft's page in
 * their browser, so the launcher never sees the password. It runs under the official launcher's
 * client id: Minecraft's services only accept Xbox tokens from apps Mojang has approved.
 */
internal object Microsoft {
    private const val CLIENT_ID = "00000000402b5328"
    private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
    private const val DEVICE_CODE = "https://login.live.com/oauth20_connect.srf"
    private const val TOKEN = "https://login.live.com/oauth20_token.srf"
    private const val XBOX_USER = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile"
    private const val MC_ENTITLEMENTS = "https://api.minecraftservices.com/entitlements/mcstore"

    const val BUY_URL = "https://www.minecraft.net/store/minecraft-java-bedrock-edition-pc"
    const val PROFILE_URL = "https://www.minecraft.net/msaprofile/mygames/editprofile"

    private val XBOX = mapOf("Accept" to "application/json", "x-xbl-contract-version" to "1")

    suspend fun code(): MicrosoftCode {
        val (status, text) = Http.form(DEVICE_CODE, mapOf("client_id" to CLIENT_ID, "scope" to SCOPE, "response_type" to "device_code"))
        val json = parse(text)
        if (status != 200 || json == null) throw MicrosoftRefused("Microsoft не выдал код входа (${json?.str("error") ?: status})")
        val userCode = json.str("user_code")!!
        return MicrosoftCode(
            userCode = userCode,
            deviceCode = json.str("device_code")!!,
            // With `otc` the page opens with the code already typed in.
            link = "${json.str("verification_uri") ?: "https://www.microsoft.com/link"}?otc=$userCode",
            interval = json.str("interval")?.toIntOrNull() ?: 5,
            expiresAt = System.currentTimeMillis() + (json.str("expires_in")?.toLongOrNull() ?: 900) * 1000,
        )
    }

    /**
     * Waits for the player to confirm [code] in the browser, then signs in to Minecraft.
     * [onStep] names each step after that, for the dialog.
     */
    suspend fun await(code: MicrosoftCode, onStep: (String) -> Unit): MicrosoftSession {
        var interval = code.interval.coerceAtLeast(1)
        while (System.currentTimeMillis() < code.expiresAt) {
            delay(interval * 1000L)
            val (status, text) = Http.form(
                TOKEN,
                mapOf("client_id" to CLIENT_ID, "device_code" to code.deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"),
            )
            val json = parse(text) ?: throw IOException("Microsoft ответил $status")
            when (json.str("error")) {
                null -> return minecraft(json, onStep)
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "authorization_declined", "access_denied" -> throw MicrosoftRefused("Вход отклонён на странице Microsoft")
                "expired_token" -> break
                else -> throw MicrosoftRefused(json.str("error_description") ?: "Microsoft отказал во входе")
            }
        }
        throw MicrosoftRefused("Код устарел — начните вход заново")
    }

    /** The whole chain again from the refresh token, for when the game's token has run out. */
    suspend fun refresh(session: MicrosoftSession): MicrosoftSession {
        val (status, text) = Http.form(
            TOKEN,
            mapOf("client_id" to CLIENT_ID, "scope" to SCOPE, "grant_type" to "refresh_token", "refresh_token" to session.refreshToken),
        )
        val json = parse(text) ?: throw IOException("Microsoft ответил $status")
        if (json.str("error") != null) throw MicrosoftRefused("Вход в Microsoft истёк — войдите снова", signedOut = true)
        return minecraft(json) {}
    }

    private suspend fun minecraft(tokens: JsonObject, onStep: (String) -> Unit): MicrosoftSession {
        val msToken = tokens.str("access_token") ?: throw MicrosoftRefused("Microsoft не выдал токен")
        val refreshToken = tokens.str("refresh_token") ?: throw MicrosoftRefused("Microsoft не выдал токен обновления")

        onStep("Вход в Xbox Live")
        val xbox = xboxUser(msToken)
        val xsts = xsts(xbox)
        val claims = xsts.obj("DisplayClaims")?.get("xui")?.jsonArray?.firstOrNull()?.jsonObject
        val userHash = claims?.str("uhs") ?: throw MicrosoftRefused("Xbox Live не выдал данные игрока")

        onStep("Вход в Minecraft")
        val (status, text) = Http.post(
            MC_LOGIN,
            buildJsonObject { put("identityToken", "XBL3.0 x=$userHash;${xsts.str("Token")}") }.toString(),
            mapOf("Accept" to "application/json"),
        )
        val login = parse(text)
        val access = login?.str("access_token")
        if (status != 200 || access == null) {
            throw MicrosoftRefused(if (status == 429) "Слишком много входов подряд — подождите пару минут" else "Minecraft не принял вход (ответ $status)")
        }
        val expiresIn = login.str("expires_in")?.toLongOrNull() ?: 86_400

        onStep("Профиль игрока")
        val bearer = mapOf("Authorization" to "Bearer $access")
        val profile = try {
            LauncherJson.parseToJsonElement(Http.text(MC_PROFILE, bearer)).jsonObject
        } catch (e: HttpRefused) {
            if (e.status != 404) throw e
            throw MicrosoftRefused(if (ownsGame(bearer)) "У аккаунта ещё нет ника в Minecraft — выберите его на minecraft.net" else "На этом аккаунте нет Minecraft: Java Edition")
        }
        val skin = profile["skins"]?.jsonArray?.map { it.jsonObject }?.let { skins -> skins.firstOrNull { it.str("state") == "ACTIVE" } ?: skins.firstOrNull() }
        return MicrosoftSession(
            refreshToken = refreshToken,
            accessToken = access,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000,
            uuid = profile.str("id")!!,
            name = profile.str("name")!!,
            xuid = claims.str("xid") ?: "0",
            skin = skin?.str("url")?.replaceFirst("http://", "https://"),
        )
    }

    /**
     * Xbox Live's user token. Tokens from login.live.com go as `t=`, the ones from Azure apps as
     * `d=`; the other is tried if Xbox turns the first one down.
     */
    private suspend fun xboxUser(msToken: String): String {
        for (prefix in listOf("t=", "d=")) {
            val body = buildJsonObject {
                putJsonObject("Properties") {
                    put("AuthMethod", "RPS")
                    put("SiteName", "user.auth.xboxlive.com")
                    put("RpsTicket", prefix + msToken)
                }
                put("RelyingParty", "http://auth.xboxlive.com")
                put("TokenType", "JWT")
            }
            val (status, text) = Http.post(XBOX_USER, body.toString(), XBOX)
            if (status == 200) parse(text)?.str("Token")?.let { return it }
        }
        throw MicrosoftRefused("Xbox Live не принял вход Microsoft")
    }

    private suspend fun xsts(userToken: String): JsonObject {
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("SandboxId", "RETAIL")
                put("UserTokens", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(userToken)) })
            }
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }
        val (status, text) = Http.post(XSTS, body.toString(), XBOX)
        val json = parse(text)
        if (status == 200 && json != null) return json
        // Xbox explains a refusal with a number; these are the ones a player can do something about.
        throw MicrosoftRefused(
            when (json?.str("XErr")) {
                "2148916233" -> "У аккаунта Microsoft нет профиля Xbox — откройте xbox.com, войдите и создайте его"
                "2148916235" -> "Xbox Live недоступен в стране аккаунта"
                "2148916236", "2148916237" -> "Аккаунту нужна проверка возраста на xbox.com"
                "2148916238" -> "Детский аккаунт — взрослый должен добавить его в семейную группу Microsoft"
                "2148916227" -> "Аккаунт заблокирован в Xbox Live"
                else -> "Xbox Live отказал во входе (ответ $status)"
            },
        )
    }

    /** Whether the account has the game at all — a profile can be missing only because no nick is picked yet. */
    private suspend fun ownsGame(bearer: Map<String, String>): Boolean = runCatching {
        LauncherJson.parseToJsonElement(Http.text(MC_ENTITLEMENTS, bearer)).jsonObject["items"]?.jsonArray?.isNotEmpty() == true
    }.getOrDefault(true)

    private fun parse(text: String): JsonObject? = runCatching { LauncherJson.parseToJsonElement(text).jsonObject }.getOrNull()
}

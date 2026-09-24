package com.yumi.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.yumi.launcher.core.ElySession
import com.yumi.launcher.core.MicrosoftSession
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class ThemeMode(val label: String, val icon: ImageVector) {
    System("Системная", Icons.Rounded.BrightnessAuto),
    Light("Светлая", Icons.Rounded.LightMode),
    Dark("Тёмная", Icons.Rounded.DarkMode),
}

/** The colours: Yumi's own Glacier, or one of the pastel palettes. Stored by name. */
enum class Palette(val label: String) { Original("Оригинал"), Green("Зелёная"), Pink("Розовая"), Blue("Синяя") }

enum class AccountKind(val label: String) { Microsoft("Microsoft"), Offline("Офлайн"), ElyBy("Ely.by") }

/**
 * How much the island draws. Low is flat colour with plain light; Medium adds the block textures,
 * smooth light from the sky and from torches, and moving water and lava; High adds sun and moon
 * shadows, halos and lens stars over the lights, the sun's lens flare, a vignette, smoke, sparks
 * and fireflies, and islands far out in the haze;
 * Ultra adds light shafts, the aurora, swaying leaves and grass, glints on the water, shadows of
 * passing clouds, birds, and the rest of the particles.
 * Stored by name, so the order here is only the order of the buttons.
 */
enum class SceneQuality(val label: String) { Low("Низкое"), Medium("Среднее"), High("Высокое"), Ultra("Ультра") }

/** What moves the camera: a slow turn, the pointer, or both. */
enum class SceneMotion(val label: String) { Rotate("Вращение"), Mouse("За мышью"), Both("Вместе") }

/** What the launcher does with itself once the game is up. */
enum class AfterLaunch(val label: String) { Keep("Оставить"), Minimize("Свернуть"), Close("Закрыть") }

/**
 * The launcher's own preferences. Kept in `java.util.prefs` — the registry on Windows — until
 * there is enough of them to deserve a settings file next to the game data. Every property is
 * Compose state, so a change shows at once and is written through on the spot.
 */
class LauncherSettings(private val store: Preferences = Preferences.userRoot().node("com/yumi/launcher")) {
    var theme by stored("theme", ThemeMode.System, { runCatching { ThemeMode.valueOf(it) }.getOrNull() }, { it.name })
    var palette by stored("palette", Palette.Original, { runCatching { Palette.valueOf(it) }.getOrNull() }, { it.name })
    var account by stored("account", AccountKind.Offline, { runCatching { AccountKind.valueOf(it) }.getOrNull() }, { it.name })
    var nick by stored("nick", "", { it }, { it })
    var memoryMb by stored("memoryMb", defaultMemoryMb(), String::toIntOrNull, Int::toString)

    /** Off: the Java runtime Mojang ships for each version. On: [javaPath], for every version. */
    var customJava by stored("customJava", false, { it == "true" }, { it.toString() })
    var javaPath by stored("javaPath", "", { it }, { it })
    var afterLaunch by stored("afterLaunch", AfterLaunch.Minimize, { runCatching { AfterLaunch.valueOf(it) }.getOrNull() }, { it.name })
    var selectedInstance by stored("selectedInstance", "", { it }, { it })

    /** The friends profile's name and its key from the friends server; empty until one is made. */
    var friendsName by stored("friendsName", "", { it }, { it })
    var friendsToken by stored("friendsToken", "", { it }, { it })

    /** Another friends server than the launcher's own; empty for that one. */
    var friendsServer by stored("friendsServer", "", { it }, { it })

    /** Whether friends see which pack and which server, or only that the player is on. */
    var friendsShare by stored("friendsShare", true, { it == "true" }, { it.toString() })

    /** The home screen's friends, folded down to their header. */
    var friendsHidden by stored("friendsHidden", false, { it == "true" }, { it.toString() })

    /** Minecraft's own settings — keys, sound, video, language — the same in every pack; see `GameOptions`. */
    var syncOptions by stored("syncOptions", true, { it == "true" }, { it.toString() })

    var sceneQuality by stored("sceneQuality", SceneQuality.Ultra, { runCatching { SceneQuality.valueOf(it) }.getOrNull() }, { it.name })
    var sceneMotion by stored("sceneMotion", SceneMotion.Both, { runCatching { SceneMotion.valueOf(it) }.getOrNull() }, { it.name })
    var sceneFps by stored("sceneFps", 60, String::toIntOrNull, Int::toString)

    /** The island holds still while the game runs, leaving the GPU to it; off, it keeps moving. */
    var scenePausedInGame by stored("scenePausedInGame", true, { it == "true" }, { it.toString() })

    /** The home screen's news, folded down to their header so the island has the corner. */
    var newsHidden by stored("newsHidden", false, { it == "true" }, { it.toString() })

    /** A personal CurseForge API key; without one only Modrinth is searched. */
    var curseForgeKey by stored("curseForgeKey", "", { it }, { it })

    /** Where Minecraft is downloaded to; empty until the player picks it on the first download. */
    var gameDir by stored("gameDir", "", { it }, { it })

    /** Mojang's EULA, accepted once in the new-server form; every server made after that inherits it. */
    var serverEulaAccepted by stored("serverEulaAccepted", false, { it == "true" }, { it.toString() })

    // Ely.by: the token pair from signing in, never the password.
    private var elyAccessToken by stored("elyAccessToken", "", { it }, { it })
    private var elyClientTokenStored by stored("elyClientToken", "", { it }, { it })
    private var elyUuid by stored("elyUuid", "", { it }, { it })
    private var elyName by stored("elyName", "", { it }, { it })

    /** Identifies this launcher to Ely.by; a token is only ever refreshed by the client it was issued to. */
    val elyClientToken: String
        get() = elyClientTokenStored.ifEmpty { UUID.randomUUID().toString().replace("-", "").also { elyClientTokenStored = it } }

    var elySession: ElySession?
        get() = if (elyAccessToken.isEmpty() || elyName.isEmpty()) null else ElySession(elyAccessToken, elyClientToken, elyUuid, elyName)
        set(value) {
            elyAccessToken = value?.accessToken.orEmpty()
            elyUuid = value?.uuid.orEmpty()
            elyName = value?.name.orEmpty()
        }

    // Microsoft: the refresh token that renews the sign-in, and the game's current token with its profile.
    private var msRefreshToken by stored("msRefreshToken", "", { it }, { it })
    private var msAccessToken by stored("msAccessToken", "", { it }, { it })
    private var msExpiresAt by stored("msExpiresAt", 0L, String::toLongOrNull, Long::toString)
    private var msUuid by stored("msUuid", "", { it }, { it })
    private var msName by stored("msName", "", { it }, { it })
    private var msXuid by stored("msXuid", "", { it }, { it })
    private var msSkin by stored("msSkin", "", { it }, { it })

    var microsoftSession: MicrosoftSession?
        get() = if (msRefreshToken.isEmpty() || msName.isEmpty()) null
        else MicrosoftSession(msRefreshToken, msAccessToken, msExpiresAt, msUuid, msName, msXuid, msSkin.ifEmpty { null })
        set(value) {
            msRefreshToken = value?.refreshToken.orEmpty()
            msAccessToken = value?.accessToken.orEmpty()
            msExpiresAt = value?.expiresAt ?: 0L
            msUuid = value?.uuid.orEmpty()
            msName = value?.name.orEmpty()
            msXuid = value?.xuid.orEmpty()
            msSkin = value?.skin.orEmpty()
        }

    /** Minecraft's own rule for a player name. */
    val nickValid: Boolean get() = NICK.matches(nick)

    private fun <T> stored(key: String, default: T, parse: (String) -> T?, format: (T) -> String) = object : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(store.get(key, null)?.let(parse) ?: default)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            store.put(key, format(value))
        }
    }

    companion object {
        private val NICK = Regex("^[A-Za-z0-9_]{3,16}$")

        /** Physical memory, for the memory slider's upper end. */
        val systemMemoryMb: Int = runCatching {
            ((ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024)).toInt()
        }.getOrDefault(8192)

        /** A quarter of the machine, kept between 2 and 6 GB: modern versions want 2, few packs gain past 6. */
        private fun defaultMemoryMb(): Int = (systemMemoryMb / 4 / 512 * 512).coerceIn(2048, 6144)
    }
}

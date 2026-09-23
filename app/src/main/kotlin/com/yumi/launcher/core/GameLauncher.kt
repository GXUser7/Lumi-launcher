package com.yumi.launcher.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

/**
 * Who the game runs as. Offline: a nick and the UUID a server derives from it, with no session
 * behind them. Ely.by: a real session, and [injector] — authlib-injector — pointing the game at
 * Ely.by's account servers instead of Mojang's.
 */
internal class GameAccount(val name: String, val uuid: String, val accessToken: String, val userType: String, val injector: File? = null) {
    companion object {
        fun offline(nick: String) = GameAccount(nick, GameLauncher.offlineUuid(nick), "0", "legacy")
    }
}

internal class LaunchOptions(
    val account: GameAccount,
    val gameDir: File,
    val memoryMb: Int,
    val jvmArgs: List<String>,
    val width: Int?,
    val height: Int?,
    val fullscreen: Boolean,
    /** `host:port` to connect to straight from the title screen. */
    val join: String? = null,
)

internal object GameLauncher {
    private const val BRAND = "Yumi"
    private const val VERSION = "0.1.0"

    /**
     * The id a server gives a player without an account: the same name-based UUID the vanilla
     * server computes for offline mode, so a world keeps the inventory of whoever plays under a nick.
     */
    fun offlineUuid(nick: String): String = UUID.nameUUIDFromBytes("OfflinePlayer:$nick".toByteArray()).toString().replace("-", "")

    fun command(prepared: Prepared, options: LaunchOptions): List<String> {
        val profile = prepared.profile
        val classpath = prepared.classpath.joinToString(Platform.classpathSeparator) { it.path }
        val assetIndex = profile.assetIndex?.str("id") ?: "legacy"
        val account = options.account
        // Versions since 1.20 connect through Quick Play; older ones take `--server` and `--port`.
        val quickPlay = profile.gameArguments?.toString()?.contains("is_quick_play_multiplayer") == true
        val join = options.join?.let(::splitAddress)
        val values = mapOf(
            "natives_directory" to prepared.natives.path,
            "launcher_name" to BRAND,
            "launcher_version" to VERSION,
            "classpath" to classpath,
            "classpath_separator" to Platform.classpathSeparator,
            "library_directory" to LauncherPaths.libraries.path,
            "version_name" to profile.id,
            "version_type" to profile.type,
            "game_directory" to options.gameDir.path,
            "assets_root" to LauncherPaths.assets.path,
            "game_assets" to (prepared.virtualAssets ?: LauncherPaths.assets).path,
            "assets_index_name" to assetIndex,
            "auth_player_name" to account.name,
            "auth_uuid" to account.uuid,
            // Offline play has no session behind these, and the game accepts that.
            "auth_access_token" to account.accessToken,
            "auth_session" to if (account.accessToken == "0") "0" else "token:${account.accessToken}:${account.uuid}",
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to account.userType,
            "user_properties" to "{}",
            "resolution_width" to (options.width ?: 854).toString(),
            "resolution_height" to (options.height ?: 480).toString(),
            // As typed: without a port the game looks up the server's SRV record itself.
            "quickPlayMultiplayer" to options.join?.trim().orEmpty(),
        )
        val features = buildSet {
            if (options.width != null && options.height != null) add("has_custom_resolution")
            if (join != null && quickPlay) add("is_quick_play_multiplayer")
        }

        val command = mutableListOf(prepared.java.path, "-Xmx${options.memoryMb}M", "-Xms${minOf(options.memoryMb, 1024)}M")
        // The log is read back from a file; without these a Russian Windows writes it in cp1251.
        command += listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
        prepared.loggingArgument?.let { command += it }
        account.injector?.let { command += AuthlibInjector.argument(it) }
        command += options.jvmArgs
        command += profile.jvmArguments?.let { expand(it, values, features) }
            ?: listOf("-Djava.library.path=${prepared.natives.path}", "-cp", classpath)
        command += profile.mainClass
        command += profile.gameArguments?.let { expand(it, values, features) }
            ?: profile.legacyArguments.orEmpty().split(' ').filter { it.isNotBlank() }.map { substitute(it, values) }
        if (options.fullscreen) command += "--fullscreen"
        if (join != null && !quickPlay) command += listOf("--server", join.first, "--port", join.second.toString())
        return command
    }

    /** Output goes to a file rather than a pipe, so the game outlives the launcher if the launcher closes. */
    fun start(command: List<String>, gameDir: File, log: File): Process {
        gameDir.mkdirs()
        log.parentFile.mkdirs()
        return ProcessBuilder(command)
            .directory(gameDir)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
    }

    private fun expand(arguments: JsonArray, values: Map<String, String>, features: Set<String>): List<String> = buildList {
        for (argument in arguments) {
            when (argument) {
                is JsonPrimitive -> add(substitute(argument.content, values))
                is JsonObject -> {
                    if (!allowed(argument["rules"]?.jsonArray, features)) continue
                    when (val value = argument["value"]) {
                        is JsonPrimitive -> add(substitute(value.content, values))
                        is JsonArray -> value.forEach { add(substitute(it.jsonPrimitive.content, values)) }
                        else -> Unit
                    }
                }
                else -> Unit
            }
        }
    }

    /** `host`, `host:port` → host and port, 25565 when none is given. */
    fun splitAddress(address: String): Pair<String, Int> {
        val trimmed = address.trim()
        val colon = trimmed.lastIndexOf(':')
        val port = if (colon > 0) trimmed.substring(colon + 1).toIntOrNull() else null
        return if (port != null) trimmed.substring(0, colon) to port else trimmed to 25565
    }

    private val placeholder = Regex("""\$\{([^}]+)}""")

    private fun substitute(text: String, values: Map<String, String>): String =
        placeholder.replace(text) { values[it.groupValues[1]] ?: it.value }
}

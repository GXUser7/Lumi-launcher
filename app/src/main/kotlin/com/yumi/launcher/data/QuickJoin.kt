package com.yumi.launcher.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.yumi.launcher.core.LauncherJson
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.PingResult
import com.yumi.launcher.core.ServerPing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** A server someone else runs, kept for a one-click join; [packId] is the pack to join it with. */
@Serializable
data class SavedServer(
    val id: String,
    val name: String,
    val address: String,
    val packId: String? = null,
    val added: Long = System.currentTimeMillis(),
)

sealed interface PingState {
    data object Loading : PingState
    data class Online(val result: PingResult) : PingState
    data class Offline(val message: String) : PingState
}

/**
 * The quick-join list, like the game's own multiplayer screen but outside it: each server pinged
 * for its description, icon and players, and joined straight from the launcher. Kept with the
 * launcher's settings, not in a pack, since one server may be played from several packs.
 */
class QuickJoinList(private val scope: CoroutineScope) {
    val all = mutableStateListOf<SavedServer>()
    val pings = mutableStateMapOf<String, PingState>()

    private val file get() = LauncherPaths.config.resolve("servers.json")

    init {
        all += runCatching { LauncherJson.decodeFromString(ListSerializer(SavedServer.serializer()), file.readText()) }.getOrDefault(emptyList())
    }

    operator fun get(id: String?): SavedServer? = all.firstOrNull { it.id == id }

    fun add(address: String, name: String, packId: String?): SavedServer {
        val server = SavedServer(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { address.trim() }, address.trim(), packId)
        all.add(0, server)
        save()
        ping(server)
        return server
    }

    fun update(server: SavedServer) {
        val at = all.indexOfFirst { it.id == server.id }
        if (at < 0) return
        val addressChanged = all[at].address != server.address
        all[at] = server
        save()
        if (addressChanged) ping(server)
    }

    fun remove(server: SavedServer) {
        all.removeAll { it.id == server.id }
        pings.remove(server.id)
        save()
    }

    fun ping(server: SavedServer) {
        pings[server.id] = PingState.Loading
        scope.launch {
            pings[server.id] = try {
                PingState.Online(ServerPing.ping(server.address))
            } catch (e: Exception) {
                PingState.Offline(e.message ?: "Нет ответа")
            }
        }
    }

    fun pingAll() = all.forEach(::ping)

    private fun save() {
        file.parentFile.mkdirs()
        file.writeText(LauncherJson.encodeToString(ListSerializer(SavedServer.serializer()), all.toList()))
    }
}

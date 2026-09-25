package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.yumi.launcher.core.ElyBy
import com.yumi.launcher.core.FriendPack
import com.yumi.launcher.core.FriendsApi
import com.yumi.launcher.core.FriendsView
import com.yumi.launcher.core.Presence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Friends, and whether they are on: a profile on the friends server under a unique name, requests
 * both ways, and every half a minute — or at once when the game starts, stops or changes server —
 * what this player is doing goes out and what their friends are doing comes back.
 *
 * Nothing is sent before the player makes a profile. After that friends see only what
 * [LauncherSettings.friendsShare] allows: the pack and the server, or just "in the game".
 */
class FriendsModel(private val model: LauncherModel, private val scope: CoroutineScope) {
    private val settings get() = model.settings

    var view by mutableStateOf<FriendsView?>(null)
        private set

    /** The last thing that went wrong, for the card to show; cleared by the next thing that works. */
    var error by mutableStateOf<String?>(null)

    /** A sign-up or a request is on its way. */
    var working by mutableStateOf(false)
        private set

    /** Skin textures of friends whose name is an Ely.by account too; null for those without one. */
    private val skins = mutableStateMapOf<String, String?>()

    private val nudge = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The servers friends are on, and whether a pack here fits the one a Yumi server shares. */
    internal val servers = FriendServers(model, scope)

    /** No server is built in: players run their own (`friends-server/`) and share its address. */
    val url: String get() = settings.friendsServer.trim().trimEnd('/')
    val available: Boolean get() = url.isNotEmpty()
    val signedIn: Boolean get() = settings.friendsToken.isNotEmpty()
    val online: Int get() = view?.friends?.count { it.online } ?: 0

    init {
        scope.launch {
            while (true) {
                if (available && signedIn) report()
                withTimeoutOrNull(REPORT_EVERY) { nudge.first() }
            }
        }
        // The moment something friends would see changes, not half a minute later.
        scope.launch {
            snapshotFlow { listOf(model.status is PlayStatus.Running, model.gameServer, settings.friendsShare, settings.friendsToken, url) }
                .collect { nudge.tryEmit(Unit) }
        }
        scope.launch {
            snapshotFlow { view?.friends.orEmpty().filter { it.online }.mapNotNull { it.server }.toSet() }
                .collect(servers::follow)
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            if (available && signedIn) FriendsApi(url, settings.friendsToken).offlineNow()
        })
    }

    fun register(name: String) = act { api ->
        val (taken, token) = api.register(name.trim())
        settings.friendsName = taken
        settings.friendsToken = token
        report()
    }

    fun add(name: String) = act { view = it.add(name.trim()) }
    fun accept(name: String) = act { view = it.accept(name) }
    fun decline(name: String) = act { view = it.decline(name) }
    fun remove(name: String) = act { view = it.remove(name) }

    /** The profile off the server — the name is free again — and out of the launcher. */
    fun deleteProfile() = act { api ->
        api.delete()
        forget()
    }

    fun refresh() {
        nudge.tryEmit(Unit)
    }

    /** A friend's skin, when their name is also an Ely.by account; looked up once. */
    fun skin(name: String): String? {
        if (name !in skins) {
            skins[name] = null
            scope.launch { skins[name] = runCatching { ElyBy.skinUrl(name) }.getOrNull() }
        }
        return skins[name]
    }

    private suspend fun report() {
        try {
            view = FriendsApi(url, settings.friendsToken).presence(presence())
            error = null
        } catch (e: FriendsApi.Unauthorized) {
            forget()
            error = e.message
        } catch (e: Exception) {
            error = e.message ?: "Нет связи с сервером друзей"
        }
    }

    private fun act(block: suspend (FriendsApi) -> Unit) {
        if (working || !available) return
        working = true
        error = null
        scope.launch {
            try {
                block(FriendsApi(url, settings.friendsToken))
            } catch (e: FriendsApi.Unauthorized) {
                forget()
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Нет связи с сервером друзей"
            } finally {
                working = false
            }
        }
    }

    private fun forget() {
        settings.friendsToken = ""
        settings.friendsName = ""
        view = null
    }

    /** In the game with the pack and the server if the player shares them; in the launcher otherwise. */
    private fun presence(): Presence {
        val running = model.status as? PlayStatus.Running ?: return Presence("launcher")
        if (!settings.friendsShare) return Presence("game")
        val pack = model.instances[running.instanceId]?.let { FriendPack(it.name, it.loader.label, it.version) }
        return Presence("game", pack, model.gameServer?.let(::reachable))
    }

    /**
     * The address as a friend can use it. The game joins a server run on this computer by
     * `localhost` or a local address, which means nothing elsewhere: that becomes the address the
     * servers screen gives friends, a VPN's first — a friend playing along is more likely on the same
     * Radmin or ZeroTier network than in the same house. Null when there is none to give.
     */
    private fun reachable(address: String): String? {
        val host = address.substringBeforeLast(':').trim('[', ']')
        val port = address.substringAfterLast(':', "25565").toIntOrNull() ?: 25565
        val local = localAddresses()
        val own = host == "localhost" || host.startsWith("127.") || host == "::1" || host == "0.0.0.0" || local.any { it.ip == host }
        if (!own) return address
        val hosted = model.servers.all.any { model.servers.session(it.id).status is ServerStatus.Running && model.servers.port(it) == port }
        if (!hosted) return null
        val pick = local.firstOrNull { it.network != LAN } ?: local.firstOrNull() ?: return null
        return if (port == 25565) pick.ip else "${pick.ip}:$port"
    }

    private companion object {
        /** Seconds between reports; the server counts a player gone after two and a half of them. */
        const val REPORT_EVERY = 30_000L
        const val LAN = "Локальная сеть"
    }
}

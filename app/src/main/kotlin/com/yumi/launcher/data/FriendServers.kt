package com.yumi.launcher.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.yumi.launcher.core.ModHints
import com.yumi.launcher.core.ServerPacks
import com.yumi.launcher.core.ServerPing
import com.yumi.launcher.core.ServerPlan
import com.yumi.launcher.core.SharedPack
import com.yumi.launcher.core.sha1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Whether a pack here is the one a friend's Yumi server shares. */
internal sealed interface PackCheck {
    data object Checking : PackCheck

    /** [packId] has the server's game, loader and every mod of its pack that could be found. */
    data class Ready(val packId: String) : PackCheck

    /** No pack here has it; [plan] is what making one takes. */
    data class Missing(val plan: ServerPlan) : PackCheck

    /** The server's pack could not be looked up; the dialog tries again. */
    data class Failed(val message: String) : PackCheck
}

/**
 * The servers friends are on, for the friends card: each pinged as it turns up, and for one run
 * from Yumi Launcher — its ping names its pack — whether a pack here already has that pack's game,
 * loader and mods, the way "Сборка сервера" would make it. By address, so two friends on one
 * server make one ping.
 */
internal class FriendServers(private val model: LauncherModel, private val scope: CoroutineScope) {
    val pings = mutableStateMapOf<String, PingState>()
    val checks = mutableStateMapOf<String, PackCheck>()

    /** A pack's plan costs a few catalogue requests; the pack itself changes only when the host changes it. */
    private val plans = ConcurrentHashMap<SharedPack, ServerPlan>()

    /** SHA-1 of the jars already read, by path, with the size and time it was read at. */
    private val sums = ConcurrentHashMap<String, Triple<Long, Long, String>>()

    private val jobs = HashMap<String, Job>()

    init {
        // A pack made, deleted or moved to another version changes what matches — and a new one
        // matches only once its mods are in, which is when the import is finished.
        scope.launch {
            snapshotFlow { model.instances.all.map { Triple(it.id, it.version, it.loader) } to model.packImports.finished?.id }
                .drop(1)
                .collect { pings.keys.forEach(::check) }
        }
    }

    /** [addresses] are where friends are now: new ones are pinged, those nobody is on any more forgotten. */
    fun follow(addresses: Set<String>) {
        (pings.keys - addresses).forEach {
            pings.remove(it)
            checks.remove(it)
            jobs.remove(it)?.cancel()
        }
        (addresses - pings.keys).forEach(::ping)
    }

    /** Pings them all again — the host may have restarted with other mods — and checks the packs here. */
    fun refresh() = pings.keys.toList().forEach(::ping)

    private fun ping(address: String) {
        if (pings[address] !is PingState.Online) pings[address] = PingState.Loading
        scope.launch {
            val state = try {
                PingState.Online(ServerPing.ping(address))
            } catch (e: Exception) {
                PingState.Offline(e.message ?: "Нет ответа")
            }
            // The friend may have left while it was on its way.
            if (address !in pings) return@launch
            pings[address] = state
            check(address)
        }
    }

    private fun check(address: String) {
        val result = (pings[address] as? PingState.Online)?.result
        val shared = result?.mods as? ModHints.Yumi
        jobs.remove(address)?.cancel()
        if (result == null || shared == null) {
            checks.remove(address)
            return
        }
        // A check that is already there stays on screen while it runs again.
        if (address !in checks) checks[address] = PackCheck.Checking
        val packs = model.instances.all.toList()
        jobs[address] = scope.launch {
            val check = try {
                val plan = plans[shared.pack] ?: run {
                    // The plan checks the game version against Mojang's list, which may still be loading.
                    withTimeoutOrNull(30_000) { snapshotFlow { model.manifest != null || model.manifestError != null }.first { it } }
                    ServerPacks.plan(result, model.lookup, model.settings.curseForgeKey).also { plans[shared.pack] = it }
                }
                withContext(Dispatchers.IO) { matching(plan, packs) }?.let { PackCheck.Ready(it.id) } ?: PackCheck.Missing(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PackCheck.Failed(e.message ?: "Не удалось проверить сборку сервера")
            }
            if (address in pings) checks[address] = check
        }
    }

    /**
     * The first of [packs] on the plan's game and loader with each of its mods in the mods folder,
     * switched on: the same file by SHA-1, or by name for one without a hash. Extra mods are fine —
     * a minimap more does not keep anyone off a server.
     */
    private fun matching(plan: ServerPlan, packs: List<Instance>): Instance? = packs
        .filter { it.version == plan.minecraft && it.loader == plan.loader }
        .firstOrNull { pack ->
            val jars = model.instances.modsDir(pack).listFiles { f -> f.isFile && f.name.endsWith(".jar") }.orEmpty()
            val names = jars.mapTo(HashSet()) { it.name }
            val hashes by lazy { jars.mapTo(HashSet()) { sumOf(it) } }
            plan.mods.all { mod -> mod.sha1?.let { it.lowercase() in hashes } ?: (mod.fileName in names) }
        }

    private fun sumOf(jar: File): String {
        val size = jar.length()
        val modified = jar.lastModified()
        sums[jar.path]?.let { (s, m, sum) -> if (s == size && m == modified) return sum }
        return sha1(jar).also { sums[jar.path] = Triple(size, modified, it) }
    }
}

package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.Download
import com.yumi.launcher.core.Http
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.PackArchives
import com.yumi.launcher.core.PackCatalog
import com.yumi.launcher.core.PackHit
import com.yumi.launcher.core.PackRelease
import com.yumi.launcher.core.ServerPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A modpack being put in: a release from a catalogue or an archive from disk becomes a new pack.
 * It lives in the model rather than on the packs screen, so leaving the screen — or the section —
 * does not cancel a download of a few hundred mods half-way. One at a time.
 */
class PackImporter(private val model: LauncherModel, private val scope: CoroutineScope) {
    /** The pack's name while an import runs; null when none does. */
    var title by mutableStateOf<String?>(null)
        private set
    var stage by mutableStateOf("")
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)

    /** The pack the last import made, until the screen has shown it. */
    var finished by mutableStateOf<Instance?>(null)

    /** Where the running import came from: "Modrinth:<project id>", or the file's path. */
    var origin by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    val busy: Boolean get() = title != null

    /** [icon] is the glyph the new pack wears until the archive gives it a picture of its own. */
    fun installFile(file: File, icon: Int) = start(file.nameWithoutExtension, file.path) { importArchive(file, null, null, icon, 0f) }

    internal fun install(source: ModSource, project: PackHit, release: PackRelease, icon: Int) = start(project.name, "$source:${project.id}") {
        val key = model.settings.curseForgeKey.trim()
        stage = "Скачиваем архив сборки"
        val ext = if (source == ModSource.Modrinth) "mrpack" else "zip"
        val archive = LauncherPaths.cache.resolve("modpacks/${source.name}-${release.id}.$ext")
        Http.downloadAll(listOf(Download(release.url, archive, release.sha1, release.size))) { report(it * 0.1f) }
        val server = release.serverId?.takeIf { it > 0 }?.let { PackCatalog.serverRelease(project.id, it, key) }
        importArchive(archive, server, project.icon, icon, 0.1f)
    }

    /**
     * A pack for [server] from what its ping gave away ([plan]): the game, the loader and the mods
     * found for it. It becomes the pack that server is joined with.
     */
    internal fun installForServer(server: SavedServer, plan: ServerPlan, icon: Int) = start(server.name, "server:${server.id}") {
        val instances = model.instances
        val pack = instances.create(server.name, plan.minecraft, icon)
        try {
            instances.save(pack.copy(loader = plan.loader, loaderVersion = plan.loaderVersion))
            stage = "Скачиваем моды"
            val mods = instances.modsDir(pack)
            Http.downloadAll(plan.mods.map { Download(it.url, mods.resolve(it.fileName), it.sha1, it.size) }) { report(it * 0.98f) }
            withContext(Dispatchers.IO) {
                model.mods.record(pack, plan.mods.map { InstalledMod(it.fileName, it.source, it.projectId, it.title, it.iconUrl, it.version) })
            }
            report(1f)
            instances[pack.id]?.let {
                model.select(it)
                model.quickJoin[server.id]?.let { saved -> model.quickJoin.update(saved.copy(packId = it.id)) }
                finished = it
            }
        } catch (e: Throwable) {
            instances.delete(pack)
            throw e
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun start(name: String, from: String, work: suspend () -> Unit) {
        if (busy) return
        model.withGameFolder {
            title = name
            origin = from
            stage = "Готовимся"
            progress = 0f
            error = null
            finished = null
            job = scope.launch {
                try {
                    work()
                } catch (e: CancellationException) {
                    error = null
                } catch (e: Exception) {
                    error = "Не удалось установить «$name»: ${e.message ?: e::class.simpleName}"
                } finally {
                    title = null
                    origin = null
                    job = null
                }
            }
        }
    }

    /**
     * Makes the pack and fills it: its version and loader from the archive's index, the mods it
     * lists, then its overrides. A pack that did not finish is taken out again, not left half-filled.
     */
    private suspend fun importArchive(file: File, server: PackRelease?, iconUrl: String?, icon: Int, from: Float) {
        val info = PackArchives.inspect(file)
        title = info.name
        val instances = model.instances
        val pack = instances.create(info.name, info.version, icon)
        try {
            instances.save(pack.copy(loader = info.loader, loaderVersion = info.loaderVersion, iconUrl = iconUrl))
            stage = "Скачиваем моды"
            PackArchives.install(file, instances.folder(pack), model.settings.curseForgeKey.trim()) { report(from + it * (0.95f - from)) }
            if (server != null) {
                stage = "Скачиваем серверную часть"
                val archive = LauncherPaths.cache.resolve("modpacks/server-${server.id}.zip")
                Http.downloadAll(listOf(Download(server.url, archive, server.sha1, server.size))) { report(0.95f + it * 0.04f) }
                PackArchives.installServerArchive(archive, instances.folder(pack))
            }
            report(1f)
            instances[pack.id]?.let {
                model.select(it)
                finished = it
            }
        } catch (e: Throwable) {
            instances.delete(pack)
            throw e
        }
    }

    /** Progress comes from download threads; the state it lands in belongs to the UI thread. */
    private fun report(value: Float) {
        scope.launch(Dispatchers.Main) { progress = value.coerceIn(0f, 1f) }
    }
}

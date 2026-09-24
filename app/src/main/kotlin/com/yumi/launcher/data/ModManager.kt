package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.CurseForge
import com.yumi.launcher.core.Download
import com.yumi.launcher.core.Http
import com.yumi.launcher.core.HttpRefused
import com.yumi.launcher.core.LauncherJson
import com.yumi.launcher.core.ModCatalog
import com.yumi.launcher.core.ModDetails
import com.yumi.launcher.core.ModHit
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.Modrinth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** What the launcher remembers about a mod it installed, so the list can show more than a file name. */
@Serializable
data class InstalledMod(
    val fileName: String,
    val source: ModSource,
    val projectId: String,
    val title: String,
    val iconUrl: String? = null,
    val version: String = "",
)

/** A jar in a pack's mods folder; [meta] is null for files put there by hand. */
data class ModEntry(val file: File, val enabled: Boolean, val meta: InstalledMod?) {
    val title: String get() = meta?.title ?: file.name.removeSuffix(DISABLED).removeSuffix(".jar")
}

private const val DISABLED = ".disabled"

/**
 * Mods for one pack at a time: catalogue search, installation with required dependencies, and the
 * pack's mods folder — where a disabled mod is simply renamed to `.jar.disabled`, which is what
 * every loader and every other launcher understands too.
 */
class ModManager(private val model: LauncherModel, private val scope: CoroutineScope) {
    var source by mutableStateOf(ModSource.Modrinth)
    var query by mutableStateOf("")
    val results = mutableStateListOf<ModHit>()
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var more by mutableStateOf(false)
        private set

    /** Keyed by `source:id` while a mod and its dependencies download. */
    val installing = mutableStateMapOf<String, Boolean>()
    var notice by mutableStateOf<String?>(null)

    val installed = mutableStateListOf<ModEntry>()
    private var searchJob: Job? = null

    private fun catalog(): ModCatalog? = when (source) {
        ModSource.Modrinth -> Modrinth
        ModSource.CurseForge -> model.settings.curseForgeKey.trim().takeIf { it.isNotEmpty() }?.let(::CurseForge)
    }

    /** Runs the current query for [instance]; typing restarts it after a short pause. */
    fun search(instance: Instance, append: Boolean = false, debounce: Boolean = false) {
        searchJob?.cancel()
        val catalog = catalog() ?: run { results.clear(); return }
        searchJob = scope.launch {
            if (debounce) delay(350)
            searching = true
            searchError = null
            try {
                val page = catalog.search(query.trim(), instance.version, instance.loader, if (append) results.size else 0)
                if (!append) results.clear()
                results += page.filter { hit -> results.none { it.id == hit.id } }
                more = page.size == 20
            } catch (e: HttpRefused) {
                searchError = if (source == ModSource.CurseForge && e.status == 403) "CurseForge не принял ключ — проверьте его в настройках" else e.message
            } catch (e: Exception) {
                searchError = "Не удалось найти: ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    /** [hit]'s page in its own catalogue — description, pictures, tags; null when it cannot be had. */
    suspend fun details(hit: ModHit): ModDetails? = runCatching {
        val catalog = when (hit.source) {
            ModSource.Modrinth -> Modrinth
            ModSource.CurseForge -> CurseForge(model.settings.curseForgeKey.trim())
        }
        catalog.details(hit.id)
    }.getOrNull()

    fun isInstalled(hit: ModHit): Boolean = installed.any { it.meta?.source == hit.source && it.meta.projectId == hit.id }

    /**
     * Installs [hit] and, recursively, every mod it requires. A dependency that has no build for
     * this version is reported and skipped rather than undoing the mod that asked for it.
     */
    fun install(instance: Instance, hit: ModHit) {
        val catalog = catalog() ?: return
        val key = "${hit.source}:${hit.id}"
        if (installing[key] == true) return
        installing[key] = true
        notice = null
        scope.launch {
            val problems = mutableListOf<String>()
            try {
                installTree(catalog, instance, hit.id, hit, mutableSetOf(), problems, root = true)
                if (problems.isNotEmpty()) notice = problems.joinToString("\n")
            } catch (e: Exception) {
                notice = e.message
            } finally {
                installing.remove(key)
                refresh(instance)
            }
        }
    }

    private suspend fun installTree(
        catalog: ModCatalog,
        instance: Instance,
        projectId: String,
        known: ModHit?,
        visited: MutableSet<String>,
        problems: MutableList<String>,
        root: Boolean,
    ) {
        if (!visited.add(projectId)) return
        if (!root && installed.any { it.meta?.source == source && it.meta.projectId == projectId }) return
        val hit = known ?: catalog.describe(projectId)
        val file = catalog.file(projectId, instance.version, instance.loader)
        if (file == null) {
            val message = "«${hit.title}» не выпущен для ${instance.loader.label} ${instance.version}"
            if (root) error(message) else { problems += message; return }
        }
        val url = file.url
        if (url == null) {
            val message = "Автор «${hit.title}» запретил скачивание через лаунчеры — скачайте его вручную: ${hit.pageUrl}"
            if (root) error(message) else { problems += message; return }
        }
        val modsDir = model.instances.modsDir(instance)
        Http.downloadAll(listOf(Download(url, modsDir.resolve(file.fileName), file.sha1, file.size))) {}
        remember(instance, InstalledMod(file.fileName, hit.source, projectId, hit.title, hit.iconUrl, file.version))
        withContext(Dispatchers.Main) { refresh(instance) }
        for (dependency in file.requires) installTree(catalog, instance, dependency, null, visited, problems, root = false)
    }

    /** Re-reads the pack's mods folder. */
    fun refresh(instance: Instance) {
        val meta = load(instance).associateBy { it.fileName }
        val files = model.instances.modsDir(instance).listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar$DISABLED")) }
            .sortedBy { it.name.lowercase() }
        installed.clear()
        installed += files.map { ModEntry(it, !it.name.endsWith(DISABLED), meta[it.name.removeSuffix(DISABLED)]) }
            .sortedBy { it.title.lowercase() }
    }

    fun setEnabled(instance: Instance, entry: ModEntry, enabled: Boolean) {
        val name = entry.file.name.removeSuffix(DISABLED)
        val target = entry.file.resolveSibling(if (enabled) name else name + DISABLED)
        if (target != entry.file) entry.file.renameTo(target)
        refresh(instance)
    }

    fun delete(instance: Instance, entry: ModEntry) {
        entry.file.delete()
        val name = entry.file.name.removeSuffix(DISABLED)
        save(instance, load(instance).filterNot { it.fileName == name })
        refresh(instance)
    }

    /** File name → title, for the mods the launcher installed into [instance]. */
    fun titles(instance: Instance): Map<String, String> = load(instance).associate { it.fileName to it.title }

    /** Remembers mods put into [instance] other than from the catalogue — for a server — so the list shows their names. */
    internal fun record(instance: Instance, mods: List<InstalledMod>) {
        val names = mods.map { it.fileName }.toSet()
        save(instance, load(instance).filter { it.fileName !in names } + mods)
    }

    private fun metaFile(instance: Instance) = model.instances.folder(instance).resolve("mods.json")

    private fun load(instance: Instance): List<InstalledMod> = runCatching {
        LauncherJson.decodeFromString(ListSerializer(InstalledMod.serializer()), metaFile(instance).readText())
    }.getOrDefault(emptyList())

    private fun save(instance: Instance, mods: List<InstalledMod>) {
        metaFile(instance).writeText(LauncherJson.encodeToString(ListSerializer(InstalledMod.serializer()), mods))
    }

    @Synchronized
    private fun remember(instance: Instance, mod: InstalledMod) {
        save(instance, load(instance).filterNot { it.fileName == mod.fileName || (it.source == mod.source && it.projectId == mod.projectId) } + mod)
    }
}

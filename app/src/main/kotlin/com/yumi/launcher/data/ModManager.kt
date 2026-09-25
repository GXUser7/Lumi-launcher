package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.Categories
import com.yumi.launcher.core.Category
import com.yumi.launcher.core.ContentType
import com.yumi.launcher.core.CurseForge
import com.yumi.launcher.core.Download
import com.yumi.launcher.core.Http
import com.yumi.launcher.core.HttpRefused
import com.yumi.launcher.core.LauncherJson
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.ModCatalog
import com.yumi.launcher.core.ModDetails
import com.yumi.launcher.core.ModHit
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.Modrinth
import com.yumi.launcher.core.SearchFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.Properties

/** What the launcher remembers about a mod, shader or resource pack it installed, so the list can show more than a file name. */
@Serializable
data class InstalledMod(
    val fileName: String,
    val source: ModSource,
    val projectId: String,
    val title: String,
    val iconUrl: String? = null,
    val version: String = "",
)

/**
 * A file in one of a pack's content folders; [meta] is null for files put there by hand. What
 * [enabled] means depends on the kind: a mod that is not renamed away, a resource pack switched on
 * in the game's list, the shader Iris is set to.
 */
data class ModEntry(val file: File, val enabled: Boolean, val meta: InstalledMod?) {
    val title: String get() = meta?.title ?: file.name.removeSuffix(DISABLED).removeSuffix(".jar").removeSuffix(".zip")
}

private const val DISABLED = ".disabled"

/**
 * Mods, shaders and resource packs for one pack at a time: catalogue search with its filters,
 * installation — with required dependencies, for mods — and the pack's own folders. A disabled mod
 * is simply renamed to `.jar.disabled`, which every loader and every other launcher understands
 * too; resource packs and shaders are switched where the game and Iris keep that choice.
 */
class ModManager(private val model: LauncherModel, private val scope: CoroutineScope) {
    var type by mutableStateOf(ContentType.Mod)
    var source by mutableStateOf(ModSource.Modrinth)
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter())
    val results = mutableStateListOf<ModHit>()
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var more by mutableStateOf(false)
        private set

    /** The current catalogue's categories for the current kind; null while they load, empty when it has none. */
    var categories by mutableStateOf<List<Category>?>(null)
        private set

    /** The pack the last search was for, so the filters panel can search again with new filters. */
    private var searchedFor: Instance? = null

    /** Keyed by `source:id` while a project and its dependencies download; the value is its title. */
    val installing = mutableStateMapOf<String, String>()
    var notice by mutableStateOf<String?>(null)

    /** The pack's files of the current kind. */
    val installed = mutableStateListOf<ModEntry>()
    private var searchJob: Job? = null
    private var categoriesJob: Job? = null

    private fun catalog(of: ModSource = source): ModCatalog? = when (of) {
        ModSource.Modrinth -> Modrinth
        ModSource.CurseForge -> model.settings.curseForgeKey.trim().takeIf { it.isNotEmpty() }?.let(::CurseForge)
    }

    /** Switches between mods, shaders and resource packs; the filters belong to the kind, so they start over. */
    fun show(instance: Instance?, kind: ContentType) {
        if (kind == type) return
        type = kind
        filter = SearchFilter()
        results.clear()
        loadCategories()
        if (instance != null) {
            refresh(instance)
            if (searchable(instance)) search(instance)
        }
    }

    /** Resource packs need no loader; mods and shaders — through Iris — do. */
    fun searchable(instance: Instance) = type == ContentType.ResourcePack || instance.loader != Loader.Vanilla

    fun loadCategories() {
        categoriesJob?.cancel()
        val kind = type
        val from = source
        categoriesJob = scope.launch {
            categories = null
            categories = runCatching { Categories.of(from, kind, model.settings.curseForgeKey.trim()) }.getOrDefault(emptyList())
        }
    }

    /** Runs the current query for [instance]; typing restarts it after a short pause. */
    fun search(instance: Instance, append: Boolean = false, debounce: Boolean = false) {
        searchJob?.cancel()
        searchedFor = instance
        val catalog = catalog() ?: run { results.clear(); return }
        val kind = type
        val filters = filter
        searchJob = scope.launch {
            if (debounce) delay(350)
            searching = true
            searchError = null
            try {
                val page = catalog.search(query.trim(), instance.version, instance.loader, if (append) results.size else 0, kind, filters)
                if (!append) results.clear()
                results += page.filter { hit -> results.none { it.id == hit.id } }
                more = page.size == 20
            } catch (e: CancellationException) {
                // Typing or switching the source started a newer search; this one simply stops.
                throw e
            } catch (e: HttpRefused) {
                searchError = if (source == ModSource.CurseForge && e.status == 403) "CurseForge не принял ключ — проверьте его в настройках" else e.message
            } catch (e: Exception) {
                searchError = "Не удалось найти: ${e.message}"
            } finally {
                // A search cancelled for a newer one must not switch off the newer one's spinner.
                if (searchJob === coroutineContext.job) searching = false
            }
        }
    }

    /** Searches again, for the same pack, with [filters]. */
    fun refilter(filters: SearchFilter) {
        filter = filters
        searchedFor?.let { search(it) }
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
     * Installs [hit] and, for a mod, recursively every mod it requires. A dependency that has no
     * build for this version is reported and skipped rather than undoing the mod that asked for it.
     * A new shader is switched on in Iris if none is yet, a new resource pack in the game's list.
     */
    fun install(instance: Instance, hit: ModHit) {
        val catalog = catalog(hit.source) ?: return
        val key = "${hit.source}:${hit.id}"
        if (key in installing) return
        installing[key] = hit.title
        notice = null
        val kind = hit.type
        scope.launch {
            val problems = mutableListOf<String>()
            var failed: String? = null
            try {
                val file = installTree(catalog, instance, hit.id, hit, mutableSetOf(), problems, root = true, kind)
                withContext(Dispatchers.IO) {
                    val game = model.instances.gameDir(instance)
                    when {
                        file == null -> Unit
                        kind == ContentType.Shader -> if (activeShader(game) == null) setShader(game, file, instance.loader)
                        kind == ContentType.ResourcePack -> editResourcePacks(game) { packs -> if ("file/$file" in packs) packs else packs + "file/$file" }
                    }
                }
                if (problems.isNotEmpty()) notice = problems.joinToString("\n")
            } catch (e: Exception) {
                failed = e.message
                notice = e.message
            } finally {
                installing.remove(key)
                model.downloads.record(hit.title, kind.single, failed)
                if (kind == type) refresh(instance)
            }
        }
    }

    /** Installs one project and — for mods — what it requires; returns the file name it got. */
    private suspend fun installTree(
        catalog: ModCatalog,
        instance: Instance,
        projectId: String,
        known: ModHit?,
        visited: MutableSet<String>,
        problems: MutableList<String>,
        root: Boolean,
        kind: ContentType,
    ): String? {
        if (!visited.add(projectId)) return null
        if (!root && load(instance, ContentType.Mod).any { it.projectId == projectId }) return null
        val hit = known ?: catalog.describe(projectId, kind)
        val file = catalog.file(projectId, instance.version, instance.loader, kind)
        if (file == null) {
            val message = "«${hit.title}» не выпущен для ${if (kind == ContentType.Mod) instance.loader.label + " " else "Minecraft "}${instance.version}"
            if (root) error(message) else { problems += message; return null }
        }
        // A CurseForge file kept off launchers is often on Modrinth byte for byte.
        val url = file.url ?: file.sha1?.let { Modrinth.urlsByHash(listOf(it))[it] }
        if (url == null) {
            val message = "Автор «${hit.title}» запретил скачивание через лаунчеры — скачайте его вручную: ${hit.pageUrl}"
            if (root) error(message) else { problems += message; return null }
        }
        if (!file.exact) problems += "У «${hit.title}» нет версии, отмеченной для ${instance.version}, — поставлена самая новая (${file.version}). Скорее всего, заработает."
        val folder = dir(instance, kind)
        Http.downloadAll(listOf(Download(url, folder.resolve(file.fileName), file.sha1, file.size))) {}
        remember(instance, kind, InstalledMod(file.fileName, hit.source, projectId, hit.title, hit.iconUrl, file.version))
        withContext(Dispatchers.Main) { if (kind == type) refresh(instance) }
        if (kind == ContentType.Mod) for (dependency in file.requires) installTree(catalog, instance, dependency, null, visited, problems, root = false, kind)
        return file.fileName
    }

    /** Whether [instance] has a mod that loads shader packs: Iris, its Forge port Oculus, or OptiFine. */
    fun hasShaderLoader(instance: Instance): Boolean = model.instances.modsDir(instance).listFiles().orEmpty()
        .any { it.name.endsWith(".jar") && SHADER_LOADERS.containsMatchIn(it.name) }

    /** Installs Iris — Oculus on Forge — with what it requires, from Modrinth. */
    fun installShaderLoader(instance: Instance) {
        val slug = if (instance.loader == Loader.Forge) "oculus" else "iris"
        val key = "${ModSource.Modrinth}:$slug"
        if (key in installing) return
        installing[key] = if (slug == "iris") "Iris" else "Oculus"
        notice = null
        scope.launch {
            var failed: String? = null
            try {
                val hit = Modrinth.describe(slug, ContentType.Mod)
                installing[key] = hit.title
                val problems = mutableListOf<String>()
                installTree(Modrinth, instance, hit.id, hit, mutableSetOf(), problems, root = true, ContentType.Mod)
                if (problems.isNotEmpty()) notice = problems.joinToString("\n")
            } catch (e: Exception) {
                failed = e.message
                notice = e.message
            } finally {
                installing.remove(key)
                model.downloads.record(if (slug == "iris") "Iris" else "Oculus", "мод", failed)
                refresh(instance)
            }
        }
    }

    /** Re-reads the pack's folder of the current kind. */
    fun refresh(instance: Instance) {
        val kind = type
        val meta = load(instance, kind).associateBy { it.fileName }
        val game = model.instances.gameDir(instance)
        val files = dir(instance, kind).listFiles().orEmpty().filter { accepts(kind, it) }
        val enabled: (File) -> Boolean = when (kind) {
            ContentType.ResourcePack -> resourcePacks(game).toSet().let { on -> { f -> "file/${f.name}" in on } }
            ContentType.Shader -> activeShader(game).let { on -> { f -> f.name == on } }
            else -> { f -> !f.name.endsWith(DISABLED) }
        }
        installed.clear()
        installed += files.map { ModEntry(it, enabled(it), meta[it.name.removeSuffix(DISABLED)]) }.sortedBy { it.title.lowercase() }
    }

    fun setEnabled(instance: Instance, entry: ModEntry, enabled: Boolean) {
        val game = model.instances.gameDir(instance)
        when (type) {
            ContentType.ResourcePack -> editResourcePacks(game) { packs ->
                val id = "file/${entry.file.name}"
                if (enabled) (if (id in packs) packs else packs + id) else packs - id
            }
            ContentType.Shader -> if (enabled) setShader(game, entry.file.name, instance.loader) else setShader(game, null, instance.loader)
            else -> {
                val name = entry.file.name.removeSuffix(DISABLED)
                val target = entry.file.resolveSibling(if (enabled) name else name + DISABLED)
                if (target != entry.file) entry.file.renameTo(target)
            }
        }
        refresh(instance)
    }

    fun delete(instance: Instance, entry: ModEntry) {
        val kind = type
        if (entry.enabled) setEnabled(instance, entry, false)
        entry.file.deleteRecursively()
        val name = entry.file.name.removeSuffix(DISABLED)
        save(instance, kind, load(instance, kind).filterNot { it.fileName == name })
        refresh(instance)
    }

    fun dir(instance: Instance, kind: ContentType = type): File = model.instances.gameDir(instance).resolve(kind.folder)

    /** File name → title, for the mods the launcher installed into [instance]. */
    fun titles(instance: Instance): Map<String, String> = load(instance, ContentType.Mod).associate { it.fileName to it.title }

    /** Remembers mods put into [instance] other than from the catalogue — for a server — so the list shows their names. */
    internal fun record(instance: Instance, mods: List<InstalledMod>) {
        val names = mods.map { it.fileName }.toSet()
        save(instance, ContentType.Mod, load(instance, ContentType.Mod).filter { it.fileName !in names } + mods)
    }

    /** Mods keep their old `mods.json`; the other kinds get a file named after their folder. */
    private fun metaFile(instance: Instance, kind: ContentType) =
        model.instances.folder(instance).resolve(if (kind == ContentType.Mod) "mods.json" else "${kind.folder}.json")

    private fun load(instance: Instance, kind: ContentType): List<InstalledMod> = runCatching {
        LauncherJson.decodeFromString(ListSerializer(InstalledMod.serializer()), metaFile(instance, kind).readText())
    }.getOrDefault(emptyList())

    private fun save(instance: Instance, kind: ContentType, mods: List<InstalledMod>) {
        metaFile(instance, kind).writeText(LauncherJson.encodeToString(ListSerializer(InstalledMod.serializer()), mods))
    }

    @Synchronized
    private fun remember(instance: Instance, kind: ContentType, mod: InstalledMod) {
        save(instance, kind, load(instance, kind).filterNot { it.fileName == mod.fileName || (it.source == mod.source && it.projectId == mod.projectId) } + mod)
    }

    private companion object {
        val SHADER_LOADERS = Regex("(?i)(^|[-_ ])(iris|oculus|optifine)")

        /** Mods are jars; shaders and resource packs are zips or unpacked folders. */
        fun accepts(kind: ContentType, file: File): Boolean = when (kind) {
            ContentType.Mod -> file.isFile && (file.name.endsWith(".jar") || file.name.endsWith(".jar$DISABLED"))
            else -> (file.isFile && file.name.endsWith(".zip", ignoreCase = true)) || (file.isDirectory && !file.name.startsWith("."))
        }

        /**
         * Where Iris keeps its choice of shader — `config/iris.properties`, and Oculus the same keys
         * in `oculus.properties`. Null when shaders are off or none is chosen.
         */
        fun activeShader(game: File): String? {
            val file = listOf("iris.properties", "oculus.properties").map { game.resolve("config").resolve(it) }.firstOrNull { it.isFile } ?: return null
            val props = Properties().apply { runCatching { file.inputStream().use(::load) } }
            if (props.getProperty("enableShaders", "true") != "true") return null
            return props.getProperty("shaderPack")?.takeIf { it.isNotBlank() }
        }

        /** Points Iris (or Oculus) at [name], or turns shaders off with null; its other settings stay as they were. */
        fun setShader(game: File, name: String?, loader: Loader) {
            val config = game.resolve("config").apply { mkdirs() }
            val files = listOf("iris.properties", "oculus.properties").map { config.resolve(it) }.filter { it.isFile }
                .ifEmpty { listOf(config.resolve(if (loader == Loader.Forge) "oculus.properties" else "iris.properties")) }
            for (file in files) {
                val props = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
                if (name != null) props.setProperty("shaderPack", name)
                props.setProperty("enableShaders", (name != null).toString())
                file.outputStream().use { props.store(it, "This file stores configuration options for Iris") }
            }
        }
    }
}

/** "мод", "шейдер", "набор ресурсов" — one of the kind, for the downloads list. */
val ContentType.single: String
    get() = when (this) {
        ContentType.Mod -> "мод"
        ContentType.Shader -> "шейдер"
        ContentType.ResourcePack -> "набор ресурсов"
        ContentType.Modpack -> "сборка"
    }

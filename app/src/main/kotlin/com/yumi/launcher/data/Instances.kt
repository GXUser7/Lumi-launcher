package com.yumi.launcher.data

import androidx.compose.runtime.mutableStateListOf
import com.yumi.launcher.core.LauncherJson
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.LauncherPaths
import kotlinx.serialization.Serializable
import java.io.File

/**
 * A pack: a Minecraft version, its own game folder, and how to start it. Anything left null here
 * falls back to the launcher's settings.
 */
@Serializable
data class Instance(
    val id: String,
    val name: String,
    val version: String,
    val loader: Loader = Loader.Vanilla,
    /** Null until the first launch picks the newest stable release; pinned from then on. */
    val loaderVersion: String? = null,
    val icon: Int = 0,
    val memoryMb: Int? = null,
    val jvmArgs: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val fullscreen: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val playSeconds: Long = 0,
)

/** Packs on disk: `instances/<id>/instance.json` beside the pack's `minecraft` game folder. */
class InstanceStore(root: File = LauncherPaths.instances) {
    val all = mutableStateListOf<Instance>()

    var root: File = root
        private set

    init {
        reload(root)
    }

    /** Reads the packs under [folder], which becomes where packs live from now on. */
    fun reload(folder: File) {
        root = folder
        root.mkdirs()
        all.clear()
        all += root.listFiles().orEmpty()
            .mapNotNull { dir -> dir.resolve(FILE).takeIf { it.isFile } }
            .mapNotNull { runCatching { LauncherJson.decodeFromString<Instance>(it.readText()) }.getOrNull() }
            .sortedByDescending { it.lastPlayed ?: it.created }
    }

    operator fun get(id: String?): Instance? = all.firstOrNull { it.id == id }

    fun folder(instance: Instance): File = root.resolve(instance.id)
    fun gameDir(instance: Instance): File = folder(instance).resolve("minecraft")
    fun modsDir(instance: Instance): File = gameDir(instance).resolve("mods")

    fun save(instance: Instance) {
        folder(instance).mkdirs()
        gameDir(instance).mkdirs()
        folder(instance).resolve(FILE).writeText(LauncherJson.encodeToString(Instance.serializer(), instance))
        val at = all.indexOfFirst { it.id == instance.id }
        if (at >= 0) all[at] = instance else all.add(0, instance)
    }

    fun create(name: String, version: String, icon: Int): Instance {
        val instance = Instance(id = uniqueId(name), name = name.trim(), version = version, icon = icon)
        save(instance)
        return instance
    }

    fun delete(instance: Instance) {
        folder(instance).deleteRecursively()
        all.removeAll { it.id == instance.id }
    }

    private fun uniqueId(name: String): String = folderId(name, "pack") { root.resolve(it).exists() || this[it] != null }

    private companion object {
        const val FILE = "instance.json"
    }
}

/**
 * A folder name for something the player named. Folder names stay ASCII: old versions run LWJGL 2
 * on Java 8, which cannot load natives from a path with Cyrillic in it, and a pack called
 * «Выживание» should not be the thing that breaks.
 */
internal fun folderId(name: String, fallback: String, taken: (String) -> Boolean): String {
    val base = transliterate(name.lowercase())
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(32)
        .ifEmpty { fallback }
    var id = base
    var n = 2
    while (taken(id)) id = "$base-${n++}"
    return id
}

private val CYRILLIC = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z",
    'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
    'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
    'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)

private fun transliterate(text: String) = buildString { text.forEach { append(CYRILLIC[it] ?: it) } }

package com.yumi.launcher

import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.data.GameOptions
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.InstanceStore
import java.io.File

/**
 * `--options-test <folder> [newer options.txt] [older options.txt]`: the shared game settings
 * without a game, on made-up packs in a throwaway folder — seeding from the pack played last,
 * a start, the player's changes going back, the launcher closed while the game ran, a pack that
 * keeps its own settings, a pack with no settings yet, and a pre-1.13 pack. Real files from two
 * versions can stand in for the made-up ones; the merged older one is left in the folder, to try
 * in the game. Exits 0 when every check passes.
 */
internal fun optionsTest(args: List<String>): Int {
    val root = File(args.firstOrNull() ?: return 2.also { println("Использование: --options-test <папка> [новый options.txt] [старый options.txt]") })
        .absoluteFile.apply { deleteRecursively(); mkdirs() }
    LauncherPaths.root = root
    LauncherPaths.config = root.resolve("config")
    val newer = args.getOrNull(1)?.let { File(it).readLines() }
        ?: listOf("version:4786", "fov:0.25", "key_key.jump:key.keyboard.space", "lang:ru_ru", "resourcePacks:[\"vanilla\",\"file/A.zip\"]", "fullscreen:true")
    val older = args.getOrNull(2)?.let { File(it).readLines() }
        ?: listOf("version:3955", "fov:0.0", "key_key.jump:key.keyboard.j", "lang:en_us", "resourcePacks:[\"vanilla\",\"file/B.zip\"]", "fullscreen:false", "oldOnly:1")

    val store = InstanceStore(root.resolve("instances"))
    fun pack(id: String, version: String, played: Long?, options: List<String>?, share: Boolean = true): Instance {
        val instance = Instance(id = id, name = id, version = version, lastPlayed = played, created = 0, syncOptions = share)
        store.save(instance)
        options?.let { store.gameDir(instance).resolve("options.txt").writeText(it.joinToString("\n", postfix = "\n")) }
        return instance
    }
    fun file(pack: Instance) = store.gameDir(pack).resolve("options.txt")
    fun options(pack: Instance): Map<String, String> =
        if (!file(pack).isFile) emptyMap() else file(pack).readLines().filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') }
    fun edit(pack: Instance, change: (MutableMap<String, String>) -> Unit) {
        val map = LinkedHashMap(options(pack)).also(change)
        file(pack).writeText(map.entries.joinToString("\n", postfix = "\n") { "${it.key}:${it.value}" })
    }
    val eraOf = { pack: Instance -> if (pack.version.startsWith("1.12")) GameOptions.Era.Legacy else GameOptions.Era.Modern }
    val own = setOf("version", "resourcePacks", "incompatibleResourcePacks", "fullscreen", "fullscreenResolution", "overrideWidth", "overrideHeight")

    var failed = 0
    fun check(what: String, ok: Boolean) {
        println((if (ok) "OK    " else "FAIL  ") + what)
        if (!ok) failed++
    }

    val a = pack("a", "26.1", 3000, newer)
    val b = pack("b", "1.21.1", 2000, older)
    val legacy = pack("old", "1.12.2", 1000, listOf("version:1343", "key_key.jump:57", "lang:en_us"))
    val fresh = pack("fresh", "1.20.1", null, null)
    val loner = pack("loner", "26.1", 5000, listOf("version:4786", "fov:1.0", "key_key.jump:key.keyboard.x"), share = false)
    val aStart = options(a)
    val bStart = options(b)

    // 1. The first start seeds from the sharing pack played last — not from the loner, played later.
    GameOptions.beforeStart(store, b, share = true, eraOf)
    val b1 = options(b)
    check("b получила общие значения из a", aStart.filterKeys { it !in own }.all { (k, v) -> b1[k] == v })
    check("у b остались свои наборы ресурсов, окно и версия данных", bStart.filterKeys { it in own }.all { (k, v) -> b1[k] == v })
    check("ключи, которых нет у a, у b остались", bStart.keys.all { it in b1 })
    check("значения loner не попали в общие", b1["key_key.jump"] != "key.keyboard.x")

    // 2. The player changes a key in b's game, and b's version drops a setting it does not know.
    edit(b) { it["key_key.jump"] = "key.keyboard.k"; it.remove("lang"); it["newKey"] = "x" }
    GameOptions.settle(store)
    GameOptions.beforeStart(store, a, share = true, eraOf)
    val a1 = options(a)
    check("изменённая в b клавиша дошла до a", a1["key_key.jump"] == "key.keyboard.k")
    check("новая настройка из b дошла до a", a1["newKey"] == "x")
    check("настройка, выпавшая у b, у a не пропала", aStart["lang"] == null || a1["lang"] == aStart["lang"])
    check("у a остались свои наборы ресурсов и окно", aStart.filterKeys { it in own }.all { (k, v) -> a1[k] == v })

    // 3. The launcher closes while a's game runs; the next start of another pack still takes a's changes.
    edit(a) { it["fov"] = "0.75" }
    GameOptions.beforeStart(store, b, share = true, eraOf)
    check("изменения из a, пока лаунчер был закрыт, дошли до b", options(b)["fov"] == "0.75")

    // 4. A pack with no settings yet starts from the shared ones, with a data version.
    GameOptions.beforeStart(store, fresh, share = true, eraOf)
    val f = options(fresh)
    check("новая сборка получила общие настройки", f["key_key.jump"] == "key.keyboard.k" && f["fov"] == "0.75")
    check("у новой сборки есть версия данных", f["version"] != null)

    // 5. The pre-1.13 pack shares only with its own era.
    GameOptions.beforeStart(store, legacy, share = true, eraOf)
    check("старая сборка не получила новые клавиши", options(legacy)["key_key.jump"] == "57")

    // 6. A pack that keeps its own settings is left alone.
    val lonerStart = file(loner).readText()
    GameOptions.beforeStart(store, loner, share = false, eraOf)
    check("сборку со своими настройками не тронули", file(loner).readText() == lonerStart)

    GameOptions.settle(store)
    check("после всех запусков нет незавершённых снимков", root.resolve("config/game-options/pending").listFiles().orEmpty().isEmpty())
    println("Слитый файл старой версии: ${file(b)}")
    println(if (failed == 0) "Все проверки прошли" else "Не прошло: $failed")
    return if (failed == 0) 0 else 1
}

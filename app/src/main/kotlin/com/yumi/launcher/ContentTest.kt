package com.yumi.launcher

import com.yumi.launcher.core.Categories
import com.yumi.launcher.core.ContentType
import com.yumi.launcher.core.GameLauncher
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.SearchFilter
import com.yumi.launcher.core.SortOrder
import com.yumi.launcher.core.Support
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ServerAuth
import com.yumi.launcher.data.ServerProfile
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.prefs.Preferences
import java.util.zip.ZipFile

/**
 * `--content-test <folder>`: the mods screen's shaders and resource packs without the window, through
 * the same model the buttons use, in a Fabric 1.21.1 pack of a throwaway game folder. Checks the
 * categories and sorts of both catalogues (CurseForge only with a key in the launcher's settings),
 * installs Iris for shaders, a shader — which Iris must then be set to — and a resource pack —
 * which the game's `options.txt` must then list — switches them off and on, deletes them, and packs
 * a log bundle to check that secrets come out. Exits 0 when all of it holds.
 */
internal fun contentTest(args: List<String>): Int = runBlocking {
    val folder = File(args.firstOrNull() ?: return@runBlocking 2.also { println("Использование: --content-test <папка>") }).absoluteFile
    // Its own settings and launcher folder: the real ones are the player's.
    LauncherPaths.config = folder.resolve("config")
    val node = Preferences.userRoot().node("com/yumi/launcher-content-test")
    val settings = LauncherSettings(node).apply {
        gameDir = folder.path
        curseForgeKey = LauncherSettings().curseForgeKey
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var failures = 0
    fun check(ok: Boolean, what: String) {
        println((if (ok) "  ок  " else "  ОШИБКА  ") + what)
        if (!ok) failures++
    }
    try {
        withContext(Dispatchers.Main) {
            val model = LauncherModel(settings, scope)
            val mods = model.mods
            suspend fun settle(what: String, busy: () -> Boolean) = withTimeout(180_000) {
                delay(300)
                while (busy()) delay(200)
            }.also { println("$what — готово") }

            println("Категории")
            for (source in ModSource.entries) {
                if (source == ModSource.CurseForge && settings.curseForgeKey.isBlank()) {
                    println("  CurseForge пропущен: нет ключа в настройках")
                    continue
                }
                for (type in ContentType.entries) {
                    val list = runCatching { Categories.of(source, type, settings.curseForgeKey) }.getOrElse { emptyList() }
                    val groups = list.groupBy { it.group }.map { (g, c) -> "$g: ${c.size}" }
                    check(list.isNotEmpty(), "$source ${type.label}: ${list.size} (${groups.joinToString()}) — ${list.take(4).joinToString { it.label }}")
                }
            }

            val pack = model.instances.create("Content test", "1.21.1", 0).let { model.instances.save(it.copy(loader = Loader.Fabric)); model.instances[it.id]!! }
            val game = model.instances.gameDir(pack)

            println("Шейдеры")
            mods.show(pack, ContentType.Shader)
            check(!mods.hasShaderLoader(pack), "в новой сборке Iris нет")
            mods.installShaderLoader(pack)
            settle("Iris") { mods.installing.isNotEmpty() }
            check(mods.hasShaderLoader(pack), "Iris поставлен: ${model.instances.modsDir(pack).list().orEmpty().sorted()}")
            for (sort in SortOrder.entries) {
                mods.filter = SearchFilter(sort = sort)
                mods.search(pack)
                settle("поиск «${sort.label}»") { mods.searching }
                check(mods.results.isNotEmpty(), "${sort.label}: ${mods.results.take(3).joinToString { it.title }}")
            }
            val realistic = mods.categories.orEmpty().firstOrNull { it.id == "realistic" }
            mods.filter = SearchFilter(categories = listOfNotNull(realistic).associate { it.group to it.id })
            mods.search(pack)
            settle("поиск по категории") { mods.searching }
            check(mods.results.isNotEmpty(), "«Реализм»: ${mods.results.take(3).joinToString { it.title }}")
            mods.filter = SearchFilter()
            mods.query = "complementary reimagined"
            mods.search(pack)
            settle("поиск Complementary") { mods.searching }
            val shader = mods.results.firstOrNull()
            check(shader != null, "найден ${shader?.title}")
            if (shader != null) {
                mods.install(pack, shader)
                settle("установка шейдера") { mods.installing.isNotEmpty() }
                val file = mods.installed.firstOrNull()
                check(file != null && file.file.isFile, "в shaderpacks: ${file?.file?.name}")
                check(file?.enabled == true, "Iris выбран на него: ${game.resolve("config/iris.properties").takeIf { it.isFile }?.readText()?.lines()?.filter { "shader" in it.lowercase() }}")
                check(mods.isInstalled(shader), "в каталоге он отмечен установленным")
                if (file != null) {
                    mods.setEnabled(pack, file, false)
                    check(mods.installed.none { it.enabled }, "выключен")
                    mods.setEnabled(pack, mods.installed.first(), true)
                    check(mods.installed.first().enabled, "включён снова")
                }
            }

            println("Ресурспаки")
            mods.query = ""
            mods.show(pack, ContentType.ResourcePack)
            settle("поиск") { mods.searching }
            settle("категории") { mods.categories == null }
            check(mods.categories.orEmpty().any { it.group == "resolutions" }, "есть фильтр «Разрешение»")
            val x16 = mods.categories.orEmpty().firstOrNull { it.id == "16x" }
            mods.filter = SearchFilter(categories = listOfNotNull(x16).associate { it.group to it.id })
            mods.search(pack)
            settle("поиск 16x") { mods.searching }
            check(mods.results.isNotEmpty(), "16x: ${mods.results.take(3).joinToString { it.title }}")
            val texture = mods.results.firstOrNull()
            if (texture != null) {
                mods.install(pack, texture)
                settle("установка ${texture.title}") { mods.installing.isNotEmpty() }
                val file = mods.installed.firstOrNull()
                val options = game.resolve("options.txt").takeIf { it.isFile }?.readText().orEmpty()
                check(file != null && "file/${file.file.name}" in options, "в options.txt: ${options.lines().firstOrNull { it.startsWith("resourcePacks") }}")
                mods.notice?.let { println("  заметка: $it") }
                if (file != null) {
                    mods.setEnabled(pack, file, false)
                    check("file/${file.file.name}" !in game.resolve("options.txt").readText(), "выключен в списке игры")
                    mods.delete(pack, mods.installed.first())
                    check(mods.installed.isEmpty() && !file.file.exists(), "удалён")
                }
            }

            if (settings.curseForgeKey.isNotBlank()) {
                println("CurseForge")
                mods.source = ModSource.CurseForge
                mods.show(pack, ContentType.Shader)
                mods.loadCategories()
                mods.search(pack)
                settle("поиск шейдеров") { mods.searching }
                check(mods.results.isNotEmpty(), "шейдеры: ${mods.results.take(3).joinToString { it.title }}")
                mods.show(pack, ContentType.ResourcePack)
                settle("поиск ресурспаков") { mods.searching }
                delay(1500)
                settle("категории") { mods.categories == null }
                val category = mods.categories.orEmpty().firstOrNull()
                mods.filter = SearchFilter(sort = SortOrder.Updated, categories = listOfNotNull(category).associate { it.group to it.id })
                mods.search(pack)
                settle("поиск «${category?.label}»") { mods.searching }
                check(mods.results.isNotEmpty(), "«${category?.label}», обновлённые: ${mods.results.take(3).joinToString { it.title }}")
            }

            println("Вайтлист")
            for (auth in ServerAuth.entries) {
                val server = ServerProfile(id = "wl-${auth.name.lowercase()}", name = "Whitelist test", version = "1.21.1", auth = auth)
                model.servers.folder(server).mkdirs()
                check(!model.servers.whitelistOn(server), "${auth.label}: выключен в новом сервере")
                model.servers.setWhitelist(server, true)
                check(model.servers.whitelistOn(server), "${auth.label}: включается — ${model.servers.folder(server).resolve("server.properties").readLines().filter { "white" in it }}")
                val name = when (auth) {
                    ServerAuth.Offline -> "Steve_Test"
                    ServerAuth.Mojang -> "Notch"
                    ServerAuth.ElyBy -> "ErickSkrauch"
                }
                model.servers.whitelistAdd(server, name)
                val entry = model.servers.whitelist(server).firstOrNull()
                check(entry?.name == name && entry.uuid.length == 36, "${auth.label}: добавлен $name, uuid ${entry?.uuid}")
                if (auth == ServerAuth.Offline) check(entry?.uuid?.replace("-", "") == GameLauncher.offlineUuid(name), "офлайн-uuid как у игры")
                if (auth == ServerAuth.Mojang) check(entry?.uuid == "069a79f4-44e9-4726-a5be-fca90e38aaf5", "uuid Notch от Mojang")
                val missing = runCatching { model.servers.whitelistAdd(server, "zz_no_such_9q") }.exceptionOrNull()
                if (auth != ServerAuth.Offline) check(missing != null, "${auth.label}: несуществующий ник — «${missing?.message}»")
                model.servers.whitelistRemove(server, name)
                check(model.servers.whitelist(server).none { it.name == name }, "${auth.label}: удалён")
                model.servers.setWhitelist(server, false)
                check(!model.servers.whitelistOn(server), "${auth.label}: выключается")
            }

            println("Лог")
            val secrets = listOf(
                "--accessToken eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnop",
                "\"accessToken\":\"0123456789abcdef0123456789abcdef\"",
                "x-api-key: \$2a\$10\$" + "a".repeat(53),
                "C:\\Users\\Даня\\AppData\\Roaming\\.minecraft",
            )
            val redacted = Support.redact(secrets.joinToString("\n"))
            println(redacted.prependIndent("    "))
            check("eyJ" !in redacted && "0123456789abcdef" !in redacted && "aaaaaaaa" !in redacted && "Даня" !in redacted, "секреты вырезаны")
            game.resolve("logs").mkdirs()
            game.resolve("logs/latest.log").writeText(secrets.joinToString("\n"))
            model.select(pack)
            model.feedback.sendLog(share = false)
            settle("архив") { model.feedback.packing }
            val zip = model.feedback.bundle
            val entries = zip?.let { ZipFile(it).use { z -> z.entries().toList().associate { e -> e.name to z.getInputStream(e).readBytes().decodeToString() } } }.orEmpty()
            check(zip != null && "info.txt" in entries && "latest.log" in entries, "архив ${zip?.name}: ${entries.keys}")
            check(entries["latest.log"]?.let { "eyJ" !in it && "Даня" !in it } == true, "в latest.log архива секретов нет")
            check(entries["info.txt"]?.contains("iris-fabric") == true, "в info.txt есть список модов")
            model.instances.delete(pack)
        }
    } finally {
        scope.cancel()
        node.removeNode()
    }
    println(if (failures == 0) "Всё в порядке" else "Ошибок: $failures")
    if (failures == 0) 0 else 1
}

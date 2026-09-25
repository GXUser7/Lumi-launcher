package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.LauncherLog
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.Support
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * Feedback to the developer: the chat in Telegram, and the logs to send there — the last game's
 * pack logs, its crash report, the launcher's own log and a page about this computer, packed by
 * [Support.bundle] with the secrets taken out.
 */
class Feedback(private val model: LauncherModel, private val scope: CoroutineScope) {
    /** The feedback card is over the window. */
    var open by mutableStateOf(false)

    var packing by mutableStateOf(false)
        private set

    /** The zip made last, on the clipboard as a file; null until one is made. */
    var bundle by mutableStateOf<File?>(null)
        private set
    var copied by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun openChat() = Support.openChat()

    /**
     * Packs the logs, puts the zip on the clipboard and opens the chat to paste it into; without
     * [share] — for the headless test — it only packs them.
     */
    fun sendLog(share: Boolean = true) {
        if (packing) return
        packing = true
        error = null
        val lines = model.log.toList()
        scope.launch {
            try {
                val zip = withContext(Dispatchers.IO) { pack(lines) }
                bundle = zip
                if (share) {
                    copied = Support.copyFile(zip)
                    Support.openChat()
                }
            } catch (e: Exception) {
                error = "Не удалось собрать лог: ${e.message}"
            } finally {
                packing = false
            }
        }
    }

    fun reveal() {
        bundle?.let(Support::reveal)
    }

    fun dismiss() {
        open = false
        error = null
    }

    private fun pack(console: List<String>): File {
        // The pack the game ran last, or the chosen one if it has not run since the launcher started.
        val folder = model.logFile?.parentFile?.parentFile ?: model.selected?.let(model.instances::folder)
        val pack = folder?.let { f -> model.instances.all.firstOrNull { model.instances.folder(it) == f } }
        val game = folder?.resolve("minecraft")
        val texts = buildList {
            add("info.txt" to info(pack, game))
            if (console.isNotEmpty()) add("console.txt" to console.joinToString("\n"))
        }
        val files = listOf(
            "launch.log" to folder?.resolve("logs/launch.log"),
            "latest.log" to game?.resolve("logs/latest.log"),
            "loader-install.log" to folder?.resolve("logs/loader-install.log"),
            "crash-report.txt" to game?.resolve("crash-reports")?.let(::newest),
            "jvm-crash.log" to game?.listFiles { f -> f.name.startsWith("hs_err_pid") }?.maxByOrNull { it.lastModified() },
            "launcher.log" to LauncherLog.file,
            "launcher-previous.log" to LauncherLog.previous,
        )
        return Support.bundle(texts, files)
    }

    private fun info(pack: Instance?, game: File?): String = buildString {
        val settings: LauncherSettings = model.settings
        val runtime = Runtime.getRuntime()
        appendLine("Yumi Launcher ${Support.VERSION}")
        appendLine("Собрано: ${LocalDateTime.now()}")
        appendLine("Система: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        appendLine("Java лаунчера: ${System.getProperty("java.version")} ${System.getProperty("java.vendor")}")
        appendLine("Память компьютера: ${LauncherSettings.systemMemoryMb} МБ, лаунчер занял ${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)} МБ")
        appendLine("Процессоров: ${runtime.availableProcessors()}")
        appendLine("Аккаунт: ${settings.account.label}")
        appendLine("Память игре: ${pack?.memoryMb ?: settings.memoryMb} МБ, Java: ${if (settings.customJava) settings.javaPath else "автоматически"}")
        appendLine("Папка игры: ${LauncherPaths.root}")
        appendLine("Состояние: ${model.status}")
        appendLine()
        if (pack == null) {
            appendLine("Сборка: не выбрана")
            return@buildString
        }
        appendLine("Сборка: ${pack.name} (${pack.id})")
        appendLine("Minecraft ${pack.version}, ${pack.loader.label} ${pack.loaderVersion.orEmpty()}")
        if (pack.jvmArgs.isNotBlank()) appendLine("Аргументы Java: ${pack.jvmArgs}")
        for ((title, dir) in listOf("Моды" to "mods", "Шейдеры" to "shaderpacks", "Наборы ресурсов" to "resourcepacks")) {
            val entries = game?.resolve(dir)?.listFiles().orEmpty().map { it.name }.sorted()
            if (entries.isEmpty()) continue
            appendLine()
            appendLine("$title (${entries.size}):")
            entries.forEach { appendLine("  $it") }
        }
    }

    private fun newest(dir: File): File? = dir.listFiles().orEmpty().filter { it.isFile }.maxByOrNull { it.lastModified() }
}

package com.yumi.launcher.core

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Getting help: the developer's Telegram, and the logs to send there.
 *
 * Nothing is sent by the launcher itself. It packs the logs into one zip with the secrets taken
 * out, puts the file on the clipboard and opens the chat; the player pastes it and presses send —
 * they see exactly what leaves their computer, and no bot token has to live in the launcher.
 */
object Support {
    const val VERSION = "0.1.0"
    const val TELEGRAM = "BBnov22"
    const val CHAT_URL = "https://t.me/$TELEGRAM"

    /** Opens the chat in Telegram Desktop when it is installed, else on t.me in the browser. */
    fun openChat() {
        if (telegramInstalled() && open("tg://resolve?domain=$TELEGRAM")) return
        open(CHAT_URL)
    }

    /**
     * Opens [url] with whatever the system has for its scheme; false when that failed. Not with
     * `Desktop.browse` on Windows: since Java 21 it hands every link to the default browser — a
     * `tg:` link too, which the browser then opens as an empty page. Windows' own handler sends
     * `tg:` to Telegram and `https:` to the browser.
     */
    fun open(url: String): Boolean {
        if (Platform.os == "windows") {
            return runCatching { ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", url).start() }.isSuccess
        }
        return runCatching { Desktop.getDesktop().browse(URI(url)) }.isSuccess
    }

    /**
     * Whether something handles `tg:` links. Asked of the registry: handing Windows a scheme nothing
     * owns does not fail, it opens the "how do you want to open this" dialog instead.
     */
    private fun telegramInstalled(): Boolean {
        if (Platform.os != "windows") return false
        return runCatching {
            val reg = ProcessBuilder("reg", "query", "HKCR\\tg", "/ve").redirectErrorStream(true).start()
            reg.inputStream.readAllBytes()
            reg.waitFor(5, TimeUnit.SECONDS) && reg.exitValue() == 0
        }.getOrDefault(false)
    }

    /**
     * Packs [texts] (name → contents) and [files] (name → file on disk, skipped when missing) into
     * a zip in the launcher's folder, every text with [redact] applied. Returns the zip.
     */
    fun bundle(texts: List<Pair<String, String>>, files: List<Pair<String, File?>>): File {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val folder = LauncherPaths.config.resolve("support").apply { mkdirs() }
        // Only the last few bundles are kept: they are made to be sent, not collected.
        folder.listFiles { f -> f.name.startsWith("yumi-log-") }.orEmpty().sortedByDescending { it.lastModified() }.drop(4).forEach { it.delete() }
        val zip = folder.resolve("yumi-log-$stamp.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            fun put(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(redact(text).toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
            texts.forEach { (name, text) -> put(name, text) }
            for ((name, file) in files) {
                if (file == null || !file.isFile) continue
                // The end of a huge log is the part that matters: where it stopped.
                val text = if (file.length() > MAX_FILE) {
                    "[… начало файла пропущено, всего ${file.length() / 1024} КБ …]\n" + tail(file, MAX_FILE)
                } else {
                    file.readText(Charsets.UTF_8)
                }
                put(name, text)
            }
        }
        return zip
    }

    /**
     * Takes secrets out of a log: access tokens in any of the forms they are printed in, JWTs,
     * `Authorization` headers, the CurseForge key, and the Windows user name in paths.
     */
    fun redact(text: String): String {
        var out = text
        for (pattern in SECRETS) out = pattern.replace(out) { m -> m.groupValues[1] + "<скрыто>" }
        out = JWT.replace(out, "<токен скрыт>")
        out = CURSE_KEY.replace(out, "<ключ скрыт>")
        out = USER_PATH.replace(out) { m -> m.groupValues[1] + "<пользователь>" }
        return out
    }

    /** Puts [file] on the clipboard as a file — Telegram, Explorer and most chats paste it with Ctrl+V. */
    fun copyFile(file: File): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(FileSelection(listOf(file)), null)
    }.isSuccess

    /** Opens Explorer with [file] selected, or its folder elsewhere. */
    fun reveal(file: File) {
        runCatching {
            if (Platform.os == "windows") ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            else Desktop.getDesktop().open(file.parentFile)
        }
    }

    private fun tail(file: File, bytes: Long): String = java.io.RandomAccessFile(file, "r").use { raf ->
        raf.seek(file.length() - bytes)
        val buffer = ByteArray(bytes.toInt())
        raf.readFully(buffer)
        String(buffer, Charsets.UTF_8).substringAfter('\n')
    }

    private class FileSelection(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return files
        }
    }

    private const val MAX_FILE = 2L * 1024 * 1024

    /** `--accessToken xyz`, `"accessToken":"xyz"`, `accessToken=xyz`, `Bearer xyz` and the like; group 1 is kept. */
    private val SECRETS = listOf(
        Regex("""(--(?:accessToken|session|clientToken|xuid)\s+)\S+"""),
        Regex("""((?:access_?token|refresh_?token|client_?token|session_?id|api[-_]?key|x-api-key)"?\s*[:=]\s*"?)[^\s",}&]+""", RegexOption.IGNORE_CASE),
        Regex("""((?:Bearer|RpsTicket:?)\s+(?:t=|d=)?)\S+""", RegexOption.IGNORE_CASE),
        Regex("""(token:)[0-9a-f]{16,}""", RegexOption.IGNORE_CASE),
    )
    private val JWT = Regex("""eyJ[\w-]{8,}\.[\w-]{8,}\.[\w-]{8,}""")
    private val CURSE_KEY = Regex("""\$2a\$10\$[./A-Za-z0-9]{53}""")
    private val USER_PATH = Regex("""((?:[A-Za-z]:)?[\\/]+Users[\\/]+)[^\\/\s"':]+""", RegexOption.IGNORE_CASE)
}

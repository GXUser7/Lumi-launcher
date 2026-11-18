package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Numeric release version parsed from `1.3`, `1.3.0`, or `v1.3.0` tags. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int = 0) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?$""")

        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            return AppVersion(major, minor, patch)
        }
    }
}

data class AppRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val installerName: String,
    val installerUrl: String,
    val checksumUrl: String?,
    val inlineSha256: String?,
    val size: Long,
    val pageUrl: String,
)

data class VerifiedUpdate(val release: AppRelease, val file: File, val sha256: String) {
    /** Re-hashes the staged installer before executing it so a tampered cache is never launched. */
    fun verify(): Boolean = file.isFile && file.length() == release.size && sha256(file).equals(sha256, ignoreCase = true)
}

class UpdateIntegrityException(message: String) : IOException(message)

fun interface UpdateTransport {
    fun open(url: String, accept: String): InputStream
}

/**
 * HTTPS-only transport pinned to GitHub release hosts. Rejects plaintext URLs, credentials in URIs,
 * custom ports, and redirects outside GitHub's official domains.
 */
class HttpsUpdateTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) : UpdateTransport {
    override fun open(url: String, accept: String): InputStream {
        var current = validateUri(URI.create(url))
        repeat(MAX_REDIRECTS) {
            val conn = (current.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", Http.USER_AGENT)
            }
            val status = conn.responseCode
            if (status in REDIRECT_CODES) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Пустой редирект при загрузке обновления")
                current = validateUri(current.resolve(location))
                return@repeat
            }
            if (status !in 200..299) {
                conn.errorStream?.close()
                conn.disconnect()
                throw IOException("GitHub ответил HTTP $status")
            }
            val stream = conn.inputStream
            return object : InputStream() {
                override fun read(): Int = stream.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
                override fun available(): Int = stream.available()
                override fun close() {
                    try {
                        stream.close()
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        }
        throw IOException("Слишком много перенаправлений при загрузке обновления")
    }

    internal fun validateUri(uri: URI): URI {
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw UpdateIntegrityException("Разрешены только HTTPS-ссылки для обновлений: ${uri.scheme ?: "none"}")
        }
        if (uri.userInfo != null) {
            throw UpdateIntegrityException("Недопустимые данные авторизации в URL обновления")
        }
        if (uri.port != -1 && uri.port != 443) {
            throw UpdateIntegrityException("Недопустимый порт в URL обновления: ${uri.port}")
        }
        val host = uri.host?.lowercase()?.trimEnd('.')
            ?: throw UpdateIntegrityException("Отсутствует хост в URL обновления")
        if (host !in ALLOWED_HOSTS) {
            throw UpdateIntegrityException("Сторонний хост обновления отклонён: $host")
        }
        return uri
    }

    private companion object {
        const val MAX_REDIRECTS = 6
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
        )
    }
}

interface UpdateSource {
    val supported: Boolean
    suspend fun check(currentVersion: String): AppRelease?
    suspend fun download(release: AppRelease, onProgress: (Long, Long) -> Unit = { _, _ -> }): VerifiedUpdate
}

/**
 * Resolves and verifies Lumi Launcher Windows installer updates against GitHub Releases.
 * Every release must carry a SHA-256 digest (`.sha256` asset, GitHub digest metadata, or release
 * notes hash) or it is ignored.
 */
class GitHubReleaseSource(
    private val directory: File? = null,
    private val transport: UpdateTransport = HttpsUpdateTransport(),
    private val apiUrl: String = RELEASES_API,
) : UpdateSource {
    override val supported: Boolean get() = Platform.os == "windows"

    private fun updatesDir(): File = directory ?: LauncherPaths.cache.resolve("updates")

    override suspend fun check(currentVersion: String): AppRelease? = withContext(Dispatchers.IO) {
        val installed = AppVersion.parse(currentVersion) ?: return@withContext null
        val json = transport.open(apiUrl, "application/vnd.github+json").use { it.readBytes().toString(Charsets.UTF_8) }
        val releases = LauncherJson.decodeFromString<List<GhRelease>>(json)

        var best: Pair<AppVersion, AppRelease>? = null
        for (item in releases) {
            if (item.draft || item.prerelease) continue
            val parsed = AppVersion.parse(item.tagName) ?: continue
            if (parsed <= installed) continue

            val installer = item.assets.firstOrNull {
                it.name.endsWith("-windows-x64-setup.exe", ignoreCase = true) && it.size > 0
            } ?: item.assets.firstOrNull {
                it.name.endsWith("-setup.exe", ignoreCase = true) && it.size > 0
            } ?: continue

            val checksumAsset = item.assets.firstOrNull { it.name.equals("${installer.name}.sha256", ignoreCase = true) }
            val inlineDigest = parseDigestField(installer.digest) ?: parseSha256InBody(item.body)
            val checksumUrl = checksumAsset?.downloadUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
            if (checksumUrl == null && inlineDigest == null) continue

            val candidate = AppRelease(
                version = item.tagName.removePrefix("v").removePrefix("V"),
                tag = item.tagName,
                notes = item.body.trim(),
                installerName = installer.name,
                installerUrl = installer.downloadUrl,
                checksumUrl = checksumUrl,
                inlineSha256 = inlineDigest,
                size = installer.size,
                pageUrl = item.htmlUrl,
            )
            if (best == null || parsed > best.first) best = parsed to candidate
        }
        best?.second
    }

    override suspend fun download(release: AppRelease, onProgress: (Long, Long) -> Unit): VerifiedUpdate = withContext(Dispatchers.IO) {
        val dir = updatesDir().apply { mkdirs() }
        val fileName = safeFileName(release.installerName)
        val target = resolveChild(dir, fileName)
        val part = resolveChild(dir, "$fileName.part")

        val expectedSha256 = resolveExpectedSha256(release)

        // Reuse an already-downloaded installer only if both byte length and SHA-256 still match.
        if (target.isFile && target.length() == release.size && sha256(target).equals(expectedSha256, ignoreCase = true)) {
            onProgress(release.size, release.size)
            purgeStale(dir, target.name)
            return@withContext VerifiedUpdate(release, target, expectedSha256)
        }

        target.delete()
        part.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            transport.open(release.installerUrl, "application/octet-stream").use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, release.size)
                    }
                    output.fd.sync()
                }
            }
            if (downloaded != release.size) {
                throw UpdateIntegrityException("Размер установщика не совпал ($downloaded из ${release.size} байт)")
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw UpdateIntegrityException("Контрольная сумма SHA-256 установщика не совпала")
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            purgeStale(dir, target.name)
            VerifiedUpdate(release, target, expectedSha256)
        } catch (e: Throwable) {
            part.delete()
            if (e is UpdateIntegrityException) target.delete()
            throw e
        }
    }

    private fun resolveExpectedSha256(release: AppRelease): String {
        val url = release.checksumUrl
        if (url != null) {
            val text = transport.open(url, "text/plain,application/octet-stream").use { it.readBytes().toString(Charsets.UTF_8) }
            val parsed = HEX_64.find(text)?.groupValues?.get(1)?.lowercase()
                ?: throw UpdateIntegrityException("Файл контрольной суммы .sha256 не содержит валидный хеш")
            return parsed
        }
        return release.inlineSha256?.lowercase()
            ?: throw UpdateIntegrityException("У релиза отсутствует контрольная сумма SHA-256")
    }

    private fun purgeStale(dir: File, keepName: String) {
        dir.listFiles().orEmpty().forEach { entry ->
            if (entry.name == keepName) return@forEach
            val lower = entry.name.lowercase()
            if (lower.endsWith(".exe") || lower.endsWith(".part") || lower.endsWith(".sha256")) {
                entry.delete()
            }
        }
    }

    private fun safeFileName(name: String): String {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && ".." !in name && name.endsWith(".exe", ignoreCase = true)) {
            "Недопустимое имя файла обновления: $name"
        }
        return name
    }

    private fun resolveChild(dir: File, name: String): File {
        val canonicalDir = dir.canonicalFile
        val child = File(canonicalDir, name).canonicalFile
        require(child.parentFile == canonicalDir) { "Выход пути обновления за пределы кэша" }
        return child
    }

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val name: String,
        val size: Long = 0,
        val digest: String? = null,
        @SerialName("browser_download_url") val downloadUrl: String = "",
    )

    private companion object {
        const val RELEASES_API = "https://api.github.com/repos/GXUser7/Lumi-launcher/releases?per_page=15"
        val BODY_SHA256 = Regex("""(?im)SHA-256\s*:\s*`?([0-9a-fA-F]{64})`?""")
        val HEX_64 = Regex("""\b([0-9a-fA-F]{64})\b""")

        fun parseDigestField(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.removePrefix("sha256:").trim()
            return if (cleaned.length == 64 && cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) cleaned.lowercase() else null
        }

        fun parseSha256InBody(body: String): String? = BODY_SHA256.find(body)?.groupValues?.get(1)?.lowercase()
    }
}

/**
 * Returns the Inno Setup installation directory when running from an installed `LumiLauncher.exe`
 * alongside `unins000.exe`, or `null` when running from Gradle / unpacked portable copies.
 */
internal fun installedDirectory(): File? {
    if (Platform.os != "windows") return null
    val appPath = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return null
    val dir = File(appPath).parentFile ?: return null
    return dir.takeIf { File(it, "unins000.exe").isFile }
}

internal fun buildInstallerCommand(installer: File, installedDir: File, pid: Long, relaunch: Boolean): List<String> = listOf(
    installer.absolutePath,
    if (relaunch) "/SILENT" else "/VERYSILENT",
    "/SUPPRESSMSGBOXES",
    "/NORESTART",
    "/CLOSEAPPLICATIONS",
    "/LUMIUPDATE=1",
    if (relaunch) "/LUMIRESTART=1" else "/LUMIRESTART=0",
    "/LUMIPID=$pid",
    "/DIR=${installedDir.absolutePath.trimEnd('\\')}",
)

/** Re-verifies [update] and launches Inno Setup in quiet update mode targeting [installedDir]. */
internal fun launchWindowsInstaller(
    update: VerifiedUpdate,
    installedDir: File? = installedDirectory(),
    relaunch: Boolean,
): Boolean {
    if (installedDir == null || !update.verify()) return false
    val command = buildInstallerCommand(update.file, installedDir, ProcessHandle.current().pid(), relaunch)
    return runCatching {
        println("Запуск установщика обновления ${update.release.version} в ${installedDir.absolutePath} (relaunch=$relaunch)")
        ProcessBuilder(command).directory(update.file.parentFile).start()
        true
    }.getOrElse { error ->
        println("Не удалось запустить установщик обновления: ${error.message}")
        false
    }
}

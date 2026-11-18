package com.yumi.launcher

import com.yumi.launcher.core.AppVersion
import com.yumi.launcher.core.GitHubReleaseSource
import com.yumi.launcher.core.HttpsUpdateTransport
import com.yumi.launcher.core.UpdateIntegrityException
import com.yumi.launcher.core.UpdateTransport
import com.yumi.launcher.core.buildInstallerCommand
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * `--update-test`: offline verification of version parsing, GitHub release selection, HTTPS host
 * allowlist, streaming SHA-256 verification, tamper rejection, stale cache cleanup, and installer CLI flags.
 */
internal fun updateTest(): Int = runBlocking {
    val root = File(".local/update-test").absoluteFile
    root.deleteRecursively()
    root.mkdirs()

    try {
        // 1. Version parsing and ordering
        val v12 = requireNotNull(AppVersion.parse("1.2"))
        val v121 = requireNotNull(AppVersion.parse("v1.2.1"))
        val v13 = requireNotNull(AppVersion.parse("1.3"))
        val v130 = requireNotNull(AppVersion.parse("1.3.0"))
        val v14 = requireNotNull(AppVersion.parse("v1.4"))
        require(v12 < v121 && v121 < v13 && v13 == v130 && v130 < v14) { "Неверный порядок версий" }
        require(AppVersion.parse("2.0-rc1") == null && AppVersion.parse("bad") == null) { "Пререлизы не должны парситься" }

        // 2. HTTPS transport allowlist & SSRF hardening
        val transport = HttpsUpdateTransport()
        transport.validateUri(URI.create("https://api.github.com/repos/GXUser7/Lumi-launcher/releases"))
        transport.validateUri(URI.create("https://release-assets.githubusercontent.com/file.exe"))
        transport.validateUri(URI.create("https://objects.githubusercontent.com/file.exe"))
        for (bad in listOf(
            "http://github.com/GXUser7/Lumi-launcher",
            "https://evil.example.com/setup.exe",
            "https://user:pass@github.com/setup.exe",
            "https://github.com:8443/setup.exe",
        )) {
            val rejected = runCatching { transport.validateUri(URI.create(bad)) }.exceptionOrNull()
            require(rejected is UpdateIntegrityException) { "Опасный URL должен отклоняться: $bad" }
        }

        // 3. Release selection & download with .sha256 sidecar + stale cleanup + cache reuse
        val payload = ByteArray(256 * 1024 + 137) { (it * 31 + 7).toByte() }
        val payloadHash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val installerUrl = "https://github.com/GXUser7/Lumi-launcher/releases/download/1.3/Lumi-Launcher-1.3-windows-x64-setup.exe"
        val checksumUrl = "$installerUrl.sha256"

        val releasesJson = """
            [
              {
                "tag_name": "9.9",
                "draft": true,
                "prerelease": false,
                "html_url": "https://github.com/GXUser7/Lumi-launcher/releases/tag/9.9",
                "assets": [{"name": "Lumi-Launcher-9.9-windows-x64-setup.exe", "size": 100, "browser_download_url": "https://github.com/a.exe"}]
              },
              {
                "tag_name": "2.0.0-beta1",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/GXUser7/Lumi-launcher/releases/tag/2.0.0-beta1",
                "assets": [{"name": "Lumi-Launcher-2.0.0-beta1-windows-x64-setup.exe", "size": 100, "browser_download_url": "https://github.com/b.exe"}]
              },
              {
                "tag_name": "1.2.1",
                "draft": false,
                "prerelease": false,
                "body": "Bugfixes\nSHA-256: `$payloadHash`",
                "html_url": "https://github.com/GXUser7/Lumi-launcher/releases/tag/1.2.1",
                "assets": [{"name": "Lumi-Launcher-1.2.1-windows-x64-setup.exe", "size": ${payload.size}, "browser_download_url": "https://github.com/121.exe"}]
              },
              {
                "tag_name": "1.3",
                "draft": false,
                "prerelease": false,
                "body": "Богатая библиотека и автообновление",
                "html_url": "https://github.com/GXUser7/Lumi-launcher/releases/tag/1.3",
                "assets": [
                  {"name": "Lumi-Launcher-1.3-windows-x64-setup.exe", "size": ${payload.size}, "browser_download_url": "$installerUrl"},
                  {"name": "Lumi-Launcher-1.3-windows-x64-setup.exe.sha256", "size": 100, "browser_download_url": "$checksumUrl"}
                ]
              }
            ]
        """.trimIndent()

        var payloadFetches = 0
        val fakeTransport = UpdateTransport { url, _ ->
            when {
                url == installerUrl -> {
                    payloadFetches++
                    ByteArrayInputStream(payload)
                }
                url == checksumUrl -> ByteArrayInputStream("$payloadHash  Lumi-Launcher-1.3-windows-x64-setup.exe\n".toByteArray(Charsets.UTF_8))
                url.contains("/releases?") || url.endsWith("/releases") -> ByteArrayInputStream(releasesJson.toByteArray(Charsets.UTF_8))
                else -> error("Неожиданный URL: $url")
            }
        }

        val cacheDir = root.resolve("cache").apply { mkdirs() }
        val stale = cacheDir.resolve("Lumi-Launcher-1.1-windows-x64-setup.exe").apply { writeText("old") }
        val source = GitHubReleaseSource(directory = cacheDir, transport = fakeTransport)

        require(source.check("1.3") == null) { "Для текущей версии обновлений быть не должно" }
        val found = requireNotNull(source.check("1.2")) { "Должен найтись релиз 1.3" }
        require(found.version == "1.3") { "Ожидалась версия 1.3, получена ${found.version}" }

        // Body SHA-256 fallback check against 1.2.0
        val bodySource = GitHubReleaseSource(
            directory = cacheDir,
            transport = UpdateTransport { _, _ ->
                ByteArrayInputStream(
                    """[{"tag_name":"1.2.1","draft":false,"prerelease":false,"body":"Fix\nSHA-256: `$payloadHash`","html_url":"https://github.com/GXUser7/Lumi-launcher/releases/tag/1.2.1","assets":[{"name":"Lumi-Launcher-1.2.1-windows-x64-setup.exe","size":${payload.size},"browser_download_url":"https://github.com/121.exe"}]}]""".toByteArray(Charsets.UTF_8),
                )
            },
        )
        val bodyRelease = requireNotNull(bodySource.check("1.2")) { "Должен распарситься SHA-256 из body" }
        require(bodyRelease.inlineSha256 == payloadHash && bodyRelease.checksumUrl == null) { "Неверный inlineSha256 из body" }

        var lastProgress = 0L
        val verified = source.download(found) { done, _ -> lastProgress = done }
        require(lastProgress == payload.size.toLong()) { "Прогресс не дошёл до конца" }
        require(verified.verify() && payloadFetches == 1) { "Проверенный файл не прошёл verify()" }
        require(!stale.exists()) { "Устаревший установщик должен быть удалён" }

        // Cached reuse on second call
        source.download(found)
        require(payloadFetches == 1) { "Повторная загрузка не должна качать файл заново" }

        // 4. Tamper detection
        val tampered = payload.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x5A).toByte() }
        val tamperDir = root.resolve("tamper").apply { mkdirs() }
        val tamperSource = GitHubReleaseSource(
            directory = tamperDir,
            transport = UpdateTransport { url, _ ->
                when (url) {
                    checksumUrl -> ByteArrayInputStream("$payloadHash  Lumi-Launcher-1.3-windows-x64-setup.exe\n".toByteArray(Charsets.UTF_8))
                    installerUrl -> ByteArrayInputStream(tampered)
                    else -> ByteArrayInputStream(releasesJson.toByteArray(Charsets.UTF_8))
                }
            },
        )
        val tamperErr = runCatching { tamperSource.download(found) }.exceptionOrNull()
        require(tamperErr is UpdateIntegrityException) { "Подменённый установщик должен отклоняться UpdateIntegrityException" }
        require(tamperDir.listFiles().orEmpty().isEmpty()) { "После ошибки SHA-256 кэш должен оставаться чистым" }

        // 5. Installer command lines
        val dir = File("C:\\Users\\Test\\AppData\\Local\\Programs\\Lumi Launcher")
        val silentCmd = buildInstallerCommand(verified.file, dir, 4242L, relaunch = true)
        require("/SILENT" in silentCmd && "/LUMIUPDATE=1" in silentCmd && "/LUMIRESTART=1" in silentCmd && "/LUMIPID=4242" in silentCmd)
        val exitCmd = buildInstallerCommand(verified.file, dir, 4242L, relaunch = false)
        require("/VERYSILENT" in exitCmd && "/LUMIRESTART=0" in exitCmd)

        println("update-test: OK (version, allowlist, check, download+sha256, cache-reuse, tamper-reject, inno-cmd)")
        0
    } finally {
        root.deleteRecursively()
    }
}

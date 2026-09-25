package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/** One file to fetch. [sha1] and [size] come from Mojang's own listings whenever they give them. */
internal class Download(val url: String, val target: File, val sha1: String?, val size: Long?)

internal object Http {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private const val ATTEMPTS = 5

    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        retry("Не удалось загрузить $url") {
            val builder = request(url)
            headers.forEach { (name, value) -> builder.header(name, value) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            // A refusal is an answer, not a network hiccup: retrying a 403 only delays saying so.
            if (response.statusCode() in 400..499) throw HttpRefused(response.statusCode())
            // "No content" is an answer too — the profile APIs say "no such player" with it.
            if (response.statusCode() == 204) return@retry ""
            if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
            response.body()
        }
    }

    /**
     * A JSON POST. The status comes back with the body instead of as an exception: the APIs posted
     * to explain a refusal in the body, and that explanation is what the player should read.
     */
    suspend fun post(url: String, json: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> = withContext(Dispatchers.IO) {
        retry("Не удалось связаться с ${URI.create(url).host}") {
            val builder = request(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
            headers.forEach { (name, value) -> builder.header(name, value) }
            val request = builder.build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() >= 500) throw IOException("HTTP ${response.statusCode()}")
            response.statusCode() to response.body()
        }
    }

    /** A form POST, as OAuth wants it; the status comes back with the body, as in [post]. */
    suspend fun form(url: String, fields: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val body = fields.entries.joinToString("&") { (name, value) ->
            "${URLEncoder.encode(name, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        retry("Не удалось связаться с ${URI.create(url).host}") {
            val request = request(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() >= 500) throw IOException("HTTP ${response.statusCode()}")
            response.statusCode() to response.body()
        }
    }

    /** Raw bytes, for icons; no retries — a missing icon is not worth waiting for. */
    suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val response = client.send(request(url).build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
        response.body()
    }

    /**
     * Fetches many files at once, skipping any already on disk. [onProgress] gets the share of
     * bytes done, at most ten times a second — it lands in UI state.
     */
    suspend fun downloadAll(jobs: List<Download>, parallel: Int = 16, onProgress: (Float) -> Unit) = coroutineScope {
        val missing = jobs.filterNot { present(it) }
        if (missing.isEmpty()) { onProgress(1f); return@coroutineScope }
        val total = missing.sumOf { it.size ?: 0L }.coerceAtLeast(1L)
        val done = AtomicLong()
        val lastReport = AtomicLong()
        val gate = Semaphore(parallel)
        missing.map { job ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    download(job) { bytes ->
                        val now = System.currentTimeMillis()
                        val sum = done.addAndGet(bytes)
                        val previous = lastReport.get()
                        if (now - previous > 100 && lastReport.compareAndSet(previous, now)) onProgress((sum.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }.awaitAll()
        onProgress(1f)
    }

    /** A file counts as present when its size matches — hashing every asset on each launch costs seconds. */
    private fun present(job: Download): Boolean = when {
        !job.target.isFile -> false
        job.size != null -> job.target.length() == job.size
        job.sha1 != null -> sha1(job.target) == job.sha1
        else -> true
    }

    /**
     * Downloads through a `.part` file and resumes it with a Range request after a dropped
     * connection — some networks cut long transfers mid-way, and starting a 30 MB jar over from
     * zero each time never finishes there. The hash is checked before the file takes its name.
     */
    private fun download(job: Download, onBytes: (Long) -> Unit) {
        job.target.parentFile.mkdirs()
        val part = File(job.target.path + ".part")
        var failure: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                val have = if (part.isFile) part.length() else 0L
                val builder = request(job.url)
                if (have > 0) builder.header("Range", "bytes=$have-")
                val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
                val append = response.statusCode() == 206
                if (response.statusCode() != 200 && !append) {
                    response.body().close()
                    throw IOException("HTTP ${response.statusCode()}")
                }
                response.body().use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            onBytes(n.toLong())
                        }
                    }
                }
                if (job.sha1 != null && sha1(part) != job.sha1) {
                    part.delete()
                    throw IOException("контрольная сумма не совпала")
                }
                Files.move(part.toPath(), job.target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: Exception) {
                failure = e
                if (attempt < ATTEMPTS - 1) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw IOException("Не удалось скачать ${job.target.name}: ${failure?.message}", failure)
    }

    private fun request(url: String) = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(60))
        // Modrinth asks every client to identify itself.
        .header("User-Agent", "YumiLauncher/0.1.0 (yumi-launcher)")

    private inline fun <T> retry(message: String, block: () -> T): T {
        var failure: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: HttpRefused) {
                throw e
            } catch (e: Exception) {
                failure = e
                if (attempt < ATTEMPTS - 1) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw IOException("$message: ${failure?.message}", failure)
    }
}

internal class HttpRefused(val status: Int) : IOException("сервер ответил $status")

internal fun sha1(file: File): String = digest(file, "SHA-1")

internal fun sha256(file: File): String = digest(file, "SHA-256")

private fun digest(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

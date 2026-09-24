package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** A file on CurseForge: its project and the file itself. */
@Serializable
data class CurseFile(val project: Long, val file: Long)

/**
 * A pack told by where its mods come from rather than by the mods themselves — small enough to
 * ride along in a server's description. The host's launcher makes one from the pack a server was
 * made from ([PackShare.describe]); a friend's reads it from the ping and builds the same pack.
 */
@Serializable
data class SharedPack(
    val minecraft: String,
    val loader: Loader = Loader.Vanilla,
    val loaderVersion: String? = null,
    /** Modrinth version ids. */
    val modrinth: List<String> = emptyList(),
    val curseForge: List<CurseFile> = emptyList(),
    /** Jars neither catalogue knows, by file name: the host has to hand these over. */
    val missing: List<String> = emptyList(),
)

/**
 * A pack in a server's description. The game shows two lines of it in the server list and no more,
 * so the pack goes on the lines after — deflated, in URL-safe Base64, after `yumi1:` — where only a
 * launcher that looks for it sees it. Nothing else has to run beside the server: no mod on it, no
 * second port to open, and it reaches whoever can reach the server.
 */
internal object PackShare {
    private const val PREFIX = "yumi1:"
    private val TAG = Regex("""\n*(?:§[0-9a-fk-orA-FK-OR])*yumi1:([A-Za-z0-9_-]+)""")

    /**
     * The whole status answer — icon included — has to fit the game's 32 767-character string;
     * an icon takes up to about 20 000 of them. A pack too big for what is left is not advertised.
     */
    const val MAX_TAG = 9000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    fun tag(pack: SharedPack): String {
        val raw = json.encodeToString(SharedPack.serializer(), pack).toByteArray()
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION, true)).use { it.write(raw) }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** The description with the pack after its lines, instead of whatever pack was there. */
    fun withTag(motd: String, tag: String): String = strip(motd).trimEnd('\n') + "\n\n§8" + tag

    /** The description as people should read it. */
    fun strip(text: String): String = TAG.replace(text, "")

    /** The pack in any of the texts of a description, as the ping's JSON holds it. */
    fun find(description: JsonElement): SharedPack? {
        texts(description).forEach { text -> decode(text)?.let { return it } }
        return null
    }

    /** [description] with the pack taken out of every text in it. */
    fun scrub(description: JsonElement): JsonElement = when (description) {
        is JsonPrimitive -> if (description.isString) JsonPrimitive(strip(description.content)) else description
        is JsonArray -> JsonArray(description.map(::scrub))
        is JsonObject -> JsonObject(description.mapValues { (key, value) -> if (key == "text" || key == "extra") scrub(value) else value })
    }

    private fun texts(e: JsonElement): Sequence<String> = when (e) {
        is JsonPrimitive -> sequenceOf(e.contentOrNull.orEmpty())
        is JsonArray -> e.asSequence().flatMap(::texts)
        is JsonObject -> sequenceOfNotNull(e["text"]).flatMap(::texts) + (e["extra"]?.let(::texts) ?: emptySequence())
    }

    private fun decode(text: String): SharedPack? {
        val data = TAG.find(text)?.groupValues?.get(1) ?: return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(data)
            // A server wrote this: it gets as much room as a real pack needs and no more.
            val raw = InflaterInputStream(bytes.inputStream(), Inflater(true)).use { it.readNBytes(1 shl 20) }
            json.decodeFromString(SharedPack.serializer(), raw.decodeToString())
        }.getOrNull()
    }

    /**
     * The pack in [jars], told by where each came from: Modrinth by the file's SHA-1, then
     * CurseForge by its fingerprint when there is a key. Anything else is named in [SharedPack.missing].
     */
    suspend fun describe(jars: List<File>, minecraft: String, loader: Loader, loaderVersion: String?, curseForgeKey: String): SharedPack =
        withContext(Dispatchers.IO) {
            val hashes = jars.associateWith { sha1(it) }
            val modrinth = Modrinth.versionsByHash(hashes.values.toSet())
            val rest = jars.filter { hashes[it] !in modrinth }
            val curse = if (curseForgeKey.isNotBlank() && rest.isNotEmpty()) CurseForge(curseForgeKey).filesByFingerprint(rest) else emptyMap()
            SharedPack(
                minecraft = minecraft,
                loader = loader,
                loaderVersion = loaderVersion,
                modrinth = jars.mapNotNull { modrinth[hashes[it]] }.distinct(),
                curseForge = rest.mapNotNull { curse[it] }.distinct(),
                missing = rest.filter { it !in curse }.map { it.name },
            )
        }
}

/**
 * CurseForge's file fingerprint: 32-bit MurmurHash2, seed 1, over the file with its tabs, line
 * breaks and spaces left out — what its API matches a jar by.
 */
internal fun curseFingerprint(file: File): Long {
    val all = file.readBytes()
    val data = ByteArray(all.size)
    var n = 0
    for (b in all) if (b != 9.toByte() && b != 10.toByte() && b != 13.toByte() && b != 32.toByte()) data[n++] = b
    val m = 0x5bd1e995
    var h = 1 xor n
    var i = 0
    while (n - i >= 4) {
        var k = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8) or
            ((data[i + 2].toInt() and 0xFF) shl 16) or ((data[i + 3].toInt() and 0xFF) shl 24)
        k *= m
        k = k xor (k ushr 24)
        k *= m
        h *= m
        h = h xor k
        i += 4
    }
    when (n - i) {
        3 -> { h = h xor ((data[i + 2].toInt() and 0xFF) shl 16); h = h xor ((data[i + 1].toInt() and 0xFF) shl 8); h = h xor (data[i].toInt() and 0xFF); h *= m }
        2 -> { h = h xor ((data[i + 1].toInt() and 0xFF) shl 8); h = h xor (data[i].toInt() and 0xFF); h *= m }
        1 -> { h = h xor (data[i].toInt() and 0xFF); h *= m }
    }
    h = h xor (h ushr 13)
    h *= m
    h = h xor (h ushr 15)
    return h.toLong() and 0xFFFFFFFFL
}

/** The body of a CurseForge POST that takes a list of numbers under [key]. */
internal fun idsBody(key: String, ids: Collection<Long>) = buildJsonObject { put(key, JsonArray(ids.map { JsonPrimitive(it) })) }.toString()

private fun sequenceOfNotNull(e: JsonElement?): Sequence<JsonElement> = if (e == null) emptySequence() else sequenceOf(e)

internal fun JsonElement.objects(): List<JsonObject> = jsonArray.map { it.jsonObject }

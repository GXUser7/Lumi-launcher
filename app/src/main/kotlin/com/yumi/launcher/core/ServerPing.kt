package com.yumi.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.Hashtable
import javax.naming.directory.InitialDirContext

/** A piece of a server's description in one style; [color] is 0xRRGGBB, null for the default grey. */
data class MotdSpan(val text: String, val color: Int?, val bold: Boolean = false, val italic: Boolean = false)

/** What a server says about itself in the multiplayer list. */
data class PingResult(
    val motd: List<MotdSpan>,
    val version: String,
    val online: Int,
    val max: Int,
    val players: List<String>,
    val favicon: ByteArray?,
    val latencyMs: Long,
)

/**
 * The server list ping: the handshake the multiplayer screen does to show a server's description,
 * icon and player count. Servers from before 1.7 answer only the older ping, which is tried next.
 */
internal object ServerPing {
    private const val TIMEOUT = 5000

    /** Descriptions with a 64×64 icon run to tens of kilobytes; anything past this is not a status. */
    private const val MAX_STATUS = 1 shl 21

    suspend fun ping(address: String): PingResult = withContext(Dispatchers.IO) {
        val (host, port) = resolve(address)
        try {
            modern(host, port)
        } catch (e: Unreachable) {
            throw e
        } catch (e: Exception) {
            // Whatever an old server made of the modern handshake, the old ping is the one it knows.
            runCatching { legacy(host, port) }.getOrNull() ?: throw (e as? IOException ?: IOException("Сервер ответил непонятно", e))
        }
    }

    /** `host[:port]`; without a port the `_minecraft._tcp` SRV record decides, as it does in the game. */
    private fun resolve(address: String): Pair<String, Int> {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) throw IOException("Пустой адрес")
        if (':' in trimmed) return GameLauncher.splitAddress(trimmed)
        return srv(trimmed) ?: (trimmed to 25565)
    }

    private fun srv(host: String): Pair<String, Int>? = runCatching {
        val env = Hashtable<String, String>().apply {
            put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
            put("com.sun.jndi.dns.timeout.initial", "2000")
            put("com.sun.jndi.dns.timeout.retries", "1")
        }
        val record = InitialDirContext(env).getAttributes("_minecraft._tcp.$host", arrayOf("SRV")).get("SRV")?.get()?.toString()
            ?: return null
        // "priority weight port target."
        val parts = record.trim().split(Regex("\\s+"))
        parts[3].trimEnd('.') to parts[2].toInt()
    }.getOrNull()

    private fun connect(host: String, port: Int) = Socket().apply {
        soTimeout = TIMEOUT
        try {
            connect(InetSocketAddress(host, port), TIMEOUT)
        } catch (e: java.net.UnknownHostException) {
            close()
            throw Unreachable("Адрес не найден")
        } catch (e: IOException) {
            close()
            throw Unreachable("Сервер не отвечает")
        }
    }

    /** Nothing answered at all — no point trying the old ping on the same silence. */
    private class Unreachable(message: String) : IOException(message)

    private fun modern(host: String, port: Int): PingResult = connect(host, port).use { socket ->
        val out = DataOutputStream(socket.getOutputStream())
        val input = DataInputStream(socket.getInputStream())
        packet(out) {
            varInt(it, 0x00)
            varInt(it, 767)
            string(it, host)
            it.writeShort(port)
            varInt(it, 1)
        }
        packet(out) { varInt(it, 0x00) }
        val started = System.nanoTime()
        readVarInt(input)
        if (readVarInt(input) != 0x00) throw IOException("Неожиданный ответ сервера")
        val length = readVarInt(input)
        if (length !in 2..MAX_STATUS) throw IOException("Неожиданный ответ сервера")
        val json = ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
        val firstByte = (System.nanoTime() - started) / 1_000_000
        // The ping packet gives a cleaner latency; a server that ignores it still counts as online.
        val latency = runCatching {
            val sent = System.nanoTime()
            packet(out) {
                varInt(it, 0x01)
                it.writeLong(sent)
            }
            readVarInt(input)
            readVarInt(input)
            input.readLong()
            (System.nanoTime() - sent) / 1_000_000
        }.getOrDefault(firstByte)
        parseStatus(LauncherJson.parseToJsonElement(json).jsonObject, latency)
    }

    private fun parseStatus(status: JsonObject, latency: Long): PingResult {
        val players = status.obj("players")
        val favicon = status.str("favicon")?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() }
        return PingResult(
            motd = status["description"]?.let(::motd).orEmpty(),
            version = status.obj("version")?.str("name").orEmpty(),
            online = players?.get("online")?.jsonPrimitive?.intOrNull ?: 0,
            max = players?.get("max")?.jsonPrimitive?.intOrNull ?: 0,
            players = players?.get("sample")?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
            favicon = favicon?.let { runCatching { Base64.getMimeDecoder().decode(it) }.getOrNull() },
            latencyMs = latency,
        )
    }

    /** 1.4–1.6 answer `0xFE 0x01` with a kick packet: `§1\0protocol\0version\0motd\0online\0max`; older ones with `motd§online§max`. */
    private fun legacy(host: String, port: Int): PingResult = connect(host, port).use { socket ->
        val started = System.nanoTime()
        socket.getOutputStream().apply {
            write(byteArrayOf(0xFE.toByte(), 0x01))
            flush()
        }
        val input = DataInputStream(socket.getInputStream())
        if (input.readUnsignedByte() != 0xFF) throw IOException("Не сервер Minecraft")
        val chars = CharArray(input.readUnsignedShort()) { input.readChar() }
        val latency = (System.nanoTime() - started) / 1_000_000
        val text = String(chars)
        val (version, motd, online, max) = if (text.startsWith("§1")) {
            val parts = text.split('\u0000')
            listOf(parts.getOrElse(2) { "" }, parts.getOrElse(3) { "" }, parts.getOrElse(4) { "0" }, parts.getOrElse(5) { "0" })
        } else {
            val parts = text.split('§')
            listOf("", parts.dropLast(2).joinToString("§"), parts.getOrElse(parts.size - 2) { "0" }, parts.lastOrNull() ?: "0")
        }
        PingResult(legacyText(motd, null, false, false), version, online.toIntOrNull() ?: 0, max.toIntOrNull() ?: 0, emptyList(), null, latency)
    }

    // ---- Description: a chat component, or plain text with § formatting codes, or both nested.

    private fun motd(element: JsonElement): List<MotdSpan> {
        val out = mutableListOf<MotdSpan>()
        fun walk(e: JsonElement, color: Int?, bold: Boolean, italic: Boolean) {
            when (e) {
                is JsonPrimitive -> out += legacyText(e.content, color, bold, italic)
                is JsonArray -> e.forEach { walk(it, color, bold, italic) }
                is JsonObject -> {
                    val c = e.str("color")?.let(::namedColor) ?: color
                    val b = e["bold"]?.jsonPrimitive?.booleanOrNull ?: bold
                    val i = e["italic"]?.jsonPrimitive?.booleanOrNull ?: italic
                    e.str("text")?.let { out += legacyText(it, c, b, i) }
                    e["extra"]?.let { extra -> if (extra is JsonArray) extra.forEach { walk(it, c, b, i) } }
                }
            }
        }
        walk(element, null, false, false)
        return out.filter { it.text.isNotEmpty() }
    }

    private fun legacyText(text: String, color: Int?, bold: Boolean, italic: Boolean): List<MotdSpan> {
        val out = mutableListOf<MotdSpan>()
        var c = color
        var b = bold
        var i = italic
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) out += MotdSpan(buffer.toString(), c, b, i)
            buffer.clear()
        }
        var at = 0
        while (at < text.length) {
            val ch = text[at]
            if (ch == '§' && at + 1 < text.length) {
                val code = text[at + 1].lowercaseChar()
                flush()
                when (code) {
                    in '0'..'9', in 'a'..'f' -> {
                        c = CODES[code.digitToInt(16)]
                        b = false
                        i = false
                    }
                    'l' -> b = true
                    'o' -> i = true
                    'r' -> {
                        c = color
                        b = bold
                        i = italic
                    }
                }
                at += 2
                continue
            }
            buffer.append(ch)
            at++
        }
        flush()
        return out
    }

    private val CODES = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    )
    private val NAMES = listOf(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
    )

    private fun namedColor(name: String): Int? = when {
        name.startsWith("#") -> name.drop(1).toIntOrNull(16)
        else -> NAMES.indexOf(name).takeIf { it >= 0 }?.let { CODES[it] }
    }

    // ---- Wire format.

    private inline fun packet(out: OutputStream, body: (DataOutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream().also { body(DataOutputStream(it)) }.toByteArray()
        val framed = ByteArrayOutputStream()
        varInt(DataOutputStream(framed), bytes.size)
        framed.write(bytes)
        out.write(framed.toByteArray())
        out.flush()
    }

    private fun varInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun string(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        varInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readVarInt(input: InputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Сервер закрыл соединение")
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 35) throw IOException("Слишком длинное число")
        }
    }
}

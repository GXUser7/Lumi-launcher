package com.yumi.launcher.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * A server's `server.properties`, edited in place: lines the launcher does not touch — comments,
 * keys it has no field for, the order the server wrote them in — stay as they were. Values are
 * written with `\uXXXX` escapes past ASCII, which every version reads back, so a Russian MOTD
 * survives servers that read the file as Latin-1.
 */
class ServerProperties(private val file: File) {
    private val lines: MutableList<String> = if (file.isFile) {
        decode(file.readBytes()).lineSequence().toMutableList().apply { if (lastOrNull()?.isEmpty() == true) removeAt(lastIndex) }
    } else {
        mutableListOf()
    }

    operator fun get(key: String): String? = lines.firstNotNullOfOrNull { line ->
        parse(line)?.takeIf { it.first == key }?.second
    }

    operator fun set(key: String, value: String) {
        val line = "$key=${escape(value)}"
        val at = lines.indexOfFirst { parse(it)?.first == key }
        if (at >= 0) lines[at] = line else lines += line
    }

    fun bool(key: String, default: Boolean) = get(key)?.trim()?.toBooleanStrictOrNull() ?: default
    fun int(key: String, default: Int) = get(key)?.trim()?.toIntOrNull() ?: default

    fun save() {
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.ISO_8859_1)
    }

    /**
     * New servers rewrite the file in plain UTF-8 on every start, old ones in Latin-1 with escapes.
     * UTF-8 first, as the game itself reads it: Latin-1 text is almost never valid UTF-8, while
     * UTF-8 read as Latin-1 turns «сервер» into «ÑÐµÑ€Ð²ÐµÑ€».
     */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charsets.ISO_8859_1)
    }

    private fun parse(line: String): Pair<String, String>? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty() || trimmed[0] == '#' || trimmed[0] == '!') return null
        val split = trimmed.indexOfFirst { it == '=' || it == ':' }
        if (split < 0) return trimmed to ""
        return trimmed.substring(0, split).trim() to unescape(trimmed.substring(split + 1).trimStart())
    }

    private fun escape(value: String) = buildString {
        value.forEachIndexed { i, c ->
            when {
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == ' ' && i == 0 -> append("\\ ")
                c.code > 126 || c.code < 32 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }

    private fun unescape(value: String) = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i + 1 >= value.length) {
                append(c)
                i++
                continue
            }
            when (val next = value[i + 1]) {
                'u' -> {
                    val code = value.substring(i + 2, minOf(i + 6, value.length)).toIntOrNull(16)
                    if (code != null) {
                        append(code.toChar())
                        i += 6
                        continue
                    }
                    append(next)
                }
                'n' -> append('\n')
                't' -> append('\t')
                else -> append(next)
            }
            i += 2
        }
    }
}

/** The world's rules, as Minecraft's own settings name them. */
enum class GameMode(val label: String, val key: String) {
    Survival("Выживание", "survival"),
    Creative("Творчество", "creative"),
    Adventure("Приключение", "adventure"),
    Spectator("Наблюдатель", "spectator");

    companion object {
        /** Before 1.14 the file held numbers: 0 survival … 3 spectator. */
        fun of(value: String?) = entries.firstOrNull { it.key == value?.trim() } ?: value?.trim()?.toIntOrNull()?.let { entries.getOrNull(it) } ?: Survival
    }
}

enum class Difficulty(val label: String, val key: String) {
    Peaceful("Мирно", "peaceful"),
    Easy("Легко", "easy"),
    Normal("Нормально", "normal"),
    Hard("Сложно", "hard");

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.key == value?.trim() } ?: value?.trim()?.toIntOrNull()?.let { entries.getOrNull(it) } ?: Normal
    }
}

/** True for versions whose `server.properties` holds game mode and difficulty as numbers. */
internal fun numericProperties(version: String): Boolean {
    val minor = Regex("""^1\.(\d+)""").find(version)?.groupValues?.get(1)?.toIntOrNull()
    return when {
        minor != null -> minor < 14
        // Alphas, betas and the classic-era ids: all long before names.
        version.firstOrNull()?.let { it == 'a' || it == 'b' || it == 'c' } == true -> true
        version.startsWith("rd-") || version.startsWith("inf-") -> true
        else -> false
    }
}

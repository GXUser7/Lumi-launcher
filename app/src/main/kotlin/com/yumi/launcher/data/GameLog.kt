package com.yumi.launcher.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns the game's output into readable lines. Mojang's logging config — the one that closes
 * Log4Shell on old versions, so it stays — writes log4j XML events for its own launcher to parse;
 * those become `[12:00:01] [Render thread/INFO]: message`. Anything else the game prints goes
 * through as it is.
 */
internal object GameLog {
    private const val OPEN = "<log4j:Event"
    private const val CLOSE = "</log4j:Event>"
    private val level = Regex("""level="([^"]*)"""")
    private val thread = Regex("""thread="([^"]*)"""")
    private val timestamp = Regex("""timestamp="(\d+)"""")
    private val message = Regex("""<log4j:Message><!\[CDATA\[(.*?)]]></log4j:Message>""", RegexOption.DOT_MATCHES_ALL)
    private val throwable = Regex("""<log4j:Throwable><!\[CDATA\[(.*?)]]></log4j:Throwable>""", RegexOption.DOT_MATCHES_ALL)
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /** Takes every complete line or event off the front of [pending], leaving a partial tail for next time. */
    fun drain(pending: StringBuilder): List<String> {
        val out = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val newline = pending.indexOf("\n")
            val event = pending.indexOf(OPEN)
            if (event >= 0 && (newline < 0 || event < newline) && pending.substring(0, event).isBlank()) {
                val end = pending.indexOf(CLOSE, event)
                if (end < 0) break
                out += format(pending.substring(event, end + CLOSE.length))
                pending.delete(0, end + CLOSE.length)
                while (pending.isNotEmpty() && (pending[0] == '\n' || pending[0] == '\r')) pending.deleteCharAt(0)
            } else {
                if (newline < 0) break
                val line = pending.substring(0, newline).trimEnd('\r')
                if (line.isNotBlank()) out += line
                pending.delete(0, newline + 1)
            }
        }
        return out
    }

    private fun format(event: String): List<String> {
        val time = timestamp.find(event)?.groupValues?.get(1)?.toLongOrNull()?.let { clock.format(Instant.ofEpochMilli(it)) } ?: ""
        val head = "[$time] [${thread.find(event)?.groupValues?.get(1)}/${level.find(event)?.groupValues?.get(1)}]: "
        val text = message.find(event)?.groupValues?.get(1).orEmpty()
        val lines = text.lines()
        return listOf(head + lines.first()) + lines.drop(1) +
            (throwable.find(event)?.groupValues?.get(1)?.lines()?.filter { it.isNotBlank() } ?: emptyList())
    }
}

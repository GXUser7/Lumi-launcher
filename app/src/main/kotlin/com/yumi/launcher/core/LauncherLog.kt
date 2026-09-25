package com.yumi.launcher.core

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

/**
 * The launcher's own log: whatever it prints — and what Compose prints when something throws — also
 * goes to `logs/launcher.log` in [LauncherPaths.config], so a problem in the launcher itself can be
 * sent along with the game's logs. The previous run's file is kept beside it as `launcher.1.log`.
 */
object LauncherLog {
    val file: File get() = LauncherPaths.config.resolve("logs").resolve("launcher.log")
    val previous: File get() = LauncherPaths.config.resolve("logs").resolve("launcher.1.log")

    fun install() {
        runCatching {
            val log = file
            log.parentFile.mkdirs()
            if (log.isFile) {
                previous.delete()
                log.renameTo(previous)
            }
            val out = FileOutputStream(log, true)
            System.setOut(PrintStream(Tee(System.out, out), true, Charsets.UTF_8))
            System.setErr(PrintStream(Tee(System.err, out), true, Charsets.UTF_8))
            println("Yumi Launcher ${Support.VERSION} · ${System.getProperty("os.name")} ${System.getProperty("os.version")} · Java ${System.getProperty("java.version")}")
        }
    }

    /** Writes to the console as before, and to the file; a full disk must not take the launcher down. */
    private class Tee(private val console: OutputStream, private val file: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            console.write(b)
            runCatching { file.write(b) }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            console.write(b, off, len)
            runCatching { file.write(b, off, len) }
        }

        override fun flush() {
            console.flush()
            runCatching { file.flush() }
        }
    }
}

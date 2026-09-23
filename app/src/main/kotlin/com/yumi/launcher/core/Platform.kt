package com.yumi.launcher.core

import java.io.File

/** The machine, in the words Mojang's version files use for it. */
internal object Platform {
    private val osName = System.getProperty("os.name").lowercase()
    private val archName = System.getProperty("os.arch").lowercase()

    /** `windows`, `osx` or `linux` — the values `rules[].os.name` compares against. */
    val os: String = when {
        "win" in osName -> "windows"
        "mac" in osName -> "osx"
        else -> "linux"
    }

    val arm: Boolean = "aarch64" in archName || archName.startsWith("arm")
    val is32: Boolean = archName in setOf("x86", "i386", "i486", "i586", "i686")

    /** `rules[].os.arch` only ever says `x86`, and means a 32-bit machine by it. */
    val ruleArch: String = if (is32) "x86" else if (arm) "arm64" else "x86_64"

    /** The key of this machine in Mojang's Java runtime index. */
    val runtimeKey: String = when (os) {
        "windows" -> if (arm) "windows-arm64" else if (is32) "windows-x86" else "windows-x64"
        "osx" -> if (arm) "mac-os-arm64" else "mac-os"
        else -> if (is32) "linux-i386" else "linux"
    }

    val classpathSeparator: String = File.pathSeparator

    /** `javaw` on Windows so the game does not bring a console window with it. */
    val javaExecutable: String = if (os == "windows") "javaw.exe" else "java"

    /**
     * Modern version files list every native build for an OS under one rule — Windows x64, x86
     * and arm64 alike — and leave the choice to the launcher. True for the ones this machine
     * cannot load.
     */
    fun foreignNative(classifier: String): Boolean {
        val c = classifier.lowercase()
        return when {
            c.endsWith("-arm64") || c.endsWith("-aarch64") -> !arm
            c.endsWith("-x86") -> !is32
            else -> false
        }
    }
}

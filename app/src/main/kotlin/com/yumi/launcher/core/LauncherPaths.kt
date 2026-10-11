package com.yumi.launcher.core

import java.io.File

/**
 * Where the launcher keeps things. Versions, libraries, assets and Java runtimes are shared by
 * every pack; each pack only owns its folder — worlds, mods, options.
 */
object LauncherPaths {
    /**
     * The launcher's own corner of the user profile: caches and the quick-join list, nothing heavy.
     * Settable only so a smoke run can keep its own.
     *
     * Players from before the launcher was renamed Lumi keep `YumiLauncher`: it is also the game
     * folder the launcher suggested, so their settings point into it, worlds and all.
     */
    @Volatile
    var config: File = (System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"))).let { appData ->
        appData.resolve("YumiLauncher").takeIf { it.isDirectory } ?: appData.resolve("LumiLauncher")
    }

    /** Where Minecraft is downloaded: the folder the player picked, [config] until they have. */
    @Volatile
    var root: File = config

    val versions: File get() = root.resolve("versions")
    val libraries: File get() = root.resolve("libraries")
    val assets: File get() = root.resolve("assets")
    val runtimes: File get() = root.resolve("runtime")
    val instances: File get() = root.resolve("instances")
    val cache: File get() = config.resolve("cache")
}

package com.yumi.launcher.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

enum class Section(val label: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home),
    Mods("Моды", Icons.Rounded.Extension),
    Packs("Сборки", Icons.Rounded.ViewInAr),
    Servers("Серверы", Icons.Rounded.Dns),
    Settings("Настройки", Icons.Rounded.Settings),
    Console("Консоль", Icons.Rounded.Terminal);

    companion object {
        /** What the top bar lists; Settings and the console live behind their own buttons. */
        val navigation = listOf(Home, Mods, Packs, Servers)
    }
}

/** The play button's look — the game's actual state is [com.yumi.launcher.data.PlayStatus]. */
enum class PlayState { Idle, Launching, Running }

/** Something another screen asked the packs screen to open with. */
sealed interface PackRequest {
    data object Create : PackRequest
    data object Import : PackRequest
    data class Edit(val id: String) : PackRequest
}

/** The servers screen's two halves: servers this computer runs, and other people's to join. */
enum class ServersTab(val label: String) { Hosted("Мои серверы"), QuickJoin("Быстрый вход") }

/** The tabs of one server's page. */
enum class ServerTab(val label: String, val icon: ImageVector) {
    Console("Терминал", Icons.Rounded.Terminal),
    Settings("Настройки", Icons.Rounded.Tune),
    Bans("Баны", Icons.Rounded.Block),
    Mods("Моды", Icons.Rounded.Extension),
}

/** Where the "my servers" half is. */
sealed interface ServerView {
    data object List : ServerView
    data object Create : ServerView
    data class Detail(val id: String) : ServerView
}

/** Where the window is — navigation only; everything that outlives a screen is in the model. */
class LauncherState {
    var section by mutableStateOf(Section.Home)
    var news by mutableIntStateOf(0)
    var packRequest by mutableStateOf<PackRequest?>(null)

    // Kept here rather than in the screen, so a server's terminal is still open after a trip to the home screen.
    var serversTab by mutableStateOf(ServersTab.Hosted)
    var serverView by mutableStateOf<ServerView>(ServerView.List)
    var serverTab by mutableStateOf(ServerTab.Console)

    /** The Ely.by sign-in form is over the window. */
    var elySignIn by mutableStateOf(false)

    fun openPacks(request: PackRequest? = null) {
        packRequest = request
        section = Section.Packs
    }
}

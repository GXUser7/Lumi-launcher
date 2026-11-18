package com.yumi.launcher.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.yumi.launcher.scene.SceneSpot
import com.yumi.launcher.ui.packs.PackSearch

/** A part of the launcher, and the place on the island the camera shows while it is open. */
enum class Section(val label: String, val icon: ImageVector, val spot: SceneSpot) {
    Home("Главная", Icons.Rounded.Home, SceneSpot.Overview),
    Mods("Моды", Icons.Rounded.Extension, SceneSpot.Forge),
    Packs("Сборки", Icons.Rounded.ViewInAr, SceneSpot.Library),
    Servers("Серверы", Icons.Rounded.Dns, SceneSpot.Portal),
    Settings("Настройки", Icons.Rounded.Settings, SceneSpot.Campfire),
    Console("Консоль", Icons.Rounded.Terminal, SceneSpot.Overview);

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

/** A server a pack is being made for: one in the quick-join list, or the one a friend is playing on. */
sealed interface ServerPackTarget {
    data class Saved(val id: String) : ServerPackTarget

    /**
     * On the way to a friend: [name] is what the new pack is called — the friend's own pack's
     * name, when they share it — and [packId] the pack to go in with instead, as things are.
     */
    data class Friend(val friend: String, val address: String, val name: String, val packId: String?) : ServerPackTarget
}

/** The servers screen's two halves: servers this computer runs, and other people's to join. */
enum class ServersTab(val label: String) { Hosted("Мои серверы"), QuickJoin("Быстрый вход") }

/** The mods screen's two halves: browse the catalogue or manage installed content. */
enum class ModsTab(val label: String) { Catalog("Каталог"), Installed("Установлено") }

/** The tabs of one server's page. */
enum class ServerTab(val label: String, val icon: ImageVector) {
    Console("Терминал", Icons.Rounded.Terminal),
    Settings("Настройки", Icons.Rounded.Tune),
    Whitelist("Вайтлист", Icons.Rounded.VerifiedUser),
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
    var modsTab by mutableStateOf(ModsTab.Catalog)
    var serversTab by mutableStateOf(ServersTab.Hosted)
    var serverView by mutableStateOf<ServerView>(ServerView.List)
    var serverTab by mutableStateOf(ServerTab.Console)

    /** The Ely.by sign-in form is over the window. */
    var elySignIn by mutableStateOf(false)

    /** The Microsoft sign-in, with its code to confirm in the browser, is over the window. */
    var microsoftSignIn by mutableStateOf(false)

    /** The server whose pack is being made, over the window. */
    var serverPack by mutableStateOf<ServerPackTarget?>(null)

    /** The newcomer's guide and where the things it points at are. */
    val guide = GuideState()

    /** The catalogue whose filters are in the panel right of the card; null — no panel. */
    var filters by mutableStateOf<FilterPanel?>(null)

    /** Top bar popovers tracked here so shortcuts and headless checks can toggle/close them. */
    var downloadsOpen by mutableStateOf(false)
    var notificationsOpen by mutableStateOf(false)

    /** The modpack catalogue's search, kept here so the filters panel can change it and a return finds it as it was. */
    val packSearch = PackSearch()

    /** Shared scroll state for Settings so smoke verification and section returns preserve scroll position. */
    val settingsScroll = ScrollState(0)

    fun openPacks(request: PackRequest? = null) {
        packRequest = request
        section = Section.Packs
    }
}

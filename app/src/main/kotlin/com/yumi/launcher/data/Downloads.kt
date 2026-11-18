package com.yumi.launcher.data

import androidx.compose.runtime.mutableStateListOf

/** Which part of the launcher a download belongs to; the panel picks its glyph by it. */
enum class DownloadKind { Game, Pack, Content, Server, Update }

/** Something downloading right now. [progress] is null when only "busy" is known. */
class ActiveDownload(
    val key: String,
    val title: String,
    val detail: String,
    val progress: Float?,
    val kind: DownloadKind,
    val cancel: (() -> Unit)? = null,
)

/** A download that has finished — or failed, with [error] saying why. */
class FinishedDownload(val title: String, val what: String, val error: String?, val at: Long = System.currentTimeMillis())

/**
 * The downloads button's list: what every part of the launcher that downloads is doing now — the
 * game before a start, a modpack, mods and shaders, a server — gathered from their own state, and
 * what finished while the launcher was open.
 */
class Downloads(private val model: LauncherModel) {
    val recent = mutableStateListOf<FinishedDownload>()

    /** Read in composition, this follows the state it is gathered from. */
    val active: List<ActiveDownload>
        get() = buildList {
            (model.status as? PlayStatus.Preparing)?.let { status ->
                add(ActiveDownload("game", model.launching ?: "Minecraft", status.stage.label, status.fraction, DownloadKind.Game, model::stop))
            }
            val imports = model.packImports
            imports.title?.let { title -> add(ActiveDownload("pack", title, imports.stage, imports.progress, DownloadKind.Pack, imports::cancel)) }
            model.mods.installing.forEach { (key, title) -> add(ActiveDownload(key, title, "Скачиваем", null, DownloadKind.Content)) }
            for (server in model.servers.all) {
                val status = model.servers.session(server.id).status as? ServerStatus.Installing ?: continue
                add(ActiveDownload("server:${server.id}", server.name, status.step, status.fraction, DownloadKind.Server))
            }
            (model.updater.state as? AppUpdateState.Downloading)?.let { upd ->
                val frac = if (upd.total > 0) (upd.downloaded.toFloat() / upd.total).coerceIn(0f, 1f) else null
                add(ActiveDownload("app-update", "Lumi Launcher ${upd.release.version}", "Обновление лаунчера", frac, DownloadKind.Update, model.updater::cancelDownload))
            }
        }

    /** Notes a finished download at the top of the recent list; [what] is its kind in a word — "мод", "сборка". */
    fun record(title: String, what: String, error: String?) {
        recent.add(0, FinishedDownload(title, what, error))
        if (recent.size > MAX_RECENT) recent.removeRange(MAX_RECENT, recent.size)
    }

    fun clear() = recent.clear()

    private companion object {
        const val MAX_RECENT = 30
    }
}

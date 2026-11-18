package com.yumi.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yumi.launcher.core.AppRelease
import com.yumi.launcher.core.AppVersion
import com.yumi.launcher.core.GitHubReleaseSource
import com.yumi.launcher.core.Support
import com.yumi.launcher.core.UpdateIntegrityException
import com.yumi.launcher.core.UpdateSource
import com.yumi.launcher.core.VerifiedUpdate
import com.yumi.launcher.core.installedDirectory
import com.yumi.launcher.core.launchWindowsInstaller
import com.yumi.launcher.settings.LauncherSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val checkedAt: Long) : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val downloaded: Long, val total: Long) : AppUpdateState
    data class Ready(val update: VerifiedUpdate) : AppUpdateState
    data class Failed(val release: AppRelease?, val message: String, val integrity: Boolean = false) : AppUpdateState
}

/**
 * Coordinates automatic release checks, verified SHA-256 staging downloads, and quiet
 * hand-off to the Inno Setup installer on restart or exit.
 */
class AppUpdater(
    private val settings: LauncherSettings,
    private val scope: CoroutineScope,
    private val source: UpdateSource = GitHubReleaseSource(),
    val currentVersion: String = Support.VERSION,
    val installed: Boolean = installedDirectory() != null,
    autoCheck: Boolean = true,
    private val isBusy: () -> Boolean = { false },
    private val onRecordDownload: (String, String, String?) -> Unit = { _, _, _ -> },
) {
    var state by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)

    private val checkMutex = Mutex()
    private var downloadJob: Job? = null

    val release: AppRelease?
        get() = when (val current = state) {
            is AppUpdateState.Available -> current.release
            is AppUpdateState.Downloading -> current.release
            is AppUpdateState.Ready -> current.update.release
            is AppUpdateState.Failed -> current.release
            else -> null
        }

    init {
        if (autoCheck && source.supported) {
            scope.launch {
                delay(INITIAL_CHECK_DELAY_MS)
                while (true) {
                    if (settings.autoUpdate) check(manual = false)
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }

    fun check(manual: Boolean = false) {
        if (!source.supported) {
            if (manual) {
                state = AppUpdateState.Failed(null, "Автообновление работает только в Windows-версии.")
            }
            return
        }
        val current = state
        if (current is AppUpdateState.Checking || current is AppUpdateState.Downloading) return

        scope.launch {
            if (!checkMutex.tryLock()) return@launch
            try {
                val previous = state
                state = AppUpdateState.Checking
                val found = source.check(currentVersion)
                if (found == null) {
                    val ready = previous as? AppUpdateState.Ready
                    val curParsed = AppVersion.parse(currentVersion)
                    val readyParsed = ready?.let { AppVersion.parse(it.update.release.version) }
                    state = if (ready != null && curParsed != null && readyParsed != null && readyParsed > curParsed && ready.update.verify()) {
                        ready
                    } else {
                        AppUpdateState.UpToDate(System.currentTimeMillis())
                    }
                    return@launch
                }

                // Reuse already verified installer if it already targets the newest release.
                val ready = previous as? AppUpdateState.Ready
                if (ready != null && ready.update.release.version == found.version && ready.update.verify()) {
                    state = ready
                    return@launch
                }

                state = AppUpdateState.Available(found)
                if (installed && settings.autoUpdate) {
                    startDownload(found, recordFailure = manual)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось проверить обновления"
                state = if (manual || e is UpdateIntegrityException) {
                    AppUpdateState.Failed(null, msg, integrity = e is UpdateIntegrityException)
                } else {
                    AppUpdateState.Idle
                }
            } finally {
                checkMutex.unlock()
            }
        }
    }

    fun download() {
        val target = when (val current = state) {
            is AppUpdateState.Available -> current.release
            is AppUpdateState.Failed -> current.release
            else -> null
        } ?: return
        startDownload(target, recordFailure = true)
    }

    fun cancelDownload() {
        val active = state as? AppUpdateState.Downloading ?: return
        downloadJob?.cancel()
        downloadJob = null
        state = AppUpdateState.Available(active.release)
    }

    fun setAutoUpdate(enabled: Boolean) {
        settings.autoUpdate = enabled
        if (!enabled) {
            cancelDownload()
        } else {
            val current = state
            if (installed && current is AppUpdateState.Available) {
                startDownload(current.release, recordFailure = false)
            } else if (current is AppUpdateState.Idle) {
                check(manual = false)
            }
        }
    }

    fun installNow(onExit: () -> Unit): Boolean {
        if (!installed || isBusy()) return false
        val ready = state as? AppUpdateState.Ready ?: return false
        if (!ready.update.verify()) {
            state = AppUpdateState.Failed(ready.update.release, "Файл обновления повреждён или был изменён.", integrity = true)
            return false
        }
        val launched = launchWindowsInstaller(ready.update, relaunch = true)
        if (launched) onExit()
        return launched
    }

    fun installOnExitIfReady() {
        if (!settings.autoUpdate || !installed || isBusy()) return
        val ready = state as? AppUpdateState.Ready ?: return
        launchWindowsInstaller(ready.update, relaunch = false)
    }

    private fun startDownload(release: AppRelease, recordFailure: Boolean) {
        if (downloadJob?.isActive == true) return
        state = AppUpdateState.Downloading(release, 0L, release.size)
        downloadJob = scope.launch {
            var lastTick = 0L
            try {
                val verified = source.download(release) { done, total ->
                    val now = System.currentTimeMillis()
                    if (done >= total || now - lastTick >= PROGRESS_THROTTLE_MS) {
                        lastTick = now
                        state = AppUpdateState.Downloading(release, done, total)
                    }
                }
                state = AppUpdateState.Ready(verified)
                onRecordDownload("Lumi Launcher ${release.version}", "обновление", null)
            } catch (_: CancellationException) {
                state = AppUpdateState.Available(release)
            } catch (e: Throwable) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось скачать обновление"
                state = AppUpdateState.Failed(release, msg, integrity = e is UpdateIntegrityException)
                if (recordFailure) {
                    onRecordDownload("Lumi Launcher ${release.version}", "обновление", msg)
                }
            } finally {
                downloadJob = null
            }
        }
    }

    private companion object {
        const val INITIAL_CHECK_DELAY_MS = 8_000L
        const val CHECK_INTERVAL_MS = 4 * 3_600_000L
        const val PROGRESS_THROTTLE_MS = 90L
    }
}

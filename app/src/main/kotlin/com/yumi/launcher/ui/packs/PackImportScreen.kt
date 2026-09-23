package com.yumi.launcher.ui.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Download
import com.yumi.launcher.core.Http
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.PackArchives
import com.yumi.launcher.core.PackCatalog
import com.yumi.launcher.core.PackHit
import com.yumi.launcher.core.PackRelease
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.ui.components.RemoteIcon
import com.yumi.launcher.ui.components.PackIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
internal fun PackImportScreen(model: LauncherModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var source by remember { mutableStateOf(ModSource.Modrinth) }
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PackHit>>(emptyList()) }
    var chosen by remember { mutableStateOf<PackHit?>(null) }
    var releases by remember { mutableStateOf<List<PackRelease>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val key = model.settings.curseForgeKey.trim()

    LaunchedEffect(source, query, version, key) {
        chosen = null
        error = null
        if (source == ModSource.CurseForge && key.isBlank()) { hits = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        try {
            hits = PackCatalog.search(source, query.trim(), version.trim(), key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось найти сборки: ${e.message}"
        } finally {
            searching = false
        }
    }

    fun importArchive(file: File, serverRelease: PackRelease? = null) {
        if (busy) return
        model.withGameFolder {
            scope.launch {
                busy = true
                error = null
                progress = 0f
                var created: com.yumi.launcher.data.Instance? = null
                try {
                    val info = PackArchives.inspect(file)
                    val pack = model.instances.create(info.name, info.version, model.instances.all.size % PackIcons.size)
                    created = pack
                    model.instances.save(pack.copy(loader = info.loader, loaderVersion = info.loaderVersion))
                    val report: (Float) -> Unit = { value -> scope.launch(Dispatchers.Main) { progress = value } }
                    PackArchives.install(file, model.instances.folder(pack), key, report)
                    if (serverRelease != null) {
                        val serverZip = LauncherPaths.cache.resolve("modpacks/server-${serverRelease.id}.zip")
                        Http.downloadAll(listOf(Download(serverRelease.url, serverZip, serverRelease.sha1, serverRelease.size))) {}
                        PackArchives.installServerArchive(serverZip, model.instances.folder(pack))
                    }
                    model.instances[pack.id]?.let(model::select)
                    onDone()
                } catch (e: Exception) {
                    created?.let(model.instances::delete)
                    error = "Не удалось импортировать сборку: ${e.message}"
                } finally {
                    busy = false
                }
            }
        }
    }

    fun downloadRelease(release: PackRelease) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val ext = if (source == ModSource.Modrinth) "mrpack" else "zip"
                val archive = LauncherPaths.cache.resolve("modpacks/${source.name}-${release.id}.$ext")
                Http.downloadAll(listOf(Download(release.url, archive, release.sha1, release.size))) { value ->
                    scope.launch(Dispatchers.Main) { progress = value * 0.2f }
                }
                val serverRelease = release.serverId?.takeIf { it > 0 }?.let { PackCatalog.serverRelease(chosen!!.id, it, key) }
                busy = false
                importArchive(archive, serverRelease)
            } catch (e: Exception) {
                error = "Не удалось скачать сборку: ${e.message}"
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { if (chosen != null) chosen = null else onDone() }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
            }
            Text(chosen?.name ?: "Готовые сборки", style = MaterialTheme.typography.headlineSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (chosen == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val dialog = FileDialog(null as Frame?, "Импорт сборки", FileDialog.LOAD).apply {
                        filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".mrpack", true) || name.endsWith(".zip", true) }
                        isVisible = true
                    }
                    dialog.file?.let { importArchive(File(dialog.directory, it)) }
                }, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Импорт файла")
                }
                ModSource.entries.forEach { item ->
                    OutlinedButton(onClick = { source = item }, enabled = !busy,
                        border = if (source == item) androidx.compose.foundation.BorderStroke(1.dp, scheme.primary) else null) {
                        Text(item.label)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Поиск сборки") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                OutlinedTextField(version, { version = it }, label = { Text("Версия Minecraft") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.width(180.dp))
            }
            if (source == ModSource.CurseForge && key.isBlank()) Text("Для каталога CurseForge добавьте ключ API в настройках.", color = scheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
        if (busy) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Устанавливаем сборку…", style = MaterialTheme.typography.bodySmall)
        } else if (searching) {
            LoadingIndicator()
        } else if (chosen == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hits, key = { it.id }) { hit ->
                    Surface(onClick = {
                        chosen = hit
                        releases = emptyList()
                        searching = true
                        scope.launch {
                            try { releases = PackCatalog.releases(source, hit.id, version.trim(), key) }
                            catch (e: Exception) { error = "Не удалось загрузить версии: ${e.message}" }
                            finally { searching = false }
                        }
                    }, shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RemoteIcon(hit.icon, Icons.Rounded.Inventory2, 44.dp)
                            Column {
                                Text(hit.name, style = MaterialTheme.typography.titleMedium)
                                Text(hit.summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else {
            Text("Выберите версию сборки", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(releases, key = { it.id }) { release ->
                    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(release.name, style = MaterialTheme.typography.titleSmall)
                                Text(release.date, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { downloadRelease(release) }) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Установить")
                            }
                        }
                    }
                }
            }
        }
    }
}

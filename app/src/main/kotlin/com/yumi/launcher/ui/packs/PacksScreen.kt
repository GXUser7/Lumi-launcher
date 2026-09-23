package com.yumi.launcher.ui.packs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.LoaderVersion
import com.yumi.launcher.core.Loaders
import com.yumi.launcher.core.VersionEntry
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.PackIcons
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.gigabytes
import com.yumi.launcher.ui.home.PackRequest

/** Where the packs screen is: the list, one pack's editor, or the editor's version picker. */
private sealed interface Mode {
    data object List : Mode
    data object Import : Mode
    data class Edit(val draft: Draft) : Mode
    data class PickVersion(val draft: Draft) : Mode
}

/** A pack being edited; nothing reaches disk until "Сохранить". */
private class Draft(
    val id: String?,
    name: String,
    version: String,
    loader: Loader,
    loaderVersion: String?,
    icon: Int,
    memoryMb: Int?,
    jvmArgs: String,
    width: Int?,
    height: Int?,
    fullscreen: Boolean,
) {
    var name by mutableStateOf(name)
    var version by mutableStateOf(version)
    var loader by mutableStateOf(loader)

    /** Null: the newest stable release, picked and pinned on first launch. */
    var loaderVersion by mutableStateOf(loaderVersion)
    var icon by mutableIntStateOf(icon)
    var customMemory by mutableStateOf(memoryMb != null)
    var memoryMb by mutableIntStateOf(memoryMb ?: 4096)
    var jvmArgs by mutableStateOf(jvmArgs)
    var width by mutableStateOf(width?.toString() ?: "")
    var height by mutableStateOf(height?.toString() ?: "")
    var fullscreen by mutableStateOf(fullscreen)

    val valid get() = name.isNotBlank() && version.isNotBlank()

    companion object {
        fun of(instance: Instance) = Draft(
            instance.id, instance.name, instance.version, instance.loader, instance.loaderVersion, instance.icon,
            instance.memoryMb, instance.jvmArgs, instance.width, instance.height, instance.fullscreen,
        )
        fun new(latest: String?, icon: Int) = Draft(null, "", latest ?: "", Loader.Vanilla, null, icon, null, "", null, null, false)
    }
}

@Composable
fun PacksScreen(model: LauncherModel, request: PackRequest?, onRequestHandled: () -> Unit, onPlay: (Instance) -> Unit) {
    var mode by remember { mutableStateOf<Mode>(Mode.List) }
    LaunchedEffect(request) {
        when (request) {
            PackRequest.Create -> mode = Mode.Edit(Draft.new(model.manifest?.latest?.release, model.instances.all.size % PackIcons.size))
            PackRequest.Import -> mode = Mode.Import
            is PackRequest.Edit -> model.instances[request.id]?.let { mode = Mode.Edit(Draft.of(it)) }
            null -> Unit
        }
        if (request != null) onRequestHandled()
    }
    AnimatedContent(
        targetState = mode,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(200, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
    ) { current ->
        when (current) {
            Mode.List -> PackList(
                model,
                onCreate = { mode = Mode.Edit(Draft.new(model.manifest?.latest?.release, model.instances.all.size % PackIcons.size)) },
                onImport = { mode = Mode.Import },
                onEdit = { mode = Mode.Edit(Draft.of(it)) },
                onPlay = onPlay,
            )
            Mode.Import -> PackImportScreen(model) { mode = Mode.List }
            is Mode.Edit -> PackEditor(
                model,
                current.draft,
                onPickVersion = { mode = Mode.PickVersion(current.draft) },
                onDone = { mode = Mode.List },
            )
            is Mode.PickVersion -> VersionPicker(model, current.draft.version, onBack = { mode = Mode.Edit(current.draft) }) {
                if (it != current.draft.version) current.draft.loaderVersion = null
                current.draft.version = it
                mode = Mode.Edit(current.draft)
            }
        }
    }
}

@Composable
private fun PackList(model: LauncherModel, onCreate: () -> Unit, onImport: () -> Unit, onEdit: (Instance) -> Unit, onPlay: (Instance) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Сборки", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Готовые сборки")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onCreate, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Новая")
            }
        }
        model.manifestError?.let { ErrorLine(it) { model.refreshVersions() } }
        if (model.instances.all.isEmpty()) {
            Text(
                "Сборка — это версия Minecraft со своей папкой: миры, настройки и потом моды у каждой отдельно.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(model.instances.all, key = { it.id }) { instance ->
                val chosen = instance.id == model.selected?.id
                Surface(
                    onClick = { onEdit(instance) },
                    shape = RoundedCornerShape(24.dp),
                    color = if (chosen) scheme.surfaceContainerHigh else scheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PackTile(instance.icon, 52.dp)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(instance.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (chosen) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Rounded.CheckCircle, "Выбрана", Modifier.size(16.dp), tint = scheme.primary)
                                }
                            }
                            Text("${instance.loader.label} ${instance.version}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(
                            onClick = { onPlay(instance) },
                            enabled = !model.busy,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Rounded.PlayArrow, "Играть в «${instance.name}»") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackEditor(model: LauncherModel, draft: Draft, onPickVersion: () -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val existing = draft.id?.let { model.instances[it] }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(if (existing == null) "Новая сборка" else "Сборка", onDone)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft.name = it.take(40) },
                label = { Text("Название") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Label("Версия")
            Surface(onClick = onPickVersion, shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShapeTile(Icons.Rounded.ViewInAr, scheme.primaryContainer, scheme.onPrimaryContainer, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(draft.version.ifEmpty { "Выберите версию" }, style = MaterialTheme.typography.titleMedium)
                        val entry = model.manifest?.versions?.firstOrNull { it.id == draft.version }
                        Text(entry?.let { "${typeLabel(it.type)} · ${it.releaseTime.take(10)}" } ?: "Ванилла", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
                }
            }
            Label("Загрузчик модов")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Loader.entries.forEach { loader ->
                    FilterChip(
                        selected = draft.loader == loader,
                        onClick = {
                            if (draft.loader != loader) draft.loaderVersion = null
                            draft.loader = loader
                        },
                        label = { Text(loader.label) },
                    )
                }
            }
            if (draft.loader != Loader.Vanilla && draft.version.isNotBlank()) {
                LoaderVersionPicker(draft.loader, draft.version, draft.loaderVersion) { draft.loaderVersion = it }
            }
            Label("Значок")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackIcons.indices.forEach { i ->
                    val picked = draft.icon == i
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable { draft.icon = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        PackTile(i, if (picked) 44.dp else 34.dp)
                    }
                }
            }
            SwitchRow("Своя память", draft.customMemory) { draft.customMemory = it }
            if (draft.customMemory) {
                MemorySlider(draft.memoryMb) { draft.memoryMb = it }
            } else {
                Text("Как в настройках лаунчера: ${gigabytes(model.settings.memoryMb)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = draft.jvmArgs,
                onValueChange = { draft.jvmArgs = it },
                label = { Text("Аргументы Java") },
                placeholder = { Text("-XX:+UseZGC") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Label("Окно игры")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Ширина", draft.width, Modifier.weight(1f)) { draft.width = it }
                NumberField("Высота", draft.height, Modifier.weight(1f)) { draft.height = it }
            }
            SwitchRow("Полный экран", draft.fullscreen) { draft.fullscreen = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val saved = save(model, draft, existing)
                    model.select(saved)
                    onDone()
                },
                enabled = draft.valid,
                shapes = ButtonDefaults.shapes(),
            ) { Text(if (existing == null) "Создать" else "Сохранить") }
            if (existing != null) {
                OutlinedButton(onClick = { model.openFolder(model.instances.gameDir(existing)) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Папка")
                }
                Spacer(Modifier.weight(1f))
                val running = model.busy && model.selected?.id == existing.id
                TextButton(
                    onClick = {
                        if (confirmDelete) {
                            model.instances.delete(existing)
                            onDone()
                        } else confirmDelete = true
                    },
                    enabled = !running,
                ) { Text(if (confirmDelete) "Точно удалить?" else "Удалить", color = scheme.error) }
            }
        }
    }
}

private fun save(model: LauncherModel, draft: Draft, existing: Instance?): Instance {
    val width = draft.width.toIntOrNull()?.takeIf { it > 0 }
    val height = draft.height.toIntOrNull()?.takeIf { it > 0 }
    val base = existing ?: model.instances.create(draft.name, draft.version, draft.icon)
    val updated = base.copy(
        name = draft.name.trim(),
        version = draft.version,
        loader = draft.loader,
        loaderVersion = if (draft.loader == Loader.Vanilla) null else draft.loaderVersion,
        icon = draft.icon,
        memoryMb = if (draft.customMemory) draft.memoryMb else null,
        jvmArgs = draft.jvmArgs.trim(),
        width = if (width != null && height != null) width else null,
        height = if (width != null && height != null) height else null,
        fullscreen = draft.fullscreen,
    )
    model.instances.save(updated)
    return updated
}

/** The loader's releases for this game version, newest first; "automatic" keeps the newest stable one. */
@Composable
private fun LoaderVersionPicker(loader: Loader, minecraft: String, selected: String?, onPick: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var versions by remember(loader, minecraft) { mutableStateOf<List<LoaderVersion>?>(null) }
    var failed by remember(loader, minecraft) { mutableStateOf(false) }
    LaunchedEffect(loader, minecraft) {
        runCatching { Loaders.versions(loader, minecraft) }.onSuccess { versions = it }.onFailure { failed = true }
    }
    var open by remember { mutableStateOf(false) }
    val list = versions
    val auto = list?.let { it.firstOrNull { v -> v.stable } ?: it.firstOrNull() }
    Box {
        Surface(
            onClick = { open = true },
            enabled = !list.isNullOrEmpty(),
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Версия ${loader.label}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text(
                        when {
                            failed -> "Не удалось получить список версий"
                            list == null -> "Загружаем…"
                            list.isEmpty() -> "${loader.label} не выпущен для Minecraft $minecraft"
                            selected != null -> selected
                            else -> "Автоматически · ${auto?.id}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (list?.isEmpty() == true || failed) scheme.error else scheme.onSurface,
                    )
                }
                if (!list.isNullOrEmpty()) Icon(Icons.Rounded.UnfoldMore, null, tint = scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp), modifier = Modifier.heightIn(max = 360.dp)) {
            DropdownMenuItem(text = { Text("Автоматически — последняя стабильная") }, onClick = { onPick(null); open = false })
            list.orEmpty().take(60).forEach { v ->
                DropdownMenuItem(
                    text = { Text(if (v.stable) "${v.id} · стабильная" else v.id) },
                    onClick = { onPick(v.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun VersionPicker(model: LauncherModel, current: String, onBack: () -> Unit, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VersionFilter.Releases) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Версия Minecraft", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Например, 1.20.1") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VersionFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        val manifest = model.manifest
        if (manifest == null) {
            val error = model.manifestError
            if (error != null) ErrorLine(error) { model.refreshVersions() }
            else Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Column
        }
        val versions = remember(manifest, filter, query) {
            manifest.versions.filter { filter.matches(it) && (query.isBlank() || query.trim() in it.id) }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(versions, key = { it.id }) { entry ->
                val picked = entry.id == current
                Surface(
                    onClick = { onPick(entry.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (picked) scheme.primaryContainer else scheme.surfaceContainer,
                    border = if (entry.id == manifest.latest.release) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            (if (entry.id == manifest.latest.release) "последний · " else "") + entry.releaseTime.take(10),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private enum class VersionFilter(val label: String) {
    Releases("Релизы"), Snapshots("Снапшоты"), Old("Старые");

    fun matches(entry: VersionEntry) = when (this) {
        Releases -> entry.type == "release"
        Snapshots -> entry.type == "snapshot"
        Old -> entry.type == "old_beta" || entry.type == "old_alpha"
    }
}

private fun typeLabel(type: String) = when (type) {
    "release" -> "Релиз"
    "snapshot" -> "Снапшот"
    "old_beta" -> "Бета"
    "old_alpha" -> "Альфа"
    else -> type
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ErrorLine(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f), maxLines = 2)
            IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Повторить", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Memory in half-gigabyte steps from 1 GB to what the machine has, minus 1 GB for the system. */
@Composable
internal fun MemorySlider(valueMb: Int, onChange: (Int) -> Unit) {
    val max = ((LauncherSettings.systemMemoryMb - 1024) / 512 * 512).coerceAtLeast(2048)
    Column {
        Row {
            Text(gigabytes(valueMb), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("из ${gigabytes(LauncherSettings.systemMemoryMb / 512 * 512)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = valueMb.coerceIn(1024, max).toFloat(),
            onValueChange = { onChange((it / 512).toInt() * 512) },
            valueRange = 1024f..max.toFloat(),
            steps = ((max - 1024) / 512 - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        placeholder = { Text("авто") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

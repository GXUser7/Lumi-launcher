package com.yumi.launcher.ui.packs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.SkippedFile
import com.yumi.launcher.core.Support
import com.yumi.launcher.core.LoaderVersion
import com.yumi.launcher.core.Loaders
import com.yumi.launcher.core.VersionEntry
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.MenuStyle
import com.yumi.launcher.data.MenuStyles
import com.yumi.launcher.settings.ThemeMode
import com.yumi.launcher.ui.settings.PalettePicker
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.FieldShape
import com.yumi.launcher.ui.components.FormSection
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.LocalFieldContainer
import com.yumi.launcher.ui.components.PackIcons
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.SearchField
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.Tip
import com.yumi.launcher.ui.components.ToggleRow
import com.yumi.launcher.ui.components.gigabytes
import com.yumi.launcher.ui.home.LauncherState
import com.yumi.launcher.ui.home.PackRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the packs screen is: the list, the modpack catalogue, one pack's editor, or its version picker. */
private sealed interface Mode {
    data object List : Mode
    data object Catalog : Mode
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
    /** The tile; its catalogue picture is kept so it can be picked again after trying a glyph. */
    val tile: TileDraft,
    memoryMb: Int?,
    jvmArgs: String,
    width: Int?,
    height: Int?,
    fullscreen: Boolean,
    menu: MenuStyle?,
    syncOptions: Boolean,
) {
    var name by mutableStateOf(name)
    var version by mutableStateOf(version)
    var loader by mutableStateOf(loader)

    /** Null: the newest stable release, picked and pinned on first launch. */
    var loaderVersion by mutableStateOf(loaderVersion)
    var customMemory by mutableStateOf(memoryMb != null)
    var memoryMb by mutableIntStateOf(memoryMb ?: 4096)
    var jvmArgs by mutableStateOf(jvmArgs)
    var width by mutableStateOf(width?.toString() ?: "")
    var height by mutableStateOf(height?.toString() ?: "")
    var fullscreen by mutableStateOf(fullscreen)

    /** Null: the game's own menus. */
    var menu by mutableStateOf(menu)
    var syncOptions by mutableStateOf(syncOptions)

    val valid get() = name.isNotBlank() && version.isNotBlank()

    companion object {
        fun of(instance: Instance) = Draft(
            instance.id, instance.name, instance.version, instance.loader, instance.loaderVersion,
            TileDraft(instance.icon, instance.iconColor, instance.iconShape, instance.iconUrl),
            instance.memoryMb, instance.jvmArgs, instance.width, instance.height, instance.fullscreen, instance.menuStyle,
            instance.syncOptions,
        )
        fun new(latest: String?, icon: Int) =
            Draft(null, "", latest ?: "", Loader.Vanilla, null, TileDraft(icon, null, null, null), null, "", null, null, false, null, true)
    }
}

@Composable
fun PacksScreen(model: LauncherModel, state: LauncherState, request: PackRequest?, onRequestHandled: () -> Unit, onPlay: (Instance) -> Unit) {
    var mode by remember { mutableStateOf<Mode>(Mode.List) }
    val nextIcon = { model.instances.all.size % PackIcons.size }
    LaunchedEffect(request) {
        when (request) {
            PackRequest.Create -> mode = Mode.Edit(Draft.new(model.manifest?.latest?.release, nextIcon()))
            PackRequest.Import -> mode = Mode.Catalog
            is PackRequest.Edit -> model.instances[request.id]?.let { mode = Mode.Edit(Draft.of(it)) }
            null -> Unit
        }
        if (request != null) onRequestHandled()
    }
    // A finished import is shown where packs are: back to the list, with the new one on top.
    LaunchedEffect(model.packImports.finished) { if (model.packImports.finished != null && mode == Mode.Catalog) mode = Mode.List }
    AnimatedContent(
        targetState = mode,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(200, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
    ) { current ->
        when (current) {
            Mode.List -> PackList(
                model,
                onCreate = { mode = Mode.Edit(Draft.new(model.manifest?.latest?.release, nextIcon())) },
                onCatalog = { mode = Mode.Catalog },
                onFile = { pickArchive()?.let { model.packImports.installFile(it, nextIcon()) } },
                onEdit = { mode = Mode.Edit(Draft.of(it)) },
                onPlay = onPlay,
            )
            Mode.Catalog -> PackCatalogScreen(model, state, onBack = { mode = Mode.List })
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

/** The system file dialog, for a modpack archive. */
internal fun pickArchive(): File? {
    val dialog = FileDialog(null as Frame?, "Сборка: .mrpack или .zip", FileDialog.LOAD).apply {
        filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".mrpack", true) || name.endsWith(".zip", true) }
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}

@Composable
private fun PackList(
    model: LauncherModel,
    onCreate: () -> Unit,
    onCatalog: () -> Unit,
    onFile: () -> Unit,
    onEdit: (Instance) -> Unit,
    onPlay: (Instance) -> Unit,
) {
    val packs = model.instances.all
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("Сборки", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Hint(
                when (packs.size) {
                    0 -> "Сборка — версия Minecraft со своей папкой: миры, настройки и моды у каждой отдельно."
                    else -> "${countOf(packs.size, "сборка", "сборки", "сборок")} · нажмите на сборку, чтобы её настроить"
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val scheme = MaterialTheme.colorScheme
            ActionTile(Icons.Rounded.Add, "Новая", "своя, с нуля", scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f), onCreate)
            ActionTile(Icons.Rounded.Explore, "Каталог", "Modrinth, CurseForge", scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f), onCatalog)
            ActionTile(Icons.Rounded.UploadFile, "Из файла", ".mrpack или .zip", scheme.secondaryContainer, scheme.onSecondaryContainer, Modifier.weight(1f), onFile)
        }
        ImportStatus(model, onPlay = onPlay, onEdit = onEdit)
        model.manifestError?.let { ErrorLine(it) { model.refreshVersions() } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(packs, key = { it.id }) { instance ->
                PackCard(model, instance, chosen = instance.id == model.selected?.id, onEdit = { onEdit(instance) }, onPlay = { onPlay(instance) })
            }
        }
    }
}

/**
 * A big tonal tile for one way to get a pack. Pressed, its corners pull in — the expressive
 * shape change M3 gives buttons, on a surface too large for a button.
 */
@Composable
private fun ActionTile(icon: ImageVector, title: String, subtitle: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 14.dp else 26.dp, spring(dampingRatio = 0.5f, stiffness = 700f))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier.height(104.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A modpack being installed, one that failed, or one that has just arrived. */
@Composable
private fun ImportStatus(model: LauncherModel, onPlay: (Instance) -> Unit, onEdit: (Instance) -> Unit) {
    val imports = model.packImports
    val scheme = MaterialTheme.colorScheme
    val title = imports.title
    val error = imports.error
    val finished = imports.finished?.let { model.instances[it.id] }
    AnimatedVisibility(title != null || error != null || finished != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        when {
            title != null -> ImportProgress(model, Modifier.fillMaxWidth())
            error != null -> Surface(shape = RoundedCornerShape(22.dp), color = scheme.errorContainer, contentColor = scheme.onErrorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { imports.error = null }) { Icon(Icons.Rounded.Close, "Скрыть") }
                }
            }
            finished != null -> Surface(shape = RoundedCornerShape(22.dp), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("«${finished.name}» установлена", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${finished.loader.label} ${finished.version} · уже выбрана на главной", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { imports.finished = null; onEdit(finished) }) { Text("Настроить") }
                        Button(onClick = { imports.finished = null; onPlay(finished) }, shapes = ButtonDefaults.shapes(), enabled = !model.busy) { Text("Играть") }
                        IconButton(onClick = { imports.finished = null }) { Icon(Icons.Rounded.Close, "Скрыть") }
                    }
                    if (imports.skipped.isNotEmpty()) SkippedFiles(model, finished, imports.skipped)
                }
            }
        }
    }
}

/**
 * The resource and shader packs an import left out: their authors let CurseForge's own site have
 * them and no launcher. The pack works without them; each has its page, and the folder it goes in
 * is a click away — dropped there, it shows up in the game's list.
 */
@Composable
private fun SkippedFiles(model: LauncherModel, pack: Instance, skipped: List<SkippedFile>) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainerLowest.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Без ${countOf(skipped.size, "файла", "файлов", "файлов")} оформления — сборка работает и без них",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Hint("Их авторы разрешили скачивание только на сайте CurseForge. Скачайте с его страницы и положите в папку, не распаковывая, — игра подхватит сама.")
            skipped.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint("${file.kind} · папка ${file.folder}")
                    }
                    if (file.page.isNotBlank()) TextButton(onClick = { Support.open(file.page) }) { Text("Страница") }
                    TextButton(onClick = { model.openFolder(model.instances.gameDir(pack).resolve(file.folder)) }) { Text("Папка") }
                }
            }
        }
    }
}

/** The running import: which pack, what step, how far, and a way to stop it. */
@Composable
internal fun ImportProgress(model: LauncherModel, modifier: Modifier = Modifier) {
    val imports = model.packImports
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Устанавливаем «${imports.title.orEmpty()}»", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Hint("${imports.stage} · ${(imports.progress * 100).toInt()}%")
                }
                TextButton(onClick = imports::cancel) { Text("Отменить") }
            }
            LinearWavyProgressIndicator(progress = { imports.progress }, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
        }
    }
}

@Composable
private fun PackCard(model: LauncherModel, instance: Instance, chosen: Boolean, onEdit: () -> Unit, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mods by produceState(-1, instance.id) {
        value = withContext(Dispatchers.IO) { model.instances.modsDir(instance).listFiles().orEmpty().count { it.name.endsWith(".jar") } }
    }
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(26.dp),
        color = if (chosen) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        border = if (chosen) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PackTile(instance, 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(instance.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (chosen) {
                        Spacer(Modifier.width(8.dp))
                        InfoChip("Выбрана", icon = Icons.Rounded.Check, container = scheme.primary, content = scheme.onPrimary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(instance.version, icon = Icons.Rounded.ViewInAr)
                    if (instance.loader != Loader.Vanilla) InfoChip(instance.loader.label, icon = Icons.Rounded.Widgets)
                    if (mods > 0) InfoChip(countOf(mods, "мод", "мода", "модов"), icon = Icons.Rounded.Extension)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Hint(played(instance))
                }
            }
            Spacer(Modifier.width(8.dp))
            Tip("Играть в «${instance.name}»") {
                if (chosen) {
                    FilledIconButton(onClick = onPlay, enabled = !model.busy, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "Играть в «${instance.name}»", Modifier.size(28.dp))
                    }
                } else {
                    FilledTonalIconButton(onClick = onPlay, enabled = !model.busy, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "Играть в «${instance.name}»", Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

private fun played(instance: Instance): String {
    val last = instance.lastPlayed ?: return "Ещё не запускалась"
    val days = (System.currentTimeMillis() - last) / 86_400_000
    val hours = instance.playSeconds / 3600
    val minutes = instance.playSeconds / 60 % 60
    val total = if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
    val ago = when (days) {
        0L -> "сегодня"
        1L -> "вчера"
        else -> "$days дн. назад"
    }
    return "Играли $ago · всего $total"
}

/** "1 сборка", "3 сборки", "12 сборок". */
internal fun countOf(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

@Composable
private fun PackEditor(model: LauncherModel, draft: Draft, onPickVersion: () -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val existing = draft.id?.let { model.instances[it] }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(if (existing == null) "Новая сборка" else draft.name.ifBlank { existing.name }, onDone)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSection("Сборка", Icons.Rounded.Inventory2) {
                Field(
                    value = draft.name,
                    onValueChange = { draft.name = it.take(40) },
                    label = "Название",
                    placeholder = "Например, «Выживание с друзьями»",
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    modifier = Modifier.fillMaxWidth(),
                )
                Label("Значок")
                TilePicker(draft.tile)
            }
            FormSection("Игра", Icons.Rounded.ViewInAr, subtitle = "Версия Minecraft и загрузчик модов") {
                VersionRow(model, draft.version, onPickVersion)
                Label("Загрузчик модов")
                ChoiceGroup(Loader.entries, draft.loader, { it.label }, {
                    if (draft.loader != it) draft.loaderVersion = null
                    draft.loader = it
                }, height = 44.dp)
                Hint(loaderHint(draft.loader))
                if (draft.loader != Loader.Vanilla && draft.version.isNotBlank()) {
                    LoaderVersionPicker(draft.loader, draft.version, draft.loaderVersion) { draft.loaderVersion = it }
                }
            }
            FormSection("Запуск", Icons.Rounded.Memory) {
                ToggleRow(
                    "Своя память",
                    draft.customMemory,
                    { draft.customMemory = it },
                    description = if (draft.customMemory) "Только для этой сборки" else "Как в настройках лаунчера: ${gigabytes(model.settings.memoryMb)}",
                )
                if (draft.customMemory) MemorySlider(draft.memoryMb) { draft.memoryMb = it }
                Field(
                    value = draft.jvmArgs,
                    onValueChange = { draft.jvmArgs = it },
                    label = "Аргументы Java",
                    placeholder = "-XX:+UseZGC",
                    icon = Icons.Rounded.Code,
                    modifier = Modifier.fillMaxWidth(),
                )
                val shared = model.settings.syncOptions
                ToggleRow(
                    "Общие настройки игры",
                    draft.syncOptions && shared,
                    { draft.syncOptions = it },
                    enabled = shared,
                    description = when {
                        !shared -> "Выключены в настройках лаунчера — у каждой сборки свои"
                        draft.syncOptions -> "Клавиши, звук и графика — как в остальных сборках"
                        else -> "У этой сборки свои клавиши, звук и графика"
                    },
                )
            }
            FormSection("Окно игры", Icons.Rounded.AspectRatio) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Ширина", draft.width, Modifier.weight(1f)) { draft.width = it }
                    NumberField("Высота", draft.height, Modifier.weight(1f)) { draft.height = it }
                }
                ToggleRow("Полный экран", draft.fullscreen, { draft.fullscreen = it }, description = "Игра откроется на весь экран")
            }
            MenuSection(model, draft)
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
                modifier = Modifier.height(48.dp),
            ) {
                Icon(if (existing == null) Icons.Rounded.Add else Icons.Rounded.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (existing == null) "Создать" else "Сохранить")
            }
            if (existing != null) {
                OutlinedButton(onClick = { model.openFolder(model.instances.gameDir(existing)) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
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
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (confirmDelete) "Точно удалить?" else "Удалить")
                }
            }
        }
    }
}

/**
 * The game's menus in the launcher's look: its palettes, light or dark, and — separately, since the
 * game has one font for everything — its typeface. Starts from the launcher's own look.
 */
@Composable
private fun MenuSection(model: LauncherModel, draft: Draft) {
    val launcherDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val supported = MenuStyles.supports(model.manifest?.versions?.firstOrNull { it.id == draft.version })
    FormSection("Меню игры", Icons.Rounded.Palette, subtitle = "Главное меню и настройки Minecraft") {
        ToggleRow(
            "Оформление Lumi",
            draft.menu != null && supported,
            { on -> draft.menu = if (on) MenuStyle(model.settings.palette, launcherDark) else null },
            description = if (supported) "Кнопки, ползунки, фоны и своя панорама — набор ресурсов, Sodium ему не мешает" else "Нужна версия 1.20.2 или новее",
            enabled = supported,
        )
        val menu = draft.menu?.takeIf { supported } ?: return@FormSection
        Label("Цвета")
        PalettePicker(menu.palette, dark = menu.dark) { draft.menu = menu.copy(palette = it) }
        Label("Тема")
        val theme = if (menu.dark) ThemeMode.Dark else ThemeMode.Light
        ChoiceGroup(listOf(ThemeMode.Light, ThemeMode.Dark), theme, { it.label }, { draft.menu = menu.copy(dark = it == ThemeMode.Dark) }, height = 44.dp) { mode ->
            Icon(mode.icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        ToggleRow(
            "Шрифт лаунчера",
            menu.font,
            { draft.menu = menu.copy(font = it) },
            description = "Roboto Flex вместо пиксельного — во всей игре, в чате и на табличках тоже",
        )
    }
}

private fun loaderHint(loader: Loader) = when (loader) {
    Loader.Vanilla -> "Чистая игра без модов."
    Loader.Fabric -> "Лёгкий и быстрый: Sodium, Iris и большинство новых модов."
    Loader.Quilt -> "Ответвление Fabric, запускает и его моды."
    Loader.Forge -> "Классика больших сборок — от 1.7.10 до 1.20.1."
    Loader.NeoForge -> "Преемник Forge: крупные моды на 1.20.2 и новее."
}

/** The pack's version as a field: tapping it opens the full list. */
@Composable
private fun VersionRow(model: LauncherModel, version: String, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val entry = model.manifest?.versions?.firstOrNull { it.id == version }
    Surface(onClick = onPick, shape = FieldShape, color = LocalFieldContainer.current ?: scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeTile(Icons.Rounded.ViewInAr, scheme.primaryContainer, scheme.onPrimaryContainer, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Версия Minecraft", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(version.ifEmpty { "Выберите версию" }, style = MaterialTheme.typography.titleMedium)
            }
            if (entry != null) {
                InfoChip(typeLabel(entry.type) + " · " + shortDate(entry.releaseTime))
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}

private fun save(model: LauncherModel, draft: Draft, existing: Instance?): Instance {
    val width = draft.width.toIntOrNull()?.takeIf { it > 0 }
    val height = draft.height.toIntOrNull()?.takeIf { it > 0 }
    val base = existing ?: model.instances.create(draft.name, draft.version, draft.tile.icon)
    val updated = base.copy(
        name = draft.name.trim(),
        version = draft.version,
        loader = draft.loader,
        loaderVersion = if (draft.loader == Loader.Vanilla) null else draft.loaderVersion,
        icon = draft.tile.icon,
        iconColor = draft.tile.color,
        iconShape = draft.tile.shape,
        iconUrl = if (draft.tile.usePicture) draft.tile.picture else null,
        memoryMb = if (draft.customMemory) draft.memoryMb else null,
        jvmArgs = draft.jvmArgs.trim(),
        width = if (width != null && height != null) width else null,
        height = if (width != null && height != null) height else null,
        fullscreen = draft.fullscreen,
        menuStyle = draft.menu,
        syncOptions = draft.syncOptions,
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
            shape = FieldShape,
            color = LocalFieldContainer.current ?: scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                if (list == null && !failed) LoadingIndicator(Modifier.size(24.dp))
                if (!list.isNullOrEmpty()) Icon(Icons.Rounded.UnfoldMore, null, tint = scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp), modifier = Modifier.heightIn(max = 360.dp)) {
            DropdownMenuItem(
                text = { Text("Автоматически — последняя стабильная") },
                trailingIcon = { if (selected == null) Icon(Icons.Rounded.Check, null, tint = scheme.primary) },
                onClick = { onPick(null); open = false },
            )
            list.orEmpty().take(60).forEach { v ->
                DropdownMenuItem(
                    text = { Text(if (v.stable) "${v.id} · стабильная" else v.id) },
                    trailingIcon = { if (selected == v.id) Icon(Icons.Rounded.Check, null, tint = scheme.primary) },
                    onClick = { onPick(v.id); open = false },
                )
            }
        }
    }
}

/** Versions modpacks are made for most; offered first, a click away. */
internal val PopularVersions = listOf("1.21.1", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2", "1.7.10")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionPicker(model: LauncherModel, current: String, onBack: () -> Unit, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VersionFilter.entries.firstOrNull { f -> model.manifest?.versions?.firstOrNull { it.id == current }?.let(f::matches) == true } ?: VersionFilter.Releases) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Версия Minecraft", onBack)
        SearchField(query, { query = it }, "Например, 1.20.1", Modifier.fillMaxWidth())
        ChoiceGroup(VersionFilter.entries, filter, { it.label }, { filter = it }, height = 44.dp)
        val manifest = model.manifest
        if (manifest == null) {
            val error = model.manifestError
            if (error != null) ErrorLine(error) { model.refreshVersions() }
            else Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Column
        }
        if (filter == VersionFilter.Releases && query.isBlank()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Для модов:", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                PopularVersions.filter { v -> manifest.versions.any { it.id == v } }.forEach { v ->
                    Surface(
                        onClick = { onPick(v) },
                        shape = CircleShape,
                        color = if (v == current) scheme.primary else scheme.surfaceContainerHigh,
                        contentColor = if (v == current) scheme.onPrimary else scheme.onSurface,
                    ) {
                        Text(v, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
        val groups = remember(manifest, filter, query) {
            manifest.versions
                .filter { filter.matches(it) && (query.isBlank() || query.trim() in it.id) }
                .groupBy { filter.group(it) }
        }
        if (groups.isEmpty()) {
            Hint("Ничего не нашлось${if (query.isNotBlank()) " по «${query.trim()}»" else ""}.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            groups.forEach { (group, versions) ->
                stickyHeader(key = "group:$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.primary,
                        modifier = Modifier.fillMaxWidth().background(scheme.surface).padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(versions, key = { it.id }) { entry ->
                    val picked = entry.id == current
                    val latest = entry.id == manifest.latest.release || entry.id == manifest.latest.snapshot
                    Surface(
                        onClick = { onPick(entry.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (picked) scheme.primaryContainer else scheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (latest) {
                                InfoChip("Последняя", container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(shortDate(entry.releaseTime), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            if (picked) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.Check, "Выбрана", Modifier.size(20.dp), tint = scheme.primary)
                            }
                        }
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

    /** Releases by line — 1.21 with its updates; snapshots by year; old builds by beta and alpha. */
    fun group(entry: VersionEntry): String = when (this) {
        Releases -> if (entry.id.startsWith("1.")) "Minecraft " + entry.id.split('.').take(2).joinToString(".") else "Minecraft " + entry.id.substringBefore('.')
        Snapshots -> entry.releaseTime.take(4)
        Old -> typeLabel(entry.type)
    }
}

private fun typeLabel(type: String) = when (type) {
    "release" -> "Релиз"
    "snapshot" -> "Снапшот"
    "old_beta" -> "Бета"
    "old_alpha" -> "Альфа"
    else -> type
}

/** "2024-06-13T…" → "13 июн. 2024". */
internal fun shortDate(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru")))
}.getOrDefault(iso.take(10))

@Composable
internal fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun ErrorLine(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f), maxLines = 2)
            IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Повторить", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
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
    Field(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = label,
        placeholder = "авто",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

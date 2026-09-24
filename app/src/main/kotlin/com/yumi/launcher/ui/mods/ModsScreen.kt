package com.yumi.launcher.ui.mods

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.ModDetails
import com.yumi.launcher.core.ModHit
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.data.Instance
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ModEntry
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.RemoteIcon
import com.yumi.launcher.ui.components.RemoteImage
import com.yumi.launcher.ui.components.SearchField
import com.yumi.launcher.ui.components.choiceColors
import com.yumi.launcher.ui.components.compactCount
import com.yumi.launcher.ui.components.connectedShapes
import java.awt.Desktop
import java.net.URI

private enum class Tab(val label: String) { Catalog("Каталог"), Installed("Установлено") }

/**
 * Mods for one pack: the Modrinth or CurseForge catalogue, filtered to the pack's game version and
 * loader, and the pack's own mods folder. A vanilla pack gets pointed at its settings instead —
 * mods need a loader, and the choice of one belongs to the pack.
 */
@Composable
fun ModsScreen(model: LauncherModel, onEditPack: (Instance) -> Unit) {
    val mods = model.mods
    var packId by remember { mutableStateOf(model.selected?.id) }
    val pack = model.instances[packId] ?: model.selected
    var tab by remember { mutableStateOf(Tab.Catalog) }
    var selectedHit by remember { mutableStateOf<ModHit?>(null) }
    val catalogList = rememberLazyListState()

    LaunchedEffect(pack?.id, pack?.loader, pack?.version) {
        selectedHit = null
        if (pack == null) return@LaunchedEffect
        mods.refresh(pack)
        if (pack.loader != Loader.Vanilla) mods.search(pack)
    }
    LaunchedEffect(mods.query, mods.source, pack?.id) { catalogList.scrollToItem(0) }

    AnimatedContent(
        targetState = selectedHit?.takeIf { pack != null },
        contentKey = { it?.let { hit -> "${hit.source}:${hit.id}" } },
        transitionSpec = { fadeIn(tween(200, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
    ) { hit ->
        if (hit != null && pack != null) ModDetail(model, pack, hit, onBack = { selectedHit = null })
        else ModsList(model, pack, tab, { tab = it }, catalogList, onPickPack = { packId = it.id }, onEditPack = onEditPack, onOpen = { selectedHit = it })
    }
}

@Composable
private fun ModsList(
    model: LauncherModel,
    pack: Instance?,
    tab: Tab,
    onTab: (Tab) -> Unit,
    catalogList: LazyListState,
    onPickPack: (Instance) -> Unit,
    onEditPack: (Instance) -> Unit,
    onOpen: (ModHit) -> Unit,
) {
    val mods = model.mods
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Моды", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            PackPicker(model, pack, onPickPack)
        }
        when {
            pack == null -> Hint("Сначала создайте сборку — моды ставятся в неё.")
            pack.loader == Loader.Vanilla -> NeedsLoader(pack) { onEditPack(pack) }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    Tab.entries.forEachIndexed { i, t ->
                        ToggleButton(
                            checked = tab == t,
                            onCheckedChange = { onTab(t) },
                            shapes = connectedShapes(i, Tab.entries.size),
                            colors = choiceColors(),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) { Text(if (t == Tab.Installed) "${t.label} · ${mods.installed.size}" else t.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
                mods.notice?.let { Notice(it) { mods.notice = null } }
                if (tab == Tab.Catalog) Catalog(model, pack, catalogList, onOpen) else Installed(model, pack)
            }
        }
    }
}

@Composable
private fun PackPicker(model: LauncherModel, pack: Instance?, onPick: (Instance) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pack != null) PackTile(pack, 32.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(pack?.name ?: "Нет сборок", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
                    if (pack != null) Text("${pack.loader.label} ${pack.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.UnfoldMore, "Выбрать сборку", Modifier.size(20.dp))
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            model.instances.all.forEach { instance ->
                DropdownMenuItem(
                    text = { Text("${instance.name} · ${instance.loader.label} ${instance.version}") },
                    leadingIcon = { PackTile(instance, 32.dp) },
                    onClick = { onPick(instance); open = false },
                )
            }
        }
    }
}

@Composable
private fun NeedsLoader(pack: Instance, onEdit: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Нужен загрузчик модов", style = MaterialTheme.typography.titleLarge)
            Hint("«${pack.name}» — ванильная сборка. Выберите для неё Fabric, NeoForge, Forge или Quilt: моды пишутся под конкретный загрузчик, и каталог покажет подходящие.")
            Button(onClick = onEdit, shapes = ButtonDefaults.shapes()) { Text("Выбрать загрузчик") }
        }
    }
}

@Composable
private fun Catalog(model: LauncherModel, pack: Instance, listState: LazyListState, onOpen: (ModHit) -> Unit) {
    val mods = model.mods
    val settings = model.settings
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(
            value = mods.query,
            onValueChange = { mods.query = it; mods.search(pack, debounce = true) },
            placeholder = "Поиск: sodium, jei, create…",
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ModSource.entries.forEachIndexed { i, source ->
                ToggleButton(
                    checked = mods.source == source,
                    onCheckedChange = { mods.source = source; mods.search(pack) },
                    shapes = connectedShapes(i, ModSource.entries.size),
                    colors = choiceColors(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(56.dp),
                ) { Text(source.label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
    if (mods.source == ModSource.CurseForge && settings.curseForgeKey.isBlank()) {
        CurseForgeKey(model, pack)
        return
    }
    mods.searchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mods.results, key = { "${it.source}:${it.id}" }) { hit ->
            HitRow(
                hit,
                installing = mods.installing["${hit.source}:${hit.id}"] == true,
                installed = mods.isInstalled(hit),
                onOpen = { onOpen(hit) },
                onInstall = { mods.install(pack, hit) },
            )
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                when {
                    mods.searching -> LoadingIndicator()
                    mods.results.isEmpty() && mods.searchError == null -> Hint("Ничего не нашлось для ${pack.loader.label} ${pack.version}.")
                    mods.more -> TextButton(onClick = { mods.search(pack, append = true) }) { Text("Показать ещё") }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: ModHit, installing: Boolean, installed: Boolean, onOpen: () -> Unit, onInstall: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onOpen, shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteIcon(hit.iconUrl, Icons.Rounded.Extension, 52.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (hit.author.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(hit.author, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Text(hit.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("↓ ${compactCount(hit.downloads)}", style = MaterialTheme.typography.labelMedium, color = scheme.outline)
            }
            when {
                installing -> LoadingIndicator(Modifier.size(40.dp))
                installed -> Icon(Icons.Rounded.Check, "Установлен", tint = scheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
                else -> FilledTonalButton(onClick = onInstall, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Установить")
                }
            }
        }
    }
}

/**
 * One mod's page: what it is and who made it, installing it without going back to the list, then
 * its pictures, tags, where it is needed, and the author's own description.
 */
@Composable
private fun ModDetail(model: LauncherModel, pack: Instance, hit: ModHit, onBack: () -> Unit) {
    val mods = model.mods
    val scheme = MaterialTheme.colorScheme
    val installing = mods.installing["${hit.source}:${hit.id}"] == true
    val installed = mods.isInstalled(hit)
    val details by produceState<ModDetails?>(null, hit.source, hit.id) { value = mods.details(hit) }
    var loaded by remember(hit.id) { mutableStateOf(false) }
    LaunchedEffect(details) { if (details != null) loaded = true }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад к каталогу") }
            Spacer(Modifier.width(4.dp))
            Text(hit.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteIcon(hit.iconUrl, Icons.Rounded.Extension, 76.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                InfoChip(hit.source.label, icon = Icons.Rounded.Extension, container = scheme.primaryContainer, content = scheme.onPrimaryContainer)
                                InfoChip(compactCount(hit.downloads), icon = Icons.Rounded.Download)
                                if (hit.author.isNotBlank()) InfoChip(hit.author, icon = Icons.Rounded.Person)
                            }
                            Text(hit.description.ifBlank { "Автор не добавил описание." }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            installed -> FilledTonalButton(onClick = {}, enabled = false, shapes = ButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Установлен в «${pack.name}»", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            else -> Button(onClick = { mods.install(pack, hit) }, enabled = !installing, shapes = ButtonDefaults.shapes()) {
                                if (installing) LoadingIndicator(Modifier.size(20.dp), color = scheme.onPrimary)
                                else Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (installing) "Устанавливаем…" else "Установить в «${pack.name}»", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(hit.pageUrl)) } }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Страница")
                        }
                    }
                    Hint("Для ${pack.loader.label} ${pack.version}. Нужные ему моды поставятся сами.")
                }
            }
            val info = details
            when {
                info == null && !loaded -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                info == null -> Unit
                else -> {
                    if (info.gallery.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            info.gallery.take(8).forEach { url -> RemoteImage(url, Modifier.size(width = 236.dp, height = 133.dp)) }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sideChip("Клиент", info.client)?.let { InfoChip(it, icon = Icons.Rounded.Computer) }
                        sideChip("Сервер", info.server)?.let { InfoChip(it, icon = Icons.Rounded.Dns) }
                        info.categories.mapNotNull(::categoryLabel).distinct().forEach { InfoChip(it, icon = Icons.Rounded.Sell) }
                        if (info.updated.isNotBlank()) InfoChip("Обновлён ${info.updated.split('-').reversed().joinToString(".")}", icon = Icons.Rounded.Update)
                        info.license?.let { InfoChip(it, icon = Icons.Rounded.Gavel) }
                    }
                    if (info.body.isNotBlank()) Description(info.body)
                }
            }
        }
    }
}

/** The author's long description, folded to its start until asked for the rest. */
@Composable
private fun Description(text: String) {
    val scheme = MaterialTheme.colorScheme
    var open by remember(text) { mutableStateOf(false) }
    val long = text.length > 900
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Описание", style = MaterialTheme.typography.titleMedium)
            Text(
                if (long && !open) text.take(900).substringBeforeLast(' ') + "…" else text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (long) TextButton(onClick = { open = !open }) { Text(if (open) "Свернуть" else "Показать полностью") }
        }
    }
}

/** "Клиент: нужен" — only when the catalogue says; "unknown" says nothing. */
private fun sideChip(side: String, value: String?): String? = when (value) {
    "required" -> "$side: нужен"
    "optional" -> "$side: по желанию"
    "unsupported" -> "$side: не нужен"
    else -> null
}

/** Modrinth's category slugs in Russian; loaders are left out — the pack already has one. */
private fun categoryLabel(slug: String): String? = when (slug.lowercase()) {
    "fabric", "forge", "neoforge", "quilt", "liteloader", "modloader", "rift", "datapack", "minecraft" -> null
    "adventure" -> "Приключения"
    "cursed" -> "Странное"
    "decoration" -> "Декор"
    "economy" -> "Экономика"
    "equipment" -> "Снаряжение"
    "food" -> "Еда"
    "game-mechanics" -> "Механики"
    "library" -> "Библиотека"
    "magic" -> "Магия"
    "management" -> "Управление"
    "minigame" -> "Мини-игры"
    "mobs" -> "Мобы"
    "optimization" -> "Оптимизация"
    "social" -> "Общение"
    "storage" -> "Хранение"
    "technology" -> "Технологии"
    "transportation" -> "Транспорт"
    "utility" -> "Утилиты"
    "worldgen" -> "Генерация мира"
    else -> slug.replaceFirstChar { it.uppercase() }
}

@Composable
private fun Installed(model: LauncherModel, pack: Instance) {
    val mods = model.mods
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint(
            if (mods.installed.isEmpty()) "В сборке пока нет модов." else "Выключенный мод остаётся в папке, но игра его не загрузит.",
            Modifier.weight(1f),
        )
        OutlinedButton(onClick = { model.openFolder(model.instances.modsDir(pack)) }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Папка модов")
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mods.installed, key = { it.file.name }) { entry ->
            InstalledRow(entry, onToggle = { mods.setEnabled(pack, entry, it) }, onDelete = { mods.delete(pack, entry) })
        }
    }
}

@Composable
private fun InstalledRow(entry: ModEntry, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteIcon(entry.meta?.iconUrl, Icons.Rounded.Extension, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (entry.enabled) scheme.onSurface else scheme.outline)
                Text(
                    listOfNotNull(entry.meta?.version?.takeIf { it.isNotBlank() }, entry.meta?.source?.label ?: "добавлен вручную").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(checked = entry.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Удалить «${entry.title}»", tint = scheme.onSurfaceVariant) }
        }
    }
}

/** Where to put a CurseForge key, right where it is missed. */
@Composable
private fun CurseForgeKey(model: LauncherModel, pack: Instance) {
    var key by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Нужен ключ CurseForge", style = MaterialTheme.typography.titleLarge)
            Hint("CurseForge пускает лаунчеры только с личным ключом. Он бесплатный: войдите на console.curseforge.com, раздел API Keys, скопируйте ключ и вставьте сюда. Modrinth работает без ключа.")
            Field(
                value = key,
                onValueChange = { key = it.trim() },
                label = "Ключ API",
                icon = Icons.Rounded.Key,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { model.settings.curseForgeKey = key; model.mods.search(pack) },
                enabled = key.length > 20,
                shapes = ButtonDefaults.shapes(),
            ) { Text("Сохранить ключ") }
        }
    }
}

@Composable
private fun Notice(text: String, onClose: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Скрыть", tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

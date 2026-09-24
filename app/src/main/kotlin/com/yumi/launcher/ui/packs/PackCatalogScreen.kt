package com.yumi.launcher.ui.packs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.PackCatalog
import com.yumi.launcher.core.PackHit
import com.yumi.launcher.core.PackRelease
import com.yumi.launcher.core.ReleaseType
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.ui.components.CurseForgeKeyPrompt
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.PackIcons
import com.yumi.launcher.ui.components.RemoteIcon
import com.yumi.launcher.ui.components.SearchField
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.choiceColors
import com.yumi.launcher.ui.components.compactCount
import com.yumi.launcher.ui.components.connectedShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI

/**
 * Ready-made modpacks: search Modrinth or CurseForge by name, Minecraft version and loader, open a
 * pack, pick the game version and the release, install. The install itself is the model's
 * [com.yumi.launcher.data.PackImporter], so it carries on when this screen is left.
 */
@Composable
internal fun PackCatalogScreen(model: LauncherModel, onBack: () -> Unit) {
    var source by remember { mutableStateOf(ModSource.Modrinth) }
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf<String?>(null) }
    var loader by remember { mutableStateOf<Loader?>(null) }
    var hits by remember { mutableStateOf<List<PackHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<PackHit?>(null) }
    val list = rememberLazyListState()
    val key = model.settings.curseForgeKey.trim()

    LaunchedEffect(source, query, version, loader, key) {
        error = null
        if (source == ModSource.CurseForge && key.isBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        try {
            hits = PackCatalog.search(source, query.trim(), version.orEmpty(), loader, key)
            list.scrollToItem(0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось найти сборки: ${e.message}"
        } finally {
            searching = false
        }
    }

    AnimatedContent(
        targetState = chosen,
        contentKey = { it?.id },
        transitionSpec = { fadeIn(tween(200, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
    ) { hit ->
        if (hit != null) {
            PackDetail(model, source, hit, preferred = version, onBack = { chosen = null })
            return@AnimatedContent
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header("Каталог сборок", onBack) {
                TextButton(onClick = { pickArchive()?.let { model.packImports.installFile(it, model.instances.all.size % PackIcons.size) } }, enabled = !model.packImports.busy) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Из файла")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, "Поиск: create, cobblemon, optimized…", Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ModSource.entries.forEachIndexed { i, item ->
                        ToggleButton(
                            checked = source == item,
                            onCheckedChange = { source = item },
                            shapes = connectedShapes(i, ModSource.entries.size),
                            colors = choiceColors(),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(56.dp),
                        ) { Text(item.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameVersionFilter(model, version) { version = it }
                LoaderFilter(loader) { loader = it }
                Spacer(Modifier.weight(1f))
                if (searching) LoadingIndicator(Modifier.size(32.dp))
            }
            AnimatedVisibility(model.packImports.busy, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                ImportProgress(model, Modifier.fillMaxWidth())
            }
            error?.let { ErrorLine(it) { query = query.trim() + " " } }
            when {
                source == ModSource.CurseForge && key.isBlank() -> CurseForgeKeyPrompt { model.settings.curseForgeKey = it }
                hits.isEmpty() && !searching && error == null -> NothingFound(version, loader)
                else -> LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hits, key = { it.id }) { hit -> HitCard(hit) { chosen = hit } }
                }
            }
        }
    }
}

/**
 * The Minecraft version to search for, as a filter chip with a menu: the versions packs are made
 * for most at the top, then every release in Mojang's list.
 */
@Composable
private fun GameVersionFilter(model: LauncherModel, version: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val releases = remember(model.manifest) { model.manifest?.versions.orEmpty().filter { it.type == "release" }.map { it.id } }
    Box {
        FilterChip(
            selected = version != null,
            onClick = { open = true },
            label = { Text(version?.let { "Minecraft $it" } ?: "Любая версия") },
            leadingIcon = { Icon(if (version != null) Icons.Rounded.Check else Icons.Rounded.ViewInAr, null, Modifier.size(FilterChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) },
            shape = CircleShape,
        )
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp), modifier = Modifier.heightIn(max = 420.dp)) {
            DropdownMenuItem(
                text = { Text("Любая версия") },
                trailingIcon = { if (version == null) Icon(Icons.Rounded.Check, null, tint = scheme.primary) },
                onClick = { onPick(null); open = false },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Популярные для модов", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            PopularVersions.forEach { v -> VersionItem(v, v == version) { onPick(v); open = false } }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Все релизы", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            releases.filter { it !in PopularVersions }.forEach { v -> VersionItem(v, v == version) { onPick(v); open = false } }
        }
    }
}

@Composable
private fun VersionItem(version: String, picked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(version) },
        trailingIcon = { if (picked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick,
    )
}

@Composable
private fun LoaderFilter(loader: Loader?, onPick: (Loader?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        FilterChip(
            selected = loader != null,
            onClick = { open = true },
            label = { Text(loader?.label ?: "Любой загрузчик") },
            leadingIcon = { Icon(if (loader != null) Icons.Rounded.Check else Icons.Rounded.Widgets, null, Modifier.size(FilterChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) },
            shape = CircleShape,
        )
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            (listOf(null) + Loader.entries.filter { it != Loader.Vanilla }).forEach { item ->
                DropdownMenuItem(
                    text = { Text(item?.label ?: "Любой загрузчик") },
                    trailingIcon = { if (item == loader) Icon(Icons.Rounded.Check, null, tint = scheme.primary) },
                    onClick = { onPick(item); open = false },
                )
            }
        }
    }
}

@Composable
private fun HitCard(hit: PackHit, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteIcon(hit.icon, Icons.Rounded.Inventory2, 60.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(hit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (hit.author.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(hit.author, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Text(hit.summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(compactCount(hit.downloads), icon = Icons.Rounded.Download)
                    hit.versions.firstOrNull()?.let { newest ->
                        InfoChip(if (hit.versions.size > 1) "${hit.versions.last()} – $newest" else newest, icon = Icons.Rounded.ViewInAr)
                    }
                    hit.loaders.take(2).forEach { InfoChip(it.label, icon = Icons.Rounded.Widgets) }
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NothingFound(version: String?, loader: Loader?) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShapeTile(Icons.Rounded.SearchOff, scheme.secondaryContainer, scheme.onSecondaryContainer, 64.dp)
        Text("Ничего не нашлось", style = MaterialTheme.typography.titleMedium)
        Hint(if (version != null || loader != null) "Попробуйте другую версию или любой загрузчик." else "Попробуйте другое название.")
    }
}

/**
 * One modpack: what it is, then its releases. The chips above the list pick the Minecraft version —
 * every version the pack was ever built for — and the newest release for it is marked as the one to
 * take; betas and alphas are labelled as such.
 */
@Composable
private fun PackDetail(model: LauncherModel, source: ModSource, hit: PackHit, preferred: String?, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val key = model.settings.curseForgeKey.trim()
    val imports = model.packImports
    val result by produceState<Result<List<PackRelease>>?>(null, source, hit.id) {
        value = runCatching { PackCatalog.releases(source, hit.id, key) }
    }
    val releases = result?.getOrNull().orEmpty()
    val versions = remember(releases) { releases.flatMap { it.gameVersions }.distinct().sortedWith(PackCatalog.VersionOrder).reversed() }
    var version by remember(hit.id, versions) { mutableStateOf(preferred?.takeIf { it in versions } ?: versions.firstOrNull()) }
    val shown = remember(releases, version) { releases.filter { version == null || version in it.gameVersions } }
    val recommended = shown.firstOrNull { it.type == ReleaseType.Release } ?: shown.firstOrNull()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад к каталогу") }
            Spacer(Modifier.width(4.dp))
            Text(hit.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteIcon(hit.icon, Icons.Rounded.Inventory2, 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoChip(source.label, icon = Icons.Rounded.Inventory2, container = scheme.primaryContainer, content = scheme.onPrimaryContainer)
                            InfoChip(compactCount(hit.downloads), icon = Icons.Rounded.Download)
                            if (hit.author.isNotBlank()) InfoChip(hit.author, icon = Icons.Rounded.Person)
                        }
                        Text(hit.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (hit.pageUrl.isNotBlank()) {
                    TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(hit.pageUrl)) } }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Страница сборки на ${source.label}")
                    }
                }
            }
        }
        AnimatedVisibility(imports.busy, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            ImportProgress(model, Modifier.fillMaxWidth())
        }
        imports.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
        when {
            result == null -> Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            result?.isFailure == true -> ErrorLine("Не удалось загрузить версии: ${result?.exceptionOrNull()?.message}") { onBack() }
            releases.isEmpty() -> Hint("У сборки нет файлов, которые можно скачать через лаунчер — только со страницы сборки.")
            else -> {
                Label("Версия Minecraft")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    versions.forEach { v ->
                        FilterChip(
                            selected = v == version,
                            onClick = { version = v },
                            label = { Text(v) },
                            leadingIcon = if (v == version) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                            shape = CircleShape,
                        )
                    }
                    FilterChip(selected = version == null, onClick = { version = null }, label = { Text("Все") }, shape = CircleShape)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Версии сборки", Modifier.weight(1f))
                    Hint(countOf(shown.size, "файл", "файла", "файлов"))
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { release ->
                        ReleaseRow(
                            release,
                            recommended = release == recommended,
                            enabled = !imports.busy,
                            onInstall = { imports.install(source, hit, release, model.instances.all.size % PackIcons.size) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseRow(release: PackRelease, recommended: Boolean, enabled: Boolean, onInstall: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (recommended) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        border = if (recommended) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(release.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        InfoChip("Рекомендуем", icon = Icons.Rounded.Star, container = scheme.primary, content = scheme.onPrimary)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val games = release.gameVersions
                    if (games.isNotEmpty()) InfoChip(if (games.size > 2) "${games.first()} +${games.size - 1}" else games.joinToString(", "), icon = Icons.Rounded.ViewInAr)
                    release.loaders.forEach { InfoChip(it.label, icon = Icons.Rounded.Widgets) }
                    if (release.type != ReleaseType.Release) {
                        InfoChip(
                            release.type.label,
                            container = if (release.type == ReleaseType.Alpha) scheme.errorContainer else scheme.tertiaryContainer,
                            content = if (release.type == ReleaseType.Alpha) scheme.onErrorContainer else scheme.onTertiaryContainer,
                        )
                    }
                    if ((release.serverId ?: 0) > 0) InfoChip("есть сервер", icon = Icons.Rounded.Dns)
                }
                Hint(listOfNotNull(shortDate(release.date), release.size?.let(::megabytes), release.downloads.takeIf { it > 0 }?.let { "↓ ${compactCount(it)}" }).joinToString(" · "))
            }
            Spacer(Modifier.width(10.dp))
            if (recommended) {
                Button(onClick = onInstall, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Установить")
                }
            } else {
                FilledTonalButton(onClick = onInstall, enabled = enabled, shapes = ButtonDefaults.shapes()) { Text("Установить") }
            }
        }
    }
}

private fun megabytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 10) "${mb.toInt()} МБ" else "%.1f МБ".format(mb).replace('.', ',')
}

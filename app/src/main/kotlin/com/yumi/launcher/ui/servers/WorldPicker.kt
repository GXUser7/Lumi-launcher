package com.yumi.launcher.ui.servers

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yumi.launcher.ui.packs.countOf
import com.yumi.launcher.core.SavedWorld
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.InfoChip
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.ModalCard
import com.yumi.launcher.ui.components.SearchField
import com.yumi.launcher.ui.components.LocalFieldContainer
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.theme.glassContainer
import com.yumi.launcher.ui.theme.glassInnerBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The world the new server starts with, laid out like the game's own world list: each saved world
 * as a card with its icon, its name, and under it the folder and when it was last played — so two
 * worlds of the same name, which the game allows, are told apart the way the game tells them — and
 * its mode, cheats and version. The form keeps to a new world and the few played last; however many
 * there are, the rest are in [AllWorlds], a window with a search, a click away from the title.
 */
@Composable
internal fun WorldPicker(worlds: List<SavedWorld>, selected: String?, enabled: Boolean, onPick: (String?) -> Unit) {
    var browsing by remember { mutableStateOf(false) }
    val recent = worlds.take(RECENT)
    val shown = recent + worlds.filter { it.folder == selected && it !in recent }
    // Only folders tell worlds apart; a name that repeats gets its folder picked out.
    val repeated = remember(worlds) { worlds.groupingBy { it.name.lowercase() }.eachCount().filterValues { it > 1 }.keys }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Мир", Modifier.weight(1f))
            if (worlds.size > RECENT) {
                TextButton(onClick = { browsing = true }, enabled = enabled) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                    Text("  Все миры · ${worlds.size}")
                }
            }
        }
        Choice(selected == null, enabled, onClick = { onPick(null) }) {
            ShapeTile(Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Новый мир", style = MaterialTheme.typography.titleSmall)
                Text("Сгенерируется при первом запуске сервера", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        shown.forEach { world -> WorldCard(world, world.folder == selected, world.name.lowercase() in repeated, enabled) { onPick(world.folder) } }
    }
    if (browsing) AllWorlds(worlds, selected, repeated, onPick = { onPick(it); browsing = false }, onClose = { browsing = false })
}

/**
 * Every world of the pack, over the whole window: searched by name or folder, the latest first or
 * by name. The list is lazy — with hundreds of worlds only the cards on screen exist, and only
 * their icons are read.
 */
@Composable
private fun AllWorlds(worlds: List<SavedWorld>, selected: String?, repeated: Set<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var byName by remember { mutableStateOf(false) }
    val found = remember(worlds, query, byName) {
        val q = query.trim().lowercase()
        worlds.filter { q.isEmpty() || q in it.name.lowercase() || q in it.folder.lowercase() }
            .let { list -> if (byName) list.sortedWith(compareBy({ it.name.lowercase() }, { it.folder.lowercase() })) else list }
    }
    // A popup, so the window is dimmed whole, not only the form it was opened from.
    Popup(onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        ModalCard(width = 560.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Миры сборки", style = MaterialTheme.typography.headlineSmall)
                    Hint(if (found.size == worlds.size) countOf(worlds.size, "мир", "мира", "миров") else "Найдено ${found.size} из ${worlds.size}")
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть") }
            }
            SearchField(query, { query = it }, "Название или папка мира", Modifier.fillMaxWidth())
            ChoiceGroup(listOf(false, true), byName, { if (it) "По имени" else "Недавние" }, { byName = it }, height = 40.dp)
            if (found.isEmpty()) {
                Hint("Ничего не нашлось — попробуйте часть названия или имени папки.", Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found, key = { it.folder }) { world ->
                        WorldCard(world, world.folder == selected, world.name.lowercase() in repeated, enabled = true) { onPick(world.folder) }
                    }
                }
            }
        }
    }
}

/** A saved world's card: icon, name, folder and date, then mode, cheats and version. */
@Composable
private fun WorldCard(world: SavedWorld, picked: Boolean, twin: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Choice(picked, enabled, onClick = onClick) {
        WorldIcon(world.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val scheme = MaterialTheme.colorScheme
            Text(world.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(14.dp), tint = if (twin) scheme.primary else scheme.outline)
                Text(
                    " ${world.folder} · ${played(world.lastPlayed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (twin) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (world.hardcore) InfoChip("Хардкор", icon = Icons.Rounded.Favorite, container = scheme.errorContainer, content = scheme.onErrorContainer)
                else modeLabel(world.mode)?.let { InfoChip(it, icon = Icons.Rounded.SportsEsports) }
                if (world.cheats) InfoChip("Читы", icon = Icons.Rounded.Code)
                world.version?.let { InfoChip(it, icon = Icons.Rounded.ViewInAr) }
            }
        }
    }
}

/** One card of the list: a quiet tone, and the accent's outline and a tick once picked. */
@Composable
private fun Choice(picked: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tone by animateColorAsState(if (picked) scheme.secondaryContainer else LocalFieldContainer.current ?: glassContainer(elevated = true), tween(160))
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = tone,
        border = if (picked) BorderStroke(2.dp, scheme.primary) else glassInnerBorder(),
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.RadioButton
            selected = picked
        },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            if (picked) Icon(Icons.Rounded.CheckCircle, "Выбран", tint = scheme.primary)
        }
    }
}

/** The world's `icon.png` — the picture the game took of it — or a globe until there is one. */
@Composable
private fun WorldIcon(file: File?) {
    val scheme = MaterialTheme.colorScheme
    var image by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        image = file?.let { withContext(Dispatchers.IO) { runCatching { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }.getOrNull() } }
    }
    val bitmap = image
    if (bitmap == null) ShapeTile(Icons.Rounded.Public, scheme.primaryContainer, scheme.onPrimaryContainer, 52.dp)
    else Image(bitmap, null, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
}

private fun modeLabel(mode: Int?) = when (mode) {
    0 -> "Выживание"
    1 -> "Творчество"
    2 -> "Приключение"
    3 -> "Наблюдатель"
    else -> null
}

private fun played(millis: Long): String =
    if (millis <= 0) "не открывался" else PLAYED.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val PLAYED = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

/** How many of the latest worlds the form shows; the rest are in the window with the search. */
private const val RECENT = 3

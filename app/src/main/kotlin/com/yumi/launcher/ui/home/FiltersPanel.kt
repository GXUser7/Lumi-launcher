package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Categories
import com.yumi.launcher.core.Category
import com.yumi.launcher.core.ContentType
import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.ModSource
import com.yumi.launcher.core.SearchFilter
import com.yumi.launcher.core.SortOrder
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.packs.GameVersionFilter
import com.yumi.launcher.ui.packs.PackSearch
import com.yumi.launcher.ui.packs.PopularVersions
import com.yumi.launcher.ui.theme.GlassAlpha
import com.yumi.launcher.ui.theme.glass
import com.yumi.launcher.ui.theme.glassContainer
import com.yumi.launcher.ui.theme.glassInnerBorder

/**
 * A catalogue on screen that wants the filters beside its card: the mods screen's list — mods,
 * shaders or resource packs, whichever it shows — or the modpack catalogue. Compared by identity,
 * so the screen that leaves takes down only its own panel, not the one of the screen that came in.
 */
class FilterPanel(val modpacks: Boolean) {
    /** The pack whose game version the content filters speak of. */
    var version by mutableStateOf<String?>(null)
}

/** The panel's width; the card stops [FILTERS_GAP] to the left of it. */
val FILTERS_WIDTH = 320.dp
val FILTERS_GAP = 12.dp

/**
 * The filters of the catalogue on screen, in a panel of their own to the right of the card, where
 * the island leaves room: how to order what is found, and a card for each group of categories the
 * catalogue has — each category a chip, tapped once to filter by it and again to let it go.
 */
@Composable
fun FiltersPanel(model: LauncherModel, state: LauncherState, panel: FilterPanel, modifier: Modifier = Modifier) {
    Column(
        modifier.width(FILTERS_WIDTH).fillMaxHeight().glass(RoundedCornerShape(36.dp), alpha = GlassAlpha).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (panel.modpacks) PackFilters(model, state.packSearch) else ContentFilters(model, panel.version)
    }
}

@Composable
private fun ColumnScope.ContentFilters(model: LauncherModel, version: String?) {
    val mods = model.mods
    val filter = mods.filter
    val change: (SearchFilter) -> Unit = mods::refilter
    Header(filter.active) { change(SearchFilter()) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mods.type != ContentType.Mod && version != null) {
            Group("Версия игры", Icons.Rounded.ViewInAr) {
                ChoiceGroup(listOf(false, true), filter.anyVersion, { if (it) "Любая" else "Для $version" }, { change(filter.copy(anyVersion = it)) }, height = 40.dp)
                Hint(if (filter.anyVersion) "Показывать и те, что не отмечены для $version, — часто работают и они." else "Только отмеченные автором для $version.")
            }
        }
        SortGroup(filter.sort) { change(filter.copy(sort = it)) }
        CategoryGroups(
            categories = mods.categories,
            filter = filter,
            missingKey = mods.source == ModSource.CurseForge && model.settings.curseForgeKey.isBlank(),
            onChange = change,
        )
    }
}

@Composable
private fun ColumnScope.PackFilters(model: LauncherModel, search: PackSearch) {
    val key = model.settings.curseForgeKey.trim()
    val categories by produceState<List<Category>?>(null, search.source, key) {
        value = null
        value = runCatching { Categories.of(search.source, ContentType.Modpack, key) }.getOrDefault(emptyList())
    }
    val filter = search.filter
    Header(filter.active || search.version != null || search.loader != null) {
        search.filter = SearchFilter()
        search.version = null
        search.loader = null
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Group("Версия Minecraft", Icons.Rounded.ViewInAr, picked = search.version) {
            Chips(listOf<String?>(null) + PopularVersions.take(6), { it ?: "Любая" }, { it == search.version }) { search.version = it }
            // Every other release is in the menu; the chip shows the one picked there.
            GameVersionFilter(model, search.version?.takeIf { it !in PopularVersions.take(6) }, anyLabel = "Другая версия") { search.version = it }
        }
        Group("Загрузчик", Icons.Rounded.Widgets, picked = search.loader?.label) {
            Chips(listOf<Loader?>(null) + Loader.entries.filter { it != Loader.Vanilla }, { it?.label ?: "Любой" }, { it == search.loader }) { search.loader = it }
        }
        SortGroup(filter.sort) { search.filter = filter.copy(sort = it) }
        CategoryGroups(
            categories = categories,
            filter = filter,
            missingKey = search.source == ModSource.CurseForge && key.isBlank(),
            onChange = { search.filter = it },
        )
    }
}

@Composable
private fun Header(active: Boolean, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapeTile(Icons.Rounded.Tune, scheme.primaryContainer, scheme.onPrimaryContainer, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Фильтры", style = MaterialTheme.typography.titleLarge)
            Hint(if (active) "Поиск сужен" else "Всё подряд")
        }
        AnimatedVisibility(active, enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = onReset) { Text("Сбросить") }
        }
    }
}

@Composable
private fun SortGroup(sort: SortOrder, onPick: (SortOrder) -> Unit) {
    Group("Сортировка", Icons.Rounded.Sort) {
        Chips(SortOrder.entries, { it.label }, { it == sort }, icon = ::sortIcon) { onPick(it) }
    }
}

/** A card for each group of categories — or why there are none. */
@Composable
private fun CategoryGroups(categories: List<Category>?, filter: SearchFilter, missingKey: Boolean, onChange: (SearchFilter) -> Unit) {
    when {
        missingKey -> Hint("Категории CurseForge появятся, когда в «Настройках» будет ключ CurseForge.", Modifier.padding(horizontal = 6.dp))
        categories == null || categories.isEmpty() -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (categories == null) LoadingIndicator(Modifier.size(36.dp)) else Hint("У каталога нет категорий для этого.")
        }
        else -> for ((group, title) in Categories.groups) {
            val options = categories.filter { it.group == group }
            if (options.isEmpty()) continue
            val current = options.firstOrNull { it.id == filter.categories[group] }
            Group(title, groupIcon(group), picked = current?.label) {
                Chips(options, { it.label }, { it == current }) { choice ->
                    onChange(filter.copy(categories = if (choice == current) filter.categories - group else filter.categories + (group to choice.id)))
                }
            }
        }
    }
}

/** One group of filters on a tonal card: its glyph and title, what is picked in it, then the choices. */
@Composable
private fun Group(title: String, icon: ImageVector, picked: String? = null, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = glassContainer(), border = glassInnerBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (picked != null) Text(picked, style = MaterialTheme.typography.labelMedium, color = scheme.primary, maxLines = 1)
            }
            content()
        }
    }
}

/** Choices as chips that wrap onto as many lines as they need; the picked one gets a tick. */
@Composable
private fun <T> Chips(
    options: List<T>,
    label: (T) -> String,
    picked: (T) -> Boolean,
    icon: ((T) -> ImageVector)? = null,
    onClick: (T) -> Unit,
) {
    // Chips for the mouse: packed at their own height, not spread to a finger's touch target.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val on = picked(option)
                FilterChip(
                    selected = on,
                    onClick = { onClick(option) },
                    label = { Text(label(option), maxLines = 1) },
                    leadingIcon = when {
                        on -> ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) })
                        icon != null -> ({ Icon(icon(option), null, Modifier.size(FilterChipDefaults.IconSize)) })
                        else -> null
                    },
                    shape = CircleShape,
                )
            }
        }
    }
}

private fun sortIcon(sort: SortOrder) = when (sort) {
    SortOrder.Relevance -> Icons.Rounded.AutoAwesome
    SortOrder.Downloads -> Icons.Rounded.TrendingUp
    SortOrder.Updated -> Icons.Rounded.Update
    SortOrder.Newest -> Icons.Rounded.NewReleases
}

private fun groupIcon(group: String) = when (group) {
    "features" -> Icons.Rounded.AutoAwesome
    "resolutions" -> Icons.Rounded.GridOn
    "performance impact" -> Icons.Rounded.Speed
    else -> Icons.Rounded.Category
}

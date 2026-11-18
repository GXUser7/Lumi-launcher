package com.yumi.launcher.ui.packs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.EmojiSymbols
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumi.launcher.data.Instance
import com.yumi.launcher.ui.components.LocalFieldContainer
import com.yumi.launcher.ui.components.PackColors
import com.yumi.launcher.ui.components.PackIcons
import com.yumi.launcher.ui.components.PackShapes
import com.yumi.launcher.ui.components.PackTile
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.connectedShapes
import com.yumi.launcher.ui.components.tileColor
import com.yumi.launcher.ui.theme.glassContainer

/** The part of a pack's tile the picker is changing. */
private enum class TilePart(val label: String, val icon: ImageVector) {
    Glyph("Значок", Icons.Rounded.EmojiSymbols),
    Color("Цвет", Icons.Rounded.Palette),
    Shape("Форма", Icons.Rounded.Interests),
}

/** A pack's tile as it is being edited: glyph, colour and shape, and whether its catalogue picture is shown instead. */
internal class TileDraft(icon: Int, color: Int?, shape: Int?, val picture: String?) {
    var icon by mutableStateOf(icon)
    var color by mutableStateOf(color)
    var shape by mutableStateOf(shape)
    var usePicture by mutableStateOf(picture != null)
}

/**
 * The pack's tile, built as a phone builds its app icons: the glyph, its colour and its shape — the
 * shapes Material 3 Expressive draws with — each on its own page of a tray, the pages switched by a
 * connected button group under it. Every choice is drawn with the other two as they are now, so the
 * tray always shows what the tile would become. A catalogue pack's own picture is the first glyph,
 * and it takes the shape too.
 */
@Composable
internal fun TilePicker(tile: TileDraft) {
    val scheme = MaterialTheme.colorScheme
    var part by remember { mutableStateOf(TilePart.Glyph) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Preview(tile, 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    TilePart.entries.forEachIndexed { i, entry ->
                        ToggleButton(
                            checked = part == entry,
                            onCheckedChange = { part = entry },
                            shapes = connectedShapes(i, TilePart.entries.size),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = LocalFieldContainer.current ?: scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurfaceVariant,
                                checkedContainerColor = scheme.secondaryContainer,
                                checkedContentColor = scheme.onSecondaryContainer,
                            ),
                            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                            modifier = Modifier.weight(1f).height(44.dp).semantics { role = Role.Tab },
                        ) {
                            Icon(entry.icon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(entry.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
                Hint(
                    when {
                        tile.usePicture && part != TilePart.Shape -> "Сейчас значок — картинка сборки из каталога. Выберите значок, чтобы сменить цвет."
                        part == TilePart.Glyph -> "Значок на плитке сборки: на главной, в списках и в меню."
                        part == TilePart.Color -> "Цвет плитки не зависит от темы лаунчера."
                        else -> "Формы Material 3 Expressive — как у значков на телефоне."
                    },
                )
            }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = LocalFieldContainer.current ?: scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(part, transitionSpec = { fadeIn(tween(180, delayMillis = 50)) togetherWith fadeOut(tween(60)) }) { shown ->
                FlowRow(
                    Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (shown) {
                        TilePart.Glyph -> {
                            tile.picture?.let { url ->
                                Option(tile.usePicture, "Картинка сборки", { tile.usePicture = true }) {
                                    PackTile(Instance(id = "", name = "", version = "", icon = tile.icon, iconUrl = url, iconShape = tile.shape), 36.dp)
                                }
                            }
                            PackIcons.indices.forEach { i ->
                                Option(!tile.usePicture && tile.icon == i, "Значок ${i + 1}", { tile.icon = i; tile.usePicture = false }) {
                                    PackTile(i, 36.dp, color = tile.color, shape = tile.shape)
                                }
                            }
                        }
                        TilePart.Color -> {
                            val current = PackColors.indexOf(tileColor(tile.icon, tile.color))
                            PackColors.indices.forEach { i ->
                                Option(!tile.usePicture && current == i, "Цвет ${i + 1}", { tile.color = i; tile.usePicture = false }) {
                                    PackTile(tile.icon, 36.dp, color = i, shape = tile.shape)
                                }
                            }
                        }
                        TilePart.Shape -> PackShapes.indices.forEach { i ->
                            Option((tile.shape ?: 0) == i, "Форма ${i + 1}", { tile.shape = i }) {
                                if (tile.usePicture && tile.picture != null) {
                                    PackTile(Instance(id = "", name = "", version = "", icon = tile.icon, iconUrl = tile.picture, iconShape = i), 36.dp)
                                } else {
                                    PackTile(tile.icon, 36.dp, color = tile.color, shape = i)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The tile as it stands, large. */
@Composable
private fun Preview(tile: TileDraft, size: Dp) {
    Box(Modifier.size(size + 16.dp).clip(RoundedCornerShape(28.dp)).background(glassContainer(elevated = true)), contentAlignment = Alignment.Center) {
        if (tile.usePicture && tile.picture != null) {
            PackTile(Instance(id = "", name = "", version = "", icon = tile.icon, iconUrl = tile.picture, iconShape = tile.shape), size)
        } else {
            PackTile(tile.icon, size, color = tile.color, shape = tile.shape)
        }
    }
}

/**
 * One choice in the tray: on a quiet round backing, and when picked on the accent, in a rounded
 * square that the circle springs into — as the Expressive pickers on a phone do.
 */
@Composable
private fun Option(picked: Boolean, label: String, onPick: () -> Unit, tile: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (picked) 16.dp else 26.dp, spring(dampingRatio = 0.55f, stiffness = 600f))
    val backing by animateColorAsState(if (picked) scheme.primary else glassContainer(), tween(160))
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(corner),
        color = backing,
        modifier = Modifier.size(52.dp).semantics {
            role = Role.RadioButton
            selected = picked
            contentDescription = label
        },
    ) {
        Box(contentAlignment = Alignment.Center) { tile() }
    }
}

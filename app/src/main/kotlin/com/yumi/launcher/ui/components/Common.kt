package com.yumi.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.EmojiNature
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fort
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Volcano
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.graphics.shapes.RoundedPolygon
import com.yumi.launcher.ui.theme.oklch
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumi.launcher.data.Instance
import com.yumi.launcher.ui.theme.YumiLightColors

/** A section label above a group of controls. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A choice among a few options: neutral until picked, primary once it is. On a form card, a tone up. */
@Composable
fun choiceColors() = MaterialTheme.colorScheme.let {
    ToggleButtonDefaults.toggleButtonColors(
        containerColor = LocalFieldContainer.current ?: it.surfaceContainerHigh,
        contentColor = it.onSurface,
        checkedContainerColor = it.primary,
        checkedContentColor = it.onPrimary,
    )
}

/** A quiet line of explanation under a control. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A connected button group over [options]; [leading] draws something before each label. */
@Composable
fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    leading: @Composable (T) -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = connectedShapes(i, options.size),
                colors = choiceColors(),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f).height(height).semantics { role = Role.RadioButton },
            ) {
                leading(option)
                Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** Shapes for button [index] of [count] in a connected button group. */
@Composable
fun connectedShapes(index: Int, count: Int) = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@Composable
fun Avatar(offline: Boolean, size: Dp) {
    Box(Modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer)) {
        PixelFace(Modifier.fillMaxSize(), offline = offline)
    }
}

@Composable
fun ShapeTile(icon: ImageVector, container: Color, content: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(MaterialShapes.Cookie4Sided.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = content) }
}

/** The glyphs a pack can wear. Stored by index, so only ever append to this list. */
val PackIcons: List<ImageVector> = listOf(
    Icons.Rounded.Forest,
    Icons.Rounded.Landscape,
    Icons.Rounded.Castle,
    Icons.Rounded.LocalFlorist,
    Icons.Rounded.Bolt,
    Icons.Rounded.Diamond,
    Icons.Rounded.RocketLaunch,
    Icons.Rounded.Pets,
    Icons.Rounded.Cottage,
    Icons.Rounded.Fort,
    Icons.Rounded.Volcano,
    Icons.Rounded.Waves,
    Icons.Rounded.WaterDrop,
    Icons.Rounded.LocalFireDepartment,
    Icons.Rounded.AcUnit,
    Icons.Rounded.WbSunny,
    Icons.Rounded.NightsStay,
    Icons.Rounded.Thunderstorm,
    Icons.Rounded.Park,
    Icons.Rounded.Agriculture,
    Icons.Rounded.EmojiNature,
    Icons.Rounded.Egg,
    Icons.Rounded.Cake,
    Icons.Rounded.SportsEsports,
    Icons.Rounded.Construction,
    Icons.Rounded.Science,
    Icons.Rounded.AutoAwesome,
    Icons.Rounded.Explore,
    Icons.Rounded.Sailing,
    Icons.Rounded.Shield,
    Icons.Rounded.Favorite,
    Icons.Rounded.Star,
)

/** A tile's colour: the container and the glyph on it. */
class TileColor(val container: Color, val content: Color)

/**
 * The colours a pack's tile can take. They do not follow the theme — a pack keeps the colour it was
 * given whatever palette the launcher wears. The first three are Glacier's containers, which a
 * pack without a colour of its own takes by its glyph, as before colours could be picked. Stored by
 * index, so only ever append to this list.
 */
val PackColors: List<TileColor> = buildList {
    val glacier = YumiLightColors
    add(TileColor(glacier.primaryContainer, glacier.onPrimaryContainer))
    add(TileColor(glacier.tertiaryContainer, glacier.onTertiaryContainer))
    add(TileColor(glacier.secondaryContainer, glacier.onSecondaryContainer))
    // Pastels round the colour wheel, at one lightness so no tile shouts over the others.
    for (hue in listOf(200f, 150f, 115f, 75f, 45f, 20f, 355f, 320f, 285f)) add(TileColor(oklch(0.90f, 0.065f, hue), oklch(0.32f, 0.085f, hue)))
    add(TileColor(oklch(0.90f, 0.008f, 255f), oklch(0.32f, 0.012f, 255f)))
    // And deep ones, light glyph on a dark container, for the pack that has to stand out.
    add(TileColor(Color(0xFF2B5CAB), Color.White))
    for (hue in listOf(155f, 70f, 10f, 305f)) add(TileColor(oklch(0.78f, 0.13f, hue), oklch(0.28f, 0.07f, hue)))
    add(TileColor(oklch(0.30f, 0.012f, 255f), oklch(0.92f, 0.01f, 255f)))
}

/**
 * The shapes a pack's tile can take, from Material 3 Expressive. The cookie comes first: every
 * pack had it before shapes could be picked. Stored by index, so only ever append to this list.
 */
val PackShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Circle,
    MaterialShapes.Square,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Arch,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Flower,
    MaterialShapes.Gem,
    MaterialShapes.Pentagon,
    MaterialShapes.Heart,
    MaterialShapes.Ghostish,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.SoftBurst,
    MaterialShapes.PixelCircle,
)

/** The colour a tile shows: the one picked, or — without one — the one its glyph has always had. */
fun tileColor(icon: Int, color: Int?): TileColor = PackColors[color?.coerceIn(0, PackColors.lastIndex) ?: (Math.floorMod(icon, 3))]

/**
 * A pack's tile: its glyph on its colour, cut to its shape. [color] and [shape] null are the look
 * packs had before they could be picked: the glyph's own Glacier colour, and the cookie.
 */
@Composable
fun PackTile(icon: Int, size: Dp, modifier: Modifier = Modifier, color: Int? = null, shape: Int? = null) {
    val tint = tileColor(icon, color)
    val polygon = PackShapes[shape?.coerceIn(0, PackShapes.lastIndex) ?: 0]
    Box(
        modifier.size(size).clip(polygon.toShape()).background(tint.container),
        contentAlignment = Alignment.Center,
    ) { Icon(PackIcons[icon.coerceIn(0, PackIcons.lastIndex)], null, Modifier.size(size * 0.5f), tint = tint.content) }
}

/**
 * A pack's tile: the picture its catalogue gave it, once loaded, and its glyph until then or
 * without one. A picture is cut to the pack's shape too once one is picked; until then it keeps
 * its rounded square.
 */
@Composable
fun PackTile(instance: Instance, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = rememberRemoteImage(instance.iconUrl)
    val shape = instance.iconShape?.let { PackShapes[it.coerceIn(0, PackShapes.lastIndex)] }
    when {
        bitmap == null -> PackTile(instance.icon, size, modifier, instance.iconColor, instance.iconShape)
        shape == null -> Image(bitmap, null, modifier.size(size).clip(RoundedCornerShape(size * 0.3f)), contentScale = ContentScale.Crop)
        else -> Image(bitmap, null, modifier.size(size).clip(shape.toShape()), contentScale = ContentScale.Crop)
    }
}

/** "259,5 млн", "12 тыс.", "640" — download counts at a glance. */
fun compactCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0).replace('.', ',')
    n >= 1_000 -> "${n / 1000} тыс."
    else -> n.toString()
}

/** "4 ГБ", "2,5 ГБ". */
fun gigabytes(mb: Int): String {
    val gb = mb / 1024f
    return if (gb % 1f == 0f) "${gb.toInt()} ГБ" else "%.1f ГБ".format(gb).replace('.', ',')
}

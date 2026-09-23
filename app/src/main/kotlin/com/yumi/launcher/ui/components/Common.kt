package com.yumi.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumi.launcher.ui.theme.YumiLightColors

/** A section label above a group of controls. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A choice among a few options: neutral until picked, primary once it is. */
@Composable
fun choiceColors() = MaterialTheme.colorScheme.let {
    ToggleButtonDefaults.toggleButtonColors(
        containerColor = it.surfaceContainerHigh,
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
)

/** A pack's tile keeps the original Glacier colours even when the surrounding theme changes. */
@Composable
fun PackTile(icon: Int, size: Dp, modifier: Modifier = Modifier) {
    val scheme = YumiLightColors
    val (container, content) = when (icon % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    ShapeTile(PackIcons[icon.coerceIn(0, PackIcons.lastIndex)], container, content, size, modifier)
}

/** "4 ГБ", "2,5 ГБ". */
fun gigabytes(mb: Int): String {
    val gb = mb / 1024f
    return if (gb % 1f == 0f) "${gb.toInt()} ГБ" else "%.1f ГБ".format(gb).replace('.', ',')
}

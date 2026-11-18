package com.yumi.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yumi.launcher.ui.theme.LocalInGlass
import com.yumi.launcher.ui.theme.glassFieldContainer

/*
 * The launcher's text field: filled and rounded with no underline — the look the nick field on
 * the home screen set. With the underline gone the field's state has to show somewhere else, and
 * it is the outline: none at rest, a hairline under the pointer, the accent while typing, the
 * error colour when the value is wrong. That reads the same in both themes and every palette,
 * which an outlined field on a translucent panel did not.
 */

val FieldShape = RoundedCornerShape(18.dp)

/**
 * What a field is filled with. The plain panel gives fields one tone; a [FormSection] card, a tone
 * higher itself, raises its fields one more step so they still stand off it.
 */
internal val LocalFieldContainer = compositionLocalOf<Color?> { null }

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = LocalTextStyle.current,
    shape: Shape = FieldShape,
) {
    val interaction = remember { MutableInteractionSource() }
    FieldFrame(modifier, supporting, isError) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().fieldOutline(interaction, shape, isError, enabled),
            enabled = enabled,
            textStyle = textStyle,
            label = label?.let { { Text(it, maxLines = 1) } },
            placeholder = placeholder?.let { { Text(it, maxLines = 1) } },
            leadingIcon = icon?.let { { Icon(it, null) } },
            trailingIcon = trailing,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            interactionSource = interaction,
            shape = shape,
            colors = fieldColors(interaction),
        )
    }
}

/** The same field over a [TextFieldValue], for callers that move the cursor themselves. */
@Composable
fun Field(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    shape: Shape = FieldShape,
) {
    val interaction = remember { MutableInteractionSource() }
    FieldFrame(modifier, null, false) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().fieldOutline(interaction, shape, false, enabled),
            enabled = enabled,
            textStyle = textStyle,
            placeholder = placeholder?.let { { Text(it, maxLines = 1) } },
            leadingIcon = icon?.let { { Icon(it, null) } },
            trailingIcon = trailing,
            singleLine = true,
            interactionSource = interaction,
            shape = shape,
            colors = fieldColors(interaction),
        )
    }
}

/** A pill-shaped search box with a clear button once something is typed. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Search,
) = Field(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    placeholder = placeholder,
    icon = icon,
    trailing = if (value.isEmpty()) null else {
        { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, "Очистить") } }
    },
    shape = CircleShape,
)

/** The supporting line goes under the outlined box rather than inside the outline with it. */
@Composable
private fun FieldFrame(modifier: Modifier, supporting: String?, isError: Boolean, field: @Composable () -> Unit) {
    if (supporting == null) {
        Column(modifier) { field() }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        field()
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Modifier.fieldOutline(interaction: MutableInteractionSource, shape: Shape, isError: Boolean, enabled: Boolean): Modifier {
    val scheme = MaterialTheme.colorScheme
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val inGlass = LocalInGlass.current
    val color by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            isError -> scheme.error
            focused -> scheme.primary
            hovered -> scheme.outline
            inGlass -> scheme.outlineVariant.copy(alpha = 0.36f)
            else -> Color.Transparent
        },
    )
    val width by animateDpAsState(if (enabled && (focused || isError)) 2.dp else 1.dp)
    return hoverable(interaction, enabled).border(width, color, shape)
}

@Composable
private fun fieldColors(interaction: MutableInteractionSource): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val hovered by interaction.collectIsHoveredAsState()
    val rest = LocalFieldContainer.current ?: glassFieldContainer()
    // State layers over the fill: the accent while typing, a trace of the text colour under the pointer.
    val focused = scheme.primary.copy(alpha = 0.08f).compositeOver(rest)
    val hover = scheme.onSurface.copy(alpha = 0.05f).compositeOver(rest)
    return TextFieldDefaults.colors(
        focusedContainerColor = focused,
        unfocusedContainerColor = if (hovered) hover else rest,
        disabledContainerColor = rest.copy(alpha = rest.alpha * 0.5f),
        errorContainerColor = scheme.error.copy(alpha = 0.06f).compositeOver(rest),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        errorLabelColor = scheme.error,
        focusedLeadingIconColor = scheme.primary,
        unfocusedLeadingIconColor = scheme.onSurfaceVariant,
        errorLeadingIconColor = scheme.error,
        focusedTrailingIconColor = scheme.onSurfaceVariant,
        unfocusedTrailingIconColor = scheme.onSurfaceVariant,
        errorTrailingIconColor = scheme.error,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
        cursorColor = scheme.primary,
        errorCursorColor = scheme.error,
    )
}

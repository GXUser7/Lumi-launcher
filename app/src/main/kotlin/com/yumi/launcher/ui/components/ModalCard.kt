package com.yumi.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumi.launcher.ui.theme.glass

/**
 * A question over the whole window: the scene dimmed, one floating card in the middle. Drawn in
 * the window itself rather than as a separate dialog window, which an undecorated window would
 * show with a stray frame of its own.
 */
@Composable
fun ModalCard(width: Dp = 480.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallows clicks so nothing behind the card can be pressed.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(width).glass(RoundedCornerShape(36.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

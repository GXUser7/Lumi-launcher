package com.yumi.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.yumi.launcher.ui.theme.glass
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

/*
 * The window is undecorated so the sky can run under the title bar, which means the three caption
 * buttons, dragging and resizing are all ours to provide.
 */

@Composable
fun FrameWindowScope.WindowControls(state: WindowState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val maximized = state.placement == WindowPlacement.Maximized
    Row(
        modifier.glass(CircleShape).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CaptionButton(Icons.Rounded.Remove, "Свернуть") { state.isMinimized = true }
        CaptionButton(if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare, if (maximized) "Восстановить" else "Развернуть") {
            if (!maximized) {
                // An undecorated frame maximises over the taskbar unless told where the work area ends.
                val screen = window.graphicsConfiguration
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(screen)
                val b = screen.bounds
                window.maximizedBounds = Rectangle(b.x + insets.left, b.y + insets.top, b.width - insets.left - insets.right, b.height - insets.top - insets.bottom)
            }
            state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
        }
        CaptionButton(Icons.Rounded.Close, "Закрыть", danger = true, onClick = onClose)
    }
}

@Composable
private fun CaptionButton(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        when {
            hovered && danger -> Color(0xFFC42B1C)
            hovered -> scheme.onSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
    )
    Box(
        Modifier.size(40.dp, 44.dp)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (hovered && danger) Color.White else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/**
 * Invisible grips along the window's edges and corners. Positions come from the screen, not from
 * the drag deltas: resizing from the left or top moves the window, and with it the coordinate
 * space the deltas would be measured in.
 */
@Composable
fun FrameWindowScope.ResizeEdges(state: WindowState, thickness: Dp = 6.dp) {
    if (state.placement != WindowPlacement.Floating) return
    Box(Modifier.fillMaxSize()) {
        Grip(Modifier.align(Alignment.CenterStart).width(thickness).fillMaxHeight(), Cursor.W_RESIZE_CURSOR, left = true)
        Grip(Modifier.align(Alignment.CenterEnd).width(thickness).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, right = true)
        Grip(Modifier.align(Alignment.TopCenter).height(thickness).fillMaxWidth(), Cursor.N_RESIZE_CURSOR, top = true)
        Grip(Modifier.align(Alignment.BottomCenter).height(thickness).fillMaxWidth(), Cursor.S_RESIZE_CURSOR, bottom = true)
        val corner = thickness * 2
        Grip(Modifier.align(Alignment.TopStart).size(corner), Cursor.NW_RESIZE_CURSOR, left = true, top = true)
        Grip(Modifier.align(Alignment.TopEnd).size(corner), Cursor.NE_RESIZE_CURSOR, right = true, top = true)
        Grip(Modifier.align(Alignment.BottomStart).size(corner), Cursor.SW_RESIZE_CURSOR, left = true, bottom = true)
        Grip(Modifier.align(Alignment.BottomEnd).size(corner), Cursor.SE_RESIZE_CURSOR, right = true, bottom = true)
    }
}

@Composable
private fun FrameWindowScope.Grip(
    modifier: Modifier,
    cursor: Int,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(Unit) {
                var start = Rectangle()
                var origin = Point()
                detectDragGestures(onDragStart = {
                    start = window.bounds
                    origin = MouseInfo.getPointerInfo().location
                }) { change, _ ->
                    change.consume()
                    val at = MouseInfo.getPointerInfo().location
                    val dx = at.x - origin.x
                    val dy = at.y - origin.y
                    val min = window.minimumSize
                    var x = start.x
                    var y = start.y
                    var w = start.width
                    var h = start.height
                    if (right) w = maxOf(min.width, start.width + dx)
                    if (bottom) h = maxOf(min.height, start.height + dy)
                    if (left) { w = maxOf(min.width, start.width - dx); x = start.x + start.width - w }
                    if (top) { h = maxOf(min.height, start.height - dy); y = start.y + start.height - h }
                    window.setBounds(x, y, w, h)
                }
            },
    )
}

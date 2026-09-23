package com.yumi.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.yumi.launcher.ui.home.PlayState
import kotlinx.coroutines.isActive
import kotlin.math.round

/**
 * The home screen's one loud control: a nine-lobed cookie that turns slowly while it waits and,
 * under the pointer, stops and settles into a rounded square — the expressive "this is ready"
 * gesture. While the game starts, the Material loading indicator takes the middle; while it runs,
 * the shape stays square and turns coral, and pressing it stops the game.
 */
@Composable
fun PlayButton(state: PlayState, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 116.dp) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme

    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square) }
    val scratch = remember { Path() }
    val settled = hovered || state != PlayState.Idle
    val progress by animateFloatAsState(if (settled) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f))
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f))
    val fill by animateColorAsState(if (state == PlayState.Running) scheme.error else scheme.primary)

    // Accumulated rather than derived from time, so the cookie resumes where it stopped. Settling
    // turns it on to the nearest quarter: a square that stops wherever the spin left it reads as
    // tilted, not as expressive.
    val angle = remember { Animatable(0f) }
    LaunchedEffect(settled) {
        if (settled) {
            angle.animateTo(round(angle.value / 90f) * 90f, spring(dampingRatio = 0.6f, stiffness = 320f))
            return@LaunchedEffect
        }
        var last = 0L
        while (isActive) {
            val now = withFrameMillis { it }
            if (last != 0L) angle.snapTo((angle.value + (now - last) * DEGREES_PER_MS) % 360f)
            last = now
        }
    }

    Box(
        modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = morph.toPath(progress.coerceIn(-0.15f, 1.15f), scratch)
            withTransform({
                rotate(angle.value, pivot = center)
                // MaterialShapes are normalised into a 0..1 box.
                scale(this@Canvas.size.width, this@Canvas.size.height, pivot = Offset.Zero)
            }) {
                drawPath(path, fill)
            }
        }
        when (state) {
            PlayState.Idle -> Icon(Icons.Rounded.PlayArrow, null, tint = scheme.onPrimary, modifier = Modifier.size(56.dp))
            PlayState.Launching -> LoadingIndicator(color = scheme.onPrimary, modifier = Modifier.size(64.dp))
            PlayState.Running -> Icon(Icons.Rounded.Stop, null, tint = scheme.onError, modifier = Modifier.size(48.dp))
        }
    }
}

/** One turn every twenty seconds: enough to read as alive, too slow to pull the eye. */
private const val DEGREES_PER_MS = 360f / 20_000f

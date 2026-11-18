package com.yumi.launcher.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode

/** Blur radius under floating glass panels, matching YumiDesktop and Masaki. */
val FrostBlur = 28.dp

/** Veil opacity for floating bars, corner pills, friends panel, and filters panel. */
const val GlassAlpha = 0.68f

/** Veil opacity for the primary left screen card. */
const val CardGlassAlpha = 0.72f

/** Denser veil for modal cards and popovers so they stay legible over any UI behind them. */
const val PopGlassAlpha = 0.88f

/**
 * Holds the single downscaled blurred snapshot of the 3D island scene for the current frame.
 * Every glass surface samples its own region from this one immutable [Image].
 */
@Stable
class BackdropGlassState {
    internal var frost by mutableStateOf<Image?>(null)
    internal var viewport by mutableStateOf(IntSize.Zero)
    private val retired = ArrayDeque<Image>(4)

    internal fun publish(next: Image) {
        frost?.let { prev ->
            retired.addLast(prev)
            while (retired.size > 3) retired.removeFirst().close()
        }
        frost = next
    }

    internal fun clear() {
        frost?.close()
        frost = null
        while (retired.isNotEmpty()) retired.removeFirst().close()
    }
}

val LocalBackdropGlass = staticCompositionLocalOf<BackdropGlassState?> { null }

/** True inside floating glass surfaces so inner cards/rows use translucent tonal fills. */
val LocalInGlass = compositionLocalOf { false }

internal fun shrinkForFrost(blurPx: Float): Float = (blurPx / 5.5f).toInt().coerceIn(4, 8).toFloat()

@Composable
@ReadOnlyComposable
fun isLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
@ReadOnlyComposable
fun lightened(alpha: Float): Float = if (isLightTheme()) alpha + (1f - alpha) * 0.32f else alpha

/**
 * Matte glass tint derived from the active OKLCH/Glacier palette: a light scattering wash in
 * light mode and a calm tinted obsidian veil in dark mode.
 */
@Composable
@ReadOnlyComposable
fun glassTint(alpha: Float = GlassAlpha): Color {
    val scheme = MaterialTheme.colorScheme
    val base = if (isLightTheme()) {
        lerp(scheme.surfaceContainerLowest, scheme.primaryContainer, 0.16f)
    } else {
        lerp(scheme.surfaceContainerHigh, scheme.primaryContainer, 0.34f)
    }
    return base.copy(alpha = lightened(alpha))
}

/**
 * Translucent inner card fill inside a glass panel, letting the blurred scene gently tint cards
 * while keeping crisp tonal separation and text contrast.
 */
@Composable
@ReadOnlyComposable
fun glassContainer(elevated: Boolean = false): Color {
    val scheme = MaterialTheme.colorScheme
    if (!LocalInGlass.current) return if (elevated) scheme.surfaceContainerHigh else scheme.surfaceContainer
    return if (isLightTheme()) {
        lerp(Color.White, scheme.primaryContainer, if (elevated) 0.14f else 0.08f)
            .copy(alpha = if (elevated) 0.72f else 0.56f)
    } else {
        lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, if (elevated) 0.24f else 0.16f)
            .copy(alpha = if (elevated) 0.52f else 0.38f)
    }
}

/** Translucent fill for text fields and embedded pickers inside glass cards. */
@Composable
@ReadOnlyComposable
fun glassFieldContainer(): Color {
    val scheme = MaterialTheme.colorScheme
    if (!LocalInGlass.current) return scheme.surfaceContainerHigh
    return if (isLightTheme()) {
        Color.White.copy(alpha = 0.74f)
    } else {
        lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, 0.18f).copy(alpha = 0.54f)
    }
}

/** Subtle top-lit specular glass border inspired by Masaki's `--glass-line`. */
@Composable
@ReadOnlyComposable
fun glassOutlineBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val light = isLightTheme()
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (light) 0.78f else 0.14f),
            scheme.outlineVariant.copy(alpha = if (light) 0.32f else 0.06f),
        ),
    )
}

/** Quiet inner card border on glass so rows and sections stay distinct over bright scenes. */
@Composable
@ReadOnlyComposable
fun glassInnerBorder(elevated: Boolean = false): BorderStroke {
    val scheme = MaterialTheme.colorScheme
    val light = isLightTheme()
    val alpha = when {
        !LocalInGlass.current -> 0f
        light -> if (elevated) 0.42f else 0.30f
        else -> if (elevated) 0.22f else 0.14f
    }
    return BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = alpha))
}

/**
 * Floating matte frosted glass cut to [shape]. Samples the pre-blurred backdrop snapshot
 * aligned to the component's window coordinates, lays down [tint] (or [glassTint]), and adds a
 * soft catchlight rim and drop shadow.
 */
fun Modifier.glass(
    shape: Shape,
    alpha: Float = GlassAlpha,
    tint: Color? = null,
    elevation: Dp = 14.dp,
): Modifier = composed {
    val backdrop = LocalBackdropGlass.current
    val resolvedTint = tint ?: glassTint(alpha)
    val outline = glassOutlineBrush()
    val light = isLightTheme()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val ambient = if (light) Color(0x220E1319) else Color(0x48000000)
    val spot = if (light) Color(0x300E1319) else Color(0x64000000)
    this
        .onGloballyPositioned { origin.value = it.positionInWindow() }
        .shadow(elevation, shape, ambientColor = ambient, spotColor = spot)
        .clip(shape)
        .drawBehind {
            val frost = backdrop?.frost
            val viewport = backdrop?.viewport ?: IntSize.Zero
            if (frost != null && viewport.width > 0 && viewport.height > 0) {
                val at = origin.value
                translate(-at.x, -at.y) {
                    scale(
                        viewport.width.toFloat() / frost.width,
                        viewport.height.toFloat() / frost.height,
                        pivot = Offset.Zero,
                    ) {
                        drawIntoCanvas { canvas ->
                            val w = frost.width.toFloat()
                            val h = frost.height.toFloat()
                            canvas.skiaCanvas.drawImageRect(
                                frost, 0f, 0f, w, h, 0f, 0f, w, h,
                                SamplingMode.LINEAR, null, true,
                            )
                        }
                    }
                }
            }
            drawRect(resolvedTint)
        }
        .border(1.dp, outline, shape)
        .graphicsLayer()
}

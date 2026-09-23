package com.yumi.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun LauncherTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (dark) YumiDarkColors else YumiLightColors,
        typography = YumiTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * The floating panels over the 3D scene.
 *
 * Compose on desktop has no backdrop blur, so a panel is the surface tone let through a little —
 * and on its own that is not enough: over the white sea of clouds a near-white panel simply
 * dissolves. The soft shadow and the hairline outline are what keep its edge when the scene behind
 * it is the same colour as it is.
 */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this
        .shadow(10.dp, shape, ambientColor = Color(0x2E0E1319), spotColor = Color(0x380E1319))
        .clip(shape)
        .background(scheme.surface.copy(alpha = 0.92f))
        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), shape)
}

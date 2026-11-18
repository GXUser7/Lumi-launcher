package com.yumi.launcher.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.yumi.launcher.settings.Palette

@Composable
fun LauncherTheme(dark: Boolean, palette: Palette, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme(palette, dark),
        typography = YumiTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

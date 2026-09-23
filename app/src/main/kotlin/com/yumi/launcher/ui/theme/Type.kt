package com.yumi.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The type of the approved mockup, not YumiDesktop's poster voice. The mockup set its headlines
 * in Roboto Flex at wght 820 and wdth 112 — wide and heavy, where Yumi's poster instance is
 * narrowed to wdth 90 — and its labels at 700.
 *
 * Both are static instances cut from the variable font with fontTools (opsz 44 for the display
 * face, 14 for the bold) and subset to Latin and Cyrillic, ~50 KB each. The UI faces below 700
 * are YumiDesktop's own instances, unchanged.
 */

private val DisplayWeight = FontWeight(820)

private val Display = FontFamily(Font("font/roboto_flex_display_wide.ttf", DisplayWeight))

private val Ui = FontFamily(
    Font("font/roboto_flex_regular.ttf", FontWeight.Normal),
    Font("font/roboto_flex_medium.ttf", FontWeight.Medium),
    Font("font/roboto_flex_semibold.ttf", FontWeight.SemiBold),
    Font("font/roboto_flex_bold.ttf", FontWeight.Bold),
)

/** Trimmed leading needs the font's own padding out of the way to have anything to trim. */
@Suppress("DEPRECATION")
private val TightPlatform = PlatformTextStyle(null, null)

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun display(size: Int, lineHeight: Float = 1f, tracking: Float = -0.02f) = TextStyle(
    fontFamily = Display,
    fontWeight = DisplayWeight,
    fontSynthesis = FontSynthesis.None,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    platformStyle = TightPlatform,
    lineHeightStyle = TrimBoth,
)

private fun ui(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.42f, tracking: Float = 0f) = TextStyle(
    fontFamily = Ui,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    platformStyle = TightPlatform,
)

val YumiTypography = Typography(
    displayLarge = display(52),
    displayMedium = display(44),
    displaySmall = display(40),

    headlineLarge = display(32),
    headlineMedium = display(26, lineHeight = 1.1f),
    headlineSmall = display(22, lineHeight = 1.12f, tracking = -0.01f),

    titleLarge = ui(20, FontWeight.Bold, lineHeight = 1.3f),
    titleMedium = ui(16, FontWeight.Bold, lineHeight = 1.35f),
    titleSmall = ui(14, FontWeight.Bold, lineHeight = 1.35f),

    bodyLarge = ui(16),
    bodyMedium = ui(14),
    bodySmall = ui(12, lineHeight = 1.35f),

    labelLarge = ui(14, FontWeight.Bold, lineHeight = 1.2f),
    labelMedium = ui(12, FontWeight.SemiBold, lineHeight = 1.2f, tracking = 0.02f),
    labelSmall = ui(11, FontWeight.Bold, lineHeight = 1.2f, tracking = 0.08f),
)

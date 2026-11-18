package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yumi.launcher.ui.components.Tip
import com.yumi.launcher.ui.theme.GlassAlpha
import com.yumi.launcher.ui.theme.glass
import com.yumi.launcher.ui.theme.glassContainer

/**
 * The header of a corner card that folds away — the news, the friends. One pill, and all of it is
 * the switch, so nothing is left on screen that looks like a button and does nothing. Open, it
 * names the card; folded, [folded] says in a line what the card holds, so the pill is worth its
 * corner. The arrow points where the card will go: [opensUp] for one that unfolds upwards.
 */
@Composable
internal fun FoldPill(
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
    label: String,
    opensUp: Boolean,
    open: @Composable RowScope.() -> Unit,
    folded: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Tip(label) {
        Surface(
            onClick = { onHiddenChange(!hidden) },
            shape = CircleShape,
            color = Color.Transparent,
            modifier = modifier.glass(CircleShape, alpha = GlassAlpha),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    hidden,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(80)))
                            .using(SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) })
                    },
                    contentAlignment = Alignment.CenterEnd,
                ) { isHidden ->
                    Row(
                        Modifier.padding(start = if (isHidden) 8.dp else 14.dp, top = if (isHidden) 8.dp else 4.dp, bottom = if (isHidden) 8.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) { if (isHidden) folded() else open() }
                }
                val up = hidden == opensUp
                val turn by animateFloatAsState(if (up) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                Box(
                    Modifier.padding(start = 10.dp, end = 6.dp).size(32.dp).background(glassContainer(elevated = true), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(turn)) }
            }
        }
    }
}

/** The glyph at the start of a folded pill: the card's colour and sign, where a face would go. */
@Composable
internal fun FoldTile(container: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(36.dp).background(container, CircleShape), contentAlignment = Alignment.Center) { content() }
}

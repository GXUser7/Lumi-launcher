package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yumi.launcher.ui.components.Tip
import com.yumi.launcher.ui.theme.glass

private class NewsItem(val tag: String, val title: String, val icon: ImageVector, val container: Color, val content: Color)

/** Widths by distance from the chosen card: one hero, one peek, one sliver — M3's hero carousel. */
private val Widths = listOf(360.dp, 180.dp, 84.dp)

/**
 * News as a hero carousel. Choosing a card grows it into the hero and the other two shrink along
 * behind it; only the hero shows its text, the others are colour and a glyph. The arrow by the
 * header folds the cards away — the header stays, to bring them back — and the choice is kept
 * between launches.
 */
@Composable
fun NewsCarousel(selected: Int, onSelect: (Int) -> Unit, hidden: Boolean, onHiddenChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val items = listOf(
        NewsItem("Лаунчер", "Каталог модов прямо в лаунчере", Icons.Rounded.Extension, scheme.primary, scheme.onPrimary),
        NewsItem("Сборки", "Вишнёвый сезон: уютные сборки", Icons.Rounded.LocalFlorist, scheme.tertiaryContainer, scheme.onTertiaryContainer),
        NewsItem("Серверы", "Сервер недели", Icons.Rounded.Dns, scheme.secondaryContainer, scheme.onSecondaryContainer),
    )
    // As wide as the cards, so the header keeps its place when they fold away and only drops down.
    Column(modifier.width(Widths.fold(0.dp) { sum, it -> sum + it } + 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = {}, shape = CircleShape, color = Color.Transparent, modifier = Modifier.glass(CircleShape)) {
                Row(Modifier.height(40.dp).padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Новости и события", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                }
            }
            val label = if (hidden) "Показать новости" else "Скрыть новости"
            val turn by animateFloatAsState(if (hidden) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 300f))
            Tip(label) {
                IconButton(
                    onClick = { onHiddenChange(!hidden) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(40.dp).glass(CircleShape),
                ) { Icon(Icons.Rounded.KeyboardArrowDown, label, Modifier.rotate(turn)) }
            }
        }
        AnimatedVisibility(
            !hidden,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 300f), expandFrom = Alignment.Top) + fadeIn(tween(250, delayMillis = 80)),
            exit = shrinkVertically(tween(260), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
        ) { Cards(items, selected, onSelect) }
    }
}

@Composable
private fun Cards(items: List<NewsItem>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(top = 10.dp).height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, item ->
            val rank = (i - selected + items.size) % items.size
            val width by animateDpAsState(Widths[rank], spring(dampingRatio = 0.72f, stiffness = 260f))
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(28.dp),
                color = item.container,
                contentColor = item.content,
                shadowElevation = 6.dp,
                modifier = Modifier.width(width).fillMaxHeight(),
            ) {
                NewsCard(item, hero = rank == 0)
            }
        }
    }
}

/** Kept out of the carousel's Row so the plain AnimatedVisibility is the one that resolves. */
@Composable
private fun NewsCard(item: NewsItem, hero: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Icon(item.icon, null, Modifier.align(Alignment.TopEnd).offset(14.dp, (-14).dp).size(150.dp).alpha(0.22f))
        AnimatedVisibility(
            hero,
            Modifier.align(Alignment.BottomStart).padding(22.dp),
            enter = fadeIn(tween(250, delayMillis = 150)),
            exit = fadeOut(tween(100)),
        ) {
            Column(Modifier.width(316.dp)) {
                Text(item.tag.uppercase(), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

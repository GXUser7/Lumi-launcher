package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private class NewsItem(val tag: String, val title: String, val icon: ImageVector, val container: Color, val content: Color)

/** Widths by distance from the chosen card: one hero, one peek, one sliver — M3's hero carousel. */
private val Widths = listOf(360.dp, 180.dp, 84.dp)

/**
 * News as a hero carousel. Choosing a card grows it into the hero and the other two shrink along
 * behind it; only the hero shows its text, the others are colour and a glyph. The header folds the
 * cards away into a pill in the corner that shows the hero's headline and brings them back; the
 * choice is kept between launches.
 */
@Composable
fun NewsCarousel(selected: Int, onSelect: (Int) -> Unit, hidden: Boolean, onHiddenChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val items = listOf(
        NewsItem("Лаунчер", "Каталог модов прямо в лаунчере", Icons.Rounded.Extension, scheme.primary, scheme.onPrimary),
        NewsItem("Сборки", "Вишнёвый сезон: уютные сборки", Icons.Rounded.LocalFlorist, scheme.tertiaryContainer, scheme.onTertiaryContainer),
        NewsItem("Серверы", "Сервер недели", Icons.Rounded.Dns, scheme.secondaryContainer, scheme.onSecondaryContainer),
    )
    // The pill keeps to the corner: open, it sits over the cards' right edge; folded, it drops down there.
    Column(modifier, horizontalAlignment = Alignment.End) {
        val hero = items[selected.coerceIn(items.indices)]
        FoldPill(
            hidden = hidden,
            onHiddenChange = onHiddenChange,
            label = if (hidden) "Показать новости" else "Скрыть новости",
            opensUp = true,
            open = {
                Icon(Icons.Rounded.Newspaper, null, Modifier.size(20.dp))
                Text("Новости и события", style = MaterialTheme.typography.titleSmall)
            },
            folded = {
                FoldTile(hero.container) { Icon(hero.icon, null, Modifier.size(20.dp), tint = hero.content) }
                Column {
                    Text("Новости · ${hero.tag}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(hero.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
                }
            },
        )
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

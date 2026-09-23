package com.yumi.launcher.ui.servers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yumi.launcher.core.MotdSpan
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.data.ServerProfile
import com.yumi.launcher.ui.components.PackTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/*
 * A server's description the way the game's multiplayer list draws it. These colours do not follow
 * the launcher's theme on purpose: § codes name fixed colours, picked for the game's dark list.
 */

/** The dark strip of the multiplayer list. */
internal val ListStrip = Color(0xFF16181C)
private val ListGrey = Color(0xFFAAAAAA)

internal fun motdText(spans: List<MotdSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        withStyle(
            SpanStyle(
                color = Color(0xFF000000 or (span.color ?: 0xAAAAAA).toLong()),
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
            ),
        ) { append(span.text) }
    }
}

/** The description on its own strip, as it shows under a server someone added. */
@Composable
internal fun MotdStrip(spans: List<MotdSpan>, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ListStrip).padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Servers centre their descriptions with spaces, which only lines up in an evenly spaced font.
        Text(motdText(spans), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * This server as a friend's multiplayer list will show it: icon, name, description, the player
 * count and full signal — a live preview for the settings that shape it.
 */
@Composable
internal fun ServerListPreview(model: LauncherModel, profile: ServerProfile, name: String, motd: List<MotdSpan>, maxPlayers: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ListStrip).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerIcon(model, profile, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                motdText(motd.ifEmpty { listOf(MotdSpan("A Minecraft Server", null)) }),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FullSignal()
            Text("0/$maxPlayers", color = ListGrey, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FullSignal() {
    Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
        val bar = size.width / 5
        for (i in 0 until 5) {
            val h = size.height * (i + 1) / 5
            drawRoundRect(Online, Offset(i * bar + bar * 0.15f, size.height - h), Size(bar * 0.7f, h), CornerRadius(1.5f, 1.5f))
        }
    }
}

/** The server's own `server-icon.png` when it has one — the icon friends see — or its pack's tile. */
@Composable
internal fun ServerIcon(model: LauncherModel, profile: ServerProfile, size: Dp) {
    val icon by produceState<ImageBitmap?>(null, profile.id) {
        value = withContext(Dispatchers.IO) {
            model.servers.folder(profile).resolve("server-icon.png").takeIf { it.isFile }
                ?.let { runCatching { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }.getOrNull() }
        }
    }
    val bitmap = icon
    if (bitmap == null) PackTile(profile.icon, size)
    else Image(bitmap, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)), filterQuality = FilterQuality.None)
}

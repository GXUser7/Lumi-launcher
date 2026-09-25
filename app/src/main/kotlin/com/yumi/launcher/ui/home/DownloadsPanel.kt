package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yumi.launcher.data.ActiveDownload
import com.yumi.launcher.data.DownloadKind
import com.yumi.launcher.data.FinishedDownload
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.theme.glass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The top bar's downloads button: a count on it while anything downloads, and under it a panel of
 * what is downloading — with how far along and a way to stop it — and what has finished.
 */
@Composable
fun DownloadsButton(model: LauncherModel, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val active = model.downloads.active
    Box(modifier) {
        IconButton(
            onClick = { open = !open },
            modifier = Modifier.size(44.dp),
            colors = if (open) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors(),
        ) {
            BadgedBox(badge = { if (active.isNotEmpty()) Badge { Text(active.size.toString()) } }) {
                AnimatedContent(active.isNotEmpty()) { busy ->
                    Icon(if (busy) Icons.Rounded.Downloading else Icons.Outlined.Download, "Загрузки")
                }
            }
        }
        if (open) {
            val below = with(LocalDensity.current) { 56.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, below),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                DownloadsPanel(model, active)
            }
        }
    }
}

@Composable
private fun DownloadsPanel(model: LauncherModel, active: List<ActiveDownload>) {
    val downloads = model.downloads
    Column(
        Modifier.width(400.dp).glass(RoundedCornerShape(30.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Загрузки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (downloads.recent.isNotEmpty()) TextButton(onClick = downloads::clear) { Text("Очистить") }
        }
        if (active.isEmpty() && downloads.recent.isEmpty()) {
            val scheme = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShapeTile(Icons.Rounded.DownloadDone, scheme.secondaryContainer, scheme.onSecondaryContainer, 44.dp)
                Column {
                    Text("Сейчас ничего не скачивается", style = MaterialTheme.typography.titleSmall)
                    Hint("Здесь появятся файлы игры перед запуском, сборки, моды, шейдеры и серверы.")
                }
            }
            return@Column
        }
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            active.forEach { ActiveRow(it) }
            if (downloads.recent.isNotEmpty()) {
                if (active.isNotEmpty()) Spacer(Modifier.size(2.dp))
                Label("Недавние")
                downloads.recent.forEach { FinishedRow(it) }
            }
        }
    }
}

@Composable
private fun ActiveRow(item: ActiveDownload) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShapeTile(glyph(item.kind), scheme.primaryContainer, scheme.onPrimaryContainer, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Hint(item.progress?.let { "${item.detail} · ${(it * 100).toInt()}%" } ?: item.detail)
                }
                item.cancel?.let { cancel -> IconButton(onClick = cancel) { Icon(Icons.Rounded.Close, "Отменить «${item.title}»") } }
            }
            val progress = item.progress
            if (progress != null) LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
            else LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(end = 6.dp))
        }
    }
}

@Composable
private fun FinishedRow(item: FinishedDownload) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (item.error == null) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            null,
            Modifier.size(20.dp),
            tint = if (item.error == null) scheme.primary else scheme.error,
        )
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.error ?: "${item.what.replaceFirstChar { it.uppercase() }} · ${TIME.format(Instant.ofEpochMilli(item.at).atZone(ZoneId.systemDefault()))}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.error == null) scheme.onSurfaceVariant else scheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun glyph(kind: DownloadKind) = when (kind) {
    DownloadKind.Game -> Icons.Rounded.SportsEsports
    DownloadKind.Pack -> Icons.Rounded.ViewInAr
    DownloadKind.Content -> Icons.Rounded.Extension
    DownloadKind.Server -> Icons.Rounded.Dns
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

package com.yumi.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.components.freeSpace
import com.yumi.launcher.ui.components.hasNonAscii
import com.yumi.launcher.ui.components.pickFolder
import com.yumi.launcher.ui.theme.PopGlassAlpha
import com.yumi.launcher.ui.theme.glass
import com.yumi.launcher.ui.theme.glassContainer
import com.yumi.launcher.ui.theme.glassInnerBorder
import java.io.File

/**
 * Asked once, before the first download: where Minecraft should go. It is worth asking — a version
 * with its Java and assets comes to a gigabyte or two, and packs keep their worlds there too.
 */
@Composable
fun FolderPrompt(onUse: (File) -> Unit, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var folder by remember { mutableStateOf(LauncherPaths.config) }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallows clicks so nothing behind the question can be pressed.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(500.dp).glass(RoundedCornerShape(36.dp), alpha = PopGlassAlpha, elevation = 24.dp).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Куда скачать Minecraft?", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Сюда лягут версии игры, Java и ваши сборки с мирами. Одна версия занимает 1–2 ГБ. Папку можно сменить позже в настройках.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Surface(shape = RoundedCornerShape(20.dp), color = glassContainer(), border = glassInnerBorder(), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShapeTile(Icons.Outlined.Folder, scheme.primaryContainer, scheme.onPrimaryContainer, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(folder.path, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        freeSpace(folder)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    }
                }
            }
            if (folder.hasNonAscii()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = scheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "В пути есть не латинские буквы — версии старше 1.13 могут не запуститься. Лучше папка вроде C:\\Games\\Lumi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickFolder(folder, "Папка для Minecraft")?.let { folder = it } }, shapes = ButtonDefaults.shapes()) {
                    Text("Другая папка…")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Отмена") }
                Button(onClick = { onUse(folder) }, shapes = ButtonDefaults.shapes()) { Text("Скачать сюда") }
            }
        }
    }
}

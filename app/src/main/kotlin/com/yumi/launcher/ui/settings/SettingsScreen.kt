package com.yumi.launcher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.selected
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.ui.theme.colorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yumi.launcher.core.FriendsApi
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.LauncherPaths
import com.yumi.launcher.data.LauncherModel
import com.yumi.launcher.settings.AfterLaunch
import com.yumi.launcher.settings.SceneMotion
import com.yumi.launcher.settings.SceneQuality
import com.yumi.launcher.settings.ThemeMode
import com.yumi.launcher.ui.components.ChoiceGroup
import com.yumi.launcher.ui.components.CurseForgeKeyField
import com.yumi.launcher.ui.components.Field
import com.yumi.launcher.ui.components.Label
import com.yumi.launcher.ui.components.LocalFieldContainer
import com.yumi.launcher.ui.components.freeSpace
import com.yumi.launcher.ui.components.hasNonAscii
import com.yumi.launcher.ui.components.pickFolder
import com.yumi.launcher.ui.components.pickJava
import com.yumi.launcher.ui.packs.MemorySlider
import java.io.File

@Composable
fun SettingsScreen(model: LauncherModel) {
    val settings = model.settings
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Настройки", style = type.displaySmall)
        Spacer(Modifier.height(2.dp))

        Group("Оформление")
        Label("Тема")
        Choice(ThemeMode.entries, settings.theme, { it.label }, { settings.theme = it }) { mode ->
            Icon(mode.icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Label("Цвета")
        PalettePicker(settings.palette) { settings.palette = it }
        Hint(
            when (settings.palette) {
                Palette.Original -> "Ледяная Glacier — фирменные цвета Yumi."
                Palette.Green -> "Пастельная мята с фисташковым и абрикосовым."
                Palette.Pink -> "Пастельная сакура с сиреневым и мятным."
                Palette.Blue -> "Пастельное небо с лавандовым и сливочным."
            },
        )

        Group("Остров на фоне")
        Label("Качество")
        Choice(SceneQuality.entries, settings.sceneQuality, { it.label }, { settings.sceneQuality = it })
        Hint(
            when (settings.sceneQuality) {
                SceneQuality.Low -> "Блоки без текстур и простой свет — для слабых компьютеров."
                SceneQuality.Medium -> "Текстуры, мягкий свет в углах, свет от факелов и фонарей, текущая вода и лава."
                SceneQuality.High -> "Плюс тени от солнца и луны, ореолы и блики у огней, блики от солнца, дым, искры, светлячки и далёкие острова."
                SceneQuality.Ultra -> "Всё сразу: лучи света, северное сияние, падающие звёзды, листва на ветру, блики на воде, тени облаков, птицы."
            },
        )
        Label("Кадров в секунду")
        Choice(listOf(30, 60, 120), settings.sceneFps, { it.toString() }, { settings.sceneFps = it })
        Label("Камера")
        Choice(SceneMotion.entries, settings.sceneMotion, { it.label }, { settings.sceneMotion = it })
        Hint(
            when (settings.sceneMotion) {
                SceneMotion.Rotate -> "Остров медленно поворачивается сам."
                SceneMotion.Mouse -> "Камера следует за курсором."
                SceneMotion.Both -> "Остров поворачивается и немного отзывается на курсор."
            },
        )
        Label("Во время игры")
        Choice(listOf(true, false), settings.scenePausedInGame, { if (it) "Останавливать" else "Не останавливать" }, { settings.scenePausedInGame = it })
        Hint(
            if (settings.scenePausedInGame) "Пока идёт Minecraft, остров замирает и не отнимает у игры видеокарту."
            else "Остров живёт и во время игры. На слабом компьютере игра может потерять несколько кадров.",
        )

        Group("Игра")
        Label("Память")
        MemorySlider(settings.memoryMb) { settings.memoryMb = it }
        Hint("Для ванильной игры хватает 2–4 ГБ. Сборке можно задать свою память в её настройках.")

        Label("Java")
        Choice(listOf(false, true), settings.customJava, { if (it) "Своя" else "Автоматически" }, { settings.customJava = it })
        if (!settings.customJava) {
            Hint("Лаунчер сам скачает от Mojang ту Java, которая нужна версии: 8 для старых, 25 для новых.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(
                    value = settings.javaPath,
                    onValueChange = { settings.javaPath = it },
                    label = "Путь к javaw.exe",
                    icon = Icons.Rounded.Coffee,
                    isError = !File(settings.javaPath.trim()).isFile,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { pickJava()?.let { settings.javaPath = it } }, shapes = ButtonDefaults.shapes()) { Text("Обзор") }
            }
        }

        Label("Папка игры")
        GameFolder(model)

        Label("Ключ CurseForge")
        CurseForgeKeyField(
            value = settings.curseForgeKey,
            onValueChange = { settings.curseForgeKey = it.trim() },
            placeholder = "Не задан — моды только с Modrinth",
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("Бесплатный личный ключ: console.curseforge.com → API Keys, начинается с \$2a\$10\$. Modrinth работает без ключа.")

        Label("Настройки Minecraft")
        Choice(listOf(true, false), settings.syncOptions, { if (it) "Общие для сборок" else "У каждой свои" }, { settings.syncOptions = it })
        Hint(
            if (settings.syncOptions) {
                "Клавиши, мышь, звук, графика и язык переходят из сборки в сборку. Наборы ресурсов и окно у каждой свои."
            } else {
                "Каждая сборка помнит свои настройки игры."
            },
        )

        Label("Когда игра запустилась")
        Choice(AfterLaunch.entries, settings.afterLaunch, { it.label }, { settings.afterLaunch = it })
        Hint(
            when (settings.afterLaunch) {
                AfterLaunch.Keep -> if (settings.scenePausedInGame) "Лаунчер останется открытым, а остров замрёт, чтобы не отнимать ресурсы у игры." else "Лаунчер останется открытым, остров — живым."
                AfterLaunch.Minimize -> "Лаунчер свернётся и развернётся обратно, когда вы выйдете из игры."
                AfterLaunch.Close -> "Лаунчер закроется, игра продолжит работать."
            },
        )
        Group("Друзья")
        Label("Что видят друзья")
        Choice(listOf(true, false), settings.friendsShare, { if (it) "Во что и где играю" else "Только в сети я или нет" }, { settings.friendsShare = it })
        Hint(
            if (settings.friendsShare) "Друзья видят название сборки, версию и сервер, на котором вы играете, — и могут зайти к вам одной кнопкой."
            else "Друзья видят только, что вы в лаунчере или в игре.",
        )
        Label("Сервер друзей")
        Field(
            value = settings.friendsServer,
            onValueChange = { settings.friendsServer = it.trim() },
            placeholder = FriendsApi.DEFAULT_URL.ifEmpty { "https://имя-проекта.amvera.io" },
            icon = Icons.Rounded.Dns,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(if (FriendsApi.DEFAULT_URL.isEmpty()) "Адрес сервера друзей на Amvera. У вас и у друзей он должен быть один и тот же." else "Пусто — общий сервер Yumi. Свой адрес нужен, только если вы подняли свой сервер друзей.")
        if (model.friends.signedIn) {
            var confirmDelete by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Профиль: ${settings.friendsName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { if (confirmDelete) { model.friends.deleteProfile(); confirmDelete = false } else confirmDelete = true },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(if (confirmDelete) "Точно удалить?" else "Удалить профиль") }
            }
            Hint("Удаление убирает вас у всех друзей и освобождает имя.")
        }

        Spacer(Modifier.height(4.dp))
        Text("Yumi Launcher 0.1.0", style = type.labelMedium, color = scheme.outline)
    }
}

@Composable
private fun GameFolder(model: LauncherModel) {
    val scheme = MaterialTheme.colorScheme
    val chosen = model.settings.gameDir.isNotBlank()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                if (chosen) model.settings.gameDir else "Не выбрана — спросим при первом скачивании",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (chosen) freeSpace(LauncherPaths.root)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        OutlinedButton(
            onClick = { pickFolder(LauncherPaths.root, "Папка для Minecraft")?.let(model::useGameFolder) },
            // Servers run from the game folder; moving it under a running one would pull the world out from under it.
            enabled = !model.busy && !model.movingPacks && !model.servers.anyActive,
            shapes = ButtonDefaults.shapes(),
        ) { Text(if (chosen) "Сменить" else "Выбрать") }
        if (chosen) {
            OutlinedButton(onClick = { model.openFolder(LauncherPaths.root) }, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Outlined.FolderOpen, "Открыть папку", Modifier.size(18.dp))
            }
        }
    }
    when {
        model.movingPacks -> Hint("Переносим сборки…")
        model.servers.anyActive -> Hint("Остановите серверы, чтобы сменить папку.")
        chosen && LauncherPaths.root.hasNonAscii() -> Text(
            "В пути есть не латинские буквы — версии старше 1.13 могут не запуститься.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.error,
        )
        else -> Hint("При смене папки сборки и серверы с мирами переедут, а версии и Java скачаются заново.")
    }
}

/**
 * The palettes side by side, each as the swatch Material uses for a theme: a circle of its accent
 * over its two quieter colours, drawn in [dark] or light — by default whichever is on now.
 */
@Composable
internal fun PalettePicker(selected: Palette, dark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f, onSelect: (Palette) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Palette.entries.forEach { palette ->
            val colors = colorScheme(palette, dark)
            val picked = palette == selected
            Surface(
                onClick = { onSelect(palette) },
                shape = RoundedCornerShape(22.dp),
                color = if (picked) scheme.surfaceContainerHighest else LocalFieldContainer.current ?: scheme.surfaceContainer,
                border = if (picked) BorderStroke(2.dp, scheme.primary) else null,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton; this.selected = picked },
            ) {
                Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(48.dp)) {
                            drawArc(colors.primary, 180f, 180f, useCenter = true)
                            drawArc(colors.tertiaryContainer, 90f, 90f, useCenter = true)
                            drawArc(colors.secondaryContainer, 0f, 90f, useCenter = true)
                        }
                        if (picked) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(colors.onPrimary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = colors.primary)
                            }
                        }
                    }
                    Text(palette.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** A heading between groups of settings. */
@Composable
private fun Group(title: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

/** A connected button group over [options]; [leading] draws something before each label. */
@Composable
private fun <T> Choice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    leading: @Composable (T) -> Unit = {},
) = ChoiceGroup(options, selected, label, onSelect, leading = leading)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

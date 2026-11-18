package com.yumi.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tour
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.yumi.launcher.settings.LauncherSettings
import com.yumi.launcher.ui.components.Hint
import com.yumi.launcher.ui.components.ShapeTile
import com.yumi.launcher.ui.theme.PopGlassAlpha
import com.yumi.launcher.ui.theme.glass
import kotlin.math.roundToInt

/** What on the screen a step of the guide points at. */
enum class GuideAnchor { Account, Pack, Play, Mods, Packs, Servers, Friends, Downloads, Feedback, Settings }

/** One step: what it points at (nothing — a card in the middle), what it says, and the section it shows. */
private class GuideStep(
    val anchor: GuideAnchor?,
    val icon: ImageVector,
    val title: String,
    val text: String,
    val section: Section = Section.Home,
)

private val STEPS = listOf(
    GuideStep(
        null, Icons.Rounded.Tour, "Добро пожаловать в Lumi",
        "Коротко покажем, что где, — это займёт минуту. Листайте стрелками или кнопкой «Далее», Esc — пропустить.",
    ),
    GuideStep(
        GuideAnchor.Account, Icons.Rounded.Person, "Аккаунт",
        "Играйте под офлайн-ником, с лицензией Microsoft или с аккаунтом Ely.by. Нажмите сюда, чтобы добавить ещё аккаунты и переключаться между ними в один клик.",
    ),
    GuideStep(
        GuideAnchor.Pack, Icons.Rounded.ViewInAr, "Сборка",
        "Сборка — это версия Minecraft со своей папкой: миры, настройки, моды у каждой свои. Здесь выбирается, во что играть.",
    ),
    GuideStep(
        GuideAnchor.Play, Icons.Rounded.PlayArrow, "Играть",
        "Первый запуск скачает игру и Java — лаунчер спросит, куда. Пока идёт загрузка, кнопка показывает прогресс, а нажатие отменяет.",
    ),
    GuideStep(
        GuideAnchor.Friends, Icons.Rounded.Group, "Друзья",
        "Кто из друзей в сети, во что играет и на каком сервере. К другу можно зайти одной кнопкой — лаунчер сам подберёт сборку.",
    ),
    GuideStep(
        GuideAnchor.Mods, Icons.Rounded.Extension, "Моды, шейдеры, ресурспаки",
        "Каталоги Modrinth и CurseForge с категориями, сортировкой и фильтрами. Всё ставится в выбранную сборку в один клик, для шейдеров лаунчер сам предложит Iris.",
        Section.Mods,
    ),
    GuideStep(
        GuideAnchor.Packs, Icons.Rounded.ViewInAr, "Сборки",
        "Готовые сборки из каталога, из файла .mrpack или .zip, или своя с нуля. Плитке сборки можно выбрать значок, цвет и форму.",
        Section.Packs,
    ),
    GuideStep(
        GuideAnchor.Servers, Icons.Rounded.Dns, "Серверы",
        "Свой сервер для друзей прямо на этом компьютере — с модами сборки — и быстрый вход на чужие серверы.",
        Section.Servers,
    ),
    GuideStep(
        GuideAnchor.Downloads, Icons.Outlined.Download, "Загрузки",
        "Всё, что сейчас скачивается — игра, сборки, моды, серверы, — с прогрессом и отменой, и что уже готово.",
    ),
    GuideStep(
        GuideAnchor.Feedback, Icons.Outlined.Feedback, "Обратная связь",
        "Нашли ошибку или придумали, как сделать лучше? Напишите разработчику в Telegram или отправьте лог — лаунчер соберёт его сам.",
    ),
    GuideStep(
        GuideAnchor.Settings, Icons.Outlined.Settings, "Настройки",
        "Десять цветовых тем, светлая и тёмная, качество острова — на «Ультра» живые облака, звёздное небо и туман, — память и Java.",
    ),
    GuideStep(
        null, Icons.Rounded.Celebration, "Готово!",
        "Теперь вы знаете, что где. Пройти обучение ещё раз можно в «Настройках». Приятной игры!",
    ),
)

/** Where the guide is — or null when it is not on — and where the things it points at are on screen. */
class GuideState {
    var step by mutableStateOf<Int?>(null)
        internal set
    val anchors = mutableStateMapOf<GuideAnchor, Rect>()

    fun start(state: LauncherState) {
        step = 0
        state.section = Section.Home
    }
}

/** Tells the guide where this is on screen, for the steps that point at [anchor]. */
fun Modifier.guide(state: LauncherState, anchor: GuideAnchor): Modifier =
    onGloballyPositioned { state.guide.anchors[anchor] = it.boundsInRoot() }

/**
 * The newcomer's guide, over the whole window: the window dimmed except for the one thing the step
 * is about — ringed in the accent, gently pulsing — and a card beside it that says what it is. Steps
 * about a section open that section, so the camera flies there as it would. Starts by itself on the
 * first launch; "Пропустить" or finishing it marks it done.
 */
@Composable
fun GuideOverlay(state: LauncherState, settings: LauncherSettings) {
    val guide = state.guide
    val index = guide.step ?: return
    val step = STEPS[index.coerceIn(0, STEPS.lastIndex)]
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val finish = {
        settings.guideDone = true
        guide.step = null
        state.section = Section.Home
    }
    val go: (Int) -> Unit = { next ->
        if (next > STEPS.lastIndex) finish() else if (next >= 0) guide.step = next
    }
    LaunchedEffect(index) { state.section = step.section }

    // The hole: the anchor grown by a margin; with nothing to point at, a dot in the middle that grows into the next one.
    val margin = with(density) { 8.dp.toPx() }
    val target = step.anchor?.let(guide.anchors::get)?.let { Rect(it.left - margin, it.top - margin, it.right + margin, it.bottom + margin) }
    var window by remember { mutableStateOf(Size.Zero) }
    val center = Rect(Offset(window.width / 2, window.height / 2), 0f)
    val hole by animateRectAsState(target ?: center, spring(dampingRatio = 0.78f, stiffness = 260f))
    val pulse by rememberInfiniteTransition().animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { window = it.toSize() }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.Enter, Key.Spacebar -> { go(index + 1); true }
                    Key.DirectionLeft -> { go(index - 1); true }
                    Key.Escape -> { finish(); true }
                    else -> false
                }
            }
            // Swallows clicks: the guide is read first, the launcher used after.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = CornerRadius(with(density) { 24.dp.toPx() })
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (hole.width > 1f) addRoundRect(RoundRect(hole, radius))
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.58f))
            if (hole.width > 1f) {
                drawRoundRect(scheme.primary, hole.topLeft, hole.size, radius, style = Stroke(with(density) { 3.dp.toPx() }))
                val grow = with(density) { (4 + 6 * pulse).dp.toPx() }
                drawRoundRect(
                    scheme.primary.copy(alpha = 0.5f * (1 - pulse) + 0.1f),
                    Offset(hole.left - grow, hole.top - grow),
                    Size(hole.width + grow * 2, hole.height + grow * 2),
                    CornerRadius(radius.x + grow),
                    style = Stroke(with(density) { 2.dp.toPx() }),
                )
            }
        }
        val gap = with(density) { 18.dp.toPx() }
        val edge = with(density) { 16.dp.toPx() }
        Layout(content = { GuideCard(step, index, onBack = { go(index - 1) }, onNext = { go(index + 1) }, onSkip = finish) }) { measurables, constraints ->
            val card = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val spot = target
                val (x, y) = when {
                    spot == null -> (w - card.width) / 2 to (h - card.height) / 2
                    // Beside a tall thing on the left — under it there is no room.
                    spot.bottom + gap + card.height > h - edge && spot.right + gap + card.width < w - edge ->
                        spot.right + gap to (spot.center.y - card.height / 2)
                    spot.bottom + gap + card.height <= h - edge -> (spot.center.x - card.width / 2) to spot.bottom + gap
                    else -> (spot.center.x - card.width / 2) to spot.top - gap - card.height
                }
                card.place(
                    x.coerceIn(edge, (w - card.width - edge).coerceAtLeast(edge)).roundToInt(),
                    y.coerceIn(edge, (h - card.height - edge).coerceAtLeast(edge)).roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun GuideCard(step: GuideStep, index: Int, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val last = index == STEPS.lastIndex
    Column(
        Modifier.width(380.dp).glass(RoundedCornerShape(30.dp), alpha = PopGlassAlpha, elevation = 24.dp).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedContent(index, transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(80)) }) { shown ->
            val current = STEPS[shown]
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeTile(current.icon, scheme.primaryContainer, scheme.onPrimaryContainer, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Hint(if (shown == 0 || shown == STEPS.lastIndex) "Знакомство с лаунчером" else "Шаг $shown из ${STEPS.size - 2}")
                        Text(current.title, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text(current.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Progress(index)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!last) TextButton(onClick = onSkip) { Text("Пропустить") }
            Spacer(Modifier.weight(1f))
            if (index > 0 && !last) {
                OutlinedButton(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Назад") }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = onNext, shapes = ButtonDefaults.shapes()) {
                Text(
                    when {
                        index == 0 -> "Начать"
                        last -> "Играть!"
                        else -> "Далее"
                    },
                )
            }
        }
    }
}

/** A dot per step; the current one stretched into a pill, as Expressive page indicators do. */
@Composable
private fun Progress(index: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        STEPS.indices.forEach { i ->
            val width by animateDpAsState(if (i == index) 22.dp else 7.dp, spring(dampingRatio = 0.6f, stiffness = 500f))
            Box(
                Modifier.height(7.dp).width(width).background(
                    if (i <= index) scheme.primary else scheme.outlineVariant,
                    CircleShape,
                ),
            )
        }
    }
}

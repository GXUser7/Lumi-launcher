package com.yumi.launcher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.yumi.launcher.settings.Palette
import com.yumi.launcher.ui.components.LANTERN
import com.yumi.launcher.ui.components.lanternColor
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The launcher's look carried into the game: a resource pack, built for one palette and theme,
 * that repaints the title screen and the settings — buttons, sliders, text fields, checkboxes,
 * tabs, scroll bars, the glass behind menus, tooltips and toasts — over a panorama of its own
 * ([MenuPanorama]).
 *
 * It replaces only the game's GUI sprites and textures, with the nine-slice metadata the game
 * reads since 1.20.2: no shaders, fonts, models or OptiFine features. That is what keeps it out of
 * Sodium's and Iris's way — they replace how the world is drawn, and the packs that fight them are
 * the ones that bring core shaders. Sodium's video settings draw their own flat controls and keep
 * them; they sit on this pack's glass like any other screen.
 *
 * Minecraft writes every label in white, so no surface here is white: the light theme has the
 * launcher's filled accent buttons over tinted glass, the dark one tonal buttons over dark glass.
 * Sprites are painted at four times the game's GUI pixel — one texel per screen pixel at the
 * automatic GUI scale on a 1080p screen — so the rounded shapes stay smooth.
 */
object MenuPack {
    /** The oldest pack format the sprites work in: 1.20.2, when the GUI moved to sprites. */
    const val MIN_FORMAT = 18

    /** What the pack's zip says about itself, so the launcher knows its own file in a game folder. */
    const val COMMENT = "Lumi Launcher"

    /** The newest resource pack format this launcher knows: 26.3. */
    const val KNOWN_FORMAT = 97

    /** What the pack is called in the game's list: the palette and the theme. */
    fun title(palette: Palette, dark: Boolean) = "Lumi — ${palette.label}, ${if (dark) "тёмная" else "светлая"}"

    /** The name of the font-only add-on in the game's list. */
    const val FONT_TITLE = "Lumi — шрифт лаунчера"

    /**
     * The pack as a zip. With [font] it also brings the launcher's typeface; [maxFormat] is the newest
     * game it claims — a newer game writes its own number in.
     */
    fun build(palette: Palette, dark: Boolean, font: Boolean = false, maxFormat: Int = KNOWN_FORMAT): ByteArray {
        val scheme = colorScheme(palette, dark)
        val kit = Kit(scheme, dark)
        val files = linkedMapOf<String, ByteArray>()
        val theme = if (dark) "тёмная" else "светлая"
        val subtitle = "меню в стиле лаунчера\\n${palette.label}, $theme" + if (font) ", шрифт лаунчера" else ""
        files["pack.mcmeta"] = mcmeta(accent(scheme, dark), subtitle, maxFormat).toByteArray()
        files["pack.png"] = icon(scheme.primary, scheme.onPrimary)
        sprites(kit).forEach { (path, png) -> files["assets/minecraft/textures/gui/$path"] = png }
        MenuPanorama.faces(palette, dark).forEachIndexed { i, png -> files["assets/minecraft/textures/gui/title/background/panorama_$i.png"] = png }
        if (font) files += fontFiles()
        return zip(files)
    }

    /**
     * The launcher's typeface on its own, to lay over any preset — or over any other pack. It is
     * separate because Minecraft has one font for everything: chat, signs and books change with it.
     */
    fun buildFont(maxFormat: Int = KNOWN_FORMAT): ByteArray {
        val scheme = colorScheme(Palette.Original, dark = true)
        val files = linkedMapOf<String, ByteArray>()
        files["pack.mcmeta"] = mcmeta(accent(scheme, dark = true), "шрифт лаунчера, Roboto Flex\\nвключайте поверх оформления", maxFormat).toByteArray()
        files["pack.png"] = icon(scheme.primaryContainer, scheme.onPrimaryContainer)
        files += fontFiles()
        return zip(files)
    }

    private fun accent(scheme: ColorScheme, dark: Boolean) = hex(if (dark) scheme.primary else scheme.inversePrimary)

    private fun mcmeta(accent: String, subtitle: String, maxFormat: Int): String {
        val max = maxOf(maxFormat, MIN_FORMAT)
        // pack_format and supported_formats for 1.20.2–1.21.8; min_format and max_format from 1.21.9,
        // which still wants the old pair while the range reaches below 65.
        return """
            {
              "pack": {
                "description": {"text": "Lumi", "color": "$accent", "extra": [{"text": " · $subtitle", "color": "#C9CFD9"}]},
                "pack_format": $MIN_FORMAT,
                "supported_formats": [$MIN_FORMAT, $max],
                "min_format": $MIN_FORMAT,
                "max_format": $max
              }
            }
        """.trimIndent() + "\n"
    }

    /**
     * Roboto Flex SemiBold ahead of the game's own pixel font, which stays behind it for whatever
     * the subset lacks — accented Latin beyond the basics, Greek, symbols. The typeface's own space
     * comes first too: the game's is a full four pixels and pulls words apart. Drawn at four times
     * the size, like the sprites, so it stays sharp at the usual GUI scale.
     */
    private fun fontFiles(): Map<String, ByteArray> {
        val ttf = MenuPack::class.java.getResourceAsStream("/font/roboto_flex_semibold.ttf")!!.use { it.readBytes() }
        val json = """
            {
              "providers": [
                {"type": "ttf", "file": "yumi:ui.ttf", "size": 9.0, "oversample": 4.0, "shift": [0.0, 0.0]},
                {"type": "reference", "id": "minecraft:include/space"},
                {"type": "reference", "id": "minecraft:include/default", "filter": {"uniform": false}},
                {"type": "reference", "id": "minecraft:include/unifont"}
              ]
            }
        """.trimIndent() + "\n"
        return mapOf("assets/yumi/font/ui.ttf" to ttf, "assets/minecraft/font/default.json" to json.toByteArray())
    }

    /** The launcher's badge: the Lumi lantern on a round plate, six texels a lantern pixel. */
    private fun icon(plate: Color, mark: Color): ByteArray = paint(128, 128, 1) {
        drawCircle(64f, 64f, 64f, fill(plate))
        val unit = 6f
        val start = (128f - LANTERN.size * unit) / 2
        LANTERN.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                lanternColor(cell, mark)?.let { drawRect(Rect.makeXYWH(start + x * unit, start + y * unit, unit, unit), fill(it)) }
            }
        }
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setComment(COMMENT)
            for ((path, bytes) in files) {
                // A fixed date: the same pack builds into the same bytes, so an unchanged one is never rewritten.
                zip.putNextEntry(ZipEntry(path).apply { time = STAMP })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private const val STAMP = 1767225600000L
}

/**
 * The pack's colours, all taken from the launcher's scheme for the palette. Every surface that
 * carries a label is dark enough for the game's white text, in both themes.
 */
private class Kit(s: ColorScheme, dark: Boolean) {
    val glass: Color
    val listGlass: Color
    val divider: Color
    val shadow: Color
    val shadowTop: Float
    val shadowBottom: Float
    val button: Color
    val buttonHover: Color
    val buttonOff: Color
    val ring: Color
    val glyph: Color
    val glyphOff: Color
    val track: Color
    val trackHover: Color
    val trackEdge: Color
    val handle: Color
    val handleHover: Color
    val field: Color
    val fieldEdge: Color
    val box: Color
    val boxEdge: Color
    val checked: Color
    val checkedHover: Color
    val check: Color
    val thumb: Color
    val rail: Color
    val tabPicked: Color
    val tabHover: Color
    val tooltip: Color
    val tooltipEdge: Color
    val toast: Color
    val toastEdge: Color
    val paper: Color
    val paperEdge: Color

    init {
        if (!dark) {
            // Glass tinted with the accent's deepest tone over the bright day sky.
            glass = s.onPrimaryContainer.copy(alpha = 0.38f)
            listGlass = s.onPrimaryContainer.copy(alpha = 0.36f)
            divider = s.primaryContainer.copy(alpha = 0.55f)
            shadow = s.onPrimaryContainer
            shadowTop = 0.32f
            shadowBottom = 0.55f
            // The launcher's filled buttons: the accent with white on it.
            button = s.primary
            buttonHover = lerp(s.primary, Color.White, 0.2f)
            buttonOff = s.inverseSurface.copy(alpha = 0.55f)
            ring = s.primaryContainer
            glyph = s.onPrimary
            glyphOff = Color(0xFFA0A0A0)
            track = s.onPrimaryContainer.copy(alpha = 0.78f)
            trackHover = lerp(s.onPrimaryContainer, s.primary, 0.3f).copy(alpha = 0.86f)
            trackEdge = s.primaryContainer.copy(alpha = 0.3f)
            handle = s.primaryContainer
            handleHover = Color.White
            field = s.inverseSurface.copy(alpha = 0.82f)
            fieldEdge = s.outline
            box = s.inverseSurface.copy(alpha = 0.5f)
            boxEdge = s.outlineVariant
            checked = s.primary
            checkedHover = lerp(s.primary, Color.White, 0.16f)
            check = s.onPrimary
            thumb = s.primaryContainer.copy(alpha = 0.9f)
            rail = Color.White.copy(alpha = 0.1f)
            tabPicked = s.primaryContainer.copy(alpha = 0.2f)
            tabHover = Color.White.copy(alpha = 0.1f)
            // Dark tooltips on light, as Material's plain tooltip is: the inverse surface.
            tooltip = s.inverseSurface.copy(alpha = 0.96f)
            tooltipEdge = s.inversePrimary.copy(alpha = 0.55f)
            toast = s.inverseSurface.copy(alpha = 0.97f)
            toastEdge = s.inversePrimary.copy(alpha = 0.5f)
            paper = s.surfaceContainerLowest
            paperEdge = s.outlineVariant
        } else {
            glass = s.surfaceContainerLowest.copy(alpha = 0.42f)
            listGlass = s.surface.copy(alpha = 0.45f)
            divider = s.outline.copy(alpha = 0.8f)
            shadow = s.surfaceContainerLowest
            shadowTop = 0.2f
            shadowBottom = 0.4f
            // Tonal buttons: the accent's container, lit with the accent on hover.
            button = s.primaryContainer
            buttonHover = lerp(s.primaryContainer, s.primary, 0.2f)
            buttonOff = s.surfaceContainerHigh.copy(alpha = 0.6f)
            ring = s.primary
            glyph = s.onPrimaryContainer
            glyphOff = Color(0xFFA0A0A0)
            track = s.surfaceContainerHighest.copy(alpha = 0.9f)
            trackHover = lerp(s.surfaceContainerHighest, s.primary, 0.12f).copy(alpha = 0.92f)
            trackEdge = s.outlineVariant
            handle = s.primary
            handleHover = s.onPrimaryContainer
            field = s.surfaceContainerLowest.copy(alpha = 0.85f)
            fieldEdge = s.outline
            box = s.surfaceContainerLowest.copy(alpha = 0.6f)
            boxEdge = s.onSurfaceVariant
            checked = s.primary
            checkedHover = s.onPrimaryContainer
            check = s.onPrimary
            thumb = s.primary.copy(alpha = 0.85f)
            rail = Color.White.copy(alpha = 0.06f)
            tabPicked = s.primary.copy(alpha = 0.16f)
            tabHover = Color.White.copy(alpha = 0.07f)
            tooltip = s.surfaceContainerHigh.copy(alpha = 0.97f)
            tooltipEdge = s.primary.copy(alpha = 0.5f)
            toast = s.surfaceContainerHigh
            toastEdge = s.primary.copy(alpha = 0.45f)
            paper = s.inverseSurface
            paperEdge = s.outline
        }
    }
}

/** How many texels a GUI pixel gets. */
private const val SCALE = 4

/** Every sprite and texture of the pack, by path under `textures/gui`, with its metadata beside it. */
private fun sprites(k: Kit): Map<String, ByteArray> {
    val out = linkedMapOf<String, ByteArray>()
    fun sprite(name: String, w: Int, h: Int, border: String?, draw: Canvas.() -> Unit) {
        out["sprites/$name.png"] = paint(w, h, SCALE, draw)
        if (border != null) out["sprites/$name.png.mcmeta"] = nineSlice(w, h, border)
    }

    // Buttons: pills. The border stops a row short of half the height, so the middle keeps a strip
    // to stretch — the game refuses a nine-slice with nothing in the middle when a button is taller.
    sprite("widget/button", 200, 20, "9") { pill(200f, 20f, k.button) }
    sprite("widget/button_highlighted", 200, 20, "9") { pill(200f, 20f, k.buttonHover, k.ring) }
    sprite("widget/button_disabled", 200, 20, "9") { pill(200f, 20f, k.buttonOff) }

    // The difficulty lock beside its button: a round button of the same kind with a padlock on it.
    for ((state, open) in listOf("locked" to false, "unlocked" to true)) {
        sprite("widget/${state}_button", 20, 20, null) { lock(k.button, k.glyph, null, open) }
        sprite("widget/${state}_button_highlighted", 20, 20, null) { lock(k.buttonHover, k.glyph, k.ring, open) }
        sprite("widget/${state}_button_disabled", 20, 20, null) { lock(k.buttonOff, k.glyphOff, null, open) }
    }

    // Sliders: a deeper track under the value, and Material's upright bar as the handle. The bar is
    // short enough to stay inside the track's round end when the value is at either extreme.
    sprite("widget/slider", 200, 20, "9") { pill(200f, 20f, k.track, k.trackEdge) }
    sprite("widget/slider_highlighted", 200, 20, "9") { pill(200f, 20f, k.trackHover, k.ring) }
    sprite("widget/slider_handle", 8, 20, "3") { drawRRect(RRect.makeLTRB(2.5f, 4.5f, 5.5f, 15.5f, 1.5f), fill(k.handle)) }
    sprite("widget/slider_handle_highlighted", 8, 20, "3") { drawRRect(RRect.makeLTRB(2f, 4f, 6f, 16f, 2f), fill(k.handleHover)) }

    // Text fields: rounded, not round, so they never pass for a button.
    sprite("widget/text_field", 200, 20, "6") { field(k.field, k.fieldEdge, 1f) }
    sprite("widget/text_field_highlighted", 200, 20, "6") { field(k.field, k.ring, 1.5f) }

    // Checkboxes are stretched to the box, not sliced.
    sprite("widget/checkbox", 20, 20, null) { box(k.box, k.boxEdge) }
    sprite("widget/checkbox_highlighted", 20, 20, null) { box(k.box, k.ring) }
    sprite("widget/checkbox_selected", 20, 20, null) { checked(k.checked, k.check, null) }
    sprite("widget/checkbox_selected_highlighted", 20, 20, null) { checked(k.checkedHover, k.check, k.ring) }

    sprite("widget/scroller", 6, 32, """{"left": 1, "top": 3, "right": 1, "bottom": 3}""") {
        drawRRect(RRect.makeLTRB(1.5f, 0.5f, 4.5f, 31.5f, 1.5f), fill(k.thumb))
    }
    sprite("widget/scroller_background", 6, 32, """{"left": 1, "top": 3, "right": 1, "bottom": 3}""") {
        drawRRect(RRect.makeLTRB(1.5f, 0.5f, 4.5f, 31.5f, 1.5f), fill(k.rail))
    }

    // Tabs: no box, a tonal backing for the picked one. The game underlines the picked tab's label
    // itself, so a bar of the pack's own would be a second underline.
    val tabBorder = """{"left": 6, "top": 6, "right": 6, "bottom": 1}"""
    sprite("widget/tab", 130, 24, tabBorder) {}
    sprite("widget/tab_highlighted", 130, 24, tabBorder) { tabTop(k.tabHover) }
    sprite("widget/tab_selected", 130, 24, tabBorder) { tabTop(k.tabPicked) }
    sprite("widget/tab_selected_highlighted", 130, 24, tabBorder) { tabTop(k.tabPicked); tabTop(k.tabHover) }

    // Tooltips: the game draws them 12 GUI pixels out from the text; the card starts 8 in.
    sprite("tooltip/background", 100, 100, "13") { drawRRect(RRect.makeXYWH(8f, 8f, 84f, 84f, 4f), fill(k.tooltip)) }
    sprite("tooltip/frame", 100, 100, "13") { drawRRect(RRect.makeXYWH(8.5f, 8.5f, 83f, 83f, 3.5f), stroke(k.tooltipEdge, 1f)) }

    // Toasts: dark cards for white text, light "paper" for the recipe and tutorial ones, whose text is dark.
    sprite("toast/system", 160, 64, "8") { card(160f, 64f, k.toast, k.toastEdge) }
    sprite("toast/now_playing", 160, 32, "8") { card(160f, 32f, k.toast, k.toastEdge) }
    sprite("toast/advancement", 160, 32, null) { card(160f, 32f, k.toast, k.toastEdge) }
    sprite("toast/recipe", 160, 32, null) { card(160f, 32f, k.paper, k.paperEdge) }
    sprite("toast/tutorial", 160, 32, "8") { card(160f, 32f, k.paper, k.paperEdge) }

    // The glass behind menus, on the title screen and over a paused world. The game tiles it.
    out["menu_background.png"] = paint(16, 16, 1) { clear(k.glass.toArgb()) }
    out["menu_list_background.png"] = paint(16, 16, 1) { clear(k.listGlass.toArgb()) }
    out["inworld_menu_background.png"] = paint(16, 16, 1) { clear(k.glass.toArgb()) }
    out["inworld_menu_list_background.png"] = paint(16, 16, 1) { clear(k.listGlass.toArgb()) }
    // Hairlines between a list and the header and footer round it: the lower row of the header's, the upper of the footer's.
    for (prefix in listOf("", "inworld_")) {
        out["${prefix}header_separator.png"] = paint(32, 2, 1) { drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, 1f, 32f, 1f), fill(k.divider)) }
        out["${prefix}footer_separator.png"] = paint(32, 2, 1) { drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, 0f, 32f, 1f), fill(k.divider)) }
    }
    // Over the panorama: shade under the logo and, more, under the version line and the copyright at the bottom.
    out["title/background/panorama_overlay.png"] = paint(16, 256, 1) {
        for (y in 0 until 256) {
            val f = y / 255f
            val a = k.shadowTop * smooth(1f - f / 0.24f) + k.shadowBottom * smooth((f - 0.7f) / 0.3f)
            drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, y.toFloat(), 16f, 1f), fill(k.shadow.copy(alpha = a.coerceIn(0f, 1f))))
        }
    }
    return out
}

private fun nineSlice(w: Int, h: Int, border: String) =
    """{"gui": {"scaling": {"type": "nine_slice", "width": $w, "height": $h, "border": $border}}}""".toByteArray()

private fun Canvas.pill(w: Float, h: Float, color: Color, edge: Color? = null) {
    drawRRect(RRect.makeXYWH(0f, 0f, w, h, h / 2), fill(color))
    if (edge != null) drawRRect(RRect.makeXYWH(0.5f, 0.5f, w - 1f, h - 1f, h / 2 - 0.5f), stroke(edge, 1f))
}

private fun Canvas.lock(color: Color, glyph: Color, ring: Color?, open: Boolean) {
    pill(20f, 20f, color, ring)
    // The shackle, lifted and free on the right when the lock is open.
    val lift = if (open) 1.6f else 0f
    val shackle = PathBuilder()
        .moveTo(7.9f, 9.6f)
        .lineTo(7.9f, 7.4f - lift)
        .cubicTo(7.9f, 4.6f - lift, 12.1f, 4.6f - lift, 12.1f, 7.4f - lift)
        .lineTo(12.1f, if (open) 7.4f - lift else 9.6f)
        .detach()
    drawPath(shackle, stroke(glyph, 1.4f).apply { strokeCap = PaintStrokeCap.ROUND })
    drawRRect(RRect.makeLTRB(6.2f, 9.2f, 13.8f, 15.4f, 1.4f), fill(glyph))
}

private fun Canvas.field(color: Color, edge: Color, width: Float) {
    drawRRect(RRect.makeXYWH(0f, 0f, 200f, 20f, 5f), fill(color))
    val i = width / 2
    drawRRect(RRect.makeXYWH(i, i, 200f - width, 20f - width, 5f - i), stroke(edge, width))
}

private fun Canvas.box(color: Color, edge: Color) {
    drawRRect(RRect.makeXYWH(1.5f, 1.5f, 17f, 17f, 4.5f), fill(color))
    drawRRect(RRect.makeXYWH(2.25f, 2.25f, 15.5f, 15.5f, 3.75f), stroke(edge, 1.5f))
}

private fun Canvas.checked(color: Color, mark: Color, ring: Color?) {
    drawRRect(RRect.makeXYWH(1f, 1f, 18f, 18f, 5f), fill(color))
    if (ring != null) drawRRect(RRect.makeXYWH(1.5f, 1.5f, 17f, 17f, 4.5f), stroke(ring, 1f))
    val tick = PathBuilder().moveTo(5.6f, 10.4f).lineTo(8.6f, 13.4f).lineTo(14.4f, 6.8f).detach()
    drawPath(tick, stroke(mark, 2.2f).apply { strokeCap = PaintStrokeCap.ROUND; strokeJoin = PaintStrokeJoin.ROUND })
}

private fun Canvas.tabTop(color: Color) = drawRRect(RRect.makeComplexXYWH(0f, 0f, 130f, 24f, floatArrayOf(5f, 5f, 5f, 5f, 0f, 0f, 0f, 0f)), fill(color))

private fun Canvas.card(w: Float, h: Float, color: Color, edge: Color) {
    drawRRect(RRect.makeXYWH(0f, 0f, w, h, 6f), fill(color))
    drawRRect(RRect.makeXYWH(0.5f, 0.5f, w - 1f, h - 1f, 5.5f), stroke(edge, 1f))
}

/** A [w]×[h] picture drawn in GUI pixels at [scale] texels each, as a PNG. */
private fun paint(w: Int, h: Int, scale: Int, draw: Canvas.() -> Unit): ByteArray {
    val surface = Surface.makeRasterN32Premul(w * scale, h * scale)
    surface.canvas.scale(scale.toFloat(), scale.toFloat())
    surface.canvas.draw()
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}

private fun fill(color: Color) = Paint().apply { isAntiAlias = true; this.color = color.toArgb() }

private fun stroke(color: Color, width: Float) = Paint().apply {
    isAntiAlias = true
    mode = PaintMode.STROKE
    strokeWidth = width
    this.color = color.toArgb()
}

private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

private fun smooth(x: Float): Float {
    val k = x.coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

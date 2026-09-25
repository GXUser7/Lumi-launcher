package com.yumi.launcher.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * What a catalogue project is, and where its file goes in a pack's game folder. Modrinth calls
 * the kinds project types, CurseForge classes.
 */
enum class ContentType(val label: String, val folder: String, val modrinth: String, val curseClass: Int) {
    Mod("Моды", "mods", "mod", 6),
    Shader("Шейдеры", "shaderpacks", "shader", 6552),
    ResourcePack("Ресурспаки", "resourcepacks", "resourcepack", 12),
    Modpack("Сборки", "", "modpack", 4471),
}

/** How a catalogue orders what it finds; CurseForge has no relevance, so its most popular stand in. */
enum class SortOrder(val label: String, val modrinth: String, val curse: Int) {
    Relevance("Подходящие", "relevance", 2),
    Downloads("Популярные", "downloads", 6),
    Updated("Обновлённые", "updated", 3),
    Newest("Новые", "newest", 11),
}

/** A category a catalogue files projects under. [group] is the filter it belongs to — Modrinth's header. */
data class Category(val id: String, val label: String, val group: String)

/** The filters of a search: the order, and at most one category from each group (group → category id). */
data class SearchFilter(
    val sort: SortOrder = SortOrder.Relevance,
    val categories: Map<String, String> = emptyMap(),
    /** Shaders and resource packs often work on versions they never listed; this drops the version from the search. */
    val anyVersion: Boolean = false,
) {
    val active: Boolean get() = sort != SortOrder.Relevance || categories.isNotEmpty() || anyVersion
}

/**
 * The categories each catalogue has for each kind of project, asked for once per run: Modrinth's
 * tag list, CurseForge's categories of the class. Names are shown in Russian where we know them,
 * as the catalogue writes them otherwise.
 */
object Categories {
    private val cache = ConcurrentHashMap<String, List<Category>>()

    /** The filter groups in the order they are shown, with their names. */
    val groups = linkedMapOf(
        "categories" to "Категория",
        "features" to "Особенности",
        "resolutions" to "Разрешение",
        "performance impact" to "Для компьютера",
    )

    suspend fun of(source: ModSource, type: ContentType, key: String): List<Category> {
        val id = "$source:$type"
        cache[id]?.let { return it }
        val list = when (source) {
            ModSource.Modrinth -> modrinth().filter { it.first == type.modrinth }.map { it.second }
            ModSource.CurseForge -> if (key.isBlank()) return emptyList() else curse(type, key)
        }
        return list.also { cache[id] = it }
    }

    /** Every tag Modrinth has, as (project type, category); one request covers all kinds. */
    private suspend fun modrinth(): List<Pair<String, Category>> {
        val tags = LauncherJson.parseToJsonElement(Http.text("https://api.modrinth.com/v2/tag/category")).jsonArray.map { it.jsonObject }
        return tags.mapNotNull { o ->
            val name = o.str("name") ?: return@mapNotNull null
            val header = o.str("header") ?: "categories"
            val type = o.str("project_type") ?: return@mapNotNull null
            type to Category(name, label(name), header)
        }.sortedWith(compareBy({ order(it.second) }, { it.second.label }))
    }

    private suspend fun curse(type: ContentType, key: String): List<Category> {
        val data = CurseForge.get("https://api.curseforge.com/v1/categories?gameId=432&classId=${type.curseClass}", key)["data"]!!.jsonArray
        return data.map { it.jsonObject }
            // The top level of the class; the finer ones under them would make the menu a page long.
            .filter { it.long("parentCategoryId") == type.curseClass.toLong() && it.long("id") != type.curseClass.toLong() }
            .mapNotNull { o ->
                val name = o.str("name") ?: return@mapNotNull null
                Category(o.long("id").toString(), CURSE[name.lowercase()] ?: name, "categories")
            }
            .sortedBy { it.label }
    }

    /** Resolutions and performance levels in their natural order, the rest alphabetically. */
    private fun order(category: Category): Int = ORDERED.indexOf(category.id).let { if (it < 0) Int.MAX_VALUE else it }

    fun label(slug: String): String = MODRINTH[slug] ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

    private val ORDERED = listOf("8x-", "16x", "32x", "48x", "64x", "128x", "256x", "512x+", "potato", "low", "medium", "high", "screenshot")

    private val MODRINTH = mapOf(
        "8x-" to "8x и меньше",
        "512x+" to "512x и больше",
        // Mods and modpacks
        "adventure" to "Приключения",
        "cursed" to "Странное",
        "decoration" to "Декор",
        "economy" to "Экономика",
        "equipment" to "Снаряжение",
        "food" to "Еда",
        "game-mechanics" to "Механики",
        "library" to "Библиотеки",
        "magic" to "Магия",
        "management" to "Управление",
        "minigame" to "Мини-игры",
        "mobs" to "Мобы",
        "optimization" to "Оптимизация",
        "social" to "Общение",
        "storage" to "Хранение",
        "technology" to "Технологии",
        "transportation" to "Транспорт",
        "utility" to "Утилиты",
        "worldgen" to "Генерация мира",
        "challenging" to "Хардкор",
        "combat" to "Бой",
        "kitchen-sink" to "Всё и сразу",
        "lightweight" to "Лёгкие",
        "multiplayer" to "Для сервера",
        "quests" to "Квесты",
        // Resource packs
        "modded" to "Для модов",
        "realistic" to "Реализм",
        "simplistic" to "Минимализм",
        "themed" to "Тематические",
        "tweaks" to "Мелкие правки",
        "vanilla-like" to "Как в ванилле",
        "audio" to "Звуки",
        "blocks" to "Блоки",
        "core-shaders" to "Шейдеры ядра",
        "entities" to "Существа",
        "environment" to "Окружение",
        "fonts" to "Шрифты",
        "gui" to "Интерфейс",
        "items" to "Предметы",
        "locale" to "Язык",
        "models" to "Модели",
        // Shaders
        "cartoon" to "Мультяшные",
        "fantasy" to "Фэнтези",
        "semi-realistic" to "Полуреализм",
        "atmosphere" to "Атмосфера",
        "bloom" to "Свечение",
        "colored-lighting" to "Цветной свет",
        "foliage" to "Листва на ветру",
        "path-tracing" to "Трассировка пути",
        "pbr" to "PBR-материалы",
        "reflections" to "Отражения",
        "shadows" to "Тени",
        "potato" to "Очень слабый",
        "low" to "Слабый",
        "medium" to "Средний",
        "high" to "Мощный",
        "screenshot" to "Для скриншотов",
    )

    private val CURSE = mapOf(
        "adventure and rpg" to "Приключения и RPG",
        "api and library" to "Библиотеки",
        "armor, tools, and weapons" to "Броня, инструменты, оружие",
        "bug fixes" to "Исправления",
        "cosmetic" to "Косметика",
        "education" to "Обучение",
        "food" to "Еда",
        "magic" to "Магия",
        "map and information" to "Карты и информация",
        "miscellaneous" to "Разное",
        "redstone" to "Редстоун",
        "server utility" to "Для серверов",
        "storage" to "Хранение",
        "technology" to "Технологии",
        "utility & qol" to "Удобства",
        "world gen" to "Генерация мира",
        "mobs" to "Мобы",
        "performance" to "Оптимизация",
        "addons" to "Аддоны",
        "twitch integration" to "Twitch",
        "mcreator" to "MCreator",
        "realistic" to "Реализм",
        "fantasy" to "Фэнтези",
        "vanilla" to "Как в ванилле",
        "medieval" to "Средневековье",
        "modern" to "Современность",
        "steampunk" to "Стимпанк",
        "animated" to "Анимированные",
        "photo realistic" to "Фотореализм",
        "traditional" to "Традиционные",
        "mod support" to "Для модов",
        "font packs" to "Шрифты",
        "data packs" to "Датапаки",
        "16x" to "16x", "32x" to "32x", "64x" to "64x", "128x" to "128x", "256x" to "256x", "512x and higher" to "512x+",
        "exploration" to "Исследование",
        "extra large" to "Огромные",
        "hardcore" to "Хардкор",
        "map based" to "На карте",
        "mini game" to "Мини-игры",
        "multiplayer" to "Для сервера",
        "quests" to "Квесты",
        "sci-fi" to "Научная фантастика",
        "skyblock" to "Скайблок",
        "small / light" to "Лёгкие",
        "combat / pvp" to "Бой и PvP",
        "horror" to "Хоррор",
        "vanilla+" to "Ванилла+",
        "fabric" to "Fabric",
        "forge" to "Forge",
        "quilt" to "Quilt",
        "neoforge" to "NeoForge",
    )
}

package com.yumi.launcher.ui.servers

import com.yumi.launcher.core.Loader

/**
 * The console commands the terminal suggests while a command is typed, as a small tree: each word
 * knows what may follow it. Enough of vanilla to run a server for friends, with a line of Russian on
 * each — the server's own `help` lists everything, in English and without a word of explanation.
 */
internal class CommandNode(
    val word: String,
    val description: String,
    /** What is still to type after this word: "<игрок> [причина]". */
    val usage: String = "",
    val next: List<CommandNode> = emptyList(),
    /** The next word is a player's name, offered from [Arg]. */
    val then: Arg = Arg.None,
    /** What comes after that name, if anything. */
    val afterName: String = "",
)

internal enum class Arg { None, Online, Banned }

/** One line of the suggestion list. */
internal class Suggestion(
    /** The whole input with this suggestion taken; null for a line that only explains the syntax. */
    val completion: String?,
    val word: String,
    val usage: String,
    val description: String,
    /** How many leading characters of [word] are already typed. */
    val typed: Int,
)

internal object ConsoleCommands {
    /** Suggestions for [text] as typed so far; empty when there is nothing useful to say. */
    fun suggest(text: String, loader: Loader, online: List<String>, banned: List<String>): List<Suggestion> {
        if (text.isBlank()) return emptyList()
        val tokens = text.trimStart().split(' ')
        val partial = tokens.last()
        val done = tokens.dropLast(1).filter { it.isNotEmpty() }
        val head = text.substring(0, text.length - partial.length)

        var level = roots(loader)
        var arg = Arg.None
        var context: CommandNode? = null
        var named = false
        for (token in done) {
            val node = level.firstOrNull { it.word.equals(token, ignoreCase = true) }
            when {
                node != null -> {
                    context = node
                    level = node.next
                    arg = node.then
                }
                arg != Arg.None -> {
                    named = true
                    level = emptyList()
                    arg = Arg.None
                }
                // Free text — a message, a reason, coordinates: nothing more to offer.
                else -> {
                    level = emptyList()
                    arg = Arg.None
                }
            }
        }

        val out = mutableListOf<Suggestion>()
        fun matches(word: String) = word.startsWith(partial, ignoreCase = true)
        val nodes = level.filter { matches(it.word) } + level.filter { !matches(it.word) && partial.length >= 2 && it.word.contains(partial, ignoreCase = true) }
        for (node in nodes) {
            val more = node.next.isNotEmpty() || node.then != Arg.None || node.usage.isNotEmpty()
            out += Suggestion(head + node.word + if (more) " " else "", node.word, node.usage, node.description, if (matches(node.word)) partial.length else 0)
        }
        val names = when (arg) {
            Arg.Online -> online
            Arg.Banned -> banned
            Arg.None -> emptyList()
        }
        for (name in names.filter { matches(it) }.sortedBy { it.lowercase() }) {
            val more = context?.afterName.orEmpty().isNotEmpty()
            out += Suggestion(
                head + name + if (more) " " else "",
                name,
                context?.afterName.orEmpty(),
                if (arg == Arg.Banned) "Забанен" else "Сейчас на сервере",
                partial.length,
            )
        }
        // Nothing to pick from, but the command is known: its syntax, the way the game prints it in grey.
        if (out.isEmpty() && context != null && (done.isNotEmpty() || partial.isEmpty())) {
            val rest = if (named) context.afterName else context.usage
            if (rest.isNotEmpty() || named) out += Suggestion(null, context.word, rest, context.description, 0)
        }
        return out
    }

    private val rootsByLoader = HashMap<Loader, List<CommandNode>>()

    private fun roots(loader: Loader): List<CommandNode> = rootsByLoader.getOrPut(loader) {
        val extra = when (loader) {
            Loader.Forge -> listOf(loaderCommands("forge", "Forge"))
            Loader.NeoForge -> listOf(loaderCommands("neoforge", "NeoForge"))
            else -> emptyList()
        }
        (VANILLA + extra).sortedBy { it.word }
    }

    private fun loaderCommands(word: String, name: String) = CommandNode(word, "Команды $name", "<tps|entity>", listOf(
        CommandNode("tps", "Нагрузка: сколько тиков в секунду успевает каждый мир"),
        CommandNode("entity", "Сущности по мирам", "list", listOf(CommandNode("list", "Сколько сущностей в каждом мире"))),
    ))

    private val MODES = listOf(
        CommandNode("survival", "Выживание", "[игрок]", then = Arg.Online),
        CommandNode("creative", "Творчество", "[игрок]", then = Arg.Online),
        CommandNode("adventure", "Приключение: нельзя ломать блоки", "[игрок]", then = Arg.Online),
        CommandNode("spectator", "Наблюдатель: летает сквозь стены", "[игрок]", then = Arg.Online),
    )

    private fun rule(word: String, description: String) =
        CommandNode(word, description, "<true|false>", listOf(CommandNode("true", "Включить"), CommandNode("false", "Выключить")))

    private val VANILLA = listOf(
        CommandNode("list", "Кто сейчас на сервере"),
        CommandNode("say", "Написать всем от имени сервера", "<сообщение>"),
        CommandNode("tell", "Личное сообщение игроку", "<игрок> <сообщение>", then = Arg.Online, afterName = "<сообщение>"),
        CommandNode("me", "Сообщение от третьего лица", "<действие>"),
        CommandNode("op", "Сделать оператором: команды, творчество, бан", "<игрок>", then = Arg.Online),
        CommandNode("deop", "Снять права оператора", "<игрок>", then = Arg.Online),
        CommandNode("kick", "Выгнать с сервера — зайти снова можно", "<игрок> [причина]", then = Arg.Online, afterName = "[причина]"),
        CommandNode("ban", "Забанить по нику", "<игрок> [причина]", then = Arg.Online, afterName = "[причина]"),
        CommandNode("ban-ip", "Забанить по IP-адресу", "<игрок|IP> [причина]", then = Arg.Online, afterName = "[причина]"),
        CommandNode("pardon", "Разбанить игрока", "<игрок>", then = Arg.Banned),
        CommandNode("pardon-ip", "Разбанить IP-адрес", "<IP>"),
        CommandNode("banlist", "Показать баны", "[players|ips]", listOf(
            CommandNode("players", "Забаненные ники"),
            CommandNode("ips", "Забаненные адреса"),
        )),
        CommandNode("whitelist", "Белый список", "<add|remove|on|off|list|reload>", listOf(
            CommandNode("add", "Пустить игрока, когда список включён", "<игрок>", then = Arg.Online),
            CommandNode("remove", "Убрать из белого списка", "<игрок>", then = Arg.Online),
            CommandNode("on", "Включить: заходят только те, кто в списке"),
            CommandNode("off", "Выключить белый список"),
            CommandNode("list", "Кто в белом списке"),
            CommandNode("reload", "Перечитать whitelist.json"),
        )),
        CommandNode("gamemode", "Режим игры для игрока", "<режим> [игрок]", MODES),
        CommandNode("defaultgamemode", "Режим для новых игроков", "<режим>", MODES.map { CommandNode(it.word, it.description) }),
        CommandNode("difficulty", "Сложность мира", "<сложность>", listOf(
            CommandNode("peaceful", "Мирная: без враждебных мобов"),
            CommandNode("easy", "Лёгкая"),
            CommandNode("normal", "Нормальная"),
            CommandNode("hard", "Сложная"),
        )),
        CommandNode("time", "Время суток", "<set|add|query>", listOf(
            CommandNode("set", "Поставить время", "<day|night|noon|midnight>", listOf(
                CommandNode("day", "Утро"),
                CommandNode("noon", "Полдень"),
                CommandNode("night", "Ночь"),
                CommandNode("midnight", "Полночь"),
            )),
            CommandNode("add", "Промотать вперёд: 20 тиков — секунда, 24000 — сутки", "<тики>"),
            CommandNode("query", "Узнать время", "<daytime|gametime|day>", listOf(
                CommandNode("daytime", "Время суток в тиках"),
                CommandNode("gametime", "Тиков с создания мира"),
                CommandNode("day", "Какой сейчас день"),
            )),
        )),
        CommandNode("weather", "Погода", "<clear|rain|thunder> [секунды]", listOf(
            CommandNode("clear", "Ясно", "[секунды]"),
            CommandNode("rain", "Дождь", "[секунды]"),
            CommandNode("thunder", "Гроза", "[секунды]"),
        )),
        CommandNode("tp", "Телепортировать игрока", "<игрок> <игрок|x y z>", then = Arg.Online, afterName = "<игрок|x y z>"),
        CommandNode("give", "Выдать предмет", "<игрок> <предмет> [количество]", then = Arg.Online, afterName = "<предмет> [количество]"),
        CommandNode("clear", "Очистить инвентарь", "<игрок> [предмет]", then = Arg.Online, afterName = "[предмет]"),
        CommandNode("kill", "Убить игрока или существ: @e[type=zombie]", "<цель>", then = Arg.Online),
        CommandNode("effect", "Эффекты зелий", "<give|clear>", listOf(
            CommandNode("give", "Наложить эффект", "<игрок> <эффект> [секунды] [уровень]", then = Arg.Online, afterName = "<эффект> [секунды] [уровень]"),
            CommandNode("clear", "Снять все эффекты", "<игрок>", then = Arg.Online),
        )),
        CommandNode("enchant", "Зачаровать предмет в руке", "<игрок> <чары> [уровень]", then = Arg.Online, afterName = "<чары> [уровень]"),
        CommandNode("xp", "Опыт", "<add|set|query>", listOf(
            CommandNode("add", "Добавить опыт", "<игрок> <количество> [levels]", then = Arg.Online, afterName = "<количество> [levels]"),
            CommandNode("set", "Задать опыт", "<игрок> <количество> [levels]", then = Arg.Online, afterName = "<количество> [levels]"),
            CommandNode("query", "Узнать опыт", "<игрок> <levels|points>", then = Arg.Online, afterName = "<levels|points>"),
        )),
        CommandNode("gamerule", "Правила мира", "<правило> [значение]", listOf(
            rule("keepInventory", "Вещи остаются после смерти"),
            rule("doDaylightCycle", "День сменяет ночь"),
            rule("doWeatherCycle", "Погода меняется сама"),
            rule("doMobSpawning", "Мобы появляются сами"),
            rule("mobGriefing", "Криперы и эндермены ломают блоки"),
            rule("doFireTick", "Огонь распространяется"),
            rule("doInsomnia", "Фантомы прилетают к тем, кто не спит"),
            rule("doImmediateRespawn", "Возрождение без экрана смерти"),
            rule("naturalRegeneration", "Здоровье восстанавливается от сытости"),
            rule("showDeathMessages", "Сообщения о смертях в чате"),
            rule("announceAdvancements", "Достижения в чате"),
            rule("fallDamage", "Урон от падения"),
            CommandNode("playersSleepingPercentage", "Сколько процентов игроков должно спать, чтобы пропустить ночь", "<проценты>"),
            CommandNode("randomTickSpeed", "Скорость роста растений, обычно 3", "<число>"),
            CommandNode("spawnRadius", "Разброс точки появления в блоках", "<блоки>"),
        )),
        CommandNode("setworldspawn", "Точка появления для всех — там, где стоит оператор", "[x y z]"),
        CommandNode("spawnpoint", "Точка возрождения игрока", "<игрок> [x y z]", then = Arg.Online, afterName = "[x y z]"),
        CommandNode("seed", "Сид этого мира"),
        CommandNode("save-all", "Сохранить мир на диск", "[flush]", listOf(CommandNode("flush", "Дождаться, пока всё запишется"))),
        CommandNode("save-off", "Выключить автосохранение — перед копированием мира"),
        CommandNode("save-on", "Включить автосохранение обратно"),
        CommandNode("stop", "Сохранить мир и выключить сервер"),
        CommandNode("reload", "Перезагрузить датапаки"),
        CommandNode("summon", "Призвать существо", "<существо> [x y z]"),
        CommandNode("locate", "Найти ближайшую структуру или биом", "<structure|biome> <что>", listOf(
            CommandNode("structure", "Структура: village_plains, stronghold…", "<структура>"),
            CommandNode("biome", "Биом: cherry_grove, desert…", "<биом>"),
        )),
        CommandNode("worldborder", "Граница мира", "<set|get|center>", listOf(
            CommandNode("set", "Задать ширину в блоках", "<ширина> [секунды]"),
            CommandNode("get", "Узнать ширину"),
            CommandNode("center", "Поставить центр", "<x z>"),
        )),
        CommandNode("title", "Надпись на экране игрока", "<игрок> <title|subtitle|actionbar> <текст>", then = Arg.Online, afterName = "<title|subtitle|actionbar> <текст>"),
        CommandNode("help", "Все команды сервера", "[команда]"),
    )
}

# AGENTS.md — Yumi Launcher

Инструкция для ИИ-агентов (OpenAI Codex и других), которые работают с этим репозиторием: что это
за проект, как его собрать и проверить, где что лежит и на какие грабли уже наступали. Прочитай
целиком до первой правки.

## Проект

- **Yumi Launcher** — лаунчер Minecraft для Windows на Kotlin/JVM и Compose Multiplatform Desktop.
  Дизайн — Material 3 Expressive, палитра Glacier, шрифт Roboto Flex, вместо картинки на фоне живой
  воксельный 3D-остров.
- Главная цель владельца — играть с друзьями без лицензии: офлайн-ник, аккаунт Ely.by, свой сервер
  на своём компьютере.
- Один Gradle-модуль `:app`, пакет `com.yumi.launcher`, точка входа `Main.kt`
  (`com.yumi.launcher.MainKt`).
- **Юнит-тестов нет.** Проверки — компиляция, headless-режимы и снимки экранов (см. «Проверки»).
- Что умеет лаунчер, подробно — в `README.md`.

## Стек

| Что | Версия |
|---|---|
| JDK | 21 (`jvmToolchain(21)`) |
| Gradle | 9.7.0 (wrapper) |
| Kotlin | 2.3.10 (плагины `jvm`, `plugin.serialization`, `plugin.compose`) |
| Compose Multiplatform | 1.11.1 |
| material3 | 1.11.0-alpha07 — ради Expressive API |
| material-icons-extended | 1.7.3 |
| kotlinx-coroutines-swing | 1.10.2 |
| kotlinx-serialization-json | 1.10.0 |

Версии совпадают с соседним проектом YumiDesktop намеренно: общий кэш Gradle и общий дизайн. Не
поднимай их без просьбы владельца. Opt-in для `ExperimentalMaterial3Api`,
`ExperimentalMaterial3ExpressiveApi` и `ExperimentalComposeUiApi` уже включены в
`app/build.gradle.kts`, отдельные аннотации для них не нужны.

## Сборка и запуск

На машине владельца (Windows, Git Bash):

```bash
source /e/Projects/.tools/env.sh                # JDK 21 и остальной тулчейн; без этого сборка не заведётся
export GRADLE_USER_HOME=C:/Users/000/.gradle    # тёплый кэш с артефактами Compose 1.11
./gradlew --offline :app:compileKotlin          # быстрая проверка, что всё компилируется
./gradlew --offline :app:run                    # открыть окно лаунчера («запусти демо»)
```

В другой среде (CI, облачная песочница): нужен JDK 21. Уберите `--offline` и две строки выше:
первая сборка скачает зависимости. `systemProp.https.protocols=TLSv1.2` в `gradle.properties` не
трогай — без неё Maven Central на сети владельца обрывает TLS.

Ошибки компиляции ищи так: `./gradlew --offline :app:compileKotlin -q 2>&1 | grep "^e:"`.

## Проверки

После любой правки код должен компилироваться без ошибок. Дальше проверяй в зависимости от того,
что менялось. Все режимы запускаются через `--args` у `:app:run` или напрямую из тестовой копии
(см. ниже).

| Режим | Что делает |
|---|---|
| `--ui-smoke` | Открывает окно и снимает все экраны в светлой и тёмной теме в `app/.local/screenshots/`: главная, сборки, моды, настройки, серверы, страница сервера, создание сервера, быстрый вход, вход Ely.by; плюс главная и настройки в каждой пастельной палитре. Настройки и данные — отдельные, пользовательские не трогает. **Обязателен после изменений UI**, снимки нужно посмотреть. |
| `--launch-test <версия> <папка> [загрузчик] [проекты Modrinth…] [--join=host:port] [--injector]` | Без окна скачивает версию (и загрузчик, и моды), запускает игру, ждёт отрисовки и закрывает. `--join` — сразу зайти на сервер, `--injector` — под authlib-injector, как с аккаунтом Ely.by. |
| `--server-test <версия> <папка> [загрузчик] [порт]` | Ставит сервер как экран «Серверы», запускает, пингует, выполняет `list`, выключает по `stop`. |
| `--ping-test <адрес…>` | Пинг серверов, как в «Быстром входе». |

**Долгие проверки запускай из «замороженной» копии**, а не через `gradlew run`: иначе правка кода
во время прогона ломает перекомпиляцию, и тест падает не по своей вине.

```bash
./gradlew --offline :app:testBundle
cp -r app/build/test-bundle .local/test-bundle
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "E:/Projects/YumiLauncher/.local/test-bundle/*" \
  com.yumi.launcher.MainKt --server-test 1.21.1 E:/Projects/YumiLauncher/.local/server-test Fabric 25602
```

Правила для проверок:

- Тестовые данные — только в `.local/` (корня или `app/`), эта папка в `.gitignore`. Реальную
  папку игры владельца и узел настроек `com/yumi/launcher` не трогай.
- Тестовые серверы привязывай к `server-ip=127.0.0.1` (так делает `--server-test`). Иначе Windows
  показывает владельцу окно брандмауэра для `javaw.exe`.
- `--launch-test` и `--server-test` качают сотни мегабайт–гигабайты (игра, Java, серверы) и
  открывают окно игры на экране. Не запускай их без нужды и параллельно.
- Утверждай «работает», только если проверил реальным запуском. Если что-то не проверено (например,
  CurseForge без ключа или вход Ely.by без настоящего аккаунта) — так и скажи.

## Устройство кода

```
app/src/main/kotlin/com/yumi/launcher/
├── Main.kt              окно, тестовые режимы, смоук-съёмка
├── LaunchTest.kt        --launch-test
├── ServerTest.kt        --server-test, --ping-test
├── core/                без Compose: сеть, Mojang, установка, запуск
│   ├── Http.kt          HTTP: повторы, докачка через .part, SHA-1, POST JSON, User-Agent
│   ├── Mojang.kt        список версий, JSON версии, правила, библиотеки, слияние inheritsFrom
│   ├── Installer.kt     подготовка клиента: jar, библиотеки, natives, ресурсы, Java-рантаймы Mojang
│   ├── GameLauncher.kt  команда запуска игры: аккаунт, Quick Play / --server, authlib-injector
│   ├── Loaders.kt       Fabric, Quilt (профили), Forge, NeoForge (их установщики)
│   ├── Mods.kt          Modrinth и CurseForge; стороны модов (server_side) по SHA-1
│   ├── ElyBy.kt         вход Ely.by (Yggdrasil), скины, authlib-injector
│   ├── ServerInstaller.kt  серверы всех загрузчиков и как их запускать
│   ├── ServerMods.kt    перенос модов сборки на сервер без клиентских
│   ├── ServerPing.kt    пинг серверов (SLP 1.7+, старый 0xFE, SRV), MOTD в цветах
│   ├── LauncherPaths.kt где что лежит
│   └── Platform.kt      ОС и архитектура в терминах Mojang
├── data/                состояние для UI (Compose state)
│   ├── LauncherModel.kt корневая модель: сборки, версии, запуск игры, папка игры
│   ├── Instances.kt     сборки на диске; folderId() — ASCII-имена папок
│   ├── ModManager.kt    каталог и установленные моды сборки
│   ├── Servers.kt       свои серверы: процесс, терминал, игроки, адреса для друзей
│   ├── ServerProperties.kt  правка server.properties с сохранением остального
│   ├── QuickJoin.kt     список «Быстрого входа» и пинги
│   ├── ElyAccount.kt    сессия Ely.by: вход, 2FA, обновление токена
│   └── GameLog.kt       разбор XML-лога log4j из игры
├── settings/LauncherSettings.kt   настройки в java.util.prefs, каждое поле — Compose state
├── scene/               воксельный остров: мир, затенение, тени, рендер через Skia drawVertices
└── ui/
    ├── theme/           цвета (Glacier + пастельные палитры в OKLCH), типографика, LauncherTheme, Modifier.glass()
    ├── components/      общие компоненты (см. «Соглашения»)
    ├── home/            окно (LauncherApp), навигация (LauncherState), главная, вход Ely.by, выбор папки
    ├── packs/ mods/ servers/ settings/ console/   экраны
```

## Данные на диске

- **Настройки** — `java.util.prefs`, узел `com/yumi/launcher` (на Windows это реестр). Смоук-режим
  пишет в `com/yumi/launcher-smoke` и удаляет его в конце.
- **`LauncherPaths.config`** = `%APPDATA%\YumiLauncher`: кэш (список версий, иконки, установщики)
  и `servers.json` — список «Быстрого входа».
- **`LauncherPaths.root`** — папка игры, которую выбрал пользователь (`settings.gameDir`; пока не
  выбрана, спрашивается при первом скачивании). Внутри:
  - `versions/`, `libraries/`, `assets/`, `runtime/` — общие для всех сборок;
  - `instances/<id>/instance.json`, `mods.json`, `minecraft/` — сборка и её папка игры;
  - `servers/<id>/yumi-server.json`, `server.properties`, мир, `mods/` — свои серверы;
  - `authlib-injector/` — jar агента.
- Форматы — `@Serializable`, читаются через `LauncherJson` (`ignoreUnknownKeys`). **Совместимость
  со старыми файлами обязательна:** у новых полей — значения по умолчанию, существующие не
  переименовывать и не удалять. Перечисления хранятся по имени. Значок сборки хранится индексом в
  `PackIcons` — в этот список только добавлять в конец.

## Соглашения

**Код**
- Пиши так же, как написан окружающий код: те же имена, идиомы и плотность комментариев.
- Комментарии и KDoc — по-английски и объясняют «почему», а не пересказывают код.
- Тексты интерфейса — по-русски: «ёлочки», длинное тире, буква «ё». Ошибки для пользователя —
  короткая фраза о том, что случилось и что делать («Порт занят — возможно, сервер уже запущен»).
- Compose state меняется на главном потоке (`Dispatchers.Main` — это Swing EDT через
  coroutines-swing). Сеть и диск — в `withContext(Dispatchers.IO)`. Ожидание процессов, которое
  должно отменяться, — через `runInterruptible`.
- Навигация, которая должна пережить уход с экрана, живёт в `LauncherState`, а не в `remember`.
- Сеть — только через `core/Http` (повторы, докачка, User-Agent: Modrinth его требует).

**Интерфейс**
- Готовые компоненты: `Modifier.glass()` (плавающие панели над 3D-фоном), `ModalCard`,
  `ChoiceGroup` (соединённая группа кнопок-переключателей), `Hint`, `Label`, `PackTile`, `ShapeTile`,
  `SwitchRow`, `MemorySlider`, `RemoteIcon`, `SkinHead`.
- Кнопки — с `ButtonDefaults.shapes()` / `IconButtonDefaults.shapes()`. Ожидание —
  `LoadingIndicator`, прогресс — `LinearWavyProgressIndicator`.
- Окно без рамки (`undecorated`). Диалоги рисуются внутри окна через `ModalCard`, а не через
  `Dialog`/`AlertDialog`: у тех своё отдельное окно с лишней рамкой.
- Цвет содержимого (`LocalContentColor`) задан один раз наверху окна. Проверяй обе темы: в тёмной
  легко получить чёрный текст на тёмном.
- Цвета — только из `MaterialTheme.colorScheme`, без захардкоженных `Color(0x…)` в экранах: у
  лаунчера четыре палитры (`settings.palette`: оригинальная Glacier, пастельные зелёная, розовая,
  синяя) × светлая и тёмная тема. Пастельные схемы строятся в `ui/theme/Palettes.kt` из трёх
  оттенков OKLCH с общими шагами светлоты; новую палитру добавляй там же и в `enum Palette`.
  Исключения — цвета, которые не зависят от темы по смыслу: MOTD серверов, «онлайн»-точка.
- Ширина карточки экрана задаётся в `ScreenCard` (`LauncherApp.kt`): главная 440, моды и серверы
  620, остальные 520 dp. Минимальный размер окна — 1100×720.

## Известные грабли

- **Кодировка.** Русская Windows пишет вывод Java в cp1251. Процессам игры и сервера всегда
  передаются `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`, вывод
  читается как UTF-8.
- **Лог игры — XML log4j**, а не текст; его разбирает `GameLog`.
- **Пути только ASCII.** Старые версии (LWJGL 2 на Java 8) не грузят natives из пути с кириллицей.
  Имена папок сборок и серверов делаются через `folderId()` с транслитерацией.
- **NeoForge** узнаёт jar игры по имени `${version_name}.jar`. Поэтому для версий с загрузчиком
  ванильный jar копируется в `versions/<id>/<id>.jar`, иначе игра грузится дважды и падает.
- **Forge/NeoForge-сервер** с 1.17 запускается строкой `java` из `run.bat` (файл `@…/win_args.txt`),
  старые — одним jar. `user_jvm_args.txt` пропускается: память задаёт лаунчер.
- **`server.properties`** пишется с `\uXXXX` для не-ASCII (иначе русский MOTD ломается на серверах,
  читающих Latin-1). До 1.14 режим и сложность — числами (`numericProperties()`).
- **Quick Play** (`--quickPlayMultiplayer`, 1.20+) в свежей папке игры ждёт экран настроек
  доступности. Для тестов в `options.txt` пишется `onboardAccessibility:false`. Версии до 1.20
  получают `--server`/`--port`.
- **Закрытие лаунчера** при работающем сервере идёт через подтверждение и `stopAll()`, плюс
  shutdown hook. Сервер не должен остаться работать без консоли.
- **Смена папки игры** переносит сборки и серверы. Пока работает сервер или идёт игра, смена
  запрещена.
- **curl из Git Bash** обрывает большие загрузки. Если качаешь руками — используй
  `/c/Windows/System32/curl.exe`. В коде качает `java.net.http.HttpClient`, с ним всё в порядке.

## Безопасность и приватность

- Пароль Ely.by никуда не сохраняется — только токены, в настройках. Не добавляй хранение паролей.
- Ключ CurseForge — личный ключ пользователя из настроек. Никаких ключей, токенов и секретов в коде
  и в репозитории.
- Никакой телеметрии и отправки данных пользователя третьим сторонам.

## Git

- `origin` — https://github.com/GXUser7/YumiLauncher.git, ветка `main`.
- `.gitattributes`: `gradlew` хранится с LF, `*.bat` — с CRLF, шрифты, картинки и jar — бинарные.
- Не коммить `.local/`, `build/`, снимки экранов и тестовые данные.
- Коммит и push — только когда владелец попросит. Сообщение коммита — по-английски: короткая
  первая строка, дальше что и зачем.

## Общение с владельцем

- Владелец пишет по-русски — отвечай по-русски.
- Он часто набирает текст в английской раскладке. Восстанавливай раскладку сам и не переспрашивай:
  `pfgei` → «запуш», `pfgecnb ltvj` → «запусти демо», `gjlcjtlbyb ubn` → «подсоедини гит».
- «Запусти демо» = `./gradlew --offline :app:run` в фоне (с `source` и `GRADLE_USER_HOME` выше).
- В ответе: что сделано, что проверено и чем, что не проверено. Без воды.

## Что ещё не сделано

- Вход через Microsoft (кнопка «Microsoft» на главной пока ведёт в «скоро»).
- Кнопки «Загрузки» и «Уведомления» в верхней панели — заглушки. Карусель новостей статична.

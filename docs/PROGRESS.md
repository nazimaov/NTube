# Прогресс

## Фаза 1. Данные YouTube — выполнена 2026-11-15

Ветка: `phase-1-youtube` (от `phase-0-skeleton`).
Критерий готовности: интеграционные тесты возвращают тренды, результаты поиска и `StreamSet` с потоками ≥ 1080p для тестового видео.

- [x] Подключить NewPipeExtractor v0.27.1 (последняя), desugaring, правило R8 для Rhino
- [x] Сверить API экстрактора по исходникам тега v0.27.1
- [x] `core:model`: Video, ChannelSummary, PlaylistSummary, SearchItem, Page, PageToken, VideoDetails, StreamSet, Comment, AppError, AppResult
- [x] `core:common`: диспетчеры `@IoDispatcher` / `@DefaultDispatcher` (Hilt)
- [x] `core:network`: единый OkHttpClient с HTTP-кэшем 50 МБ, Json
- [x] `data:youtube`: OkHttpDownloader (429 → ReCaptchaException), инициализация NewPipe в `Application.onCreate`
- [x] `YouTubeRepository` и реализация всех методов из 4.3 + `dashManifest`, `channelPlaylists`
- [x] Мапперы в собственные модели; DASH-манифест собирают классы экстрактора (OTF и post-live)
- [x] Ошибки: исключения экстрактора → `AppError` (4.5), отмена корутин не глотается
- [x] Кэш `VideoDetails` в памяти на 1 час, до 20 видео; `forceRefresh` для 403/410
- [x] Модульные тесты: мапперы, ошибки, токены страниц, кэш — 15 тестов
- [x] `./gradlew :data:youtube:liveTest` — 6 тестов зелёные

Результат живых тестов (Big Buck Bunny `aqz-KE-bpKQ`): видео-потоки до 2160p60 (VP9, AV1), 1080p60 (AVC, VP9), все с доставкой PROGRESSIVE_HTTP; тренды — 15 видео; поиск — 20 + 16 на второй странице; канал Blender — вкладки, 30 видео, RSS-фид 15, плейлист 95 видео; комментарии — 20.

Также проверено: `test`, `lint` (0 ошибок), `assembleDebug`, `assembleRelease` с R8; приложение стартует на эмуляторе с инициализацией экстрактора.

Известные проблемы:

- Страница «В тренде» у YouTube нестабильна: `trending()` сначала пробует киоск `Trending`, при пустом ответе — киоск по умолчанию (`live`). Какой из них отдаёт ленту, видно только в живом тесте.
- `channelFeed` (RSS) надёжно работает только с ID вида `UC…`; подписки в фазе 4 сохранять с ID из `channel()`.
- Живые тесты с серверов GitHub могут падать на проверке «не бот» — так и задумано в 10.7.

## Фаза 0. Каркас — выполнена 2026-11-15

Ветка: `phase-0-skeleton`.
Критерий готовности: `assembleDebug` проходит; приложение видно в лаунчере ТВ и открывает пустой экран с боковым меню.

- [x] Gradle wrapper 9.9.1, `settings.gradle.kts`, репозитории google(), mavenCentral(), jitpack.io
- [x] Version catalog `gradle/libs.versions.toml` с актуальными стабильными версиями
- [x] `build-logic` с convention-плагинами: android-application, android-library, compose, feature, hilt, jvm-library
- [x] Все модули из раздела 3 (пустые): core/*, data/*, player, feature/*
- [x] `gradle.properties`: название приложения, versionName/versionCode, репозиторий обновлений
- [x] `app`: Hilt Application, MainActivity, NavHost с маршрутами, манифест из раздела 8, баннер 320×180
- [x] `core:designsystem`: тёмная тема на `tv-material`
- [x] Боковое меню `NavigationDrawer`: Поиск, Главная, Подписки, История, Настройки (пустые экраны)
- [x] Строки ru и en в `strings.xml`
- [x] CI `.github/workflows/ci.yml`: lint, test, assembleDebug
- [x] `./gradlew :app:assembleDebug` проходит; `test` и `lint` проходят (0 ошибок)
- [x] Эмулятор Android TV 1080p (API 34): приложение в `LEANBACK_LAUNCHER`, открывается, меню переключает разделы с D-pad

Известные проблемы:

- `android.overridePathCheck=true` нужен, пока проект лежит в пути с кириллицей. AGP помечает опцию как экспериментальную; при странных ошибках сборки перенести проект в путь из латиницы.
- При первом запуске фокус стоит на пункте «Поиск», а не на выбранном «Главная». Фокус на первой карточке — задача фазы 3.
- `lumen.updateRepoOwner` в `gradle.properties` = `CHANGE_ME`; заполнить до фазы 6.
- Lint: 6 предупреждений (правила резервного копирования в манифесте, совместимость) — не блокируют.

# Прогресс

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

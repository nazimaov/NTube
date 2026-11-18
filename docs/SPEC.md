# ТЗ: собственный YouTube-клиент для Android TV (аналог SmartTube)

Nov 15, 2026 · @Nazim

## 1. Цель и рамки

Строим с нуля приложение для Android TV, которое показывает YouTube без рекламы, пропускает спонсорские вставки и имеет собственные моды. Рабочее название — `Lumen` (пакет `tv.lumen.app`); название меняется одной строкой в `gradle.properties`.

Допущения, принятые без уточнения. Если какое-то неверно — поменяйте здесь, остальной документ от них зависит.

| Вопрос | Принятое решение |
| --- | --- |
| Платформа | Android TV, Google TV, Fire TV, ТВ-приставки на Android 7.0+ (API 24+). Телефоны не поддерживаются. |
| Управление | Только пульт (D-pad, OK, Back, Play/Pause, перемотка). Без тач-жестов. |
| Основа | Свой код с нуля, не форк SmartTube. Данные YouTube — через библиотеку NewPipeExtractor. |
| Назначение | Личное использование и установка APK вручную. Публикация в Google Play не планируется. |
| Аккаунт Google | В MVP не используется: подписки и история хранятся локально. Вход в аккаунт — отдельная поздняя фаза. |
| Язык интерфейса | Русский и английский, строки только в `strings.xml`. |

**Входит в MVP (версия 1.0):** главная лента (тренды), поиск с голосовым вводом, страница канала, плейлисты, плеер до 4K с выбором качества и скорости, субтитры, локальные подписки с лентой, локальная история с продолжением просмотра, SponsorBlock, настройки, самообновление APK.

**После MVP:** DeArrow, счётчик дизлайков, вход в аккаунт Google, комментарии, живой чат, автоподстройка частоты кадров, импорт подписок, Shorts.

**Не делаем вообще:** загрузку видео на устройство, обход платного контента и возрастных ограничений, собственный сервер-прокси, сбор телеметрии.

## 2. Ключевые решения и стек

Стек — Kotlin, Compose for TV, Media3 и NewPipeExtractor; SmartTube служит образцом по функциям, но не по коду. SmartTube написан на Java с Leanback и собственной сборкой ExoPlayer 2.10.6 ([обзор архитектуры](https://deepwiki.com/yuliskov/SmartTube)) — повторять этот устаревший стек незачем.

| Область | Решение | Версия | Почему |
| --- | --- | --- | --- |
| Язык | Kotlin, корутины, Flow | последняя стабильная | Стандарт для нового Android-кода |
| UI | Jetpack Compose + `androidx.tv:tv-material` | 1.1.0 ([релизы](https://developer.android.com/jetpack/androidx/releases/tv)) | Готовые ТВ-компоненты с фокусом |
| Плеер | `androidx.media3` (ExoPlayer) | 1.10.1 ([релизы](https://developer.android.com/jetpack/androidx/releases/media3)) | Актуальная замена ExoPlayer 2 |
| Данные YouTube | `com.github.teamnewpipe:NewPipeExtractor` через JitPack | v0.26.5 ([релизы](https://github.com/TeamNewPipe/NewPipeExtractor/releases)) | Поддерживается командой NewPipe, быстро чинится после изменений YouTube |
| Сеть | OkHttp + kotlinx.serialization | последняя стабильная | Один HTTP-клиент на всё приложение |
| DI | Hilt | последняя стабильная | Стандарт, работает с ViewModel |
| БД | Room | последняя стабильная | Подписки, история, кэш |
| Настройки | DataStore Preferences | последняя стабильная | Типобезопасные ключи, Flow |
| Картинки | Coil 3 (`coil-compose`, `coil-network-okhttp`) | последняя стабильная | Нативно для Compose |
| Навигация | Navigation Compose, типобезопасные маршруты | последняя стабильная | Один Activity |
| Сборка | Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml` | AGP и Gradle — последние стабильные | Все версии в одном файле |

Правила по версиям для Claude Code:

- Три версии из таблицы с номерами проверены на дату документа. Перед стартом проверить, не вышла ли более новая NewPipeExtractor — брать всегда самую свежую, от этого зависит работоспособность.
- Для строк «последняя стабильная» взять актуальные версии на момент создания проекта и зафиксировать в `libs.versions.toml`. Альфа- и бета-версии не использовать.
- `minSdk = 24`, `targetSdk` и `compileSdk` — последний стабильный API. Java toolchain 17. Включить `coreLibraryDesugaring` (нужен экстрактору на старых Android).
- NewPipeExtractor распространяется под GPLv3. Значит, и приложение при любой раздаче APK должно быть под GPLv3 с открытым кодом. Для личного использования ограничений нет.

Отвергнутые варианты и причины:

- **Форк SmartTube** — Java, MVP, Leanback, ExoPlayer 2.10.6; тяжело модифицировать, лицензия нестандартная.
- **WebView с youtube.com/tv и вырезанием рекламы скриптами** — медленно на слабых приставках, ломается при каждом обновлении сайта.
- **Официальный YouTube Data API v3** — не отдаёт ссылки на видеопотоки, жёсткие квоты.
- **Свой клиент InnerTube с нуля** — месяцы работы и постоянная починка; берём готовый экстрактор.

## 3. Архитектура и структура репозитория

Приложение — один Activity, многомодульный Gradle-проект, три слоя: UI (Compose + ViewModel) → репозитории → источники данных. Экстрактор NewPipe виден только из модуля `:data:youtube`; остальной код работает с собственными моделями из `:core:model`. Это главное правило архитектуры: когда YouTube что-то сломает, чинить придётся один модуль.

```text
lumen/
├── CLAUDE.md                     # правила для Claude Code (раздел 14)
├── docs/SPEC.md                  # этот документ в Markdown
├── settings.gradle.kts           # список модулей, репозитории: google(), mavenCentral(), jitpack.io
├── gradle/libs.versions.toml     # все версии
├── build-logic/                  # convention-плагины: android-library, compose, hilt
├── app/                          # MainActivity, NavHost, Hilt Application, манифест
├── core/
│   ├── model/                    # чистый Kotlin: Video, Channel, Playlist, StreamSet, Segment…
│   ├── common/                   # Result-обёртка, диспетчеры, форматирование времени и чисел
│   ├── network/                  # единый OkHttpClient, Json, кэш HTTP
│   ├── database/                 # Room: сущности, DAO, миграции
│   ├── datastore/                # настройки (DataStore), SettingsRepository
│   └── designsystem/             # тема, VideoCard, Row, фокус-рамка, диалоги
├── data/
│   ├── youtube/                  # ЕДИНСТВЕННЫЙ модуль с NewPipeExtractor
│   ├── sponsorblock/             # клиент SponsorBlock
│   ├── dearrow/                  # клиент DeArrow
│   ├── ryd/                      # клиент Return YouTube Dislike
│   ├── library/                  # подписки, история, позиции просмотра
│   └── updater/                  # проверка и установка обновлений
├── player/                       # Media3: PlayerController, выбор потоков, пропуск сегментов
└── feature/
    ├── home/  search/  channel/  playlist/
    ├── watch/                    # экран плеера и его оверлеи
    ├── library/                  # подписки и история
    └── settings/
```

Правила зависимостей между модулями:

- `feature:*` зависит от `core:*`, `data:*` и `player`. Модули `feature` друг от друга не зависят; переходы идут через маршруты в `app`.
- `data:*` зависит только от `core:*`.
- `player` зависит от `core:*`, `data:youtube`, `data:sponsorblock`, `data:library`.
- `core:model` не зависит ни от чего, включая Android SDK.

Шаблон каждого экрана одинаковый: `XxxRoute` (получает ViewModel) → `XxxScreen` (чистый Composable от состояния) → `XxxViewModel` со `StateFlow<XxxUiState>`, где `UiState` — sealed-интерфейс `Loading | Content | Error`. Вся работа с сетью идёт в `Dispatchers.IO`; экстрактор блокирующий, каждый его вызов оборачивается в `withContext(io)`.

Ошибки наружу из `data:*` выходят только как `AppError`: `Network`, `ContentUnavailable(reason)`, `ExtractionBroken`, `AgeRestricted`, `Unknown`. Экран показывает текст ошибки и кнопку «Повторить»; `ExtractionBroken` дополнительно предлагает проверить обновление приложения.

## 4. Источник данных YouTube (`:data:youtube`)

Все данные YouTube — ленту, поиск, каналы, ссылки на потоки — приложение получает через NewPipeExtractor, без ключей API и без своего сервера. Рекламу при этом «блокировать» не нужно: рекламные ролики запрашивает официальный клиент отдельными запросами, а наш клиент их просто не делает.

### 4.1 Подключение

```kotlin
// settings.gradle.kts → dependencyResolutionManagement.repositories
maven("https://jitpack.io")

// data/youtube/build.gradle.kts
implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5") // или новее
coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
```

В `proguard-rules.pro` модуля `app` добавить правило из [README экстрактора](https://javadoc.jitpack.io/p/teamnewpipe/newpipeextractor): `-keep class org.mozilla.javascript.** { *; }`. Без него в release-сборке не работает расшифровка подписей потоков.

### 4.2 Инициализация

1. Реализовать `OkHttpDownloader : org.schabi.newpipe.extractor.downloader.Downloader` — метод `execute(request)` перекладывает запрос в общий `OkHttpClient`. При HTTP 429 бросать `ReCaptchaException`.
2. В `Application.onCreate` один раз вызвать `NewPipe.init(downloader, Localization(lang, country), ContentCountry(country))`. Язык и страна берутся из настроек, по умолчанию — из системной локали.
3. Сервис — `ServiceList.YouTube`.

### 4.3 Контракт модуля

Наружу модуль отдаёт один интерфейс; классы `org.schabi.newpipe.*` за пределы модуля не выходят.

```kotlin
interface YouTubeRepository {
    suspend fun trending(): Result<List<Video>>
    suspend fun search(query: String, filter: SearchFilter, page: PageToken?): Result<Page<SearchItem>>
    suspend fun searchSuggestions(query: String): Result<List<String>>
    suspend fun videoDetails(videoId: String): Result<VideoDetails>   // метаданные + StreamSet + похожие
    suspend fun channel(channelId: String): Result<ChannelDetails>
    suspend fun channelVideos(channelId: String, tab: ChannelTab, page: PageToken?): Result<Page<Video>>
    suspend fun channelFeed(channelId: String): Result<List<Video>>   // быстрый RSS-фид для подписок
    suspend fun playlist(playlistId: String, page: PageToken?): Result<Page<Video>>
    suspend fun comments(videoId: String, page: PageToken?): Result<Page<Comment>>
}
```

| Метод | Вызов экстрактора |
| --- | --- |
| `trending` | `KioskInfo.getInfo(YouTube, <url киоска по умолчанию из YouTube.kioskList>)` |
| `search` | `SearchInfo.getInfo(YouTube, YouTube.searchQHFactory.fromQuery(query, filters, ""))`, далее `SearchInfo.getMoreItems(...)` |
| `searchSuggestions` | `YouTube.suggestionExtractor.suggestionList(query)` |
| `videoDetails` | `StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$id")` |
| `channel`, `channelVideos` | `ChannelInfo.getInfo(...)`, затем `ChannelTabInfo.getInfo(YouTube, tabHandler)` |
| `channelFeed` | `FeedInfo.getInfo(YouTube, channelUrl)` |
| `playlist` | `PlaylistInfo.getInfo(...)`, `PlaylistInfo.getMoreItems(...)` |
| `comments` | `CommentsInfo.getInfo(...)`, `CommentsInfo.getMoreItems(...)` |

Имена классов и сигнатуры записаны по публичному API библиотеки; перед написанием кода Claude Code сверяет их с исходниками подключённой версии (они лежат в кэше Gradle после первой сборки), а не ищет в сети.

### 4.4 Потоки видео

`StreamInfo` содержит четыре нужных нам источника. Модуль преобразует их в собственную модель `StreamSet`.

| Поле `StreamInfo` | Что это | Как используем |
| --- | --- | --- |
| `videoOnlyStreams` | Видео без звука, все разрешения до 4K/8K, кодеки AVC / VP9 / AV1 | Основной путь: видео + отдельная аудиодорожка |
| `audioStreams` | Аудио отдельно (Opus, AAC), возможны несколько языков | Пара к видеопотоку |
| `videoStreams` | Видео со звуком в одном файле, обычно только 360p | Запасной путь, если отдельных потоков нет |
| `hlsUrl` / `dashMpdUrl` | Готовый манифест | Прямые трансляции |

У каждого потока читать `deliveryMethod`: `PROGRESSIVE_HTTP` (прямая ссылка), `DASH` (нужен манифест) или `HLS`. Для DASH-потоков YouTube манифест собирается классами из пакета `org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators` — свой генератор не писать.

Ссылки на потоки временные (живут несколько часов). `VideoDetails` кэшируется в памяти максимум на 1 час; при ответе 403 или 410 плеер заново запрашивает `videoDetails` и продолжает с той же позиции.

### 4.5 Защита YouTube: SABR и PoToken

YouTube регулярно меняет выдачу потоков сторонним клиентам. В июне 2026 года экстрактору пришлось обходить принудительный протокол SABR переходом на другой клиент плеера ([PR #1508](https://github.com/TeamNewPipe/NewPipeExtractor/pull/1508)); обход не работает для детских видео ([релизы NewPipe](https://github.com/TeamNewPipe/NewPipe/releases)). Отсюда требования:

- Версия экстрактора задаётся одной строкой в `libs.versions.toml`; обновление экстрактора = замена строки + новая сборка APK.
- Если `videoOnlyStreams` пуст и есть только 360p — не падать, играть 360p и показать уведомление «Высокое качество временно недоступно, проверьте обновление».
- PoToken (токен подтверждения клиента) экстрактор сам не генерирует: он принимает реализацию интерфейса `PoTokenProvider`, а сама реализация на WebView лежит в репозитории приложения NewPipe, а не в библиотеке ([обсуждение в PR #11955](https://github.404890603.workers.dev/TeamNewPipe/NewPipe/pull/11955)). В MVP работаем без него. Если в фазе 2 видео не играют с ошибкой 403 — перенести пакет `org.schabi.newpipe.util.potoken` из NewPipe (GPLv3) в `:data:youtube` и подключить через `YoutubeStreamExtractor.setPoTokenProvider(...)`.
- Любое исключение разбора (`ParsingException`, `ExtractionException`) превращается в `AppError.ExtractionBroken`; `ContentNotAvailableException` и наследники — в `ContentUnavailable` с понятной причиной.

## 5. Плеер (`:player`)

Плеер — Media3 ExoPlayer 1.10.1 с собственным интерфейсом на Compose; готовый `PlayerView` используется только как поверхность для видео без встроенных кнопок (`useController = false`).

### 5.1 Зависимости

`media3-exoplayer`, `media3-exoplayer-dash`, `media3-exoplayer-hls`, `media3-datasource-okhttp`, `media3-ui`, `media3-session` — все одной версии.

### 5.2 Сборка источника

1. Выбрать один видеопоток и один аудиопоток (правила ниже).
2. Для каждого создать `MediaSource`: `ProgressiveMediaSource` для `PROGRESSIVE_HTTP`, `DashMediaSource` из сгенерированного манифеста для `DASH`.
3. Объединить через `MergingMediaSource(video, audio)`; субтитры добавить как `SubtitleConfiguration` (формат VTT).
4. Прямой эфир: `HlsMediaSource` по `hlsUrl`, без выбора потоков вручную.
5. Все запросы идут через `OkHttpDataSource.Factory(sharedOkHttpClient)`.

### 5.3 Выбор качества

- Настройка `maxResolution`: 720, 1080, 1440, 2160 или «по экрану» (по умолчанию — разрешение дисплея из `Display.Mode`).
- Берётся поток с наибольшим разрешением, не превышающим `maxResolution`; при равном разрешении — с наибольшей частотой кадров.
- Кодек: порядок предпочтения AV1 → VP9 → AVC, но кодек допускается только при наличии аппаратного декодера нужного разрешения. Проверка — `MediaCodecUtil.getDecoderInfos(...)` и `isVideoSizeAndRateSupportedV21`. Программные декодеры для видео не использовать.
- HDR-потоки выбирать только если `Display.getHdrCapabilities()` сообщает о поддержке; иначе — SDR.
- Аудио: Opus с наибольшим битрейтом, язык — оригинальная дорожка, если в настройках не выбран другой.
- Ручная смена качества в плеере пересобирает источник и возвращает позицию. Адаптивное переключение на лету в MVP не делаем.

### 5.4 Контракт

```kotlin
interface PlayerController {
    val state: StateFlow<PlayerState>      // isPlaying, position, duration, buffered, speed, quality, error
    fun load(videoId: String, startPositionMs: Long = 0)
    fun play(); fun pause(); fun seekTo(ms: Long); fun seekBy(deltaMs: Long)
    fun setSpeed(speed: Float)             // 0.25–3.0
    fun setQuality(height: Int); fun setSubtitle(lang: String?); fun setAudioTrack(id: String)
    fun release()
}
```

### 5.5 Управление с пульта

| Кнопка | Панель скрыта | Панель показана |
| --- | --- | --- |
| OK / Play-Pause | Пауза или воспроизведение, показать панель | Нажать кнопку в фокусе |
| Влево / Вправо | Перемотка −10 / +10 с; при удержании шаг растёт до 60 с | Движение по полосе или по кнопкам |
| Вверх | Показать панель | Переход между рядами |
| Вниз | Показать ряд «Похожие видео» | Переход между рядами |
| Back | Выйти из плеера | Скрыть панель |
| Перемотка вперёд / назад | ±30 с | ±30 с |

Панель скрывается через 5 с без нажатий. Кнопки панели: качество, скорость, субтитры, аудиодорожка, канал, подписаться, комментарии (после MVP).

### 5.6 Поведение

- Позиция сохраняется в БД каждые 5 с и при выходе. При повторном открытии видео продолжается с неё, если просмотрено от 5% до 95%.
- По окончании: автовоспроизведение следующего (из плейлиста или первое похожее) с 10-секундным отсчётом и кнопкой «Отмена»; отключается в настройках.
- На время воспроизведения — `FLAG_KEEP_SCREEN_ON`; `MediaSession` через `media3-session`, чтобы работали медиакнопки пульта и голосовые команды «пауза».
- При ошибке источника (403, 410, обрыв) — до 2 автоматических повторов с перезапросом ссылок, затем экран ошибки.
- Автоподстройка частоты кадров дисплея (после MVP): `Window.attributes.preferredDisplayModeId` под частоту кадров видео, возврат при выходе.

## 6. Реклама и моды

Рекламы YouTube в приложении нет по построению (раздел 4); моды ниже убирают то, что вшито в сами ролики, и меняют внешний вид. Каждый мод — отдельный модуль `data:*` с выключателем в настройках; сбой мода никогда не мешает воспроизведению (таймаут 3 с, ошибка молча игнорируется).

### 6.1 SponsorBlock (MVP)

Базовый адрес — `https://sponsor.ajay.app` ([документация API](https://wiki.sponsor.ajay.app/w/API_Docs)). Используем приватный вариант запроса по префиксу хэша: сервер не узнаёт, какое именно видео смотрят.

```text
GET /api/skipSegments/{prefix}?categories=["sponsor","selfpromo","interaction","intro","outro","preview","music_offtopic","filler"]&actionTypes=["skip"]

prefix = первые 4 символа hex(SHA-256(videoId))

Ответ 200:
[ { "videoID": "...",
    "segments": [ { "segment": [12.5, 48.0], "UUID": "...", "category": "sponsor",
                    "actionType": "skip", "locked": 0, "votes": 5,
                    "videoDuration": 612.3, "description": "" } ] } ]
Ответ 404: сегментов нет — это не ошибка.
```

Логика:

1. При `load(videoId)` параллельно с загрузкой видео запросить сегменты; из массива ответа взять элемент, чей `videoID` точно равен нашему.
2. Отбросить сегменты короче 1 с и сегменты категорий, выключенных в настройках.
3. Для каждого сегмента поставить `player.createMessage { seekTo(end) }.setPosition(startMs).setDeleteAfterDelivery(false).send()`. Опрос позиции по таймеру не использовать.
4. При пропуске показать на 3 с плашку «Пропущено: спонсор» с кнопкой «Вернуть» (перемотка к началу сегмента и отключение его до конца просмотра).
5. Если пользователь сам перемотал внутрь сегмента — не пропускать.
6. Сегменты рисуются на полосе прогресса цветом категории.

Режим каждой категории в настройках: «пропускать», «показать кнопку пропуска», «выключено». По умолчанию `sponsor` и `selfpromo` — пропускать, остальные выключены.

Лицензия данных — CC BY-NC-SA 4.0 ([страница базы](https://sponsor.ajay.app/database)): нужна строка атрибуции на экране «О приложении» и запрещено коммерческое использование. Отправку своих сегментов и голосов не реализуем.

### 6.2 DeArrow (после MVP)

DeArrow заменяет кликбейтные заголовки и обложки на нейтральные, предложенные сообществом. API на том же сервере ([документация](https://wiki.sponsor.ajay.app/w/API_Docs/DeArrow)).

```text
GET https://sponsor.ajay.app/api/branding/{prefix}        // prefix — как в SponsorBlock
Ответ: { "<videoID>": {
   "titles":     [ { "title": "...", "original": false, "votes": 3, "locked": false, "UUID": "..." } ],
   "thumbnails": [ { "timestamp": 123.4, "original": false, "votes": 2, "locked": false, "UUID": "..." } ],
   "randomTime": 0.42, "videoDuration": 612.3 } }

GET https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID={id}&time={timestamp}
Ответ 200: картинка. Ответ 204: картинки нет — показать оригинальную обложку.
```

Правила: брать первый элемент `titles` и `thumbnails` (сервер сортирует по качеству), только если он `locked` или `votes >= 0`; элемент с `original: true` означает «оставить оригинал». Из заголовка удалить служебные символы `>` перед словами. Запросы делаются лениво для видимых карточек, результат кэшируется в БД на 24 часа. Карточка сначала показывает оригинал и подменяет его без мигания.

### 6.3 Return YouTube Dislike (после MVP)

```text
GET https://returnyoutubedislikeapi.com/votes?videoId={id}
Ответ: { "id": "...", "likes": 31885, "dislikes": 579721, "rating": 1.21, "viewCount": 3762293, "deleted": false }
404 — видео нет в базе, 400 — неверный id.
```

Формат ответа — из [README проекта](https://github.com/Anarios/return-youtube-dislike). Число дизлайков — оценка, а не точное значение; в интерфейсе показывать со знаком «≈» под заголовком в панели плеера.

### 6.4 Собственные моды интерфейса

Все — переключатели в настройках, реализуются фильтрами над списками в репозиториях:

- Скрыть Shorts из лент, поиска и каналов (по типу элемента и длительности ≤ 60 с).
- Скрыть просмотренные видео (просмотрено ≥ 90%).
- Чёрный список каналов и слов в заголовках.
- Скорость по умолчанию и отдельная скорость для каждого канала.
- Настраиваемый порядок и видимость разделов бокового меню.

Новый мод добавляется так: ключ в `SettingsRepository` → переключатель в `feature:settings` → фильтр или декоратор в соответствующем репозитории или в `PlayerController`. Код экранов при этом не меняется.

## 7. Аккаунт, подписки, история

В MVP аккаунт Google не нужен: подписки и история живут в локальной базе на устройстве. NewPipeExtractor вход в аккаунт не поддерживает, поэтому синхронизация с аккаунтом вынесена в отдельную фазу с исследованием.

### 7.1 Локальные подписки (MVP)

- Кнопка «Подписаться» на странице канала и в плеере пишет канал в таблицу `subscription`.
- Лента подписок: для каждого канала вызвать `channelFeed` (лёгкий RSS-запрос), не более 6 запросов одновременно, объединить, отсортировать по дате, сохранить в `feed_item`.
- Обновление ленты: при открытии раздела, если с прошлого раза прошло больше 30 минут, и по кнопке «Обновить». Старая лента показывается сразу из базы.
- Импорт (после MVP): файл `subscriptions.csv` из Google Takeout. На ТВ файл передаётся через встроенный HTTP-сервер: приложение показывает адрес вида `http://192.168.x.x:8765` и QR-код, пользователь загружает файл с телефона.
- Экспорт и резервная копия: один JSON-файл с подписками, историей и настройками, тем же способом.

### 7.2 Локальная история (MVP)

Каждый просмотр пишет строку в `watch_history` с позицией и длительностью. Раздел «История» показывает последние 500 видео, ряд «Продолжить просмотр» на главной — недосмотренные. В настройках — «Очистить историю» и «Не вести историю».

### 7.3 Вход в аккаунт Google (фаза 8, необязательная)

Это единственная часть ТЗ, которую нельзя расписать до конца заранее. Официального способа для сторонних клиентов нет; SmartTube делает это через собственную библиотеку MediaServiceCore, имитируя ТВ-клиент YouTube (ввод кода на youtube.com/activate). Механизм недокументирован и меняется без предупреждения.

Порядок работы в этой фазе:

1. Исследование (не больше 2 дней): изучить текущую реализацию входа в [MediaServiceCore](https://github.com/yuliskov/MediaServiceCore) и проверить, работает ли она сегодня.
2. Решение: если работает — написать отдельный модуль `:data:account` с интерфейсом `AccountRepository` (вход по коду, обновление токена, подписки, история, «Смотреть позже»). Если нет — остаться на локальных данных и импорте.
3. Токены хранить только в зашифрованном виде (Android Keystore), в логи не писать.

Предупреждение для владельца: вход через неофициальный клиент нарушает условия YouTube и может привести к ограничениям аккаунта. Безопаснее использовать отдельный аккаунт, а не основной.

## 8. Экраны и навигация

В приложении восемь экранов и боковое меню; всё управляется стрелками и кнопками OK и Back.

| Экран | Маршрут | Содержимое |
| --- | --- | --- |
| Главная | `Home` | Ряды: «Продолжить просмотр», «Подписки — новое», «В тренде» |
| Поиск | `Search` | Поле ввода, подсказки, голосовой ввод, сетка результатов, фильтр «видео / каналы / плейлисты» |
| Подписки | `Subscriptions` | Лента новых видео, ряд аватаров каналов |
| История | `History` | Сетка просмотренных видео с полосой прогресса |
| Канал | `Channel(channelId)` | Шапка, кнопка подписки, вкладки «Видео / Трансляции / Плейлисты» |
| Плейлист | `Playlist(playlistId)` | Список видео, «Воспроизвести всё» |
| Плеер | `Watch(videoId, playlistId?, startMs?)` | Видео на весь экран, панель управления, ряд похожих |
| Обзор | `Explore` | Плитки тем (первая — «Атмосфера»: камин, дождь, океан и т. п. длинные видео-фоны); тема открывается с чипами подтем и сеткой видео из поиска |
| Настройки | `Settings` | Разделы из пункта 9.2, «О приложении», обновление |

Требования к интерфейсу:

- Боковое меню — `NavigationDrawer` из `tv-material`: свёрнуто до иконок, раскрывается при фокусе. Пункты: Поиск, Главная, Подписки, История, Настройки.
- Карточка видео: обложка 16:9, длительность, заголовок в 2 строки, канал, просмотры и дата, полоса прогресса. В фокусе — увеличение 1,05 и рамка.
- Долгое нажатие OK на карточке — меню: «Открыть канал», «Подписаться», «Не показывать канал», «Удалить из истории».
- Фокус никогда не теряется: при возврате на экран восстанавливается на том же элементе (`focusRestorer`), при первой загрузке — на первой карточке.
- Подгрузка следующей страницы — когда до конца списка остаётся 6 карточек.
- Back на корневом экране: сначала фокус в меню, второе нажатие — диалог выхода.
- Тёмная тема, отступы безопасной зоны 48 dp по горизонтали и 27 dp по вертикали, шрифт не мельче 14 sp.
- Голосовой поиск: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`; если на устройстве нет распознавателя — кнопка скрыта.

Манифест (`app/src/main/AndroidManifest.xml`) обязан содержать:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<application android:banner="@drawable/tv_banner" ...>   <!-- баннер 320×180 px -->
  <activity android:name=".MainActivity" android:exported="true"
            android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize">
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
    <intent-filter>   <!-- открытие ссылок youtube.com/watch и youtu.be -->
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="https" android:host="www.youtube.com" android:pathPrefix="/watch" />
      <data android:scheme="https" android:host="youtu.be" />
    </intent-filter>
  </activity>
</application>
```

## 9. Данные и настройки

Локально хранятся пять таблиц Room и один файл настроек DataStore; ничего не отправляется на сторонние серверы, кроме запросов из разделов 4 и 6.

### 9.1 Схема БД (`core:database`, файл `lumen.db`, версия 1)

| Таблица | Поля | Ключ и индексы |
| --- | --- | --- |
| `subscription` | `channel_id`, `name`, `avatar_url`, `subscribed_at`, `playback_speed` (nullable) | PK `channel_id` |
| `feed_item` | `video_id`, `channel_id`, `title`, `thumbnail_url`, `duration_s`, `published_at`, `is_live`, `is_short` | PK `video_id`; индекс `published_at`; FK на `subscription` с каскадным удалением |
| `watch_history` | `video_id`, `title`, `channel_id`, `channel_name`, `thumbnail_url`, `duration_ms`, `position_ms`, `watched_at` | PK `video_id`; индекс `watched_at` |
| `blocked_channel` | `channel_id`, `name`, `blocked_at` | PK `channel_id` |
| `branding_cache` | `video_id`, `title` (nullable), `thumbnail_time` (nullable), `fetched_at` | PK `video_id` |

Схема экспортируется в `core/database/schemas/` (`exportSchema = true`); каждое изменение схемы — новая версия и миграция с тестом. `fallbackToDestructiveMigration` запрещён.

### 9.2 Настройки (`core:datastore`)

| Раздел | Ключ | Тип | По умолчанию |
| --- | --- | --- | --- |
| Плеер | `player.max_resolution` | Int (0 = по экрану) | 0 |
| Плеер | `player.default_speed` | Float | 1.0 |
| Плеер | `player.preferred_codec` | `AUTO / AV1 / VP9 / AVC` | AUTO |
| Плеер | `player.autoplay_next` | Boolean | true |
| Плеер | `player.subtitles_lang` | String? | null |
| Плеер | `player.audio_lang` | String? (null = оригинал) | null |
| Плеер | `player.match_frame_rate` | Boolean | false |
| SponsorBlock | `sb.enabled` | Boolean | true |
| SponsorBlock | `sb.category.<имя>` | `SKIP / BUTTON / OFF` | `sponsor`, `selfpromo` = SKIP; остальные OFF |
| Моды | `dearrow.titles`, `dearrow.thumbnails` | Boolean | false |
| Моды | `ryd.enabled` | Boolean | false |
| Лента | `feed.hide_shorts` | Boolean | true |
| Лента | `feed.hide_watched` | Boolean | false |
| Лента | `feed.blocked_words` | Set\<String> | пусто |
| Общие | `general.content_country`, `general.content_language` | String | из системы |
| Общие | `general.history_enabled` | Boolean | true |
| Обновления | `update.check_on_start` | Boolean | true |

`SettingsRepository` отдаёт каждую настройку как `Flow<T>` и имеет `suspend fun set(...)`. Экраны и плеер читают настройки только через него.

### 9.3 Кэши

- HTTP-кэш OkHttp — 50 МБ в `cacheDir/http`.
- Картинки Coil — дисковый кэш 100 МБ, память 15% от доступной.
- Видео на диск не кэшируется.

## 10. Сборка, подпись, обновления, установка

Результат сборки — один подписанный APK, который ставится на ТВ вручную и дальше обновляет себя сам из релизов вашего репозитория на GitHub.

### 10.1 Окружение разработки

- JDK 17, Android SDK (последняя стабильная платформа и build-tools), переменная `ANDROID_HOME`.
- Эмулятор Android TV 1080p (системный образ «Android TV» или «Google TV») и реальное устройство через `adb connect <ip>:5555`.
- Команды: `./gradlew :app:assembleDebug`, `./gradlew :app:installDebug`, `./gradlew test`, `./gradlew lint`, `./gradlew :app:assembleRelease`.

### 10.2 Варианты сборки

| Вариант | applicationId | Особенности |
| --- | --- | --- |
| `debug` | `tv.lumen.app.debug` | Логи включены, без минификации, ставится рядом с release |
| `release` | `tv.lumen.app` | R8 (`isMinifyEnabled = true`, `isShrinkResources = true`), подпись своим ключом |

Разделение по ABI не нужно: в проекте нет собственного нативного кода.

### 10.3 Подпись

1. Один раз создать ключ: `keytool -genkeypair -v -keystore lumen-release.jks -alias lumen -keyalg RSA -keysize 4096 -validity 10000`.
2. Файл `.jks` и пароли хранить вне репозитория. Пути и пароли читаются из `keystore.properties` (в `.gitignore`) или из переменных окружения `LUMEN_KEYSTORE`, `LUMEN_KEYSTORE_PASSWORD`, `LUMEN_KEY_ALIAS`, `LUMEN_KEY_PASSWORD`.
3. Потеря ключа = невозможность обновлять приложение поверх установленного. Сделать резервную копию.

### 10.4 Версии и релизы

- `versionName` — `МАЖОР.МИНОР.ПАТЧ`, `versionCode` — целое, растёт на 1 с каждым релизом; оба в `gradle.properties`.
- GitHub Actions `.github/workflows/release.yml`: по тегу `v*` собрать `assembleRelease`, подписать ключом из секретов, приложить `lumen-<versionName>.apk` к релизу.
- `.github/workflows/ci.yml`: на каждый push — `lint`, `test`, `assembleDebug`.

### 10.5 Самообновление (`:data:updater`)

1. При запуске (не чаще раза в 3 часа, а также сразу после ошибки воспроизведения) и по кнопке в настройках: `GET https://api.github.com/repos/<owner>/<repo>/releases/latest`.
2. Сравнить `tag_name` с текущим `versionName`; если новее — показать диалог с текстом из поля `body`.
3. Скачать первый файл `.apk` из `assets[].browser_download_url` в `cacheDir/updates/`, показывая прогресс.
4. Установить через `PackageInstaller` (сессия → запись → commit); система сама спросит подтверждение. Если нет разрешения на установку из неизвестных источников — открыть `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
5. Владелец и имя репозитория — константы `BuildConfig.UPDATE_REPO_OWNER` и `UPDATE_REPO_NAME` из `gradle.properties`. Репозиторий релизов должен быть публичным, иначе запрос без токена не пройдёт.

Быстрое обновление критично: когда YouTube ломает экстракцию, починка — это новый APK с новой версией экстрактора.

### 10.6 Первая установка на телевизор

- Через ADB: включить на ТВ режим разработчика и отладку по сети, затем `adb connect <ip>:5555` и `adb install lumen.apk`.
- Без компьютера: скачать APK на ТВ приложением-загрузчиком по прямой ссылке на файл релиза или перенести на USB-накопителе.

### 10.7 Автоматическая починка: сборка при выходе новой версии экстрактора

GitHub каждый час проверяет, вышла ли новая версия NewPipeExtractor, и если да — сам собирает, подписывает и публикует новый релиз приложения. Телевизор забирает его механизмом из пункта 10.5. Участие человека нужно только когда сборка или тесты падают.

Файл `.github/workflows/extractor-update.yml`:

- Запуск: `schedule` с cron `17 * * * *` (каждый час на 17-й минуте) и `workflow_dispatch` для ручного запуска. Права: `contents: write`, `issues: write`.
- Защита от параллельных запусков: `concurrency: extractor-update`.

Шаги:

1. Получить последнюю версию: `GET https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest`, поле `tag_name`.
2. Сравнить со значением `newpipeExtractor` в `gradle/libs.versions.toml`. Совпадает — завершить работу.
3. Записать новую версию в `libs.versions.toml`. Увеличить `versionCode` на 1 и патч-номер `versionName` в `gradle.properties`.
4. Выполнить `./gradlew test lint :app:assembleRelease`. JitPack собирает новую версию библиотеки при первом обращении, поэтому при ошибке загрузки зависимости повторить до 5 раз с паузой 2 минуты.
5. При успехе: закоммитить изменения в `main`, поставить тег `v<versionName>`, создать релиз с подписанным APK и текстом «Обновлён NewPipeExtractor до <версия>».
6. При неудаче: ничего не публиковать, открыть issue «Автосборка не прошла: NewPipeExtractor <версия>» с хвостом лога. GitHub пришлёт уведомление на почту. Повторно issue для той же версии не создавать.

Секреты репозитория (Settings → Secrets and variables → Actions): `LUMEN_KEYSTORE_BASE64` (файл ключа в base64), `LUMEN_KEYSTORE_PASSWORD`, `LUMEN_KEY_ALIAS`, `LUMEN_KEY_PASSWORD`.

Ограничения, которые нужно знать:

- Живые тесты YouTube (`liveTest`) в этом workflow запускаются, но релиз не блокируют: с серверов GitHub YouTube может отвечать проверкой на бота, и тест упадёт без реальной поломки. Блокируют релиз только сборка, модульные тесты и линт.
- Если новая версия экстрактора изменила свой интерфейс, сборка упадёт на шаге 4. Тогда нужно открыть Claude Code с промптом: «Автосборка упала на версии <версия> NewPipeExtractor, issue №N. Обнови код модуля :data:youtube под новый API, прогони тесты, выпусти релиз».
- Расписание GitHub не точное: запуск может опаздывать на 5–30 минут в часы высокой нагрузки. Чаще раза в час проверять нет смысла — экстрактор выходит несколько раз в месяц.
- GitHub отключает запуск по расписанию в репозитории, где 60 дней не было активности. Автоматические коммиты из шага 5 активностью считаются не всегда; если придёт письмо об отключении — включить workflow заново на вкладке Actions.
- Скорость починки ограничена командой NewPipe: пока они не выпустили исправление, собирать нечего.

Итоговая задержка от выхода исправления экстрактора до обновления на телевизоре: до 1 часа на проверку, около 15 минут на сборку, до 3 часов на проверку обновлений приложением — или сразу, если видео не запустилось.

## 11. Этапы разработки

Работа идёт девятью фазами строго по порядку; MVP готов после фазы 6. Каждая фаза заканчивается работающей сборкой, пройденными тестами и отдельным коммитом; к следующей не переходить, пока критерий не выполнен на эмуляторе Android TV.

| Фаза | Что делаем | Критерий готовности |
| --- | --- | --- |
| 0. Каркас | Gradle-проект, все модули из раздела 3 (пустые), version catalog, Hilt, тема, манифест, CI | `assembleDebug` проходит; приложение видно в лаунчере ТВ и открывает пустой экран с боковым меню |
| 1. Данные YouTube | `core:model`, `core:network`, `data:youtube` целиком (раздел 4), мапперы, обработка ошибок | Интеграционные тесты возвращают тренды, результаты поиска и `StreamSet` с потоками ≥ 1080p для тестового видео |
| 2. Плеер | `player` (раздел 5), экран `Watch` с панелью, выбор качества, скорости, субтитров | Видео по зашитому id играет в 1080p со звуком; перемотка, пауза и смена качества работают с пульта; прямой эфир играет |
| 3. Навигация и каталог | `core:designsystem`, экраны `Home`, `Search`, `Channel`, `Playlist`, пагинация, голосовой поиск | От запуска до воспроизведения найденного видео можно дойти одним пультом; фокус не теряется при возврате назад |
| 4. Библиотека | `core:database`, `data:library`, подписки с лентой, история, «Продолжить просмотр» | Подписка переживает перезапуск; лента из 20 каналов обновляется быстрее 10 с; видео продолжается с сохранённой позиции |
| 5. SponsorBlock и настройки | `data:sponsorblock`, пропуск сегментов, `core:datastore`, экран `Settings`, фильтры ленты из 6.4 | На видео с известным спонсорским сегментом происходит пропуск с плашкой; каждая настройка из 9.2 меняет поведение без перезапуска |
| 6. Релиз 1.0 | `data:updater`, release-сборка с R8, подпись, workflow релиза и автоматической сборки из пункта 10.7, экран «О приложении» с лицензиями | Release-APK ставится на реальный ТВ, проходит чек-лист раздела 12, обновляется с версии 1.0.0 на тестовую 1.0.1 |
| 7. Моды | `data:dearrow`, `data:ryd`, комментарии, автоподстройка частоты кадров, импорт и экспорт подписок | Каждый мод включается и выключается в настройках; при недоступном сервере мода приложение работает как без него |
| 8. Аккаунт Google (необязательно) | Исследование и `data:account` по пункту 7.3 | Вход по коду работает, подписки аккаунта видны в ленте — или зафиксирован отказ с причиной в `docs/DECISIONS.md` |

Правила ведения работы:

- В начале каждой фазы Claude Code пишет план фазы в `docs/PROGRESS.md` списком задач, в конце — отмечает выполненное и список известных проблем.
- Любое отступление от ТЗ записывается в `docs/DECISIONS.md`: что изменено и почему.
- Одна фаза — одна ветка `phase-N-<имя>` и один merge в `main`.

## 12. Тестирование и приёмка

Приёмка версии 1.0 — это зелёные автотесты плюс ручной чек-лист на реальном телевизоре.

| Уровень | Что проверяем | Инструменты |
| --- | --- | --- |
| Модульные | Мапперы, выбор потоков по настройкам, логика пропуска сегментов, фильтры ленты, сравнение версий, ViewModel | JUnit, kotlinx-coroutines-test, Turbine |
| Сеть модов | Разбор ответов SponsorBlock, DeArrow, RYD, GitHub Releases; коды 404 и таймауты | OkHttp MockWebServer, JSON-фикстуры в `src/test/resources` |
| БД | DAO и миграции | Room in-memory, `MigrationTestHelper` |
| Интеграция с YouTube | Живые запросы: тренды, поиск, канал, потоки | Отдельная задача `./gradlew :data:youtube:liveTest`, не входит в CI на каждый push, запускается по расписанию раз в сутки |
| UI | Переходы фокуса, состояния Loading / Content / Error | Compose UI Test с эмуляцией клавиш D-pad |

Ежесуточный `liveTest` — система раннего оповещения: его падение означает, что YouTube что-то изменил и пора обновлять экстрактор.

Ручной чек-лист перед релизом:

- [ ] Холодный запуск до главной ленты — не дольше 4 с на тестовом устройстве
- [ ] От нажатия на карточку до первого кадра — не дольше 5 с
- [ ] Видео 1080p60 и 4K играют без пропуска кадров 10 минут
- [ ] Прямой эфир играет 10 минут
- [ ] Перемотка удержанием, смена качества, скорости и субтитров работают с пульта
- [ ] Спонсорский сегмент пропускается, кнопка «Вернуть» работает
- [ ] После часа паузы воспроизведение продолжается
- [ ] Без сети — понятное сообщение и кнопка «Повторить» на каждом экране, без падений
- [ ] Подписки, история и настройки сохраняются после перезапуска и обновления приложения
- [ ] Ни одного рекламного ролика за 30 минут просмотра разных видео
- [ ] Самообновление скачивает и ставит новую версию
- [ ] Фокус всегда виден и никогда не «пропадает» ни на одном экране

## 13. Риски и ограничения

Главный риск проекта — не код, а YouTube: он меняет внутренние механизмы без предупреждения, и приложение будет периодически ломаться. Это нужно принять как постоянную статью поддержки.

| Риск | Последствие | Что заложено в ТЗ |
| --- | --- | --- |
| YouTube меняет выдачу потоков (SABR, PoToken, новые проверки) | Видео не играют или доступно только 360p | Экстрактор изолирован в одном модуле; ежесуточный `liveTest`; самообновление; запасной путь 360p |
| NewPipeExtractor не успевает за YouTube | Простой на дни или недели | Можно временно подключить dev-ветку экстрактора по хэшу коммита через JitPack |
| Слабое железо приставок | Рывки интерфейса, пропуск кадров | Только аппаратные декодеры; ленивые списки; чек-лист производительности |
| Недоступность серверов модов | Нет пропуска спонсоров, заголовков DeArrow | Таймаут 3 с, тихий отказ, воспроизведение не зависит от модов |
| Вход в аккаунт через неофициальный клиент | Ограничения или блокировка аккаунта | В MVP аккаунта нет; фаза 8 необязательна; рекомендация отдельного аккаунта |
| Потеря ключа подписи | Нельзя обновить установленное приложение | Резервная копия ключа (пункт 10.3) |

Правовая сторона (я не юрист, это общие сведения, а не консультация):

- Просмотр YouTube через сторонний клиент без рекламы противоречит условиям использования YouTube. Публикация такого приложения в Google Play или Amazon Appstore невозможна.
- В названии, иконке и баннере не использовать слово YouTube и его логотип.
- NewPipeExtractor — GPLv3: при передаче APK другим людям исходный код приложения должен быть открыт под той же лицензией.
- Данные SponsorBlock и DeArrow — CC BY-NC-SA 4.0: только некоммерческое использование и атрибуция. Монетизировать приложение с этими модами нельзя без отдельного разрешения автора.
- Приложение не скачивает видео и не обходит платный доступ — это зафиксировано в рамках (раздел 1) и менять это не следует.

## 14. Запуск в Claude Code

Чтобы Claude Code работал без поисков и догадок, в пустом репозитории должны лежать два файла: этот документ как `docs/SPEC.md` и короткий `CLAUDE.md` с правилами.

Подготовка:

1. Создать пустой репозиторий на GitHub и клонировать его.
2. Экспортировать этот документ в Markdown и сохранить как `docs/SPEC.md`.
3. Создать в корне `CLAUDE.md` с текстом ниже.
4. Установить JDK 17 и Android SDK, создать эмулятор Android TV.
5. Запустить Claude Code в папке репозитория и отправить первый промпт.

Файл `CLAUDE.md`:

```markdown
# Lumen — YouTube-клиент для Android TV

Полное ТЗ: docs/SPEC.md. Оно — источник истины. Прогресс: docs/PROGRESS.md. Отступления от ТЗ: docs/DECISIONS.md.

## Команды
- Сборка: ./gradlew :app:assembleDebug
- Установка на эмулятор или ТВ: ./gradlew :app:installDebug
- Тесты: ./gradlew test
- Линт: ./gradlew lint
- Живые тесты YouTube: ./gradlew :data:youtube:liveTest

## Жёсткие правила
- Работать по фазам из раздела 11 SPEC.md, строго по порядку. Не начинать следующую фазу, пока критерий готовности текущей не выполнен.
- Классы org.schabi.newpipe.* импортируются только в модуле :data:youtube.
- Все версии зависимостей — только в gradle/libs.versions.toml. Только стабильные версии.
- UI — только Compose и androidx.tv.material3. Никаких XML-макетов, Leanback, Fragment.
- Каждый интерактивный элемент достижим с D-pad и имеет видимое состояние фокуса. Тач-жестов нет.
- Сеть и БД — только в Dispatchers.IO. Никаких блокирующих вызовов в главном потоке.
- Наружу из data-модулей выходят Result и AppError, исключения не пробрасывать.
- Сбой любого мода (SponsorBlock, DeArrow, RYD) не должен мешать воспроизведению.
- Строки интерфейса — только в strings.xml (ru и en).
- Не коммитить ключи, пароли, keystore. Не добавлять аналитику и телеметрию.
- Не реализовывать скачивание видео и обход платного контента.

## При сомнениях
- API NewPipeExtractor и Media3 сверять по исходникам подключённых версий (кэш Gradle), а не по памяти.
- Если ТЗ не покрывает ситуацию — выбрать простейшее решение, записать его в docs/DECISIONS.md и продолжить.
- Если видео не играет (403, пустые потоки) — сначала проверить, есть ли новая версия NewPipeExtractor, затем пункт 4.5 SPEC.md.
```

Первый промпт:

```text
Прочитай CLAUDE.md и docs/SPEC.md целиком. Затем выполни фазу 0 из раздела 11:
создай Gradle-проект со всеми модулями из раздела 3, version catalog, Hilt, тему,
манифест из раздела 8 и CI из пункта 10.4. Перед началом запиши план фазы в docs/PROGRESS.md.
В конце убедись, что ./gradlew :app:assembleDebug проходит, отметь выполненное в PROGRESS.md
и сделай коммит. К фазе 1 не переходи — дождись моего подтверждения.
```

Промпт для каждой следующей фазы: «Выполни фазу N из раздела 11 SPEC.md. План — в PROGRESS.md, критерий готовности проверь на эмуляторе, затем коммит». После каждой фазы стоит самому запустить приложение и пройти критерий вручную.

## 15. Источники

- [NewPipeExtractor — релизы](https://github.com/TeamNewPipe/NewPipeExtractor/releases) — версия v0.26.5, обход SABR в v0.26.3
- [NewPipeExtractor — подключение через JitPack и правила ProGuard](https://javadoc.jitpack.io/p/teamnewpipe/newpipeextractor)
- [NewPipeExtractor PR #1508 — обход принудительного SABR](https://github.com/TeamNewPipe/NewPipeExtractor/pull/1508)
- [NewPipe — релизы](https://github.com/TeamNewPipe/NewPipe/releases) — ограничение обхода для детских видео
- [NewPipe PR #11955 — реализация PoToken в приложении](https://github.com/TeamNewPipe/NewPipe/pull/11955)
- [yt-dlp — PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide) — что такое PoToken и где он требуется
- [Media3 — релизы](https://developer.android.com/jetpack/androidx/releases/media3) — стабильная 1.10.1
- [Compose for TV — релизы](https://developer.android.com/jetpack/androidx/releases/tv) — tv-material 1.1.0
- [SponsorBlock — документация API](https://wiki.sponsor.ajay.app/w/API_Docs)
- [DeArrow — документация API](https://wiki.sponsor.ajay.app/w/API_Docs/DeArrow)
- [SponsorBlock — лицензия базы и API](https://sponsor.ajay.app/database)
- [Return YouTube Dislike — README с описанием API](https://github.com/Anarios/return-youtube-dislike)
- [SmartTube — обзор архитектуры](https://deepwiki.com/yuliskov/SmartTube)

Не проверено по источникам и записано по общему знанию библиотек: точные имена методов NewPipeExtractor в пунктах 4.2–4.5, правило очистки символа `>` в заголовках DeArrow, полный список категорий SponsorBlock. Claude Code сверяет их с исходниками и документацией в фазах 1, 5 и 7.

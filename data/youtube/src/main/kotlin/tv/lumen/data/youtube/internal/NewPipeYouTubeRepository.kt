package tv.lumen.data.youtube.internal

import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelDetails
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.Comment
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken
import tv.lumen.core.model.PlaylistSummary
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import java.util.Locale
import tv.lumen.core.model.Video
import tv.lumen.core.model.VideoDetails
import tv.lumen.core.model.channelUrl
import tv.lumen.core.model.playlistUrl
import tv.lumen.core.model.videoUrl
import tv.lumen.data.youtube.AuthorizedPlayer
import tv.lumen.data.youtube.YouTubeInitializer
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import tv.lumen.data.youtube.YouTubeRepository
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NewPipeYouTubeRepository @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
    /** Запрос от имени аккаунта для видео «для детей» (см. [AuthorizedPlayer]); пусто — не подключён. */
    private val authorizedPlayer: java.util.Optional<AuthorizedPlayer> = java.util.Optional.empty(),
) : YouTubeRepository {

    private val youtube get() = ServiceList.YouTube

    /** Ссылки на потоки живут несколько часов; держим не дольше часа (SPEC 4.4). */
    private val details = TimedCache<String, CachedVideo>(ttlMillis = 60 * 60 * 1000L, maxSize = 20)

    private class CachedVideo(val details: VideoDetails, val raw: StreamInfo)

    private suspend fun <T> call(block: () -> T): AppResult<T> = extract(io) {
        YouTubeInitializer.ensureInitialized(client)
        block()
    }

    override suspend fun trending(): AppResult<List<Video>> = call {
        // Страница «В тренде» у YouTube может пропадать; тогда берём киоск по умолчанию.
        val kiosks = youtube.kioskList
        val ids = listOf(YoutubeTrendingExtractor.KIOSK_ID, kiosks.defaultKioskId).distinct()
        var lastError: Exception? = null
        for (id in ids) {
            try {
                val url = kiosks.getListLinkHandlerFactoryByType(id).fromId(id).url
                val videos = Mappers.videos(KioskInfo.getInfo(youtube, url).relatedItems)
                if (videos.isNotEmpty()) return@call videos
            } catch (e: Exception) {
                lastError = e
            }
        }
        lastError?.let { throw it }
        emptyList()
    }

    /** Чарты YouTube «Музыка в тренде» (киоск trending_music); не во всех странах. */
    override suspend fun musicTrending(): AppResult<List<Video>> = call {
        try {
            kioskVideos(MUSIC_KIOSK_ID)
        } catch (e: org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException) {
            // YouTube Charts есть не для всех стран (например, нет для Азербайджана): вместо чарта
            // берём поиск популярной музыки на языке контента
            musicSearchFallback()
        }
    }

    /**
     * «Музыка в тренде» для страны без YouTube Charts: песни на языке РЕГИОНА (страна контента, а не язык
     * интерфейса), найденные несколькими запросами и отсортированные по [trendScore] — чем больше
     * просмотров и чем свежее выпуск, тем выше.
     */
    private fun musicSearchFallback(): List<Video> {
        val country = NewPipe.getPreferredContentCountry().countryCode.ifBlank { "US" }
        val language = RegionLanguages.languageFor(country)
        val year = java.time.Year.now().value
        val queries = when (language) {
            "ru" -> listOf("популярная музыка $year", "новые песни $year", "хиты $year", "русские хиты", "новинки музыки", "популярные клипы $year")
            "tr" -> listOf("popüler müzik $year", "yeni şarkılar $year", "türkçe pop hitleri", "en çok dinlenen şarkılar", "yeni klipler $year")
            "az" -> listOf("ən yaxşı mahnılar $year", "yeni mahnılar $year", "azərbaycan mahnıları", "hit mahnılar", "yeni klip $year")
            "en" -> listOf("top music hits $year", "new songs $year", "best songs", "pop hits $year", "popular music videos")
            else -> {
                // Для остальных стран — название страны на её языке, чтобы поиск дал местную музыку
                val locale = Locale(language, country)
                val name = locale.getDisplayCountry(locale).ifBlank { country }
                listOf("$name top songs $year", "$name new songs $year", "$name music hits", "$name popular music", "$name music videos $year")
            }
        }
        val videos = LinkedHashMap<String, Video>()
        for (query in queries) {
            runCatching {
                val handler = youtube.searchQHFactory.fromQuery(query, listOf(searchFilter(SearchFilter.VIDEOS)), "")
                SearchInfo.getInfo(youtube, handler).relatedItems.mapNotNull(Mappers::searchItem)
            }.getOrNull()?.forEach { item ->
                val v = (item as? SearchItem.VideoItem)?.video ?: return@forEach
                if (!v.isLive && !v.isShort && v.durationSec in 90..900) videos.putIfAbsent(v.id, v)
            }
        }
        if (videos.isEmpty()) throw ExtractionException("Нет музыки для региона")
        val now = Instant.now()
        return videos.values.sortedByDescending { trendScore(it, now) }
    }

    /** Чарт YouTube «Трейлеры фильмов и сериалов» (киоск trending_movies_and_shows). */
    override suspend fun trailers(): AppResult<List<Video>> = call { kioskVideos(TRAILERS_KIOSK_ID) }

    private fun kioskVideos(kioskId: String): List<Video> {
        val kiosks = youtube.kioskList
        val url = kiosks.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).url
        return Mappers.videos(KioskInfo.getInfo(youtube, url).relatedItems)
    }

    /**
     * Популярное в регионе из настроек (страна контента): то, что реально смотрят в этой стране, на её языке.
     * Общие киоски YouTube («В тренде» и т. п.) не годятся: они отдают мировой список, а не страны. Поэтому
     * берётся поиск на языке региона несколькими запросами («<страна> популярное», «самое просматриваемое»…),
     * без эфиров и Shorts, и результат сортируется по [trendScore]: больше просмотров и свежее выпуск — выше.
     * Киоски — только запасной вариант, если поиск ничего не дал.
     */
    override suspend fun regionalPopular(): AppResult<List<Video>> = call {
        val country = NewPipe.getPreferredContentCountry().countryCode.ifBlank { "US" }
        val language = RegionLanguages.languageFor(country)
        val collected = LinkedHashMap<String, Video>()
        // Запросы идут параллельно: так список готов за время одного запроса, а не пяти
        val found = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            regionalQueries(country).map { query ->
                async {
                    runCatching {
                        val handler = youtube.searchQHFactory.fromQuery(query, listOf(searchFilter(SearchFilter.VIDEOS)), "")
                        SearchInfo.getInfo(youtube, handler).relatedItems.mapNotNull(Mappers::searchItem)
                    }.getOrNull().orEmpty()
                }
            }.awaitAll()
        }
        for (item in found.flatten()) {
            val video = (item as? SearchItem.VideoItem)?.video ?: continue
            if (!video.isLive && !video.isShort && video.durationSec >= 60 && titleFitsRegion(video.title, language)) {
                collected.putIfAbsent(video.id, video)
            }
        }
        if (collected.size < MIN_REGIONAL) {
            val kiosks = youtube.kioskList
            for (id in REGIONAL_KIOSKS) {
                runCatching {
                    val url = kiosks.getListLinkHandlerFactoryByType(id).fromId(id).url
                    Mappers.videos(KioskInfo.getInfo(youtube, url).relatedItems)
                }.getOrNull()?.filter { !it.isLive && !it.isShort && titleFitsRegion(it.title, language) }?.forEach { collected.putIfAbsent(it.id, it) }
            }
        }
        if (collected.isEmpty()) throw ExtractionException("Нет популярного для региона $country")
        val now = Instant.now()
        collected.values.sortedByDescending { trendScore(it, now) }
    }

    override suspend fun search(query: String, filter: SearchFilter, page: PageToken?) = call {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(searchFilter(filter)), "")
        if (page == null) {
            val info = SearchInfo.getInfo(youtube, handler)
            Page(info.relatedItems.mapNotNull(Mappers::searchItem), PageTokens.encode(info.nextPage))
        } else {
            SearchInfo.getMoreItems(youtube, handler, PageTokens.decode(page)).toPage(Mappers::searchItem)
        }
    }

    override suspend fun searchSuggestions(query: String) = call {
        if (query.isBlank()) emptyList() else youtube.suggestionExtractor.suggestionList(query).orEmpty()
    }

    override suspend fun videoDetails(videoId: String, forceRefresh: Boolean): AppResult<VideoDetails> {
        if (forceRefresh) details.remove(videoId) else details.get(videoId)?.let { return AppResult.Success(it.details) }
        val primary = call {
            val info = StreamInfo.getInfo(youtube, videoUrl(videoId))
            val mapped = Mappers.videoDetails(videoId, info, Instant.now())
            details.put(videoId, CachedVideo(mapped, info))
            mapped
        }
        // «Видео недоступно» от экстрактора: так он отвечает на видео «для детей» (v0.27 их не поддерживает).
        // Пробуем телевизионный клиент от имени аккаунта, как SmartTube; без входа остаётся прежняя ошибка.
        if (primary is AppResult.Failure && (primary.error as? AppError.ContentUnavailable)?.reason == tv.lumen.core.model.UnavailableReason.OTHER) {
            // Сначала клиент YouTube Kids (без входа, прямые ссылки), затем телевизионный клиент от имени аккаунта
            kidsFallback(videoId)?.let { return AppResult.Success(it) }
            tvFallback(videoId)?.let { return AppResult.Success(it) }
        }
        return primary
    }

    /**
     * Видео «для детей» через клиент приложения YouTube Kids для Android: он отдаёт такие видео без входа,
     * а ссылки на потоки приходят прямыми, без шифрования (проверено 2026-11-18: 1080p/2160p, ответ 206).
     */
    private suspend fun kidsFallback(videoId: String): VideoDetails? = kotlinx.coroutines.withContext(io) {
        try {
            val localization = NewPipe.getPreferredLocalization()
            val country = NewPipe.getPreferredContentCountry().countryCode
            val clientInfo = org.json.JSONObject()
                .put("clientName", "ANDROID_KIDS").put("clientVersion", KIDS_CLIENT_VERSION)
                .put("hl", localization.languageCode).put("gl", country)
                .put("androidSdkVersion", 31).put("osName", "Android").put("osVersion", "12")
            val body = org.json.JSONObject()
                .put("context", org.json.JSONObject().put("client", clientInfo))
                .put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", KIDS_USER_AGENT)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = org.json.JSONObject(response.body.string())
                TvPlayerParser.unplayableReason(root)?.let {
                    android.util.Log.w(TV_TAG, "Клиент YouTube Kids не отдал видео: $it")
                    return@withContext null
                }
                TvPlayerParser.details(videoId, root, Instant.now(), decipher = { it }, throttling = { it })
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TV_TAG, "Запрос клиента YouTube Kids не удался: ${e.javaClass.simpleName}")
            null
        }
    }

    override suspend fun kidsSearch(query: String, language: String): AppResult<List<Video>> = kotlinx.coroutines.withContext(io) {
        try {
            val country = NewPipe.getPreferredContentCountry().countryCode
            val clientInfo = org.json.JSONObject()
                .put("clientName", "ANDROID_KIDS").put("clientVersion", KIDS_CLIENT_VERSION)
                .put("hl", language).put("gl", country)
                .put("androidSdkVersion", 31).put("osName", "Android").put("osVersion", "12")
                .put(
                    "kidsAppInfo",
                    org.json.JSONObject().put(
                        "contentSettings",
                        org.json.JSONObject()
                            .put("corpusPreference", "KIDS_CORPUS_PREFERENCE_YOUNGER")
                            .put("kidsNoSearchMode", "YT_KIDS_NO_SEARCH_MODE_OFF"),
                    ),
                )
            val body = org.json.JSONObject().put("context", org.json.JSONObject().put("client", clientInfo)).put("query", query)
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", KIDS_USER_AGENT)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext AppResult.Failure(AppError.Network)
                AppResult.Success(KidsSearchParser.parse(org.json.JSONObject(response.body.string())))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            AppResult.Failure(AppError.Network)
        } catch (e: Exception) {
            android.util.Log.w(TV_TAG, "Поиск YouTube Kids не удался: ${e.javaClass.simpleName}")
            AppResult.Failure(AppError.ExtractionBroken)
        }
    }

    private suspend fun tvFallback(videoId: String): VideoDetails? {
        val player = authorizedPlayer.orElse(null) ?: return null
        val sts = kotlinx.coroutines.withContext(io) { runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull() }
        val json = player.playerJson(videoId, sts) ?: return null
        return kotlinx.coroutines.withContext(io) {
            try {
                val root = org.json.JSONObject(json)
                TvPlayerParser.unplayableReason(root)?.let {
                    android.util.Log.w(TV_TAG, "Телевизионный клиент тоже не отдал видео: $it")
                    return@withContext null
                }
                TvPlayerParser.details(
                    videoId, root, Instant.now(),
                    decipher = { YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, it) },
                    throttling = { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, it) },
                ).also { if (it == null) android.util.Log.w(TV_TAG, "В ответе телевизионного клиента нет потоков") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TV_TAG, "Разбор ответа телевизионного клиента не удался", e)
                null
            } catch (e: StackOverflowError) {
                null
            }
        }
    }

    override suspend fun dashManifest(videoId: String, streamId: String): AppResult<String> {
        val cached = details.get(videoId) ?: when (val fresh = videoDetails(videoId, forceRefresh = true)) {
            is AppResult.Failure -> return fresh
            is AppResult.Success -> details.get(videoId)
        } ?: return AppResult.Failure(AppError.ExtractionBroken)

        return call {
            val raw = cached.raw
            val stream: Stream = (raw.videoOnlyStreams.orEmpty() + raw.audioStreams.orEmpty() + raw.videoStreams.orEmpty())
                .firstOrNull { it.id == streamId && it.deliveryMethod == DeliveryMethod.DASH }
                ?: throw IllegalArgumentException("DASH-поток $streamId не найден у видео $videoId")
            val itag = stream.itagItem ?: throw IllegalStateException("У потока $streamId нет ItagItem")
            if (raw.streamType == StreamType.POST_LIVE_STREAM) {
                YoutubePostLiveStreamDvrDashManifestCreator.fromPostLiveStreamDvrStreamingUrl(
                    stream.content, itag, itag.targetDurationSec, raw.duration,
                )
            } else {
                YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itag, raw.duration)
            }
        }
    }

    override suspend fun channel(channelId: ChannelId): AppResult<ChannelDetails> = call {
        Mappers.channelDetails(ChannelInfo.getInfo(youtube, channelUrl(channelId)))
    }

    override suspend fun channelVideos(channelId: ChannelId, tab: ChannelTab, page: PageToken?): AppResult<Page<Video>> {
        require(tab != ChannelTab.PLAYLISTS) { "Для вкладки плейлистов есть channelPlaylists()" }
        return call { channelTabPage(channelId, tab, page) { (it as? org.schabi.newpipe.extractor.stream.StreamInfoItem)?.let(Mappers::video) } }
    }

    override suspend fun channelPlaylists(channelId: ChannelId, page: PageToken?): AppResult<Page<PlaylistSummary>> = call {
        channelTabPage(channelId, ChannelTab.PLAYLISTS, page) { (it as? PlaylistInfoItem)?.let(Mappers::playlist) }
    }

    private fun <T : Any> channelTabPage(
        channelId: ChannelId,
        tab: ChannelTab,
        page: PageToken?,
        map: (InfoItem) -> T?,
    ): Page<T> {
        val handler = youtube.channelTabLHFactory.fromQuery(
            Mappers.channelPath(channelId),
            listOf(Mappers.channelTabName(tab)),
            "",
        )
        return if (page == null) {
            val info = ChannelTabInfo.getInfo(youtube, handler)
            Page(info.relatedItems.mapNotNull(map), PageTokens.encode(info.nextPage))
        } else {
            ChannelTabInfo.getMoreItems(youtube, handler, PageTokens.decode(page)).toPage(map)
        }
    }

    override suspend fun channelFeed(channelId: ChannelId): AppResult<List<Video>> = call {
        Mappers.videos(FeedInfo.getInfo(youtube, channelUrl(channelId)).relatedItems)
    }

    override suspend fun playlist(playlistId: String, page: PageToken?): AppResult<Page<Video>> = call {
        val url = playlistUrl(playlistId)
        if (page == null) {
            val info = PlaylistInfo.getInfo(youtube, url)
            Page(Mappers.videos(info.relatedItems), PageTokens.encode(info.nextPage))
        } else {
            PlaylistInfo.getMoreItems(youtube, url, PageTokens.decode(page)).toPage(Mappers::video)
        }
    }

    override suspend fun comments(videoId: String, page: PageToken?): AppResult<Page<Comment>> = call {
        val url = videoUrl(videoId)
        if (page == null) {
            val info = CommentsInfo.getInfo(youtube, url)
            Page(info.relatedItems.map(Mappers::comment), PageTokens.encode(info.nextPage))
        } else {
            CommentsInfo.getMoreItems(youtube, url, PageTokens.decode(page)).toPage(Mappers::comment)
        }
    }

    private fun <I : InfoItem, T : Any> InfoItemsPage<I>.toPage(map: (I) -> T?): Page<T> =
        Page(items.mapNotNull(map), PageTokens.encode(nextPage))

    private fun searchFilter(filter: SearchFilter): String = when (filter) {
        SearchFilter.ALL -> YoutubeSearchQueryHandlerFactory.ALL
        SearchFilter.VIDEOS -> YoutubeSearchQueryHandlerFactory.VIDEOS
        SearchFilter.CHANNELS -> YoutubeSearchQueryHandlerFactory.CHANNELS
        SearchFilter.PLAYLISTS -> YoutubeSearchQueryHandlerFactory.PLAYLISTS
        SearchFilter.MUSIC_SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
        SearchFilter.MUSIC_VIDEOS -> YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
        SearchFilter.MUSIC_ALBUMS -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
        SearchFilter.MUSIC_ARTISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
        SearchFilter.MUSIC_PLAYLISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
    }

    private companion object {
        // Идентификатор киоска из YoutubeTrendingMusicLinkHandlerFactory (NewPipeExtractor v0.27.1)
        const val MUSIC_KIOSK_ID = "trending_music"
        const val TRAILERS_KIOSK_ID = "trending_movies_and_shows"

        // Региональные киоски экстрактора v0.27.1; «Trending» — YoutubeTrendingExtractor.KIOSK_ID
        val REGIONAL_KIOSKS = listOf(
            YoutubeTrendingExtractor.KIOSK_ID, "trending_music", "trending_gaming", "trending_podcasts_episodes",
        )
        const val MIN_REGIONAL = 12
    }
}

/**
 * Оценка «трендовости»: просмотры, делённые на возраст в днях в степени 0,7 (+3 дня, чтобы свежий выпуск
 * не получал бесконечный вес). Больше просмотров — выше; при равных просмотрах выше свежее. Видео без числа
 * просмотров идут в конец, без даты выпуска считаются месячной давности.
 */
internal fun trendScore(video: Video, now: Instant): Double {
    if (video.viewCount <= 0) return 0.0
    val ageDays = video.publishedAt?.let { java.time.Duration.between(it, now).toHours() / 24.0 }?.coerceAtLeast(0.0) ?: 30.0
    return video.viewCount / Math.pow(ageDays + 3.0, 0.7)
}

/** Запросы «популярное сейчас» на языке региона [country], с названием страны; для редких языков — по названию страны. */
internal fun regionalQueries(country: String): List<String> {
    val year = java.time.Year.now().value
    val language = RegionLanguages.languageFor(country)
    val locale = java.util.Locale(language, country)
    val name = locale.getDisplayCountry(locale).ifBlank { country }
    return when (language) {
        "ru" -> listOf("$name популярное $year", "популярные видео $year", "в тренде сейчас $name", "новое и популярное", "самое просматриваемое за неделю")
        "tr" -> listOf("$name gündem videolar $year", "$name popüler videolar", "en çok izlenenler $name", "gündemdeki videolar", "yeni popüler videolar $year")
        "az" -> listOf("$name populyar videolar $year", "$name trend videolar", "ən çox izlənənlər $name", "gündəm videoları", "yeni populyar videolar $year")
        "en" -> listOf("$name trending videos $year", "most popular videos this week $name", "popular now $name", "most watched $name")
        else -> listOf("$name trending $year", "$name popular videos", "$name most watched this week", "$name new videos $year")
    }
}

/**
 * Грубая проверка письма: для стран с кириллицей заголовок должен её содержать, для латиницы
 * (турецкий, азербайджанский и т. д.) не должен состоять в основном из кириллицы, иероглифов или арабского.
 * Не определяет язык точно, но отсекает явно чужое.
 */
internal fun titleFitsRegion(title: String, language: String): Boolean {
    val letters = title.filter { it.isLetter() }
    if (letters.isEmpty()) return true
    val cyrillic = letters.count { it in '\u0400'..'\u04FF' }
    return when (language) {
        "ru", "uk", "be", "kk", "ky", "tg" -> cyrillic > 0
        // В Азербайджане много русскоязычного, поэтому кириллицу там не отсекаем
        "az" -> letters.count { it.code > 0x2E80 || it in '؀'..'ۿ' } * 2 < letters.length
        "en", "tr", "de", "fr", "es", "it", "pt", "pl", "nl", "ro", "uz" -> cyrillic * 2 < letters.length &&
            letters.count { it.code > 0x2E80 || it in '\u0600'..'\u06FF' } * 2 < letters.length
        else -> true
    }
}

private const val TV_TAG = "Lumen/TvPlayer"
private const val KIDS_CLIENT_VERSION = "10.26.4"
private const val KIDS_USER_AGENT = "com.google.android.apps.youtube.kids/$KIDS_CLIENT_VERSION (Linux; U; Android 12) gzip"

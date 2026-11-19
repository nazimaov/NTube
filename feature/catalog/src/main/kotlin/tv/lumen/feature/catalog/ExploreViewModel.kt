package tv.lumen.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.AppResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.Video
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

/**
 * «Обзор»: плитки тем, внутри темы — чипы подтем и сетка видео. Видео — результаты обычного поиска
 * по запросу темы или подтемы; короткие ролики отсеиваются по длительности, эфиры остаются.
 */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
    private val userLists: tv.lumen.core.datastore.UserLists,
    settings: tv.lumen.core.datastore.SettingsRepository,
) : ViewModel() {

    /** Страна контента и режим «язык по региону» из настроек; язык подборки считает [tv.lumen.core.datastore.ContentLang]. */
    val region: kotlinx.coroutines.flow.StateFlow<Pair<String?, Boolean>> = settings.flow { it.contentCountry to it.contentByRegion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null to true)

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val raw = MutableStateFlow(PagedState<Video>())
    private val minDuration = MutableStateFlow(0L)
    private var pager: Pager<Video>? = null
    private var collectJob: Job? = null
    private var shown: String? = null
    private val liveOnly = MutableStateFlow(false)
    private val sortByDate = MutableStateFlow(false)
    private var current: Triple<Topic, Subtopic?, QueryLang>? = null
    private var currentLive = false

    val results: StateFlow<PagedState<Video>> = combine(raw, minDuration, filter.rules, liveOnly, sortByDate) { state, min, rules, live, sort ->
        val items = rules.videos(state.items).filter { if (live) it.isLive else it.isLive || it.durationSec >= min }
        state.copy(items = if (sort) items.sortedByDescending { it.publishedAt?.toEpochMilli() ?: Long.MIN_VALUE } else items)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagedState())

    /** Показать тему [topic] (или её подтему [sub]); тот же запрос повторно не грузится. */
    fun show(topic: Topic, sub: Subtopic?, lang: QueryLang, live: Boolean = false) {
        current = Triple(topic, sub, lang)
        currentLive = live
        minDuration.value = topic.minDurationSec
        val spec = topic.live.takeIf { live }
        liveOnly.value = spec != null
        sortByDate.value = topic.sortByDate && spec == null
        val kidsForYou = topic.id == "kids" && sub == null
        val channels = spec?.channels?.get(lang).orEmpty()
        val keywords = sub?.liveKeywords.orEmpty()
        val query = if (kidsForYou) "kids:mix:${lang.name}" else if (channels.isNotEmpty()) "channels:" + channels.joinToString("|") + "#" + keywords.joinToString(",") else (sub?.query ?: topic.query).at(lang)
        if (shown == query) return
        shown = query
        focusMemory.reset()
        collectJob?.cancel()
        val subPools = if (topic.id == "kids" && !kidsForYou) kidsPools(sub?.query ?: topic.query, lang) else emptyList()
        val p = Pager<Video>(viewModelScope, { it.id }) { token ->
            when {
                kidsForYou -> kidsMix(topic, lang, first = token == null)
                topic.id == "kids" -> kidsLoad(subPools)
                channels.isNotEmpty() -> liveOfChannels(channels, keywords)
                else -> load(query, token)
            }
        }
        pager = p
        collectJob = viewModelScope.launch { p.state.collect { raw.value = it } }
        p.refresh()
    }

    private suspend fun load(query: String, token: PageToken?): AppResult<Page<Video>> =
        when (val r = youtube.search(query, SearchFilter.VIDEOS, token)) {
            is AppResult.Success -> AppResult.Success(
                Page(r.value.items.filterIsInstance<SearchItem.VideoItem>().map { it.video }, r.value.next),
            )
            is AppResult.Failure -> r
        }

    private val channelIds = HashMap<String, ChannelId?>()

    /**
     * Текущие трансляции указанных каналов (параллельно); канал, который не нашёлся или не в эфире, пропускается.
     * [keywords] — отбор по заголовку для подтемы; пусто — все эфиры.
     */
    private suspend fun liveOfChannels(names: List<String>, keywords: List<String>): AppResult<Page<Video>> = coroutineScope {
        val results = names.map { name ->
            async {
                val id = channelIds[name] ?: findChannel(name)?.also { channelIds[name] = it }
                if (id == null) null else youtube.channelVideos(id, ChannelTab.LIVE, null)
            }
        }.awaitAll()
        val videos = LinkedHashMap<String, Video>()
        var failure: AppResult.Failure? = null
        for (r in results) when (r) {
            is AppResult.Success -> r.value.items.filter { it.isLive && matches(it, keywords) }.forEach { videos.putIfAbsent(it.id, it) }
            is AppResult.Failure -> failure = r
            null -> Unit
        }
        // Ошибка сети показывается, только если ничего не нашлось
        if (videos.isEmpty() && failure != null) failure else AppResult.Success(Page(videos.values.toList(), null))
    }

    /** Канал по имени: точное совпадение важнее вхождения. */
    private suspend fun findChannel(name: String): ChannelId? {
        val r = youtube.search(name, SearchFilter.CHANNELS, null) as? AppResult.Success ?: return null
        val channels = r.value.items.filterIsInstance<SearchItem.ChannelItem>().map { it.channel }
        val key = name.normalized()
        return (channels.firstOrNull { it.name.normalized() == key } ?: channels.firstOrNull { it.name.normalized().contains(key) })?.id
    }

    private fun matches(video: Video, keywords: List<String>): Boolean =
        keywords.isEmpty() || video.title.normalized().let { t -> keywords.any { t.contains(it.normalized()) } }

    private fun String.normalized() = lowercase().replace("i\u0307", "i").replace("ə", "e").replace("ı", "i").replace("ü", "u")
        .replace("ö", "o").replace("ç", "c").replace("ş", "s").replace("ğ", "g")

    /**
     * Источник «Детского»: один поиск YouTube Kids с подгрузкой страниц. В буфере — уже полученные, ещё не показанные видео.
     * [maxPages] — сколько страниц брать, прежде чем перейти к следующему источнику (сначала язык региона, потом дубляж).
     */
    private class KidsPool(val query: String, val lang: String, val extra: Boolean = false, val maxPages: Int = Int.MAX_VALUE) {
        val buffer = ArrayDeque<Video>()
        var next: PageToken? = null
        var pages = 0
        var done = false
        val exhausted get() = done && buffer.isEmpty()
    }

    /** Дозагрузить страницу источника; при ошибке источник закрывается, ошибка возвращается. */
    private suspend fun KidsPool.fetch(keep: (Video) -> Boolean = { true }): AppResult.Failure? {
        if (done) return null
        return when (val r = youtube.kidsSearch(query, lang, next)) {
            is AppResult.Success -> {
                pages++
                next = r.value.next
                if (next == null || pages >= maxPages || r.value.items.isEmpty()) done = true
                r.value.items.filter(keep).forEach(buffer::addLast)
                null
            }
            is AppResult.Failure -> { done = true; r }
        }
    }

    /**
     * Источники для запроса подтемы: язык региона; для Азербайджана после азербайджанских — турецкие (детское там
     * в основном с турецким дубляжом). Одинаковые запросы (например «Like Nastya Türkçe») не повторяются.
     */
    private fun kidsPools(query: Map<QueryLang, String>, lang: QueryLang): List<KidsPool> {
        if (lang != QueryLang.AZ) return listOf(KidsPool(query.at(lang), lang.name.lowercase()))
        val az = query.at(QueryLang.AZ)
        val tr = query.at(QueryLang.TR)
        return if (tr == az) listOf(KidsPool(az, "az")) else listOf(KidsPool(az, "az", maxPages = AZ_PAGES), KidsPool(tr, "tr"))
    }

    /** Подтема «Детского»: страница за страницей — сначала источник языка региона, когда он кончится — следующий. */
    private suspend fun kidsLoad(pools: List<KidsPool>): AppResult<Page<Video>> {
        var failure: AppResult.Failure? = null
        val out = ArrayList<Video>()
        for (pool in pools) {
            if (pool.exhausted) continue
            if (pool.buffer.isEmpty()) pool.fetch()?.let { failure = it }
            while (pool.buffer.isNotEmpty()) out += pool.buffer.removeFirst()
            if (out.isNotEmpty()) break
        }
        val more = pools.any { !it.exhausted }
        val error = failure
        if (out.isEmpty() && error != null && !more) return error
        return AppResult.Success(Page(out, if (more) PageToken("more") else null))
    }

    /** Источники «Для вас» текущего показа; пересоздаются при каждом новом показе. */
    private var mixPools: List<KidsPool> = emptyList()

    /**
     * «Для вас» в «Детском»: понемногу из каждой подтемы (по кругу по [MIX_TAKE] видео) и без конца — когда
     * буфер подтемы пустеет, догружается её следующая страница. Подтемы, из которых ребёнок чаще открывал видео,
     * и любимые каналы дают вдвое больше; уже открытые видео не повторяются. В Азербайджане сначала идут все
     * азербайджанские источники, потом турецкие.
     */
    private suspend fun kidsMix(topic: Topic, lang: QueryLang, first: Boolean): AppResult<Page<Video>> {
        val lists = userLists.playlists.value
        val watchedLists = lists.filter { it.id.startsWith(tv.lumen.core.datastore.UserLists.KIDS_WATCHED) }
        val watched = watchedLists.flatMap { it.videos }.mapTo(HashSet()) { it.id }
        if (first) {
            fun weight(sub: Subtopic) = lists.firstOrNull { it.id == kidsListId(sub.id) }?.videos?.size ?: 0
            val subs = topic.subtopics.sortedByDescending(::weight)
            // Каналы, которые ребёнок смотрит чаще всего (до 3): их свежие видео идут первыми — так «Для вас» подстраивается
            val favoriteChannels = watchedLists.flatMap { it.videos }.mapNotNull { it.channelName?.takeIf { n -> n.isNotBlank() } }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
            val code = lang.name.lowercase()
            // Сначала подборки на языке региона, затем любимые каналы (они могут быть на любом языке)
            // В Азербайджане подтема без азербайджанского запроса (Like Nastya, Влад и Никита — только турецкий дубляж)
            // считается турецкой и идёт после азербайджанских
            fun subLang(sub: Subtopic) = if (lang == QueryLang.AZ && sub.query.at(QueryLang.AZ) == sub.query.at(QueryLang.TR)) "tr" else code
            val main = subs.map { KidsPool(it.query.at(lang), subLang(it), extra = weight(it) > 0) } +
                favoriteChannels.map { KidsPool(it, code, extra = true) }
            val second = if (lang == QueryLang.AZ) {
                subs.map { KidsPool(it.query.at(QueryLang.TR), "tr", extra = weight(it) > 0) }.filter { p -> main.none { it.query == p.query } }
            } else emptyList()
            mixPools = main + second
        }
        val pools = mixPools
        val keep: (Video) -> Boolean = { it.id !in watched && !it.isShort && !it.isLive }
        // Догрузить тех, у кого в буфере мало (параллельно)
        val failures = coroutineScope {
            pools.filter { !it.done && it.buffer.size < MIX_TAKE * 2 }.map { async { it.fetch(keep) } }.awaitAll()
        }.filterNotNull()
        if (pools.all { it.exhausted }) {
            return if (first && failures.isNotEmpty()) failures.first() else AppResult.Success(Page(emptyList(), null))
        }
        val out = ArrayList<Video>()
        val seen = HashSet<String>()
        // Сначала по кругу источники языка региона, затем — дубляж (в Азербайджане турецкий)
        val dubbed = lang == QueryLang.AZ
        for (group in listOf(pools.filter { !(dubbed && it.lang == "tr") }, pools.filter { dubbed && it.lang == "tr" })) {
            group.forEach { pool ->
                repeat(if (pool.extra) MIX_TAKE * 2 else MIX_TAKE) {
                    while (pool.buffer.isNotEmpty()) { val v = pool.buffer.removeFirst(); if (seen.add(v.id)) { out += v; break } }
                }
            }
        }
        val more = pools.any { !it.exhausted }
        return AppResult.Success(Page(out, if (more) PageToken("more") else null))
    }

    private fun kidsListId(subId: String?) = tv.lumen.core.datastore.UserLists.KIDS_WATCHED + (subId?.let { "_$it" } ?: "")

    /** Запоминает открытое в «Детском» видео и подтему, из которой его открыли, — для «Для вас». */
    fun markKidsWatched(video: Video, subId: String?) {
        viewModelScope.launch {
            val id = kidsListId(subId)
            userLists.create("kids", id = id)
            userLists.add(id, video)
        }
    }

    fun loadMore() = pager?.loadMore()

    fun retry() {
        val (topic, sub, lang) = current ?: return
        shown = null
        show(topic, sub, lang, currentLive)
    }
}

/** Сколько страниц азербайджанского поиска показать, прежде чем добавлять турецкие (дубляж). */
private const val AZ_PAGES = 4

/** Сколько видео за круг «Для вас» берётся из каждой подтемы. */
private const val MIX_TAKE = 3

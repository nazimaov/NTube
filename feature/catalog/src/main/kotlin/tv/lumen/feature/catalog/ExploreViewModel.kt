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
        val p = Pager<Video>(viewModelScope, { it.id }) { token -> if (kidsForYou) kidsMix(topic, lang) else if (topic.id == "kids") kidsLoad(query, lang) else if (channels.isNotEmpty()) liveOfChannels(channels, keywords) else load(query, token) }
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

    /** Поиск в «Детском» — через YouTube Kids: там только одобренные для детей видео, и все они воспроизводятся. */
    private suspend fun kidsLoad(query: String, lang: QueryLang): AppResult<Page<Video>> =
        when (val r = youtube.kidsSearch(query, lang.name.lowercase())) {
            is AppResult.Success -> AppResult.Success(Page(r.value, null))
            is AppResult.Failure -> r
        }

    /**
     * «Для вас» в «Детском»: понемногу из каждой подтемы (поиск подтем идёт параллельно, по кругу по 1 видео).
     * Подтемы, из которых ребёнок чаще открывал видео, идут первыми и дают больше; уже открытые видео не повторяются.
     */
    private suspend fun kidsMix(topic: Topic, lang: QueryLang): AppResult<Page<Video>> {
        val lists = userLists.playlists.value
        val watched = lists.filter { it.id.startsWith(tv.lumen.core.datastore.UserLists.KIDS_WATCHED) }.flatMap { it.videos }.mapTo(HashSet()) { it.id }
        fun weight(sub: Subtopic) = lists.firstOrNull { it.id == kidsListId(sub.id) }?.videos?.size ?: 0
        val subs = topic.subtopics.sortedByDescending(::weight)
        // Каналы, которые ребёнок смотрит чаще всего (до 3): их свежие видео идут первыми — так «Для вас» подстраивается
        val favoriteChannels = lists.filter { it.id.startsWith(tv.lumen.core.datastore.UserLists.KIDS_WATCHED) }
            .flatMap { it.videos }.mapNotNull { it.channelName?.takeIf { n -> n.isNotBlank() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
        val (channelResults, results) = coroutineScope {
            val ch = favoriteChannels.map { name -> async { kidsLoad(name, lang) } }
            val q = subs.map { s -> async { kidsLoad(s.query.at(lang), lang) } }
            ch.awaitAll() to q.awaitAll()
        }
        val channelPools = channelResults.map { r -> (r as? AppResult.Success)?.value?.items.orEmpty().filter { it.id !in watched && !it.isShort && !it.isLive }.toMutableList() }
        val pools = channelPools + results.map { r -> (r as? AppResult.Success)?.value?.items.orEmpty().filter { it.id !in watched && !it.isShort }.toMutableList() }
        if (pools.all { it.isEmpty() }) return results.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Page(emptyList(), null))
        // Любимые подтемы получают дополнительное место в каждом круге
        val extra = channelPools.map { true } + subs.map { weight(it) > 0 }
        val seen = HashSet<String>()
        val out = ArrayList<Video>()
        while (pools.any { it.isNotEmpty() } && out.size < 80) {
            pools.forEachIndexed { i, pool ->
                repeat(if (extra[i]) 2 else 1) {
                    while (pool.isNotEmpty()) { val v = pool.removeAt(0); if (seen.add(v.id)) { out += v; break } }
                }
            }
        }
        return AppResult.Success(Page(out, null))
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

package tv.lumen.feature.catalog

import androidx.annotation.StringRes

/** Подтема «Обзора»: чип и поисковый запрос на русском и английском (остальные языки — английский). */
data class Subtopic(
    val id: String,
    @StringRes val label: Int,
    val query: Map<QueryLang, String>,
    /** Слова, по которым в «Live» отбираются эфиры этой подтемы (любое из слов в заголовке); пусто — все эфиры. */
    val liveKeywords: List<String> = emptyList(),
)

/**
 * «Прямой эфир» темы: каналы по языкам эфира. Вместо поиска берутся текущие трансляции с вкладки
 * «Прямые эфиры» этих каналов (канал ищется по имени, неподходящее имя просто пропускается);
 * пока ни у одного нет эфира, список пуст. Язык эфира определяется тем, чей это канал.
 */
data class LiveSpec(val channels: Map<QueryLang, List<String>>)

/**
 * Тема «Обзора»: плитка и набор подтем. [minDurationSec] отсеивает короткие ролики; эфиры (длительность
 * неизвестна) остаются. [ambient] — «Атмосфера»: длинные видео для фона.
 */
data class Topic(
    val id: String,
    val emoji: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val query: Map<QueryLang, String>,
    val minDurationSec: Long,
    val subtopics: List<Subtopic>,
    val ambient: Boolean = false,
    /** Есть кнопка «Прямой эфир» (в каждой подтеме и в «Все»). */
    val live: LiveSpec? = null,
    /** Видео в сетке — от новых к старым по дате загрузки. */
    val sortByDate: Boolean = false,
)

private fun q(ru: String, en: String) =
    mapOf(QueryLang.RU to ru, QueryLang.TR to en, QueryLang.AZ to en, QueryLang.EN to en)

private fun q(ru: String, tr: String, az: String, en: String) =
    mapOf(QueryLang.RU to ru, QueryLang.TR to tr, QueryLang.AZ to az, QueryLang.EN to en)

/** Запрос на нужном языке; для языков без перевода — английский. */
fun Map<QueryLang, String>.at(lang: QueryLang): String = this[lang] ?: this.getValue(QueryLang.EN)

object Topics {
    val all: List<Topic> = listOf(
        Topic(
            id = "atmosphere", emoji = "🔥", title = R.string.topic_atmosphere, description = R.string.topic_atmosphere_desc,
            query = q("атмосфера расслабление 4k", "rahatlatıcı atmosfer 4k", "rahatladıcı atmosfer 4k", "ambience relaxing 4k"), minDurationSec = 1800, ambient = true,
            subtopics = listOf(
                Subtopic("fireplace", R.string.sub_atmosphere_fireplace, q("камин 4k", "şömine 4k", "kamin 4k", "fireplace 4k")),
                Subtopic("rain", R.string.sub_atmosphere_rain, q("дождь для сна", "uyku için yağmur sesi", "yuxu üçün yağış səsi", "rain sounds for sleeping")),
                Subtopic("ocean", R.string.sub_atmosphere_ocean, q("шум океана волны 4k", "okyanus dalga sesi 4k", "okean dalğaları səsi 4k", "ocean waves 4k")),
                Subtopic("forest", R.string.sub_atmosphere_forest, q("лес звуки природы 4k", "orman doğa sesleri 4k", "meşə təbiət səsləri 4k", "forest nature ambience 4k")),
                Subtopic("nightcity", R.string.sub_atmosphere_nightcity, q("ночной город атмосфера", "gece şehir atmosferi", "gecə şəhəri atmosferi", "night city ambience")),
                Subtopic("aquarium", R.string.sub_atmosphere_aquarium, q("аквариум 4k", "akvaryum 4k", "akvarium 4k", "aquarium 4k")),
                Subtopic("space", R.string.sub_atmosphere_space, q("космос релакс 4k", "uzay rahatlatıcı 4k", "kosmos rahatladıcı 4k", "space ambient 4k")),
                Subtopic("relax", R.string.sub_atmosphere_relax, q("расслабляющая музыка", "rahatlatıcı müzik", "rahatladıcı musiqi", "relaxing music")),
            ),
        ),
        Topic(
            id = "gaming", emoji = "🎮", title = R.string.topic_gaming, description = R.string.topic_gaming_desc,
            query = q("игры", "oyunlar", "oyunlar", "gaming"), minDurationSec = 120,
            subtopics = listOf(
                Subtopic("walkthroughs", R.string.sub_gaming_walkthroughs, q("прохождение игры", "oyun geçişi", "oyunun keçilişi", "game walkthrough")),
                Subtopic("streams", R.string.sub_gaming_streams, q("стрим игры", "oyun canlı yayın", "oyun canlı yayım", "gaming live stream")),
                Subtopic("reviews", R.string.sub_gaming_reviews, q("обзор игры", "oyun inceleme", "oyun icmalı", "game review")),
                Subtopic("top", R.string.sub_gaming_top, q("топ игр", "en iyi oyunlar", "ən yaxşı oyunlar", "top games")),
                Subtopic("esports", R.string.sub_gaming_esports, q("киберспорт", "espor", "esport", "esports")),
            ),
        ),
        Topic(
            id = "sports", emoji = "⚽", title = R.string.topic_sports, description = R.string.topic_sports_desc,
            query = q("спорт", "spor", "idman", "sports"), minDurationSec = 120, sortByDate = true,
            live = LiveSpec(
                channels = mapOf(
                    QueryLang.RU to listOf("Матч ТВ", "Okko Спорт", "KHL", "Eurosport Россия", "Russian Premier Liga", "РФС ТВ"),
                    QueryLang.TR to listOf("TRT SPOR", "beIN SPORTS Türkiye", "A Spor", "NTV Spor", "Türkiye Futbol Federasyonu", "TVF VOLEYBOL TV"),
                    QueryLang.AZ to listOf("CBC Sport", "İdman Azərbaycan", "İdman TV", "AFFA", "AzPFL", "İctimai Televiziya"),
                    QueryLang.EN to listOf(
                        "beIN SPORTS", "ESPN", "Sky Sports Football", "CBS Sports Golazo", "NBA", "UFC", "FORMULA 1",
                        "DAZN Boxing", "Premier League", "UEFA", "Olympic Games",
                    ),
                ),
            ),
            subtopics = listOf(
                Subtopic("football", R.string.sub_sports_football, q("футбол обзор матча", "futbol maç özeti", "futbol oyunun icmalı", "football match highlights"), liveKeywords = listOf("футбол", "football", "futbol", "soccer", "лига", "league", "liga", "uefa", "champions", "премьер", "premier", "матч", "match", "maç")),
                Subtopic("basketball", R.string.sub_sports_basketball, q("баскетбол лучшие моменты", "basketbol en iyi anlar", "basketbol ən yaxşı anlar", "basketball highlights"), liveKeywords = listOf("баскетбол", "basketball", "basketbol", "nba", "euroleague")),
                Subtopic("fight", R.string.sub_sports_fight, q("бокс mma", "boks mma", "boks mma", "boxing mma fights"), liveKeywords = listOf("бокс", "boxing", "boks", "mma", "ufc", "fight", "бой", "единоборств", "dövüş", "döyüş", "güləş")),
                Subtopic("motor", R.string.sub_sports_motor, q("автоспорт гонки", "otomobil yarışı", "avtomobil yarışı", "motorsport racing"), liveKeywords = listOf("формула", "formula", "f1", "motogp", "гонк", "race", "racing", "yarış", "ralli", "ралли", "nascar")),
                Subtopic("moments", R.string.sub_sports_moments, q("лучшие моменты матчей", "maçların en iyi anları", "oyunların ən yaxşı anları", "best sports moments")),
            ),
        ),
        Topic(
            id = "cooking", emoji = "🍳", title = R.string.topic_cooking, description = R.string.topic_cooking_desc,
            query = q("кулинария рецепты", "yemek tarifleri", "yemək reseptləri", "cooking recipes"), minDurationSec = 120,
            subtopics = listOf(
                Subtopic("recipes", R.string.sub_cooking_recipes, q("рецепты", "yemek tarifleri", "yemək reseptləri", "recipes")),
                Subtopic("cuisines", R.string.sub_cooking_cuisines, q("национальная кухня", "geleneksel mutfak yemekleri", "milli mətbəx yeməkləri", "world cuisine")),
                Subtopic("baking", R.string.sub_cooking_baking, q("выпечка рецепты", "hamur işi ve tatlı tarifleri", "xəmir və şirniyyat reseptləri", "baking recipes")),
                Subtopic("quick", R.string.sub_cooking_quick, q("быстрые блюда", "pratik hızlı yemekler", "tez hazırlanan yeməklər", "quick meals")),
                Subtopic("restaurants", R.string.sub_cooking_restaurants, q("обзор ресторана", "restoran inceleme", "restoran icmalı", "restaurant review")),
            ),
        ),
        Topic(
            id = "tech", emoji = "💻", title = R.string.topic_tech, description = R.string.topic_tech_desc,
            query = q("технологии", "teknoloji", "texnologiya", "technology"), minDurationSec = 120,
            subtopics = listOf(
                Subtopic("phones", R.string.sub_tech_phones, q("обзор смартфона", "akıllı telefon inceleme", "smartfon icmalı", "smartphone review")),
                Subtopic("pc", R.string.sub_tech_pc, q("обзор компьютера", "bilgisayar inceleme", "kompüter icmalı", "pc build review")),
                Subtopic("ai", R.string.sub_tech_ai, q("искусственный интеллект", "yapay zeka", "süni intellekt", "artificial intelligence")),
                Subtopic("gadgets", R.string.sub_tech_gadgets, q("обзор техники", "teknoloji inceleme", "texnika icmalı", "tech review")),
                Subtopic("apps", R.string.sub_tech_apps, q("программы и приложения", "en iyi uygulamalar ve programlar", "ən yaxşı proqramlar və tətbiqlər", "best apps")),
            ),
        ),
        Topic(
            id = "travel", emoji = "✈️", title = R.string.topic_travel, description = R.string.topic_travel_desc,
            query = q("путешествия", "seyahat", "səyahət", "travel"), minDurationSec = 120,
            subtopics = listOf(
                Subtopic("countries", R.string.sub_travel_countries, q("путешествие по странам", "ülke gezi rehberi", "ölkə səyahət bələdçisi", "country travel guide")),
                Subtopic("cities", R.string.sub_travel_cities, q("прогулка по городу", "şehir yürüyüş turu", "şəhər gəzintisi", "city walking tour")),
                Subtopic("vlogs", R.string.sub_travel_vlogs, q("влог путешествие", "seyahat vlog", "səyahət vlog", "travel vlog")),
                Subtopic("hotels", R.string.sub_travel_hotels, q("обзор отеля", "otel inceleme", "otel icmalı", "hotel review")),
                Subtopic("sights", R.string.sub_travel_sights, q("достопримечательности", "gezilecek yerler", "görməli yerlər", "famous landmarks")),
            ),
        ),
        Topic(
            id = "fun", emoji = "🎭", title = R.string.topic_fun, description = R.string.topic_fun_desc,
            query = q("развлечения", "eğlence", "əyləncə", "entertainment"), minDurationSec = 120,
            subtopics = listOf(
                Subtopic("humor", R.string.sub_fun_humor, q("юмор", "komedi", "komediya", "comedy")),
                Subtopic("sketch", R.string.sub_fun_sketch, q("скетч шоу", "skeç komedi", "skeç şou", "sketch comedy")),
                Subtopic("pranks", R.string.sub_fun_pranks, q("пранки", "şakalar", "pranklar", "pranks")),
                Subtopic("interviews", R.string.sub_fun_interviews, q("интервью", "röportaj", "müsahibə", "interview")),
                Subtopic("tvshows", R.string.sub_fun_tvshows, q("телешоу", "tv programı", "televiziya verilişi", "tv show")),
            ),
        ),
        Topic(
            id = "learn", emoji = "🎓", title = R.string.topic_learn, description = R.string.topic_learn_desc,
            query = q("обучение", "eğitim", "təhsil", "learning"), minDurationSec = 120,
            subtopics = listOf(
                Subtopic("languages", R.string.sub_learn_languages, q("изучение языков", "dil öğrenme", "dil öyrənmə", "learn languages")),
                Subtopic("coding", R.string.sub_learn_coding, q("программирование уроки", "programlama dersleri", "proqramlaşdırma dərsləri", "programming tutorial")),
                Subtopic("math", R.string.sub_learn_math, q("математика уроки", "matematik dersleri", "riyaziyyat dərsləri", "math lessons")),
                Subtopic("history", R.string.sub_learn_history, q("история", "tarih belgesel", "tarix sənədli film", "history documentary")),
                Subtopic("masterclass", R.string.sub_learn_masterclass, q("мастер-класс", "workshop usta sınıfı", "master klas", "masterclass")),
            ),
        ),
    )

    /** Раздел «Детское» (отдельный пункт меню, не плитка «Обзора»): подтемы-чипы и рекомендации по просмотрам. */
    val kids = Topic(
        id = "kids", emoji = "🧸", title = R.string.topic_kids, description = R.string.topic_kids_desc,
        query = q("детские видео для детей", "çocuklar için videolar", "uşaqlar üçün videolar", "videos for kids"), minDurationSec = 60,
        subtopics = listOf(
            Subtopic("cartoons", R.string.sub_kids_cartoons, q("мультики для детей", "çocuklar için çizgi film", "uşaqlar üçün cizgi filmlər", "cartoons for kids")),
            Subtopic("learn", R.string.sub_kids_learn, q("развивающие мультики обучение для детей", "çocuklar için eğitici öğretici çizgi film", "uşaqlar üçün təhsil öyrədici cizgi film", "learning videos for kids")),
            Subtopic("music", R.string.sub_kids_music, q("детские песни", "çocuk şarkıları", "uşaq mahnıları", "kids songs nursery rhymes")),
            Subtopic("dance", R.string.sub_kids_dance, q("детские танцы песенки для малышей", "çocuk dans şarkıları", "uşaq rəqs mahnıları", "kids dance songs")),
            Subtopic("tales", R.string.sub_kids_tales, q("сказки для детей на ночь", "çocuklar için masallar", "uşaqlar üçün nağıllar", "bedtime stories for kids")),
            Subtopic("nastya", R.string.sub_kids_nastya, q("Like Nastya", "Like Nastya Türkçe", "Like Nastya Türkçe", "Like Nastya")),
            Subtopic("vlad", R.string.sub_kids_vlad, q("Влад и Никита", "Vlad ve Niki Türkçe", "Vlad ve Niki Türkçe", "Vlad and Niki")),
        ),
    )

    fun byId(id: String?): Topic? = (all + kids).firstOrNull { it.id == id }
}

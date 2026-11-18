package tv.lumen.feature.search

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/** Язык музыки: на нём пишутся запросы настроений и подбираются исполнители. */
enum class MusicLang { RU, TR, AZ, EN }

/**
 * Плитка «Настроение и жанры»: цвета градиента и запрос на каждом языке музыки.
 * Запросы — обычный поиск видео, как у остальных подборок.
 */
data class Mood(
    val id: String,
    @StringRes val label: Int,
    val from: Color,
    val to: Color,
    val terms: Map<MusicLang, String>,
    /** Без отсева по длительности (детское: песни и мультфильмы любой длины). */
    val anyLength: Boolean = false,
)

private fun terms(ru: String, tr: String, az: String, en: String) =
    mapOf(MusicLang.RU to ru, MusicLang.TR to tr, MusicLang.AZ to az, MusicLang.EN to en)

private fun c(argb: Long) = Color(argb)

object MusicBrowse {
    val moods: List<Mood> = listOf(
        Mood("chill", R.string.mood_chill, c(0xFF1E6F8C), c(0xFF0B3B4D), terms("спокойная музыка для расслабления", "sakin müzik rahatlama", "sakit musiqi", "chill relaxing music")),
        Mood("dance", R.string.mood_energy, c(0xFFD9480F), c(0xFF7A2606), terms("танцевальные хиты клубная музыка", "dans müzikleri hit", "rəqs mahnıları", "dance hits")),
        Mood("romance", R.string.mood_romance, c(0xFFB0306A), c(0xFF5A1236), terms("романтические песни о любви", "romantik aşk şarkıları", "romantik sevgi mahnıları", "love songs")),
        Mood("sad", R.string.mood_sad, c(0xFF495057), c(0xFF212529), terms("грустные песни", "hüzünlü şarkılar", "kədərli mahnılar", "sad songs")),
        Mood("pop", R.string.mood_pop, c(0xFFE64980), c(0xFF8A1F48), terms("поп музыка хиты", "pop müzik hitleri", "pop mahnılar", "pop hits")),
        Mood("rock", R.string.mood_rock, c(0xFF862E2E), c(0xFF3F1111), terms("рок музыка", "rock müzik", "rok musiqi", "rock music")),
        Mood("hiphop", R.string.mood_hiphop, c(0xFF6741D9), c(0xFF2F1A73), terms("хип-хоп рэп", "rap müzik", "rep musiqi", "hip hop rap")),
        Mood("electronic", R.string.mood_electronic, c(0xFF0C8599), c(0xFF063F4A), terms("электронная музыка", "elektronik müzik", "elektron musiqi", "electronic music")),
        Mood("classical", R.string.mood_classical, c(0xFF8C6D46), c(0xFF40301D), terms("классическая музыка", "klasik müzik", "klassik musiqi", "classical music")),
    )

    /** Известные исполнители для круглых аватаров; каналы ищутся по имени, не найденные пропускаются. */
    val artists: Map<MusicLang, List<String>> = mapOf(
        MusicLang.RU to listOf(
            "Баста", "Макс Корж", "Мот", "Zivert", "Скриптонит", "Монеточка", "Miyagi", "Полина Гагарина", "Егор Крид", "Тима Белорусских",
        ),
        MusicLang.TR to listOf(
            "Tarkan", "Sezen Aksu", "Mabel Matiz", "Hadise", "Murat Boz", "Ezhel", "Kenan Doğulu", "Sıla", "Gökhan Türkmen", "Simge",
        ),
        MusicLang.AZ to listOf(
            "Röya", "Aygün Kazımova", "Brilliant Dadaşova", "Nigar Camal", "Faiq Ağayev", "Eldar Qasımov", "Emin Ağalarov", "Samir Cavadzadə",
        ),
        MusicLang.EN to listOf(
            "Taylor Swift", "The Weeknd", "Dua Lipa", "Ed Sheeran", "Billie Eilish", "Bruno Mars", "Drake", "Adele", "Coldplay", "Imagine Dragons",
        ),
    )

    /**
     * Язык музыки «Авто»: по стране контента из настроек, а пока страна не выбрана
     * (человек впервые запустил приложение) — по языку интерфейса.
     */
    fun autoLang(contentCountry: String?, uiLanguage: String): MusicLang = when (contentCountry?.uppercase()) {
        "AZ" -> MusicLang.AZ
        "TR" -> MusicLang.TR
        "RU", "BY", "KZ", "KG", "UZ", "UA", "AM", "GE", "MD", "TJ", "TM" -> MusicLang.RU
        null, "" -> when (uiLanguage) {
            "ru" -> MusicLang.RU
            "tr" -> MusicLang.TR
            "az" -> MusicLang.AZ
            else -> MusicLang.EN
        }
        else -> MusicLang.EN
    }
}

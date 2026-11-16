package tv.lumen.core.datastore

import tv.lumen.core.model.SegmentMode
import tv.lumen.core.model.SponsorCategory

enum class PreferredCodec { AUTO, AV1, VP9, AVC }

/** Пункты бокового меню, которые можно скрыть и переставить (SPEC 6.4). «Настройки» всегда видны. */
enum class MenuItem { SEARCH, HOME, MUSIC, MOVIES, SERIES, SUBSCRIPTIONS, HISTORY }

/** На каком языке искать фильмы и сериалы: оригинал (по стране подборки) или дубляж на выбранный язык. */
enum class DubLanguage { ORIGINAL, RU, TR, AZ, EN }

/** Снимок всех настроек (SPEC 9.2). */
data class Settings(
    // Плеер
    val maxResolution: Int = 0,
    val defaultSpeed: Float = 1.0f,
    val preferredCodec: PreferredCodec = PreferredCodec.AUTO,
    val autoplayNext: Boolean = true,
    /** Порядок воспроизведения — кнопка в плеере. */
    val playbackOrder: tv.lumen.core.model.PlaybackOrder = tv.lumen.core.model.PlaybackOrder.SEQUENTIAL,
    val subtitlesLang: String? = null,
    val audioLang: String? = null,
    val matchFrameRate: Boolean = false,
    // SponsorBlock
    val sponsorBlockEnabled: Boolean = true,
    val sponsorCategories: Map<SponsorCategory, SegmentMode> = DEFAULT_SPONSOR_MODES,
    // Моды
    val deArrowTitles: Boolean = false,
    val deArrowThumbnails: Boolean = false,
    val rydEnabled: Boolean = false,
    // Лента
    val hideShorts: Boolean = true,
    val hideWatched: Boolean = false,
    val blockedWords: Set<String> = emptySet(),
    // Общие
    /** null — из системной локали. */
    val contentCountry: String? = null,
    val contentLanguage: String? = null,
    /** Язык интерфейса (BCP 47, например «az» или «pt-BR»); null — как в системе. */
    val appLanguage: String? = null,
    /** Язык фильмов и сериалов (разделы «Фильмы» и «Сериалы»). */
    val catalogDub: DubLanguage = DubLanguage.ORIGINAL,
    val historyEnabled: Boolean = true,
    val menuItems: List<MenuItem> = MenuItem.entries,
    val hiddenMenuItems: Set<MenuItem> = emptySet(),
    // Обновления
    val updateCheckOnStart: Boolean = true,
    /** Текущий локальный профиль (таблица profile); 0 — ещё не выбран. */
    val currentProfileId: Long = 0L,
) {
    fun modeOf(category: SponsorCategory): SegmentMode = sponsorCategories[category] ?: SegmentMode.OFF

    companion object {
        /** По умолчанию sponsor и selfpromo пропускаются, остальные выключены (SPEC 6.1). */
        val DEFAULT_SPONSOR_MODES: Map<SponsorCategory, SegmentMode> = SponsorCategory.entries.associateWith {
            if (it == SponsorCategory.SPONSOR || it == SponsorCategory.SELF_PROMO) SegmentMode.SKIP else SegmentMode.OFF
        }
    }
}
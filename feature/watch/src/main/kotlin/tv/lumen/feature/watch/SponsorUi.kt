package tv.lumen.feature.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import tv.lumen.core.model.SponsorCategory

/** Цвета категорий как в SponsorBlock. */
internal fun categoryColor(category: SponsorCategory): Color = when (category) {
    SponsorCategory.SPONSOR -> Color(0xFF00D400)
    SponsorCategory.SELF_PROMO -> Color(0xFFFFFF00)
    SponsorCategory.INTERACTION -> Color(0xFFCC00FF)
    SponsorCategory.INTRO -> Color(0xFF00FFFF)
    SponsorCategory.OUTRO -> Color(0xFF0202ED)
    SponsorCategory.PREVIEW -> Color(0xFF008FD6)
    SponsorCategory.MUSIC_OFFTOPIC -> Color(0xFFFF9900)
    SponsorCategory.FILLER -> Color(0xFF7300FF)
}

@Composable
internal fun categoryName(category: SponsorCategory): String = stringResource(
    when (category) {
        SponsorCategory.SPONSOR -> R.string.sb_sponsor
        SponsorCategory.SELF_PROMO -> R.string.sb_selfpromo
        SponsorCategory.INTERACTION -> R.string.sb_interaction
        SponsorCategory.INTRO -> R.string.sb_intro
        SponsorCategory.OUTRO -> R.string.sb_outro
        SponsorCategory.PREVIEW -> R.string.sb_preview
        SponsorCategory.MUSIC_OFFTOPIC -> R.string.sb_music_offtopic
        SponsorCategory.FILLER -> R.string.sb_filler
    },
)
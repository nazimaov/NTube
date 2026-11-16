package tv.lumen.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.lumen.core.common.ApplicationScope
import tv.lumen.data.library.LibraryRepository
import tv.lumen.core.datastore.PreferredCodec
import tv.lumen.core.datastore.Settings
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.data.sponsorblock.SponsorBlockRepository
import tv.lumen.data.youtube.YouTubeRepository
import tv.lumen.player.internal.AndroidDeviceCapabilities
import tv.lumen.player.internal.ExoPlayerController
import tv.lumen.player.internal.MediaSourceBuilder
import tv.lumen.player.internal.StreamSelector
import javax.inject.Inject

/** Создаёт плеер для экрана просмотра. Вызывающий обязан вызвать [PlayerController.release]. */
class PlayerControllerFactory @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val repository: YouTubeRepository,
    private val sources: MediaSourceBuilder,
    private val capabilities: AndroidDeviceCapabilities,
    private val library: LibraryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val sponsorBlock: SponsorBlockRepository,
    private val settings: SettingsRepository,
) {
    /** [scope] — scope главного потока (viewModelScope). */
    fun create(scope: CoroutineScope, prefs: PlayerPreferences = fromSettings(settings.current.value)): PlayerController =
        ExoPlayerController(context, scope, repository, sources, StreamSelector(capabilities), prefs, library, appScope, sponsorBlock) { order ->
            appScope.launch { settings.set { it.copy(playbackOrder = order) } }
        }

    /** Настройки читаются при каждом открытии плеера — изменения действуют без перезапуска (SPEC 9.2). */
    private fun fromSettings(s: Settings) = PlayerPreferences(
        maxResolution = s.maxResolution,
        preferredCodec = when (s.preferredCodec) {
            PreferredCodec.AUTO -> CodecPreference.AUTO
            PreferredCodec.AV1 -> CodecPreference.AV1
            PreferredCodec.VP9 -> CodecPreference.VP9
            PreferredCodec.AVC -> CodecPreference.AVC
        },
        audioLanguage = s.audioLang,
        subtitleLanguage = s.subtitlesLang,
        defaultSpeed = s.defaultSpeed,
        autoplayNext = s.autoplayNext,
        playbackOrder = s.playbackOrder,
        sponsorBlockEnabled = s.sponsorBlockEnabled,
        sponsorModes = s.sponsorCategories,
    )
}

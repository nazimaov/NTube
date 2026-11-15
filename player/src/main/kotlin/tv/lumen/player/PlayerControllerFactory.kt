package tv.lumen.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
) {
    /** [scope] — scope главного потока (viewModelScope). */
    fun create(scope: CoroutineScope, prefs: PlayerPreferences = PlayerPreferences()): PlayerController =
        ExoPlayerController(context, scope, repository, sources, StreamSelector(capabilities), prefs)
}

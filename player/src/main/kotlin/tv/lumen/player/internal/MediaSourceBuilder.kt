package tv.lumen.player.internal

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.DeliveryMethod
import tv.lumen.core.model.SubtitleTrack
import tv.lumen.core.model.VideoStream
import tv.lumen.core.model.AudioStream
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

/**
 * Сборка MediaSource по SPEC 5.2. Все запросы идут через общий OkHttpClient.
 *
 * Источник каждой дорожки создаёт DefaultMediaSourceFactory: он сам подключает субтитры VTT
 * (MediaItem.SubtitleConfiguration, разбор при извлечении), а DASH-манифест экстрактора
 * передаётся ему как data:-URI — DefaultDataSource умеет схему data:.
 */
@OptIn(UnstableApi::class)
internal class MediaSourceBuilder @Inject constructor(
    @ApplicationContext context: Context,
    client: OkHttpClient,
    private val repository: YouTubeRepository,
) {
    // Видео не кэшируется на диск (SPEC 9.3): клиент без HTTP-кэша
    private val mediaClient = client.newBuilder().cache(null).build()
    // Потоки googlevideo качаются кусками по 10 МБ: длинные запросы YouTube сильно замедляет (см. ChunkedDataSource)
    private val dataSourceFactory = DataSource.Factory {
        ChunkedDataSource(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(mediaClient)))
    }
    private val factory = DefaultMediaSourceFactory(dataSourceFactory)

    suspend fun separate(
        videoId: String,
        video: VideoStream,
        audio: AudioStream,
        subtitle: SubtitleTrack?,
    ): AppResult<MediaSource> {
        val videoItem = when (val r = itemFor(videoId, video.id, video.url, video.delivery)) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val audioItem = when (val r = itemFor(videoId, audio.id, audio.url, audio.delivery)) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val videoSource = factory.createMediaSource(videoItem.withSubtitle(subtitle))
        val audioSource = factory.createMediaSource(audioItem)
        return AppResult.Success(MergingMediaSource(true, videoSource, audioSource))
    }

    fun muxed(stream: VideoStream, subtitle: SubtitleTrack?): MediaSource =
        factory.createMediaSource(MediaItem.Builder().setUri(stream.url).build().withSubtitle(subtitle))

    /** Прямой эфир: готовый манифест, без ручного выбора потоков (SPEC 5.2, п. 4). */
    fun live(hlsUrl: String?, dashMpdUrl: String?): MediaSource {
        val item = if (hlsUrl != null) {
            MediaItem.Builder().setUri(hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        } else {
            MediaItem.Builder().setUri(dashMpdUrl).setMimeType(MimeTypes.APPLICATION_MPD).build()
        }
        return factory.createMediaSource(item)
    }

    private suspend fun itemFor(videoId: String, streamId: String, url: String, delivery: DeliveryMethod): AppResult<MediaItem> =
        when (delivery) {
            DeliveryMethod.DASH -> when (val manifest = repository.dashManifest(videoId, streamId)) {
                is AppResult.Failure -> manifest
                is AppResult.Success -> AppResult.Success(
                    MediaItem.Builder()
                        .setUri(dataUri(manifest.value))
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .build(),
                )
            }
            else -> AppResult.Success(MediaItem.Builder().setUri(url).build())
        }

    private fun MediaItem.withSubtitle(track: SubtitleTrack?): MediaItem {
        if (track == null) return this
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(track.languageTag)
            .setLabel(track.name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        return buildUpon().setSubtitleConfigurations(listOf(config)).build()
    }

    private fun dataUri(xml: String): Uri =
        Uri.parse("data:application/dash+xml;base64," + Base64.encodeToString(xml.toByteArray(), Base64.NO_WRAP))
}

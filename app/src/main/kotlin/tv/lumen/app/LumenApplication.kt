package tv.lumen.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import tv.lumen.data.youtube.YouTubeInitializer
import javax.inject.Inject

@HiltAndroidApp
class LumenApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var youTubeInitializer: YouTubeInitializer

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // SPEC 4.2: один раз при запуске. Язык и страна — из системной локали, позже из настроек.
        youTubeInitializer.initialize()
    }

    /** Картинки Coil: общий OkHttp, диск 100 МБ, память 15% (SPEC 9.3). */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val appContext: Context = context.applicationContext
        return ImageLoader.Builder(appContext)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(appContext, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
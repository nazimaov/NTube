package tv.lumen.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import tv.lumen.data.youtube.YouTubeInitializer
import javax.inject.Inject

@HiltAndroidApp
class LumenApplication : Application() {

    @Inject
    lateinit var youTubeInitializer: YouTubeInitializer

    override fun onCreate() {
        super.onCreate()
        // SPEC 4.2: один раз при запуске. Язык и страна — из системной локали, позже из настроек.
        youTubeInitializer.initialize()
    }
}

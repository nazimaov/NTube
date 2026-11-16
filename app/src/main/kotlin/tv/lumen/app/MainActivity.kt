package tv.lumen.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import tv.lumen.app.navigation.DeepLinks
import tv.lumen.app.ui.LumenApp
import tv.lumen.core.designsystem.theme.LumenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    /** Видео из внешней ссылки, ещё не открытое. */
    private val pendingVideo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingVideo.value = DeepLinks.videoId(intent?.data)
        setContent {
            LumenTheme {
                Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                    LumenApp(pendingVideoId = pendingVideo.value, onPendingVideoHandled = { pendingVideo.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.videoId(intent.data)?.let { pendingVideo.value = it }
    }
}
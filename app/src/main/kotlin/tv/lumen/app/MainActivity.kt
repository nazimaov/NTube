package tv.lumen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import tv.lumen.app.ui.LumenApp
import tv.lumen.core.designsystem.theme.LumenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                    LumenApp()
                }
            }
        }
    }
}
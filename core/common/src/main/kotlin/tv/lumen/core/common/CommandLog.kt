package tv.lumen.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал команд, пришедших в плеер извне: кнопки пульта и медиаклавиши, команды медиасессии (голосовые
 * помощники вроде Алисы) с именем приложения-отправителя, потеря звука (аудиофокуса). Только в памяти,
 * последние [MAX] записей, никуда не отправляется — для диагностики на устройстве (Настройки → О приложении).
 */
object CommandLog {
    private const val MAX = 40
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _entries.update { (listOf("$time  $text") + it).take(MAX) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

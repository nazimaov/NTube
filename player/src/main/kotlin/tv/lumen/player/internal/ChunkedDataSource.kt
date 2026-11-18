package tv.lumen.player.internal

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Источник, который качает потоки googlevideo кусками по 10 МБ, а не одним длинным запросом.
 * YouTube сильно ограничивает скорость длинных запросов (замерено: ~520 КБ/с против ~16 МБ/с на кусках
 * при том же видео 1080p), поэтому видео зависало даже на хорошем канале. Так же делает NewPipe.
 * Остальные адреса (манифест data:, субтитры) проходят без изменений.
 */
@OptIn(UnstableApi::class)
internal class ChunkedDataSource(factory: DataSource.Factory) : DataSource {

    private val upstream: DataSource = factory.createDataSource()
    private var spec: DataSpec? = null
    private var chunked = false
    /** Абсолютная позиция следующего байта и конец текущего куска. */
    private var position = 0L
    private var chunkEnd = 0L
    /** Абсолютный конец ресурса, если известен (clen в адресе или короткий последний кусок). */
    private var end = C.LENGTH_UNSET.toLong()

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        chunked = uri.scheme == "https" && (uri.host.orEmpty().endsWith("googlevideo.com")) && dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET
        if (!chunked) return upstream.open(dataSpec)
        spec = dataSpec
        position = dataSpec.position
        end = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> position + dataSpec.length
            else -> uri.getQueryParameter("clen")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
        }
        openChunk()
        return if (end == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else (end - position).coerceAtLeast(0)
    }

    private fun openChunk() {
        val remaining = if (end == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else end - position
        val length = minOf(CHUNK, remaining)
        chunkEnd = position + length
        upstream.open(spec!!.buildUpon().setPosition(position).setLength(length).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return upstream.read(buffer, offset, length)
        if (end != C.LENGTH_UNSET.toLong() && position >= end) return C.RESULT_END_OF_INPUT
        var n = upstream.read(buffer, offset, length)
        if (n == C.RESULT_END_OF_INPUT) {
            // Кусок закончился раньше времени — это конец файла; иначе открываем следующий
            if (position < chunkEnd) { end = position; return C.RESULT_END_OF_INPUT }
            if (end != C.LENGTH_UNSET.toLong() && position >= end) return C.RESULT_END_OF_INPUT
            upstream.close()
            try {
                openChunk()
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode == 416) { end = position; return C.RESULT_END_OF_INPUT }
                throw e
            }
            n = upstream.read(buffer, offset, length)
            if (n == C.RESULT_END_OF_INPUT) { end = position; return C.RESULT_END_OF_INPUT }
        }
        position += n
        return n
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        chunked = false
        upstream.close()
    }

    private companion object {
        const val CHUNK = 10L * 1024 * 1024
    }
}

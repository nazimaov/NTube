package tv.lumen.data.library.backup

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Встроенный HTTP-сервер для передачи файлов с телефона (SPEC 7.1): работает, пока открыт диалог.
 * GET / — страница загрузки, POST /import — файл (Takeout CSV или резервная копия), GET /export.json — резервная копия.
 */
@Singleton
class BackupServer @Inject constructor(private val service: BackupService) {

    private var scope: CoroutineScope? = null
    private var socket: ServerSocket? = null

    /** Адрес вида http://192.168.x.x:8765 или null, если сеть недоступна. */
    @Synchronized
    fun start(onImported: (ImportResult?) -> Unit): String? {
        stop()
        val ip = lanAddress() ?: return null
        val server = runCatching { ServerSocket(PORT) }.getOrElse { Log.w(TAG, "Порт $PORT занят", it); return null }
        socket = server
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            while (isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { runCatching { client.use { handle(it, onImported) } }.onFailure { Log.w(TAG, "Запрос", it) } }
            }
        }
        return "http://$ip:$PORT"
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        scope?.cancel()
        socket = null
        scope = null
    }

    private fun handle(client: Socket, onImported: (ImportResult?) -> Unit) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val (method, path) = requestLine.split(' ').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "/" } }
        val out = client.getOutputStream()
        when {
            method == "GET" && (path == "/" || path.startsWith("/?")) -> respond(out, 200, "text/html; charset=utf-8", PAGE.toByteArray())
            method == "GET" && path == "/export.json" -> {
                val body = runBlocking { service.export() }.toByteArray()
                respond(out, 200, "application/json; charset=utf-8", body, "Content-Disposition: attachment; filename=\"lumen-backup.json\"")
            }
            method == "POST" && path == "/import" -> {
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length <= 0 || length > MAX_UPLOAD) return respond(out, 413, "text/plain; charset=utf-8", "Файл пустой или больше 5 МБ".toByteArray())
                val body = readExactly(input, length).toString(Charsets.UTF_8)
                val result = runBlocking { service.import(body) }
                onImported(result)
                val text = if (result == null) "Формат файла не распознан" else
                    "Импортировано подписок: ${result.subscriptions}, записей истории: ${result.history}" + (if (result.settings) ", настройки восстановлены" else "")
                respond(out, if (result == null) 400 else 200, "text/plain; charset=utf-8", text.toByteArray())
            }
            else -> respond(out, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, extra: String? = null) {
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "Payload Too Large" }
        val head = buildString {
            append("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
            if (extra != null) append(extra).append("\r\n")
            append("\r\n")
        }
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8.name()).trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) return null
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(data, read, length - read)
            if (n < 0) break
            read += n
        }
        return data.copyOf(read)
    }

    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private companion object {
        const val PORT = 8765
        const val MAX_UPLOAD = 5 * 1024 * 1024
        const val TAG = "Lumen/Backup"

        // Страница для телефона: файл отправляется как есть через fetch — без multipart
        val PAGE = """
            <!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Lumen</title><style>body{font-family:sans-serif;max-width:560px;margin:24px auto;padding:0 16px;background:#111;color:#eee}
            button,a.btn{display:block;width:100%;padding:14px;margin:12px 0;font-size:18px;border:0;border-radius:10px;background:#ff5252;color:#fff;text-align:center;text-decoration:none}
            input{font-size:16px;margin:12px 0}#r{margin-top:16px;font-size:18px}</style></head><body>
            <h1>Lumen</h1>
            <p>Импорт подписок: файл <b>subscriptions.csv</b> из Google Takeout (YouTube → подписки) или резервная копия Lumen (.json).</p>
            <input type="file" id="f" accept=".csv,.json,text/csv,application/json">
            <button onclick="up()">Импортировать</button>
            <a class="btn" href="/export.json">Скачать резервную копию</a>
            <div id="r"></div>
            <script>
            async function up(){const f=document.getElementById('f').files[0];const r=document.getElementById('r');
            if(!f){r.textContent='Выберите файл';return}r.textContent='Загрузка…';
            try{const res=await fetch('/import',{method:'POST',body:f});r.textContent=await res.text()}catch(e){r.textContent='Ошибка: '+e}}
            </script></body></html>
        """.trimIndent()
    }
}
package tv.lumen.app.navigation

import android.net.Uri

/** id видео из ссылок youtube.com/watch?v=ID и youtu.be/ID (SPEC 8, intent-filter в манифесте). */
object DeepLinks {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun videoId(uri: Uri?): String? {
        uri ?: return null
        val host = uri.host?.lowercase() ?: return null
        val id = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            host.endsWith("youtube.com") && uri.path == "/watch" -> uri.getQueryParameter("v")
            else -> null
        }
        return id?.takeIf { ID.matches(it) }
    }
}
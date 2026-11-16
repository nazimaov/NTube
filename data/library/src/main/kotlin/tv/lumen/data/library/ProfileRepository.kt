package tv.lumen.data.library

import kotlinx.coroutines.flow.Flow
import tv.lumen.core.model.Profile
import tv.lumen.core.model.Reaction

/**
 * Локальные профили («виртуальные аккаунты») и отметки «нравится / не нравится».
 * Всё хранится только на устройстве: без входа в аккаунт Google YouTube отметки не получает.
 */
interface ProfileRepository {
    val profiles: Flow<List<Profile>>

    /** Текущий профиль; при первом запуске создаётся профиль по умолчанию. */
    val current: Flow<Profile>

    /** id текущего профиля; подписки, история и отметки берутся по нему. */
    val currentId: Flow<Long>

    suspend fun currentId(): Long

    /** Создаёт профиль и делает его текущим. */
    suspend fun create(name: String): Profile

    suspend fun select(id: Long)

    suspend fun rename(id: Long, name: String)

    /** Удаляет профиль вместе с его отметками. Последний профиль не удаляется. */
    suspend fun delete(id: Long)

    /** Отметка текущего профиля для видео; null — нет отметки. */
    fun reaction(videoId: String): Flow<Reaction?>

    /** null снимает отметку. */
    suspend fun setReaction(videoId: String, reaction: Reaction?)

    /** Лайкнутые видео текущего профиля, сначала свежие (до [limit]). */
    fun likedVideoIds(limit: Int = 20): Flow<List<String>>

    /** Дизлайкнутые видео текущего профиля. */
    val dislikedVideoIds: Flow<Set<String>>
}

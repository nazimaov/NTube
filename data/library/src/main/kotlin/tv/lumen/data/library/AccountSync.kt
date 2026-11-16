package tv.lumen.data.library

import tv.lumen.core.model.ChannelId

/**
 * Передача действий библиотеки в аккаунт Google (если выполнен вход). data:* не зависят друг от друга
 * (SPEC 3), поэтому реализацию предоставляет модуль app. Ошибки не пробрасываются.
 */
interface AccountSync {
    /** Подписка или отписка на канал в аккаунте. */
    suspend fun setSubscribed(channelId: ChannelId, subscribed: Boolean)
}

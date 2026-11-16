package tv.lumen.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.core.model.ChannelId
import tv.lumen.data.account.AccountActions
import tv.lumen.data.library.AccountSync
import javax.inject.Inject

/** Подписки приложения повторяются в аккаунте Google, если выполнен вход (иначе ничего не делает). */
class GoogleAccountSync @Inject constructor(
    private val actions: AccountActions,
) : AccountSync {
    override suspend fun setSubscribed(channelId: ChannelId, subscribed: Boolean) {
        actions.setSubscribed(channelId, subscribed)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountSyncModule {
    @Binds
    abstract fun accountSync(impl: GoogleAccountSync): AccountSync
}

package tv.lumen.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.data.account.AccountActions
import tv.lumen.data.youtube.AuthorizedPlayer
import javax.inject.Inject

/** Видео «для детей» играют через телевизионный клиент от имени аккаунта (data:youtube о data:account не знает). */
class AccountAuthorizedPlayer @Inject constructor(
    private val actions: AccountActions,
    private val account: tv.lumen.data.account.GoogleAccount,
) : AuthorizedPlayer {
    override val isSignedIn: Boolean get() = account.isSignedIn

    override suspend fun playerJson(videoId: String, signatureTimestamp: Int?): String? =
        actions.playerJson(videoId, signatureTimestamp)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthorizedPlayerModule {
    @Binds
    abstract fun authorizedPlayer(impl: AccountAuthorizedPlayer): AuthorizedPlayer
}

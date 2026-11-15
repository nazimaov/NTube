package tv.lumen.core.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Единый OkHttpClient и Json на всё приложение (SPEC 2, 9.3). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val HTTP_CACHE_BYTES = 50L * 1024 * 1024

    @Provides
    @Singleton
    fun okHttpClient(@ApplicationContext context: Context): OkHttpClient =
        LumenHttp.baseClientBuilder()
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_BYTES))
            .build()

    @Provides
    @Singleton
    fun json(): Json = LumenHttp.json
}

/** Общие настройки, доступные и без Hilt (живые тесты). */
object LumenHttp {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
}

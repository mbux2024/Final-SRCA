package com.streambert.tv.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.streambert.tv.data.omdb.OmdbApi
import com.streambert.tv.data.settings.SettingsRepository
import com.streambert.tv.data.stream.StremioApi
import com.streambert.tv.data.tmdb.TmdbApi
import com.streambert.tv.data.torbox.TorBoxApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit/OkHttp clients for TMDB and TorBox.
 *
 * Auth keys live in DataStore and can change at runtime (Settings screen),
 * so each client uses an interceptor that reads the *current* key on every
 * request rather than baking it in at construction time.
 */
object NetworkModule {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val jsonConverter =
        json.asConverterFactory("application/json".toMediaType())

    private fun logging() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // ── TMDB ──────────────────────────────────────────────────────────────
    fun tmdbApi(settings: SettingsRepository): TmdbApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = runBlocking { settings.currentTmdbKey() }
                val req = chain.request().newBuilder()
                    .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer $token") }
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(TmdbApi::class.java)
    }

    // ── TorBox main API ───────────────────────────────────────────────────
    fun torboxApi(settings: SettingsRepository): TorBoxApi {
        val client = torboxClient(settings)
        return Retrofit.Builder()
            .baseUrl("https://api.torbox.app/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(TorBoxApi::class.java)
    }

    // ── Stremio addon manifests (absolute @Url) ─────────────────────────────
    fun addonApi(): com.streambert.tv.data.addons.AddonApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("Accept", "application/json").build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://stremio.example/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(com.streambert.tv.data.addons.AddonApi::class.java)
    }

    // ── MDBList (multi-source ratings) ──────────────────────────────────────
    fun mdbListApi(): com.streambert.tv.data.mdblist.MDBListApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.mdblist.com/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(com.streambert.tv.data.mdblist.MDBListApi::class.java)
    }

    // ── Google Gemini (Generative Language) ─────────────────────────────────
    fun geminiApi(): com.streambert.tv.data.gemini.GeminiApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(com.streambert.tv.data.gemini.GeminiApi::class.java)
    }

    // ── Real-Debrid REST API ────────────────────────────────────────────────
    fun realDebridApi(): com.streambert.tv.data.realdebrid.RealDebridApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.real-debrid.com/rest/1.0/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(com.streambert.tv.data.realdebrid.RealDebridApi::class.java)
    }

    // ── Stremio addon (Torrentio / Comet) ──────────────────────────────────
    // No auth header; addons are configured via their URL path. We call them
    // with absolute @Url values, so the base URL here is only a placeholder.
    fun stremioApi(): StremioApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://torrentio.strem.fun/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(StremioApi::class.java)
    }

    // ── Trakt.tv (OAuth device flow + watchlist) ────────────────────────────
    // Every request carries the Trakt API version + the user's client id as the
    // api-key header; these are read fresh so a newly-entered client id applies
    // without a restart. Bearer tokens are added per-call by the repository.
    fun traktApi(settings: SettingsRepository): com.streambert.tv.data.trakt.TraktApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val clientId = runBlocking { settings.currentTraktClientId() }
                val req = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("trakt-api-version", "2")
                    .apply { if (clientId.isNotBlank()) addHeader("trakt-api-key", clientId) }
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.trakt.tv/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(com.streambert.tv.data.trakt.TraktApi::class.java)
    }

    // ── OMDb (real IMDb ratings) ────────────────────────────────────────────
    fun omdbApi(): OmdbApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(OmdbApi::class.java)
    }

    private fun torboxClient(settings: SettingsRepository): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = runBlocking { settings.currentTorboxKey() }
                val req = chain.request().newBuilder()
                    .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer $token") }
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging())
            .build()
}

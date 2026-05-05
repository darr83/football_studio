package com.footballstudio.livescore.data

import com.footballstudio.livescore.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ScoresApi {
    @GET("api/scores")
    suspend fun getScores(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
        @Query("competitionKey") competitionKey: String? = null
    ): ScoresResponse

    @GET("api/matches/{matchId}/details")
    suspend fun getMatchDetails(
        @Path("matchId") matchId: Int
    ): MatchDetailsResponse

    @GET("api/live-feed")
    suspend fun getLiveTicker(
        @Query("competitionKey") competitionKey: String? = null,
        @Query("includeWelcome") includeWelcome: Boolean = false
    ): LiveTickerResponse
}

object ScoresApiFactory {
    private const val NETWORK_TIMEOUT_SECONDS = 20L

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun create(baseUrl: String): ScoresApi {
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
            .create(ScoresApi::class.java)
    }

    val api: ScoresApi by lazy {
        create(BuildConfig.BACKEND_BASE_URL)
    }
}

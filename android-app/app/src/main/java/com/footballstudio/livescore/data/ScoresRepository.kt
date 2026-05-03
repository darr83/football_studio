package com.footballstudio.livescore.data

import com.footballstudio.livescore.BuildConfig
import java.net.URI
import retrofit2.HttpException

class ScoresRepository {
    suspend fun fetchScores(
        mode: String,
        date: String?,
        competitionKey: String?
    ): ScoresResponse {
        val candidates = buildCandidateUrls(BuildConfig.BACKEND_BASE_URL)
        var lastError: Throwable? = null
        val errorsByUrl = mutableListOf<String>()

        for (url in candidates) {
            runCatching {
                ScoresApiFactory.create(url).getScores(
                    mode = mode,
                    date = date,
                    competitionKey = competitionKey
                )
            }
                .onSuccess { return it }
                .onFailure {
                    lastError = it
                    errorsByUrl += "$url -> ${describeError(it)}"
                }
        }

        val usingOnlyRemoteUrl = candidates.size == 1 && !isLikelyLocalUrl(candidates.first())
        val details = errorsByUrl.joinToString(" | ")

        if (usingOnlyRemoteUrl) {
            throw IllegalStateException(
                "Backend request failed for ${candidates.first()}. $details",
                lastError
            )
        }

        throw IllegalStateException(
            "Could not connect to backend. Checked: ${candidates.joinToString()}. " +
                "If using a phone on local backend, run: adb reverse tcp:3000 tcp:3000. " +
                details,
            lastError
        )
    }

    private fun buildCandidateUrls(primary: String): List<String> {
        val primaryNormalized = ensureTrailingSlash(primary.trim())

        val base = if (isLikelyLocalUrl(primaryNormalized)) {
            listOf(
                primaryNormalized,
                "http://10.0.2.2:3000/",
                "http://10.0.3.2:3000/",
                "http://127.0.0.1:3000/"
            )
        } else {
            listOf(primaryNormalized)
        }

        return base
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map(::ensureTrailingSlash)
            .distinct()
    }

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun isLikelyLocalUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()

        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host == "10.0.3.2" ||
            host.startsWith("192.168.") ||
            host.startsWith("172.16.") ||
            host.startsWith("172.17.") ||
            host.startsWith("172.18.") ||
            host.startsWith("172.19.") ||
            host.startsWith("172.2") ||
            host.startsWith("172.30.") ||
            host.startsWith("172.31.")
    }

    private fun describeError(throwable: Throwable): String {
        if (throwable is HttpException) {
            val body = throwable.response()?.errorBody()?.string().orEmpty()
            if (body.isNotBlank()) {
                return "HTTP ${throwable.code()}: $body"
            }

            return "HTTP ${throwable.code()}: ${throwable.message()}"
        }

        return throwable.message ?: throwable::class.java.simpleName
    }
}

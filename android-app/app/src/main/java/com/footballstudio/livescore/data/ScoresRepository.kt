package com.footballstudio.livescore.data

import com.footballstudio.livescore.BuildConfig

class ScoresRepository {
    suspend fun fetchScores(
        mode: String,
        date: String?,
        competitionKey: String?
    ): ScoresResponse {
        val candidates = buildCandidateUrls(BuildConfig.BACKEND_BASE_URL)
        var lastError: Throwable? = null

        for (url in candidates) {
            runCatching {
                ScoresApiFactory.create(url).getScores(
                    mode = mode,
                    date = date,
                    competitionKey = competitionKey
                )
            }
                .onSuccess { return it }
                .onFailure { lastError = it }
        }

        throw IllegalStateException(
            "Could not connect to backend. Checked: ${candidates.joinToString()}. " +
                "If using a phone, run: adb reverse tcp:3000 tcp:3000",
            lastError
        )
    }

    private fun buildCandidateUrls(primary: String): List<String> {
        val base = listOf(
            primary,
            "http://10.0.2.2:3000/",
            "http://10.0.3.2:3000/",
            "http://127.0.0.1:3000/"
        )

        return base
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.endsWith("/")) it else "$it/" }
            .distinct()
    }
}

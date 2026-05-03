package com.footballstudio.livescore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.footballstudio.livescore.data.MatchDetails
import com.footballstudio.livescore.data.ScoreMatch
import com.footballstudio.livescore.data.ScoresResponse
import com.footballstudio.livescore.data.ScoresRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveScoresUiState(
    val isLoading: Boolean = true,
    val matches: List<ScoreMatch> = emptyList(),
    val mode: String = "live",
    val selectedDate: String? = null,
    val dateRelation: String = "today",
    val competitionKey: String = "premier-league",
    val lastUpdatedUtc: String? = null,
    val error: String? = null,
    val selectedMatch: ScoreMatch? = null,
    val matchDetails: MatchDetails? = null,
    val isDetailsLoading: Boolean = false,
    val matchDetailsError: String? = null
)

class LiveScoreViewModel(
    private val repository: ScoresRepository = ScoresRepository()
) : ViewModel() {

    private var currentMode: String = "today-live"
    private var currentDate: String? = null
    private var currentCompetitionKey: String = "premier-league"
    private val responseCache = mutableMapOf<String, ScoresResponse>()

    private val _uiState = MutableStateFlow(
        LiveScoresUiState(
            mode = "today-live",
            dateRelation = "today",
            competitionKey = "premier-league"
        )
    )
    val uiState: StateFlow<LiveScoresUiState> = _uiState.asStateFlow()

    init {
        startPolling()
    }

    fun manualRefresh() {
        viewModelScope.launch {
            loadScores(showLoading = false)
        }
    }

    fun selectMatchForDetails(match: ScoreMatch) {
        if (!isEligibleForDetails(match)) {
            return
        }

        val matchId = match.id

        _uiState.update {
            it.copy(
                selectedMatch = match,
                matchDetails = null,
                isDetailsLoading = matchId != null,
                matchDetailsError = if (matchId == null) "Details are unavailable for this match." else null
            )
        }

        if (matchId == null) {
            return
        }

        viewModelScope.launch {
            runCatching { repository.fetchMatchDetails(matchId) }
                .onSuccess { details ->
                    val stillSelected = _uiState.value.selectedMatch?.id == matchId

                    if (!stillSelected) {
                        return@onSuccess
                    }

                    _uiState.update {
                        it.copy(
                            matchDetails = details,
                            isDetailsLoading = false,
                            matchDetailsError = null
                        )
                    }
                }
                .onFailure { throwable ->
                    val stillSelected = _uiState.value.selectedMatch?.id == matchId

                    if (!stillSelected) {
                        return@onFailure
                    }

                    _uiState.update {
                        it.copy(
                            isDetailsLoading = false,
                            matchDetailsError = throwable.message ?: "Could not load match details"
                        )
                    }
                }
        }
    }

    fun dismissMatchDetails() {
        _uiState.update {
            it.copy(
                selectedMatch = null,
                matchDetails = null,
                isDetailsLoading = false,
                matchDetailsError = null
            )
        }
    }

    fun setFilters(
        competitionKey: String,
        mode: String,
        date: String?
    ) {
        val changed =
            competitionKey != currentCompetitionKey ||
                mode != currentMode ||
                date != currentDate

        if (!changed) {
            return
        }

        currentCompetitionKey = competitionKey
        currentMode = mode
        currentDate = date

        val cacheKey = toCacheKey(competitionKey, mode, date)
        val cached = responseCache[cacheKey]

        if (cached != null) {
            applyResponse(
                response = cached,
                mode = mode,
                date = date,
                competitionKey = competitionKey
            )
        }

        viewModelScope.launch {
            loadScores(showLoading = cached == null)
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                loadScores(showLoading = uiState.value.matches.isEmpty())
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun loadScores(showLoading: Boolean) {
        val requestMode = currentMode
        val requestDate = currentDate
        val requestCompetitionKey = currentCompetitionKey

        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        runCatching {
            repository.fetchScores(
                mode = requestMode,
                date = requestDate,
                competitionKey = requestCompetitionKey
            )
        }
            .onSuccess { response ->
                responseCache[toCacheKey(requestCompetitionKey, requestMode, requestDate)] = response

                val isStillActiveRequest =
                    requestMode == currentMode &&
                        requestDate == currentDate &&
                        requestCompetitionKey == currentCompetitionKey

                if (!isStillActiveRequest) {
                    return@onSuccess
                }

                applyResponse(
                    response = response,
                    mode = requestMode,
                    date = requestDate,
                    competitionKey = requestCompetitionKey
                )
            }
            .onFailure { throwable ->
                val isStillActiveRequest =
                    requestMode == currentMode &&
                        requestDate == currentDate &&
                        requestCompetitionKey == currentCompetitionKey

                if (!isStillActiveRequest) {
                    return@onFailure
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        mode = requestMode,
                        selectedDate = requestDate,
                        competitionKey = requestCompetitionKey,
                        error = throwable.message ?: "Could not load scores"
                    )
                }
            }
    }

    private fun applyResponse(
        response: ScoresResponse,
        mode: String,
        date: String?,
        competitionKey: String
    ) {
        _uiState.update {
            it.copy(
                isLoading = false,
                mode = response.mode ?: mode,
                selectedDate = response.selectedDate ?: date,
                dateRelation = response.dateRelation ?: "today",
                competitionKey = competitionKey,
                matches = response.matches,
                lastUpdatedUtc = response.lastUpdatedUtc,
                error = response.error
            )
        }
    }

    private fun toCacheKey(
        competitionKey: String,
        mode: String,
        date: String?
    ): String {
        return "$competitionKey|$mode|${date.orEmpty()}"
    }

    private fun isEligibleForDetails(match: ScoreMatch): Boolean {
        val value = match.status?.trim()?.lowercase().orEmpty()

        return value in setOf(
            "1st_half",
            "2nd_half",
            "halftime",
            "extra_time",
            "penalties",
            "live",
            "in_progress",
            "break",
            "paused",
            "finished",
            "fulltime",
            "ft",
            "after_extra_time",
            "aet"
        )
    }

    private companion object {
        const val POLL_INTERVAL_MS = 20_000L
    }
}

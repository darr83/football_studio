package com.footballstudio.livescore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.footballstudio.livescore.data.LiveTickerEvent
import com.footballstudio.livescore.data.LiveTickerAiStatus
import com.footballstudio.livescore.data.MatchDetails
import com.footballstudio.livescore.data.ScoreMatch
import com.footballstudio.livescore.data.ScoresResponse
import com.footballstudio.livescore.data.ScoresRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class LiveNarrationItem(
    val eventKey: String,
    val text: String,
    val role: LiveNarrationRole = LiveNarrationRole.MATCH_UPDATE,
    val matchKey: String? = null,
    val eventType: String? = null,
    val venueName: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null
)

enum class LiveNarrationRole {
    PRESENTER,
    MATCH_UPDATE
}

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
    val matchDetailsError: String? = null,
    val isLiveTickerOpen: Boolean = false,
    val isLiveTickerLoading: Boolean = false,
    val liveTickerEvents: List<LiveTickerEvent> = emptyList(),
    val liveTickerLiveMatches: List<ScoreMatch> = emptyList(),
    val liveTickerTodayResults: List<ScoreMatch> = emptyList(),
    val liveTickerTomorrowFixtures: List<ScoreMatch> = emptyList(),
    val liveTickerError: String? = null,
    val pendingNarration: List<LiveNarrationItem> = emptyList(),
    val isLiveAudioMuted: Boolean = false,
    val liveTickerAiStatus: LiveTickerAiStatus? = null
)

class LiveScoreViewModel(
    private val repository: ScoresRepository = ScoresRepository()
) : ViewModel() {

    private var currentMode: String = "today-live"
    private var currentDate: String? = null
    private var currentCompetitionKey: String = "premier-league"
    private val responseCache = mutableMapOf<String, ScoresResponse>()
    private val seenTickerEventKeys = LinkedHashSet<String>()
    private var liveTickerJob: Job? = null
    private var shouldRequestLiveWelcome: Boolean = false
    private var shouldRequestLiveRoundupOnOpen: Boolean = false
    private var lastLiveRoundupRequestedAtMs: Long = 0L
    private var cachedTomorrowFixturesDateIso: String? = null
    private var cachedTomorrowFixturesCompetitionKey: String? = null
    private var cachedTomorrowFixturesAtMs: Long = 0L
    private var cachedTomorrowFixtures: List<ScoreMatch> = emptyList()
    private var cachedTodayResultsDateIso: String? = null
    private var cachedTodayResultsCompetitionKey: String? = null
    private var cachedTodayResultsAtMs: Long = 0L
    private var cachedTodayResults: List<ScoreMatch> = emptyList()

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

    fun openLiveTicker() {
        seenTickerEventKeys.clear()
        shouldRequestLiveWelcome = true
        shouldRequestLiveRoundupOnOpen = true
        lastLiveRoundupRequestedAtMs = 0L

        _uiState.update {
            it.copy(
                isLiveTickerOpen = true,
                isLiveTickerLoading = true,
                liveTickerEvents = emptyList(),
                liveTickerLiveMatches = emptyList(),
                liveTickerTodayResults = emptyList(),
                liveTickerTomorrowFixtures = emptyList(),
                liveTickerError = null,
                pendingNarration = emptyList()
            )
        }

        viewModelScope.launch {
            loadLiveTicker(showLoading = true)
        }

        startLiveTickerPolling()
    }

    fun closeLiveTicker() {
        liveTickerJob?.cancel()
        liveTickerJob = null
        shouldRequestLiveWelcome = false
        shouldRequestLiveRoundupOnOpen = false
        lastLiveRoundupRequestedAtMs = 0L

        _uiState.update {
            it.copy(
                isLiveTickerOpen = false,
                isLiveTickerLoading = false,
                liveTickerError = null,
                liveTickerLiveMatches = emptyList(),
                liveTickerTodayResults = emptyList(),
                liveTickerTomorrowFixtures = emptyList(),
                pendingNarration = emptyList()
            )
        }
    }

    fun setLiveAudioMuted(muted: Boolean) {
        _uiState.update { it.copy(isLiveAudioMuted = muted) }
    }

    fun consumeLiveNarration(eventKey: String) {
        _uiState.update {
            it.copy(
                pendingNarration = it.pendingNarration.filterNot { item ->
                    item.eventKey == eventKey
                }
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

        if (_uiState.value.isLiveTickerOpen) {
            seenTickerEventKeys.clear()
            shouldRequestLiveWelcome = false
            shouldRequestLiveRoundupOnOpen = false

            _uiState.update {
                it.copy(
                    liveTickerEvents = emptyList(),
                    liveTickerLiveMatches = emptyList(),
                    liveTickerTodayResults = emptyList(),
                    liveTickerTomorrowFixtures = emptyList(),
                    liveTickerError = null,
                    isLiveTickerLoading = true,
                    pendingNarration = emptyList(),
                    liveTickerAiStatus = null
                )
            }

            viewModelScope.launch {
                loadLiveTicker(showLoading = true)
            }
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

    private fun startLiveTickerPolling() {
        if (liveTickerJob?.isActive == true) {
            return
        }

        liveTickerJob = viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.isLiveTickerOpen) {
                    break
                }

                loadLiveTicker(showLoading = false)
                delay(TICKER_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun loadLiveTicker(showLoading: Boolean) {
        if (!_uiState.value.isLiveTickerOpen) {
            return
        }

        if (showLoading) {
            _uiState.update { it.copy(isLiveTickerLoading = true) }
        }

        runCatching {
            repository.fetchLiveTicker(
                competitionKey = currentCompetitionKey,
                includeWelcome = shouldRequestLiveWelcome
            )
        }
            .onSuccess { response ->
                if (!_uiState.value.isLiveTickerOpen) {
                    return@onSuccess
                }

                val welcomeNarrationItems =
                    if (shouldRequestLiveWelcome) {
                        response.welcomeCommentary
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { welcomeText ->
                                listOf(
                                    LiveNarrationItem(
                                        eventKey = "welcome-${response.lastUpdatedUtc ?: System.currentTimeMillis()}",
                                        text = welcomeText,
                                        role = LiveNarrationRole.PRESENTER
                                    )
                                )
                            }
                            ?: emptyList()
                    } else {
                        emptyList()
                    }

                if (shouldRequestLiveWelcome) {
                    shouldRequestLiveWelcome = false
                }

                val previousLiveMatches = _uiState.value.liveTickerLiveMatches

                val allLiveMatches = runCatching {
                    repository.fetchScores(
                        mode = "today-live",
                        date = null,
                        competitionKey = null
                    ).matches
                }
                    .getOrElse { previousLiveMatches }
                    .filter { isLiveRoundupMatch(it) }
                    .sortedBy { it.kickoffUtc.orEmpty() }

                val tomorrowFixtures =
                    if (allLiveMatches.isEmpty()) {
                        loadTomorrowFixturesFallback(currentCompetitionKey)
                    } else {
                        emptyList()
                    }

                val todayResults =
                    if (allLiveMatches.isEmpty()) {
                        loadTodayResultsFallback(currentCompetitionKey)
                    } else {
                        emptyList()
                    }

                val newEvents = response.events.filter { seenTickerEventKeys.add(it.eventKey) }
                val liveMatchesByKey = allLiveMatches.associateBy { match ->
                    toLiveNarrationMatchKey(
                        competitionName = match.leagueName,
                        homeTeam = match.homeTeam,
                        awayTeam = match.awayTeam
                    )
                }
                val narrationItems =
                    newEvents
                        .asReversed()
                        .mapNotNull { event ->
                            val text = event.commentary?.trim().orEmpty().ifBlank {
                                event.message
                            }
                            val matchKey = toLiveNarrationMatchKey(
                                competitionName = event.competitionName,
                                homeTeam = event.homeTeam,
                                awayTeam = event.awayTeam
                            )
                            val matchContext = liveMatchesByKey[matchKey]

                            text.takeIf { it.isNotBlank() }?.let {
                                LiveNarrationItem(
                                    eventKey = event.eventKey,
                                    text = it,
                                    role = LiveNarrationRole.MATCH_UPDATE,
                                    matchKey = matchKey,
                                    eventType = event.eventType,
                                    venueName = matchContext?.venueName,
                                    homeTeam = event.homeTeam,
                                    awayTeam = event.awayTeam
                                )
                            }
                        }

                _uiState.update { current ->
                    val merged = (newEvents + current.liveTickerEvents)
                        .distinctBy { it.eventKey }
                        .take(MAX_TICKER_EVENTS)
                    val narrationQueue = (welcomeNarrationItems + current.pendingNarration + narrationItems)
                        .distinctBy { it.eventKey }
                        .takeLast(MAX_TICKER_NARRATION_ITEMS)

                    seenTickerEventKeys.clear()
                    seenTickerEventKeys.addAll(merged.map { it.eventKey })

                    current.copy(
                        isLiveTickerLoading = false,
                        liveTickerEvents = merged,
                        liveTickerLiveMatches = allLiveMatches,
                        liveTickerTodayResults = todayResults,
                        liveTickerTomorrowFixtures = tomorrowFixtures,
                        liveTickerError = response.error,
                        pendingNarration = narrationQueue,
                        liveTickerAiStatus = response.ai
                    )
                }

                    val nowMs = System.currentTimeMillis()
                    val shouldRunOpenRoundup = shouldRequestLiveRoundupOnOpen
                    val shouldRunTimedRoundup =
                        !shouldRunOpenRoundup &&
                            nowMs - lastLiveRoundupRequestedAtMs >= ROUNDUP_INTERVAL_MS

                    if (shouldRunOpenRoundup || shouldRunTimedRoundup) {
                        queueLiveRoundupNarration(
                            nowMs = nowMs,
                            includeNoLiveFallback = shouldRunOpenRoundup,
                            eventPrefix = if (shouldRunOpenRoundup) "roundup-open" else "roundup-5m"
                        )
                    }
            }
            .onFailure { throwable ->
                if (!_uiState.value.isLiveTickerOpen) {
                    return@onFailure
                }

                _uiState.update {
                    it.copy(
                        isLiveTickerLoading = false,
                        liveTickerError = throwable.message ?: "Could not load live ticker"
                    )
                }
            }
    }

    private suspend fun loadTomorrowFixturesFallback(competitionKey: String?): List<ScoreMatch> {
        val todayIso = currentDateIsoUtc()
        val tomorrowIso = shiftDateIso(todayIso, 1)
        val nowMs = System.currentTimeMillis()

        if (
            cachedTomorrowFixturesDateIso == tomorrowIso &&
                cachedTomorrowFixturesCompetitionKey == competitionKey &&
                nowMs - cachedTomorrowFixturesAtMs <= TOMORROW_FIXTURES_CACHE_MS
        ) {
            return cachedTomorrowFixtures
        }

        val fixtures = runCatching {
            repository.fetchScores(
                mode = "date",
                date = tomorrowIso,
                competitionKey = competitionKey
            ).matches
        }
            .getOrDefault(emptyList())
            .filter { isUpcomingRoundupMatch(it) }
            .sortedWith(compareBy<ScoreMatch> { it.leagueName.lowercase() }.thenBy { it.kickoffUtc.orEmpty() })

        cachedTomorrowFixturesDateIso = tomorrowIso
        cachedTomorrowFixturesCompetitionKey = competitionKey
        cachedTomorrowFixturesAtMs = nowMs
        cachedTomorrowFixtures = fixtures

        return fixtures
    }

    private suspend fun loadTodayResultsFallback(competitionKey: String?): List<ScoreMatch> {
        val todayIso = currentDateIsoUtc()
        val nowMs = System.currentTimeMillis()

        if (
            cachedTodayResultsDateIso == todayIso &&
                cachedTodayResultsCompetitionKey == competitionKey &&
                nowMs - cachedTodayResultsAtMs <= TODAY_RESULTS_CACHE_MS
        ) {
            return cachedTodayResults
        }

        val results = runCatching {
            repository.fetchScores(
                mode = "date",
                date = todayIso,
                competitionKey = competitionKey
            ).matches
        }
            .getOrDefault(emptyList())
            .filter { isResultRoundupMatch(it) }
            .sortedWith(compareBy<ScoreMatch> { it.leagueName.lowercase() }.thenBy { it.kickoffUtc.orEmpty() })

        cachedTodayResultsDateIso = todayIso
        cachedTodayResultsCompetitionKey = competitionKey
        cachedTodayResultsAtMs = nowMs
        cachedTodayResults = results

        return results
    }

    private suspend fun queueLiveRoundupNarration(
        nowMs: Long,
        includeNoLiveFallback: Boolean,
        eventPrefix: String
    ) {
        lastLiveRoundupRequestedAtMs = nowMs

        if (includeNoLiveFallback) {
            shouldRequestLiveRoundupOnOpen = false
        }

        val roundupText = runCatching {
            buildLiveRoundupNarration(includeNoLiveFallback = includeNoLiveFallback)
        }.getOrNull().orEmpty().trim()

        if (roundupText.isBlank() || !_uiState.value.isLiveTickerOpen) {
            return
        }

        _uiState.update { current ->
            if (!current.isLiveTickerOpen) {
                return@update current
            }

            val narrationQueue = (
                current.pendingNarration +
                    LiveNarrationItem(
                        eventKey = "$eventPrefix-$nowMs",
                        text = roundupText,
                        role = LiveNarrationRole.PRESENTER
                    )
                )
                .distinctBy { it.eventKey }
                .takeLast(MAX_TICKER_NARRATION_ITEMS)

            current.copy(pendingNarration = narrationQueue)
        }
    }

    private suspend fun buildLiveRoundupNarration(includeNoLiveFallback: Boolean): String {
        val today = repository.fetchScores(
            mode = "today-live",
            date = null,
            competitionKey = null
        )
        val liveMatches = today.matches
            .filter { isLiveRoundupMatch(it) }
            .sortedBy { it.kickoffUtc.orEmpty() }

        if (liveMatches.isNotEmpty()) {
            return liveMatches.joinToString(" ") { match ->
                val scoreLine = buildRoundupScoreline(match)
                val minuteLine = match.minute?.let { "$it minutes" } ?: "minute unknown"
                "$scoreLine, $minuteLine."
            }
        }

        if (!includeNoLiveFallback) {
            return ""
        }

        val upcomingToday = today.matches
            .filter { isUpcomingRoundupMatch(it) }
            .sortedBy { it.kickoffUtc.orEmpty() }

        val upcomingLine =
            if (upcomingToday.isEmpty()) {
                "Coming up: no scheduled matches right now."
            } else {
                val list = upcomingToday.joinToString(" ") { match ->
                    val kickoffLabel = kickoffLabelFromIso(match.kickoffUtc)
                    "${match.homeTeam} versus ${match.awayTeam} at $kickoffLabel."
                }

                "Coming up: $list"
            }

        return "There are no live matches right now. $upcomingLine"
    }

    private fun buildRoundupScoreline(match: ScoreMatch): String {
        return "${match.homeTeam} ${toRoundupScoreToken(match.homeScore)} " +
            "${match.awayTeam} ${toRoundupScoreToken(match.awayScore)}"
    }

    private fun toRoundupScoreToken(score: Int?): String {
        return when (score) {
            null -> "unknown"
            0 -> "nil"
            else -> score.toString()
        }
    }

    private fun isLiveRoundupMatch(match: ScoreMatch): Boolean {
        val status = match.status?.trim()?.lowercase().orEmpty()

        return status in setOf(
            "1st_half",
            "2nd_half",
            "halftime",
            "extra_time",
            "penalties",
            "live",
            "in_progress",
            "break",
            "paused"
        )
    }

    private fun isUpcomingRoundupMatch(match: ScoreMatch): Boolean {
        val status = match.status?.trim()?.lowercase().orEmpty()

        return status in setOf(
            "notstarted",
            "scheduled",
            "postponed",
            "cancelled",
            "canceled",
            "delayed"
        ) || (match.homeScore == null && match.awayScore == null)
    }

    private fun isResultRoundupMatch(match: ScoreMatch): Boolean {
        val status = match.status?.trim()?.lowercase().orEmpty()

        return status in setOf(
            "finished",
            "fulltime",
            "ft",
            "after_extra_time",
            "aet",
            "penalties"
        ) || (match.homeScore != null && match.awayScore != null)
    }

    private fun kickoffLabelFromIso(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return "time to be confirmed"
        }

        val match = Regex("T(\\d{2}:\\d{2})").find(raw)
        return match?.groupValues?.getOrNull(1) ?: "time to be confirmed"
    }

    private fun toLiveNarrationMatchKey(
        competitionName: String,
        homeTeam: String,
        awayTeam: String
    ): String {
        return "${competitionName.lowercase(Locale.ROOT)}|${homeTeam.lowercase(Locale.ROOT)}|${awayTeam.lowercase(Locale.ROOT)}"
    }

    private fun currentDateIsoUtc(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).time)
    }

    private fun shiftDateIso(dateIso: String, dayDelta: Int): String {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        parser.isLenient = false
        val parsed = runCatching { parser.parse(dateIso) }.getOrNull()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        if (parsed != null) {
            calendar.time = parsed
        }

        calendar.add(Calendar.DAY_OF_YEAR, dayDelta)

        return parser.format(calendar.time)
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
        const val TICKER_POLL_INTERVAL_MS = 12_000L
        const val ROUNDUP_INTERVAL_MS = 5 * 60 * 1000L
        const val TODAY_RESULTS_CACHE_MS = 60_000L
        const val TOMORROW_FIXTURES_CACHE_MS = 60_000L
        const val MAX_TICKER_EVENTS = 120
        const val MAX_TICKER_NARRATION_ITEMS = 120
    }
}

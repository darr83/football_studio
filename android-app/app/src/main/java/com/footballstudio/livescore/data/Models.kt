package com.footballstudio.livescore.data

import com.squareup.moshi.Json

data class ScoresResponse(
    val source: String,
    val mode: String? = null,
    val selectedDate: String? = null,
    val dateRelation: String? = null,
    @Json(name = "lastUpdatedUtc") val lastUpdatedUtc: String?,
    val matches: List<ScoreMatch>,
    val error: String?
)

data class ScoreMatch(
    val id: Int?,
    val leagueId: Int?,
    val leagueName: String,
    val competitionKey: String?,
    val kickoffUtc: String?,
    val status: String?,
    val minute: Int?,
    val homeTeamId: Int?,
    val awayTeamId: Int?,
    val homeTeamBadgeUrl: String?,
    val awayTeamBadgeUrl: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeScorers: List<GoalScorer> = emptyList(),
    val awayScorers: List<GoalScorer> = emptyList()
)

data class GoalScorer(
    val player: String,
    val minuteLabel: String
)

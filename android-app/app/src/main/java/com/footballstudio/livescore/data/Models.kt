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
    val venueName: String?,
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

data class MatchDetailsResponse(
    val source: String,
    @Json(name = "lastUpdatedUtc") val lastUpdatedUtc: String?,
    val match: MatchDetails?,
    val error: String?
)

data class MatchDetails(
    val id: Int?,
    val status: String?,
    val minute: Int?,
    val refereeName: String?,
    val venueName: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeTeamBadgeUrl: String?,
    val awayTeamBadgeUrl: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeScorers: List<GoalScorer> = emptyList(),
    val awayScorers: List<GoalScorer> = emptyList(),
    val stats: MatchStats = MatchStats(),
    val lineups: MatchLineups = MatchLineups()
)

data class MatchStats(
    val possessionHome: Int? = null,
    val possessionAway: Int? = null,
    val shotsHome: Int? = null,
    val shotsAway: Int? = null,
    val shotsOnTargetHome: Int? = null,
    val shotsOnTargetAway: Int? = null,
    val cornersHome: Int? = null,
    val cornersAway: Int? = null,
    val yellowCardsHome: Int? = null,
    val yellowCardsAway: Int? = null,
    val redCardsHome: Int? = null,
    val redCardsAway: Int? = null
)

data class MatchLineups(
    val home: TeamLineup = TeamLineup(),
    val away: TeamLineup = TeamLineup()
)

data class TeamLineup(
    val managerName: String? = null,
    val formation: String? = null,
    val starting11: List<LineupPlayer> = emptyList(),
    val substitutions: List<SubstitutionItem> = emptyList()
)

data class LineupPlayer(
    val name: String,
    val jerseyNumber: String? = null,
    val position: String? = null,
    val subOutMinute: Int? = null,
    val yellowCard: Boolean = false,
    val redCard: Boolean = false
)

data class SubstitutionItem(
    val name: String,
    val minuteIn: Int? = null,
    val replacedPlayerName: String? = null,
    val yellowCard: Boolean = false,
    val redCard: Boolean = false
)

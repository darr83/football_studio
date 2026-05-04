package com.footballstudio.livescore

import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.footballstudio.livescore.data.GoalScorer
import com.footballstudio.livescore.data.LiveTickerEvent
import com.footballstudio.livescore.data.LineupPlayer
import com.footballstudio.livescore.data.MatchDetails
import com.footballstudio.livescore.data.MatchStats
import com.footballstudio.livescore.data.ScoreMatch
import com.footballstudio.livescore.data.SubstitutionItem
import com.footballstudio.livescore.data.TeamLineup
import com.footballstudio.livescore.ui.theme.FootballLiveTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class CompetitionTab(
    val title: String,
    val key: String,
    val badgeUrl: String
)

private data class TimelineTab(
    val title: String,
    val mode: String,
    val dateIso: String? = null,
    val relation: String = "today"
)

private const val LEAGUE_BADGE_BASE_URL = "https://sports.bzzoiro.com/img/league"
private val yellowCardRowColor = Color(0xFFFFF59D)
private val yellowCardTextColor = Color(0xFF5D4037)
private val redCardRowColor = Color(0xFFFFCDD2)
private val redCardTextColor = Color(0xFF8B0000)
private val liveMinuteBgColor = Color(0xFFC8E6C9)
private val liveMinuteTextColor = Color(0xFF1B5E20)
private val tickerPanelColor = Color(0xFFD8E7E7)
private val tickerTextColor = Color(0xFF1E2A2A)
private val tickerHighlightHome = Color(0xFF1B5E20)
private val tickerHighlightAway = Color(0xFF0D47A1)

private val competitionTabs = listOf(
    CompetitionTab(
        title = "Premier League",
        key = "premier-league",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/1/"
    ),
    CompetitionTab(
        title = "Championship",
        key = "championship",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/12/"
    ),
    CompetitionTab(
        title = "FA Cup",
        key = "fa-cup",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/39/"
    ),
    CompetitionTab(
        title = "Carabao Cup",
        key = "carabao-cup",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/40/"
    ),
    CompetitionTab(
        title = "Champions League",
        key = "champions-league",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/7/"
    ),
    CompetitionTab(
        title = "Europa League",
        key = "europa-league",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/8/"
    )
)

private fun buildTimelineTabs(): List<TimelineTab> {
    val apiTimeZone = TimeZone.getTimeZone("Europe/London")
    val apiFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val labelFormatter = SimpleDateFormat("EEE d MMM", Locale.UK)
    apiFormatter.timeZone = apiTimeZone
    labelFormatter.timeZone = apiTimeZone

    val dateTabs = (-7..-1).map { dayOffset ->
        val date = Calendar.getInstance(apiTimeZone).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.time

        TimelineTab(
            title = labelFormatter.format(date),
            mode = "date",
            dateIso = apiFormatter.format(date),
            relation = "past"
        )
    } + listOf(
        TimelineTab(
            title = "TODAY/LIVE",
            mode = "today-live",
            dateIso = null,
            relation = "today"
        )
    ) + (1..7).map { dayOffset ->
        val date = Calendar.getInstance(apiTimeZone).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.time

        TimelineTab(
            title = labelFormatter.format(date),
            mode = "date",
            dateIso = apiFormatter.format(date),
            relation = "future"
        )
    }

    return dateTabs
}

class MainActivity : ComponentActivity() {

    private val viewModel: LiveScoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FootballLiveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LiveScoresScreen(
                        state = state,
                        onFiltersChanged = viewModel::setFilters,
                        onMatchSelected = viewModel::selectMatchForDetails,
                        onDismissMatchDetails = viewModel::dismissMatchDetails,
                        onOpenLiveTicker = viewModel::openLiveTicker,
                        onCloseLiveTicker = viewModel::closeLiveTicker,
                        onConsumeLiveNarration = viewModel::consumeLiveNarration
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LiveScoresScreen(
    state: LiveScoresUiState,
    onFiltersChanged: (competitionKey: String, mode: String, date: String?) -> Unit,
    onMatchSelected: (ScoreMatch) -> Unit,
    onDismissMatchDetails: () -> Unit,
    onOpenLiveTicker: () -> Unit,
    onCloseLiveTicker: () -> Unit,
    onConsumeLiveNarration: (String) -> Unit
) {
    val timelineTabs = buildTimelineTabs()
    var selectedCompetitionIndex by rememberSaveable { mutableStateOf(0) }
    val todayLiveDefaultIndex = timelineTabs.indexOfFirst { it.mode == "today-live" }.coerceAtLeast(0)
    var selectedTimelineIndex by rememberSaveable { mutableStateOf(todayLiveDefaultIndex) }

    val selectedCompetition = competitionTabs[selectedCompetitionIndex]
    val selectedTimeline = timelineTabs[selectedTimelineIndex]

    LaunchedEffect(selectedCompetitionIndex, selectedTimelineIndex) {
        onFiltersChanged(
            selectedCompetition.key,
            selectedTimeline.mode,
            selectedTimeline.dateIso
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Football STUDIO",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.1.sp
                            )

                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (state.isLiveTickerOpen) redCardRowColor else MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable(onClick = onOpenLiveTicker)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isLiveTickerOpen) redCardTextColor else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                )

                ScrollableTabRow(selectedTabIndex = selectedCompetitionIndex) {
                    competitionTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == selectedCompetitionIndex,
                            onClick = { selectedCompetitionIndex = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CompetitionTabBadge(
                                        badgeUrl = tab.badgeUrl,
                                        contentDescription = tab.title
                                    )
                                    Text(tab.title)
                                }
                            }
                        )
                    }
                }

                ScrollableTabRow(selectedTabIndex = selectedTimelineIndex) {
                    timelineTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == selectedTimelineIndex,
                            onClick = { selectedTimelineIndex = index },
                            text = { Text(tab.title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading && state.matches.isEmpty() -> LoadingState(padding)
            else -> ScoresList(
                modifier = Modifier.padding(padding),
                state = state,
                onMatchClick = onMatchSelected
            )
        }

        val selectedMatch = state.selectedMatch
        if (selectedMatch != null) {
            MatchDetailsDialog(
                summaryMatch = selectedMatch,
                details = state.matchDetails,
                isLoading = state.isDetailsLoading,
                error = state.matchDetailsError,
                onDismiss = onDismissMatchDetails
            )
        }

        if (state.isLiveTickerOpen) {
            LiveTickerDialog(
                events = state.liveTickerEvents,
                isLoading = state.isLiveTickerLoading,
                error = state.liveTickerError,
                onDismiss = onCloseLiveTicker
            )

            LiveTickerNarrator(
                queue = state.pendingNarration,
                onConsume = onConsumeLiveNarration
            )
        }
    }
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ScoresList(
    modifier: Modifier,
    state: LiveScoresUiState,
    onMatchClick: (ScoreMatch) -> Unit
) {
    val grouped = state.matches.groupBy { it.leagueName }.toSortedMap()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (!state.error.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        grouped.forEach { (league, matches) ->
            item {
                Text(
                    text = league,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            items(matches, key = { it.id ?: "${it.homeTeam}-${it.awayTeam}-${it.kickoffUtc}" }) { match ->
                MatchCard(match, onMatchClick)
            }
        }

        if (grouped.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No Matches Today",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: ScoreMatch,
    onMatchClick: (ScoreMatch) -> Unit
) {
    val isLive = isLiveMatchStatus(match.status)
    val isFinished = isFinishedMatchStatus(match.status)
    val canOpenDetails = isLive || isFinished
    val statusLabel = formatStatusLabel(match.status, match.kickoffUtc)
    val minuteLabel = match.minute?.let { "$it'" }

    Card(
        modifier = Modifier.clickable(enabled = canOpenDetails) {
            onMatchClick(match)
        },
        border = if (isLive) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MatchTimer(
                statusLabel = statusLabel,
                minuteLabel = minuteLabel,
                isLive = isLive
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TeamLine(
                    teamName = match.homeTeam,
                    score = match.homeScore,
                    scorers = match.homeScorers,
                    badgeUrl = match.homeTeamBadgeUrl
                )

                TeamLine(
                    teamName = match.awayTeam,
                    score = match.awayScore,
                    scorers = match.awayScorers,
                    badgeUrl = match.awayTeamBadgeUrl
                )

                Text(
                    text = "Stadium: ${match.venueName ?: "TBD"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MatchTimer(
    statusLabel: String,
    minuteLabel: String?,
    isLive: Boolean
) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .height(78.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isLive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (minuteLabel != null) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(
                        color = if (isLive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = minuteLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TeamLine(
    teamName: String,
    score: Int?,
    scorers: List<GoalScorer>,
    badgeUrl: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TeamBadge(badgeUrl = badgeUrl)
                Text(teamName, fontWeight = FontWeight.SemiBold)
            }

            if (scorers.isNotEmpty()) {
                Text(
                    text = formatScorers(scorers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = score?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TeamBadge(
    badgeUrl: String?,
    size: Dp = 20.dp
) {
    val badgeModifier = Modifier
        .size(size)
        .clip(CircleShape)

    if (badgeUrl.isNullOrBlank()) {
        Image(
            painter = painterResource(id = android.R.drawable.ic_menu_help),
            contentDescription = null,
            modifier = badgeModifier,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        return
    }

    AsyncImage(
        model = badgeUrl,
        contentDescription = null,
        modifier = badgeModifier,
        contentScale = ContentScale.Crop,
        error = painterResource(id = android.R.drawable.ic_menu_help)
    )
}

@Composable
private fun MatchDetailsDialog(
    summaryMatch: ScoreMatch,
    details: MatchDetails?,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    val homeTeam = details?.homeTeam ?: summaryMatch.homeTeam
    val awayTeam = details?.awayTeam ?: summaryMatch.awayTeam
    val homeBadge = details?.homeTeamBadgeUrl ?: summaryMatch.homeTeamBadgeUrl
    val awayBadge = details?.awayTeamBadgeUrl ?: summaryMatch.awayTeamBadgeUrl
    val homeScore = details?.homeScore ?: summaryMatch.homeScore
    val awayScore = details?.awayScore ?: summaryMatch.awayScore
    val minute = details?.minute ?: summaryMatch.minute
    val status = details?.status ?: summaryMatch.status
    val venueName = details?.venueName ?: summaryMatch.venueName
    val refereeName = details?.refereeName
    val homeScorers = details?.homeScorers?.takeIf { it.isNotEmpty() } ?: summaryMatch.homeScorers
    val awayScorers = details?.awayScorers?.takeIf { it.isNotEmpty() } ?: summaryMatch.awayScorers
    var selectedTab by rememberSaveable(summaryMatch.id, summaryMatch.kickoffUtc) { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Match Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TeamBadge(badgeUrl = homeBadge, size = 44.dp)
                        Text(
                            text = homeTeam,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${homeScore ?: "-"} - ${awayScore ?: "-"}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TeamBadge(badgeUrl = awayBadge, size = 44.dp)
                        Text(
                            text = awayTeam,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeamScorersList(
                        scorers = homeScorers,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        horizontalAlignment = Alignment.Start
                    )
                    TeamScorersList(
                        scorers = awayScorers,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        horizontalAlignment = Alignment.End
                    )
                }

                val minuteText = minute?.let { "$it'" } ?: formatStatusLabel(status, summaryMatch.kickoffUtc)
                val isLiveMinute = isLiveMatchStatus(status)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Minute:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isLiveMinute) liveMinuteBgColor else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = minuteText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiveMinute) liveMinuteTextColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Stadium: ${venueName ?: "TBD"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Referee: ${refereeName ?: "TBD"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Lineup") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Game Stats") }
                    )
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    !error.isNullOrBlank() -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    details == null -> {
                        Text("Match details are unavailable.")
                    }

                    selectedTab == 0 -> {
                        LineupTab(
                            homeTeamName = homeTeam,
                            awayTeamName = awayTeam,
                            home = details.lineups.home,
                            away = details.lineups.away,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        GameStatsTab(
                            stats = details.stats,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTickerDialog(
    events: List<LiveTickerEvent>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = tickerPanelColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE RESULTS TICKER",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = tickerTextColor
                    )
                    Text(
                        text = "Close",
                        color = tickerTextColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }

                when {
                    isLoading && events.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    !error.isNullOrBlank() && events.isEmpty() -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(events, key = { it.eventKey }) { event ->
                                LiveTickerRow(event = event)
                            }
                        }

                        if (!error.isNullOrBlank()) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTickerRow(event: LiveTickerEvent) {
    val statusLabel = toTickerStatusLabel(event)
    val minuteLabel = toTickerMinuteColumn(event)
    val competitionCode = toTickerCompetitionCode(event.competitionName)
    val textColor = tickerTextColor
    val shouldHighlightScoringTeam = event.eventType == "goal" || event.eventType == "penalty"
    val homeTeamColor =
        if (shouldHighlightScoringTeam && event.teamSide == "home") tickerHighlightHome else textColor
    val awayTeamColor =
        if (shouldHighlightScoringTeam && event.teamSide == "away") tickerHighlightAway else textColor
    val commentaryText = event.commentary?.trim().orEmpty().ifBlank {
        toTickerEventSuffix(event)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tickerPanelColor)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = statusLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.width(34.dp)
            )

            Text(
                text = minuteLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.width(46.dp)
            )

            Text(
                text = competitionCode,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.width(36.dp)
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = homeTeamColor)) {
                        append(event.homeTeam.uppercase())
                    }
                    append(" ${event.homeScore ?: "-"} - ${event.awayScore ?: "-"} ")
                    withStyle(style = SpanStyle(color = awayTeamColor)) {
                        append(event.awayTeam.uppercase())
                    }
                    append(" - ")
                    append(commentaryText)
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiveTickerNarrator(
    queue: List<LiveNarrationItem>,
    onConsume: (String) -> Unit
) {
    val context = LocalContext.current
    var isReady by remember { mutableStateOf(false) }
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true

                val engine = ttsState.value
                if (engine != null) {
                    val languageResult = engine.setLanguage(Locale.UK)

                    if (
                        languageResult == TextToSpeech.LANG_MISSING_DATA ||
                        languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.setLanguage(Locale.getDefault())
                    }
                }
            }
        }

        ttsState.value = tts

        onDispose {
            tts.stop()
            tts.shutdown()
            ttsState.value = null
            isReady = false
        }
    }

    LaunchedEffect(queue, isReady) {
        if (!isReady) {
            return@LaunchedEffect
        }

        val nextItem = queue.firstOrNull() ?: return@LaunchedEffect
        val text = nextItem.text.trim()

        if (text.isBlank()) {
            onConsume(nextItem.eventKey)
            return@LaunchedEffect
        }

        ttsState.value?.speak(
            text,
            TextToSpeech.QUEUE_ADD,
            null,
            "live-commentary-${nextItem.eventKey}"
        )

        onConsume(nextItem.eventKey)
    }
}

private fun toTickerStatusLabel(event: LiveTickerEvent): String {
    return when (event.eventType) {
        "goal" -> "GL"
        "penalty" -> "PEN"
        "yellow-card" -> "YC"
        "red-card" -> "RC"
        "half-time" -> "HT"
        "full-time" -> "FT"
        else -> "EVT"
    }
}

private fun toTickerMinuteColumn(event: LiveTickerEvent): String {
    val raw = event.minuteLabel?.trim().orEmpty()

    if (raw.isBlank()) {
        return "(-- )"
    }

    if (raw == "HT" || raw == "FT") {
        return "(- -)"
    }

    val cleaned = raw.removeSuffix("'")
    return "($cleaned)"
}

private fun toTickerCompetitionCode(competitionName: String): String {
    return when (competitionName.lowercase()) {
        "premier league" -> "EPL"
        "championship" -> "CH"
        "fa cup" -> "FAC"
        "carabao cup" -> "EFL"
        "champions league" -> "UCL"
        "europa league" -> "UEL"
        else -> competitionName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(separator = "") { it.first().uppercase() }
            .ifBlank { "LGE" }
    }
}

private fun toTickerEventSuffix(event: LiveTickerEvent): String {
    val player = event.playerName?.takeIf { it.isNotBlank() }

    return when (event.eventType) {
        "goal" -> if (player != null) "GOAL $player" else "GOAL"
        "penalty" -> if (player != null) "PENALTY GOAL $player" else "PENALTY GOAL"
        "yellow-card" -> if (player != null) "YELLOW CARD $player" else "YELLOW CARD"
        "red-card" -> if (player != null) "RED CARD $player" else "RED CARD"
        "half-time" -> "HALF TIME"
        "full-time" -> "FULL TIME"
        else -> event.message
    }
}

@Composable
private fun TeamScorersList(
    scorers: List<GoalScorer>,
    modifier: Modifier = Modifier,
    textAlign: TextAlign,
    horizontalAlignment: Alignment.Horizontal
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (scorers.isEmpty()) {
            Text(
                text = "-",
                style = MaterialTheme.typography.bodySmall,
                textAlign = textAlign,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            scorers.forEach { scorer ->
                Text(
                    text = "${scorer.player} ${scorer.minuteLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = textAlign,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LineupTab(
    homeTeamName: String,
    awayTeamName: String,
    home: TeamLineup,
    away: TeamLineup,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TeamLineupSection(
            teamName = homeTeamName,
            lineup = home,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        TeamLineupSection(
            teamName = awayTeamName,
            lineup = away,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun TeamLineupSection(
    teamName: String,
    lineup: TeamLineup,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = teamName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Manager: ${lineup.managerName ?: "TBD"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )

            if (!lineup.formation.isNullOrBlank()) {
                Text(
                    text = "Formation: ${lineup.formation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text(
                text = "Starting 11",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lineup.starting11.isEmpty()) {
                        Text(
                            text = "No starting lineup available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lineup.starting11.forEach { player ->
                            LineupPlayerRow(player = player)
                        }
                    }
                }
            }

            Text(
                text = "Substitutes",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lineup.substitutions.isEmpty()) {
                        Text(
                            text = "No substitutes listed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lineup.substitutions.forEach { substitute ->
                            SubstitutionPlayerRow(substitute = substitute)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LineupPlayerRow(player: LineupPlayer) {
    val rowColor = when {
        player.redCard -> redCardRowColor
        player.yellowCard -> yellowCardRowColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        player.redCard -> redCardTextColor
        player.yellowCard -> yellowCardTextColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Text(
            text = formatLineupPlayer(player),
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

@Composable
private fun SubstitutionPlayerRow(substitute: SubstitutionItem) {
    val rowColor = when {
        substitute.redCard -> redCardRowColor
        substitute.yellowCard -> yellowCardRowColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        substitute.redCard -> redCardTextColor
        substitute.yellowCard -> yellowCardTextColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Text(
            text = substitute.name,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

@Composable
private fun GameStatsTab(
    stats: MatchStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsRow(
            label = "Possession",
            homeValue = formatStat(stats.possessionHome, suffix = "%"),
            awayValue = formatStat(stats.possessionAway, suffix = "%")
        )
        StatsRow(
            label = "Shots",
            homeValue = formatStat(stats.shotsHome),
            awayValue = formatStat(stats.shotsAway)
        )
        StatsRow(
            label = "Shots On Target",
            homeValue = formatStat(stats.shotsOnTargetHome),
            awayValue = formatStat(stats.shotsOnTargetAway)
        )
        StatsRow(
            label = "Corners",
            homeValue = formatStat(stats.cornersHome),
            awayValue = formatStat(stats.cornersAway)
        )
        StatsRow(
            label = "Yellow Cards",
            homeValue = formatStat(stats.yellowCardsHome),
            awayValue = formatStat(stats.yellowCardsAway)
        )
        StatsRow(
            label = "Red Cards",
            homeValue = formatStat(stats.redCardsHome),
            awayValue = formatStat(stats.redCardsAway)
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun StatsRow(
    label: String,
    homeValue: String,
    awayValue: String
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeValue,
                modifier = Modifier.width(48.dp),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = awayValue,
                modifier = Modifier.width(48.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatStat(value: Int?, suffix: String = ""): String {
    return if (value == null) "-" else "$value$suffix"
}

private fun formatLineupPlayer(player: LineupPlayer): String {
    val positionSuffix = player.position?.let { " ($it)" }.orEmpty()
    val jerseyPrefix = player.jerseyNumber?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return "$jerseyPrefix${player.name}$positionSuffix"
}

@Composable
private fun CompetitionTabBadge(
    badgeUrl: String,
    contentDescription: String
) {
    AsyncImage(
        model = badgeUrl,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
        error = painterResource(id = android.R.drawable.ic_menu_help)
    )
}

private fun formatScorers(scorers: List<GoalScorer>): String {
    return scorers.joinToString(" • ") { "${it.player} ${it.minuteLabel}" }
}

private fun formatStatusLabel(status: String?, kickoffUtc: String?): String {
    return when (status?.trim()?.lowercase()) {
        "1st_half" -> "1st"
        "2nd_half" -> "2nd"
        "halftime" -> "HT"
        "finished", "fulltime", "ft" -> "FT"
        "notstarted", "scheduled" -> formatKickoffTime(kickoffUtc)
        "extra_time" -> "ET"
        "penalties" -> "PEN"
        else -> status?.take(4)?.uppercase() ?: "N/A"
    }
}

private fun isLiveMatchStatus(status: String?): Boolean {
    val value = status?.trim()?.lowercase().orEmpty()

    return value in setOf(
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

private fun isFinishedMatchStatus(status: String?): Boolean {
    val value = status?.trim()?.lowercase().orEmpty()

    return value in setOf(
        "finished",
        "fulltime",
        "ft",
        "after_extra_time",
        "aet",
        "penalties"
    )
}

private fun parseApiDate(raw: String): Date? {
    val parsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )

    parsers.forEach { parser ->
        parser.isLenient = false

        if (parser.toPattern().contains("'Z'")) {
            parser.timeZone = TimeZone.getTimeZone("UTC")
        }

        val parsed = runCatching { parser.parse(raw) }.getOrNull()

        if (parsed != null) {
            return parsed
        }
    }

    return null
}

private fun formatKickoffTime(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return "n/a"
    }

    val parsed = parseApiDate(raw) ?: return raw
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(parsed)
}

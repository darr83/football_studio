package com.footballstudio.livescore

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.footballstudio.livescore.data.GoalScorer
import com.footballstudio.livescore.data.ScoreMatch
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
                        onManualRefresh = viewModel::manualRefresh,
                        onFiltersChanged = viewModel::setFilters
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
    onManualRefresh: () -> Unit,
    onFiltersChanged: (competitionKey: String, mode: String, date: String?) -> Unit
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
                        Column {
                            Text("Live Football Scores")
                            Text(
                                text = "${selectedCompetition.title} · ${selectedTimeline.title} · Updated ${formatUpdatedTime(state.lastUpdatedUtc)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = onManualRefresh,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text("Refresh")
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
                state = state
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
    state: LiveScoresUiState
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
                MatchCard(match)
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
private fun MatchCard(match: ScoreMatch) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                text = "${formatStatus(match.status, match.minute)} · Kickoff ${formatKickoffDateTime(match.kickoffUtc)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun TeamBadge(badgeUrl: String?) {
    val badgeModifier = Modifier
        .size(20.dp)
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

private fun formatStatus(status: String?, minute: Int?): String {
    val minuteText = minute?.let { "$it'" } ?: ""
    return listOf(status.orEmpty(), minuteText).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown" }
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

private fun formatKickoffDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return "n/a"
    }

    val parsed = parseApiDate(raw) ?: return raw
    val formatter = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(parsed)
}

private fun formatUpdatedTime(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return "n/a"
    }

    val parsed = parseApiDate(raw) ?: return raw
    val formatter = SimpleDateFormat("HH:mm:ss z", Locale.getDefault())
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(parsed)
}

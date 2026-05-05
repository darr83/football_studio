package com.footballstudio.livescore

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.footballstudio.livescore.data.LiveTickerAiStatus
import com.footballstudio.livescore.data.LiveTickerEvent
import com.footballstudio.livescore.data.LineupPlayer
import com.footballstudio.livescore.data.MatchDetails
import com.footballstudio.livescore.data.MatchStats
import com.footballstudio.livescore.data.ScoreMatch
import com.footballstudio.livescore.data.SubstitutionItem
import com.footballstudio.livescore.data.TeamLineup
import com.footballstudio.livescore.ui.theme.FootballLiveTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class CompetitionTab(
    val title: String,
    val key: String,
    val badgeUrl: String?
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
private const val LIVE_NARRATION_RATE = 0.95f
private const val LIVE_NARRATION_PITCH = 1.0f
private const val TTS_PREFS_NAME = "live_tts_settings"
private const val TTS_PREF_PREFER_AI_VOICE = "prefer_ai_voice"
private const val TTS_PREF_SELECTED_VOICE = "selected_voice"
private const val TTS_PREF_SELECTED_COMPETITIONS = "selected_competitions"
private const val TTS_PREF_PITCH = "pitch"
private const val TTS_PREF_SPEED = "speed"
private const val MINE_COMPETITION_KEY = "mine"
private const val TTS_PITCH_MIN = 0.7f
private const val TTS_PITCH_MAX = 1.4f
private const val TTS_SPEED_MIN = 0.7f
private const val TTS_SPEED_MAX = 1.3f
private val initialDotNameRegex = Regex("\\b[A-Za-z]\\.([A-Za-z][\\p{L}'-]*)\\b")
private val leadingInitialNameRegex = Regex("^[A-Za-z]\\.\\s*([A-Za-z][\\p{L}'-]*)$")
private val initialTokenRegex = Regex("^([A-Za-z]\\.?|([A-Za-z]\\.){2})$")
private val surnameJoiners = setOf(
    "da",
    "de",
    "del",
    "della",
    "di",
    "dos",
    "du",
    "la",
    "le",
    "van",
    "von"
)

private data class TickerEventPalette(
    val border: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val scoreColor: Color,
    val commentaryBackground: Color,
    val commentaryBorder: Color,
    val commentaryText: Color
)

private data class LivePresentationMatchCard(
    val key: String,
    val competitionName: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val minuteLabel: String?,
    val homeBadgeUrl: String?,
    val awayBadgeUrl: String?,
    val homeScorers: List<String>,
    val awayScorers: List<String>,
    val homeCards: List<String>,
    val awayCards: List<String>,
    val homeSubs: List<String>,
    val awaySubs: List<String>
)

private data class TtsNarrationSettings(
    val preferAiVoice: Boolean = true,
    val selectedVoiceName: String? = null,
    val selectedCompetitionKeys: Set<String> = emptySet(),
    val pitch: Float = LIVE_NARRATION_PITCH,
    val speed: Float = LIVE_NARRATION_RATE
)

private data class TtsVoiceOption(
    val name: String,
    val label: String
)

private val competitionTabs = listOf(
    CompetitionTab(
        title = "Mine",
        key = MINE_COMPETITION_KEY,
        badgeUrl = null
    ),
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
        title = "Ligue 1",
        key = "ligue-1",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/6/"
    ),
    CompetitionTab(
        title = "Bundesliga",
        key = "bundesliga",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/5/"
    ),
    CompetitionTab(
        title = "La Liga",
        key = "la-liga",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/3/"
    ),
    CompetitionTab(
        title = "FA Cup",
        key = "fa-cup",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/39/"
    ),
    CompetitionTab(
        title = "Coppa Italia",
        key = "coppa-italia",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/42/"
    ),
    CompetitionTab(
        title = "Scottish Premiership",
        key = "scottish-premiership",
        badgeUrl = "$LEAGUE_BADGE_BASE_URL/13/"
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

private val selectableCompetitions = competitionTabs.filter { tab ->
    tab.key != MINE_COMPETITION_KEY
}

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
        applyImmersiveMode()

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
                        onConsumeLiveNarration = viewModel::consumeLiveNarration,
                        onSetLiveAudioMuted = viewModel::setLiveAudioMuted
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    onConsumeLiveNarration: (String) -> Unit,
    onSetLiveAudioMuted: (Boolean) -> Unit
) {
    KeepScreenOnWhen(enabled = state.isLiveTickerOpen)
    val context = LocalContext.current

    val timelineTabs = buildTimelineTabs()
    val defaultCompetitionIndex = competitionTabs.indexOfFirst { it.key == "premier-league" }.coerceAtLeast(0)
    var selectedCompetitionIndex by rememberSaveable { mutableStateOf(defaultCompetitionIndex) }
    val todayLiveDefaultIndex = timelineTabs.indexOfFirst { it.mode == "today-live" }.coerceAtLeast(0)
    var selectedTimelineIndex by rememberSaveable { mutableStateOf(todayLiveDefaultIndex) }
    var isTtsSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var ttsSettings by remember { mutableStateOf(loadTtsNarrationSettings(context)) }

    val selectedCompetition = competitionTabs[selectedCompetitionIndex]
    val selectedTimeline = timelineTabs[selectedTimelineIndex]

    LaunchedEffect(selectedCompetitionIndex, selectedTimelineIndex, ttsSettings.selectedCompetitionKeys) {
        val effectiveCompetitionKey = toEffectiveCompetitionFilter(
            selectedCompetitionKey = selectedCompetition.key,
            selectedTimelineMode = selectedTimeline.mode,
            selectedCompetitionKeys = ttsSettings.selectedCompetitionKeys
        )

        onFiltersChanged(
            effectiveCompetitionKey,
            selectedTimeline.mode,
            selectedTimeline.dateIso
        )
    }

    if (state.isLiveTickerOpen) {
        LiveTickerPresentationScreen(
            events = state.liveTickerEvents,
            liveMatches = state.liveTickerLiveMatches,
            todayResults = state.liveTickerTodayResults,
            tomorrowFixtures = state.liveTickerTomorrowFixtures,
            isLoading = state.isLiveTickerLoading,
            error = state.liveTickerError,
            aiStatus = state.liveTickerAiStatus,
            isAudioMuted = state.isLiveAudioMuted,
            onToggleMute = { onSetLiveAudioMuted(!state.isLiveAudioMuted) },
            onClose = onCloseLiveTicker
        )

        LiveTickerNarrator(
            queue = state.pendingNarration,
            isMuted = state.isLiveAudioMuted,
            settings = ttsSettings,
            onConsume = onConsumeLiveNarration
        )

        return
    }

    if (isTtsSettingsOpen) {
        TtsSettingsDialog(
            settings = ttsSettings,
            onDismiss = { isTtsSettingsOpen = false },
            onSave = { updatedSettings ->
                ttsSettings = updatedSettings
                saveTtsNarrationSettings(context, updatedSettings)
                isTtsSettingsOpen = false
            }
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
                    },
                    actions = {
                        IconButton(onClick = { isTtsSettingsOpen = true }) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_manage),
                                contentDescription = "Open voice settings"
                            )
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
    }
}

@Composable
private fun TtsSettingsDialog(
    settings: TtsNarrationSettings,
    onDismiss: () -> Unit,
    onSave: (TtsNarrationSettings) -> Unit
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var isVoiceMenuExpanded by remember { mutableStateOf(false) }
    val voices = rememberAvailableNarrationVoices()
    val selectedVoiceLabel = voices
        .firstOrNull { it.name == draft.selectedVoiceName }
        ?.label
        ?: "Auto (best available)"

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Leagues In Mine",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Selected leagues will appear in Mine and Today/Live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    selectableCompetitions.forEach { competition ->
                        val isSelected = draft.selectedCompetitionKeys.contains(competition.key)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    val current = draft.selectedCompetitionKeys
                                    val updated =
                                        if (checked) {
                                            current + competition.key
                                        } else if (isSelected && current.size > 1) {
                                            current - competition.key
                                        } else {
                                            current
                                        }

                                    draft = draft.copy(
                                        selectedCompetitionKeys = sanitizeSelectedCompetitionKeys(updated)
                                    )
                                }
                            )

                            CompetitionTabBadge(
                                badgeUrl = competition.badgeUrl,
                                contentDescription = competition.title
                            )

                            Text(
                                text = competition.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prefer AI voice",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Prioritise network/neural voices when available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = draft.preferAiVoice,
                        onCheckedChange = { checked ->
                            draft = draft.copy(preferAiVoice = checked)
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Voice",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box {
                        OutlinedButton(onClick = { isVoiceMenuExpanded = true }) {
                            Text(selectedVoiceLabel)
                        }

                        DropdownMenu(
                            expanded = isVoiceMenuExpanded,
                            onDismissRequest = { isVoiceMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 340.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auto (best available)") },
                                onClick = {
                                    draft = draft.copy(selectedVoiceName = null)
                                    isVoiceMenuExpanded = false
                                }
                            )

                            voices.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        draft = draft.copy(selectedVoiceName = option.name)
                                        isVoiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Pitch: ${formatTtsValue(draft.pitch)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = draft.pitch,
                        onValueChange = { draft = draft.copy(pitch = clampTtsPitch(it)) },
                        valueRange = TTS_PITCH_MIN..TTS_PITCH_MAX
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Speed: ${formatTtsValue(draft.speed)}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = draft.speed,
                        onValueChange = { draft = draft.copy(speed = clampTtsSpeed(it)) },
                        valueRange = TTS_SPEED_MIN..TTS_SPEED_MAX
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    TextButton(
                        onClick = {
                            onSave(
                                draft.copy(
                                    selectedCompetitionKeys = sanitizeSelectedCompetitionKeys(draft.selectedCompetitionKeys),
                                    pitch = clampTtsPitch(draft.pitch),
                                    speed = clampTtsSpeed(draft.speed)
                                )
                            )
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAvailableNarrationVoices(): List<TtsVoiceOption> {
    val context = LocalContext.current
    val voiceOptions = remember { mutableStateOf<List<TtsVoiceOption>>(emptyList()) }

    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            voiceOptions.value = emptyList()
            onDispose {}
        } else {
            var engineRef: TextToSpeech? = null
            val engine = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    return@TextToSpeech
                }

                val readyEngine = engineRef ?: return@TextToSpeech
                val locale = configureNarrationLocale(readyEngine)
                voiceOptions.value = readyEngine.voices
                    ?.asSequence()
                    ?.filter { voice ->
                        val voiceLocale = voice.locale ?: return@filter false
                        val sameLanguage =
                            voiceLocale.language.equals(locale.language, ignoreCase = true)
                        val isNotInstalled =
                            voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                        sameLanguage && !isNotInstalled
                    }
                    ?.sortedByDescending { voice ->
                        narrationVoiceScore(voice, locale, preferAiVoice = true)
                    }
                    ?.map { voice ->
                        TtsVoiceOption(
                            name = voice.name,
                            label = buildTtsVoiceLabel(voice)
                        )
                    }
                    ?.distinctBy { it.name }
                    ?.take(50)
                    ?.toList()
                    ?: emptyList()
            }
                    engineRef = engine

            onDispose {
                engine.stop()
                engine.shutdown()
            }
        }
    }

    return voiceOptions.value
}

private fun loadTtsNarrationSettings(context: Context): TtsNarrationSettings {
    val preferences = context.getSharedPreferences(TTS_PREFS_NAME, Context.MODE_PRIVATE)
    val selectedCompetitionKeys =
        preferences.getStringSet(TTS_PREF_SELECTED_COMPETITIONS, null)
            ?.toSet()
            .orEmpty()

    return TtsNarrationSettings(
        preferAiVoice = preferences.getBoolean(TTS_PREF_PREFER_AI_VOICE, true),
        selectedVoiceName = preferences.getString(TTS_PREF_SELECTED_VOICE, null)?.takeIf { it.isNotBlank() },
        selectedCompetitionKeys = sanitizeSelectedCompetitionKeys(selectedCompetitionKeys),
        pitch = clampTtsPitch(preferences.getFloat(TTS_PREF_PITCH, LIVE_NARRATION_PITCH)),
        speed = clampTtsSpeed(preferences.getFloat(TTS_PREF_SPEED, LIVE_NARRATION_RATE))
    )
}

private fun saveTtsNarrationSettings(context: Context, settings: TtsNarrationSettings) {
    context.getSharedPreferences(TTS_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(TTS_PREF_PREFER_AI_VOICE, settings.preferAiVoice)
        .putString(TTS_PREF_SELECTED_VOICE, settings.selectedVoiceName)
        .putStringSet(
            TTS_PREF_SELECTED_COMPETITIONS,
            sanitizeSelectedCompetitionKeys(settings.selectedCompetitionKeys)
        )
        .putFloat(TTS_PREF_PITCH, clampTtsPitch(settings.pitch))
        .putFloat(TTS_PREF_SPEED, clampTtsSpeed(settings.speed))
        .apply()
}

private fun sanitizeSelectedCompetitionKeys(keys: Set<String>): Set<String> {
    val allowed = selectableCompetitions.map { it.key }.toSet()
    val filtered = keys.filter { key -> allowed.contains(key) }.toSet()

    if (filtered.isNotEmpty()) {
        return filtered
    }

    return allowed
}

private fun toEffectiveCompetitionFilter(
    selectedCompetitionKey: String,
    selectedTimelineMode: String,
    selectedCompetitionKeys: Set<String>
): String {
    if (selectedTimelineMode != "today-live" && selectedCompetitionKey != MINE_COMPETITION_KEY) {
        return selectedCompetitionKey
    }

    return sanitizeSelectedCompetitionKeys(selectedCompetitionKeys)
        .sorted()
        .joinToString(",")
}

private fun clampTtsPitch(value: Float): Float {
    return value.coerceIn(TTS_PITCH_MIN, TTS_PITCH_MAX)
}

private fun clampTtsSpeed(value: Float): Float {
    return value.coerceIn(TTS_SPEED_MIN, TTS_SPEED_MAX)
}

private fun formatTtsValue(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun buildTtsVoiceLabel(voice: Voice): String {
    val localeTag = voice.locale?.toLanguageTag() ?: "default"
    val style = if (voice.isNetworkConnectionRequired) "AI" else "Local"
    return "$style • ${voice.name} • $localeTag"
}

@Composable
private fun KeepScreenOnWhen(enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(context, enabled) {
        val activity = context.findActivity()

        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
@OptIn(ExperimentalFoundationApi::class)
private fun LiveTickerPresentationScreen(
    events: List<LiveTickerEvent>,
    liveMatches: List<ScoreMatch>,
    todayResults: List<ScoreMatch>,
    tomorrowFixtures: List<ScoreMatch>,
    isLoading: Boolean,
    error: String?,
    aiStatus: LiveTickerAiStatus?,
    isAudioMuted: Boolean,
    onToggleMute: () -> Unit,
    onClose: () -> Unit
) {
    ForceLandscapeOrientation()

    var liveClockLabel by remember { mutableStateOf(currentTickerClockLabel()) }
    val cards = remember(events, liveMatches) {
        buildLivePresentationCards(events = events, liveMatches = liveMatches)
    }
    var selectedCardIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            liveClockLabel = currentTickerClockLabel()
            delay(1_000)
        }
    }

    LaunchedEffect(cards.size) {
        if (selectedCardIndex >= cards.size) {
            selectedCardIndex = 0
        }
    }

    LaunchedEffect(cards) {
        if (cards.size <= 1) {
            return@LaunchedEffect
        }

        while (true) {
            delay(7_500)
            selectedCardIndex = (selectedCardIndex + 1) % cards.size
        }
    }

    LaunchedEffect(events, cards) {
        if (cards.isEmpty() || events.isEmpty()) {
            return@LaunchedEffect
        }

        val latestEvent = events.firstOrNull() ?: return@LaunchedEffect
        val eventMatchKey = livePresentationMatchKey(
            competitionName = latestEvent.competitionName,
            homeTeam = latestEvent.homeTeam,
            awayTeam = latestEvent.awayTeam
        )
        val eventCardIndex = cards.indexOfFirst { card -> card.key == eventMatchKey }

        if (eventCardIndex >= 0) {
            selectedCardIndex = eventCardIndex
        }
    }

    val selectedCard = cards.getOrNull(selectedCardIndex)
    val bottomTickerText = remember(cards, todayResults, tomorrowFixtures) {
        buildLiveBottomTickerText(
            cards = cards,
            todayResults = todayResults,
            tomorrowFixtures = tomorrowFixtures
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1F5E)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B1F5E)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = if (isAudioMuted) Color(0xFF5E4630) else Color(0xFF1B5E20),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onToggleMute)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isAudioMuted) "Audio Muted" else "Audio Live",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Football STUDIO LIVE",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 22.sp
                )
                Text(
                    text = liveClockLabel,
                    color = Color(0xFFE2E8FF),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF152E7A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (cards.isEmpty()) "No Live Games" else "Live Games",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (isLoading && cards.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else if (cards.isEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No live events right now.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (tomorrowFixtures.isNotEmpty()) {
                                Text(
                                    text = "Tomorrow Fixtures",
                                    color = Color(0xFFFFF59D),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(tomorrowFixtures, key = { "${it.id}-${it.kickoffUtc}-${it.homeTeam}-${it.awayTeam}" }) { fixture ->
                                        Text(
                                            text = "${fixture.leagueName}: ${fixture.homeTeam} vs ${fixture.awayTeam} (${formatKickoffTime(fixture.kickoffUtc)})",
                                            color = Color(0xFFE3EAFF),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cards, key = { it.key }) { card ->
                                val itemIndex = cards.indexOfFirst { it.key == card.key }
                                val isSelected = itemIndex == selectedCardIndex

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCardIndex = itemIndex.coerceAtLeast(0)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF213D99) else Color(0xFF1A327F)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFFFFE082) else Color(0xFF3B58A9)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = card.competitionName,
                                                color = Color(0xFFC9D5FF),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${card.homeTeam} ${card.homeScore ?: "-"}-${card.awayScore ?: "-"} ${card.awayTeam}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }

                                        Text(
                                            text = toPresentationMinute(card.minuteLabel),
                                            color = Color(0xFFFFF59D),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E2A72))
            ) {
                if (selectedCard == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoading) "Loading live presentation..." else "Waiting for live matches",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    LivePresentationMainMatchCard(card = selectedCard)
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = "Issue: $error",
                modifier = Modifier.padding(horizontal = 14.dp),
                color = Color(0xFFFFCDD2),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = buildAiStatusLine(aiStatus),
            modifier = Modifier.padding(horizontal = 14.dp),
            color = Color(0xFFE2E8FF),
            style = MaterialTheme.typography.bodySmall
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF59D))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = bottomTickerText,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE),
                maxLines = 1,
                color = Color(0xFF10244D),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LivePresentationMainMatchCard(card: LivePresentationMatchCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = card.competitionName,
            color = Color(0xFFC9D5FF),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamBadge(badgeUrl = card.homeBadgeUrl, size = 44.dp)
                Text(
                    text = card.homeTeam,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${card.homeScore ?: "-"} - ${card.awayScore ?: "-"}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp
                )
                Text(
                    text = toPresentationMinute(card.minuteLabel),
                    color = Color(0xFFFFF59D),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamBadge(badgeUrl = card.awayBadgeUrl, size = 44.dp)
                Text(
                    text = card.awayTeam,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LivePresentationTeamFacts(
                title = card.homeTeam,
                scorers = card.homeScorers,
                cards = card.homeCards,
                substitutions = card.homeSubs,
                modifier = Modifier.weight(1f)
            )
            LivePresentationTeamFacts(
                title = card.awayTeam,
                scorers = card.awayScorers,
                cards = card.awayCards,
                substitutions = card.awaySubs,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LivePresentationTeamFacts(
    title: String,
    scorers: List<String>,
    cards: List<String>,
    substitutions: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3A8E)),
        border = BorderStroke(1.dp, Color(0xFF3A57A4))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            LiveFactSection(label = "Scorers", items = scorers)
            LiveFactSection(label = "Cards", items = cards)
            LiveFactSection(label = "Subs", items = substitutions)
        }
    }
}

@Composable
private fun LiveFactSection(
    label: String,
    items: List<String>
) {
    Text(
        text = label,
        color = Color(0xFFFFF59D),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )

    if (items.isEmpty()) {
        Text(
            text = "-",
            color = Color(0xFFE3EAFF),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        items.take(6).forEach { item ->
            Text(
                text = item,
                color = Color(0xFFE3EAFF),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ForceLandscapeOrientation() {
    val context = LocalContext.current

    DisposableEffect(context) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this

    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }

        current = current.baseContext
    }

    return null
}

private fun buildLivePresentationCards(
    events: List<LiveTickerEvent>,
    liveMatches: List<ScoreMatch>
): List<LivePresentationMatchCard> {
    if (liveMatches.isEmpty()) {
        return emptyList()
    }

    val cards = linkedMapOf<String, LivePresentationMatchCard>()
    val liveKeys = linkedSetOf<String>()

    liveMatches.forEach { match ->
        val key = livePresentationMatchKey(match.leagueName, match.homeTeam, match.awayTeam)
        liveKeys += key
        cards[key] = LivePresentationMatchCard(
            key = key,
            competitionName = match.leagueName,
            homeTeam = match.homeTeam,
            awayTeam = match.awayTeam,
            homeScore = match.homeScore,
            awayScore = match.awayScore,
            minuteLabel = match.minute?.let { "$it'" },
            homeBadgeUrl = match.homeTeamBadgeUrl,
            awayBadgeUrl = match.awayTeamBadgeUrl,
            homeScorers = match.homeScorers.map { "${it.player} ${it.minuteLabel}".trim() },
            awayScorers = match.awayScorers.map { "${it.player} ${it.minuteLabel}".trim() },
            homeCards = emptyList(),
            awayCards = emptyList(),
            homeSubs = emptyList(),
            awaySubs = emptyList()
        )
    }

    events.forEach { event ->
        val key = livePresentationMatchKey(event.competitionName, event.homeTeam, event.awayTeam)
        val existing = cards[key]

        if (existing == null || !liveKeys.contains(key)) {
            return@forEach
        }

        val homeScorers = existing.homeScorers.toMutableList()
        val awayScorers = existing.awayScorers.toMutableList()
        val homeCards = existing.homeCards.toMutableList()
        val awayCards = existing.awayCards.toMutableList()
        val homeSubs = existing.homeSubs.toMutableList()
        val awaySubs = existing.awaySubs.toMutableList()

        val minuteText = event.minuteLabel?.takeIf { it.isNotBlank() }?.let { " (${it.trim()})" }.orEmpty()
        val playerName = event.playerName?.takeIf { it.isNotBlank() }
        val playerOut = event.playerOutName?.takeIf { it.isNotBlank() }

        when (event.eventType) {
            "goal", "penalty" -> {
                val item = playerName?.let { "$it$minuteText" } ?: "Goal$minuteText"
                if (event.teamSide == "away") {
                    addUnique(awayScorers, item)
                } else {
                    addUnique(homeScorers, item)
                }
            }
            "yellow-card" -> {
                val item = (playerName ?: "Yellow card") + minuteText
                if (event.teamSide == "away") {
                    addUnique(awayCards, "YC $item")
                } else {
                    addUnique(homeCards, "YC $item")
                }
            }
            "red-card" -> {
                val item = (playerName ?: "Red card") + minuteText
                if (event.teamSide == "away") {
                    addUnique(awayCards, "RC $item")
                } else {
                    addUnique(homeCards, "RC $item")
                }
            }
            "substitution" -> {
                val subText =
                    when {
                        playerName != null && playerOut != null -> "$playerName for $playerOut$minuteText"
                        playerName != null -> "$playerName on$minuteText"
                        else -> "Substitution$minuteText"
                    }

                if (event.teamSide == "away") {
                    addUnique(awaySubs, subText)
                } else {
                    addUnique(homeSubs, subText)
                }
            }
        }

        cards[key] = LivePresentationMatchCard(
            key = key,
            competitionName = existing.competitionName,
            homeTeam = existing.homeTeam,
            awayTeam = existing.awayTeam,
            homeScore = event.homeScore ?: existing.homeScore,
            awayScore = event.awayScore ?: existing.awayScore,
            minuteLabel = event.minuteLabel ?: existing.minuteLabel,
            homeBadgeUrl = existing.homeBadgeUrl,
            awayBadgeUrl = existing.awayBadgeUrl,
            homeScorers = homeScorers,
            awayScorers = awayScorers,
            homeCards = homeCards,
            awayCards = awayCards,
            homeSubs = homeSubs,
            awaySubs = awaySubs
        )
    }

    return cards.values
        .sortedWith(compareBy<LivePresentationMatchCard> { it.competitionName.lowercase() }.thenBy { it.homeTeam.lowercase() })
}

private fun livePresentationMatchKey(
    competitionName: String,
    homeTeam: String,
    awayTeam: String
): String {
    return "${competitionName.lowercase(Locale.ROOT)}|${homeTeam.lowercase(Locale.ROOT)}|${awayTeam.lowercase(Locale.ROOT)}"
}

private fun buildLiveBottomTickerText(
    cards: List<LivePresentationMatchCard>,
    todayResults: List<ScoreMatch>,
    tomorrowFixtures: List<ScoreMatch>
): String {
    if (cards.isNotEmpty()) {
        val liveText = cards.joinToString("   •   ") { card ->
            "${card.competitionName.uppercase(Locale.ROOT)} ${card.homeTeam} ${card.homeScore ?: "-"} ${card.awayTeam} ${card.awayScore ?: "-"} (${toPresentationMinute(card.minuteLabel)})"
        }

        return toAlwaysScrollingTicker(liveText)
    }

    val sections = mutableListOf<String>()

    if (todayResults.isNotEmpty()) {
        val resultsText = todayResults.joinToString(" | ") { result ->
            "${result.leagueName.uppercase(Locale.ROOT)} ${result.homeTeam} ${toTickerScoreToken(result.homeScore)} ${result.awayTeam} ${toTickerScoreToken(result.awayScore)}"
        }

        sections += "TODAY RESULTS: $resultsText"
    }

    if (tomorrowFixtures.isNotEmpty()) {
        val grouped = tomorrowFixtures.groupBy { it.leagueName }

        val tomorrowText = grouped.entries.joinToString("   •   ") { (league, fixtures) ->
            val fixturesText = fixtures.joinToString(" | ") { fixture ->
                "${fixture.homeTeam} v ${fixture.awayTeam} ${formatKickoffTime(fixture.kickoffUtc)}"
            }

            "${league.uppercase(Locale.ROOT)}: $fixturesText"
        }

        sections += "TOMORROW FIXTURES: $tomorrowText"
    }

    if (sections.isNotEmpty()) {
        return toAlwaysScrollingTicker(sections.joinToString("   •   "))
    }

    return toAlwaysScrollingTicker("NO LIVE GAMES RIGHT NOW. CHECK BACK SOON FOR LIVE SCORES.")
}

private fun toTickerScoreToken(score: Int?): String {
    return when (score) {
        null -> "-"
        0 -> "nil"
        else -> score.toString()
    }
}

private fun toAlwaysScrollingTicker(raw: String): String {
    val base = raw.trim().ifBlank { "LIVE TICKER" }
    return List(4) { base }.joinToString("   •   ")
}

private fun toPresentationMinute(minuteLabel: String?): String {
    val raw = minuteLabel?.trim().orEmpty()

    if (raw.isBlank()) {
        return "-- mins"
    }

    if (raw.equals("HT", ignoreCase = true) || raw.equals("FT", ignoreCase = true)) {
        return raw.uppercase(Locale.ROOT)
    }

    return raw.removeSuffix("'") + " mins"
}

private fun addUnique(items: MutableList<String>, value: String) {
    val normalized = value.trim()

    if (normalized.isBlank()) {
        return
    }

    if (!items.contains(normalized)) {
        items += normalized
    }
}

@Composable
private fun LiveTickerDialog(
    events: List<LiveTickerEvent>,
    isLoading: Boolean,
    error: String?,
    aiStatus: LiveTickerAiStatus?,
    isAudioMuted: Boolean,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit
) {
    var liveClockLabel by remember { mutableStateOf(currentTickerClockLabel()) }

    LaunchedEffect(Unit) {
        while (true) {
            liveClockLabel = currentTickerClockLabel()
            delay(1_000)
        }
    }

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
                        text = "Live Ticker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tickerTextColor
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAudioMuted) "Unmute" else "Mute",
                            color = tickerTextColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onToggleMute)
                        )
                        Text(
                            text = "Close",
                            color = tickerTextColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onDismiss)
                        )
                    }
                }

                Text(
                    text = if (isAudioMuted) "Audio: MUTED" else "Audio: LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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

                Text(
                    text = buildAiStatusLine(aiStatus),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = tickerTextColor
                )

                val aiIssue = aiStatus?.error?.takeIf { it.isNotBlank() }
                val issueLine = aiIssue ?: error?.takeIf { it.isNotBlank() }

                if (!issueLine.isNullOrBlank()) {
                    Text(
                        text = "Issue: $issueLine",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = liveClockLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun currentTickerClockLabel(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}

@Composable
private fun LiveTickerRow(event: LiveTickerEvent) {
    val eventLabel = toTickerEventLabel(event)
    val minuteLabel = toTickerMinuteLabel(event)
    val palette = tickerEventPalette(event)
    val textColor = MaterialTheme.colorScheme.onSurface
    val shouldHighlightScoringTeam = event.eventType == "goal" || event.eventType == "penalty"
    val homeTeamColor =
        if (shouldHighlightScoringTeam && event.teamSide == "home") tickerHighlightHome else textColor
    val awayTeamColor =
        if (shouldHighlightScoringTeam && event.teamSide == "away") tickerHighlightAway else textColor
    val commentaryText = stripInitialDotPlayerNames(
        event.commentary?.trim().orEmpty().ifBlank {
            toTickerEventSuffix(event)
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.competitionName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = palette.badgeBackground,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = eventLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.badgeText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = minuteLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.homeTeam,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = homeTeamColor
                )

                Text(
                    text = "${event.homeScore ?: "-"} - ${event.awayScore ?: "-"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.scoreColor
                )

                Text(
                    text = event.awayTeam,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = awayTeamColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = palette.commentaryBackground,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        border = BorderStroke(1.dp, palette.commentaryBorder),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = commentaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.commentaryText,
                    maxLines = 3
                )
            }
        }
    }
}

private fun toTickerEventLabel(event: LiveTickerEvent): String {
    return when (event.eventType) {
        "goal" -> "GOAL"
        "penalty" -> "PENALTY"
        "substitution" -> "SUB"
        "yellow-card" -> "YELLOW"
        "red-card" -> "RED"
        "half-time" -> "HT"
        "full-time" -> "FT"
        else -> "EVENT"
    }
}

private fun toTickerMinuteLabel(event: LiveTickerEvent): String {
    val raw = event.minuteLabel?.trim().orEmpty()

    if (raw.isNotBlank()) {
        return raw
    }

    return when (event.eventType) {
        "half-time" -> "HT"
        "full-time" -> "FT"
        else -> "--"
    }
}

private fun tickerEventPalette(event: LiveTickerEvent): TickerEventPalette {
    return when (event.eventType) {
        "goal" -> TickerEventPalette(
            border = Color(0xFF2E7D32),
            badgeBackground = Color(0xFF2E7D32),
            badgeText = Color.White,
            scoreColor = Color(0xFF1B5E20),
            commentaryBackground = Color(0xFFE8F5E9),
            commentaryBorder = Color(0xFFA5D6A7),
            commentaryText = Color(0xFF1B5E20)
        )
        "penalty" -> TickerEventPalette(
            border = Color(0xFF1565C0),
            badgeBackground = Color(0xFF1565C0),
            badgeText = Color.White,
            scoreColor = Color(0xFF0D47A1),
            commentaryBackground = Color(0xFFE3F2FD),
            commentaryBorder = Color(0xFF90CAF9),
            commentaryText = Color(0xFF0D47A1)
        )
        "substitution" -> TickerEventPalette(
            border = Color(0xFF00695C),
            badgeBackground = Color(0xFF00695C),
            badgeText = Color.White,
            scoreColor = Color(0xFF004D40),
            commentaryBackground = Color(0xFFE0F2F1),
            commentaryBorder = Color(0xFF80CBC4),
            commentaryText = Color(0xFF004D40)
        )
        "yellow-card" -> TickerEventPalette(
            border = Color(0xFFF9A825),
            badgeBackground = Color(0xFFF9A825),
            badgeText = Color(0xFF4E342E),
            scoreColor = Color(0xFF6D4C41),
            commentaryBackground = Color(0xFFFFF8E1),
            commentaryBorder = Color(0xFFFFE082),
            commentaryText = Color(0xFF5D4037)
        )
        "red-card" -> TickerEventPalette(
            border = Color(0xFFC62828),
            badgeBackground = Color(0xFFC62828),
            badgeText = Color.White,
            scoreColor = Color(0xFF8E0000),
            commentaryBackground = Color(0xFFFFEBEE),
            commentaryBorder = Color(0xFFFFCDD2),
            commentaryText = Color(0xFF8B0000)
        )
        "half-time" -> TickerEventPalette(
            border = Color(0xFF546E7A),
            badgeBackground = Color(0xFF546E7A),
            badgeText = Color.White,
            scoreColor = Color(0xFF37474F),
            commentaryBackground = Color(0xFFECEFF1),
            commentaryBorder = Color(0xFFCFD8DC),
            commentaryText = Color(0xFF37474F)
        )
        "full-time" -> TickerEventPalette(
            border = Color(0xFF283593),
            badgeBackground = Color(0xFF283593),
            badgeText = Color.White,
            scoreColor = Color(0xFF1A237E),
            commentaryBackground = Color(0xFFE8EAF6),
            commentaryBorder = Color(0xFFC5CAE9),
            commentaryText = Color(0xFF1A237E)
        )
        else -> TickerEventPalette(
            border = Color(0xFF455A64),
            badgeBackground = Color(0xFF455A64),
            badgeText = Color.White,
            scoreColor = Color(0xFF263238),
            commentaryBackground = Color(0xFFF1F5F7),
            commentaryBorder = Color(0xFFD7E1E5),
            commentaryText = Color(0xFF263238)
        )
    }
}

@Composable
private fun LiveTickerNarrator(
    queue: List<LiveNarrationItem>,
    isMuted: Boolean,
    settings: TtsNarrationSettings,
    onConsume: (String) -> Unit
) {
    val context = LocalContext.current
    var isReady by remember { mutableStateOf(false) }
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
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

    LaunchedEffect(isReady, settings) {
        if (!isReady) {
            return@LaunchedEffect
        }

        val engine = ttsState.value ?: return@LaunchedEffect
        val selectedLocale = configureNarrationLocale(engine)
        configureNarrationVoice(engine, selectedLocale, settings)
        engine.setSpeechRate(clampTtsSpeed(settings.speed))
        engine.setPitch(clampTtsPitch(settings.pitch))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    LaunchedEffect(queue, isReady, isMuted) {
        if (!isReady) {
            return@LaunchedEffect
        }

        val nextItem = queue.firstOrNull() ?: return@LaunchedEffect
        val text = stripInitialDotPlayerNames(nextItem.text.trim())

        if (text.isBlank()) {
            onConsume(nextItem.eventKey)
            return@LaunchedEffect
        }

        if (isMuted) {
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

private fun configureNarrationLocale(engine: TextToSpeech): Locale {
    val fallbackLocales = listOf(Locale.UK, Locale.US, Locale.getDefault())

    fallbackLocales.forEach { locale ->
        val languageResult = engine.setLanguage(locale)

        if (
            languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return locale
        }
    }

    return Locale.getDefault()
}

private fun configureNarrationVoice(
    engine: TextToSpeech,
    locale: Locale,
    settings: TtsNarrationSettings
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        return
    }

    val candidateVoices = engine.voices
        ?.asSequence()
        ?.filter { voice ->
            val voiceLocale = voice.locale ?: return@filter false
            val sameLanguage = voiceLocale.language.equals(locale.language, ignoreCase = true)
            val isNotInstalled =
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
            sameLanguage && !isNotInstalled
        }
        ?.toList()
        .orEmpty()

    val selectedByName = settings.selectedVoiceName
        ?.takeIf { it.isNotBlank() }
        ?.let { selectedVoiceName ->
            candidateVoices.firstOrNull { voice -> voice.name == selectedVoiceName }
        }

    val selectedVoice = selectedByName
        ?: candidateVoices.maxByOrNull { voice ->
            narrationVoiceScore(
                voice = voice,
                locale = locale,
                preferAiVoice = settings.preferAiVoice
            )
        }

    if (selectedVoice != null) {
        engine.voice = selectedVoice
    }
}

private fun narrationVoiceScore(
    voice: Voice,
    locale: Locale,
    preferAiVoice: Boolean
): Int {
    var score = 0
    val voiceLocale = voice.locale

    if (voiceLocale != null && voiceLocale.language.equals(locale.language, ignoreCase = true)) {
        score += 300
    }

    if (locale.country.isNotBlank() && voiceLocale?.country == locale.country) {
        score += 60
    }

    if (voice.isNetworkConnectionRequired) {
        score += if (preferAiVoice) 85 else -8
    } else {
        score += if (preferAiVoice) 6 else 20
    }

    score += voice.quality
    score -= voice.latency / 3

    val normalizedName = voice.name.lowercase(Locale.ROOT)
    if (
        normalizedName.contains("neural") ||
        normalizedName.contains("neural2") ||
        normalizedName.contains("journey") ||
        normalizedName.contains("natural") ||
        normalizedName.contains("wavenet") ||
        normalizedName.contains("studio")
    ) {
        score += if (preferAiVoice) 80 else 40
    }

    if (
        normalizedName.contains("legacy") ||
        normalizedName.contains("embedded") ||
        normalizedName.contains("espeak")
    ) {
        score -= 30
    }

    return score
}

private fun buildAiStatusLine(aiStatus: LiveTickerAiStatus?): String {
    if (aiStatus == null) {
        return "AI: UNKNOWN"
    }

    val modelSuffix = aiStatus.model?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

    return when (aiStatus.status.lowercase()) {
        "active" -> "AI: ACTIVE$modelSuffix"
        "fallback" -> "AI: FALLBACK$modelSuffix"
        "missing-key" -> "AI: MISSING KEY"
        "disabled" -> "AI: OFF"
        else -> "AI: ${aiStatus.status.uppercase()}$modelSuffix"
    }
}

private fun toTickerStatusLabel(event: LiveTickerEvent): String {
    return when (event.eventType) {
        "goal" -> "GL"
        "penalty" -> "PEN"
        "substitution" -> "SUB"
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
        "ligue 1" -> "L1"
        "bundesliga" -> "BUN"
        "la liga" -> "LL"
        "fa cup" -> "FAC"
        "coppa italia" -> "COP"
        "scottish premiership" -> "SP"
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
    val player = toDisplaySurname(event.playerName).takeIf { it.isNotBlank() }
    val playerOut = toDisplaySurname(event.playerOutName).takeIf { it.isNotBlank() }

    return when (event.eventType) {
        "goal" -> if (player != null) "GOAL $player" else "GOAL"
        "penalty" -> if (player != null) "PENALTY GOAL $player" else "PENALTY GOAL"
        "substitution" -> when {
            player != null && playerOut != null -> "SUBSTITUTION $player FOR $playerOut"
            player != null -> "SUBSTITUTION $player ON"
            else -> "SUBSTITUTION"
        }
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
                    text = "${toDisplaySurname(scorer.player)} ${scorer.minuteLabel}",
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
            text = toDisplaySurname(substitute.name),
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
    return "$jerseyPrefix${toDisplaySurname(player.name)}$positionSuffix"
}

@Composable
private fun CompetitionTabBadge(
    badgeUrl: String?,
    contentDescription: String
) {
    if (badgeUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contentDescription.take(1).uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        return
    }

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
    return scorers.joinToString(" • ") { "${toDisplaySurname(it.player)} ${it.minuteLabel}" }
}

private fun stripInitialDotPlayerNames(value: String): String {
    if (value.isBlank()) {
        return value
    }

    return initialDotNameRegex.replace(value) { match ->
        match.groupValues.getOrElse(1) { match.value }
    }
}

private fun toDisplaySurname(value: String?): String {
    val cleaned = value
        ?.trim()
        .orEmpty()
        .replace(Regex("\\s+"), " ")

    if (cleaned.isBlank()) {
        return ""
    }

    val leadingInitialMatch = leadingInitialNameRegex.find(cleaned)
    if (leadingInitialMatch != null) {
        return leadingInitialMatch.groupValues.getOrElse(1) { cleaned }
    }

    val tokens = cleaned.split(" ").filter { it.isNotBlank() }.toMutableList()

    while (tokens.size > 1 && initialTokenRegex.matches(tokens.first())) {
        tokens.removeAt(0)
    }

    if (tokens.size <= 1) {
        return tokens.firstOrNull() ?: cleaned
    }

    val last = tokens.last()
    val previous = tokens[tokens.lastIndex - 1]

    return if (surnameJoiners.contains(previous.lowercase(Locale.ROOT))) {
        "$previous $last"
    } else {
        last
    }
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

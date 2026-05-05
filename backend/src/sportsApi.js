import axios from "axios";
import { config } from "./config.js";

const TARGET_COMPETITIONS = [
  {
    key: "premier-league",
    aliases: ["premier league"]
  },
  {
    key: "championship",
    aliases: ["championship"]
  },
  {
    key: "ligue-1",
    aliases: ["ligue 1", "ligue1", "french ligue 1"]
  },
  {
    key: "bundesliga",
    aliases: ["bundesliga", "german bundesliga"]
  },
  {
    key: "la-liga",
    aliases: ["la liga", "laliga", "spanish la liga", "primera division"]
  },
  {
    key: "fa-cup",
    aliases: ["fa cup"]
  },
  {
    key: "coppa-italia",
    aliases: ["coppa italia", "italy cup"]
  },
  {
    key: "scottish-premiership",
    aliases: ["scottish premiership", "premiership (scotland)", "scotland premiership"]
  },
  {
    key: "carabao-cup",
    aliases: ["carabao cup", "efl cup", "league cup"]
  },
  {
    key: "champions-league",
    aliases: ["champions league", "uefa champions league"]
  },
  {
    key: "europa-league",
    aliases: ["europa league", "uefa europa league"]
  }
];

const KNOWN_COMPETITION_KEYS = new Set(
  TARGET_COMPETITIONS.map((competition) => competition.key)
);

const API_HEADERS = {
  Authorization: `Token ${config.apiKey}`
};

const API_TIMEOUT_MS = 20_000;
const GOAL_INCIDENT_TYPES = new Set(["goal", "penalty_goal", "own_goal"]);
const TEAM_BADGE_BASE_URL = "https://sports.bzzoiro.com/img/team";
const VALID_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_PAGINATION_PAGES = 50;

const extractList = (payload) => {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (Array.isArray(payload?.results)) {
    return payload.results;
  }

  if (Array.isArray(payload?.data)) {
    return payload.data;
  }

  return [];
};

const resolveCompetitionKey = (event) => {
  const leagueName = String(
    event?.league?.name ?? event?.league_name ?? event?.group_name ?? ""
  ).toLowerCase();

  for (const competition of TARGET_COMPETITIONS) {
    if (competition.aliases.some((alias) => leagueName.includes(alias))) {
      return competition.key;
    }
  }

  return null;
};

const isKnownCompetitionKey = (competitionKey) => {
  return KNOWN_COMPETITION_KEYS.has(competitionKey);
};

const parseCompetitionFilterKeys = (competitionKey) => {
  if (competitionKey === null || competitionKey === undefined) {
    return null;
  }

  const requested = String(competitionKey)
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);

  if (requested.length === 0) {
    return null;
  }

  return Array.from(new Set(requested)).filter((key) => isKnownCompetitionKey(key));
};

const isTargetLeague = (event) => {
  return resolveCompetitionKey(event) !== null;
};

const withCompetitionFilter = (events, competitionKey) => {
  const filterKeys = parseCompetitionFilterKeys(competitionKey);

  if (filterKeys === null) {
    return events;
  }

  if (filterKeys.length === 0) {
    return [];
  }

  const keySet = new Set(filterKeys);

  return events.filter((event) => {
    const resolvedKey = resolveCompetitionKey(event);
    return resolvedKey !== null && keySet.has(resolvedKey);
  });
};

const normalizeTeamName = (event, side) => {
  const objKey = side === "home" ? "home_team_obj" : "away_team_obj";
  const fallbackObj = side === "home" ? "home_team" : "away_team";

  return (
    event?.[objKey]?.name ??
    event?.[fallbackObj]?.name ??
    String(event?.[fallbackObj] ?? (side === "home" ? "Home" : "Away"))
  );
};

const normalizeTeamId = (event, side) => {
  const objKey = side === "home" ? "home_team_obj" : "away_team_obj";
  const fallbackObj = side === "home" ? "home_team" : "away_team";

  return toNumber(event?.[objKey]?.id ?? event?.[fallbackObj]?.id ?? event?.[fallbackObj]);
};

const toTeamBadgeUrl = (teamId) => {
  return teamId ? `${TEAM_BADGE_BASE_URL}/${teamId}/` : null;
};

const toNumber = (value) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const toMinuteLabel = (incident) => {
  const minute = toNumber(incident?.minute);
  const addedTime = toNumber(incident?.added_time);

  if (minute === null) {
    return "?";
  }

  return addedTime && addedTime > 0 ? `${minute}+${addedTime}'` : `${minute}'`;
};

const toMinuteSort = (incident) => {
  const minute = toNumber(incident?.minute) ?? 0;
  const addedTime = toNumber(incident?.added_time) ?? 0;
  return minute * 100 + addedTime;
};

const normalizeIncidentType = (value) => {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, "");
};

const toUpperLabel = (value) => {
  return String(value ?? "")
    .trim()
    .replace(/\s+/g, " ")
    .toUpperCase();
};

const toCoachName = (value) => {
  if (!value) {
    return null;
  }

  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }

  if (typeof value === "object") {
    const preferred = String(value.name ?? value.short_name ?? "").trim();
    return preferred ? preferred : null;
  }

  return null;
};

const SURNAME_JOINERS = new Set([
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
]);

const toSurname = (value) => {
  const raw = String(value ?? "").trim().replace(/\s+/g, " ");

  if (!raw) {
    return "Unknown";
  }

  const initialDotMatch = raw.match(/^[A-Za-z]\.[\s]*([A-Za-z][\p{L}\-']*)$/u);
  if (initialDotMatch) {
    return initialDotMatch[1];
  }

  const tokens = raw.split(" ").filter((token) => token.trim().length > 0);

  if (tokens.length <= 1) {
    return raw;
  }

  while (
    tokens.length > 1 &&
    /^([A-Za-z]\.?){1,2}$/u.test(tokens[0])
  ) {
    tokens.shift();
  }

  if (tokens.length <= 1) {
    return tokens[0] ?? raw;
  }

  const last = tokens[tokens.length - 1];
  const previous = tokens[tokens.length - 2];

  if (SURNAME_JOINERS.has(previous.toLowerCase())) {
    return `${previous} ${last}`;
  }

  return last;
};

const pickFirstNumber = (obj, keys) => {
  for (const key of keys) {
    const parsed = toNumber(obj?.[key]);

    if (parsed !== null) {
      return Math.round(parsed);
    }
  }

  return null;
};

const normalizePlayer = (player) => {
  const jerseyRaw = player?.jersey_number;
  const positionRaw = player?.position ?? player?.specific_position;

  return {
    name: toSurname(player?.name),
    jerseyNumber: jerseyRaw !== null && jerseyRaw !== undefined ? String(jerseyRaw) : null,
    position: positionRaw ? String(positionRaw) : null,
    subOutMinute: toNumber(player?.sub_out),
    yellowCard: Boolean(player?.yellow_card),
    redCard: Boolean(player?.red_card)
  };
};

const normalizeSubstitution = (substitute, playersById) => {
  const replacedPlayerId = toNumber(substitute?.replaces_player_id);

  return {
    name: toSurname(substitute?.name),
    minuteIn: toNumber(substitute?.sub_in),
    replacedPlayerName:
      replacedPlayerId !== null ? playersById.get(replacedPlayerId) ?? null : null,
    yellowCard: Boolean(substitute?.yellow_card),
    redCard: Boolean(substitute?.red_card)
  };
};

const normalizeTeamLineup = ({ lineup, fallbackCoach }) => {
  const playersRaw = Array.isArray(lineup?.players) ? lineup.players : [];
  const substitutesRaw = Array.isArray(lineup?.substitutes) ? lineup.substitutes : [];
  const playersById = new Map(
    playersRaw
      .map((player) => [toNumber(player?.player_id), toSurname(player?.name)])
      .filter(([playerId]) => playerId !== null)
  );

  const startingRaw = playersRaw.filter((player) => toNumber(player?.sub_in) === null);
  const starting11 = (startingRaw.length > 0 ? startingRaw : playersRaw).map(normalizePlayer);
  const substitutions = substitutesRaw.map((substitute) =>
    normalizeSubstitution(substitute, playersById)
  );

  return {
    managerName:
      toCoachName(lineup?.coach) ??
      toCoachName(lineup?.manager) ??
      toCoachName(fallbackCoach),
    formation: lineup?.formation ? String(lineup.formation) : null,
    starting11,
    substitutions
  };
};

const normalizeStats = (homeStats, awayStats) => {
  return {
    possessionHome: pickFirstNumber(homeStats, ["ball_possession", "possession"]),
    possessionAway: pickFirstNumber(awayStats, ["ball_possession", "possession"]),
    shotsHome: pickFirstNumber(homeStats, ["total_shots", "shots"]),
    shotsAway: pickFirstNumber(awayStats, ["total_shots", "shots"]),
    shotsOnTargetHome: pickFirstNumber(homeStats, ["shots_on_target", "shots_on_goal"]),
    shotsOnTargetAway: pickFirstNumber(awayStats, ["shots_on_target", "shots_on_goal"]),
    cornersHome: pickFirstNumber(homeStats, ["corner_kicks", "corners"]),
    cornersAway: pickFirstNumber(awayStats, ["corner_kicks", "corners"]),
    yellowCardsHome: pickFirstNumber(homeStats, ["yellow_cards"]),
    yellowCardsAway: pickFirstNumber(awayStats, ["yellow_cards"]),
    redCardsHome: pickFirstNumber(homeStats, ["red_cards"]),
    redCardsAway: pickFirstNumber(awayStats, ["red_cards"])
  };
};

const extractGoalScorers = (event) => {
  const incidents = Array.isArray(event?.incidents) ? event.incidents : [];
  const home = [];
  const away = [];

  for (const incident of incidents) {
    if (!GOAL_INCIDENT_TYPES.has(incident?.type)) {
      continue;
    }

    if (incident?.is_home !== true && incident?.is_home !== false) {
      continue;
    }

    const scorer = {
      player: toSurname(incident?.player),
      minuteLabel: toMinuteLabel(incident),
      sortOrder: toMinuteSort(incident)
    };

    const isOwnGoal = incident?.type === "own_goal" || incident?.goal_type === "own_goal";

    if ((incident.is_home && !isOwnGoal) || (!incident.is_home && isOwnGoal)) {
      home.push(scorer);
    } else {
      away.push(scorer);
    }
  }

  const toPublic = (items) =>
    items
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(({ player, minuteLabel }) => ({ player, minuteLabel }));

  return {
    homeScorers: toPublic(home),
    awayScorers: toPublic(away)
  };
};

const normalizeMatch = (event) => {
  const { homeScorers, awayScorers } = extractGoalScorers(event);
  const homeTeamId = normalizeTeamId(event, "home");
  const awayTeamId = normalizeTeamId(event, "away");
  const competitionKey = resolveCompetitionKey(event);
  const addedTimeRaw = toNumber(event?.added_time ?? event?.injury_time);
  const addedTime =
    addedTimeRaw !== null && addedTimeRaw > 0
      ? Math.round(addedTimeRaw)
      : null;
  const venueName =
    event?.venue?.name ??
    event?.venue_name ??
    null;

  return {
    id: event?.id,
    leagueId: event?.league?.id ?? null,
    leagueName: event?.league?.name ?? "Unknown League",
    competitionKey,
    kickoffUtc: event?.event_date ?? null,
    venueName,
    status: event?.status ?? "UNKNOWN",
    minute: event?.current_minute ?? null,
    addedTime,
    homeTeamId,
    awayTeamId,
    homeTeamBadgeUrl: toTeamBadgeUrl(homeTeamId),
    awayTeamBadgeUrl: toTeamBadgeUrl(awayTeamId),
    homeTeam: normalizeTeamName(event, "home"),
    awayTeam: normalizeTeamName(event, "away"),
    homeScore: event?.home_score ?? null,
    awayScore: event?.away_score ?? null,
    homeScorers,
    awayScorers
  };
};

const normalizeMatchDetails = (event) => {
  const { homeScorers, awayScorers } = extractGoalScorers(event);
  const homeTeamId = normalizeTeamId(event, "home");
  const awayTeamId = normalizeTeamId(event, "away");
  const venueName = event?.venue?.name ?? event?.venue_name ?? null;
  const lineups = event?.lineups ?? {};
  const homeLiveStats = event?.live_stats?.home ?? event?.sr_stats?.home ?? {};
  const awayLiveStats = event?.live_stats?.away ?? event?.sr_stats?.away ?? {};

  return {
    id: event?.id,
    status: event?.status ?? "UNKNOWN",
    minute: event?.current_minute ?? null,
    refereeName: toCoachName(event?.referee),
    venueName,
    homeTeam: normalizeTeamName(event, "home"),
    awayTeam: normalizeTeamName(event, "away"),
    homeTeamBadgeUrl: toTeamBadgeUrl(homeTeamId),
    awayTeamBadgeUrl: toTeamBadgeUrl(awayTeamId),
    homeScore: event?.home_score ?? null,
    awayScore: event?.away_score ?? null,
    homeScorers,
    awayScorers,
    stats: normalizeStats(homeLiveStats, awayLiveStats),
    lineups: {
      home: normalizeTeamLineup({
        lineup: lineups?.home,
        fallbackCoach: event?.home_coach ?? event?.home_team_obj?.coach
      }),
      away: normalizeTeamLineup({
        lineup: lineups?.away,
        fallbackCoach: event?.away_coach ?? event?.away_team_obj?.coach
      })
    }
  };
};

const toTickerMinuteSort = (incident) => {
  const minute = toNumber(incident?.minute) ?? 0;
  const addedTime = toNumber(incident?.added_time);
  const normalizedAdded = addedTime !== null && addedTime > 0 && addedTime < 30 ? addedTime : 0;
  return minute * 100 + normalizedAdded;
};

const toTickerMessage = ({
  leagueName,
  homeTeam,
  awayTeam,
  homeScore,
  awayScore,
  eventLabel,
  playerName = null
}) => {
  const scoreToken = (value) => {
    if (value === 0) {
      return "nil";
    }

    return Number.isFinite(Number(value)) ? String(value) : "unknown";
  };

  const scoreline = `${homeTeam} ${scoreToken(homeScore)} ${awayTeam} ${scoreToken(awayScore)}`;
  const suffix = playerName ? ` ${playerName}` : "";
  return `${leagueName} - ${scoreline} - ${eventLabel}${suffix}`;
};

const normalizeTickerIncident = (event, incident) => {
  const incidentTypeRaw = String(incident?.type ?? "").toLowerCase();
  const incidentType = normalizeIncidentType(incident?.type);
  const leagueName = String(event?.league?.name ?? event?.group_name ?? "Unknown League");
  const homeTeam = normalizeTeamName(event, "home");
  const awayTeam = normalizeTeamName(event, "away");
  const homeScore = event?.home_score ?? incident?.home_score ?? null;
  const awayScore = event?.away_score ?? incident?.away_score ?? null;
  const playerName = incident?.player ? toSurname(incident.player) : null;
  const minuteLabel = toMinuteLabel(incident);
  const teamSide =
    incident?.is_home === true ? "home" : incident?.is_home === false ? "away" : null;

  if (incidentType === "goal" || incidentType === "penaltygoal" || incidentType === "owngoal") {
    const goalType = String(incident?.goal_type ?? "regular").toLowerCase();
    const isPenaltyGoal = incidentType === "penaltygoal" || goalType === "penalty";
    const isOwnGoal = incidentType === "owngoal" || goalType === "own_goal";
    const eventLabel = isPenaltyGoal ? "PENALTY GOAL" : isOwnGoal ? "OWN GOAL" : "GOAL";

    return {
      eventKey: [
        "live",
        String(event?.id ?? "na"),
        incidentTypeRaw,
        minuteLabel,
        String(playerName ?? ""),
        goalType,
        String(homeScore ?? ""),
        String(awayScore ?? "")
      ].join(":"),
      eventType: isPenaltyGoal ? "penalty" : "goal",
      teamSide,
      competitionName: leagueName,
      homeTeam,
      awayTeam,
      homeScore,
      awayScore,
      minuteLabel,
      playerName,
      message: toTickerMessage({
        leagueName,
        homeTeam,
        awayTeam,
        homeScore,
        awayScore,
        eventLabel,
        playerName
      }),
      sortValue: toTickerMinuteSort(incident)
    };
  }

  if (incidentType === "card") {
    const cardType = String(incident?.card_type ?? "yellow").toLowerCase();
    const eventLabel = cardType === "red" ? "RED CARD" : "YELLOW CARD";

    return {
      eventKey: [
        "live",
        String(event?.id ?? "na"),
        incidentType,
        minuteLabel,
        String(playerName ?? ""),
        cardType
      ].join(":"),
      eventType: cardType === "red" ? "red-card" : "yellow-card",
      teamSide,
      competitionName: leagueName,
      homeTeam,
      awayTeam,
      homeScore,
      awayScore,
      minuteLabel,
      playerName,
      message: toTickerMessage({
        leagueName,
        homeTeam,
        awayTeam,
        homeScore,
        awayScore,
        eventLabel,
        playerName
      }),
      sortValue: toTickerMinuteSort(incident)
    };
  }

  if (incidentType === "period") {
    const periodText = String(incident?.text ?? "").toLowerCase();
    const isHalfTime = periodText.includes("ht") || periodText.includes("half time");
    const isFullTime = periodText.includes("ft") || periodText.includes("full time");

    if (!isHalfTime && !isFullTime) {
      return null;
    }

    const eventLabel = isHalfTime ? "HALF TIME" : "FULL TIME";
    const statusMinuteLabel = isHalfTime ? "HT" : "FT";

    return {
      eventKey: [
        "live",
        String(event?.id ?? "na"),
        incidentType,
        statusMinuteLabel,
        eventLabel
      ].join(":"),
      eventType: isHalfTime ? "half-time" : "full-time",
      teamSide: null,
      competitionName: leagueName,
      homeTeam,
      awayTeam,
      homeScore,
      awayScore,
      minuteLabel: statusMinuteLabel,
      playerName: null,
      message: toTickerMessage({
        leagueName,
        homeTeam,
        awayTeam,
        homeScore,
        awayScore,
        eventLabel
      }),
      sortValue: toTickerMinuteSort(incident)
    };
  }

  if (incidentType === "substitution") {
    const playerIn = incident?.player_in ? toSurname(incident.player_in) : null;
    const playerOut = incident?.player_out ? toSurname(incident.player_out) : null;
    const substitutionDetail =
      playerIn && playerOut
        ? `${playerIn} for ${playerOut}`
        : playerIn ?? playerOut;

    return {
      eventKey: [
        "live",
        String(event?.id ?? "na"),
        incidentType,
        minuteLabel,
        String(playerIn ?? ""),
        String(playerOut ?? "")
      ].join(":"),
      eventType: "substitution",
      teamSide,
      competitionName: leagueName,
      homeTeam,
      awayTeam,
      homeScore,
      awayScore,
      minuteLabel,
      playerName: playerIn,
      playerOutName: playerOut,
      message: toTickerMessage({
        leagueName,
        homeTeam,
        awayTeam,
        homeScore,
        awayScore,
        eventLabel: "SUBSTITUTION",
        playerName: substitutionDetail
      }),
      sortValue: toTickerMinuteSort(incident)
    };
  }

  if (incidentType === "injurytime") {
    const periodText = toUpperLabel(incident?.text);
    const addedMinutes = toNumber(incident?.injury_time ?? incident?.added_time);
    const addedText =
      addedMinutes !== null && addedMinutes > 0 ? `${addedMinutes} ADDED MINUTES` : null;
    const eventLabel =
      periodText || addedText
        ? `INJURY TIME ${periodText || addedText}`.trim()
        : "INJURY TIME";

    return {
      eventKey: [
        "live",
        String(event?.id ?? "na"),
        incidentTypeRaw,
        minuteLabel,
        String(periodText || addedText || "")
      ].join(":"),
      eventType: "injury-time",
      teamSide: null,
      competitionName: leagueName,
      homeTeam,
      awayTeam,
      homeScore,
      awayScore,
      minuteLabel,
      playerName: null,
      message: toTickerMessage({
        leagueName,
        homeTeam,
        awayTeam,
        homeScore,
        awayScore,
        eventLabel
      }),
      sortValue: toTickerMinuteSort(incident)
    };
  }

  if (incidentType === "vardecision") {
    const decisionText =
      toUpperLabel(incident?.decision) ||
      toUpperLabel(incident?.var_decision) ||
      toUpperLabel(incident?.text);
    const eventLabel =
      decisionText.length > 0 ? `VAR DECISION ${decisionText}` : "VAR DECISION";

    return {
      eventKey: [
        "live",
        String(event?.id ?? "na"),
        incidentTypeRaw,
        minuteLabel,
        String(playerName ?? ""),
        decisionText
      ].join(":"),
      eventType: "var-decision",
      teamSide,
      competitionName: leagueName,
      homeTeam,
      awayTeam,
      homeScore,
      awayScore,
      minuteLabel,
      playerName,
      message: toTickerMessage({
        leagueName,
        homeTeam,
        awayTeam,
        homeScore,
        awayScore,
        eventLabel,
        playerName
      }),
      sortValue: toTickerMinuteSort(incident)
    };
  }

  return null;
};

const normalizeLiveTickerEvents = (event) => {
  const incidents = Array.isArray(event?.incidents) ? event.incidents : [];

  return incidents
    .map((incident) => normalizeTickerIncident(event, incident))
    .filter((incident) => incident !== null);
};

const sortByKickoff = (a, b) => {
  const aTime = a.kickoffUtc ? Date.parse(a.kickoffUtc) : 0;
  const bTime = b.kickoffUtc ? Date.parse(b.kickoffUtc) : 0;
  return aTime - bTime;
};

const fetchJson = async (path, params = {}) => {
  const response = await axios.get(`${config.apiBaseUrl}${path}`, {
    headers: API_HEADERS,
    params,
    timeout: API_TIMEOUT_MS
  });

  return response.data;
};

const fetchPaginatedList = async (path, params = {}) => {
  const collected = [];
  let pageCount = 0;
  let nextUrl = `${config.apiBaseUrl}${path}`;
  let nextParams = { ...params };

  while (nextUrl && pageCount < MAX_PAGINATION_PAGES) {
    const response = await axios.get(nextUrl, {
      headers: API_HEADERS,
      params: nextParams,
      timeout: API_TIMEOUT_MS
    });

    const payload = response.data;
    collected.push(...extractList(payload));

    nextUrl = payload?.next ?? null;
    nextParams = undefined;
    pageCount += 1;
  }

  return collected;
};

const fetchLiveTargetLeagues = async () => {
  const payload = await fetchJson("/live/", { tz: config.apiTimezone });
  const liveEvents = extractList(payload);
  return liveEvents.filter(isTargetLeague);
};

const fetchEventsByDateRange = async ({ dateFromIso, dateToIso }) => {
  if (!VALID_DATE_RE.test(String(dateFromIso ?? ""))) {
    throw new Error("Invalid date_from format. Expected YYYY-MM-DD.");
  }

  if (!VALID_DATE_RE.test(String(dateToIso ?? ""))) {
    throw new Error("Invalid date_to format. Expected YYYY-MM-DD.");
  }

  const events = await fetchPaginatedList("/events/", {
    date_from: dateFromIso,
    date_to: dateToIso,
    tz: config.apiTimezone
  });

  return events.filter(isTargetLeague);
};

const fetchDateTargetLeagues = async (dateIso) => {
  return fetchEventsByDateRange({ dateFromIso: dateIso, dateToIso: dateIso });
};

export const fetchLiveMatches = async ({ competitionKey = null } = {}) => {
  if (!config.apiKey) {
    throw new Error("SPORTS_API_KEY is missing. Add it to backend/.env");
  }

  const liveMatches = await fetchLiveTargetLeagues();
  const filtered = withCompetitionFilter(liveMatches, competitionKey);

  return filtered.map(normalizeMatch).sort(sortByKickoff);
};

export const fetchMatchesForDate = async ({ dateIso, competitionKey = null }) => {
  if (!config.apiKey) {
    throw new Error("SPORTS_API_KEY is missing. Add it to backend/.env");
  }

  const dateMatches = await fetchDateTargetLeagues(dateIso);
  const filtered = withCompetitionFilter(dateMatches, competitionKey);

  return filtered.map(normalizeMatch).sort(sortByKickoff);
};

export const fetchMatchesForDateWindow = async ({
  dateFromIso,
  dateToIso,
  competitionKey = null
}) => {
  if (!config.apiKey) {
    throw new Error("SPORTS_API_KEY is missing. Add it to backend/.env");
  }

  const matches = await fetchEventsByDateRange({ dateFromIso, dateToIso });
  const filtered = withCompetitionFilter(matches, competitionKey);

  return filtered.map(normalizeMatch).sort(sortByKickoff);
};

export const fetchMatchDetails = async ({ matchId }) => {
  if (!config.apiKey) {
    throw new Error("SPORTS_API_KEY is missing. Add it to backend/.env");
  }

  const normalizedMatchId = toNumber(matchId);

  if (normalizedMatchId === null) {
    throw new Error("Invalid match id");
  }

  const event = await fetchJson(`/events/${normalizedMatchId}/`, {
    tz: config.apiTimezone
  });

  return normalizeMatchDetails(event);
};

export const fetchLiveTickerEvents = async ({ competitionKey = null } = {}) => {
  if (!config.apiKey) {
    throw new Error("SPORTS_API_KEY is missing. Add it to backend/.env");
  }

  const liveMatches = await fetchLiveTargetLeagues();
  const filtered = withCompetitionFilter(liveMatches, competitionKey);

  return filtered
    .flatMap(normalizeLiveTickerEvents)
    .sort((a, b) => b.sortValue - a.sortValue)
    .map(({ sortValue, ...event }) => event);
};

export const fetchRelevantMatches = async () => {
  return fetchLiveMatches();
};

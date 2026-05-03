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
    key: "fa-cup",
    aliases: ["fa cup"]
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

const API_HEADERS = {
  Authorization: `Token ${config.apiKey}`
};

const API_TIMEOUT_MS = 10_000;
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
  return TARGET_COMPETITIONS.some((competition) => competition.key === competitionKey);
};

const isTargetLeague = (event) => {
  return resolveCompetitionKey(event) !== null;
};

const withCompetitionFilter = (events, competitionKey) => {
  if (!competitionKey) {
    return events;
  }

  if (!isKnownCompetitionKey(competitionKey)) {
    return [];
  }

  return events.filter((event) => resolveCompetitionKey(event) === competitionKey);
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
      player: String(incident?.player ?? "Unknown"),
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

export const fetchRelevantMatches = async () => {
  return fetchLiveMatches();
};

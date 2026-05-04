import { generateLiveWelcomeCommentary, getAiCommentaryStatus, withAiCommentary } from "./aiCommentary.js";
import { getDateWindowState, getState } from "./cacheStore.js";
import { config } from "./config.js";
import { fetchLiveTickerEvents, fetchMatchesForDate } from "./sportsApi.js";

const FIXTURE_STATUSES = new Set([
  "notstarted",
  "scheduled",
  "postponed",
  "cancelled",
  "canceled",
  "delayed"
]);

const DEFAULT_COMPETITION_KEYS = [
  null,
  "premier-league",
  "championship",
  "fa-cup",
  "carabao-cup",
  "champions-league",
  "europa-league"
];

const LIVE_FEED_STORAGE_KEY_ALL = "__all__";
const WELCOME_CACHE_TTL_MS = 90_000;
const LIVE_FEED_STALE_MULTIPLIER = 1.8;

const liveFeedSnapshots = new Map();
const welcomeCache = new Map();
const watchedCompetitionKeys = new Set(DEFAULT_COMPETITION_KEYS);
const refreshingKeys = new Set();

let intervalHandle = null;

const currentDateIsoInApiTimezone = () => {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: config.apiTimezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });

  return formatter.format(new Date());
};

const toStorageCompetitionKey = (competitionKey) => {
  const normalized =
    typeof competitionKey === "string" && competitionKey.trim().length > 0
      ? competitionKey.trim()
      : null;

  return normalized ?? LIVE_FEED_STORAGE_KEY_ALL;
};

const ensureCompetitionIsWatched = (competitionKey) => {
  if (typeof competitionKey !== "string") {
    return;
  }

  const normalized = competitionKey.trim();

  if (normalized.length > 0) {
    watchedCompetitionKeys.add(normalized);
  }
};

const toTickerStatusEntries = (matches) => {
  return matches
    .map((match) => {
      const status = String(match?.status ?? "").toLowerCase();
      const label =
        status === "halftime"
          ? "HALF TIME"
          : status === "finished" ||
              status === "fulltime" ||
              status === "ft" ||
              status === "after_extra_time" ||
              status === "aet" ||
              status === "penalties"
            ? "FULL TIME"
            : null;

      if (!label) {
        return null;
      }

      const homeScore = match?.homeScore;
      const awayScore = match?.awayScore;
      const score = `${homeScore ?? "-"} - ${awayScore ?? "-"}`;

      return {
        eventKey: [
          "status",
          String(match?.id ?? `${String(match?.homeTeam ?? "")}:${String(match?.awayTeam ?? "")}`),
          label,
          score
        ].join(":"),
        eventType: label === "HALF TIME" ? "half-time" : "full-time",
        teamSide: null,
        competitionName: String(match?.leagueName ?? "Unknown League"),
        homeTeam: String(match?.homeTeam ?? "Home"),
        awayTeam: String(match?.awayTeam ?? "Away"),
        homeScore,
        awayScore,
        minuteLabel: match?.minute !== null && match?.minute !== undefined ? `${match.minute}'` : null,
        playerName: null,
        message: `${String(match?.leagueName ?? "Unknown League")} - ${String(match?.homeTeam ?? "Home")} ${score} ${String(match?.awayTeam ?? "Away")} - ${label}`
      };
    })
    .filter((entry) => entry !== null);
};

const getCachedDateMatches = ({ dateIso, competitionKey }) => {
  const dateWindow = getDateWindowState();
  const withinWindow =
    Boolean(dateWindow.dateFromIso) &&
    Boolean(dateWindow.dateToIso) &&
    dateIso >= dateWindow.dateFromIso &&
    dateIso <= dateWindow.dateToIso;

  if (!withinWindow) {
    return {
      hit: false,
      matches: []
    };
  }

  const bucket = dateWindow.matchesByDate?.[dateIso];

  if (!Array.isArray(bucket)) {
    return {
      hit: false,
      matches: []
    };
  }

  const matches = competitionKey
    ? bucket.filter((match) => match.competitionKey === competitionKey)
    : bucket;

  return {
    hit: true,
    matches
  };
};

const buildDefaultSnapshot = (error = null) => {
  return {
    source: error ? "error" : "live-feed-cache",
    lastUpdatedUtc: new Date().toISOString(),
    events: [],
    ai: getAiCommentaryStatus(),
    error
  };
};

const setSnapshot = (competitionKey, patch) => {
  const storageKey = toStorageCompetitionKey(competitionKey);

  liveFeedSnapshots.set(storageKey, {
    source: String(patch?.source ?? "live-feed-cache"),
    lastUpdatedUtc: String(patch?.lastUpdatedUtc ?? new Date().toISOString()),
    events: Array.isArray(patch?.events) ? patch.events : [],
    ai: patch?.ai ?? getAiCommentaryStatus(),
    error:
      typeof patch?.error === "string" && patch.error.trim().length > 0
        ? patch.error
        : null
  });
};

const refreshLiveFeedForCompetition = async (competitionKey) => {
  const storageKey = toStorageCompetitionKey(competitionKey);

  if (refreshingKeys.has(storageKey)) {
    return;
  }

  refreshingKeys.add(storageKey);

  try {
    const liveEntries = await fetchLiveTickerEvents({ competitionKey });
    const cachedToday = getCachedDateMatches({
      dateIso: currentDateIsoInApiTimezone(),
      competitionKey
    });
    const statusEntries = cachedToday.hit ? toTickerStatusEntries(cachedToday.matches) : [];
    const merged = new Map();

    for (const event of [...liveEntries, ...statusEntries]) {
      merged.set(event.eventKey, event);
    }

    const eventsWithCommentary = await withAiCommentary(Array.from(merged.values()));

    setSnapshot(competitionKey, {
      source: "live-feed-cache",
      lastUpdatedUtc: new Date().toISOString(),
      events: eventsWithCommentary,
      ai: getAiCommentaryStatus(),
      error: null
    });
  } catch (error) {
    const previous = liveFeedSnapshots.get(storageKey);

    setSnapshot(competitionKey, {
      source: "error",
      lastUpdatedUtc: new Date().toISOString(),
      events: Array.isArray(previous?.events) ? previous.events : [],
      ai: getAiCommentaryStatus(),
      error: error instanceof Error ? error.message : "Unknown backend error"
    });
  } finally {
    refreshingKeys.delete(storageKey);
  }
};

const refreshAllLiveFeedsNow = async () => {
  const keys = Array.from(watchedCompetitionKeys.values());

  for (const competitionKey of keys) {
    await refreshLiveFeedForCompetition(competitionKey);
  }
};

const isSnapshotStale = (snapshot) => {
  if (!snapshot?.lastUpdatedUtc) {
    return true;
  }

  const lastUpdatedMs = Date.parse(snapshot.lastUpdatedUtc);

  if (!Number.isFinite(lastUpdatedMs)) {
    return true;
  }

  const staleAfterMs = Math.max(
    8_000,
    Math.floor(config.liveFeedSyncIntervalMs * LIVE_FEED_STALE_MULTIPLIER)
  );

  return Date.now() - lastUpdatedMs > staleAfterMs;
};

const getWelcomeCommentaryFreshCache = (competitionKey) => {
  const storageKey = toStorageCompetitionKey(competitionKey);
  const todayIso = currentDateIsoInApiTimezone();
  const cached = welcomeCache.get(storageKey);

  if (!cached) {
    return null;
  }

  if (cached.dateIso !== todayIso) {
    return null;
  }

  if (Date.now() - cached.cachedAtMs > WELCOME_CACHE_TTL_MS) {
    return null;
  }

  return cached.text;
};

const setWelcomeCommentaryCache = (competitionKey, text) => {
  const storageKey = toStorageCompetitionKey(competitionKey);

  welcomeCache.set(storageKey, {
    text,
    dateIso: currentDateIsoInApiTimezone(),
    cachedAtMs: Date.now()
  });
};

export const startLiveFeedScheduler = async () => {
  if (intervalHandle) {
    return;
  }

  await refreshAllLiveFeedsNow();

  intervalHandle = setInterval(() => {
    void refreshAllLiveFeedsNow();
  }, config.liveFeedSyncIntervalMs);
};

export const getLiveFeedSnapshot = async ({ competitionKey = null } = {}) => {
  ensureCompetitionIsWatched(competitionKey);

  const storageKey = toStorageCompetitionKey(competitionKey);
  let snapshot = liveFeedSnapshots.get(storageKey);

  if (!snapshot || isSnapshotStale(snapshot)) {
    await refreshLiveFeedForCompetition(competitionKey);
    snapshot = liveFeedSnapshots.get(storageKey);
  }

  return snapshot ?? buildDefaultSnapshot();
};

export const getLiveWelcomeCommentary = async ({ competitionKey = null } = {}) => {
  ensureCompetitionIsWatched(competitionKey);

  const fromCache = getWelcomeCommentaryFreshCache(competitionKey);

  if (fromCache) {
    return fromCache;
  }

  const snapshot = getState();
  const liveMatches = competitionKey
    ? snapshot.matches.filter((match) => match.competitionKey === competitionKey)
    : snapshot.matches;
  const dateIso = currentDateIsoInApiTimezone();
  const cachedToday = getCachedDateMatches({ dateIso, competitionKey });

  let todayMatches = cachedToday.hit ? cachedToday.matches : [];

  if (!cachedToday.hit) {
    try {
      todayMatches = await fetchMatchesForDate({ dateIso, competitionKey });
    } catch {
      todayMatches = [];
    }
  }

  const upcomingMatches = todayMatches.filter((match) => {
    const status = String(match?.status ?? "").toLowerCase();
    return FIXTURE_STATUSES.has(status);
  });

  const totalGamesToday = todayMatches.length > 0 ? todayMatches.length : liveMatches.length;

  const text = await generateLiveWelcomeCommentary({
    totalGamesToday,
    liveMatches,
    upcomingMatches,
    competitionKey
  });

  setWelcomeCommentaryCache(competitionKey, text);

  return text;
};

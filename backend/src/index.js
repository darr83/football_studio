import cors from "cors";
import express from "express";
import { config } from "./config.js";
import { getDateWindowState, getState, onUpdate, startScheduler } from "./cacheStore.js";
import { fetchMatchesForDate } from "./sportsApi.js";

const app = express();

app.use(cors());
app.use(express.json());

const VALID_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const FINISHED_STATUSES = new Set([
  "finished",
  "fulltime",
  "ft",
  "after_extra_time",
  "aet",
  "penalties"
]);
const FIXTURE_STATUSES = new Set([
  "notstarted",
  "scheduled",
  "postponed",
  "cancelled",
  "canceled",
  "delayed"
]);

const currentDateIsoInApiTimezone = () => {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: config.apiTimezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });

  return formatter.format(new Date());
};

const toDateRelation = (dateIso) => {
  const todayIso = currentDateIsoInApiTimezone();

  if (dateIso < todayIso) {
    return "past";
  }

  if (dateIso > todayIso) {
    return "future";
  }

  return "today";
};

const toStatus = (match) => String(match?.status ?? "").toLowerCase();

const isResultMatch = (match) => {
  const status = toStatus(match);
  return (
    FINISHED_STATUSES.has(status) ||
    (match?.homeScore !== null && match?.awayScore !== null)
  );
};

const isFixtureMatch = (match) => {
  const status = toStatus(match);
  return (
    FIXTURE_STATUSES.has(status) ||
    (match?.homeScore === null && match?.awayScore === null)
  );
};

const filterMatchesForRelation = (matches, relation) => {
  if (relation === "past") {
    return matches.filter(isResultMatch);
  }

  if (relation === "future") {
    return matches.filter(isFixtureMatch);
  }

  return matches;
};

const todayIso = () => currentDateIsoInApiTimezone();

const toTimestamp = (value) => {
  const parsed = Date.parse(String(value ?? ""));
  return Number.isFinite(parsed) ? parsed : 0;
};

const sortMatchesByKickoff = (matches) => {
  return [...matches].sort((a, b) => toTimestamp(a?.kickoffUtc) - toTimestamp(b?.kickoffUtc));
};

const getMatchIdentity = (match) => {
  if (match?.id !== null && match?.id !== undefined) {
    return `id:${match.id}`;
  }

  return `fallback:${String(match?.homeTeam ?? "")}|${String(match?.awayTeam ?? "")}|${String(match?.kickoffUtc ?? "")}`;
};

const mergeDateAndLiveMatches = (dateMatches, liveMatches) => {
  const merged = new Map();

  for (const match of dateMatches) {
    merged.set(getMatchIdentity(match), match);
  }

  // Live payload should override stale snapshots for the same event id.
  for (const match of liveMatches) {
    merged.set(getMatchIdentity(match), match);
  }

  return sortMatchesByKickoff(Array.from(merged.values()));
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

app.get("/health", (_req, res) => {
  const snapshot = getState();
  res.json({
    ok: true,
    source: snapshot.source,
    lastUpdatedUtc: snapshot.lastUpdatedUtc,
    hasError: Boolean(snapshot.error)
  });
});

app.get("/api/scores", async (req, res) => {
  const mode = String(req.query.mode ?? "live").toLowerCase();
  const competitionKey = req.query.competitionKey
    ? String(req.query.competitionKey)
    : null;

  if (mode === "today-live") {
    const selectedDate = todayIso();
    const snapshot = getState();
    const cachedLiveMatches = competitionKey
      ? snapshot.matches.filter((match) => match.competitionKey === competitionKey)
      : snapshot.matches;
    const cachedDate = getCachedDateMatches({ dateIso: selectedDate, competitionKey });

    try {
      if (cachedDate.hit) {
        const mergedMatches = mergeDateAndLiveMatches(cachedDate.matches, cachedLiveMatches);

        return res.json({
          source: "cache-date-window-live-merged",
          mode,
          selectedDate,
          dateRelation: "today",
          lastUpdatedUtc: new Date().toISOString(),
          matches: mergedMatches,
          error: null
        });
      }

      const dateMatches = await fetchMatchesForDate({ dateIso: selectedDate, competitionKey });
      const mergedMatches = mergeDateAndLiveMatches(dateMatches, cachedLiveMatches);

      return res.json({
        source: "sports-api-today-live",
        mode,
        selectedDate,
        dateRelation: "today",
        lastUpdatedUtc: new Date().toISOString(),
        matches: mergedMatches,
        error: null
      });
    } catch (error) {
      return res.status(500).json({
        source: "error",
        mode,
        selectedDate,
        dateRelation: "today",
        lastUpdatedUtc: new Date().toISOString(),
        matches: [],
        error: error instanceof Error ? error.message : "Unknown backend error"
      });
    }
  }

  if (mode === "date") {
    const dateIso = String(req.query.date ?? "");

    if (!VALID_DATE_RE.test(dateIso)) {
      return res.status(400).json({
        error: true,
        detail: "Invalid date query. Use YYYY-MM-DD."
      });
    }

    const dateRelation = toDateRelation(dateIso);
    const cachedDate = getCachedDateMatches({ dateIso, competitionKey });

    if (cachedDate.hit) {
      return res.json({
        source: "cache-date-window",
        mode,
        selectedDate: dateIso,
        dateRelation,
        lastUpdatedUtc: new Date().toISOString(),
        matches: filterMatchesForRelation(cachedDate.matches, dateRelation),
        error: null
      });
    }

    try {
      const matches = await fetchMatchesForDate({ dateIso, competitionKey });
      const filteredMatches = filterMatchesForRelation(matches, dateRelation);

      return res.json({
        source: "sports-api-date",
        mode,
        selectedDate: dateIso,
        dateRelation,
        lastUpdatedUtc: new Date().toISOString(),
        matches: filteredMatches,
        error: null
      });
    } catch (error) {
      return res.status(500).json({
        source: "error",
        mode,
        selectedDate: dateIso,
        dateRelation,
        lastUpdatedUtc: new Date().toISOString(),
        matches: [],
        error: error instanceof Error ? error.message : "Unknown backend error"
      });
    }
  }

  const snapshot = getState();
  const matches = competitionKey
    ? snapshot.matches.filter((match) => match.competitionKey === competitionKey)
    : snapshot.matches;

  return res.json({
    ...snapshot,
    mode: "live",
    selectedDate: null,
    dateRelation: "today",
    matches
  });
});

app.get("/api/scores/stream", (req, res) => {
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.flushHeaders();

  const emit = (payload) => {
    res.write(`data: ${JSON.stringify(payload)}\n\n`);
  };

  emit(getState());

  const unsubscribe = onUpdate(emit);

  req.on("close", () => {
    unsubscribe();
    res.end();
  });
});

const start = async () => {
  await startScheduler();

  app.listen(config.port, () => {
    console.log(`Live score backend listening on http://localhost:${config.port}`);
  });
};

void start();

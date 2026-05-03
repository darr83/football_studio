import { EventEmitter } from "node:events";
import { config } from "./config.js";
import { fetchLiveMatches, fetchMatchesForDateWindow } from "./sportsApi.js";
import {
  initRedisCache,
  loadRedisCacheSnapshot,
  persistRedisCacheSnapshot
} from "./redisCache.js";

const updates = new EventEmitter();
const DATE_WINDOW_DAYS = 7;
const DATE_WINDOW_REFRESH_MS = Math.max(config.syncIntervalMs, 60_000);

const state = {
  source: "boot",
  lastUpdatedUtc: null,
  matches: [],
  error: null,
  dateWindow: {
    dateFromIso: null,
    dateToIso: null,
    lastUpdatedUtc: null,
    error: null,
    matchesByDate: {}
  }
};

let intervalHandle = null;
let dateWindowIntervalHandle = null;
let isRefreshingDateWindow = false;

const toLiveSnapshot = () => ({
  source: state.source,
  lastUpdatedUtc: state.lastUpdatedUtc,
  error: state.error,
  matches: state.matches
});

const toDateWindowSnapshot = () => ({
  ...state.dateWindow,
  matchesByDate: Object.fromEntries(
    Object.entries(state.dateWindow.matchesByDate).map(([dateIso, matches]) => [
      dateIso,
      [...matches]
    ])
  )
});

const persistStateToRedis = () => {
  void persistRedisCacheSnapshot({
    live: toLiveSnapshot(),
    dateWindow: toDateWindowSnapshot()
  });
};

const hydrateStateFromRedis = async () => {
  await initRedisCache();

  const snapshot = await loadRedisCacheSnapshot();

  const live = snapshot?.live;
  if (live && Array.isArray(live.matches)) {
    state.source = typeof live.source === "string" ? live.source : "redis-cache";
    state.lastUpdatedUtc = typeof live.lastUpdatedUtc === "string" ? live.lastUpdatedUtc : null;
    state.error = typeof live.error === "string" ? live.error : null;
    state.matches = live.matches;
  }

  const dateWindow = snapshot?.dateWindow;
  if (dateWindow && typeof dateWindow === "object") {
    const rawMatchesByDate =
      dateWindow.matchesByDate && typeof dateWindow.matchesByDate === "object"
        ? dateWindow.matchesByDate
        : {};

    const matchesByDate = Object.fromEntries(
      Object.entries(rawMatchesByDate)
        .filter(([dateIso, matches]) => typeof dateIso === "string" && Array.isArray(matches))
        .map(([dateIso, matches]) => [dateIso, matches])
    );

    state.dateWindow = {
      dateFromIso: typeof dateWindow.dateFromIso === "string" ? dateWindow.dateFromIso : null,
      dateToIso: typeof dateWindow.dateToIso === "string" ? dateWindow.dateToIso : null,
      lastUpdatedUtc:
        typeof dateWindow.lastUpdatedUtc === "string" ? dateWindow.lastUpdatedUtc : null,
      error: typeof dateWindow.error === "string" ? dateWindow.error : null,
      matchesByDate
    };
  }
};

export const getState = () => ({ ...state, matches: [...state.matches] });

export const getDateWindowState = () => ({
  ...state.dateWindow,
  matchesByDate: Object.fromEntries(
    Object.entries(state.dateWindow.matchesByDate).map(([dateIso, matches]) => [
      dateIso,
      [...matches]
    ])
  )
});

export const onUpdate = (listener) => {
  updates.on("scores-updated", listener);
  return () => updates.off("scores-updated", listener);
};

export const refreshNow = async () => {
  try {
    const matches = await fetchLiveMatches();
    state.matches = matches;
    state.lastUpdatedUtc = new Date().toISOString();
    state.error = null;
    state.source = "sports-api";
    updates.emit("scores-updated", getState());
  } catch (error) {
    state.lastUpdatedUtc = new Date().toISOString();
    state.error = error instanceof Error ? error.message : "Unknown backend error";
    state.source = "error";
    updates.emit("scores-updated", getState());
  } finally {
    persistStateToRedis();
  }
};

const currentDateIsoInApiTimezone = () => {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: config.apiTimezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });

  return formatter.format(new Date());
};

const shiftDateIso = (dateIso, dayDelta) => {
  const date = new Date(`${dateIso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + dayDelta);
  return date.toISOString().slice(0, 10);
};

const buildDateSequence = (dateFromIso, dateToIso) => {
  const dates = [];
  let cursor = dateFromIso;

  while (cursor <= dateToIso) {
    dates.push(cursor);
    cursor = shiftDateIso(cursor, 1);
  }

  return dates;
};

export const refreshDateWindowNow = async () => {
  if (isRefreshingDateWindow) {
    return;
  }

  isRefreshingDateWindow = true;

  const todayIso = currentDateIsoInApiTimezone();
  const dateFromIso = shiftDateIso(todayIso, -DATE_WINDOW_DAYS);
  const dateToIso = shiftDateIso(todayIso, DATE_WINDOW_DAYS);

  try {
    const matches = await fetchMatchesForDateWindow({
      dateFromIso,
      dateToIso
    });

    const matchesByDate = Object.fromEntries(
      buildDateSequence(dateFromIso, dateToIso).map((dateIso) => [dateIso, []])
    );

    for (const match of matches) {
      const dateIso = String(match?.kickoffUtc ?? "").slice(0, 10);

      if (matchesByDate[dateIso]) {
        matchesByDate[dateIso].push(match);
      }
    }

    state.dateWindow = {
      dateFromIso,
      dateToIso,
      lastUpdatedUtc: new Date().toISOString(),
      error: null,
      matchesByDate
    };
  } catch (error) {
    state.dateWindow = {
      ...state.dateWindow,
      dateFromIso,
      dateToIso,
      lastUpdatedUtc: new Date().toISOString(),
      error: error instanceof Error ? error.message : "Unknown backend error"
    };
  } finally {
    isRefreshingDateWindow = false;
    persistStateToRedis();
  }
};

export const startScheduler = async () => {
  if (intervalHandle || dateWindowIntervalHandle) {
    return;
  }

  await hydrateStateFromRedis();
  await refreshNow();
  void refreshDateWindowNow();

  intervalHandle = setInterval(() => {
    void refreshNow();
  }, config.syncIntervalMs);

  dateWindowIntervalHandle = setInterval(() => {
    void refreshDateWindowNow();
  }, DATE_WINDOW_REFRESH_MS);
};

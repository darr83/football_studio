import { EventEmitter } from "node:events";
import { config } from "./config.js";
import { fetchLiveMatches, fetchMatchesForDateWindow } from "./sportsApi.js";

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
  }
};

export const startScheduler = async () => {
  if (intervalHandle || dateWindowIntervalHandle) {
    return;
  }

  await refreshNow();
  void refreshDateWindowNow();

  intervalHandle = setInterval(() => {
    void refreshNow();
  }, config.syncIntervalMs);

  dateWindowIntervalHandle = setInterval(() => {
    void refreshDateWindowNow();
  }, DATE_WINDOW_REFRESH_MS);
};

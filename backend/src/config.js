import dotenv from "dotenv";

dotenv.config();

const asNumber = (value, fallback) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const asString = (value, fallback = "") => {
  if (typeof value !== "string") {
    return fallback;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : fallback;
};

export const config = {
  port: asNumber(process.env.PORT, 3000),
  apiBaseUrl: process.env.SPORTS_API_BASE_URL ?? "https://sports.bzzoiro.com/api",
  apiKey: process.env.SPORTS_API_KEY ?? "",
  apiTimezone: process.env.SPORTS_API_TIMEZONE ?? "Europe/London",
  syncIntervalMs: asNumber(process.env.SYNC_INTERVAL_MS, 20_000),
  redisUrl: asString(process.env.REDIS_URL),
  redisKeyPrefix: asString(process.env.REDIS_KEY_PREFIX, "footballstudio")
};

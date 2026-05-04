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

const asBoolean = (value, fallback) => {
  if (typeof value !== "string") {
    return fallback;
  }

  const normalized = value.trim().toLowerCase();

  if (normalized === "true" || normalized === "1" || normalized === "yes") {
    return true;
  }

  if (normalized === "false" || normalized === "0" || normalized === "no") {
    return false;
  }

  return fallback;
};

export const config = {
  port: asNumber(process.env.PORT, 3000),
  apiBaseUrl: process.env.SPORTS_API_BASE_URL ?? "https://sports.bzzoiro.com/api",
  apiKey: process.env.SPORTS_API_KEY ?? "",
  apiTimezone: process.env.SPORTS_API_TIMEZONE ?? "Europe/London",
  syncIntervalMs: asNumber(process.env.SYNC_INTERVAL_MS, 20_000),
  aiCommentaryEnabled: asBoolean(process.env.AI_COMMENTARY_ENABLED, true),
  openAiApiKey: asString(process.env.OPENAI_API_KEY),
  openAiModel: asString(process.env.OPENAI_MODEL, "gpt-4o-mini"),
  openAiBaseUrl: asString(process.env.OPENAI_BASE_URL, "https://api.openai.com/v1"),
  redisUrl: asString(process.env.REDIS_URL),
  redisKeyPrefix: asString(process.env.REDIS_KEY_PREFIX, "footballstudio")
};

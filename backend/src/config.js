import dotenv from "dotenv";

dotenv.config();

const asNumber = (value, fallback) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export const config = {
  port: asNumber(process.env.PORT, 3000),
  apiBaseUrl: process.env.SPORTS_API_BASE_URL ?? "https://sports.bzzoiro.com/api",
  apiKey: process.env.SPORTS_API_KEY ?? "",
  apiTimezone: process.env.SPORTS_API_TIMEZONE ?? "Europe/London",
  syncIntervalMs: asNumber(process.env.SYNC_INTERVAL_MS, 20_000)
};

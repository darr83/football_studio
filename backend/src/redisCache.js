import Redis from "ioredis";
import { config } from "./config.js";

const LIVE_CACHE_KEY = `${config.redisKeyPrefix}:scores:live`;
const DATE_WINDOW_CACHE_KEY = `${config.redisKeyPrefix}:scores:date-window`;

let redisClient = null;
let redisReady = false;

const parseJson = (raw) => {
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

const isEnabled = () => Boolean(config.redisUrl);

export const initRedisCache = async () => {
  if (!isEnabled()) {
    return false;
  }

  try {
    redisClient = new Redis(config.redisUrl, {
      lazyConnect: true,
      maxRetriesPerRequest: 1,
      enableOfflineQueue: false
    });

    await redisClient.connect();
    redisReady = true;
    console.log("Redis cache enabled");
    return true;
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    console.warn(`Redis cache disabled: ${message}`);

    if (redisClient) {
      redisClient.disconnect();
    }

    redisClient = null;
    redisReady = false;
    return false;
  }
};

export const hasRedisCache = () => redisReady && redisClient !== null;

export const loadRedisCacheSnapshot = async () => {
  if (!hasRedisCache()) {
    return null;
  }

  try {
    const [liveRaw, dateWindowRaw] = await redisClient.mget(LIVE_CACHE_KEY, DATE_WINDOW_CACHE_KEY);

    return {
      live: parseJson(liveRaw),
      dateWindow: parseJson(dateWindowRaw)
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    console.warn(`Redis read failed: ${message}`);
    return null;
  }
};

export const persistRedisCacheSnapshot = async ({ live, dateWindow }) => {
  if (!hasRedisCache()) {
    return;
  }

  try {
    const pipeline = redisClient.multi();

    if (live) {
      pipeline.set(LIVE_CACHE_KEY, JSON.stringify(live));
    }

    if (dateWindow) {
      pipeline.set(DATE_WINDOW_CACHE_KEY, JSON.stringify(dateWindow));
    }

    await pipeline.exec();
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    console.warn(`Redis write failed: ${message}`);
  }
};

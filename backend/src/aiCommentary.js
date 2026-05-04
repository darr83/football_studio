import axios from "axios";
import { config } from "./config.js";

const MAX_COMMENTARY_CACHE = 800;
const MAX_NEW_EVENTS_PER_AI_CALL = 24;
const commentaryCache = new Map();
const aiRuntimeState = {
  enabled: config.aiCommentaryEnabled,
  hasApiKey: Boolean(config.openAiApiKey),
  status: config.aiCommentaryEnabled
    ? (config.openAiApiKey ? "active" : "missing-key")
    : "disabled",
  error: null,
  model: config.openAiModel || null,
  lastUpdatedUtc: new Date().toISOString()
};

const setAiRuntimeState = (patch) => {
  Object.assign(aiRuntimeState, patch, { lastUpdatedUtc: new Date().toISOString() });
};

const trimCache = () => {
  while (commentaryCache.size > MAX_COMMENTARY_CACHE) {
    const firstKey = commentaryCache.keys().next().value;

    if (!firstKey) {
      return;
    }

    commentaryCache.delete(firstKey);
  }
};

const toSafeString = (value) => {
  return typeof value === "string" ? value.trim() : "";
};

const stripInitialDotPlayerNames = (value) => {
  return String(value ?? "").replace(/\b[A-Za-z]\.([A-Za-z][\p{L}'-]*)\b/gu, "$1");
};

const toScoreToken = (score) => {
  if (score === 0) {
    return "nil";
  }

  return Number.isFinite(Number(score)) ? String(score) : "unknown";
};

const formatScorelineForCommentary = ({ homeTeam, awayTeam, homeScore, awayScore }) => {
  return `${homeTeam} ${toScoreToken(homeScore)} ${awayTeam} ${toScoreToken(awayScore)}`;
};

const fallbackCommentary = (event) => {
  const homeTeam = toSafeString(event?.homeTeam) || "Home";
  const awayTeam = toSafeString(event?.awayTeam) || "Away";
  const scoreline = formatScorelineForCommentary({
    homeTeam,
    awayTeam,
    homeScore: event?.homeScore,
    awayScore: event?.awayScore
  });
  const player = toSafeString(event?.playerName);
  const playerOut = toSafeString(event?.playerOutName);

  switch (event?.eventType) {
    case "goal":
      return player
        ? `${scoreline}. Goal scored by ${player}.`
        : `${scoreline}. Goal scored.`;
    case "penalty":
      return player
        ? `${scoreline}. Penalty converted by ${player}.`
        : `${scoreline}. Penalty goal.`;
    case "yellow-card":
      return player ? `Yellow card shown to ${player}.` : "Yellow card shown.";
    case "red-card":
      return player ? `Red card shown to ${player}.` : "Red card shown.";
    case "substitution":
      return player && playerOut
        ? `Substitution: ${player} replaces ${playerOut}.`
        : player
          ? `Substitution: ${player} comes on.`
          : "Substitution made.";
    case "half-time":
      return `Half-time: ${scoreline}.`;
    case "full-time":
      return `Full-time: ${scoreline}.`;
    default:
      return toSafeString(event?.message) || `${scoreline}.`;
  }
};

const normalizeCommentary = (value, fallback) => {
  const cleaned = stripInitialDotPlayerNames(toSafeString(value)).replace(/\s+/g, " ");

  if (!cleaned) {
    return fallback;
  }

  if (cleaned.length <= 220) {
    return cleaned;
  }

  const sliced = cleaned.slice(0, 220);
  const lastSpace = sliced.lastIndexOf(" ");

  if (lastSpace > 120) {
    return sliced.slice(0, lastSpace);
  }

  return sliced;
};

const randomPick = (items) => {
  if (!Array.isArray(items) || items.length === 0) {
    return "";
  }

  return items[Math.floor(Math.random() * items.length)];
};

const formatMatchLabel = (match) => {
  const homeTeam = toSafeString(match?.homeTeam) || "Home";
  const awayTeam = toSafeString(match?.awayTeam) || "Away";
  const homeScore = match?.homeScore;
  const awayScore = match?.awayScore;

  if (homeScore === null || homeScore === undefined || awayScore === null || awayScore === undefined) {
    return `${homeTeam} versus ${awayTeam}`;
  }

  return formatScorelineForCommentary({
    homeTeam,
    awayTeam,
    homeScore,
    awayScore
  });
};

const fallbackWelcomeCommentary = ({
  totalGamesToday,
  liveMatches,
  upcomingMatches
}) => {
  const intro = randomPick([
    "Welcome to Football Studio Live.",
    "Welcome to Football Studio live coverage.",
    "You are now tuned in to Football Studio Live."
  ]);
  const todayLine = `Today there are ${totalGamesToday} games taking place.`;
  const liveLine =
    liveMatches.length > 0
      ? `Live now: ${liveMatches.slice(0, 2).map(formatMatchLabel).join(" and ")}.`
      : "There are no live matches right now.";
  const upcomingLine =
    upcomingMatches.length > 0
      ? `Later today we have ${upcomingMatches.slice(0, 2).map(formatMatchLabel).join(" and ")}.`
      : "We will bring you more fixtures as they begin.";

  return `${intro} ${todayLine} ${liveLine} ${upcomingLine}`;
};

const extractJsonPayload = (raw) => {
  const trimmed = toSafeString(raw);

  if (!trimmed) {
    return null;
  }

  const firstBrace = trimmed.indexOf("{");
  const lastBrace = trimmed.lastIndexOf("}");

  if (firstBrace >= 0 && lastBrace > firstBrace) {
    return trimmed.slice(firstBrace, lastBrace + 1);
  }

  return null;
};

const parseCommentaryMap = (content) => {
  const parsedDirect = (() => {
    try {
      return JSON.parse(content);
    } catch {
      return null;
    }
  })();

  if (parsedDirect && typeof parsedDirect === "object" && !Array.isArray(parsedDirect)) {
    return parsedDirect;
  }

  const extracted = extractJsonPayload(content);

  if (!extracted) {
    return null;
  }

  try {
    const parsed = JSON.parse(extracted);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
};

const buildPromptEvents = (events) => {
  return events.map((event) => ({
    eventKey: event.eventKey,
    eventType: event.eventType,
    teamSide: event.teamSide,
    competitionName: event.competitionName,
    homeTeam: event.homeTeam,
    awayTeam: event.awayTeam,
    homeScore: event.homeScore,
    awayScore: event.awayScore,
    minuteLabel: event.minuteLabel,
    playerName: event.playerName,
    playerOutName: event.playerOutName,
    message: event.message
  }));
};

const generateAiCommentaryMap = async (events) => {
  if (!config.aiCommentaryEnabled || !config.openAiApiKey || events.length === 0) {
    return new Map();
  }

  const payloadEvents = buildPromptEvents(events);
  const systemPrompt =
    "You are a football live commentator. Write one concise, energetic sentence per event. " +
    "Format score mentions as: HomeTeam score AwayTeam score. Use nil for zero. " +
    "Use player surnames only, not initials or full names. " +
    "Keep each line under 18 words, no hashtags, no emojis, no markdown.";
  const userPrompt =
    "Return only a JSON object mapping each eventKey to commentary text. " +
    "Do not omit keys and do not include extra keys. Events:\n" +
    JSON.stringify(payloadEvents);

  const response = await axios.post(
    `${config.openAiBaseUrl}/chat/completions`,
    {
      model: config.openAiModel,
      temperature: 0.6,
      messages: [
        {
          role: "system",
          content: systemPrompt
        },
        {
          role: "user",
          content: userPrompt
        }
      ]
    },
    {
      headers: {
        Authorization: `Bearer ${config.openAiApiKey}`,
        "Content-Type": "application/json"
      },
      timeout: 10_000
    }
  );

  const rawContent =
    response?.data?.choices?.[0]?.message?.content ??
    response?.data?.choices?.[0]?.text ??
    "";
  const parsed = parseCommentaryMap(rawContent);

  if (!parsed) {
    return new Map();
  }

  return new Map(
    Object.entries(parsed)
      .filter(([eventKey, commentary]) => typeof eventKey === "string")
      .map(([eventKey, commentary]) => [eventKey, toSafeString(String(commentary ?? ""))])
  );
};

const generateAiWelcomeCommentary = async ({
  totalGamesToday,
  liveMatches,
  upcomingMatches,
  competitionKey
}) => {
  if (!config.aiCommentaryEnabled || !config.openAiApiKey) {
    throw new Error("AI welcome commentary unavailable");
  }

  const promptPayload = {
    competitionKey,
    totalGamesToday,
    liveNow: liveMatches.slice(0, 3).map((match) => ({
      homeTeam: match.homeTeam,
      awayTeam: match.awayTeam,
      homeScore: match.homeScore,
      awayScore: match.awayScore
    })),
    laterToday: upcomingMatches.slice(0, 4).map((match) => ({
      homeTeam: match.homeTeam,
      awayTeam: match.awayTeam
    }))
  };

  const systemPrompt =
    "You are a live football commentator. Write one short, punchy welcome line for a live ticker. " +
    "It must mention: Football Studio Live, total games today, live now summary, and later today summary. " +
    "Use natural spoken English. Max 50 words. No markdown.";
  const userPrompt =
    "Generate welcome commentary from this JSON:\n" +
    JSON.stringify(promptPayload);

  const response = await axios.post(
    `${config.openAiBaseUrl}/chat/completions`,
    {
      model: config.openAiModel,
      temperature: 0.85,
      messages: [
        {
          role: "system",
          content: systemPrompt
        },
        {
          role: "user",
          content: userPrompt
        }
      ]
    },
    {
      headers: {
        Authorization: `Bearer ${config.openAiApiKey}`,
        "Content-Type": "application/json"
      },
      timeout: 10_000
    }
  );

  const content =
    response?.data?.choices?.[0]?.message?.content ??
    response?.data?.choices?.[0]?.text ??
    "";

  return normalizeCommentary(
    content,
    fallbackWelcomeCommentary({ totalGamesToday, liveMatches, upcomingMatches })
  );
};

export const withAiCommentary = async (events) => {
  if (!Array.isArray(events) || events.length === 0) {
    setAiRuntimeState({
      enabled: config.aiCommentaryEnabled,
      hasApiKey: Boolean(config.openAiApiKey),
      status: config.aiCommentaryEnabled
        ? (config.openAiApiKey ? "active" : "missing-key")
        : "disabled",
      error: null,
      model: config.openAiModel || null
    });

    return [];
  }

  const withoutCache = events.filter(
    (event) => typeof event?.eventKey === "string" && !commentaryCache.has(event.eventKey)
  );

  const aiBatch = withoutCache.slice(0, MAX_NEW_EVENTS_PER_AI_CALL);

  setAiRuntimeState({
    enabled: config.aiCommentaryEnabled,
    hasApiKey: Boolean(config.openAiApiKey),
    model: config.openAiModel || null
  });

  if (aiBatch.length > 0) {
    let aiMap = new Map();

    if (!config.aiCommentaryEnabled) {
      setAiRuntimeState({
        status: "disabled",
        error: null
      });
    } else if (!config.openAiApiKey) {
      setAiRuntimeState({
        status: "missing-key",
        error: "OPENAI_API_KEY is not configured"
      });
    } else {
      try {
        aiMap = await generateAiCommentaryMap(aiBatch);
        setAiRuntimeState({
          status: aiMap.size > 0 ? "active" : "fallback",
          error: aiMap.size > 0 ? null : "AI returned empty commentary; using fallback lines"
        });
      } catch (error) {
        setAiRuntimeState({
          status: "fallback",
          error: error instanceof Error ? error.message : "AI commentary request failed"
        });
      }
    }

    for (const event of aiBatch) {
      const fallback = fallbackCommentary(event);
      const aiText = aiMap.get(event.eventKey);
      const finalText = normalizeCommentary(aiText, fallback);
      commentaryCache.set(event.eventKey, finalText);
    }
  }

  for (const event of withoutCache.slice(MAX_NEW_EVENTS_PER_AI_CALL)) {
    const fallback = fallbackCommentary(event);
    commentaryCache.set(event.eventKey, fallback);
  }

  trimCache();

  return events.map((event) => {
    const fallback = fallbackCommentary(event);
    const commentary = commentaryCache.get(event.eventKey) ?? fallback;

    return {
      ...event,
      commentary
    };
  });
};

export const generateLiveWelcomeCommentary = async ({
  totalGamesToday,
  liveMatches,
  upcomingMatches,
  competitionKey
}) => {
  const safeTotal = Number.isFinite(Number(totalGamesToday)) ? Number(totalGamesToday) : 0;
  const safeLiveMatches = Array.isArray(liveMatches) ? liveMatches : [];
  const safeUpcomingMatches = Array.isArray(upcomingMatches) ? upcomingMatches : [];
  const fallback = fallbackWelcomeCommentary({
    totalGamesToday: safeTotal,
    liveMatches: safeLiveMatches,
    upcomingMatches: safeUpcomingMatches
  });

  if (!config.aiCommentaryEnabled || !config.openAiApiKey) {
    return fallback;
  }

  try {
    const aiLine = await generateAiWelcomeCommentary({
      totalGamesToday: safeTotal,
      liveMatches: safeLiveMatches,
      upcomingMatches: safeUpcomingMatches,
      competitionKey
    });

    return normalizeCommentary(aiLine, fallback);
  } catch {
    return fallback;
  }
};

export const getAiCommentaryStatus = () => {
  return {
    enabled: Boolean(aiRuntimeState.enabled),
    hasApiKey: Boolean(aiRuntimeState.hasApiKey),
    status: String(aiRuntimeState.status ?? "unknown"),
    error:
      typeof aiRuntimeState.error === "string" && aiRuntimeState.error.trim().length > 0
        ? aiRuntimeState.error
        : null,
    model:
      typeof aiRuntimeState.model === "string" && aiRuntimeState.model.trim().length > 0
        ? aiRuntimeState.model
        : null,
    lastUpdatedUtc: aiRuntimeState.lastUpdatedUtc
  };
};

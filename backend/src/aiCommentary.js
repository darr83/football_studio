import axios from "axios";
import { config } from "./config.js";

const MAX_COMMENTARY_CACHE = 800;
const MAX_NEW_EVENTS_PER_AI_CALL = 24;
const commentaryCache = new Map();

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

const fallbackCommentary = (event) => {
  const homeTeam = toSafeString(event?.homeTeam) || "Home";
  const awayTeam = toSafeString(event?.awayTeam) || "Away";
  const score = `${event?.homeScore ?? "-"}-${event?.awayScore ?? "-"}`;
  const player = toSafeString(event?.playerName);

  switch (event?.eventType) {
    case "goal":
      return player
        ? `${homeTeam} ${score} ${awayTeam}. Goal scored by ${player}.`
        : `${homeTeam} ${score} ${awayTeam}. Goal scored.`;
    case "penalty":
      return player
        ? `${homeTeam} ${score} ${awayTeam}. Penalty converted by ${player}.`
        : `${homeTeam} ${score} ${awayTeam}. Penalty goal.`;
    case "yellow-card":
      return player ? `Yellow card shown to ${player}.` : "Yellow card shown.";
    case "red-card":
      return player ? `Red card shown to ${player}.` : "Red card shown.";
    case "half-time":
      return `Half-time: ${homeTeam} ${score} ${awayTeam}.`;
    case "full-time":
      return `Full-time: ${homeTeam} ${score} ${awayTeam}.`;
    default:
      return toSafeString(event?.message) || `${homeTeam} ${score} ${awayTeam}.`;
  }
};

const normalizeCommentary = (value, fallback) => {
  const cleaned = toSafeString(value).replace(/\s+/g, " ");

  if (!cleaned) {
    return fallback;
  }

  return cleaned.slice(0, 220);
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
    playerName: event.playerName
  }));
};

const generateAiCommentaryMap = async (events) => {
  if (!config.aiCommentaryEnabled || !config.openAiApiKey || events.length === 0) {
    return new Map();
  }

  const payloadEvents = buildPromptEvents(events);
  const systemPrompt =
    "You are a football live commentator. Write one concise, energetic sentence per event. " +
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

export const withAiCommentary = async (events) => {
  if (!Array.isArray(events) || events.length === 0) {
    return [];
  }

  const withoutCache = events.filter(
    (event) => typeof event?.eventKey === "string" && !commentaryCache.has(event.eventKey)
  );

  const aiBatch = withoutCache.slice(0, MAX_NEW_EVENTS_PER_AI_CALL);

  if (aiBatch.length > 0) {
    const aiMap = await generateAiCommentaryMap(aiBatch).catch(() => new Map());

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

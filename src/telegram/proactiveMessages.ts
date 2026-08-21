import type { Env } from "../config/env";
import { numVar } from "../config/env";
import { TelegramClient } from "./client";
import { getAIProvider } from "../ai";
import { buildMessages } from "../ai/promptBuilder";
import { parseKiraReply } from "../ai/replyParsing";
import { PROACTIVE_MESSAGE_TRIGGER } from "../config/character";
import { loadContext, rememberExchange } from "../memory/manager";
import { mergePreferences } from "../database/users";
import { logError } from "../database/errorLog";

const LAST_SENT_KV_KEY = "kira:proactive:last-sent";

/**
 * Запускается из cron несколько раз в день: с некоторой вероятностью и не
 * чаще заданного минимального интервала Кира сама пишет создателю первой,
 * без повода — чтобы не выглядело как жёсткое расписание, а естественно.
 */
export async function runProactiveMessage(env: Env): Promise<void> {
  const ownerId = Number(env.OWNER_TELEGRAM_ID);
  if (!Number.isFinite(ownerId) || ownerId <= 0) return;

  const minGapMs = numVar(env.PROACTIVE_MESSAGE_MIN_GAP_HOURS, 6) * 60 * 60 * 1000;
  const lastSentRaw = await env.KIRA_KV.get(LAST_SENT_KV_KEY);
  const lastSentAt = lastSentRaw ? Number(lastSentRaw) : 0;
  if (Date.now() - lastSentAt < minGapMs) return;

  const probability = clampProbability(numVar(env.PROACTIVE_MESSAGE_PROBABILITY, 0.4));
  if (Math.random() > probability) return;

  const client = new TelegramClient(env);

  try {
    const { profile, history } = await loadContext(env, ownerId, ownerId);
    const provider = getAIProvider(env);
    const prompt = buildMessages(profile, history, PROACTIVE_MESSAGE_TRIGGER, null, false, true);
    const rawReply = await provider.generate(prompt);
    const { skip, visibleText, note } = parseKiraReply(rawReply);

    if (note) {
      await mergePreferences(env, ownerId, note).catch((error) => logError(env, "mergePreferences", error));
    }
    if (skip || !visibleText) return;

    await client.sendMessage(ownerId, visibleText);
    await rememberExchange(env, ownerId, ownerId, PROACTIVE_MESSAGE_TRIGGER, visibleText);
    await env.KIRA_KV.put(LAST_SENT_KV_KEY, String(Date.now()));
  } catch (error) {
    await logError(env, "runProactiveMessage", error);
  }
}

function clampProbability(value: number): number {
  return Math.min(1, Math.max(0, value));
}

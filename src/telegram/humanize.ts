import type { Env } from "../config/env";
import { numVar } from "../config/env";

/** Случайный разброс задержки перед ответом (0.5x–1.5x базовой) — без него
 * задержка выглядит как таймер у бота, а не как естественная пауза. */
export function humanDelayMs(env: Env): number {
  const base = numVar(env.RESPONSE_DELAY_MS, 400);
  return Math.round(base * (0.5 + Math.random()));
}

const MIN_SPLIT_LENGTH = 120;
const SPLIT_PROBABILITY = 0.5;

/**
 * Иногда (не всегда) разбивает длинный ответ на два сообщения подряд —
 * в обычной переписке люди редко пишут одним сплошным абзацем.
 */
export function splitIntoBubbles(text: string): string[] {
  if (text.length < MIN_SPLIT_LENGTH || Math.random() > SPLIT_PROBABILITY) {
    return [text];
  }

  const paragraphBreak = text.indexOf("\n\n");
  if (paragraphBreak > 20 && paragraphBreak < text.length - 20) {
    return [text.slice(0, paragraphBreak).trim(), text.slice(paragraphBreak + 2).trim()].filter(Boolean);
  }

  const mid = Math.floor(text.length / 2);
  const windowStart = Math.max(0, mid - 40);
  const windowEnd = Math.min(text.length, mid + 40);
  const segment = text.slice(windowStart, windowEnd);

  const sentenceEndRe = /[.!?…]\s+/g;
  let splitAt = -1;
  let match: RegExpExecArray | null;
  while ((match = sentenceEndRe.exec(segment))) {
    splitAt = windowStart + match.index + match[0].length;
  }

  if (splitAt > 0) {
    return [text.slice(0, splitAt).trim(), text.slice(splitAt).trim()].filter(Boolean);
  }

  return [text];
}

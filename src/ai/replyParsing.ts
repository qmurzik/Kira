/**
 * Скрытый протокол внутри ответа модели (никогда не показывается
 * пользователю, только парсится):
 *  - <<KIRA_SKIP>> — модель решила, что сообщение не адресовано ей
 *    (например, два пользователя просто общаются друг с другом в её теме);
 *  - <<KIRA_NOTE: ключ=значение; ключ2=значение2>> — новые факты о
 *    собеседнике, которые стоит запомнить (или <<KIRA_NOTE: none>>).
 */

export const SKIP_SENTINEL = "<<KIRA_SKIP>>";
const NOTE_RE = /<<KIRA_NOTE:\s*(.*?)>>/is;
const SKIP_RE = /<<KIRA_SKIP>>/i;

export interface ParsedReply {
  skip: boolean;
  visibleText: string;
  note: Record<string, string> | null;
}

export function parseKiraReply(raw: string): ParsedReply {
  const skip = SKIP_RE.test(raw);

  let visibleText = raw.replace(SKIP_RE, "");
  let note: Record<string, string> | null = null;

  const noteMatch = visibleText.match(NOTE_RE);
  if (noteMatch) {
    note = parseNoteBody(noteMatch[1] ?? "");
    visibleText = visibleText.replace(NOTE_RE, "");
  }

  return { skip, visibleText: visibleText.trim(), note };
}

function parseNoteBody(body: string): Record<string, string> | null {
  const trimmed = body.trim();
  if (!trimmed || /^none$/i.test(trimmed)) return null;

  const result: Record<string, string> = {};
  for (const pair of trimmed.split(";")) {
    const [key, ...rest] = pair.split("=");
    const cleanKey = key?.trim();
    const value = rest.join("=").trim();
    if (cleanKey && value) {
      result[cleanKey.slice(0, 40)] = value.slice(0, 120);
    }
  }
  return Object.keys(result).length > 0 ? result : null;
}

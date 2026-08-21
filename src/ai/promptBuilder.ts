import { buildSystemPrompt } from "../config/character";
import type { ChatMessage } from "./types";
import type { UserProfile } from "../database/users";
import type { ShortTermMessage } from "../memory/shortTerm";

export function buildMessages(
  profile: UserProfile | null,
  history: ShortTermMessage[],
  userMessage: string,
  qmodsContextLine: string | null = null,
  ambiguousAddressee = false,
  isOwner = false,
): ChatMessage[] {
  const messages: ChatMessage[] = [
    { role: "system", content: buildSystemPrompt(ambiguousAddressee, isOwner) },
  ];

  if (profile) {
    const name = profile.firstName || profile.username;
    const prefs = safeParsePreferences(profile.preferencesJson);
    const notes: string[] = [];
    if (name) notes.push(`Имя пользователя: ${name}.`);
    if (Object.keys(prefs).length > 0) {
      notes.push(`Известные предпочтения/заметки о пользователе: ${JSON.stringify(prefs)}.`);
    }
    if (notes.length > 0) {
      messages.push({ role: "system", content: notes.join(" ") });
    }
  }

  if (qmodsContextLine) {
    messages.push({ role: "system", content: qmodsContextLine });
  }

  for (const item of history) {
    messages.push({ role: item.role, content: item.content });
  }

  messages.push({ role: "user", content: userMessage });
  return messages;
}

function safeParsePreferences(json: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(json);
    return typeof parsed === "object" && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

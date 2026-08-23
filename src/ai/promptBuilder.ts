import { buildSystemPrompt } from "../config/character";
import type { ChatMessage, ChatRole } from "./types";
import { safeParse, type UserProfile } from "../database/users";

interface HistoryEntry {
  role: ChatRole;
  content: string;
  speakerName?: string;
}

export function buildMessages(
  profile: UserProfile | null,
  history: HistoryEntry[],
  userMessage: string,
  qmodsContextLine: string | null = null,
  ambiguousAddressee = false,
  isOwner = false,
  participantsNote: string | null = null,
): ChatMessage[] {
  const messages: ChatMessage[] = [
    { role: "system", content: buildSystemPrompt(ambiguousAddressee, isOwner) },
  ];

  if (profile) {
    const name = profile.firstName || profile.username;
    const prefs = safeParse(profile.preferencesJson);
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

  if (participantsNote) {
    messages.push({ role: "system", content: participantsNote });
  }

  for (const item of history) {
    // В групповой истории перед репликами участников стоит их имя
    // ("Аня: текст") — так модель различает, кто что сказал в общем чате.
    const content = item.role === "user" && item.speakerName ? `${item.speakerName}: ${item.content}` : item.content;
    messages.push({ role: item.role, content });
  }

  messages.push({ role: "user", content: userMessage });
  return messages;
}

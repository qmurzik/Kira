import type { Env } from "../config/env";
import type { TgUpdate } from "./types";
import { getBotId, getBotUsername } from "./bot";
import { decideShouldRespond } from "./middleware/topicFilter";
import { handleForumTopicCreated } from "./handlers/forumTopic";
import { handleAIMessage } from "./handlers/message";
import { handleStart } from "./commands/start";
import { handleHelp } from "./commands/help";
import { handleProfile } from "./commands/profile";
import { handleSettings } from "./commands/settings";
import { handleClearMemory } from "./commands/clearMemory";
import { handleLink } from "./commands/link";
import { handleUnlink } from "./commands/unlink";
import { logError } from "../database/errorLog";

type CommandHandler = (env: Env, message: NonNullable<TgUpdate["message"]>, args: string[]) => Promise<void>;

const COMMANDS: Record<string, CommandHandler> = {
  "/start": (env, message) => handleStart(env, message),
  "/help": (env, message) => handleHelp(env, message),
  "/profile": (env, message) => handleProfile(env, message),
  "/settings": (env, message, args) => handleSettings(env, message, args),
  "/clear_memory": (env, message) => handleClearMemory(env, message),
  "/link": (env, message, args) => handleLink(env, message, args),
  "/unlink": (env, message) => handleUnlink(env, message),
};

function parseCommand(text: string): { command: string; args: string[] } | null {
  const match = text.match(/^\/([a-zA-Z0-9_]+)(@\S+)?\s*(.*)$/s);
  if (!match) return null;
  const [, command, , rest] = match;
  return { command: `/${(command ?? "").toLowerCase()}`, args: rest ? rest.trim().split(/\s+/) : [] };
}

export async function routeUpdate(env: Env, update: TgUpdate): Promise<void> {
  const message = update.message;
  if (!message) return;

  try {
    if (message.forum_topic_created) {
      await handleForumTopicCreated(env, message);
      return;
    }

    if (!message.text) return;

    const parsed = parseCommand(message.text);
    const handler = parsed ? COMMANDS[parsed.command] : undefined;
    if (parsed && handler) {
      await handler(env, message, parsed.args);
      return;
    }

    const botId = getBotId(env);
    if (message.from?.id === botId) return; // никогда не отвечаем сами себе

    const botUsername = await getBotUsername(env);
    const decision = await decideShouldRespond(env, message, botId, botUsername);
    if (!decision.shouldRespond) return;

    await handleAIMessage(env, message);
  } catch (error) {
    await logError(env, "routeUpdate", error);
  }
}

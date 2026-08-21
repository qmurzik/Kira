import type { Env } from "../config/env";
import type { AIProvider } from "./types";
import { createCloudflareProvider } from "./providers/cloudflareWorkersAI";
import { createGroqProvider } from "./providers/groq";
import { createOpenRouterProvider } from "./providers/openrouter";

/**
 * Фабрика провайдера ИИ. Провайдер выбирается через AI_PROVIDER в wrangler.toml,
 * без изменений в коде. По умолчанию — бесплатный Cloudflare Workers AI.
 */
export function getAIProvider(env: Env): AIProvider {
  switch (env.AI_PROVIDER) {
    case "groq":
      return createGroqProvider(env);
    case "openrouter":
      return createOpenRouterProvider(env);
    case "cloudflare":
    default:
      return createCloudflareProvider(env);
  }
}

export type { AIProvider, ChatMessage, ChatRole } from "./types";

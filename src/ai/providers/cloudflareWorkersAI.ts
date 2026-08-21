import type { Env } from "../../config/env";
import type { AIProvider, ChatMessage } from "../types";

/**
 * Провайдер по умолчанию: Cloudflare Workers AI.
 * Полностью бесплатен в рамках дневного лимита нейронов, не требует
 * внешнего API-ключа — используется биндинг env.AI.
 */
interface WorkersAIResult {
  response?: string;
  choices?: Array<{ message?: { content?: string } }>;
}

export function createCloudflareProvider(env: Env): AIProvider {
  return {
    name: "cloudflare",
    async generate(messages: ChatMessage[]): Promise<string> {
      const model = env.AI_MODEL || "@cf/zai-org/glm-4.7-flash";
      const raw = await env.AI.run(model as keyof AiModels, {
        messages,
        max_tokens: 800,
        max_completion_tokens: 800,
        temperature: 0.7,
        // У reasoning-моделей часть токенов уходит на скрытые "раздумья" —
        // держим их короткими, чтобы Кира отвечала быстро и не срезала себе
        // сам ответ лимитом токенов.
        reasoning_effort: "low",
      } as never);

      const result = raw as WorkersAIResult;
      const content = result.response || result.choices?.[0]?.message?.content;
      if (!content) {
        throw new Error(
          `Cloudflare Workers AI: пустой ответ модели. raw=${JSON.stringify(raw).slice(0, 1500)}`,
        );
      }
      return content.trim();
    },
  };
}

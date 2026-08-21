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
      const model = env.AI_MODEL || "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
      const raw = await env.AI.run(model as keyof AiModels, {
        messages,
        max_tokens: 500,
        max_completion_tokens: 500,
        temperature: 0.7,
        // Если AI_MODEL переключат на reasoning-модель — часть токенов уйдёт
        // на скрытые "раздумья"; держим их короткими, чтобы ответ не срезался
        // лимитом токенов. Для обычных моделей поле просто игнорируется.
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

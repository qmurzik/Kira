import type { Env } from "../../config/env";
import type { AIProvider, ChatMessage } from "../types";

/**
 * Провайдер по умолчанию: Cloudflare Workers AI.
 * Полностью бесплатен в рамках дневного лимита нейронов, не требует
 * внешнего API-ключа — используется биндинг env.AI.
 */
export function createCloudflareProvider(env: Env): AIProvider {
  return {
    name: "cloudflare",
    async generate(messages: ChatMessage[]): Promise<string> {
      const model = env.AI_MODEL || "@cf/meta/llama-3.1-8b-instruct";
      const result = await env.AI.run(model as keyof AiModels, {
        messages,
        max_tokens: 512,
        temperature: 0.7,
      } as never);

      const response = (result as { response?: string })?.response;
      if (!response) {
        throw new Error("Cloudflare Workers AI: пустой ответ модели");
      }
      return response.trim();
    },
  };
}

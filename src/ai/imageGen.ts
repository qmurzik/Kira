import type { Env } from "../config/env";
import { buildKiraImagePrompt } from "./imagePrompt";

// FLUX.1 [schnell] — современная модель Black Forest Labs, Cloudflare-hosted
// (не помечена "Partner", в отличие от FLUX.2/Leonardo — то есть в рамках
// того же бесплатного дневного лимита нейронов Workers AI, которым уже
// пользуется текстовая модель Киры, без нового аккаунта/платного ключа).
// Заметно детальнее классического SDXL. SDXL-Lightning жертвует качеством
// ради скорости (дистиллирована на 4 фиксированных шага) — для стилизованной
// иллюстрации выходит плоско; SDXL base — честный fallback ($0.00/шаг).
const DEFAULT_IMAGE_MODEL = "@cf/black-forest-labs/flux-1-schnell";
const FLUX_MODEL_ID = "@cf/black-forest-labs/flux-1-schnell";

/**
 * Генерирует изображение Киры через Workers AI (бесплатная модель по
 * умолчанию, без внешних ключей). Модель настраивается через KIRA_IMAGE_MODEL,
 * аналогично AI_MODEL для текста. Возвращает сырые байты изображения.
 *
 * ВАЖНО: без LoRA/референс-изображения консистентность внешности между
 * генерациями не гарантирована — только общий стиль по фиксированному промпту.
 * Ни одна бесплатная модель Workers AI не даёт уровня детализации, как у
 * генераторов вроде DALL·E/GPT-Image — на бесплатном каталоге Cloudflare
 * нет анимной LoRA/чекпоинта.
 */
export async function generateKiraPhoto(env: Env): Promise<ArrayBuffer> {
  const model = env.KIRA_IMAGE_MODEL || DEFAULT_IMAGE_MODEL;
  const { prompt, negativePrompt, fluxPrompt } = buildKiraImagePrompt();

  const isFlux = model === FLUX_MODEL_ID;
  const raw = isFlux
    ? await env.AI.run(model as keyof AiModels, {
        prompt: fluxPrompt,
        steps: 8,
        seed: Math.floor(Math.random() * 1_000_000),
      } as never)
    : await env.AI.run(model as keyof AiModels, {
        prompt,
        negative_prompt: negativePrompt,
        height: 1024,
        width: 1024,
        num_steps: 20,
        guidance: 9,
      } as never);

  return await toArrayBuffer(raw);
}

async function toArrayBuffer(raw: unknown): Promise<ArrayBuffer> {
  if (raw instanceof ArrayBuffer) return raw;

  if (raw instanceof ReadableStream) {
    return await new Response(raw).arrayBuffer();
  }

  if (raw instanceof Uint8Array) {
    return raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength) as ArrayBuffer;
  }

  // FLUX и некоторые другие модели отдают { image: "<base64>" } вместо потока байт.
  const base64 = (raw as { image?: string } | null)?.image;
  if (base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  throw new Error(
    `Workers AI (image): неожиданный формат ответа: ${JSON.stringify(raw)?.slice(0, 300)}`,
  );
}

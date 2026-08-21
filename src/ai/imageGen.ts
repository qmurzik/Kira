import type { Env } from "../config/env";
import { buildKiraImagePrompt } from "./imagePrompt";

const DEFAULT_IMAGE_MODEL = "@cf/bytedance/stable-diffusion-xl-lightning";

/**
 * Генерирует изображение Киры через Workers AI (бесплатная модель по
 * умолчанию, без внешних ключей). Модель настраивается через KIRA_IMAGE_MODEL,
 * аналогично AI_MODEL для текста. Возвращает сырые байты PNG.
 *
 * ВАЖНО: без LoRA/референс-изображения консистентность внешности между
 * генерациями не гарантирована — только общий стиль по фиксированному промпту.
 */
export async function generateKiraPhoto(env: Env): Promise<ArrayBuffer> {
  const model = env.KIRA_IMAGE_MODEL || DEFAULT_IMAGE_MODEL;
  const { prompt, negativePrompt } = buildKiraImagePrompt();

  const raw = await env.AI.run(model as keyof AiModels, {
    prompt,
    negative_prompt: negativePrompt,
    height: 1024,
    width: 1024,
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

  // Некоторые модели отдают { image: "<base64>" } вместо потока байт.
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

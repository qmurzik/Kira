import { KIRA_APPEARANCE } from "../config/character";

const BASE_PROMPT = [
  "anime style portrait of a young woman character named Kira",
  KIRA_APPEARANCE.hair,
  KIRA_APPEARANCE.eyes,
  KIRA_APPEARANCE.accessories,
  KIRA_APPEARANCE.outfit,
  "cyber goth anime aesthetic, purple accent lighting, detailed digital illustration, friendly expression",
].join(", ");

const NEGATIVE_PROMPT =
  "extra fingers, deformed hands, extra limbs, blurry, lowres, watermark, text, signature, bad anatomy, disfigured";

// Лёгкая вариативность позы/сцены между генерациями — консистентность
// персонажа при этом не гарантирована (нет LoRA/референс-изображения),
// но общий стиль и внешность заданы фиксированным промптом выше.
const SCENE_VARIANTS = [
  "close-up selfie, soft smile, looking at camera",
  "sitting at a neon-lit desk with a laptop, cozy cyberpunk room",
  "leaning against a wall with city lights bokeh in the background",
  "half-body shot, slight shy smile, holding a cup of coffee",
  "casual pose, soft studio lighting, gentle expression",
  "sitting by a window at night, purple neon reflections",
];

export function buildKiraImagePrompt(): { prompt: string; negativePrompt: string } {
  const variant = SCENE_VARIANTS[Math.floor(Math.random() * SCENE_VARIANTS.length)];
  return {
    prompt: `${BASE_PROMPT}, ${variant}`,
    negativePrompt: NEGATIVE_PROMPT,
  };
}

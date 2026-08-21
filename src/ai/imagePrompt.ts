/**
 * Фиксированное описание внешности Киры для генерации изображений —
 * держим формулировку максимально стабильной между вызовами, чтобы модель
 * (обычный text-to-image, без LoRA/референс-изображения) давала максимально
 * похожий результат. Полная пиксельная консистентность лица недостижима
 * без файнтюнинга/img2img — честно предупреждаем об этом в README.
 */
const CHARACTER_PROMPT = [
  "1girl, kira, original character, official mascot of tech brand QMODS",
  "anime style, young adult woman, short bob haircut with layered fringe",
  "dark black hair with subtle purple gradient tips",
  "glowing bright purple eyes",
  "pale fair skin, delicate gentle facial features, friendly gentle smile",
  "small silver cross-shaped hairpins in hair, stylish earrings",
  "black choker with small purple gem pendant",
  "oversized black techwear jacket with neon purple accents and glowing tech details",
  "black crop top with QMODS logo",
  "dark accessories, cyberpunk gothic tech aesthetic, premium gaming brand character design",
].join(", ");

const QUALITY_PROMPT =
  "ultra detailed anime character illustration, high quality, detailed hair strands, detailed eyes, realistic lighting, beautiful shadows, professional character design, clean composition";

const NEGATIVE_PROMPT = [
  "different character, different hairstyle, different eye color, realistic human face",
  "bad anatomy, extra fingers, distorted hands, extra limbs, deformed hands",
  "low quality, blurry, lowres, ugly face, wrong proportions, old appearance",
  "random clothes, different style, duplicate character, watermark, text, signature",
].join(", ");

// Вариации позы/сцены между генерациями — по мотивам образов из брифа
// (приветствие, помощь с интерфейсом, задумчивость, радость, работа,
// прогулка по городу). Внешность персонажа остаётся неизменной.
const SCENE_VARIANTS = [
  "friendly pose, waving hello with one hand, warm smile, purple neon cyberpunk background with holographic UI elements",
  "holding a holographic tablet showing a QMODS interface, helpful expression, soft glowing purple light",
  "thoughtful pose, finger near chin, looking slightly upward, soft ambient lighting",
  "joyful smile after completing a task, cheerful expression, purple neon studio background",
  "sitting calmly with a laptop, working, cozy futuristic room, soft lighting",
  "casual walk through the city at dusk, relaxed pose, neon purple city lights in the background",
];

export function buildKiraImagePrompt(): { prompt: string; negativePrompt: string } {
  const variant = SCENE_VARIANTS[Math.floor(Math.random() * SCENE_VARIANTS.length)];
  return {
    prompt: `${CHARACTER_PROMPT}, ${variant}, ${QUALITY_PROMPT}`,
    negativePrompt: NEGATIVE_PROMPT,
  };
}

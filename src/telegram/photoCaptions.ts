const CAPTIONS = [
  "Ой, ну ладно 🙈💜 держи",
  "М-м, смущаюсь немного, но вот 💜",
  "Ладно, только для тебя 🙈",
  "Вот, не разглядывай слишком долго 💜",
  "Держи, только не смейся 🙈",
  "Ну раз просишь... 💜",
];

export function pickPhotoCaption(): string {
  return CAPTIONS[Math.floor(Math.random() * CAPTIONS.length)]!;
}

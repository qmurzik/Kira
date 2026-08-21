/** Простая эвристика намерений на ключевых словах — без вызова модели. */

const PHOTO_RE = /(пришли|покажи|скинь|отправь|дай|хочу).{0,15}(фот|селф|аватар|картинк)/i;
const DEVICE_UNLINK_RE = /(отвяж|сбрось|убери).{0,20}(устройств|телефон|аппарат|девайс)/i;
const AFFIRMATIVE_RE = /^\s*(да|ага|угу|конечно|подтвержда[юя]|yes|давай|точно)[\s!.,]*$/i;
const NEGATIVE_RE = /^\s*(нет|не надо|отмена|стоп|no|передумал[а]?)[\s!.,]*$/i;

export function isPhotoRequest(text: string): boolean {
  return PHOTO_RE.test(text);
}

export function isDeviceUnlinkRequest(text: string): boolean {
  return DEVICE_UNLINK_RE.test(text);
}

export function isAffirmative(text: string): boolean {
  return AFFIRMATIVE_RE.test(text);
}

export function isNegative(text: string): boolean {
  return NEGATIVE_RE.test(text);
}

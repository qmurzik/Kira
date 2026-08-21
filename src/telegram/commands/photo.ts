import type { Env } from "../../config/env";
import type { TgMessage } from "../types";
import { handlePhotoRequest } from "../handlers/photo";

export async function handlePhotoCommand(env: Env, message: TgMessage): Promise<void> {
  await handlePhotoRequest(env, message);
}

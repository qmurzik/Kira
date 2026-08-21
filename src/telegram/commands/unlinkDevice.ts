import type { Env } from "../../config/env";
import type { TgMessage } from "../types";
import { requestDeviceUnlink } from "../handlers/deviceAction";

export async function handleUnlinkDeviceCommand(env: Env, message: TgMessage): Promise<void> {
  await requestDeviceUnlink(env, message);
}

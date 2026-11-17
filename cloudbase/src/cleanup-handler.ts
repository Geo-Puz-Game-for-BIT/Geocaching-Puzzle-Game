import { cleanupPendingPhotoDeletions } from "../functions/api/handlers/photos.ts";

export async function main() {
  return cleanupPendingPhotoDeletions();
}

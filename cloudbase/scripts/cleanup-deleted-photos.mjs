import { cleanupPendingPhotoDeletions } from "../functions/api/handlers/photos.ts";

const result = await cleanupPendingPhotoDeletions();
console.log(JSON.stringify(result));
process.exitCode = result.failed > 0 ? 1 : 0;

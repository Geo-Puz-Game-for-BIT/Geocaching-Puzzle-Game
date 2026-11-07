import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const vitest = fileURLToPath(new URL("../node_modules/vitest/vitest.mjs", import.meta.url));
const result = spawnSync(
  process.execPath,
  [vitest, "run", "tests/database.integration.test.ts", "--reporter=verbose"],
  {
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    env: {
      ...process.env,
      RUN_DATABASE_INTEGRATION: "1",
    },
    stdio: "inherit",
  },
);

process.exitCode = result.status ?? 1;

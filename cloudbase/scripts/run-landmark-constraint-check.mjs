import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const envId = process.env.CLOUDBASE_ENV_ID?.trim();
if (!envId) {
  throw new Error("CLOUDBASE_ENV_ID is required");
}

const sql = readFileSync(
  new URL("../tests/sql/verify_active_landmark_catalog_fields.sql", import.meta.url),
  "utf8",
);
const cli = fileURLToPath(
  new URL("../../node_modules/@cloudbase/cli/dist/standalone/cli.js", import.meta.url),
);

const result = spawnSync(
  process.execPath,
  [cli, "db", "execute", "-e", envId, "--sql", sql, "--json"],
  {
    cwd: root,
    encoding: "utf8",
    env: process.env,
  },
);

const output = `${result.stdout ?? ""}\n${result.stderr ?? ""}`;
const match = output.match(/VERIFY_PASS\|[^"\\}\r\n]+/);
if (!match) {
  process.stdout.write(output);
  process.exitCode = 1;
} else {
  console.log(match[0]);
}

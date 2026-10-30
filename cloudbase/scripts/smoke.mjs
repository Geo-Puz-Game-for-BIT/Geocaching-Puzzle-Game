import { fork, spawn } from 'node:child_process';
import { once } from 'node:events';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const cliPackage = require.resolve('@usebruno/cli/package.json');
const cli = resolve(dirname(cliPackage), JSON.parse(readFileSync(cliPackage, 'utf8')).bin.bru);
const server = fork(fileURLToPath(new URL('../src/server.ts', import.meta.url)), [], {
  env: { ...process.env, PORT: '0' },
  stdio: ['ignore', 'inherit', 'inherit', 'ipc'],
});
let bruno;
try {
  const baseUrl = await new Promise((resolveReady, reject) => {
    const timer = setTimeout(() => reject(new Error('Local API did not start within 15s')), 15000);
    server.once('message', (message) => {
      if (message.type === 'ready') { clearTimeout(timer); resolveReady(message.baseUrl); }
    });
    server.once('error', (error) => { clearTimeout(timer); reject(error); });
    server.once('exit', (code) => { clearTimeout(timer); reject(new Error(`Local API exited: ${code}`)); });
  });
  bruno = spawn(process.execPath, [cli, 'run', 'health', '--env', 'local', '--env-var', `API_BASE_URL=${baseUrl}`, '--bail'], {
    cwd: fileURLToPath(new URL('../../api-tests/', import.meta.url)),
    stdio: 'inherit',
  });
  const [code] = await once(bruno, 'exit');
  process.exitCode = code ?? 1;
} finally {
  if (bruno && bruno.exitCode === null) bruno.kill();
  if (server.exitCode === null) {
    const exited = once(server, 'exit');
    server.kill();
    await exited;
  }
}

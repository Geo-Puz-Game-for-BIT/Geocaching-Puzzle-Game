import { createApp } from '../functions/api/app.ts';

const port = Number(process.env.PORT ?? 3000);
if (!Number.isInteger(port) || port < 0 || port > 65535) {
  throw new Error('PORT must be an integer between 0 and 65535');
}

const server = createApp();
server.listen(port, '127.0.0.1', () => {
  const address = server.address();
  if (address && typeof address !== 'string') {
    const baseUrl = `http://127.0.0.1:${address.port}`;
    console.log(`Local API: ${baseUrl}/health`);
    if (process.send) process.send({ type: 'ready', baseUrl });
  }
});

process.on('SIGTERM', () => server.close());
process.on('SIGINT', () => server.close());

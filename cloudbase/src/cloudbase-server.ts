import { createApp } from '../functions/api/app.ts';

const port = Number(process.env.API_PORT ?? process.env.PORT ?? 9000);
const host = process.env.API_HOST ?? '0.0.0.0';

const server = createApp();
server.listen(port, host, () => {
  console.log(`GeoTreasure API listening on http://${host}:${String(port)}`);
});

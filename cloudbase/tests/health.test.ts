import type { AddressInfo } from "node:net";

import { afterEach, describe, expect, it } from "vitest";

import { createApp } from "../functions/api/app.ts";
import { healthHandler } from "../functions/api/handlers/health.ts";

describe("health handler", () => {
  it("givenFixedTime_whenCreateHealth_thenReturnsContractShape", () => {
    const now = new Date("2026-10-19T12:00:00.000Z");
    const response = healthHandler({
      now,
      environment: "test",
      version: "0.1.0",
      requestId: "123e4567-e89b-42d3-a456-426614174000",
    });

    expect(response).toEqual({
      requestId: "123e4567-e89b-42d3-a456-426614174000",
      data: {
        status: "ok",
        service: "geotreasure-api",
        version: "0.1.0",
        environment: "test",
        timestamp: "2026-10-19T12:00:00.000Z",
      },
    });
  });

  it("givenHealthRoute_whenRequest_thenReturns200WithRequestId", async () => {
    const server = createApp();
    await new Promise<void>((resolve) => {
      server.listen(0, "127.0.0.1", resolve);
    });

    const address = server.address() as AddressInfo;
    const response = await fetch(`http://127.0.0.1:${String(address.port)}/api/v1/health`);
    const body = (await response.json()) as {
      requestId: string;
      data: { status: string; timestamp: string };
    };

    expect(response.status).toBe(200);
    expect(response.headers.get("x-request-id")).toBe(body.requestId);
    expect(body.requestId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    expect(body.data.status).toBe("ok");
    expect(new Date(body.data.timestamp).toISOString()).toBe(body.data.timestamp);

    await new Promise<void>((resolve, reject) => {
      server.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        resolve();
      });
    });
  });
});

afterEach(() => {
  delete process.env.APP_ENV;
  delete process.env.SERVICE_VERSION;
});

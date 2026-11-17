import type { AddressInfo } from "node:net";
import { request as httpRequest } from "node:http";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createApp } from "../functions/api/app.ts";

interface JsonResponse {
  status: number;
  body: Record<string, unknown>;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function call(
  baseUrl: string,
  path: string,
  method = "GET",
  body?: string,
): Promise<JsonResponse> {
  return new Promise((resolve, reject) => {
    const request = httpRequest(
      `${baseUrl}${path}`,
      {
        method,
        headers: {
          Authorization: "Bearer test-token",
          "X-Request-Id": "123e4567-e89b-42d3-a456-426614174000",
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
      },
      (response) => {
        const chunks: Buffer[] = [];
        response.on("data", (chunk: Buffer) => chunks.push(chunk));
        response.on("end", () => {
          resolve({
            status: response.statusCode ?? 0,
            body: JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<
              string,
              unknown
            >,
          });
        });
      },
    );
    request.on("error", reject);
    request.end(body);
  });
}

beforeEach(() => {
  process.env.DEMO_AUTH_TOKEN = "test-token";
  process.env.DEMO_USER_ID = "usr_demo01";
  process.env.STORAGE_API_BASE = "https://test.example";
  process.env.CLOUDBASE_API_KEY = "test-key";
  process.env.STORAGE_BUCKET = "checkin-photos";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("recovery and deletion routes", () => {
  it("returns 202 when GET /checkins finds a processing attempt", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = String(input);
        if (url.includes("/rpc/reconcile_checkin_attempt")) {
          return jsonResponse({ status: "PROCESSING" });
        }
        if (url.includes("/rdb/rest/checkin_attempts")) {
          return jsonResponse([
            {
              id: "att_test",
              user_id: "usr_demo01",
              request_hash: "a".repeat(64),
              status: "PROCESSING",
              check_in_id: null,
              failure_code: null,
            },
          ]);
        }
        return jsonResponse([]);
      }),
    );
    const server = createApp();
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address() as AddressInfo;
    try {
      const response = await call(
        `http://127.0.0.1:${String(address.port)}`,
        "/api/v1/checkins?attemptId=att_test",
      );
      expect(response.status).toBe(202);
      expect(response.body).toMatchObject({
        data: { attemptId: "att_test", status: "PROCESSING" },
      });
    } finally {
      await new Promise<void>((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      );
    }
  });

  it("returns CHECKIN_NOT_FOUND when no attempt or legacy check-in exists", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse({ status: "NOT_FOUND" })),
    );
    const server = createApp();
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address() as AddressInfo;
    try {
      const response = await call(
        `http://127.0.0.1:${String(address.port)}`,
        "/api/v1/checkins?attemptId=att_missing",
      );
      expect(response.status).toBe(404);
      expect(response.body).toMatchObject({
        error: { code: "CHECKIN_NOT_FOUND", retryable: false },
      });
    } finally {
      await new Promise<void>((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      );
    }
  });

  it("returns 409 when deleting a photo during a processing attempt", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = String(input);
        if (url.includes("/rdb/rest/photos")) {
          return jsonResponse([
            {
              id: "pho_test",
              user_id: "usr_demo01",
              object_key: "checkins/usr_demo01/pho_test.jpg",
              attempt_id: "att_test",
              deleted_at: null,
            },
          ]);
        }
        if (url.includes("/rdb/rest/checkin_attempts")) {
          return jsonResponse([{ id: "att_test", status: "PROCESSING" }]);
        }
        return jsonResponse([]);
      }),
    );
    const server = createApp();
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address() as AddressInfo;
    try {
      const response = await call(
        `http://127.0.0.1:${String(address.port)}`,
        "/api/v1/checkin-photos/pho_test",
        "DELETE",
      );
      expect(response.status).toBe(409);
      expect(response.body).toMatchObject({
        error: { code: "CHECKIN_RESULT_UNKNOWN", retryable: true },
      });
    } finally {
      await new Promise<void>((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      );
    }
  });

  it("requires X-Attempt-Id when creating a challenge", async () => {
    const server = createApp();
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address() as AddressInfo;
    try {
      const response = await call(
        `http://127.0.0.1:${String(address.port)}`,
        "/api/v1/checkin-challenges",
        "POST",
        JSON.stringify({ landmarkId: "lmk_library" }),
      );
      expect(response.status).toBe(400);
      expect(response.body).toMatchObject({
        error: { code: "INVALID_REQUEST" },
      });
    } finally {
      await new Promise<void>((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      );
    }
  });
});

describe("player landmark list", () => {
  it("omits incomplete active landmarks instead of failing the whole response", async () => {
    const validTask = {
      id: "task_north_lake",
      landmark_id: "lmk_north_lake",
      type: "photo",
      instructions: "Take a photo.",
      puzzle_id: "puzzle_liangxiang_campus",
      fragment_id: "fragment_north_lake",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = String(input);
        if (url.includes("/rdb/rest/landmarks")) {
          return jsonResponse([
            {
              id: "lmk_north_lake",
              name: "North Lake",
              latitude: 39.7366,
              longitude: 116.1666,
              coordinate_system: "GCJ02",
              check_in_radius_meters: 55,
              campus_zone: "NORTH",
              piece_index: 0,
              fragment_id: "fragment_north_lake",
              coordinate_verified: false,
              check_in_area: { type: "circle", radiusMeters: 55 },
              difficulty: 1,
              terrain: 1,
              hint: null,
              status: "active",
            },
            {
              id: "lmk_itest_null_catalog",
              name: "Integration Landmark",
              latitude: 39.7366,
              longitude: 116.1666,
              coordinate_system: "GCJ02",
              check_in_radius_meters: 55,
              campus_zone: null,
              piece_index: null,
              fragment_id: "fragment_itest_null_catalog",
              coordinate_verified: false,
              check_in_area: { type: "circle", radiusMeters: 55 },
              difficulty: 1,
              terrain: 1,
              hint: null,
              status: "active",
            },
            {
              id: "lmk_itest_missing_task",
              name: "Integration Landmark Without Task",
              latitude: 39.7366,
              longitude: 116.1666,
              coordinate_system: "GCJ02",
              check_in_radius_meters: 55,
              campus_zone: "NORTH",
              piece_index: 1,
              fragment_id: "fragment_itest_missing_task",
              coordinate_verified: false,
              check_in_area: { type: "circle", radiusMeters: 55 },
              difficulty: 1,
              terrain: 1,
              hint: null,
              status: "active",
            },
          ]);
        }
        if (url.includes("/rdb/rest/tasks")) {
          return jsonResponse([validTask]);
        }
        return jsonResponse([]);
      }),
    );

    const server = createApp();
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address() as AddressInfo;
    try {
      const response = await call(
        `http://127.0.0.1:${String(address.port)}`,
        "/api/v1/landmarks",
      );
      expect(response.status).toBe(200);
      expect(response.body.requestId).toBe("123e4567-e89b-42d3-a456-426614174000");
      expect(response.body.data).toEqual([
        expect.objectContaining({
          id: "lmk_north_lake",
          campusZone: "NORTH",
          pieceIndex: 0,
          task: expect.objectContaining({ id: "task_north_lake" }),
        }),
      ]);
    } finally {
      await new Promise<void>((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      );
    }
  });
});

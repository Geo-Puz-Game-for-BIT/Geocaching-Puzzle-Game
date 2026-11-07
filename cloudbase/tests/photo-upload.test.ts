import { Readable } from "node:stream";
import type { IncomingMessage } from "node:http";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  cleanupPendingPhotoDeletions,
  deleteCheckInPhoto,
  storeCheckInPhoto,
} from "../functions/api/handlers/photos.ts";
import { deletePhotoObjectStrict } from "../functions/api/storage.ts";
import { MAX_PHOTO_BYTES } from "../shared/photo.ts";

function requestWithBody(
  body: Buffer,
  contentType: string,
  headers: Record<string, string> = {},
): IncomingMessage {
  return Object.assign(Readable.from([body]), {
    headers: { "content-type": contentType, ...headers },
  }) as unknown as IncomingMessage;
}

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  process.env.STORAGE_API_BASE = "https://test.example";
  process.env.CLOUDBASE_API_KEY = "test-key";
  process.env.STORAGE_BUCKET = "checkin-photos";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("storeCheckInPhoto", () => {
  it("givenJpeg_whenStore_thenUsesUserDirectoryAndUtcTimestamp", async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = String(input);
        calls.push(url);
        if (url.includes("/rdb/rest/challenges")) {
          return response([
            {
              id: "chl_test",
              user_id: "usr_demo01",
              used_at: null,
              expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
              attempt_id: "att_test",
            },
          ]);
        }
        if (url.includes("/v1/storages/object/")) {
          return response({}, 200);
        }
        return response(
          [
            {
              id: "pho_test",
              uploaded_at: "2026-10-20T08:17:21.000Z",
            },
          ],
          201,
        );
      }),
    );

    const result = await storeCheckInPhoto(
      "usr_demo01",
      requestWithBody(
        Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
        "image/jpeg",
        {
          "x-challenge-id": "chl_test",
          "x-attempt-id": "att_test",
        },
      ),
    );

    expect(result.objectKey).toMatch(
      /^checkins\/usr_demo01\/pho_[0-9a-f-]+\.jpg$/,
    );
    expect(result.uploadedAt).toBe("2026-10-20T08:17:21.000Z");
    expect(calls.some((call) => call.includes(
      "/v1/storages/object/checkin-photos/checkins/usr_demo01/",
    ))).toBe(true);
    expect(calls.some((call) => call.includes("/v1/rdb/rest/photos"))).toBe(true);
  });

  it("givenWrongMime_whenStore_thenRejectsPhotoInvalid", async () => {
    await expect(
      storeCheckInPhoto(
        "usr_demo01",
        requestWithBody(Buffer.from([0xff, 0xd8, 0xff]), "image/png"),
      ),
    ).rejects.toMatchObject({ code: "PHOTO_INVALID" });
  });

  it("requires challenge and attempt binding headers for a valid upload", async () => {
    await expect(
      storeCheckInPhoto(
        "usr_demo01",
        requestWithBody(Buffer.from([0xff, 0xd8, 0xff, 0xd9]), "image/jpeg"),
      ),
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  });

  it("rejects a challenge bound to a different attempt", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        response([
          {
            id: "chl_test",
            user_id: "usr_demo01",
            used_at: null,
            expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
            attempt_id: "att_other",
          },
        ]),
      ),
    );

    await expect(
      storeCheckInPhoto(
        "usr_demo01",
        requestWithBody(
          Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
          "image/jpeg",
          {
            "x-challenge-id": "chl_test",
            "x-attempt-id": "att_test",
          },
        ),
      ),
    ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  });

  it("associates an uploaded photo with its challenge and attempt", async () => {
    const writtenRows: Record<string, unknown>[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input);
        if (url.includes("/rdb/rest/challenges")) {
          return response([
            {
              id: "chl_test",
              user_id: "usr_demo01",
              used_at: null,
              expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
              attempt_id: "att_test",
            },
          ]);
        }
        if (url.includes("/v1/storages/object/")) {
          return response({}, 200);
        }
        if (url.includes("/rdb/rest/photos") && init?.method === "POST") {
          writtenRows.push(JSON.parse(String(init.body)) as Record<string, unknown>);
          return response(
            [{ id: "pho_test", uploaded_at: "2026-11-04T08:00:01.000Z" }],
            201,
          );
        }
        return response([]);
      }),
    );

    await storeCheckInPhoto(
      "usr_demo01",
      requestWithBody(
        Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
        "image/jpeg",
        {
          "x-challenge-id": "chl_test",
          "x-attempt-id": "att_test",
        },
      ),
    );

    expect(writtenRows[0]).toMatchObject({
      challenge_id: "chl_test",
      attempt_id: "att_test",
    });
  });

  it("givenNonJpegBytes_whenStore_thenRejectsPhotoInvalid", async () => {
    await expect(
      storeCheckInPhoto(
        "usr_demo01",
        requestWithBody(Buffer.from("not-a-jpeg"), "image/jpeg"),
      ),
    ).rejects.toMatchObject({ code: "PHOTO_INVALID" });
  });

  it("givenOversizedPhoto_whenStore_thenRejectsPhotoInvalid", async () => {
    await expect(
      storeCheckInPhoto(
        "usr_demo01",
        requestWithBody(Buffer.alloc(MAX_PHOTO_BYTES + 1), "image/jpeg"),
      ),
    ).rejects.toMatchObject({ code: "PHOTO_INVALID" });
  });
});

describe("deletePhotoObjectStrict", () => {
  it("verifies the object is absent after deletion", async () => {
    const methods: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_input: string | URL | Request, init?: RequestInit) => {
        methods.push(init?.method ?? "GET");
        return new Response(null, {
          status: init?.method === "DELETE" ? 204 : 404,
        });
      }),
    );

    await expect(
      deletePhotoObjectStrict("checkins/usr_demo01/pho_test.jpg"),
    ).resolves.toBeUndefined();
    expect(methods).toEqual(["DELETE", "HEAD"]);
  });

  it("fails when storage deletion fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("unavailable", { status: 503 })),
    );

    await expect(
      deletePhotoObjectStrict("checkins/usr_demo01/pho_test.jpg"),
    ).rejects.toThrow(/delete|storage/i);
  });

  it("fails when the object still exists after deletion", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_input: string | URL | Request, init?: RequestInit) =>
        new Response(null, { status: init?.method === "DELETE" ? 204 : 200 }),
      ),
    );

    await expect(
      deletePhotoObjectStrict("checkins/usr_demo01/pho_test.jpg"),
    ).rejects.toThrow(/still exists|absent/i);
  });
});

describe("deleteCheckInPhoto", () => {
  it("logically deletes an owned photo without touching an existing check-in", async () => {
    const requests: Array<{ method: string; url: string }> = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input);
        const method = init?.method ?? "GET";
        requests.push({ method, url });
        if (url.includes("/rdb/rest/photos") && method === "GET") {
          return response([
            {
              id: "pho_test",
              user_id: "usr_demo01",
              object_key: "checkins/usr_demo01/pho_test.jpg",
              attempt_id: "att_test",
              deleted_at: null,
            },
          ]);
        }
        if (url.includes("/rdb/rest/checkin_attempts") && method === "GET") {
          return response([
            {
              id: "att_test",
              status: "COMPLETED",
            },
          ]);
        }
        if (url.includes("/rdb/rest/photos") && method === "PATCH") {
          return response([{ id: "pho_test" }]);
        }
        if (url.includes("/v1/storages/object/") && method === "DELETE") {
          return new Response(null, { status: 204 });
        }
        if (url.includes("/v1/storages/object/") && method === "HEAD") {
          return new Response(null, { status: 404 });
        }
        return response([]);
      }),
    );

    await expect(
      deleteCheckInPhoto("usr_demo01", "pho_test", "req_delete"),
    ).resolves.toMatchObject({
      photoId: "pho_test",
      status: "DELETED",
      storageCleanup: "COMPLETED",
    });

    expect(
      requests.some(
        (request) =>
          request.method === "PATCH" &&
          request.url.includes("/rdb/rest/photos"),
      ),
    ).toBe(true);
    expect(
      requests.some(
        (request) =>
          request.method === "DELETE" &&
          request.url.includes("/v1/storages/object/"),
      ),
    ).toBe(true);
    expect(
      requests.some((request) => request.url.includes("/rdb/rest/checkins")),
    ).toBe(false);
  });

  it("refuses to delete the only photo while the attempt result is unknown", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = String(input);
        if (url.includes("/rdb/rest/photos")) {
          return response([
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
          return response([{ id: "att_test", status: "PROCESSING" }]);
        }
        return response([]);
      }),
    );

    await expect(
      deleteCheckInPhoto("usr_demo01", "pho_test", "req_delete"),
    ).rejects.toMatchObject({
      code: "CHECKIN_RESULT_UNKNOWN",
      retryable: true,
    });
  });

  it("does not reveal photos owned by another user", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => response([])),
    );

    await expect(
      deleteCheckInPhoto("usr_demo01", "pho_other", "req_delete"),
    ).rejects.toMatchObject({ code: "PHOTO_NOT_FOUND", status: 404 });
  });
});

describe("cleanupPendingPhotoDeletions", () => {
  it("removes due storage objects and marks cleanup completed", async () => {
    const methods: string[] = [];
    const requestUrls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input);
        const method = init?.method ?? "GET";
        methods.push(method);
        requestUrls.push(url);
        if (url.includes("/rdb/rest/photos") && method === "GET") {
          return response([
            {
              id: "pho_test",
              user_id: "usr_demo01",
              object_key: "checkins/usr_demo01/pho_test.jpg",
            },
          ]);
        }
        if (url.includes("/v1/storages/object/") && method === "DELETE") {
          return new Response(null, { status: 204 });
        }
        if (url.includes("/v1/storages/object/") && method === "HEAD") {
          return new Response(null, { status: 404 });
        }
        if (url.includes("/rdb/rest/photos") && method === "PATCH") {
          return response([{ id: "pho_test" }]);
        }
        return response([]);
      }),
    );

    await expect(
      cleanupPendingPhotoDeletions(),
    ).resolves.toEqual({
      scanned: 1,
      cleaned: 1,
      failed: 0,
    });
    expect(methods).toContain("PATCH");
    expect(
      requestUrls.some((url) => url.includes("delete_after=lte.")),
    ).toBe(false);
  });
});

import { Readable } from "node:stream";
import type { IncomingMessage } from "node:http";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { storeCheckInPhoto } from "../functions/api/handlers/photos.ts";
import { deletePhotoObjectStrict } from "../functions/api/storage.ts";
import { MAX_PHOTO_BYTES } from "../shared/photo.ts";

function requestWithBody(body: Buffer, contentType: string): IncomingMessage {
  return Object.assign(Readable.from([body]), {
    headers: { "content-type": contentType },
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
      requestWithBody(Buffer.from([0xff, 0xd8, 0xff, 0xd9]), "image/jpeg"),
    );

    expect(result.objectKey).toMatch(
      /^checkins\/usr_demo01\/pho_[0-9a-f-]+\.jpg$/,
    );
    expect(result.uploadedAt).toBe("2026-10-20T08:17:21.000Z");
    expect(calls[0]).toContain(
      "/v1/storages/object/checkin-photos/checkins/usr_demo01/",
    );
    expect(calls[1]).toContain("/v1/rdb/rest/photos");
  });

  it("givenWrongMime_whenStore_thenRejectsPhotoInvalid", async () => {
    await expect(
      storeCheckInPhoto(
        "usr_demo01",
        requestWithBody(Buffer.from([0xff, 0xd8, 0xff]), "image/png"),
      ),
    ).rejects.toMatchObject({ code: "PHOTO_INVALID" });
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

import { beforeEach, describe, expect, it, vi } from "vitest";

import { queryCheckInAttempt } from "../functions/api/handlers/checkins.ts";

interface FakeAttempt {
  id: string;
  user_id: string;
  request_hash: string;
  status: "PROCESSING" | "COMPLETED" | "FAILED";
  check_in_id: string | null;
  failure_code: string | null;
}

interface FakeCheckIn {
  id: string;
  user_id: string;
  landmark_id: string;
  challenge_id: string;
  photo_id: string;
  distance_meters: number;
  accuracy_meters: number;
  idempotency_key: string;
  request_hash: string;
  request_id: string;
  created_at: string;
}

const checkIn: FakeCheckIn = {
  id: "chk_test",
  user_id: "usr_demo01",
  landmark_id: "lmk_library",
  challenge_id: "chl_test",
  photo_id: "pho_test",
  distance_meters: 4,
  accuracy_meters: 8,
  idempotency_key: "att_test",
  request_hash: "a".repeat(64),
  request_id: "123e4567-e89b-42d3-a456-426614174000",
  created_at: "2026-11-04T08:00:00.000Z",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function eqValue(value: string | null): string | undefined {
  return value?.startsWith("eq.") ? value.slice(3) : value ?? undefined;
}

function installFetch(attempt: FakeAttempt | null): void {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(typeof input === "string" ? input : input.toString());
      const table = url.pathname.split("/").pop();
      const method = init?.method ?? "GET";
      const get = (key: string) => url.searchParams.get(key);

      if (table === "checkin_attempts" && method === "GET") {
        const rows = attempt
          ? [attempt].filter(
              (row) =>
                row.id === eqValue(get("id")) &&
                row.user_id === eqValue(get("user_id")),
            )
          : [];
        return jsonResponse(rows);
      }

      if (table === "checkins" && method === "GET") {
        const rows = [checkIn].filter(
          (row) =>
            (!get("id") || row.id === eqValue(get("id"))) &&
            (!get("idempotency_key") ||
              row.idempotency_key === eqValue(get("idempotency_key"))),
        );
        return jsonResponse(rows);
      }

      if (table === "fragment_grants" && method === "GET") {
        return jsonResponse([
          {
            id: "grant_test",
            user_id: "usr_demo01",
            fragment_id: "frg_campus_01_01",
            check_in_id: "chk_test",
            granted_at: "2026-11-04T08:00:01.000Z",
          },
        ]);
      }

      if (table === "fragments" && method === "GET") {
        return jsonResponse([
          {
            id: "frg_campus_01_01",
            puzzle_id: "pzl_campus_01",
            piece_index: 0,
          },
        ]);
      }

      if (table === "audit_logs" && method === "GET") {
        return jsonResponse([{ id: 1 }]);
      }

      return jsonResponse([]);
    }),
  );
}

beforeEach(() => {
  process.env.STORAGE_API_BASE = "https://test.example";
  process.env.CLOUDBASE_API_KEY = "test-key";
});

describe("queryCheckInAttempt", () => {
  it("returns processing for a live attempt", async () => {
    installFetch({
      id: "att_test",
      user_id: "usr_demo01",
      request_hash: "a".repeat(64),
      status: "PROCESSING",
      check_in_id: null,
      failure_code: null,
    });

    await expect(
      queryCheckInAttempt("usr_demo01", "att_test", "req_query"),
    ).resolves.toEqual({ kind: "processing" });
  });

  it("returns the stored receipt for a completed attempt", async () => {
    installFetch({
      id: "att_test",
      user_id: "usr_demo01",
      request_hash: "a".repeat(64),
      status: "COMPLETED",
      check_in_id: "chk_test",
      failure_code: null,
    });

    const result = await queryCheckInAttempt("usr_demo01", "att_test", "req_query");

    expect(result.kind).toBe("completed");
    if (result.kind === "completed") {
      expect(result.receipt.checkInId).toBe("chk_test");
    }
  });

  it("returns not_found only when the attempt has no successful check-in", async () => {
    installFetch({
      id: "att_test",
      user_id: "usr_demo01",
      request_hash: "a".repeat(64),
      status: "FAILED",
      check_in_id: null,
      failure_code: "OUT_OF_RANGE",
    });

    await expect(
      queryCheckInAttempt("usr_demo01", "att_test", "req_query"),
    ).resolves.toEqual({ kind: "not_found", attemptId: "att_test" });
  });

  it("returns not_found for an unknown attempt owned by the current user", async () => {
    installFetch(null);

    await expect(
      queryCheckInAttempt("usr_demo01", "att_missing", "req_query"),
    ).resolves.toEqual({ kind: "not_found", attemptId: "att_missing" });
  });
});

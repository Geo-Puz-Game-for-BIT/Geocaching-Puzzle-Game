import { beforeEach, describe, expect, it, vi } from "vitest";

import { submitCheckIn } from "../functions/api/handlers/checkins.ts";

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
  created_at: string;
}

interface FakeGrant {
  id: string;
  user_id: string;
  fragment_id: string;
  check_in_id: string;
  granted_at: string;
}

interface FakeState {
  checkins: FakeCheckIn[];
  grants: FakeGrant[];
  audits: Record<string, unknown>[];
  validChallenges: Set<string>;
  photos: Set<string>;
  failCheckInBeforeInsertOnce: boolean;
}

const landmark = {
  id: "lmk_library",
  name: "BIT Library",
  latitude: 39.9592,
  longitude: 116.3083,
  coordinate_system: "GCJ02",
  check_in_radius_meters: 100,
  difficulty: 2,
  terrain: 1,
  hint: "Look near the main entrance.",
  status: "active",
};

const fragment = {
  id: "frg_campus_01_01",
  puzzle_id: "pzl_campus_01",
  piece_index: 0,
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

function installFetch(state: FakeState): void {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(typeof input === "string" ? input : input.toString());
      const table = url.pathname.split("/").pop();
      const method = init?.method ?? "GET";
      const body =
        typeof init?.body === "string" && init.body.length > 0
          ? (JSON.parse(init.body) as Record<string, unknown>)
          : undefined;
      const get = (key: string) => url.searchParams.get(key);

      if (table === "checkins" && method === "GET") {
        return jsonResponse(
          state.checkins.filter(
            (row) =>
              (!get("user_id") || row.user_id === eqValue(get("user_id"))) &&
              (!get("landmark_id") || row.landmark_id === eqValue(get("landmark_id"))) &&
              (!get("idempotency_key") ||
                row.idempotency_key === eqValue(get("idempotency_key"))),
          ),
        );
      }

      if (table === "challenges" && method === "PATCH") {
        const challengeId = eqValue(get("id")) ?? "";
        if (!state.validChallenges.has(challengeId)) {
          return jsonResponse([]);
        }
        state.validChallenges.delete(challengeId);
        return jsonResponse([{ id: challengeId }]);
      }

      if (table === "photos" && method === "GET") {
        const photoId = eqValue(get("id"));
        return jsonResponse(photoId && state.photos.has(photoId) ? [{ id: photoId }] : []);
      }

      if (table === "landmarks" && method === "GET") {
        return jsonResponse([landmark]);
      }

      if (table === "tasks" && method === "GET") {
        return jsonResponse([{ fragment_id: fragment.id }]);
      }

      if (table === "fragments" && method === "GET") {
        return jsonResponse([fragment]);
      }

      if (table === "fragment_grants" && method === "GET") {
        return jsonResponse(
          state.grants.filter(
            (row) =>
              (!get("user_id") || row.user_id === eqValue(get("user_id"))) &&
              (!get("check_in_id") ||
                row.check_in_id === eqValue(get("check_in_id"))),
          ),
        );
      }

      if (table === "fragment_grants" && method === "POST") {
        const grant = {
          id: String(body?.id),
          user_id: String(body?.user_id),
          fragment_id: String(body?.fragment_id),
          check_in_id: String(body?.check_in_id),
          granted_at: "2026-10-20T08:17:21.000Z",
        };
        state.grants.push(grant);
        return jsonResponse([grant]);
      }

      if (table === "checkins" && method === "POST") {
        if (state.failCheckInBeforeInsertOnce) {
          state.failCheckInBeforeInsertOnce = false;
          throw new Error("simulated insert failure");
        }
        const checkIn = {
          ...body,
          id: String(body?.id),
          created_at: "2026-10-20T08:17:21.000Z",
        } as FakeCheckIn;
        state.checkins.push(checkIn);
        return jsonResponse([checkIn]);
      }

      if (table === "audit_logs" && method === "POST") {
        state.audits.push(body ?? {});
        return jsonResponse([{ id: 1 }]);
      }

      return jsonResponse([]);
    }),
  );
}

function newState(overrides: Partial<FakeState> = {}): FakeState {
  return {
    checkins: [],
    grants: [],
    audits: [],
    validChallenges: new Set(["chl_test"]),
    photos: new Set(["pho_test", "pho_other"]),
    failCheckInBeforeInsertOnce: false,
    ...overrides,
  };
}

function validBody(overrides: Record<string, unknown> = {}) {
  return {
    landmarkId: "lmk_library",
    challengeId: "chl_test",
    photoId: "pho_test",
    location: {
      point: { latitude: 39.9592, longitude: 116.3083 },
      accuracyMeters: 10,
      capturedAt: "2026-10-20T08:17:21.000Z",
    },
    ...overrides,
  };
}

beforeEach(() => {
  process.env.STORAGE_API_BASE = "https://test.example";
  process.env.CLOUDBASE_API_KEY = "test-key";
});

describe("PR #5 check-in regressions", () => {
  it("same idempotency key with a changed photo returns IDEMPOTENCY_CONFLICT", async () => {
    const state = newState();
    installFetch(state);

    await submitCheckIn("usr_demo01", "req_1", "idem_test", validBody());

    await expect(
      submitCheckIn(
        "usr_demo01",
        "req_2",
        "idem_test",
        validBody({ photoId: "pho_other" }),
      ),
    ).rejects.toMatchObject({ code: "IDEMPOTENCY_CONFLICT" });
    expect(state.checkins).toHaveLength(1);
    expect(state.grants).toHaveLength(1);
  });

  it("challenge remains usable when a pre-insert validation fails", async () => {
    const state = newState({ photos: new Set() });
    installFetch(state);

    await expect(
      submitCheckIn("usr_demo01", "req_1", "idem_test", validBody()),
    ).rejects.toMatchObject({ code: "PHOTO_REQUIRED" });
    expect(state.validChallenges.has("chl_test")).toBe(true);

    state.photos.add("pho_test");
    const receipt = await submitCheckIn("usr_demo01", "req_2", "idem_test", validBody());

    expect(receipt.status).toBe("COMPLETED");
    expect(state.checkins).toHaveLength(1);
    expect(state.grants).toHaveLength(1);
    expect(state.audits).toHaveLength(1);
  });

  it("challenge remains usable when the check-in insert fails before commit", async () => {
    const state = newState({ failCheckInBeforeInsertOnce: true });
    installFetch(state);

    await expect(
      submitCheckIn("usr_demo01", "req_1", "idem_test", validBody()),
    ).rejects.toThrow("simulated insert failure");
    expect(state.checkins).toHaveLength(0);
    expect(state.validChallenges.has("chl_test")).toBe(true);

    const receipt = await submitCheckIn("usr_demo01", "req_2", "idem_test", validBody());

    expect(receipt.status).toBe("COMPLETED");
    expect(state.checkins).toHaveLength(1);
  });

  it("recovery restores the missing CHECKIN_GRANTED audit", async () => {
    const state = newState();
    installFetch(state);

    await submitCheckIn("usr_demo01", "req_1", "idem_test", validBody());
    state.audits.length = 0;

    const receipt = await submitCheckIn("usr_demo01", "req_2", "idem_test", validBody());

    expect(receipt.status).toBe("COMPLETED");
    expect(state.checkins).toHaveLength(1);
    expect(state.grants).toHaveLength(1);
    expect(state.audits).toHaveLength(1);
  });
});

import { randomUUID } from "node:crypto";

import { afterEach, describe, expect, it } from "vitest";

import {
  deleteRows,
  insertRow,
  selectRows,
  updateRows,
} from "../functions/api/db.ts";
import { submitCheckIn } from "../functions/api/handlers/checkins.ts";
import { listNearbyLandmarks } from "../functions/api/handlers/landmarks.ts";
import { getPuzzleProgress } from "../functions/api/handlers/puzzles.ts";

const runDatabaseIntegration = process.env.RUN_DATABASE_INTEGRATION === "1";

interface Fixture {
  userId: string;
  puzzleId: string;
  sectionId: string;
  landmarkId: string;
  fragmentId: string;
  taskId: string;
  photoIds: string[];
}

const fixture: Fixture = {
  userId: "",
  puzzleId: "",
  sectionId: "",
  landmarkId: "",
  fragmentId: "",
  taskId: "",
  photoIds: [],
};

function requestBody(landmarkId: string, challengeId: string, photoId: string) {
  return {
    landmarkId,
    challengeId,
    photoId,
    location: {
      point: { latitude: 39.7366, longitude: 116.1666 },
      accuracyMeters: 8,
      capturedAt: "2026-10-25T10:00:00.000Z",
    },
  };
}

async function createFixture(): Promise<Fixture> {
  const suffix = randomUUID().replaceAll("-", "").slice(0, 12);
  const value: Fixture = {
    userId: `usr_itest_${suffix}`,
    puzzleId: `puzzle_itest_${suffix}`,
    sectionId: `section_itest_${suffix}`,
    landmarkId: `lmk_itest_${suffix}`,
    fragmentId: `fragment_itest_${suffix}`,
    taskId: `task_itest_${suffix}`,
    photoIds: [
      `pho_itest_${suffix}_a`,
      `pho_itest_${suffix}_b`,
    ],
  };

  await insertRow("users", {
    id: value.userId,
    display_name: "Database Integration Test",
  });
  await insertRow("puzzles", {
    id: value.puzzleId,
    name: "Integration Puzzle",
    rows: 1,
    columns: 1,
    default_reveal_mode: "GRID",
  });
  await insertRow("puzzle_sections", {
    id: value.sectionId,
    puzzle_id: value.puzzleId,
    campus_zone: "NORTH",
    rows: 1,
    columns: 1,
    sort_order: 0,
  });
  await insertRow("landmarks", {
    id: value.landmarkId,
    name: "Integration Landmark",
    latitude: 39.7366,
    longitude: 116.1666,
    coordinate_system: "GCJ02",
    check_in_radius_meters: 55,
    status: "active",
    campus_zone: null,
    piece_index: null,
    coordinate_verified: false,
    check_in_area: { type: "circle", radiusMeters: 55 },
    difficulty: 1,
    terrain: 1,
    hint: "Integration only",
  });
  await insertRow("fragments", {
    id: value.fragmentId,
    puzzle_id: value.puzzleId,
    piece_index: 0,
    image_key: `puzzles/${value.puzzleId}/${value.fragmentId}.png`,
    section_id: value.sectionId,
    landmark_id: value.landmarkId,
    title: "Integration Fragment",
  });
  await updateRows(
    "landmarks",
    { id: `eq.${value.landmarkId}` },
    { fragment_id: value.fragmentId },
  );
  await insertRow("tasks", {
    id: value.taskId,
    landmark_id: value.landmarkId,
    type: "photo",
    instructions: "Integration photo task",
    puzzle_id: value.puzzleId,
    fragment_id: value.fragmentId,
  });
  await insertRow("photos", {
    id: value.photoIds[0],
    user_id: value.userId,
    object_key: `checkins/${value.userId}/${value.photoIds[0]}.jpg`,
    mime_type: "image/jpeg",
    size_bytes: 3,
  });
  await insertRow("photos", {
    id: value.photoIds[1],
    user_id: value.userId,
    object_key: `checkins/${value.userId}/${value.photoIds[1]}.jpg`,
    mime_type: "image/jpeg",
    size_bytes: 3,
  });

  Object.assign(fixture, value);
  return value;
}

async function createChallenge(value: Fixture, suffix: string): Promise<string> {
  const challengeId = `chl_itest_${suffix}`;
  await insertRow("challenges", {
    id: challengeId,
    user_id: value.userId,
    landmark_id: value.landmarkId,
    expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
  });
  return challengeId;
}

async function cleanupFixture(value: Fixture): Promise<void> {
  await deleteRows("audit_logs", { user_id: `eq.${value.userId}` });
  await deleteRows("fragment_grants", { user_id: `eq.${value.userId}` });
  await deleteRows("checkins", { user_id: `eq.${value.userId}` });
  await deleteRows("challenges", { user_id: `eq.${value.userId}` });
  await deleteRows("photos", { user_id: `eq.${value.userId}` });
  await deleteRows("tasks", { landmark_id: `eq.${value.landmarkId}` });
  await updateRows(
    "landmarks",
    { id: `eq.${value.landmarkId}` },
    { fragment_id: null },
  );
  await deleteRows("fragments", { id: `eq.${value.fragmentId}` });
  await deleteRows("landmarks", { id: `eq.${value.landmarkId}` });
  await deleteRows("puzzle_sections", { id: `eq.${value.sectionId}` });
  await deleteRows("puzzles", { id: `eq.${value.puzzleId}` });
  await deleteRows("users", { id: `eq.${value.userId}` });
}

afterEach(async () => {
  if (runDatabaseIntegration && fixture.userId) {
    await cleanupFixture(fixture);
  }
});

describe.skipIf(!runDatabaseIntegration)("real PostgreSQL consistency", () => {
  it("canonical API model exposes three campus sections and twelve landmarks", async () => {
    const landmarks = await listNearbyLandmarks({});
    const puzzle = await getPuzzleProgress(
      "usr_demo01",
      "puzzle_liangxiang_campus",
    );

    expect(landmarks).toHaveLength(12);
    expect(new Set(landmarks.map((landmark) => landmark.campusZone))).toEqual(
      new Set(["NORTH", "EAST", "SOUTH"]),
    );
    expect(puzzle.sections).toHaveLength(3);
    expect(puzzle.sections.map((section) => section.campusZone)).toEqual([
      "NORTH",
      "EAST",
      "SOUTH",
    ]);
    expect(puzzle.sections.flatMap((section) => section.fragments)).toHaveLength(12);
  });

  it("same key with a changed photo is a conflict and keeps one persisted result", async () => {
    const value = await createFixture();
    const challengeId = await createChallenge(value, "idem");
    const key = `idem-${randomUUID()}`;

    const first = await submitCheckIn(
      value.userId,
      randomUUID(),
      key,
      requestBody(value.landmarkId, challengeId, value.photoIds[0]),
    );

    await expect(
      submitCheckIn(
        value.userId,
        randomUUID(),
        key,
        {
          ...requestBody(value.landmarkId, challengeId, value.photoIds[1]),
          location: {
            ...requestBody(value.landmarkId, challengeId, value.photoIds[1]).location,
            capturedAt: "2026-10-25T10:00:01.000Z",
          },
        },
      ),
    ).rejects.toMatchObject({ code: "IDEMPOTENCY_CONFLICT" });

    const checkins = await selectRows("checkins", {
      user_id: `eq.${value.userId}`,
    });
    const grants = await selectRows("fragment_grants", {
      user_id: `eq.${value.userId}`,
    });

    expect(first.status).toBe("COMPLETED");
    expect(checkins).toHaveLength(1);
    expect(grants).toHaveLength(1);
  });

  it("failed pre-insert validation does not consume the challenge", async () => {
    const value = await createFixture();
    const challengeId = await createChallenge(value, "retry");
    const key = `retry-${randomUUID()}`;

    await expect(
      submitCheckIn(
        value.userId,
        randomUUID(),
        key,
        requestBody(value.landmarkId, challengeId, "pho_missing"),
      ),
    ).rejects.toMatchObject({ code: "PHOTO_REQUIRED" });

    const untouched = await selectRows<{ used_at: string | null }>("challenges", {
      select: "used_at",
      id: `eq.${challengeId}`,
      limit: "1",
    });
    expect(untouched[0]?.used_at).toBeNull();

    const receipt = await submitCheckIn(
      value.userId,
      randomUUID(),
      key,
      requestBody(value.landmarkId, challengeId, value.photoIds[0]),
    );
    expect(receipt.status).toBe("COMPLETED");
  });

  it("transaction rolls back challenge and check-in when reward materialization fails", async () => {
    const value = await createFixture();
    const challengeId = await createChallenge(value, "rollback");
    const key = `rollback-${randomUUID()}`;

    await deleteRows("tasks", { landmark_id: `eq.${value.landmarkId}` });
    await expect(
      submitCheckIn(
        value.userId,
        randomUUID(),
        key,
        requestBody(value.landmarkId, challengeId, value.photoIds[0]),
      ),
    ).rejects.toThrow(/LANDMARK_NOT_FOUND/i);

    const rolledBackChallenge = await selectRows<{ used_at: string | null }>(
      "challenges",
      {
        select: "used_at",
        id: `eq.${challengeId}`,
        limit: "1",
      },
    );
    const rolledBackCheckins = await selectRows("checkins", {
      user_id: `eq.${value.userId}`,
    });
    expect(rolledBackChallenge[0]?.used_at).toBeNull();
    expect(rolledBackCheckins).toHaveLength(0);

    await insertRow("tasks", {
      id: value.taskId,
      landmark_id: value.landmarkId,
      type: "photo",
      instructions: "Integration photo task",
      puzzle_id: value.puzzleId,
      fragment_id: value.fragmentId,
    });
    const receipt = await submitCheckIn(
      value.userId,
      randomUUID(),
      key,
      requestBody(value.landmarkId, challengeId, value.photoIds[0]),
    );

    expect(receipt.status).toBe("COMPLETED");
  });

  it("retry repairs a missing audit while preserving one check-in and one grant", async () => {
    const value = await createFixture();
    const challengeId = await createChallenge(value, "audit");
    const key = `audit-${randomUUID()}`;
    const body = requestBody(value.landmarkId, challengeId, value.photoIds[0]);

    await submitCheckIn(value.userId, randomUUID(), key, body);
    await deleteRows("audit_logs", { user_id: `eq.${value.userId}` });

    const receipt = await submitCheckIn(value.userId, randomUUID(), key, body);
    const checkins = await selectRows("checkins", { user_id: `eq.${value.userId}` });
    const grants = await selectRows("fragment_grants", { user_id: `eq.${value.userId}` });
    const audits = await selectRows("audit_logs", {
      user_id: `eq.${value.userId}`,
      event_type: "eq.CHECKIN_GRANTED",
    });

    expect(receipt.status).toBe("COMPLETED");
    expect(checkins).toHaveLength(1);
    expect(grants).toHaveLength(1);
    expect(audits).toHaveLength(1);
  });

  it("concurrent different keys still produce one check-in and one grant", async () => {
    const value = await createFixture();
    const firstChallenge = await createChallenge(value, "concurrent_a");
    const secondChallenge = await createChallenge(value, "concurrent_b");

    const results = await Promise.all([
      submitCheckIn(
        value.userId,
        randomUUID(),
        `concurrent-a-${randomUUID()}`,
        requestBody(value.landmarkId, firstChallenge, value.photoIds[0]),
      ),
      submitCheckIn(
        value.userId,
        randomUUID(),
        `concurrent-b-${randomUUID()}`,
        requestBody(value.landmarkId, secondChallenge, value.photoIds[1]),
      ),
    ]);

    const checkins = await selectRows("checkins", { user_id: `eq.${value.userId}` });
    const grants = await selectRows("fragment_grants", { user_id: `eq.${value.userId}` });
    const audits = await selectRows("audit_logs", {
      user_id: `eq.${value.userId}`,
      event_type: "eq.CHECKIN_GRANTED",
    });

    expect(results.map((receipt) => receipt.status).sort()).toEqual([
      "ALREADY_COMPLETED",
      "COMPLETED",
    ]);
    expect(checkins).toHaveLength(1);
    expect(grants).toHaveLength(1);
    expect(audits).toHaveLength(1);
  });
});

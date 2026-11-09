import { createHash, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

import { afterEach, describe, expect, it } from "vitest";

import {
  deleteRows,
  insertRow,
  selectRows,
  updateRows,
} from "../functions/api/db.ts";
import {
  queryCheckInAttempt,
  submitCheckIn,
} from "../functions/api/handlers/checkins.ts";
import { listNearbyLandmarks } from "../functions/api/handlers/landmarks.ts";
import { getPuzzleProgress } from "../functions/api/handlers/puzzles.ts";

const runDatabaseIntegration = process.env.RUN_DATABASE_INTEGRATION === "1";
const sleep = (milliseconds: number) =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));
interface CanonicalFragment {
  id: string;
  landmarkId: string;
  title: string;
  pieceIndex: number;
  imageKey: string;
}

interface CanonicalSection {
  id: string;
  campusZone: string;
  rows: number;
  columns: number;
  fragments: CanonicalFragment[];
}

interface CanonicalLandmark {
  id: string;
  name: string;
  sectionId: string;
  pieceIndex: number;
  latitude: number;
  longitude: number;
  coordinateSystem: string;
  coordinateVerified: boolean;
  checkInArea: unknown;
}

interface CanonicalCatalog {
  sections: CanonicalSection[];
  landmarks: CanonicalLandmark[];
}

const canonicalCatalog = JSON.parse(
  readFileSync(
    new URL("../../docs/api/fixtures/campus-catalog.json", import.meta.url),
    "utf8",
  ),
) as CanonicalCatalog;

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
      capturedAt: new Date().toISOString(),
    },
  };
}

function requestHash(body: ReturnType<typeof requestBody>): string {
  return createHash("sha256")
    .update(
      JSON.stringify([
        body.landmarkId,
        body.challengeId,
        body.photoId,
        body.location.point.latitude,
        body.location.point.longitude,
        body.location.accuracyMeters,
        body.location.capturedAt,
        "UNKNOWN",
        null,
        null,
      ]),
    )
    .digest("hex");
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
  Object.assign(fixture, value);

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
    campus_zone: "NORTH",
    piece_index: 0,
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
  return value;
}

async function createChallenge(
  value: Fixture,
  suffix: string,
  attemptId: string,
): Promise<string> {
  const challengeId = `chl_itest_${suffix}_${randomUUID().replaceAll("-", "").slice(0, 12)}`;
  await insertRow("challenges", {
    id: challengeId,
    user_id: value.userId,
    landmark_id: value.landmarkId,
    attempt_id: attemptId,
    expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
  });
  return challengeId;
}

async function bindPhoto(
  value: Fixture,
  photoIndex: number,
  challengeId: string,
  attemptId: string,
): Promise<void> {
  await updateRows(
    "photos",
    { id: `eq.${value.photoIds[photoIndex]}` },
    {
      challenge_id: challengeId,
      attempt_id: attemptId,
    },
  );
}

async function cleanupFixture(value: Fixture): Promise<void> {
  await deleteRows("audit_logs", { user_id: `eq.${value.userId}` });
  await deleteRows("fragment_grants", { user_id: `eq.${value.userId}` });
  await deleteRows("checkin_attempts", { user_id: `eq.${value.userId}` });
  await deleteRows("checkins", { user_id: `eq.${value.userId}` });
  await updateRows(
    "photos",
    { user_id: `eq.${value.userId}` },
    {
      challenge_id: null,
      attempt_id: null,
    },
  );
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
    const canonicalIds = new Set(canonicalCatalog.landmarks.map((landmark) => landmark.id));
    const landmarks = (await listNearbyLandmarks({})).filter((landmark) =>
      canonicalIds.has(landmark.id),
    );
    const puzzle = await getPuzzleProgress(
      "usr_demo01",
      "puzzle_liangxiang_campus",
    );

    expect(landmarks).toHaveLength(12);
    expect(new Set(landmarks.map((landmark) => landmark.campusZone))).toEqual(
      new Set(["NORTH", "EAST", "SOUTH"]),
    );
    expect(
      puzzle.sections.map((section) => ({
        id: section.id,
        campusZone: section.campusZone,
        rows: section.rows,
        columns: section.columns,
        fragments: section.fragments.map((fragment) => ({
          id: fragment.id,
          landmarkId: fragment.landmarkId,
          title: fragment.title,
          pieceIndex: fragment.pieceIndex,
          imageKey: fragment.imageKey,
        })),
      })),
    ).toEqual(
      canonicalCatalog.sections.map((section) => ({
        id: section.id,
        campusZone: section.campusZone,
        rows: section.rows,
        columns: section.columns,
        fragments: section.fragments.map((fragment) => ({
          id: fragment.id,
          landmarkId: fragment.landmarkId,
          title: fragment.title,
          pieceIndex: fragment.pieceIndex,
          imageKey: fragment.imageKey,
        })),
      })),
    );
    const actualLandmarks = landmarks
      .map((landmark) => ({
        id: landmark.id,
        name: landmark.name,
        campusZone: landmark.campusZone,
        fragmentId: landmark.fragmentId,
        pieceIndex: landmark.pieceIndex,
        point: landmark.point,
        coordinateVerified: landmark.coordinateVerified,
        checkInArea: landmark.checkInArea,
      }))
      .sort((left, right) => left.id.localeCompare(right.id));
    const expectedLandmarks = canonicalCatalog.landmarks
      .map((landmark) => ({
        id: landmark.id,
        name: landmark.name,
        campusZone: canonicalCatalog.sections.find(
          (section) => section.id === landmark.sectionId,
        )!.campusZone,
        fragmentId: canonicalCatalog.sections
          .flatMap((section) => section.fragments)
          .find((fragment) => fragment.landmarkId === landmark.id)!.id,
        pieceIndex: landmark.pieceIndex,
        point: {
          latitude: landmark.latitude,
          longitude: landmark.longitude,
        },
        coordinateVerified: landmark.coordinateVerified,
        checkInArea: landmark.checkInArea,
      }))
      .sort((left, right) => left.id.localeCompare(right.id));
    expect(actualLandmarks).toEqual(expectedLandmarks);
  });

  it("same key with a changed photo is a conflict and keeps one persisted result", async () => {
    const value = await createFixture();
    const key = `idem-${randomUUID()}`;
    const challengeId = await createChallenge(value, "idem", key);
    await bindPhoto(value, 0, challengeId, key);

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
    const attempts = await selectRows("checkin_attempts", {
      user_id: `eq.${value.userId}`,
    });

    expect(first.status).toBe("COMPLETED");
    expect(checkins).toHaveLength(1);
    expect(grants).toHaveLength(1);
    expect(attempts).toHaveLength(1);
    expect(attempts[0]).toMatchObject({
      status: "COMPLETED",
      check_in_id: first.checkInId,
    });

    await expect(
      queryCheckInAttempt(value.userId, key, randomUUID()),
    ).resolves.toMatchObject({
      kind: "completed",
      receipt: { checkInId: first.checkInId },
    });
  });

  it("failed pre-insert validation does not consume the challenge", async () => {
    const value = await createFixture();
    const firstAttemptId = `retry-a-${randomUUID()}`;
    const firstChallengeId = await createChallenge(value, "retry_a", firstAttemptId);
    await bindPhoto(value, 0, firstChallengeId, firstAttemptId);

    await expect(
      submitCheckIn(
        value.userId,
        randomUUID(),
        firstAttemptId,
        requestBody(value.landmarkId, firstChallengeId, "pho_missing"),
      ),
    ).rejects.toMatchObject({ code: "PHOTO_REQUIRED" });

    const untouched = await selectRows<{ used_at: string | null }>("challenges", {
      select: "used_at",
      id: `eq.${firstChallengeId}`,
      limit: "1",
    });
    expect(untouched[0]?.used_at).toBeNull();

    const secondAttemptId = `retry-b-${randomUUID()}`;
    const secondChallengeId = await createChallenge(value, "retry_b", secondAttemptId);
    await bindPhoto(value, 0, secondChallengeId, secondAttemptId);
    const receipt = await submitCheckIn(
      value.userId,
      randomUUID(),
      secondAttemptId,
      requestBody(value.landmarkId, secondChallengeId, value.photoIds[0]),
    );
    expect(receipt.status).toBe("COMPLETED");
  });

  it("transaction rolls back challenge and check-in when reward materialization fails", async () => {
    const value = await createFixture();
    const key = `rollback-${randomUUID()}`;
    const challengeId = await createChallenge(value, "rollback", key);
    await bindPhoto(value, 0, challengeId, key);
    const body = requestBody(value.landmarkId, challengeId, value.photoIds[0]);

    await deleteRows("tasks", { landmark_id: `eq.${value.landmarkId}` });
    await expect(
      submitCheckIn(
        value.userId,
        randomUUID(),
        key,
        body,
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
      body,
    );

    expect(receipt.status).toBe("COMPLETED");
  });

  it("retry repairs a missing audit while preserving one check-in and one grant", async () => {
    const value = await createFixture();
    const key = `audit-${randomUUID()}`;
    const challengeId = await createChallenge(value, "audit", key);
    await bindPhoto(value, 0, challengeId, key);
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
    const firstKey = `concurrent-a-${randomUUID()}`;
    const secondKey = `concurrent-b-${randomUUID()}`;
    const firstChallenge = await createChallenge(value, "concurrent_a", firstKey);
    const secondChallenge = await createChallenge(value, "concurrent_b", secondKey);
    await bindPhoto(value, 0, firstChallenge, firstKey);
    await bindPhoto(value, 1, secondChallenge, secondKey);

    const results = await Promise.all([
      submitCheckIn(
        value.userId,
        randomUUID(),
        firstKey,
        requestBody(value.landmarkId, firstChallenge, value.photoIds[0]),
      ),
      submitCheckIn(
        value.userId,
        randomUUID(),
        secondKey,
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

  it("seals an orphan PROCESSING attempt and rejects a late request", async () => {
    const value = await createFixture();
    const attemptId = `orphan-${randomUUID()}`;
    await insertRow("checkin_attempts", {
      id: attemptId,
      user_id: value.userId,
      request_hash: "a".repeat(64),
      status: "PROCESSING",
      created_at: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
      updated_at: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    });

    await expect(
      queryCheckInAttempt(value.userId, attemptId, randomUUID()),
    ).resolves.toEqual({ kind: "not_found", attemptId });

    const attempts = await selectRows<{ status: string; failure_code: string | null }>(
      "checkin_attempts",
      {
        select: "status,failure_code",
        id: `eq.${attemptId}`,
        user_id: `eq.${value.userId}`,
        limit: "1",
      },
    );
    expect(attempts[0]).toMatchObject({
      status: "ABANDONED",
      failure_code: "PROCESSING_TIMEOUT",
    });

    const challengeId = await createChallenge(value, "late", attemptId);
    await bindPhoto(value, 0, challengeId, attemptId);
    await expect(
      submitCheckIn(
        value.userId,
        randomUUID(),
        attemptId,
        requestBody(value.landmarkId, challengeId, value.photoIds[0]),
      ),
    ).rejects.toMatchObject({ code: "ATTEMPT_SEALED" });
  });

  it("keeps POST-first interleaving from returning a false 404", async () => {
    const value = await createFixture();
    const key = `post-first-${randomUUID()}`;
    const challengeId = await createChallenge(value, "post_first", key);
    await bindPhoto(value, 0, challengeId, key);
    const body = requestBody(value.landmarkId, challengeId, value.photoIds[0]);
    await insertRow("checkin_attempts", {
      id: key,
      user_id: value.userId,
      request_hash: requestHash(body),
      status: "PROCESSING",
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    });

    const submitPromise = submitCheckIn(value.userId, randomUUID(), key, body);
    const queryPromise = (async () => {
      await sleep(25);
      return queryCheckInAttempt(value.userId, key, randomUUID());
    })();

    const [submitResult, queryResult] = await Promise.allSettled([
      submitPromise,
      queryPromise,
    ]);
    expect(submitResult.status).toBe("fulfilled");
    expect(queryResult.status).toBe("fulfilled");
    if (queryResult.status === "fulfilled") {
      expect(queryResult.value.kind).not.toBe("not_found");
    }

    await expect(
      queryCheckInAttempt(value.userId, key, randomUUID()),
    ).resolves.toMatchObject({ kind: "completed" });
    const checkins = await selectRows("checkins", {
      user_id: `eq.${value.userId}`,
    });
    expect(checkins).toHaveLength(1);
  });

  it("keeps seal-first and submit-first interleavings internally consistent", async () => {
    const value = await createFixture();
    const attemptId = `seal-race-${randomUUID()}`;
    const challengeId = await createChallenge(value, "seal_race", attemptId);
    await bindPhoto(value, 0, challengeId, attemptId);
    const body = requestBody(value.landmarkId, challengeId, value.photoIds[0]);
    await insertRow("checkin_attempts", {
      id: attemptId,
      user_id: value.userId,
      request_hash: requestHash(body),
      status: "PROCESSING",
      created_at: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
      updated_at: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    });

    const queryPromise = queryCheckInAttempt(value.userId, attemptId, randomUUID());
    const submitPromise = (async () => {
      await sleep(10);
      return submitCheckIn(value.userId, randomUUID(), attemptId, body);
    })();
    const [queryResult, submitResult] = await Promise.allSettled([
      queryPromise,
      submitPromise,
    ]);

    expect(queryResult.status).toBe("fulfilled");
    if (submitResult.status === "fulfilled") {
      if (queryResult.status === "fulfilled") {
        expect(queryResult.value.kind).toBe("completed");
      }
      const checkins = await selectRows("checkins", {
        user_id: `eq.${value.userId}`,
        idempotency_key: `eq.${attemptId}`,
      });
      expect(checkins).toHaveLength(1);
    } else {
      expect(submitResult.reason).toMatchObject({ code: "ATTEMPT_SEALED" });
      if (queryResult.status === "fulfilled") {
        expect(queryResult.value.kind).toBe("not_found");
      }
      const attempts = await selectRows("checkin_attempts", {
        id: `eq.${attemptId}`,
        user_id: `eq.${value.userId}`,
      });
      expect(attempts[0]).toMatchObject({
        status: "ABANDONED",
        failure_code: "PROCESSING_TIMEOUT",
      });
    }
  });

  it("selects immediately pending photo cleanup without waiting for delete_after", async () => {
    const value = await createFixture();
    const future = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
    await updateRows(
      "photos",
      { id: `eq.${value.photoIds[0]}` },
      {
        deleted_at: new Date().toISOString(),
        delete_after: future,
        storage_deleted_at: null,
      },
    );

    const pending = await selectRows<{
      id: string;
      delete_after: string;
      storage_deleted_at: string | null;
    }>("photos", {
      select: "id,delete_after,storage_deleted_at",
      user_id: `eq.${value.userId}`,
      deleted_at: "not.is.null",
      storage_deleted_at: "is.null",
    });

    expect(pending).toHaveLength(1);
    expect(pending[0]).toMatchObject({
      id: value.photoIds[0],
      storage_deleted_at: null,
    });
    expect(Date.parse(pending[0]!.delete_after)).toBe(Date.parse(future));
  });
});

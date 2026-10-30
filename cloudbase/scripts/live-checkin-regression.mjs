import { randomUUID } from "node:crypto";
import { Buffer } from "node:buffer";

import {
  deleteRows,
  selectRows,
} from "../functions/api/db.ts";
import { deletePhotoObjectStrict } from "../functions/api/storage.ts";

const apiBase = process.env.LIVE_API_BASE;
const userId = process.env.DEMO_USER_ID;
const authToken = process.env.DEMO_AUTH_TOKEN;

// 1x1 synthetic JPEG used only for the real HTTP upload path.
const syntheticJpeg = Buffer.from(
  "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/EH//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/EH//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/EH//2Q==",
  "base64",
);

if (!apiBase || !userId || !authToken) {
  throw new Error("LIVE_API_BASE, DEMO_USER_ID, and DEMO_AUTH_TOKEN are required");
}

const idempotencyKey = `live-${randomUUID()}`;
const photoObjects = [];
let challengeId = "";
let checkInId = "";

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function jsonRequest(path, init = {}) {
  const requestId = String(init.headers?.["X-Request-Id"] ?? randomUUID());
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${authToken}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
      "X-Request-Id": requestId,
    },
  });
  const body = await response.json();
  assert(
    response.headers.get("x-request-id") === requestId &&
      body.requestId === requestId,
    `Request ID mismatch for ${path}`,
  );
  return { requestId, response, body };
}

async function uploadPhoto() {
  const requestId = randomUUID();
  const response = await fetch(`${apiBase}/checkin-photos`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${authToken}`,
      "Content-Type": "image/jpeg",
      "X-Request-Id": requestId,
    },
    body: syntheticJpeg,
  });
  const body = await response.json();
  assert(response.status === 200, `Photo upload failed: ${response.status}`);
  assert(
    response.headers.get("x-request-id") === requestId &&
      body.requestId === requestId,
    "Request ID mismatch for /checkin-photos",
  );
  const uploaded = {
    requestId,
    photoId: body.data.photoId,
    objectKey: body.data.objectKey,
  };
  photoObjects.push(uploaded);
  return uploaded;
}

function checkInBody(landmark, challenge, photoId, capturedAt = new Date().toISOString()) {
  return {
    landmarkId: landmark.id,
    challengeId: challenge,
    photoId,
    location: {
      point: {
        latitude: landmark.latitude,
        longitude: landmark.longitude,
      },
      accuracyMeters: 8,
      capturedAt,
    },
  };
}

async function ownedFragmentCount() {
  const puzzle = await jsonRequest("/puzzles/puzzle_liangxiang_campus", {
    method: "GET",
    headers: { "X-Request-Id": randomUUID() },
  });
  assert(puzzle.response.status === 200, "Puzzle progress request failed");
  return {
    count: puzzle.body.data.ownedFragmentIds.length,
    puzzle: puzzle.body.data,
    requestId: puzzle.requestId,
  };
}

async function cleanup() {
  const failures = [];
  const dbOperations = [
    checkInId
      ? () => deleteRows("audit_logs", { entity_id: `eq.${checkInId}` })
      : null,
    checkInId
      ? () => deleteRows("fragment_grants", { check_in_id: `eq.${checkInId}` })
      : null,
    checkInId
      ? () => deleteRows("checkins", { id: `eq.${checkInId}` })
      : null,
    challengeId
      ? () => deleteRows("challenges", { id: `eq.${challengeId}` })
      : null,
    ...photoObjects.map(
      (photo) => () => deleteRows("photos", { id: `eq.${photo.photoId}` }),
    ),
  ].filter(Boolean);

  for (const operation of dbOperations) {
    try {
      await operation();
    } catch (error) {
      failures.push(error);
    }
  }
  for (const photo of photoObjects) {
    try {
      await deletePhotoObjectStrict(photo.objectKey);
    } catch (error) {
      failures.push(error);
    }
  }
  if (failures.length > 0) {
    throw new AggregateError(failures, "Live regression cleanup failed");
  }
}

try {
  const [landmarks, existingCheckIns] = await Promise.all([
    selectRows("landmarks", {
      select: "id,latitude,longitude",
      status: "eq.active",
      order: "id.asc",
    }),
    selectRows("checkins", {
      select: "landmark_id",
      user_id: `eq.${userId}`,
    }),
  ]);
  const completed = new Set(existingCheckIns.map((row) => row.landmark_id));
  const landmark = landmarks.find((row) => !completed.has(row.id));
  assert(landmark, "No unused active landmark is available for live regression");

  const progressBefore = await ownedFragmentCount();

  const challenge = await jsonRequest("/checkin-challenges", {
    method: "POST",
    headers: { "X-Request-Id": randomUUID() },
    body: JSON.stringify({ landmarkId: landmark.id }),
  });
  assert(challenge.response.status === 200, "Challenge creation failed");
  challengeId = challenge.body.data.id;

  const firstPhoto = await uploadPhoto();
  const secondPhoto = await uploadPhoto();
  const firstBody = checkInBody(landmark, challengeId, firstPhoto.photoId);
  const first = await jsonRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(firstBody),
  });
  assert(
    first.response.status === 200 && first.body.data.status === "COMPLETED",
    "First check-in failed or did not return COMPLETED",
  );
  checkInId = first.body.data.checkInId;

  const progressAfterFirst = await ownedFragmentCount();
  assert(
    progressAfterFirst.count === progressBefore.count + 1,
    "First check-in did not increase puzzle progress by one",
  );
  assert(
    progressAfterFirst.puzzle.ownedFragmentIds.includes(
      first.body.data.fragmentGrant.fragmentId,
    ),
    "Granted fragment is not owned by the puzzle response",
  );

  const replay = await jsonRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(firstBody),
  });
  assert(
    replay.response.status === 200 &&
      replay.body.data.checkInId === checkInId &&
      replay.body.data.status === "COMPLETED",
    "Same-key replay did not return the original receipt",
  );
  const progressAfterReplay = await ownedFragmentCount();
  assert(
    progressAfterReplay.count === progressAfterFirst.count,
    "Same-key replay changed puzzle progress",
  );

  const conflict = await jsonRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(
      checkInBody(
        landmark,
        challengeId,
        secondPhoto.photoId,
        new Date(Date.now() + 1_000).toISOString(),
      ),
    ),
  });
  assert(
    conflict.response.status === 409 &&
      conflict.body.error?.code === "IDEMPOTENCY_CONFLICT",
    "Changed photo did not return IDEMPOTENCY_CONFLICT",
  );

  const duplicate = await jsonRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": `live-duplicate-${randomUUID()}`,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(
      checkInBody(
        landmark,
        challengeId,
        firstPhoto.photoId,
        new Date(Date.now() + 2_000).toISOString(),
      ),
    ),
  });
  assert(
    duplicate.response.status === 200 &&
      duplicate.body.data.checkInId === checkInId &&
      duplicate.body.data.status === "ALREADY_COMPLETED",
    "New-key duplicate did not return ALREADY_COMPLETED",
  );
  const progressAfterDuplicate = await ownedFragmentCount();
  assert(
    progressAfterDuplicate.count === progressAfterFirst.count,
    "Duplicate check-in changed puzzle progress",
  );

  const [checkIns, grants, audits, challenges] = await Promise.all([
    selectRows("checkins", { id: `eq.${checkInId}` }),
    selectRows("fragment_grants", { check_in_id: `eq.${checkInId}` }),
    selectRows("audit_logs", {
      entity_id: `eq.${checkInId}`,
      event_type: "eq.CHECKIN_GRANTED",
    }),
    selectRows("challenges", {
      select: "used_at",
      id: `eq.${challengeId}`,
      limit: "1",
    }),
  ]);

  assert(checkIns.length === 1, "Expected exactly one persisted check-in");
  assert(grants.length === 1, "Expected exactly one fragment grant");
  assert(audits.length === 1, "Expected exactly one audit record");
  assert(Boolean(challenges[0]?.used_at), "Challenge was not consumed");

  console.log(
    JSON.stringify(
      {
        requestIds: {
          progressBefore: progressBefore.requestId,
          challenge: challenge.requestId,
          firstPhoto: firstPhoto.requestId,
          secondPhoto: secondPhoto.requestId,
          firstCheckIn: first.requestId,
          firstProgress: progressAfterFirst.requestId,
          replay: replay.requestId,
          replayProgress: progressAfterReplay.requestId,
          conflict: conflict.requestId,
          duplicate: duplicate.requestId,
          duplicateProgress: progressAfterDuplicate.requestId,
        },
        landmarkId: landmark.id,
        challengeId,
        photoIds: photoObjects.map((photo) => photo.photoId),
        objectKeys: photoObjects.map((photo) => photo.objectKey),
        checkInId,
        firstStatus: first.body.data.status,
        replayStatus: replay.body.data.status,
        duplicateStatus: duplicate.body.data.status,
        conflictCode: conflict.body.error.code,
        progress: {
          before: progressBefore.count,
          afterFirst: progressAfterFirst.count,
          afterReplay: progressAfterReplay.count,
          afterDuplicate: progressAfterDuplicate.count,
        },
        challengeUsed: true,
        databaseCounts: {
          checkins: checkIns.length,
          grants: grants.length,
          audits: audits.length,
        },
      },
      null,
      2,
    ),
  );
} finally {
  await cleanup();
  const leftovers = await Promise.all([
    checkInId ? selectRows("checkins", { id: `eq.${checkInId}` }) : [],
    checkInId ? selectRows("fragment_grants", { check_in_id: `eq.${checkInId}` }) : [],
    checkInId ? selectRows("audit_logs", { entity_id: `eq.${checkInId}` }) : [],
    challengeId ? selectRows("challenges", { id: `eq.${challengeId}` }) : [],
    ...photoObjects.map((photo) => selectRows("photos", { id: `eq.${photo.photoId}` })),
  ]);
  assert(
    leftovers.every((rows) => rows.length === 0),
    "Live regression cleanup left database rows behind",
  );
}

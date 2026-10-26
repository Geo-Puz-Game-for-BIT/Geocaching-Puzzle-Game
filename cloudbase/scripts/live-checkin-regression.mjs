import { randomUUID } from "node:crypto";
import { Buffer } from "node:buffer";

import {
  deleteRows,
  selectRows,
} from "../functions/api/db.ts";

const apiBase =
  process.env.LIVE_API_BASE ??
  "https://cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com/api/v1";
const userId = process.env.DEMO_USER_ID;
const authToken = process.env.DEMO_AUTH_TOKEN;

// 1x1 synthetic JPEG used only to exercise the real multipart/binary upload path.
const syntheticJpeg = Buffer.from(
  "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/EH//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/EH//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/EH//2Q==",
  "base64",
);

if (!userId || !authToken) {
  throw new Error("DEMO_USER_ID and DEMO_AUTH_TOKEN are required");
}

const idempotencyKey = `live-${randomUUID()}`;
let challengeId = "";
let firstPhotoId = "";
let secondPhotoId = "";
let checkInId = "";

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function jsonRequest(path, init = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${authToken}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  return { response, body: await response.json() };
}

async function uploadPhoto() {
  const response = await fetch(`${apiBase}/checkin-photos`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${authToken}`,
      "Content-Type": "image/jpeg",
      "X-Request-Id": randomUUID(),
    },
    body: syntheticJpeg,
  });
  const body = await response.json();
  assert(response.status === 200, `Photo upload failed: ${response.status}`);
  return body.data.photoId;
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

async function cleanup() {
  if (checkInId) {
    await deleteRows("audit_logs", { entity_id: `eq.${checkInId}` });
    await deleteRows("fragment_grants", { check_in_id: `eq.${checkInId}` });
    await deleteRows("checkins", { id: `eq.${checkInId}` });
  }
  if (challengeId) {
    await deleteRows("challenges", { id: `eq.${challengeId}` });
  }
  if (firstPhotoId) {
    await deleteRows("photos", { id: `eq.${firstPhotoId}` });
  }
  if (secondPhotoId) {
    await deleteRows("photos", { id: `eq.${secondPhotoId}` });
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

  const challengeResponse = await jsonRequest("/checkin-challenges", {
    method: "POST",
    headers: { "X-Request-Id": randomUUID() },
    body: JSON.stringify({ landmarkId: landmark.id }),
  });
  assert(challengeResponse.response.status === 200, "Challenge creation failed");
  challengeId = challengeResponse.body.data.id;

  firstPhotoId = await uploadPhoto();
  secondPhotoId = await uploadPhoto();

  const firstBody = checkInBody(landmark, challengeId, firstPhotoId);
  const first = await jsonRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(firstBody),
  });
  assert(first.response.status === 200, "First check-in failed");
  checkInId = first.body.data.checkInId;

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
        secondPhotoId,
        new Date(Date.now() + 1_000).toISOString(),
      ),
    ),
  });
  assert(
    conflict.response.status === 409 &&
      conflict.body.error?.code === "IDEMPOTENCY_CONFLICT",
    "Changed photo did not return IDEMPOTENCY_CONFLICT",
  );

  const duplicateBody = checkInBody(
    landmark,
    challengeId,
    firstPhotoId,
    new Date(Date.now() + 2_000).toISOString(),
  );
  const duplicate = await jsonRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": `live-duplicate-${randomUUID()}`,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(duplicateBody),
  });
  assert(
    duplicate.response.status === 200 &&
      duplicate.body.data.checkInId === checkInId &&
      duplicate.body.data.status === "ALREADY_COMPLETED",
    "New-key duplicate did not return ALREADY_COMPLETED",
  );

  const [checkIns, grants, audits, challenges, photos] = await Promise.all([
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
    selectRows("photos", {
      select: "id",
      user_id: `eq.${userId}`,
    }),
  ]);

  assert(checkIns.length === 1, "Expected exactly one persisted check-in");
  assert(grants.length === 1, "Expected exactly one fragment grant");
  assert(audits.length === 1, "Expected exactly one audit record");
  assert(Boolean(challenges[0]?.used_at), "Challenge was not consumed");
  assert(
    photos.some((photo) => photo.id === firstPhotoId),
    "First uploaded photo row is missing",
  );

  console.log(
    JSON.stringify(
      {
        requestIds: {
          challenge: challengeResponse.body.requestId,
          first: first.body.requestId,
          replay: replay.body.requestId,
          conflict: conflict.body.requestId,
          duplicate: duplicate.body.requestId,
        },
        landmarkId: landmark.id,
        challengeId,
        photoIds: [firstPhotoId, secondPhotoId],
        checkInId,
        firstStatus: first.body.data.status,
        replayStatus: replay.body.data.status,
        duplicateStatus: duplicate.body.data.status,
        conflictCode: conflict.body.error.code,
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
    firstPhotoId ? selectRows("photos", { id: `eq.${firstPhotoId}` }) : [],
    secondPhotoId ? selectRows("photos", { id: `eq.${secondPhotoId}` }) : [],
  ]);
  assert(
    leftovers.every((rows) => rows.length === 0),
    "Live regression cleanup left rows behind",
  );
}

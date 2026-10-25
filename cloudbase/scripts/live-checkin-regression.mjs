import { randomUUID } from "node:crypto";

import {
  deleteRows,
  insertRow,
  selectRows,
} from "../functions/api/db.ts";

const apiBase =
  process.env.LIVE_API_BASE ??
  "https://cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com/api/v1";
const userId = process.env.DEMO_USER_ID;
const authToken = process.env.DEMO_AUTH_TOKEN;

if (!userId || !authToken) {
  throw new Error("DEMO_USER_ID and DEMO_AUTH_TOKEN are required");
}

const suffix = randomUUID().replaceAll("-", "").slice(0, 12);
const firstPhotoId = `pho_live_${suffix}_a`;
const secondPhotoId = `pho_live_${suffix}_b`;
const challengeId = `chl_live_${suffix}`;
const idempotencyKey = `live-${randomUUID()}`;
let checkInId = "";

async function apiRequest(path, init = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${authToken}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  const body = await response.json();
  return { response, body };
}

function requestBody(landmark, photoId) {
  return {
    landmarkId: landmark.id,
    challengeId,
    photoId,
    location: {
      point: {
        latitude: landmark.latitude,
        longitude: landmark.longitude,
      },
      accuracyMeters: 8,
      capturedAt: new Date().toISOString(),
    },
  };
}

async function cleanup() {
  if (checkInId) {
    await deleteRows("audit_logs", { entity_id: `eq.${checkInId}` });
    await deleteRows("fragment_grants", { check_in_id: `eq.${checkInId}` });
    await deleteRows("checkins", { id: `eq.${checkInId}` });
  }
  await deleteRows("challenges", { id: `eq.${challengeId}` });
  await deleteRows("photos", { id: `eq.${firstPhotoId}` });
  await deleteRows("photos", { id: `eq.${secondPhotoId}` });
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
  if (!landmark) {
    throw new Error("No unused active landmark is available for live regression");
  }
  for (const photoId of [firstPhotoId, secondPhotoId]) {
    await insertRow("photos", {
      id: photoId,
      user_id: userId,
      object_key: `checkins/${userId}/${photoId}.jpg`,
      mime_type: "image/jpeg",
      size_bytes: 3,
    });
  }
  await insertRow("challenges", {
    id: challengeId,
    user_id: userId,
    landmark_id: landmark.id,
    expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
  });

  const firstBody = requestBody(landmark, firstPhotoId);
  const first = await apiRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(firstBody),
  });
  if (first.response.status !== 200) {
    throw new Error(`First check-in failed: ${first.response.status} ${JSON.stringify(first.body)}`);
  }
  checkInId = first.body.data.checkInId;

  const conflict = await apiRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify({
      ...requestBody(landmark, secondPhotoId),
      location: {
        ...requestBody(landmark, secondPhotoId).location,
        capturedAt: new Date(Date.now() + 1_000).toISOString(),
      },
    }),
  });
  if (
    conflict.response.status !== 409 ||
    conflict.body.error?.code !== "IDEMPOTENCY_CONFLICT"
  ) {
    throw new Error(`Expected idempotency conflict, got ${conflict.response.status}`);
  }

  const replay = await apiRequest("/checkins", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Request-Id": randomUUID(),
    },
    body: JSON.stringify(firstBody),
  });
  if (
    replay.response.status !== 200 ||
    replay.body.data.checkInId !== checkInId
  ) {
    throw new Error("Same-key replay did not return the original check-in");
  }

  const [checkIns, grants, audits, challenges] = await Promise.all([
    selectRows("checkins", { user_id: `eq.${userId}`, landmark_id: `eq.${landmark.id}` }),
    selectRows("fragment_grants", { check_in_id: `eq.${checkInId}` }),
    selectRows("audit_logs", {
      user_id: `eq.${userId}`,
      entity_id: `eq.${checkInId}`,
      event_type: "eq.CHECKIN_GRANTED",
    }),
    selectRows("challenges", {
      select: "used_at",
      id: `eq.${challengeId}`,
      limit: "1",
    }),
  ]);

  console.log(
    JSON.stringify(
      {
        requestIds: {
          first: first.body.requestId,
          conflict: conflict.body.requestId,
          replay: replay.body.requestId,
        },
        landmarkId: landmark.id,
        checkInId,
        firstStatus: first.body.data.status,
        replayStatus: replay.body.data.status,
        conflictCode: conflict.body.error.code,
        challengeUsed: Boolean(challenges[0]?.used_at),
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
}

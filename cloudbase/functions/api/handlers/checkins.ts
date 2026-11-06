import { createHash, randomUUID } from "node:crypto";

import { z } from "zod";

import {
  insertRow,
  insertRowIfAbsent,
  PostgrestError,
  selectRows,
  updateRows,
} from "../db.ts";
import { ApiError } from "../errors.ts";
import {
  assertWithinCheckInArea,
  distanceMeters,
  validateLocationAccuracy,
  validateLocationTime,
} from "../geo.ts";
import { requireActiveLandmark } from "./landmarks.ts";

const checkInSchema = z.object({
  landmarkId: z.string().min(1),
  challengeId: z.string().min(1),
  photoId: z.string().min(1),
  location: z.object({
    point: z.object({
      latitude: z.number().min(-90).max(90),
      longitude: z.number().min(-180).max(180),
    }),
    accuracyMeters: z.number().nonnegative(),
    capturedAt: z.string().datetime(),
    source: z
      .enum(["GPS", "WIFI", "CELL", "NETWORK", "CACHE", "DEMO", "UNKNOWN"])
      .optional()
      .default("UNKNOWN"),
    elapsedRealtimeNanos: z.number().int().nonnegative().optional(),
    submittedElapsedRealtimeNanos: z.number().int().nonnegative().optional(),
  }),
});

interface CheckInRow {
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

interface TaskRow {
  fragment_id: string;
}

interface FragmentRow {
  id: string;
  puzzle_id: string;
  piece_index: number;
}

interface GrantRow {
  id: string;
  user_id: string;
  fragment_id: string;
  check_in_id: string;
  granted_at: string;
}

interface CheckInAttemptRow {
  id: string;
  user_id: string;
  request_hash: string;
  status: "PROCESSING" | "COMPLETED" | "FAILED" | "ABANDONED";
  check_in_id: string | null;
  failure_code: string | null;
  created_at: string;
  updated_at: string;
}

export interface CheckInReceipt {
  checkInId: string;
  status: "COMPLETED" | "ALREADY_COMPLETED";
  fragmentGrant: {
    fragmentId: string;
    puzzleId: string;
    pieceIndex: number;
    grantedAt: string;
  };
}

export type CheckInAttemptQueryResult =
  | { kind: "completed"; receipt: CheckInReceipt }
  | { kind: "processing" }
  | { kind: "not_found"; attemptId: string };

const PROCESSING_TIMEOUT_MS = 2 * 60 * 1000;

function requestHash(input: z.infer<typeof checkInSchema>): string {
  const canonical = JSON.stringify([
    input.landmarkId,
    input.challengeId,
    input.photoId,
    input.location.point.latitude,
    input.location.point.longitude,
    input.location.accuracyMeters,
    input.location.capturedAt,
    input.location.source,
    input.location.elapsedRealtimeNanos ?? null,
    input.location.submittedElapsedRealtimeNanos ?? null,
  ]);
  return createHash("sha256").update(canonical).digest("hex");
}

async function findCheckIn(
  field: "id" | "landmark_id" | "idempotency_key",
  userId: string,
  value: string,
): Promise<CheckInRow | null> {
  const rows = await selectRows<CheckInRow>("checkins", {
    select:
      "id,user_id,landmark_id,challenge_id,photo_id,distance_meters,accuracy_meters,idempotency_key,request_hash,request_id,created_at",
    user_id: `eq.${userId}`,
    [field]: `eq.${value}`,
    limit: "1",
  });
  return rows[0] ?? null;
}

async function findCheckInAttempt(
  userId: string,
  attemptId: string,
): Promise<CheckInAttemptRow | null> {
  const rows = await selectRows<CheckInAttemptRow>("checkin_attempts", {
    select:
      "id,user_id,request_hash,status,check_in_id,failure_code,created_at,updated_at",
    id: `eq.${attemptId}`,
    user_id: `eq.${userId}`,
    limit: "1",
  });
  return rows[0] ?? null;
}

async function beginCheckInAttempt(
  userId: string,
  attemptId: string,
  requestHashValue: string,
): Promise<CheckInAttemptRow> {
  let attempt = await findCheckInAttempt(userId, attemptId);
  if (!attempt) {
    const inserted = await insertRowIfAbsent<CheckInAttemptRow>("checkin_attempts", {
      id: attemptId,
      user_id: userId,
      request_hash: requestHashValue,
      status: "PROCESSING",
    });
    attempt = inserted ?? await findCheckInAttempt(userId, attemptId);
  }
  if (!attempt) {
    throw new ApiError(
      "IDEMPOTENCY_CONFLICT",
      "The attempt identifier is already owned by another user",
    );
  }
  if (attempt.status === "ABANDONED") {
    throw new ApiError(
      "ATTEMPT_SEALED",
      "This attempt was abandoned after a processing timeout; start a new attempt",
    );
  }
  if (attempt.request_hash !== requestHashValue) {
    throw new ApiError(
      "IDEMPOTENCY_CONFLICT",
      "The Idempotency-Key was already used with a different request",
    );
  }
  if (attempt.status === "PROCESSING") {
    const rows = await updateRows<CheckInAttemptRow>(
      "checkin_attempts",
      {
        id: `eq.${attemptId}`,
        user_id: `eq.${userId}`,
        status: "eq.PROCESSING",
      },
      { updated_at: new Date().toISOString() },
    );
    return rows[0] ?? { ...attempt, updated_at: new Date().toISOString() };
  }
  if (attempt.status === "FAILED") {
    const rows = await updateRows<CheckInAttemptRow>(
      "checkin_attempts",
      {
        id: `eq.${attemptId}`,
        user_id: `eq.${userId}`,
        status: "eq.FAILED",
      },
      {
        status: "PROCESSING",
        failure_code: null,
        updated_at: new Date().toISOString(),
      },
    );
    attempt = rows[0] ?? { ...attempt, status: "PROCESSING", failure_code: null };
  }
  return attempt;
}

async function markAttemptCompleted(
  userId: string,
  attemptId: string,
  checkInId: string,
): Promise<void> {
  await updateRows("checkin_attempts", {
    id: `eq.${attemptId}`,
    user_id: `eq.${userId}`,
  }, {
    status: "COMPLETED",
    check_in_id: checkInId,
    failure_code: null,
    updated_at: new Date().toISOString(),
  });
}

async function markAttemptFailed(
  userId: string,
  attemptId: string,
  failureCode: string,
): Promise<void> {
  try {
    await updateRows("checkin_attempts", {
      id: `eq.${attemptId}`,
      user_id: `eq.${userId}`,
      status: "eq.PROCESSING",
    }, {
      status: "FAILED",
      failure_code: failureCode,
      updated_at: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Unable to persist failed check-in attempt", {
      attemptId,
      failureCode,
      error,
    });
  }
}

async function abandonStaleAttempt(
  userId: string,
  attempt: CheckInAttemptRow,
): Promise<boolean> {
  const now = Date.now();
  const updatedAt = Date.parse(attempt.updated_at);
  if (
    attempt.status !== "PROCESSING" ||
    !Number.isFinite(updatedAt) ||
    now - updatedAt < PROCESSING_TIMEOUT_MS
  ) {
    return false;
  }
  const rows = await updateRows<CheckInAttemptRow>(
    "checkin_attempts",
    {
      id: `eq.${attempt.id}`,
      user_id: `eq.${userId}`,
      status: "eq.PROCESSING",
    },
    {
      status: "ABANDONED",
      failure_code: "PROCESSING_TIMEOUT",
      updated_at: new Date(now).toISOString(),
    },
  );
  return rows.length > 0;
}

async function getTask(landmarkId: string): Promise<TaskRow | null> {
  const rows = await selectRows<TaskRow>("tasks", {
    select: "fragment_id",
    landmark_id: `eq.${landmarkId}`,
    type: "eq.photo",
    limit: "1",
  });
  return rows[0] ?? null;
}

async function getFragment(fragmentId: string): Promise<FragmentRow | null> {
  const rows = await selectRows<FragmentRow>("fragments", {
    select: "id,puzzle_id,piece_index",
    id: `eq.${fragmentId}`,
    limit: "1",
  });
  return rows[0] ?? null;
}

export async function grantFragmentOnce(checkIn: CheckInRow): Promise<GrantRow> {
  const existing = await selectRows<GrantRow>("fragment_grants", {
    select: "id,user_id,fragment_id,check_in_id,granted_at",
    check_in_id: `eq.${checkIn.id}`,
    limit: "1",
  });
  if (existing[0]) {
    return existing[0];
  }

  const task = await getTask(checkIn.landmark_id);
  if (!task) {
    throw new ApiError("LANDMARK_NOT_FOUND", "An active photo task is not configured");
  }

  const inserted = await insertRowIfAbsent<GrantRow>("fragment_grants", {
    id: `grant_${randomUUID()}`,
    user_id: checkIn.user_id,
    fragment_id: task.fragment_id,
    check_in_id: checkIn.id,
  });
  if (inserted) {
    return inserted;
  }

  const afterInsert = await selectRows<GrantRow>("fragment_grants", {
    select: "id,user_id,fragment_id,check_in_id,granted_at",
    check_in_id: `eq.${checkIn.id}`,
    limit: "1",
  });
  if (afterInsert[0]) {
    return afterInsert[0];
  }

  throw new ApiError("INTERNAL_ERROR", "Unable to persist the fragment grant", {
    retryable: true,
  });
}

async function toReceipt(
  checkIn: CheckInRow,
  status: CheckInReceipt["status"],
  requestId: string,
  ensureAudit = true,
): Promise<CheckInReceipt> {
  const grant = await grantFragmentOnce(checkIn);
  const fragment = await getFragment(grant.fragment_id);
  if (!fragment) {
    throw new ApiError("INTERNAL_ERROR", "The granted fragment is missing");
  }

  const receipt: CheckInReceipt = {
    checkInId: checkIn.id,
    status,
    fragmentGrant: {
      fragmentId: fragment.id,
      puzzleId: fragment.puzzle_id,
      pieceIndex: fragment.piece_index,
      grantedAt: new Date(grant.granted_at).toISOString(),
    },
  };

  if (ensureAudit) {
    await ensureCheckInGrantedAudit(checkIn, receipt, requestId);
  }
  return receipt;
}

async function ensureCheckInGrantedAudit(
  checkIn: CheckInRow,
  receipt: CheckInReceipt,
  requestId: string,
): Promise<void> {
  const existing = await selectRows<{ id: number }>("audit_logs", {
    select: "id",
    event_type: "eq.CHECKIN_GRANTED",
    entity_id: `eq.${checkIn.id}`,
    limit: "1",
  });
  if (existing[0]) {
    return;
  }

  try {
    await insertRow("audit_logs", {
      request_id: requestId,
      event_type: "CHECKIN_GRANTED",
      user_id: checkIn.user_id,
      entity_id: checkIn.id,
      metadata: {
        landmarkId: checkIn.landmark_id,
        challengeId: checkIn.challenge_id,
        photoId: checkIn.photo_id,
        fragmentId: receipt.fragmentGrant.fragmentId,
        distanceMeters: checkIn.distance_meters,
        accuracyMeters: checkIn.accuracy_meters,
        recovered: true,
      },
    });
  } catch (error) {
    if (error instanceof PostgrestError && error.status === 409) {
      return;
    }
    console.error("Audit log write failed", { requestId, error });
    throw new ApiError(
      "AUDIT_WRITE_FAILED",
      "Check-in audit is not yet available; retry with the same Idempotency-Key",
      { retryable: true },
    );
  }
}

function translateChallengeError(error: unknown): void {
  if (
    error instanceof PostgrestError &&
    /ATTEMPT_SEALED/i.test(error.message)
  ) {
    throw new ApiError(
      "ATTEMPT_SEALED",
      "This attempt is sealed and cannot create a late check-in",
    );
  }
  if (
    error instanceof PostgrestError &&
    /CHALLENGE_EXPIRED/i.test(error.message)
  ) {
    throw new ApiError("CHALLENGE_EXPIRED", "Challenge is expired or already used", {
      retryable: true,
    });
  }
}

export async function queryCheckInAttempt(
  userId: string,
  attemptId: string,
  requestId: string,
): Promise<CheckInAttemptQueryResult> {
  if (!attemptId || attemptId.length > 128) {
    throw new ApiError("INVALID_REQUEST", "A valid attemptId is required");
  }

  const storedCheckIn = await findCheckIn("idempotency_key", userId, attemptId);
  const attempt = await findCheckInAttempt(userId, attemptId);
  if (storedCheckIn) {
    if (attempt?.status === "PROCESSING") {
      await markAttemptCompleted(userId, attemptId, storedCheckIn.id);
    }
    return {
      kind: "completed",
      receipt: await toReceipt(storedCheckIn, "COMPLETED", requestId, false),
    };
  }
  if (!attempt) {
    return { kind: "not_found", attemptId };
  }

  if (attempt.status === "PROCESSING") {
    if (await abandonStaleAttempt(userId, attempt)) {
      return { kind: "not_found", attemptId };
    }
    return { kind: "processing" };
  }
  if (
    attempt.status === "FAILED" ||
    attempt.status === "ABANDONED" ||
    !attempt.check_in_id
  ) {
    return { kind: "not_found", attemptId };
  }

  const checkIn = await findCheckIn("id", userId, attempt.check_in_id);
  if (!checkIn) {
    return { kind: "not_found", attemptId };
  }
  return {
    kind: "completed",
    receipt: await toReceipt(checkIn, "COMPLETED", requestId, false),
  };
}

export async function submitCheckIn(
  userId: string,
  requestId: string,
  idempotencyKey: string,
  body: unknown,
): Promise<CheckInReceipt> {
  const parsed = checkInSchema.safeParse(body);
  if (!parsed.success) {
    throw new ApiError("INVALID_REQUEST", "Check-in request is invalid", {
      details: parsed.error.flatten(),
    });
  }
  const input = parsed.data;
  const hash = requestHash(input);

  if (!idempotencyKey || idempotencyKey.length > 128) {
    throw new ApiError("INVALID_REQUEST", "A valid Idempotency-Key header is required");
  }

  const attempt = await beginCheckInAttempt(userId, idempotencyKey, hash);
  if (attempt.status === "COMPLETED" && attempt.check_in_id) {
    const completedAttempt = await findCheckIn("id", userId, attempt.check_in_id);
    if (completedAttempt) {
      return toReceipt(completedAttempt, "COMPLETED", requestId);
    }
  }

  try {
    const prior = await findCheckIn("idempotency_key", userId, idempotencyKey);
    if (prior) {
      if (prior.request_hash !== hash) {
        throw new ApiError(
          "IDEMPOTENCY_CONFLICT",
          "The Idempotency-Key was already used with a different request",
        );
      }
      await markAttemptCompleted(userId, idempotencyKey, prior.id);
      return toReceipt(prior, "COMPLETED", requestId);
    }

    const completed = await findCheckIn("landmark_id", userId, input.landmarkId);
    if (completed) {
      await markAttemptCompleted(userId, idempotencyKey, completed.id);
      return toReceipt(completed, "ALREADY_COMPLETED", requestId);
    }

    const photoRows = await selectRows<{
      id: string;
      challenge_id: string | null;
      attempt_id: string | null;
      deleted_at: string | null;
    }>("photos", {
      select: "id,challenge_id,attempt_id,deleted_at",
      id: `eq.${input.photoId}`,
      user_id: `eq.${userId}`,
      limit: "1",
    });
    const photo = photoRows[0];
    if (!photo || photo.deleted_at) {
      throw new ApiError("PHOTO_REQUIRED", "A valid uploaded photo is required");
    }
    if (
      photo.challenge_id !== input.challengeId ||
      photo.attempt_id !== idempotencyKey
    ) {
      throw new ApiError(
        "PHOTO_INVALID",
        "The photo does not belong to this attempt and challenge",
      );
    }

    validateLocationTime(input.location);
    validateLocationAccuracy(input.location.accuracyMeters);

    const landmark = await requireActiveLandmark(input.landmarkId);
    const distance = distanceMeters(input.location.point, {
      latitude: landmark.latitude,
      longitude: landmark.longitude,
    });
    const checkInArea =
      landmark.check_in_area ?? {
        type: "circle" as const,
        radiusMeters: landmark.check_in_radius_meters,
      };
    assertWithinCheckInArea(
      {
        latitude: landmark.latitude,
        longitude: landmark.longitude,
      },
      input.location.point,
      input.location.accuracyMeters,
      checkInArea,
    );

    let checkIn: CheckInRow;
    try {
      checkIn = await insertRow<CheckInRow>("checkins", {
        id: `chk_${randomUUID()}`,
        user_id: userId,
        landmark_id: input.landmarkId,
        challenge_id: input.challengeId,
        photo_id: input.photoId,
        distance_meters: distance,
        accuracy_meters: input.location.accuracyMeters,
        idempotency_key: idempotencyKey,
        request_hash: hash,
        request_id: requestId,
        location_source: input.location.source,
        captured_at: input.location.capturedAt,
        elapsed_realtime_nanos: input.location.elapsedRealtimeNanos ?? null,
      });
    } catch (error) {
      const sameKey = await findCheckIn("idempotency_key", userId, idempotencyKey);
      if (sameKey) {
        if (sameKey.request_hash !== hash) {
          throw new ApiError(
            "IDEMPOTENCY_CONFLICT",
            "The Idempotency-Key was already used with a different request",
          );
        }
        await markAttemptCompleted(userId, idempotencyKey, sameKey.id);
        return toReceipt(sameKey, "COMPLETED", requestId);
      }

      const sameLandmark = await findCheckIn("landmark_id", userId, input.landmarkId);
      if (sameLandmark) {
        await markAttemptCompleted(userId, idempotencyKey, sameLandmark.id);
        return toReceipt(sameLandmark, "ALREADY_COMPLETED", requestId);
      }

      translateChallengeError(error);
      throw error;
    }

    await markAttemptCompleted(userId, idempotencyKey, checkIn.id);
    return toReceipt(checkIn, "COMPLETED", requestId);
  } catch (error) {
    if (error instanceof ApiError) {
      await markAttemptFailed(userId, idempotencyKey, error.code);
    }
    throw error;
  }
}

import { randomUUID } from "node:crypto";

import { z } from "zod";

import { insertRow } from "../db.ts";
import { ApiError } from "../errors.ts";
import { requireActiveLandmark } from "./landmarks.ts";

const CHALLENGE_TTL_MS = 5 * 60 * 1000;

const createChallengeSchema = z.object({
  landmarkId: z.string().min(1),
});

export async function createCheckInChallenge(
  userId: string,
  attemptId: string,
  body: unknown,
) {
  const input = createChallengeSchema.safeParse(body);
  if (!input.success) {
    throw new ApiError("INVALID_REQUEST", "landmarkId is required");
  }
  if (!attemptId || attemptId.length > 128) {
    throw new ApiError("INVALID_REQUEST", "A valid attemptId is required");
  }

  await requireActiveLandmark(input.data.landmarkId);

  const id = `chl_${randomUUID()}`;
  const expiresAt = new Date(Date.now() + CHALLENGE_TTL_MS);
  await insertRow("challenges", {
    id,
    user_id: userId,
    landmark_id: input.data.landmarkId,
    attempt_id: attemptId,
    expires_at: expiresAt.toISOString(),
  });

  return {
    id,
    landmarkId: input.data.landmarkId,
    attemptId,
    expiresAt: expiresAt.toISOString(),
  };
}

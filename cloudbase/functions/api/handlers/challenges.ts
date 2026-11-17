import { randomUUID } from "node:crypto";

import { z } from "zod";

import { insertRow } from "../db.ts";
import { ApiError } from "../errors.ts";
import { requireActiveLandmark } from "./landmarks.ts";

const CHALLENGE_TTL_MS = 5 * 60 * 1000;

const createChallengeSchema = z.object({
  landmarkId: z.string().min(1),
});

export async function createCheckInChallenge(userId: string, body: unknown) {
  const input = createChallengeSchema.safeParse(body);
  if (!input.success) {
    throw new ApiError("INVALID_REQUEST", "landmarkId is required");
  }

  await requireActiveLandmark(input.data.landmarkId);

  const id = `chl_${randomUUID()}`;
  const expiresAt = new Date(Date.now() + CHALLENGE_TTL_MS);
  await insertRow("challenges", {
    id,
    user_id: userId,
    landmark_id: input.data.landmarkId,
    expires_at: expiresAt.toISOString(),
  });

  return {
    id,
    landmarkId: input.data.landmarkId,
    expiresAt: expiresAt.toISOString(),
  };
}

import { timingSafeEqual } from "node:crypto";
import type { IncomingMessage } from "node:http";

import { requiredEnv } from "./config.ts";
import { ApiError } from "./errors.ts";

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
}

export function requireUserId(request: IncomingMessage): string {
  const authorization = request.headers.authorization;
  const expectedToken = requiredEnv("DEMO_AUTH_TOKEN");
  const expected = `Bearer ${expectedToken}`;

  if (typeof authorization !== "string" || !safeEqual(authorization, expected)) {
    throw new ApiError("AUTH_REQUIRED", "A valid bearer token is required");
  }

  return requiredEnv("DEMO_USER_ID");
}

import { describe, expect, it } from "vitest";

import { ApiError } from "../functions/api/errors.ts";
import {
  assertWithinRadius,
  validateLocationAccuracy,
  validateLocationTime,
} from "../functions/api/geo.ts";
import { statusForErrorCode, toApiError } from "../functions/api/http.ts";
import { buildPhotoObjectKey } from "../functions/api/storage.ts";

describe("backend contract helpers", () => {
  it("givenInRange_whenAssertWithinRadius_thenAccepts", () => {
    expect(() => assertWithinRadius(90, 100, 10)).not.toThrow();
  });

  it("givenOutOfRange_whenAssertWithinRadius_thenReturnsOutOfRange", () => {
    expect(() => assertWithinRadius(101, 100, 10)).toThrowError(
      expect.objectContaining({ code: "OUT_OF_RANGE", status: 422 }),
    );
  });

  it("givenLowAccuracy_whenValidateLocationAccuracy_thenRejects", () => {
    expect(() => validateLocationAccuracy(31)).toThrowError(
      expect.objectContaining({ code: "LOCATION_ACCURACY_TOO_LOW", status: 422 }),
    );
  });

  it("givenThirtyMeterAccuracy_whenValidateLocationAccuracy_thenAccepts", () => {
    expect(() => validateLocationAccuracy(30)).not.toThrow();
  });

  it("givenStaleLocation_whenValidateLocationTime_thenRejects", () => {
    const now = new Date("2026-11-04T08:00:31.000Z");
    expect(() =>
      validateLocationTime(
        {
          capturedAt: "2026-11-04T08:00:00.000Z",
        },
        now,
      ),
    ).toThrowError(expect.objectContaining({ code: "LOCATION_STALE", status: 422 }));
  });

  it("givenClockMismatch_whenValidateLocationTime_thenRejects", () => {
    const now = new Date("2026-11-04T08:00:10.000Z");
    expect(() =>
      validateLocationTime(
        {
          capturedAt: "2026-11-04T08:00:00.000Z",
          elapsedRealtimeNanos: 1_000_000_000,
          submittedElapsedRealtimeNanos: 20_000_000_000,
        },
        now,
      ),
    ).toThrowError(
      expect.objectContaining({ code: "LOCATION_CLOCK_INVALID", status: 422 }),
    );
  });

  it("givenPhotoIdentity_whenBuildPhotoObjectKey_thenUsesUserDirectory", () => {
    expect(buildPhotoObjectKey("usr_demo01", "pho_123")).toBe(
      "checkins/usr_demo01/pho_123.jpg",
    );
  });

  it("givenApiError_whenToApiError_thenKeepsContractFields", () => {
    const failure = toApiError(
      new ApiError("CHALLENGE_EXPIRED", "expired"),
      "123e4567-e89b-42d3-a456-426614174000",
    );
    expect(failure).toEqual({
      requestId: "123e4567-e89b-42d3-a456-426614174000",
      error: {
        code: "CHALLENGE_EXPIRED",
        message: "expired",
        retryable: false,
      },
    });
    expect(statusForErrorCode(failure.error.code)).toBe(409);
  });

  it("givenAuditWriteFailure_whenMapStatus_thenReturnsServiceUnavailable", () => {
    const failure = toApiError(
      new ApiError("AUDIT_WRITE_FAILED", "audit unavailable", {
        retryable: true,
      }),
      "123e4567-e89b-42d3-a456-426614174000",
    );
    expect(failure.error.code).toBe("AUDIT_WRITE_FAILED");
    expect(failure.error.retryable).toBe(true);
    expect(statusForErrorCode(failure.error.code)).toBe(503);
  });
});

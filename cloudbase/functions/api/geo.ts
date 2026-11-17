import { ApiError } from "./errors.ts";

const EARTH_RADIUS_METERS = 6_371_008.8;
export const MAX_CHECK_IN_ACCURACY_METERS = 30;
export const MAX_CHECK_IN_LOCATION_AGE_MS = 30_000;
export const MAX_LOCATION_FUTURE_SKEW_MS = 5_000;
export const MAX_CLOCK_CROSS_CHECK_DELTA_MS = 5_000;

export interface LocalOffsetMeters {
  eastMeters: number;
  northMeters: number;
}

export interface CircularCheckInArea {
  type: "circle";
  radiusMeters: number;
}

export interface PolygonCheckInArea {
  type: "polygon";
  vertices: LocalOffsetMeters[];
}

export type CheckInArea = CircularCheckInArea | PolygonCheckInArea;

function toRadians(value: number): number {
  return (value * Math.PI) / 180;
}

export function distanceMeters(
  from: { latitude: number; longitude: number },
  to: { latitude: number; longitude: number },
): number {
  const latitudeDelta = toRadians(to.latitude - from.latitude);
  const longitudeDelta = toRadians(to.longitude - from.longitude);
  const fromLatitude = toRadians(from.latitude);
  const toLatitude = toRadians(to.latitude);

  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.sin(longitudeDelta / 2) ** 2;

  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(haversine)));
}

export function toLocalOffsetMeters(
  origin: { latitude: number; longitude: number },
  target: { latitude: number; longitude: number },
): LocalOffsetMeters {
  const latitudeDelta = toRadians(target.latitude - origin.latitude);
  const longitudeDelta = toRadians(target.longitude - origin.longitude);
  return {
    eastMeters:
      longitudeDelta * EARTH_RADIUS_METERS * Math.cos(toRadians(origin.latitude)),
    northMeters: latitudeDelta * EARTH_RADIUS_METERS,
  };
}

function isPointInsidePolygon(
  point: LocalOffsetMeters,
  vertices: LocalOffsetMeters[],
): boolean {
  let inside = false;
  for (let index = 0, previous = vertices.length - 1; index < vertices.length; previous = index++) {
    const currentVertex = vertices[index];
    const previousVertex = vertices[previous];
    if (!currentVertex || !previousVertex) {
      continue;
    }

    const intersects =
      currentVertex.northMeters > point.northMeters !==
        previousVertex.northMeters > point.northMeters &&
      point.eastMeters <
        ((previousVertex.eastMeters - currentVertex.eastMeters) *
          (point.northMeters - currentVertex.northMeters)) /
          (previousVertex.northMeters - currentVertex.northMeters) +
          currentVertex.eastMeters;

    if (intersects) {
      inside = !inside;
    }
  }
  return inside;
}

export function isWithinCheckInArea(
  origin: { latitude: number; longitude: number },
  point: { latitude: number; longitude: number },
  area: CheckInArea,
): boolean {
  if (area.type === "circle") {
    return distanceMeters(origin, point) <= area.radiusMeters;
  }

  return isPointInsidePolygon(toLocalOffsetMeters(origin, point), area.vertices);
}

export function isWithinRadius(
  distance: number,
  radiusMeters: number,
  accuracyMeters: number,
): boolean {
  return distance <= radiusMeters && accuracyMeters <= MAX_CHECK_IN_ACCURACY_METERS;
}

export function validateLocationAccuracy(accuracyMeters: number): void {
  if (accuracyMeters > MAX_CHECK_IN_ACCURACY_METERS) {
    throw new ApiError("LOCATION_ACCURACY_TOO_LOW", "Location accuracy must be 30m or better", {
      details: { accuracyMeters },
    });
  }
}

export interface LocationTimeEvidence {
  capturedAt: string;
  elapsedRealtimeNanos?: number;
  submittedElapsedRealtimeNanos?: number;
}

export function validateLocationTime(
  location: LocationTimeEvidence,
  now: Date = new Date(),
): void {
  const capturedAtMs = Date.parse(location.capturedAt);
  if (!Number.isFinite(capturedAtMs)) {
    throw new ApiError("LOCATION_CLOCK_INVALID", "Captured time must be a valid UTC timestamp");
  }

  const wallAgeMs = now.getTime() - capturedAtMs;
  if (wallAgeMs < -MAX_LOCATION_FUTURE_SKEW_MS) {
    throw new ApiError("LOCATION_CLOCK_INVALID", "Location timestamp is too far in the future", {
      details: { wallAgeMs },
    });
  }
  if (wallAgeMs > MAX_CHECK_IN_LOCATION_AGE_MS) {
    throw new ApiError("LOCATION_STALE", "Location sample is stale", {
      details: {
        wallAgeMs,
        maxAgeMs: MAX_CHECK_IN_LOCATION_AGE_MS,
      },
    });
  }

  const hasCapturedElapsed = location.elapsedRealtimeNanos !== undefined;
  const hasSubmittedElapsed = location.submittedElapsedRealtimeNanos !== undefined;
  if (hasCapturedElapsed !== hasSubmittedElapsed) {
    throw new ApiError(
      "LOCATION_CLOCK_INVALID",
      "Both captured and submitted monotonic timestamps are required together",
    );
  }
  if (!hasCapturedElapsed || !hasSubmittedElapsed) {
    return;
  }

  const monotonicAgeMs =
    (Number(location.submittedElapsedRealtimeNanos) -
      Number(location.elapsedRealtimeNanos)) /
    1_000_000;
  const clockDeltaMs = Math.abs(wallAgeMs - monotonicAgeMs);
  if (
    monotonicAgeMs < 0 ||
    monotonicAgeMs > MAX_CHECK_IN_LOCATION_AGE_MS ||
    clockDeltaMs > MAX_CLOCK_CROSS_CHECK_DELTA_MS
  ) {
    throw new ApiError(
      "LOCATION_CLOCK_INVALID",
      "Device wall clock and monotonic interval are inconsistent",
      {
        details: {
          wallAgeMs,
          monotonicAgeMs,
          clockDeltaMs,
        },
      },
    );
  }
}

export function assertWithinRadius(
  distance: number,
  radiusMeters: number,
  accuracyMeters: number,
): void {
  if (!isWithinRadius(distance, radiusMeters, accuracyMeters)) {
    throw new ApiError("OUT_OF_RANGE", "Current location is outside the check-in area", {
      details: {
        distanceMeters: distance,
        radiusMeters,
      },
    });
  }
}

export function assertWithinCheckInArea(
  origin: { latitude: number; longitude: number },
  point: { latitude: number; longitude: number },
  accuracyMeters: number,
  area: CheckInArea,
): void {
  if (
    accuracyMeters > MAX_CHECK_IN_ACCURACY_METERS ||
    !isWithinCheckInArea(origin, point, area)
  ) {
    throw new ApiError("OUT_OF_RANGE", "Current location is outside the check-in area", {
      details: {
        distanceMeters: distanceMeters(origin, point),
        checkInArea: area,
      },
    });
  }
}

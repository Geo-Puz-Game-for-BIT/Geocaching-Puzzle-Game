import { ApiError } from "./errors.ts";

const EARTH_RADIUS_METERS = 6_371_008.8;

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
  return distance <= radiusMeters && accuracyMeters <= 50;
}

export function validateLocationAccuracy(accuracyMeters: number): void {
  if (accuracyMeters > 50) {
    throw new ApiError("LOCATION_ACCURACY_TOO_LOW", "Location accuracy must be 50m or better", {
      details: { accuracyMeters },
    });
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
  if (accuracyMeters > 50 || !isWithinCheckInArea(origin, point, area)) {
    throw new ApiError("OUT_OF_RANGE", "Current location is outside the check-in area", {
      details: {
        distanceMeters: distanceMeters(origin, point),
        checkInArea: area,
      },
    });
  }
}

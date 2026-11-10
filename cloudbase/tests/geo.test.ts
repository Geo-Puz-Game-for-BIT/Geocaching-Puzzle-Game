import { describe, expect, it } from "vitest";

import {
  distanceMeters,
  isWithinCheckInArea,
  isWithinRadius,
  toLocalOffsetMeters,
} from "../functions/api/geo.ts";

describe("distanceMeters", () => {
  it("givenSamePoint_whenCalculateDistance_thenReturnsZero", () => {
    const point = { latitude: 39.9592, longitude: 116.3083 };
    expect(distanceMeters(point, point)).toBe(0);
  });

  it("givenNearbyPoints_whenCalculateDistance_thenReturnsExpectedScale", () => {
    const distance = distanceMeters(
      { latitude: 39.9592, longitude: 116.3083 },
      { latitude: 39.9602, longitude: 116.3083 },
    );
    expect(distance).toBeGreaterThan(100);
    expect(distance).toBeLessThan(120);
  });
});

describe("isWithinRadius", () => {
  it("givenAccurateInRangeSample_whenCheckRadius_thenReturnsTrue", () => {
    expect(isWithinRadius(40, 100, 10)).toBe(true);
  });

  it("givenInaccurateSample_whenCheckRadius_thenReturnsFalse", () => {
    expect(isWithinRadius(40, 100, 51)).toBe(false);
  });
});

describe("CheckInArea", () => {
  const origin = { latitude: 39.7366, longitude: 116.1666 };

  it("givenCircleArea_whenPointIsInside_thenReturnsTrue", () => {
    expect(
      isWithinCheckInArea(origin, origin, {
        type: "circle",
        radiusMeters: 55,
      }),
    ).toBe(true);
  });

  it("givenCircleArea_whenPointIsOutside_thenReturnsFalse", () => {
    const point = { latitude: 39.7372, longitude: 116.1666 };
    expect(
      isWithinCheckInArea(origin, point, {
        type: "circle",
        radiusMeters: 55,
      }),
    ).toBe(false);
  });

  it("givenPolygonArea_whenPointIsInside_thenReturnsTrue", () => {
    const point = {
      latitude: 39.7367,
      longitude: 116.1667,
    };
    const offset = toLocalOffsetMeters(origin, point);
    expect(offset.eastMeters).toBeGreaterThan(0);
    expect(offset.northMeters).toBeGreaterThan(0);
    expect(
      isWithinCheckInArea(origin, point, {
        type: "polygon",
        vertices: [
          { eastMeters: -20, northMeters: -20 },
          { eastMeters: 20, northMeters: -20 },
          { eastMeters: 20, northMeters: 20 },
          { eastMeters: -20, northMeters: 20 },
        ],
      }),
    ).toBe(true);
  });
});

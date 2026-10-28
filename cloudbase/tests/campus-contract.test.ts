import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

import {
  validateCampusContract,
  type CampusCatalogFixture,
} from "../scripts/campus-contract.ts";

const fixture = JSON.parse(
  readFileSync(
    new URL("../../docs/api/fixtures/campus-catalog.json", import.meta.url),
    "utf8",
  ),
) as CampusCatalogFixture;
const androidCatalog = readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/bitse/geotreasure/core/model/LiangxiangTreasureCatalog.kt",
    import.meta.url,
  ),
  "utf8",
);

function mutatedFixture(): CampusCatalogFixture {
  return structuredClone(fixture);
}

describe("canonical campus contract", () => {
  it("accepts the confirmed 12-piece fixture", () => {
    expect(() => validateCampusContract(mutatedFixture(), androidCatalog)).not.toThrow();
  });

  it("rejects swapped piece indexes", () => {
    const invalid = mutatedFixture();
    const north = invalid.sections[0];
    [north.fragments[0].pieceIndex, north.fragments[1].pieceIndex] = [
      north.fragments[1].pieceIndex,
      north.fragments[0].pieceIndex,
    ];

    expect(() => validateCampusContract(invalid, androidCatalog)).toThrow(/pieceIndex/i);
  });

  it("rejects a fragment in the wrong campus section", () => {
    const invalid = mutatedFixture();
    invalid.sections[0].campusZone = "EAST";

    expect(() => validateCampusContract(invalid, androidCatalog)).toThrow(/campusZone|section/i);
  });

  it("rejects mismatched landmark, fragment, or task IDs", () => {
    const invalid = mutatedFixture();
    invalid.sections[0].fragments[0].landmarkId = "lmk_other";
    invalid.sections[0].fragments[0].id = "fragment_other";
    invalid.sections[0].fragments[0].taskId = "task_other";

    expect(() => validateCampusContract(invalid, androidCatalog)).toThrow(/landmarkId|fragment|task/i);
  });

  it("rejects coordinates that do not match the Android baseline", () => {
    const invalid = mutatedFixture();
    invalid.landmarks[0].latitude = 0;

    expect(() => validateCampusContract(invalid, androidCatalog)).toThrow(/latitude|coordinate/i);
  });
});

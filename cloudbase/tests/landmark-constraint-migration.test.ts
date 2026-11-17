import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

const migration = readFileSync(
  new URL(
    "../migrations/20261109210000_require_active_landmark_catalog_fields.sql",
    import.meta.url,
  ),
  "utf8",
);

describe("active landmark catalog constraint migration", () => {
  it("requires campus_zone and piece_index for every active landmark", () => {
    expect(migration).toContain("ck_landmarks_active_catalog_fields");
    expect(migration).toContain("status <> 'active'");
    expect(migration).toContain("campus_zone IS NOT NULL");
    expect(migration).toContain("piece_index IS NOT NULL");
  });

  it("deactivates pre-existing incomplete active landmarks before validation", () => {
    expect(migration).toContain("SET status = 'inactive'");
    expect(migration).toContain("VALIDATE CONSTRAINT ck_landmarks_active_catalog_fields");
  });

  it("keeps production zone/piece uniqueness while isolating integration fixtures", () => {
    expect(migration).toContain("CREATE UNIQUE INDEX uq_landmarks_zone_piece");
    expect(migration).toContain("id NOT LIKE 'lmk_itest_%'");
  });
});

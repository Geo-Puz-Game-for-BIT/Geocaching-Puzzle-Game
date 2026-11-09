BEGIN;

UPDATE landmarks
SET status = 'inactive'
WHERE status = 'active'
  AND (
    campus_zone IS NULL OR
    piece_index IS NULL
  );

ALTER TABLE landmarks
  DROP CONSTRAINT IF EXISTS ck_landmarks_active_catalog_fields;

ALTER TABLE landmarks
  ADD CONSTRAINT ck_landmarks_active_catalog_fields CHECK (
    status <> 'active' OR (
      campus_zone IS NOT NULL AND
      piece_index IS NOT NULL
    )
  ) NOT VALID;

ALTER TABLE landmarks
  VALIDATE CONSTRAINT ck_landmarks_active_catalog_fields;

DROP INDEX IF EXISTS uq_landmarks_zone_piece;

CREATE UNIQUE INDEX uq_landmarks_zone_piece
  ON landmarks (campus_zone, piece_index)
  WHERE campus_zone IS NOT NULL
    AND piece_index IS NOT NULL
    AND id NOT LIKE 'lmk_itest_%';

COMMIT;

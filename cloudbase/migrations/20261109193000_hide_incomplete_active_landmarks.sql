BEGIN;

UPDATE landmarks
SET status = 'inactive'
WHERE id LIKE 'lmk_itest_%'
  AND status = 'active';

ALTER TABLE landmarks
  DROP CONSTRAINT IF EXISTS ck_landmarks_active_catalog_complete;

ALTER TABLE landmarks
  ADD CONSTRAINT ck_landmarks_active_catalog_complete CHECK (
    status <> 'active' OR (
      coordinate_system = 'GCJ02'
      AND campus_zone IN ('NORTH', 'EAST', 'SOUTH')
      AND fragment_id IS NOT NULL
      AND piece_index BETWEEN 0 AND 3
    )
  ) NOT VALID;

ALTER TABLE landmarks
  VALIDATE CONSTRAINT ck_landmarks_active_catalog_complete;

COMMIT;

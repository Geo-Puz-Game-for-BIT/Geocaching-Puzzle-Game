ALTER TABLE landmarks
  ADD COLUMN IF NOT EXISTS difficulty smallint NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS terrain smallint NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS hint text;

ALTER TABLE landmarks
  DROP CONSTRAINT IF EXISTS ck_landmarks_difficulty,
  DROP CONSTRAINT IF EXISTS ck_landmarks_terrain;

ALTER TABLE landmarks
  ADD CONSTRAINT ck_landmarks_difficulty CHECK (difficulty BETWEEN 1 AND 5),
  ADD CONSTRAINT ck_landmarks_terrain CHECK (terrain BETWEEN 1 AND 5);

UPDATE landmarks
SET difficulty = 2,
    terrain = 1,
    hint = 'Look near the main entrance.'
WHERE id = 'lmk_library';

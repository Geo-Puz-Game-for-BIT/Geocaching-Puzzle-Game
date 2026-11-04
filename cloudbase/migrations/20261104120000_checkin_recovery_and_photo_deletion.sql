BEGIN;

CREATE TABLE IF NOT EXISTS checkin_attempts (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id),
  request_hash char(64) NOT NULL,
  status text NOT NULL DEFAULT 'PROCESSING',
  check_in_id text,
  failure_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_checkin_attempts_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_checkin_attempts_status CHECK (
    status IN ('PROCESSING', 'COMPLETED', 'FAILED')
  ),
  CONSTRAINT ck_checkin_attempts_completed CHECK (
    (status = 'COMPLETED' AND check_in_id IS NOT NULL) OR
    (status <> 'COMPLETED')
  ),
  CONSTRAINT fk_checkin_attempts_checkin_owner FOREIGN KEY (check_in_id, user_id)
    REFERENCES checkins(id, user_id)
);

ALTER TABLE photos
  ADD COLUMN IF NOT EXISTS challenge_id text REFERENCES challenges(id),
  ADD COLUMN IF NOT EXISTS attempt_id text,
  ADD COLUMN IF NOT EXISTS deleted_at timestamptz,
  ADD COLUMN IF NOT EXISTS delete_after timestamptz,
  ADD COLUMN IF NOT EXISTS storage_deleted_at timestamptz;

ALTER TABLE checkins
  ADD COLUMN IF NOT EXISTS location_source text NOT NULL DEFAULT 'UNKNOWN',
  ADD COLUMN IF NOT EXISTS captured_at timestamptz,
  ADD COLUMN IF NOT EXISTS elapsed_realtime_nanos bigint;

ALTER TABLE checkins
  ADD CONSTRAINT ck_checkins_location_source CHECK (
    location_source IN ('GPS', 'WIFI', 'CELL', 'NETWORK', 'CACHE', 'DEMO', 'UNKNOWN')
  ),
  ADD CONSTRAINT ck_checkins_elapsed_realtime CHECK (
    elapsed_realtime_nanos IS NULL OR elapsed_realtime_nanos >= 0
  );

ALTER TABLE photos
  ADD CONSTRAINT ck_photos_deletion_window CHECK (
    deleted_at IS NULL OR delete_after IS NOT NULL
  );

CREATE INDEX IF NOT EXISTS idx_checkin_attempts_user_status
  ON checkin_attempts (user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_photos_pending_cleanup
  ON photos (delete_after)
  WHERE deleted_at IS NOT NULL AND storage_deleted_at IS NULL;

INSERT INTO checkin_attempts (
  id,
  user_id,
  request_hash,
  status,
  check_in_id,
  created_at,
  updated_at
)
SELECT
  checkin.idempotency_key,
  checkin.user_id,
  checkin.request_hash,
  'COMPLETED',
  checkin.id,
  checkin.created_at,
  checkin.created_at
FROM checkins AS checkin
ON CONFLICT (id) DO NOTHING;

COMMIT;

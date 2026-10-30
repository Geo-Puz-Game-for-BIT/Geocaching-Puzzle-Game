CREATE TABLE IF NOT EXISTS users (
  id text PRIMARY KEY,
  display_name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_users_id_prefix CHECK (id LIKE 'usr\_%' ESCAPE '\')
);

CREATE TABLE IF NOT EXISTS puzzles (
  id text PRIMARY KEY,
  name text NOT NULL,
  rows smallint NOT NULL DEFAULT 3,
  columns smallint NOT NULL DEFAULT 3,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_puzzles_id_prefix CHECK (id LIKE 'pzl\_%' ESCAPE '\'),
  CONSTRAINT ck_puzzles_grid_size CHECK (rows > 0 AND columns > 0)
);

CREATE TABLE IF NOT EXISTS landmarks (
  id text PRIMARY KEY,
  name text NOT NULL,
  latitude double precision NOT NULL,
  longitude double precision NOT NULL,
  coordinate_system text NOT NULL DEFAULT 'GCJ02',
  check_in_radius_meters double precision NOT NULL,
  status text NOT NULL DEFAULT 'active',
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_landmarks_id_prefix CHECK (id LIKE 'lmk\_%' ESCAPE '\'),
  CONSTRAINT ck_landmarks_latitude CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_landmarks_longitude CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_landmarks_coordinate_system CHECK (coordinate_system = 'GCJ02'),
  CONSTRAINT ck_landmarks_radius CHECK (check_in_radius_meters > 0),
  CONSTRAINT ck_landmarks_status CHECK (status IN ('active', 'inactive'))
);

CREATE TABLE IF NOT EXISTS fragments (
  id text PRIMARY KEY,
  puzzle_id text NOT NULL REFERENCES puzzles(id),
  piece_index smallint NOT NULL,
  image_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_fragments_id_prefix CHECK (id LIKE 'frg\_%' ESCAPE '\'),
  CONSTRAINT ck_fragments_piece_index CHECK (piece_index BETWEEN 0 AND 8),
  CONSTRAINT uq_fragments_puzzle_piece UNIQUE (puzzle_id, piece_index),
  CONSTRAINT uq_fragments_image_key UNIQUE (image_key)
);

CREATE TABLE IF NOT EXISTS tasks (
  id text PRIMARY KEY,
  landmark_id text NOT NULL REFERENCES landmarks(id),
  type text NOT NULL,
  instructions text NOT NULL,
  puzzle_id text NOT NULL REFERENCES puzzles(id),
  fragment_id text NOT NULL REFERENCES fragments(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_tasks_id_prefix CHECK (id LIKE 'tsk\_%' ESCAPE '\'),
  CONSTRAINT ck_tasks_type CHECK (type = 'photo'),
  CONSTRAINT uq_tasks_landmark_type UNIQUE (landmark_id, type)
);

CREATE TABLE IF NOT EXISTS challenges (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id),
  landmark_id text NOT NULL REFERENCES landmarks(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  CONSTRAINT ck_challenges_id_prefix CHECK (id LIKE 'chl\_%' ESCAPE '\'),
  CONSTRAINT ck_challenges_expiry CHECK (expires_at > created_at),
  CONSTRAINT uq_challenges_owner UNIQUE (id, user_id, landmark_id)
);

CREATE TABLE IF NOT EXISTS photos (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id),
  object_key text NOT NULL,
  mime_type text NOT NULL,
  size_bytes bigint NOT NULL,
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_photos_id_prefix CHECK (id LIKE 'pho\_%' ESCAPE '\'),
  CONSTRAINT ck_photos_mime_type CHECK (mime_type = 'image/jpeg'),
  CONSTRAINT ck_photos_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
  CONSTRAINT ck_photos_object_key_user CHECK (
    object_key LIKE 'checkins/' || user_id || '/%'
  ),
  CONSTRAINT uq_photos_object_key UNIQUE (object_key),
  CONSTRAINT uq_photos_id_user UNIQUE (id, user_id)
);

CREATE TABLE IF NOT EXISTS checkins (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id),
  landmark_id text NOT NULL REFERENCES landmarks(id),
  challenge_id text NOT NULL,
  photo_id text NOT NULL,
  distance_meters double precision NOT NULL,
  accuracy_meters double precision NOT NULL,
  idempotency_key text NOT NULL,
  request_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_checkins_id_prefix CHECK (id LIKE 'chk\_%' ESCAPE '\'),
  CONSTRAINT ck_checkins_distance CHECK (distance_meters >= 0),
  CONSTRAINT ck_checkins_accuracy CHECK (accuracy_meters >= 0),
  CONSTRAINT ck_checkins_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT uq_checkins_user_landmark UNIQUE (user_id, landmark_id),
  CONSTRAINT uq_checkins_user_idempotency UNIQUE (user_id, idempotency_key),
  CONSTRAINT uq_checkins_challenge UNIQUE (challenge_id),
  CONSTRAINT uq_checkins_id_user UNIQUE (id, user_id),
  CONSTRAINT fk_checkins_challenge_owner FOREIGN KEY (
    challenge_id,
    user_id,
    landmark_id
  ) REFERENCES challenges(id, user_id, landmark_id),
  CONSTRAINT fk_checkins_photo_owner FOREIGN KEY (photo_id, user_id)
    REFERENCES photos(id, user_id)
);

CREATE TABLE IF NOT EXISTS fragment_grants (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id),
  fragment_id text NOT NULL REFERENCES fragments(id),
  check_in_id text NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_fragment_grants_id_prefix CHECK (id LIKE 'grant\_%' ESCAPE '\'),
  CONSTRAINT uq_fragment_grants_user_fragment UNIQUE (user_id, fragment_id),
  CONSTRAINT uq_fragment_grants_checkin UNIQUE (check_in_id),
  CONSTRAINT fk_fragment_grants_checkin_owner FOREIGN KEY (check_in_id, user_id)
    REFERENCES checkins(id, user_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id text NOT NULL,
  event_type text NOT NULL,
  user_id text,
  entity_id text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_audit_logs_event_type CHECK (event_type = upper(event_type))
);

CREATE INDEX IF NOT EXISTS idx_landmarks_status
  ON landmarks (status);

CREATE INDEX IF NOT EXISTS idx_tasks_landmark
  ON tasks (landmark_id);

CREATE INDEX IF NOT EXISTS idx_challenges_owner
  ON challenges (user_id, landmark_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_challenges_expiry
  ON challenges (expires_at)
  WHERE used_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_photos_user
  ON photos (user_id, uploaded_at DESC);

CREATE INDEX IF NOT EXISTS idx_checkins_landmark
  ON checkins (landmark_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_fragment_grants_user
  ON fragment_grants (user_id, granted_at DESC);

CREATE INDEX IF NOT EXISTS idx_fragment_grants_fragment
  ON fragment_grants (fragment_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_request
  ON audit_logs (request_id, created_at);

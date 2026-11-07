BEGIN;

ALTER TABLE checkins
  ADD COLUMN IF NOT EXISTS request_id uuid NOT NULL DEFAULT gen_random_uuid();

UPDATE checkins
SET request_id = gen_random_uuid()
WHERE request_id IS NULL;

ALTER TABLE checkins
  ALTER COLUMN request_id SET NOT NULL;

UPDATE challenges AS challenge
SET used_at = checkin.created_at
FROM checkins AS checkin
WHERE challenge.id = checkin.challenge_id
  AND challenge.used_at IS NULL;

INSERT INTO fragment_grants (id, user_id, fragment_id, check_in_id)
SELECT
  'grant_recovered_' || checkin.id,
  checkin.user_id,
  task.fragment_id,
  checkin.id
FROM checkins AS checkin
JOIN tasks AS task
  ON task.landmark_id = checkin.landmark_id
 AND task.type = 'photo'
LEFT JOIN fragment_grants AS grant_row
  ON grant_row.check_in_id = checkin.id
WHERE grant_row.id IS NULL
ON CONFLICT DO NOTHING;

WITH ranked_audits AS (
  SELECT
    id,
    row_number() OVER (
      PARTITION BY event_type, entity_id
      ORDER BY id DESC
    ) AS duplicate_rank
  FROM audit_logs
  WHERE event_type = 'CHECKIN_GRANTED'
    AND entity_id IS NOT NULL
)
DELETE FROM audit_logs AS audit
USING ranked_audits AS ranked
WHERE audit.id = ranked.id
  AND ranked.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_logs_checkin_granted
  ON audit_logs (event_type, entity_id)
  WHERE event_type = 'CHECKIN_GRANTED'
    AND entity_id IS NOT NULL;

INSERT INTO audit_logs (request_id, event_type, user_id, entity_id, metadata)
SELECT
  checkin.request_id::text,
  'CHECKIN_GRANTED',
  checkin.user_id,
  checkin.id,
  jsonb_build_object(
    'landmarkId', checkin.landmark_id,
    'challengeId', checkin.challenge_id,
    'photoId', checkin.photo_id,
    'fragmentId', task.fragment_id,
    'distanceMeters', checkin.distance_meters,
    'accuracyMeters', checkin.accuracy_meters,
    'recoveredByMigration', true
  )
FROM checkins AS checkin
JOIN tasks AS task
  ON task.landmark_id = checkin.landmark_id
 AND task.type = 'photo'
WHERE NOT EXISTS (
  SELECT 1
  FROM audit_logs AS audit
  WHERE audit.event_type = 'CHECKIN_GRANTED'
    AND audit.entity_id = checkin.id
)
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION checkin_00_guard_idempotency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  existing_hash char(64);
BEGIN
  PERFORM pg_advisory_xact_lock(
    hashtextextended(NEW.user_id || ':' || NEW.idempotency_key, 0)
  );

  SELECT request_hash
  INTO existing_hash
  FROM checkins
  WHERE user_id = NEW.user_id
    AND idempotency_key = NEW.idempotency_key
  LIMIT 1;

  IF FOUND THEN
    IF existing_hash <> NEW.request_hash THEN
      RAISE EXCEPTION 'IDEMPOTENCY_CONFLICT'
        USING ERRCODE = 'P0001';
    END IF;
    RAISE EXCEPTION 'CHECKIN_ALREADY_EXISTS'
      USING ERRCODE = 'P0001';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION checkin_10_consume_challenge()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  claimed_challenge_id text;
BEGIN
  UPDATE challenges
  SET used_at = now()
  WHERE id = NEW.challenge_id
    AND user_id = NEW.user_id
    AND landmark_id = NEW.landmark_id
    AND used_at IS NULL
    AND expires_at > now()
  RETURNING id INTO claimed_challenge_id;

  IF claimed_challenge_id IS NULL THEN
    RAISE EXCEPTION 'CHALLENGE_EXPIRED'
      USING ERRCODE = 'P0001';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION checkin_20_materialize_rewards()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  granted_fragment_id text;
BEGIN
  SELECT task.fragment_id
  INTO granted_fragment_id
  FROM tasks AS task
  WHERE task.landmark_id = NEW.landmark_id
    AND task.type = 'photo'
  LIMIT 1;

  IF granted_fragment_id IS NULL THEN
    RAISE EXCEPTION 'LANDMARK_NOT_FOUND'
      USING ERRCODE = 'P0001';
  END IF;

  INSERT INTO fragment_grants (id, user_id, fragment_id, check_in_id)
  VALUES (
    'grant_' || gen_random_uuid(),
    NEW.user_id,
    granted_fragment_id,
    NEW.id
  )
  ON CONFLICT DO NOTHING;

  INSERT INTO audit_logs (request_id, event_type, user_id, entity_id, metadata)
  VALUES (
    NEW.request_id::text,
    'CHECKIN_GRANTED',
    NEW.user_id,
    NEW.id,
    jsonb_build_object(
      'landmarkId', NEW.landmark_id,
      'challengeId', NEW.challenge_id,
      'photoId', NEW.photo_id,
      'fragmentId', granted_fragment_id,
      'distanceMeters', NEW.distance_meters,
      'accuracyMeters', NEW.accuracy_meters,
      'transactional', true
    )
  )
  ON CONFLICT (event_type, entity_id)
    WHERE event_type = 'CHECKIN_GRANTED'
    AND entity_id IS NOT NULL
  DO NOTHING;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS checkin_00_guard_idempotency ON checkins;
CREATE TRIGGER checkin_00_guard_idempotency
BEFORE INSERT ON checkins
FOR EACH ROW
EXECUTE FUNCTION checkin_00_guard_idempotency();

DROP TRIGGER IF EXISTS checkin_10_consume_challenge ON checkins;
CREATE TRIGGER checkin_10_consume_challenge
BEFORE INSERT ON checkins
FOR EACH ROW
EXECUTE FUNCTION checkin_10_consume_challenge();

DROP TRIGGER IF EXISTS checkin_20_materialize_rewards ON checkins;
CREATE TRIGGER checkin_20_materialize_rewards
AFTER INSERT ON checkins
FOR EACH ROW
EXECUTE FUNCTION checkin_20_materialize_rewards();

COMMIT;

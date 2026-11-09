BEGIN;

CREATE OR REPLACE FUNCTION reconcile_checkin_attempt(
  p_user_id text,
  p_attempt_id text,
  p_timeout_seconds integer DEFAULT 120
)
RETURNS jsonb
LANGUAGE plpgsql
AS $$
DECLARE
  attempt_row checkin_attempts%ROWTYPE;
  successful_check_in_id text;
BEGIN
  SELECT *
  INTO attempt_row
  FROM checkin_attempts
  WHERE id = p_attempt_id
    AND user_id = p_user_id
  FOR UPDATE;

  SELECT id
  INTO successful_check_in_id
  FROM checkins
  WHERE user_id = p_user_id
    AND idempotency_key = p_attempt_id
  LIMIT 1;

  IF successful_check_in_id IS NOT NULL THEN
    IF attempt_row.id IS NOT NULL AND (
      attempt_row.status <> 'COMPLETED'
      OR attempt_row.check_in_id IS DISTINCT FROM successful_check_in_id
    ) THEN
      UPDATE checkin_attempts
      SET
        status = 'COMPLETED',
        check_in_id = successful_check_in_id,
        failure_code = NULL,
        updated_at = now()
      WHERE id = p_attempt_id
        AND user_id = p_user_id;
    END IF;

    RETURN jsonb_build_object(
      'status', 'COMPLETED',
      'checkInId', successful_check_in_id
    );
  END IF;

  IF attempt_row.id IS NULL THEN
    RETURN jsonb_build_object('status', 'NOT_FOUND');
  END IF;

  IF attempt_row.status = 'PROCESSING' THEN
    IF attempt_row.updated_at <= now() - make_interval(secs => p_timeout_seconds) THEN
      UPDATE checkin_attempts
      SET
        status = 'ABANDONED',
        failure_code = 'PROCESSING_TIMEOUT',
        updated_at = now()
      WHERE id = p_attempt_id
        AND user_id = p_user_id
        AND status = 'PROCESSING';
      RETURN jsonb_build_object('status', 'NOT_FOUND');
    END IF;

    RETURN jsonb_build_object('status', 'PROCESSING');
  END IF;

  RETURN jsonb_build_object('status', 'NOT_FOUND');
END;
$$;

COMMIT;

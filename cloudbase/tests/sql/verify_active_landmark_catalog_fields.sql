DO $verify$
DECLARE
  target_id constant text := 'lmk_north_lake';
  expected_constraint constant text := 'ck_landmarks_active_catalog_fields';
  original_zone text;
  original_piece smallint;
  original_fragment text;
  original_task text;
  observed_sqlstate text;
  observed_constraint text;
BEGIN
  SELECT campus_zone, piece_index, fragment_id
  INTO original_zone, original_piece, original_fragment
  FROM landmarks
  WHERE id = target_id
    AND status = 'active';

  IF NOT FOUND OR original_zone IS NULL OR original_piece IS NULL OR original_fragment IS NULL THEN
    RAISE EXCEPTION
      'VERIFY_FAIL|reason=valid-baseline-missing|landmark=%',
      target_id;
  END IF;

  SELECT id
  INTO original_task
  FROM tasks
  WHERE landmark_id = target_id
    AND type = 'photo'
  LIMIT 1;

  IF original_task IS NULL THEN
    RAISE EXCEPTION
      'VERIFY_FAIL|reason=task-reference-missing|landmark=%',
      target_id;
  END IF;

  -- The temporary DDL is rolled back by the deliberate exception at the end.
  ALTER TABLE landmarks
    DROP CONSTRAINT IF EXISTS ck_landmarks_active_catalog_complete;
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

  BEGIN
    UPDATE landmarks SET campus_zone = NULL WHERE id = target_id;
    RAISE EXCEPTION 'VERIFY_FAIL|case=only-campus-null|reason=update-succeeded';
  EXCEPTION WHEN check_violation THEN
    GET STACKED DIAGNOSTICS
      observed_sqlstate = RETURNED_SQLSTATE,
      observed_constraint = CONSTRAINT_NAME;
    IF observed_sqlstate <> '23514' OR observed_constraint <> expected_constraint THEN
      RAISE EXCEPTION
        'VERIFY_FAIL|case=only-campus-null|sqlstate=%|constraint=%',
        observed_sqlstate,
        observed_constraint;
    END IF;
  END;

  BEGIN
    UPDATE landmarks SET piece_index = NULL WHERE id = target_id;
    RAISE EXCEPTION 'VERIFY_FAIL|case=only-piece-null|reason=update-succeeded';
  EXCEPTION WHEN check_violation THEN
    GET STACKED DIAGNOSTICS
      observed_sqlstate = RETURNED_SQLSTATE,
      observed_constraint = CONSTRAINT_NAME;
    IF observed_sqlstate <> '23514' OR observed_constraint <> expected_constraint THEN
      RAISE EXCEPTION
        'VERIFY_FAIL|case=only-piece-null|sqlstate=%|constraint=%',
        observed_sqlstate,
        observed_constraint;
    END IF;
  END;

  BEGIN
    UPDATE landmarks
    SET campus_zone = NULL, piece_index = NULL
    WHERE id = target_id;
    RAISE EXCEPTION 'VERIFY_FAIL|case=both-null|reason=update-succeeded';
  EXCEPTION WHEN check_violation THEN
    GET STACKED DIAGNOSTICS
      observed_sqlstate = RETURNED_SQLSTATE,
      observed_constraint = CONSTRAINT_NAME;
    IF observed_sqlstate <> '23514' OR observed_constraint <> expected_constraint THEN
      RAISE EXCEPTION
        'VERIFY_FAIL|case=both-null|sqlstate=%|constraint=%',
        observed_sqlstate,
        observed_constraint;
    END IF;
  END;

  UPDATE landmarks
  SET campus_zone = original_zone, piece_index = original_piece
  WHERE id = target_id;

  IF NOT EXISTS (
    SELECT 1
    FROM fragments
    WHERE id = original_fragment
  ) THEN
    RAISE EXCEPTION 'VERIFY_FAIL|case=valid|reason=fragment-fk-missing';
  END IF;

  RAISE EXCEPTION
    'VERIFY_PASS|sqlstate=23514|constraint=%|onlyCampusNull=rejected|onlyPieceNull=rejected|bothNull=rejected|valid=accepted|foreignKeys=intact',
    expected_constraint
    USING ERRCODE = 'P0001';
END
$verify$;

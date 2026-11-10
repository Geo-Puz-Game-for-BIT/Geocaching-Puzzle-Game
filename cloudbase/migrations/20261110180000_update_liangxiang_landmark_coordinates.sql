-- Update the player catalog with the 2026-11-10 coordinate reconciliation.
-- Values are GCJ-02, matching the AMap client and the existing catalog schema.
-- Coordinate verification remains unchanged: an OSM/building center is not a
-- confirmed safe photo station.

UPDATE landmarks AS landmark
SET latitude = candidate.latitude,
    longitude = candidate.longitude,
    coordinate_system = 'GCJ02'
FROM (VALUES
  ('lmk_north_lake', 39.734016, 116.168918),
  ('lmk_north_zongjiao', 39.732482, 116.170086),
  ('lmk_north_student_service', 39.731746, 116.166618),
  ('lmk_north_library', 39.730967, 116.170264),
  ('lmk_east_gantang', 39.736011, 116.173911),
  ('lmk_east_qinyuan', 39.734800, 116.176863),
  ('lmk_east_wencui', 39.731730, 116.172769),
  ('lmk_east_wenbo', 39.732405, 116.175579),
  ('lmk_south_shutong', 39.729631, 116.167842),
  ('lmk_south_boya', 39.726161, 116.169149),
  ('lmk_south_lijiao', 39.729262, 116.170382),
  ('lmk_south_industrial_ecology', 39.725183, 116.169984)
) AS candidate(id, latitude, longitude)
WHERE landmark.id = candidate.id;

DO $$
DECLARE
  updated_count integer;
BEGIN
  SELECT COUNT(*)
  INTO updated_count
  FROM landmarks
  WHERE coordinate_system = 'GCJ02'
    AND id IN (
      'lmk_north_lake',
      'lmk_north_zongjiao',
      'lmk_north_student_service',
      'lmk_north_library',
      'lmk_east_gantang',
      'lmk_east_qinyuan',
      'lmk_east_wencui',
      'lmk_east_wenbo',
      'lmk_south_shutong',
      'lmk_south_boya',
      'lmk_south_lijiao',
      'lmk_south_industrial_ecology'
    );
  IF updated_count <> 12 THEN
    RAISE EXCEPTION 'Expected 12 campus landmarks to update, got %', updated_count;
  END IF;
END $$;

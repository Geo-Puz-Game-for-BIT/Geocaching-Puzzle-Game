-- Update the player catalog with the 2026-11-12 measured coordinates and reveal anchors.
-- Values are GCJ-02, matching the AMap client and the existing catalog schema.
-- Coordinate verification remains unchanged: an OSM/building center is not a
-- confirmed safe photo station.

ALTER TABLE landmarks
  ADD COLUMN IF NOT EXISTS reveal_points jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE landmarks AS landmark
SET latitude = candidate.latitude,
    longitude = candidate.longitude,
    coordinate_system = 'GCJ02',
    check_in_area = candidate.check_in_area,
    reveal_points = candidate.reveal_points
FROM (VALUES
  ('lmk_north_lake', 39.734934, 116.170196, '{"type":"polygon","vertices":[{"eastMeters":-195,"northMeters":-101},{"eastMeters":195,"northMeters":-101},{"eastMeters":195,"northMeters":101},{"eastMeters":-195,"northMeters":101}]}'::jsonb, '[{"latitude":39.735705,"longitude":116.170333},{"latitude":39.734873,"longitude":116.168265},{"latitude":39.734074,"longitude":116.170034}]'::jsonb),
  ('lmk_north_zongjiao', 39.733317, 116.171304, '{"type":"polygon","vertices":[{"eastMeters":-88.5,"northMeters":-44.5},{"eastMeters":88.5,"northMeters":-44.5},{"eastMeters":88.5,"northMeters":44.5},{"eastMeters":-88.5,"northMeters":44.5}]}'::jsonb, '[]'::jsonb),
  ('lmk_north_student_service', 39.732696, 116.167787, '{"type":"circle","radiusMeters":56}'::jsonb, '[]'::jsonb),
  ('lmk_north_library', 39.731851, 116.171335, '{"type":"polygon","vertices":[{"eastMeters":-74.5,"northMeters":-48.5},{"eastMeters":74.5,"northMeters":-48.5},{"eastMeters":74.5,"northMeters":48.5},{"eastMeters":-74.5,"northMeters":48.5}]}'::jsonb, '[]'::jsonb),
  ('lmk_east_gantang', 39.736011, 116.173911, '{"type":"polygon","vertices":[{"eastMeters":-92,"northMeters":-40.5},{"eastMeters":92,"northMeters":-40.5},{"eastMeters":92,"northMeters":40.5},{"eastMeters":-92,"northMeters":40.5}]}'::jsonb, '[]'::jsonb),
  ('lmk_east_qinyuan', 39.734705, 116.176207, '{"type":"polygon","vertices":[{"eastMeters":-102,"northMeters":-66.5},{"eastMeters":102,"northMeters":-66.5},{"eastMeters":102,"northMeters":66.5},{"eastMeters":-102,"northMeters":66.5}]}'::jsonb, '[]'::jsonb),
  ('lmk_east_wencui', 39.732692, 116.173830, '{"type":"polygon","vertices":[{"eastMeters":-199,"northMeters":-58},{"eastMeters":199,"northMeters":-58},{"eastMeters":199,"northMeters":58},{"eastMeters":-199,"northMeters":58}]}'::jsonb, '[]'::jsonb),
  ('lmk_east_wenbo', 39.733296, 116.176620, '{"type":"polygon","vertices":[{"eastMeters":-95,"northMeters":-62},{"eastMeters":95,"northMeters":-62},{"eastMeters":95,"northMeters":62},{"eastMeters":-95,"northMeters":62}]}'::jsonb, '[]'::jsonb),
  ('lmk_south_shutong', 39.729654, 116.167735, '{"type":"polygon","vertices":[{"eastMeters":-165,"northMeters":-36},{"eastMeters":165,"northMeters":-36},{"eastMeters":165,"northMeters":36},{"eastMeters":-165,"northMeters":36}]}'::jsonb, '[]'::jsonb),
  ('lmk_south_boya', 39.726161, 116.169149, '{"type":"polygon","vertices":[{"eastMeters":-55,"northMeters":-36},{"eastMeters":55,"northMeters":-36},{"eastMeters":55,"northMeters":36},{"eastMeters":-55,"northMeters":36}]}'::jsonb, '[]'::jsonb),
  ('lmk_south_lijiao', 39.730289, 116.171439, '{"type":"polygon","vertices":[{"eastMeters":-68,"northMeters":-44.5},{"eastMeters":68,"northMeters":-44.5},{"eastMeters":68,"northMeters":44.5},{"eastMeters":-68,"northMeters":44.5}]}'::jsonb, '[]'::jsonb),
  ('lmk_south_industrial_ecology', 39.726058, 116.171010, '{"type":"polygon","vertices":[{"eastMeters":-66,"northMeters":-23.5},{"eastMeters":66,"northMeters":-23.5},{"eastMeters":66,"northMeters":23.5},{"eastMeters":-66,"northMeters":23.5}]}'::jsonb, '[]'::jsonb)
) AS candidate(id, latitude, longitude, check_in_area, reveal_points)
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

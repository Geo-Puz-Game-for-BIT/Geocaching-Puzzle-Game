INSERT INTO users (id, display_name)
VALUES ('usr_demo01', 'Demo User')
ON CONFLICT (id) DO NOTHING;

INSERT INTO puzzles (id, name, rows, columns)
VALUES ('pzl_campus_01', 'Campus Puzzle 01', 3, 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO landmarks (
  id,
  name,
  latitude,
  longitude,
  coordinate_system,
  check_in_radius_meters,
  status
)
VALUES (
  'lmk_library',
  'BIT Library',
  39.9592,
  116.3083,
  'GCJ02',
  100,
  'active'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fragments (id, puzzle_id, piece_index, image_key)
VALUES (
  'frg_campus_01_01',
  'pzl_campus_01',
  0,
  'puzzles/pzl_campus_01/frg_campus_01_01.png'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tasks (
  id,
  landmark_id,
  type,
  instructions,
  puzzle_id,
  fragment_id
)
VALUES (
  'tsk_library_photo',
  'lmk_library',
  'photo',
  'Take a photo of the library entrance and submit it from the marker.',
  'pzl_campus_01',
  'frg_campus_01_01'
)
ON CONFLICT (id) DO NOTHING;

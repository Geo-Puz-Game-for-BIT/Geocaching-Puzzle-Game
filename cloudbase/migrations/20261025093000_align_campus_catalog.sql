BEGIN;

ALTER TABLE puzzles
  ADD COLUMN IF NOT EXISTS default_reveal_mode text NOT NULL DEFAULT 'FOG_OF_WAR';

ALTER TABLE puzzles
  DROP CONSTRAINT IF EXISTS ck_puzzles_id_prefix,
  DROP CONSTRAINT IF EXISTS ck_puzzles_default_reveal_mode;

ALTER TABLE puzzles
  ADD CONSTRAINT ck_puzzles_id_prefix
    CHECK (id LIKE 'pzl\_%' ESCAPE '\' OR id LIKE 'puzzle\_%' ESCAPE '\'),
  ADD CONSTRAINT ck_puzzles_default_reveal_mode
    CHECK (default_reveal_mode IN ('GRID', 'FOG_OF_WAR'));

CREATE TABLE IF NOT EXISTS puzzle_sections (
  id text PRIMARY KEY,
  puzzle_id text NOT NULL REFERENCES puzzles(id) ON DELETE CASCADE,
  campus_zone text NOT NULL,
  rows smallint NOT NULL,
  columns smallint NOT NULL,
  sort_order smallint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_puzzle_sections_zone
    CHECK (campus_zone IN ('NORTH', 'EAST', 'SOUTH')),
  CONSTRAINT ck_puzzle_sections_grid
    CHECK (rows > 0 AND columns > 0),
  CONSTRAINT uq_puzzle_sections_zone
    UNIQUE (puzzle_id, campus_zone)
);

ALTER TABLE fragments
  ADD COLUMN IF NOT EXISTS section_id text,
  ADD COLUMN IF NOT EXISTS landmark_id text,
  ADD COLUMN IF NOT EXISTS title text;

ALTER TABLE fragments
  DROP CONSTRAINT IF EXISTS ck_fragments_id_prefix,
  DROP CONSTRAINT IF EXISTS uq_fragments_puzzle_piece,
  DROP CONSTRAINT IF EXISTS ck_fragments_section_id_fkey,
  DROP CONSTRAINT IF EXISTS ck_fragments_landmark_id_fkey;

ALTER TABLE fragments
  ADD CONSTRAINT ck_fragments_id_prefix
  CHECK (
    id LIKE 'frg\_%' ESCAPE '\'
    OR id LIKE 'fragment\_%' ESCAPE '\'
  ),
  ADD CONSTRAINT uq_fragments_puzzle_section_piece
  UNIQUE (puzzle_id, section_id, piece_index);

ALTER TABLE tasks
  DROP CONSTRAINT IF EXISTS ck_tasks_id_prefix;

ALTER TABLE tasks
  ADD CONSTRAINT ck_tasks_id_prefix
  CHECK (
    id LIKE 'tsk\_%' ESCAPE '\'
    OR id LIKE 'task\_%' ESCAPE '\'
  );

ALTER TABLE landmarks
  ADD COLUMN IF NOT EXISTS campus_zone text,
  ADD COLUMN IF NOT EXISTS piece_index smallint,
  ADD COLUMN IF NOT EXISTS fragment_id text,
  ADD COLUMN IF NOT EXISTS coordinate_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS check_in_area jsonb;

ALTER TABLE landmarks
  DROP CONSTRAINT IF EXISTS ck_landmarks_campus_zone,
  DROP CONSTRAINT IF EXISTS ck_landmarks_piece_index,
  DROP CONSTRAINT IF EXISTS ck_landmarks_check_in_area;

ALTER TABLE landmarks
  ADD CONSTRAINT ck_landmarks_campus_zone
    CHECK (campus_zone IS NULL OR campus_zone IN ('NORTH', 'EAST', 'SOUTH')),
  ADD CONSTRAINT ck_landmarks_piece_index
    CHECK (piece_index IS NULL OR piece_index BETWEEN 0 AND 3),
  ADD CONSTRAINT ck_landmarks_check_in_area
    CHECK (
      check_in_area IS NULL
      OR (
        check_in_area->>'type' = 'circle'
        AND (check_in_area->>'radiusMeters')::double precision > 0
      )
      OR (
        check_in_area->>'type' = 'polygon'
        AND jsonb_array_length(check_in_area->'vertices') >= 3
      )
    );

INSERT INTO puzzles (id, name, rows, columns, default_reveal_mode)
VALUES (
  'puzzle_liangxiang_campus',
  '良乡校区探索图',
  2,
  2,
  'FOG_OF_WAR'
)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    rows = EXCLUDED.rows,
    columns = EXCLUDED.columns,
    default_reveal_mode = EXCLUDED.default_reveal_mode;

INSERT INTO puzzle_sections (id, puzzle_id, campus_zone, rows, columns, sort_order)
VALUES
  ('section_north', 'puzzle_liangxiang_campus', 'NORTH', 2, 2, 0),
  ('section_east', 'puzzle_liangxiang_campus', 'EAST', 2, 2, 1),
  ('section_south', 'puzzle_liangxiang_campus', 'SOUTH', 2, 2, 2)
ON CONFLICT (id) DO UPDATE
SET puzzle_id = EXCLUDED.puzzle_id,
    campus_zone = EXCLUDED.campus_zone,
    rows = EXCLUDED.rows,
    columns = EXCLUDED.columns,
    sort_order = EXCLUDED.sort_order;

INSERT INTO landmarks (
  id,
  name,
  latitude,
  longitude,
  coordinate_system,
  check_in_radius_meters,
  status,
  campus_zone,
  piece_index,
  coordinate_verified,
  check_in_area,
  difficulty,
  terrain,
  hint
)
VALUES
  (
    'lmk_north_lake', '北湖', 39.7366, 116.1666, 'GCJ02', 55, 'active',
    'NORTH', 0, false,
    '{"type":"circle","radiusMeters":55}'::jsonb,
    1, 1, '沿湖岸寻找具有校园辨识度的取景点。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_north_zongjiao', '综教', 39.7339, 116.1680, 'GCJ02', 42, 'active',
    'NORTH', 1, false,
    '{"type":"polygon","vertices":[{"eastMeters":-32.727,"northMeters":-25.806},{"eastMeters":38.572,"northMeters":-15.785},{"eastMeters":32.727,"northMeters":25.806},{"eastMeters":-38.572,"northMeters":15.785}]}'::jsonb,
    1, 1, '观察综合教学楼入口附近的建筑标识。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_north_student_service', '学服', 39.7336, 116.1662, 'GCJ02', 31, 'active',
    'NORTH', 2, false,
    '{"type":"polygon","vertices":[{"eastMeters":-25.646,"northMeters":-14.398},{"eastMeters":22.092,"northMeters":-19.416},{"eastMeters":25.646,"northMeters":14.398},{"eastMeters":-22.092,"northMeters":19.416}]}'::jsonb,
    1, 1, '在学生服务中心外选择不影响通行的位置完成任务。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_north_library', '图书馆', 39.7335, 116.1649, 'GCJ02', 50, 'active',
    'NORTH', 3, false,
    '{"type":"polygon","vertices":[{"eastMeters":-41.221,"northMeters":-26.941},{"eastMeters":44.569,"northMeters":-20.942},{"eastMeters":41.221,"northMeters":26.941},{"eastMeters":-44.569,"northMeters":20.942}]}'::jsonb,
    1, 1, '寻找徐特立图书馆具有代表性的外立面。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_east_gantang', '甘棠', 39.7371, 116.1717, 'GCJ02', 42, 'active',
    'EAST', 0, false,
    '{"type":"circle","radiusMeters":42}'::jsonb,
    1, 1, '在甘棠园公共区域寻找任务标志。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_east_qinyuan', '勤园', 39.7368, 116.1743, 'GCJ02', 42, 'active',
    'EAST', 1, false,
    '{"type":"circle","radiusMeters":42}'::jsonb,
    1, 1, '从公共道路观察勤园的区域特征。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_east_wencui', '文萃', 39.7339, 116.1730, 'GCJ02', 46, 'active',
    'EAST', 2, false,
    '{"type":"polygon","vertices":[{"eastMeters":-39,"northMeters":-25},{"eastMeters":39,"northMeters":-25},{"eastMeters":39,"northMeters":25},{"eastMeters":-39,"northMeters":25}]}'::jsonb,
    1, 1, '寻找文萃楼附近容易辨认的建筑细节。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_east_wenbo', '文博中心', 39.7340, 116.1754, 'GCJ02', 44, 'active',
    'EAST', 3, false,
    '{"type":"polygon","vertices":[{"eastMeters":-35,"northMeters":-27.5},{"eastMeters":35,"northMeters":-27.5},{"eastMeters":35,"northMeters":27.5},{"eastMeters":-35,"northMeters":27.5}]}'::jsonb,
    1, 1, '在文博中心外完成不包含他人正脸的合影。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_south_shutong', '疏桐', 39.7305, 116.1661, 'GCJ02', 45, 'active',
    'SOUTH', 0, false,
    '{"type":"circle","radiusMeters":45}'::jsonb,
    1, 1, '沿疏桐园公共道路寻找指定区域。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_south_boya', '博雅', 39.7269, 116.1657, 'GCJ02', 45, 'active',
    'SOUTH', 1, false,
    '{"type":"circle","radiusMeters":45}'::jsonb,
    1, 1, '在博雅园公共区域完成现场任务。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_south_lijiao', '理教', 39.7301, 116.1695, 'GCJ02', 44, 'active',
    'SOUTH', 2, false,
    '{"type":"polygon","vertices":[{"eastMeters":-35.798,"northMeters":-23.906},{"eastMeters":38.101,"northMeters":-20.033},{"eastMeters":35.798,"northMeters":23.906},{"eastMeters":-38.101,"northMeters":20.033}]}'::jsonb,
    1, 1, '寻找理科教学楼入口附近的校园标识。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  ),
  (
    'lmk_south_industrial_ecology', '工业生态楼', 39.7270, 116.1697, 'GCJ02', 43, 'active',
    'SOUTH', 3, false,
    '{"type":"polygon","vertices":[{"eastMeters":-35.875,"northMeters":-19.949},{"eastMeters":31.866,"northMeters":-25.876},{"eastMeters":35.875,"northMeters":19.949},{"eastMeters":-31.866,"northMeters":25.876}]}'::jsonb,
    1, 1, '在工业生态楼外选择安全位置完成打卡。当前坐标仅用于开发演示，正式打卡前需要实地核验。'
  )
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    coordinate_system = EXCLUDED.coordinate_system,
    check_in_radius_meters = EXCLUDED.check_in_radius_meters,
    status = EXCLUDED.status,
    campus_zone = EXCLUDED.campus_zone,
    piece_index = EXCLUDED.piece_index,
    coordinate_verified = EXCLUDED.coordinate_verified,
    check_in_area = EXCLUDED.check_in_area,
    difficulty = EXCLUDED.difficulty,
    terrain = EXCLUDED.terrain,
    hint = EXCLUDED.hint;

INSERT INTO fragments (
  id,
  puzzle_id,
  piece_index,
  image_key,
  section_id,
  landmark_id,
  title
)
VALUES
  ('fragment_north_lake', 'puzzle_liangxiang_campus', 0, 'puzzles/puzzle_liangxiang_campus/fragment_north_lake.png', 'section_north', 'lmk_north_lake', '北湖'),
  ('fragment_north_zongjiao', 'puzzle_liangxiang_campus', 1, 'puzzles/puzzle_liangxiang_campus/fragment_north_zongjiao.png', 'section_north', 'lmk_north_zongjiao', '综教'),
  ('fragment_north_student_service', 'puzzle_liangxiang_campus', 2, 'puzzles/puzzle_liangxiang_campus/fragment_north_student_service.png', 'section_north', 'lmk_north_student_service', '学服'),
  ('fragment_north_library', 'puzzle_liangxiang_campus', 3, 'puzzles/puzzle_liangxiang_campus/fragment_north_library.png', 'section_north', 'lmk_north_library', '图书馆'),
  ('fragment_east_gantang', 'puzzle_liangxiang_campus', 0, 'puzzles/puzzle_liangxiang_campus/fragment_east_gantang.png', 'section_east', 'lmk_east_gantang', '甘棠'),
  ('fragment_east_qinyuan', 'puzzle_liangxiang_campus', 1, 'puzzles/puzzle_liangxiang_campus/fragment_east_qinyuan.png', 'section_east', 'lmk_east_qinyuan', '勤园'),
  ('fragment_east_wencui', 'puzzle_liangxiang_campus', 2, 'puzzles/puzzle_liangxiang_campus/fragment_east_wencui.png', 'section_east', 'lmk_east_wencui', '文萃'),
  ('fragment_east_wenbo', 'puzzle_liangxiang_campus', 3, 'puzzles/puzzle_liangxiang_campus/fragment_east_wenbo.png', 'section_east', 'lmk_east_wenbo', '文博中心'),
  ('fragment_south_shutong', 'puzzle_liangxiang_campus', 0, 'puzzles/puzzle_liangxiang_campus/fragment_south_shutong.png', 'section_south', 'lmk_south_shutong', '疏桐'),
  ('fragment_south_boya', 'puzzle_liangxiang_campus', 1, 'puzzles/puzzle_liangxiang_campus/fragment_south_boya.png', 'section_south', 'lmk_south_boya', '博雅'),
  ('fragment_south_lijiao', 'puzzle_liangxiang_campus', 2, 'puzzles/puzzle_liangxiang_campus/fragment_south_lijiao.png', 'section_south', 'lmk_south_lijiao', '理教'),
  ('fragment_south_industrial_ecology', 'puzzle_liangxiang_campus', 3, 'puzzles/puzzle_liangxiang_campus/fragment_south_industrial_ecology.png', 'section_south', 'lmk_south_industrial_ecology', '工业生态楼')
ON CONFLICT (id) DO UPDATE
SET puzzle_id = EXCLUDED.puzzle_id,
    piece_index = EXCLUDED.piece_index,
    image_key = EXCLUDED.image_key,
    section_id = EXCLUDED.section_id,
    landmark_id = EXCLUDED.landmark_id,
    title = EXCLUDED.title;

UPDATE landmarks
SET fragment_id = 'fragment_' || substr(id, 5)
WHERE id IN (
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

INSERT INTO tasks (id, landmark_id, type, instructions, puzzle_id, fragment_id)
VALUES
  ('task_north_lake', 'lmk_north_lake', 'photo', '在北湖完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_north_lake'),
  ('task_north_zongjiao', 'lmk_north_zongjiao', 'photo', '在综教完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_north_zongjiao'),
  ('task_north_student_service', 'lmk_north_student_service', 'photo', '在学服完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_north_student_service'),
  ('task_north_library', 'lmk_north_library', 'photo', '在图书馆完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_north_library'),
  ('task_east_gantang', 'lmk_east_gantang', 'photo', '在甘棠完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_east_gantang'),
  ('task_east_qinyuan', 'lmk_east_qinyuan', 'photo', '在勤园完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_east_qinyuan'),
  ('task_east_wencui', 'lmk_east_wencui', 'photo', '在文萃完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_east_wencui'),
  ('task_east_wenbo', 'lmk_east_wenbo', 'photo', '在文博中心完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_east_wenbo'),
  ('task_south_shutong', 'lmk_south_shutong', 'photo', '在疏桐完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_south_shutong'),
  ('task_south_boya', 'lmk_south_boya', 'photo', '在博雅完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_south_boya'),
  ('task_south_lijiao', 'lmk_south_lijiao', 'photo', '在理教完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_south_lijiao'),
  ('task_south_industrial_ecology', 'lmk_south_industrial_ecology', 'photo', '在工业生态楼完成现场拍照并上传。', 'puzzle_liangxiang_campus', 'fragment_south_industrial_ecology')
ON CONFLICT (id) DO UPDATE
SET landmark_id = EXCLUDED.landmark_id,
    type = EXCLUDED.type,
    instructions = EXCLUDED.instructions,
    puzzle_id = EXCLUDED.puzzle_id,
    fragment_id = EXCLUDED.fragment_id;

UPDATE landmarks
SET status = 'inactive'
WHERE id = 'lmk_library';

UPDATE puzzles
SET default_reveal_mode = 'FOG_OF_WAR'
WHERE id = 'pzl_campus_01';

ALTER TABLE fragments
  DROP CONSTRAINT IF EXISTS fk_fragments_section,
  DROP CONSTRAINT IF EXISTS fk_fragments_landmark;

ALTER TABLE fragments
  ADD CONSTRAINT fk_fragments_section
    FOREIGN KEY (section_id) REFERENCES puzzle_sections(id),
  ADD CONSTRAINT fk_fragments_landmark
    FOREIGN KEY (landmark_id) REFERENCES landmarks(id);

ALTER TABLE landmarks
  DROP CONSTRAINT IF EXISTS fk_landmarks_fragment;

ALTER TABLE landmarks
  ADD CONSTRAINT fk_landmarks_fragment
    FOREIGN KEY (fragment_id) REFERENCES fragments(id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_landmarks_zone_piece
  ON landmarks (campus_zone, piece_index)
  WHERE campus_zone IS NOT NULL
    AND piece_index IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_puzzle_sections_puzzle
  ON puzzle_sections (puzzle_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_fragments_section
  ON fragments (section_id, piece_index);

COMMIT;

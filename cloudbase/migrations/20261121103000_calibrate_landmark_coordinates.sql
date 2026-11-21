-- 2026-11-21 地标坐标校准：将 12 个 active 地标的位置对齐真实地图。
--
-- 背景：rebuild 后端按迁移序列重放，align_campus_catalog.sql 的早期演示坐标
-- 覆盖了数据库，导致真机水滴整体偏离真实建筑 126~580m。
-- 本迁移写入 LiangxiangTreasureCatalog.kt 的 2026-11-14 实测校准值（GCJ-02，
-- 与高德底图同系），并同步打卡区域 / 半径 / 校验标记 / 提示语。
BEGIN;

UPDATE landmarks SET
  latitude = 39.734934, longitude = 116.170196,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-195,"northMeters":-101},{"eastMeters":195,"northMeters":-101},{"eastMeters":195,"northMeters":101},{"eastMeters":-195,"northMeters":101}]}'::jsonb,
  check_in_radius_meters = 220,
  coordinate_verified = true,
  hint = '沿湖岸寻找具有校园辨识度的取景点。'
WHERE id = 'lmk_north_lake';

UPDATE landmarks SET
  latitude = 39.733317, longitude = 116.171304,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-88.5,"northMeters":-44.5},{"eastMeters":88.5,"northMeters":-44.5},{"eastMeters":88.5,"northMeters":44.5},{"eastMeters":-88.5,"northMeters":44.5}]}'::jsonb,
  check_in_radius_meters = 99,
  coordinate_verified = true,
  hint = '观察综合教学楼入口附近的建筑标识。'
WHERE id = 'lmk_north_zongjiao';

UPDATE landmarks SET
  latitude = 39.732696, longitude = 116.167787,
  check_in_area = '{"type":"circle","radiusMeters":56}'::jsonb,
  check_in_radius_meters = 56,
  coordinate_verified = true,
  hint = '在学生服务中心外选择不影响通行的位置完成任务。'
WHERE id = 'lmk_north_student_service';

UPDATE landmarks SET
  latitude = 39.731851, longitude = 116.171335,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-74.5,"northMeters":-48.5},{"eastMeters":74.5,"northMeters":-48.5},{"eastMeters":74.5,"northMeters":48.5},{"eastMeters":-74.5,"northMeters":48.5}]}'::jsonb,
  check_in_radius_meters = 89,
  coordinate_verified = true,
  hint = '寻找徐特立图书馆具有代表性的外立面。'
WHERE id = 'lmk_north_library';

UPDATE landmarks SET
  latitude = 39.736011, longitude = 116.173911,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-92,"northMeters":-40.5},{"eastMeters":92,"northMeters":-40.5},{"eastMeters":92,"northMeters":40.5},{"eastMeters":-92,"northMeters":40.5}]}'::jsonb,
  check_in_radius_meters = 101,
  coordinate_verified = true,
  hint = '在甘棠园公共区域寻找任务标志。'
WHERE id = 'lmk_east_gantang';

UPDATE landmarks SET
  latitude = 39.734705, longitude = 116.176207,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-102,"northMeters":-66.5},{"eastMeters":102,"northMeters":-66.5},{"eastMeters":102,"northMeters":66.5},{"eastMeters":-102,"northMeters":66.5}]}'::jsonb,
  check_in_radius_meters = 122,
  coordinate_verified = true,
  hint = '从公共道路观察勤园的区域特征。'
WHERE id = 'lmk_east_qinyuan';

UPDATE landmarks SET
  latitude = 39.732692, longitude = 116.173830,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-89,"northMeters":-129.5},{"eastMeters":89,"northMeters":-129.5},{"eastMeters":89,"northMeters":129.5},{"eastMeters":-89,"northMeters":129.5}]}'::jsonb,
  check_in_radius_meters = 157,
  coordinate_verified = true,
  hint = '寻找文萃楼附近容易辨认的建筑细节。'
WHERE id = 'lmk_east_wencui';

UPDATE landmarks SET
  latitude = 39.733296, longitude = 116.176620,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-95,"northMeters":-62},{"eastMeters":95,"northMeters":-62},{"eastMeters":95,"northMeters":62},{"eastMeters":-95,"northMeters":62}]}'::jsonb,
  check_in_radius_meters = 113,
  coordinate_verified = true,
  hint = '在文博中心外完成不包含他人正脸的合影。'
WHERE id = 'lmk_east_wenbo';

UPDATE landmarks SET
  latitude = 39.729654, longitude = 116.167735,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-55,"northMeters":-107.5},{"eastMeters":55,"northMeters":-107.5},{"eastMeters":55,"northMeters":107.5},{"eastMeters":-55,"northMeters":107.5}]}'::jsonb,
  check_in_radius_meters = 121,
  coordinate_verified = true,
  hint = '沿疏桐园公共道路寻找指定区域。'
WHERE id = 'lmk_south_shutong';

UPDATE landmarks SET
  latitude = 39.726161, longitude = 116.169149,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-55,"northMeters":-36},{"eastMeters":55,"northMeters":-36},{"eastMeters":55,"northMeters":36},{"eastMeters":-55,"northMeters":36}]}'::jsonb,
  check_in_radius_meters = 66,
  coordinate_verified = true,
  hint = '在博雅园公共区域完成现场任务。'
WHERE id = 'lmk_south_boya';

UPDATE landmarks SET
  latitude = 39.730289, longitude = 116.171439,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-68,"northMeters":-44.5},{"eastMeters":68,"northMeters":-44.5},{"eastMeters":68,"northMeters":44.5},{"eastMeters":-68,"northMeters":44.5}]}'::jsonb,
  check_in_radius_meters = 81,
  coordinate_verified = true,
  hint = '寻找理科教学楼入口附近的校园标识。'
WHERE id = 'lmk_south_lijiao';

UPDATE landmarks SET
  latitude = 39.726058, longitude = 116.171010,
  check_in_area = '{"type":"polygon","vertices":[{"eastMeters":-66,"northMeters":-23.5},{"eastMeters":66,"northMeters":-23.5},{"eastMeters":66,"northMeters":23.5},{"eastMeters":-66,"northMeters":23.5}]}'::jsonb,
  check_in_radius_meters = 70,
  coordinate_verified = true,
  hint = '在工业生态楼外选择安全位置完成打卡。'
WHERE id = 'lmk_south_industrial_ecology';

COMMIT;

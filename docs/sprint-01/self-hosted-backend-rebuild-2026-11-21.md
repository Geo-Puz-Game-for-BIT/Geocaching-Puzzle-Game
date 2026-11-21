# 自建后端重建记录（2026-11-21）

## 背景

D（张博淳）侧 dev 服务（`cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com`）已失效，
网关返回 **HTTP 410 Gone**（2026-11-14 时为 200），导致 App 地标列表加载失败（红色错误条、无水滴）。
该域名 AppId 为 1510671296（D 的环境），不在本组账号下，无法拉起。
经确认，改为在 **本组自己的 CloudBase 环境** 用仓库内服务端源码完整重建。

## 新服务端坐标

| 项 | 值 |
| --- | --- |
| 环境 | `geocaching0-d5gzu3bsq9863e0b1`（ap-shanghai，AppId 1511214174） |
| API Base URL | `https://geocaching0-d5gzu3bsq9863e0b1-1511214174.ap-shanghai.app.tcloudbase.com/api/v1` |
| 打卡 Token | 与原交接一致（App 侧 local.properties 无需改 token，只需换 API_BASE_URL） |
| 函数 | `api`（HTTP，Nodejs20.19，zip 部署，入口 `dist/main.cjs`，监听 9000）+ `photo-cleanup`（Event，timer 每 10 分钟） |
| 网关路由 | `/api/v1` → WEB_SCF `api`，路径透传开启，网关层不鉴权（函数内 Bearer 校验） |
| 数据库 | 环境内 PostgreSQL（cloudbase-pg），已按 `cloudbase/migrations/` 全量 10 个迁移建库 |
| 种子数据 | 12 个 active 地标（三区 2×2，GCJ02）+ 1 个 inactive（lmk_library），与原 /landmarks 契约一致 |
| 存储 | pgstore 桶 `checkin-photos`（私有），打卡照片 object key `checkins/{userId}/{photoId}.jpg` |
| 服务端 API Key | service_role，长期有效，配置在两个函数的 `CLOUDBASE_API_KEY` 环境变量中（不入库） |

## 与源码的差异（部署口径，非代码改动）

1. **迁移**：`applyMigration` 客户端拦截 DROP 类语句，故以下 DROP 通过云 API `ExecutePGSql`
   以 `cloudbase_postgres` 角色单独执行，其余 SQL 原样走迁移（迁移历史 9 条均已落库）：
   - `uq_fragments_puzzle_piece`、`ck_puzzles_id_prefix`、`ck_fragments_id_prefix`、`ck_tasks_id_prefix`
     （迁移 5 前置，新数据用 `puzzle_/fragment_/task_` 前缀且同拼图 piece 跨区复用）
   - `ck_landmarks_active_catalog_complete`（迁移 9 前置）、`uq_landmarks_zone_piece`（迁移 10 前置）
2. **函数环境变量**（cloudbaserc.json 的 `{{env.*}}` 注入等价手工配置）：
   `API_HOST=0.0.0.0`、`API_PORT=9000`、`APP_ENV=dev`、`SERVICE_VERSION=0.1.0`、
   `DEMO_AUTH_TOKEN=<原交接 token>`、`DEMO_USER_ID=usr_demo01`、
   `STORAGE_API_BASE=https://geocaching0-d5gzu3bsq9863e0b1.api.tcloudbasegateway.com`、
   `STORAGE_BUCKET=checkin-photos`、`CLOUDBASE_API_KEY=<service_role key>`。
3. **App 侧**：`android-app/local.properties` 的 `API_BASE_URL` 换为新网关 URL
   （local.properties 不入库；APK 的 BuildConfig 已带新地址）。
   `DEMO_AUTH_TOKEN` 不变，`HAS_CLOUD_API=true` 推导不变。

## 验证（2026-11-21）

- `GET /api/v1/health` → **200**（~0.4s）
- `GET /api/v1/landmarks`（Bearer）→ **200**，12 条，字段与 openapi 契约一致
  （id/name/point(GCJ02)/campusZone/fragmentId/pieceIndex/checkInArea/task…）
- App 构建：141 单测 0 失败、lint 0 errors / 8 warnings（基线一致）。
- 交付 APK：`ui-v2-自建后端v8-debug.apk`（119.46MB，SHA256 63F82D94…5672D2）。

## 后续

- 若 D 的服务日后恢复，只需把 `API_BASE_URL` 换回原 URL 重打包即可（协议契约完全相同）。
- 服务端再发布：`cloudbase/` 下 `npm run build:cloud` 出 `deploy/dist/main.cjs`，
  再用 CloudBase MCP `manageFunctions(updateFunctionCode)` 或 `tcb deploy --only functions` 更新。
- 服务端 API Key 属敏感凭据：只存于函数环境变量与 D 私发渠道，不写入任何入库文件。

## 追记：地标坐标回归修复（2026-11-21 晚）

真机验收发现 12 个水滴整体偏离真实建筑 126~580m。根因：重放迁移时
`align_campus_catalog.sql`（ON CONFLICT DO UPDATE）把早期开发演示坐标写回了数据库，
而 App 本地目录 `LiangxiangTreasureCatalog.kt` 自 2026-11-14 起已是实测校准值（GCJ-02），
D 原环境是手工同步过的，重建后未再同步。

修复：新增迁移 `20261121103000_calibrate_landmark_coordinates.sql`
（版本 20261121103000，已入远端迁移历史），内容：

- 12 个 active 地标 lat/lon 全部改为 2026-11-14 校准值（GCJ-02，与高德底图同系）；
- `check_in_area` 同步为校准打卡区（矩形按 ±w/2±h/2，学服圆 r=56m），
  `check_in_radius_meters` 改为圆半径/矩形半对角线；
- `coordinate_verified=true`，hint 去掉「仅用于开发演示」声明。

验证：`/landmarks` 返回 12/12 与本地校准值逐位一致。App 代码零改动，
v8 APK 继续可用（坐标由云端下发），重装/杀进程刷新地标列表即可生效。

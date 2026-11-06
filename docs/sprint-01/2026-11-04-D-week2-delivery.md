# D 第二轮交付记录

> 日期：2026-11-04
> 后端分支：`feat/cloudbase-api-poc`
> Android 分支：`fix/android-cloudbase-live-connectivity`

## 任务状态

| 编号 | 状态 | 本轮内容 |
|---|---|---|
| D-N01 | 源码完成，待联调 | 新增 `GET /checkins?attemptId=`、`checkin_attempts` 状态表和 200/202/404(`CHECKIN_NOT_FOUND`) 三态；成功记录优先返回 200 |
| D-N02 | 源码完成，待真实事务环境复跑 | attempt 与 `request_hash` 分离保存；相同 key 改请求继续冲突；未知结果保留 PROCESSING；恢复路径复用原打卡/奖励/审计 |
| D-N03 | 源码完成，边界已记录 | 服务端精度统一 30m；增加来源、采集时间和设备单调时钟字段；照片可绑定 challenge/attempt；服务端时间窗口交叉校验 |
| D-N04 | 外部依赖阻塞 | 未接入人脸/照片核验服务，未把客户端初检写成服务端已验证；协议和验收边界见 `docs/api/photo-verification.md` |
| D-N05 | 已部署验证 | 照片逻辑删除、归属校验、PROCESSING attempt 阻止删除、即时补偿扫描和每 10 分钟 CloudBase timer 均已部署；手动调用成功 |
| D-N06 | 源码完成 | Android 统一 `/api/v1`、取消异常透传、地图/地标在线状态可辨；已合入 C 的 attempt 状态机，AppContainer 同时装配 CloudBase 地标和 RemoteCheckInRepository |
| D-N07 | 已部署验证 | OpenAPI、错误码、恢复/删除契约、迁移和线上部署已同步；health 的 UUID 头体一致 |

## 后端验证

执行：

```powershell
npm run api:check --workspace @geotreasure/cloudbase
```

结果：

- TypeScript 类型检查通过。
- Vitest：67 通过。
- OpenAPI 与三校区 canonical fixture 校验通过。
- 真实 PostgreSQL：8 通过，包含事务、冲突、审计恢复、并发、孤儿封存、迟到请求拒绝和即时清理候选。

已覆盖：

- 正常、范围外、低精度、challenge 过期、照片缺失和 photo/challenge 不匹配。
- 同 attempt 重复点击、相同 key 不同请求冲突、响应丢失恢复、审计补偿。
- 查询 200/202/404 路由、处理中 attempt 阻止照片删除。
- 30m 边界、定位过期、未来时间和墙钟/单调时钟不一致。
- 照片逻辑删除、存储清理完成/待补偿。
- 成功 check-in 已落库但 attempt 仍为 PROCESSING 时，查询返回 200 并修复状态。
- 孤儿 PROCESSING 超过 2 分钟后转 ABANDONED，查询返回 404，迟到 POST 返回 `ATTEMPT_SEALED`。
- challenge 签发绑定 attempt；照片上传和打卡提交校验 attempt↔challenge↔photo。
- 清理扫描不等待 `delete_after`；该字段仅保留审计/备份窗口。

## 迁移与部署顺序

1. 已应用 `cloudbase/migrations/20261104120000_checkin_recovery_and_photo_deletion.sql`。
2. 已部署 `api` 与 `photo-cleanup` 云函数。
3. `/health` 已返回 UUID，响应头与响应体一致。
4. attempt 查询和 challenge attempt 必填验证已通过线上 smoke test。
5. 真实 PostgreSQL 8/8 通过。

本轮迁移是增量迁移，旧服务可继续忽略新增表和字段。应用回退时先回退云函数，保留新增列和 attempt 审计数据；不得直接删除 `checkin_attempts`，否则无法解释未知历史和补偿。需要完整数据库回退时，必须先导出 attempt/photo 删除审计并经 E 批准。

## Android 验证

- Git HEAD：`102501c9f68ec970a95f15014925b38a3412ffdb`
- `:app:testDebugUnitTest`：109 项通过，0 失败 / 0 错误 / 0 跳过。
- `:app:assembleDebug`：BUILD SUCCESSFUL。
- Debug APK SHA-256：`168B25F6540B5639D76126C932E4FAB0E5982A402FB7CED90DBE315C8AA05062`。
- APK 签名 SHA-1：`2f9d2c1cb9c0db84e286c1b5fb3cb0e38f19b96d`。
- 当前没有连接 ADB 设备，因此定位、断网、强杀恢复和照片删除实机流程仍为 E 待验收。

## 尚未完成的外部条件

- 尚未获得照片核验服务地址、账号、阈值和数据处理条款。
- E 尚未使用固定 SHA 安装 APK 做实机打卡、断网、强杀和照片删除验收。

## 线上部署证据（2026-11-06）

- `api`：Deployment completed，FunctionId `lam-btbif7il`，ModTime `2026-11-06 16:22:28`
- `photo-cleanup`：Active，FunctionId `lam-f8b470dr`，ModTime `2026-11-06 16:27:47`
- timer：`photo-cleanup-every-10-minutes`，cron `0 */10 * * * * *`
- 手动调用 RequestId：`aae31edf-d4aa-4ca7-aeb7-a4106fae9bc6`
- 手动调用结果：`{"scanned":0,"cleaned":0,"failed":0}`，耗时 260ms
- 线上 health RequestId：`91281d25-5683-452d-8060-11fe61330236`，状态 200，响应头与响应体 UUID 一致
- 线上 attempt 查询：404 `CHECKIN_NOT_FOUND`
- 线上无 `X-Attempt-Id` 创建 challenge：400 `INVALID_REQUEST`

# 打卡结果查询与恢复契约

> 状态：客户端已实现；服务端路由与实现已落地，等待联调部署
> 关联：PR #10、ADR 0004、Issue #14

## 查询接口

```http
GET /checkins?attemptId=<attemptId>
Authorization: Bearer <token>
X-Request-Id: <uuid>
```

服务端只按当前用户的 `attemptId` 查询本次尝试，不拿同用户同地标的历史成功记录替代。

| 场景 | HTTP | 响应 | 客户端结果 |
|---|---:|---|---|
| 本次 attempt 已成功 | 200 | `{ requestId, data: CheckInReceipt }` | `Success` |
| 已受理但仍在处理 | 202 | `{ requestId, data: { attemptId, status: "PROCESSING" } }` | `Processing` |
| 明确没有成功记录 | 404 | `error.code = CHECKIN_NOT_FOUND` | `NotFound` |
| 路由/代理/服务/网络故障 | 其它 | 任意 | `Unavailable` |

只有“404 + `CHECKIN_NOT_FOUND`”表示明确无记录。普通 404、405、5xx、超时和未配置均表示查询故障，客户端保留材料并继续查询。

## 服务端实现约定

- `Idempotency-Key` 同时作为本次 `attemptId`，最长 128 字符。
- POST 开始前写入 `checkin_attempts(PROCESSING)`；数据库事务成功后更新为 `COMPLETED`。
- 已明确失败且未消费 challenge 的 attempt 标记为 `FAILED`，查询返回 `CHECKIN_NOT_FOUND`。
- 进程中断或响应丢失时保留 `PROCESSING`，重试只恢复同一 attempt，不能生成第二个打卡。
- 查询必须先核对当前用户与 attempt 的成功 check-in；成功记录存在时必须返回 200，不能永久返回 202。
- `PROCESSING` 超过 2 分钟且没有成功记录时转换为 `ABANDONED`，随后查询返回明确无记录。
- 数据库触发器在 check-in 写入时锁定 attempt；`ABANDONED` 或其它非 PROCESSING 状态拒绝迟到请求，避免“先 404、后又成功”。
- GET 通过 `reconcile_checkin_attempt` PostgreSQL RPC 在单个事务内完成 attempt 行锁、成功记录核对、超时判断以及封存/COMPLETED 修复，禁止拆成多个 PostgREST 请求。
- `request_hash` 覆盖地标、challenge、照片、坐标、精度、采集时间、来源和单调时钟字段。
- 同 attempt 不同请求继续返回 `IDEMPOTENCY_CONFLICT`。

## 时间与照片关联

- challenge 拍前签发，有效期 5 分钟，单次消费。
- challenge 签发时必须携带 `X-Attempt-Id` 并持久化绑定。
- 照片上传必须携带 `X-Challenge-Id` 和 `X-Attempt-Id`；服务端校验 challenge 属于当前用户和同一 attempt。
- 打卡提交校验 attempt、challenge、photo 三者关系，任一不一致返回 `PHOTO_INVALID`。
- 服务端只接受 30 秒内采集的定位；未来时间超过 5 秒拒绝。
- 客户端同时提供 `elapsedRealtimeNanos` 和 `submittedElapsedRealtimeNanos` 时，服务端比较墙钟年龄与单调区间，偏差超过 5 秒判定时钟异常。
- EXIF 只作为辅助证据，不能替代服务端时间和范围校验。

## 照片删除

```http
DELETE /checkin-photos/{photoId}
Authorization: Bearer <token>
```

- 只允许删除当前用户照片。
- 关联 attempt 为 `PROCESSING` 时返回 `CHECKIN_RESULT_UNKNOWN`，先恢复结果。
- 数据库先写 `deleted_at` 和 24 小时清理窗口，再删除存储对象。
- 删除照片不撤销已成功打卡、碎片或统计。
- 存储清理失败返回 202；补偿任务不等 `delete_after` 到期，而是立即扫描 `deleted_at IS NOT NULL AND storage_deleted_at IS NULL`。
- `delete_after` 只表示审计/备份保留窗口，不是清理任务的等待条件。

## 真实验证边界

- HTTP 路由、错误码、三态解析和删除状态由单元/契约测试覆盖。
- 真实 PostgreSQL 事务、并发和存储清理需在授权环境执行，未执行前不得标记 PASS。
- 正式多用户身份系统尚未实现；当前仍使用演示 Bearer Token 与固定演示用户。

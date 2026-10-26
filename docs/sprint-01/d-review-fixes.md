# PR #5 审核修复记录

负责人：张博淳（D）
修复日期：2026-10-25；复审补充：2026-10-26

## 修复任务

| 编号 | 优先级 | 结果 | 修复 |
|---|---|---|---|
| `S1-D-R01` | P1 | 完成 | 幂等键与请求哈希先于地标完成状态检查，竞争冲突统一回读同一 key 的持久化记录 |
| `S1-D-R02` | P1 | 完成 | challenge 消耗、checkin 写入、fragment grant 和 audit 改由同一 PostgreSQL INSERT 事务中的 trigger 完成 |
| `S1-D-R03` | P2 | 完成 | `CHECKIN_GRANTED` 由事务 trigger 写入，并保留幂等补偿路径修复历史缺失审计 |
| `S1-D-R04` | P1 | 完成 | 契约、迁移和 API 统一为三个 2x2 section、12 个地标、12 个 fragment |
| `S1-D-R05` | P1 | 完成 | 部署最终云函数并验证 UUID requestId 的响应头与响应体一致 |

### 2026-10-26 复审补充

| 编号 | 优先级 | 结果 | 修复 |
|---|---|---|---|
| `S1-D-R03A` | P2 | 完成 | 审计补写失败返回 `503 AUDIT_WRITE_FAILED`，原幂等键重试后审计恰一条 |
| `S1-D-R01A` | P2 | 完成 | 统一首次、同 key 重放、新 key 重复地标和同 key 不同请求的响应矩阵 |
| `S1-D-R04A` | P2 | 完成 | 增加完整 12 片校园目录夹具和 Android stable ID/2x2/0..3 校验 |
| `S1-D-R05A` | P2 | 完成 | 收紧 health UUID、service、版本、UTC 毫秒和 no-store 契约 |
| `S1-D-R06` | P2 | 完成 | 线上回归改为真实 challenge、照片上传和 checkin HTTP 全链路，并增加清理失败断言 |

## 根因与修复

### S1-D-R01

根因：旧实现先按 `(user_id, landmark_id)` 返回 ALREADY_COMPLETED，再检查相同
`Idempotency-Key` 的 request hash，导致相同地标只修改照片时绕过冲突判断。

修复：优先查询 idempotency key 并比较 hash；相同 key 配不同请求统一返回
`409 IDEMPOTENCY_CONFLICT`。数据库 trigger 同时按 key 加 advisory transaction lock，
竞争请求回读同一持久化记录。

### S1-D-R02

根因：challenge 在打卡写入前单独 PATCH 为已使用。后续 INSERT 失败时没有事务回滚，
原 challenge 无法重试。

修复：API 先完成照片、精度、地标和打卡区域校验，再执行单条 checkins INSERT：

- `checkin_00_guard_idempotency`：串行化同一 user/key 的并发请求。
- `checkin_10_consume_challenge`：在同一 INSERT 事务内消耗 challenge。
- `checkin_20_materialize_rewards`：在同一事务内创建 fragment grant 和审计。
- 任一前触发器、checkins INSERT 或后触发器失败，challenge 与打卡整体回滚。

### S1-D-R03

根因：恢复路径只重建 receipt，没有检查缺失的 `CHECKIN_GRANTED` 审计。

修复：新增 `uq_audit_logs_checkin_granted(event_type, entity_id)` 唯一约束；
事务 trigger 正常写入审计，恢复路径通过 `ensureCheckInGrantedAudit()` 做幂等补偿。
如果补偿写入仍失败，接口现在返回可重试的
`503 AUDIT_WRITE_FAILED`，不再把缺审计的打卡报告为成功。

### S1-D-R04

修复后目录：

- puzzle：`puzzle_liangxiang_campus`
- section：`section_north`、`section_east`、`section_south`
- 每区 2x2，共 12 个地标、12 个 fragment、12 个 task
- landmark 包含 `campusZone`、`fragmentId`、`pieceIndex`、
  `coordinateVerified` 和 `checkInArea`
- `checkInArea` 支持 circle 与 polygon

契约流程：

- Issue #6：`[S1][Contract] 三组 2x2 校园地标与拼图模型`
- PR #5 的首个修复提交 `7b79d20`：只包含契约与流程记录，供 B/C/E 先审核
- B 的固定实施基线：`feat/map-fog-of-war@1320ce3e45dec8ea3c811761d7e27c435b3f0e90`
- 完整目录夹具：`docs/api/fixtures/campus-catalog.json`

重复打卡响应矩阵：

| 场景 | HTTP | 结果 |
|---|---:|---|
| 首次 | 200 | `data.status=COMPLETED` |
| 同 key、同请求 | 200 | 原 receipt，`data.status=COMPLETED` |
| 新 key、同用户同地标 | 200 | 原 receipt，`data.status=ALREADY_COMPLETED` |
| 同 key、不同请求 | 409 | `IDEMPOTENCY_CONFLICT` |

## 验证结果

Node.js：`v24.20.0`

```text
npm run api:check
Test Files  7 passed | 1 skipped
Tests       35 passed | 6 skipped
OpenAPI contract and canonical campus fixture are valid

npm run api:test:database
Test Files  1 passed
Tests       6 passed

npm run api:smoke
Requests    1 passed
Tests       4/4 passed

npm run build:cloud
deploy/dist/main.cjs  838.5 KB
```

## 真实数据库证据

迁移状态：

```text
20261020073459 init_schema
20261020073548 seed_first_week
20261020074358 add_landmark_display_fields
20261025090000 harden_checkin_transactions
20261025093000 align_campus_catalog
```

模型计数：

```json
{
  "active_landmarks": 12,
  "sections": 3,
  "fragments": 12,
  "tasks": 12
}
```

校区计数：

```text
EAST  4  {0,1,2,3}
NORTH 4  {0,1,2,3}
SOUTH 4  {0,1,2,3}
```

真实 PostgreSQL 用例覆盖：

- 相同 key 修改照片返回 `IDEMPOTENCY_CONFLICT`
- pre-insert 校验失败不消耗 challenge
- 奖励物化失败时 challenge、checkin 和 grant 整体回滚
- 恢复路径补齐 audit 且 checkin/grant/audit 均为 1
- 并发不同 key 最终只有 1 个 checkin、1 个 grant
- 真实 API 模型返回 12 个地标、3 个 section、12 个 fragment
- 完整夹具与 Android 稳定 ID、三个唯一校区、每区 2x2、索引 0..3 一致

`review-checkin-regression.test.ts` 额外覆盖：

- 无效输入不消耗 challenge，不写 checkin、grant 或 audit
- 审计补写首次失败后返回可重试错误，同 key 恢复后审计恰一条

测试后清理检查：

```json
{
  "test_users": 0,
  "test_checkins": 0,
  "test_grants": 0,
  "test_audits": 0
}
```

## 线上部署与回归

线上脚本通过真实 HTTP 依次创建 challenge、上传两个合成 JPEG、提交 checkin，
再执行同 key 重放、同 key 修改照片冲突和新 key 重复地标，不只直接写数据库。

- 云函数：`api`
- 函数 ID：`lam-btbif7il`
- 状态：`Active`
- 运行时：`Nodejs20.19`
- 最终部署：`2026-10-26 18:56:50`（Asia/Shanghai）
- 运行时提交：`08ee4aa`

线上 health：

```text
HTTP 200
UUID requestId
X-Request-Id == response.requestId
固定请求 UUID 被原样回传
Cache-Control 包含 no-store
```

请求 ID 证据：

```text
固定 UUID：8d7f1f6b-3ea3-4ad0-9d74-ecd851e0d880
```

线上完整打卡回归：

```json
{
  "requestIds": {
    "challenge": "abc7c370-3cd0-4af2-875b-917f52fe9d93",
    "first": "349092c9-a5e6-4de9-988a-f32a5beb9084",
    "replay": "f4c90a32-0ac5-45cb-8e98-e08764a3ec46",
    "conflict": "62c4d096-9bc0-444f-8f64-5b7d77512ce6",
    "duplicate": "66dfb5e9-b5a0-43b4-be09-c8593dcfa5af"
  },
  "landmarkId": "lmk_east_gantang",
  "firstStatus": "COMPLETED",
  "replayStatus": "COMPLETED",
  "duplicateStatus": "ALREADY_COMPLETED",
  "conflictCode": "IDEMPOTENCY_CONFLICT",
  "challengeUsed": true,
  "databaseCounts": {
    "checkins": 1,
    "grants": 1,
    "audits": 1
  }
}
```

回归结束后测试数据已清理，没有遗留测试 checkin、grant、audit 或用户。

# PR #5 审核修复记录

负责人：张博淳（D）
修复日期：2026-10-25

## 修复任务

| 编号 | 优先级 | 结果 | 修复 |
|---|---|---|---|
| `S1-D-R01` | P1 | 完成 | 幂等键与请求哈希先于地标完成状态检查，竞争冲突统一回读同一 key 的持久化记录 |
| `S1-D-R02` | P1 | 完成 | challenge 消耗、checkin 写入、fragment grant 和 audit 改由同一 PostgreSQL INSERT 事务中的 trigger 完成 |
| `S1-D-R03` | P2 | 完成 | `CHECKIN_GRANTED` 由事务 trigger 写入，并保留幂等补偿路径修复历史缺失审计 |
| `S1-D-R04` | P1 | 完成 | 契约、迁移和 API 统一为三个 2x2 section、12 个地标、12 个 fragment |
| `S1-D-R05` | P1 | 完成 | 部署最终云函数并验证 UUID requestId 的响应头与响应体一致 |

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

## 验证结果

Node.js：`v24.20.0`

```text
npm run api:check
Test Files  7 passed | 1 skipped
Tests       32 passed | 6 skipped
OpenAPI contract is valid

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

- 云函数：`api`
- 函数 ID：`lam-btbif7il`
- 状态：`Active`
- 运行时：`Nodejs20.19`
- 首次部署：`2026-10-25 23:35:25`（Asia/Shanghai），运行时提交 `71c5ca5`
- 最终重建部署：`2026-10-25 23:37:16`（Asia/Shanghai），head 为 `da32d08`；仅后续文档变化

线上 health：

```text
HTTP 200
UUID requestId
X-Request-Id == response.requestId
固定请求 UUID 被原样回传
```

请求 ID 证据：

```text
固定 UUID：8d7f1f6b-3ea3-4ad0-9d74-ecd851e0d880
```

线上完整打卡回归：

```json
{
  "requestIds": {
    "first": "a648c75c-ff53-4e19-a7b1-9b2d8f121c1d",
    "conflict": "c119d16c-6593-4628-aa59-134d61ea8bb4",
    "replay": "0e71b6e2-ab70-4bc8-a573-e4292a87a4a3"
  },
  "landmarkId": "lmk_east_gantang",
  "firstStatus": "COMPLETED",
  "replayStatus": "COMPLETED",
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

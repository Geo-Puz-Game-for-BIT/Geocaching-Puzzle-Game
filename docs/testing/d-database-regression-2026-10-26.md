# D 真实 PostgreSQL 回归记录

日期：2026-10-26
负责人：张博淳（D）
Node.js：`v24.20.0`

命令：

```powershell
npm run api:test:database
```

脱敏结果：

```text
Test Files  1 passed (1)
Tests       6 passed (6)
Duration    12.06s

✓ canonical API model exposes three campus sections and twelve landmarks
✓ same key with a changed photo is a conflict and keeps one persisted result
✓ failed pre-insert validation does not consume the challenge
✓ transaction rolls back challenge and check-in when reward materialization fails
✓ retry repairs a missing audit while preserving one check-in and one grant
✓ concurrent different keys still produce one check-in and one grant
```

测试夹具：

- `docs/api/fixtures/campus-catalog.json`
- 12 个地标
- 3 个 section
- 每区 2x2
- 每区 `pieceIndex=0..3`
- 12 个 fragment
- 12 个 task

清理断言：

```json
{
  "test_users": 0,
  "test_checkins": 0,
  "test_grants": 0,
  "test_audits": 0
}
```

本记录不包含 Token、API Key、数据库密码、真实照片或真实轨迹。

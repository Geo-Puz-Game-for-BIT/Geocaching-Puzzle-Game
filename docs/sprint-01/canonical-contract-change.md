# S1-D-04 契约变更：校园地标与三组 2x2 拼图

负责人：张博淳（D）
契约版本：`1.1.0-poc`
状态：已收到 B 固定提交 `1320ce3e45dec8ea3c811761d7e27c435b3f0e90` 的数据确认；契约提交位于原 PR #5 的首个修复提交 `7b79d20`，由 B/C/E 审核

## 统一来源

- `docs/adr/0002-campus-puzzle-reveal.md`
- `android-app/.../core/model/DomainModels.kt`
- `android-app/.../core/model/LiangxiangTreasureCatalog.kt`
- 群内确认的三组 `2x2` 布局

## 固定模型

| 对象 | 规则 |
|---|---|
| 拼图 | `puzzle_liangxiang_campus` |
| 区块 | `section_north`、`section_east`、`section_south` |
| 地标 | `lmk_<zone>_<name>`，共 12 个 |
| 碎片 | `fragment_<zone>_<name>`，共 12 个 |
| 任务 | `task_<zone>_<name>`，共 12 个 |
| 网格 | 每个区块 `2x2` |
| 坐标 | GCJ02，当前全部 `coordinateVerified=false` |
| 打卡区域 | `circle` 或 `polygon`，多边形使用米制局部偏移 |

## 契约变化

- `Landmark` 增加 `campusZone`、`fragmentId`、`pieceIndex`、`coordinateVerified` 和 `checkInArea`。
- `Landmark.checkInRadiusMeters` 被 `checkInArea` 取代。
- `PuzzleProgress` 从单张 3x3 改为按 `sections` 分组的三组 2x2。
- `FragmentGrant.pieceIndex` 范围改为 `0..3`。
- 地标、拼图和碎片 ID 使用 B/C Android 目录中的稳定 ID。

## 合并顺序

1. B 已用固定提交 `1320ce3e45dec8ea3c811761d7e27c435b3f0e90` 确认字段、ID、坐标与索引。
2. 项目协调人于 2026-10-28 明确：同一审核项的契约与实现保留在原 PR #5，不再恢复独立契约 PR #7。
3. 审核范围按提交顺序区分：`7b79d20` 为契约与流程记录，后续提交为实现、测试、迁移和部署证据。
4. B/C/E 对最终 HEAD 集中复审；最终部署后验证 UUID requestId、响应头与响应体一致。

真实 Token、数据库密码和未脱敏位置数据不进入仓库。

# API 契约

第 1 周客户端与云端共同遵守的 HTTP 契约。

- 负责人：张博淳（D）
- 契约版本：`1.1.0-poc`
- 正式契约：[openapi.yaml](./openapi.yaml)
- 服务端实现：`cloudbase/functions/api/`
- 客户端调用方：B 的地图与主流程、C 的相机与上传流程
- 团队接入与凭据共享规则：[CloudBase 协作接入包](../sprint-01/cloudbase-collaboration-handoff.md)

坐标统一为 `GCJ02`，时间统一为 UTC ISO 8601，距离字段统一以 `Meters` 结尾，JSON 字段统一使用 `camelCase`。

## 校园目录模型

地标、拼图和碎片与 B/C 的 Android 领域模型保持一致：

- 拼图 ID：`puzzle_liangxiang_campus`
- 校区区块：`section_north`、`section_east`、`section_south`
- 每个区块固定 `2x2`，共 12 个地标和 12 个碎片
- 地标 ID：`lmk_<zone>_<name>`
- 碎片 ID：`fragment_<zone>_<name>`
- 任务 ID：`task_<zone>_<name>`
- 地标响应包含 `campusZone`、`fragmentId`、`pieceIndex`、`coordinateVerified` 和 `checkInArea`
- `checkInArea` 使用 `circle` 或 `polygon` 判别联合，多边形顶点使用相对地标的米制偏移

当前开发坐标均为 `coordinateVerified=false`。正式开放打卡前必须完成实地核验。

修改契约前先提交 `[S1][Contract]` Issue，并通知 B、C、D、E；涉及 UI 时同时通知 A。

# D 第一周工作日志

## 工作范围

- 负责人：张博淳（D）
- 周期：2026-10-20 至 2026-10-25
- 主责目录：`cloudbase/`、`docs/api/`、`docs/sprint-01/`
- 云端环境：`cloud-d0g0u4r64556b8177`

## 完成记录

| 日期 | 工作内容 | 结果 |
|---|---|---|
| 2026-10-20 | 创建 TypeScript HTTP API 工程和 CloudBase PostgreSQL 环境 | 完成 |
| 2026-10-20 | 建立表、约束、索引和第一周测试数据 | 完成 |
| 2026-10-20 | 创建私有照片桶 `checkin-photos` | 完成 |
| 2026-10-20 | 实现 health、landmarks、challenge、photo、checkins、puzzle 接口 | 完成 |
| 2026-10-20 | 实现 challenge 单次使用、照片校验、打卡和碎片发放 | 完成 |
| 2026-10-20 | 实现重复请求幂等和幂等键冲突处理 | 完成 |
| 2026-10-20 | 建立错误码、请求日志和 audit_logs 审计记录 | 完成 |
| 2026-10-21 | 补齐 `docs/api/openapi.yaml` 和 `1.0.0-poc` 契约版本 | 完成 |
| 2026-10-21 | 对齐 `healthHandler`、`listNearbyLandmarks`、`storeCheckInPhoto` 等固定命名 | 完成 |
| 2026-10-21 | 补齐正常、范围外、低精度、过期、缺照片、连续点击、超时重试测试 | 完成 |
| 2026-10-21 | 重新部署云函数并完成云端回归 | 完成 |
| 2026-10-25 | 修复相同幂等键修改照片未冲突 | `S1-D-R01` 完成 |
| 2026-10-25 | 用 PostgreSQL trigger 事务统一 challenge、checkin、grant、audit | `S1-D-R02/R03` 完成 |
| 2026-10-25 | 对齐北/东/南三组 2x2，共 12 个地标和 12 个碎片 | `S1-D-R04` 完成 |
| 2026-10-25 | 使用 Node.js 24.20.0 跑真实 PostgreSQL 与线上完整打卡回归 | `S1-D-R05` 完成 |

## 交付物

- `cloudbase/functions/api/`
- `cloudbase/shared/error-codes.ts`
- `cloudbase/shared/photo.ts`
- `cloudbase/migrations/`
- `cloudbase/tests/`
- `docs/api/openapi.yaml`
- `docs/api/README.md`
- `docs/sprint-01/d-cloudbase-acceptance.md`

## 验证结果

- 自动化测试：32 个单元/契约用例通过；6 个真实 PostgreSQL 用例通过
- OpenAPI：Redocly 校验通过
- 依赖审计：根工作区现有工具链仍有 16 条 advisory，主要来自 Bruno/Swagger 依赖链；本次未使用 `audit fix --force` 做破坏性升级
- 云函数：`api`，HTTP，Node.js 20.19，状态 Active
- 数据库迁移：5/5 已应用
- 重复打卡：数据库保持 1 条 checkin 和 1 条 fragment grant
- 日志：可通过 requestId 查到访问日志、START、Response、END 和 Report
- 审计：事务内生成，恢复路径幂等补齐

## 备注

契约阶段作为 PR #5 的首个修复提交 `7b79d20`，实现与契约在同一 PR 按提交顺序审核，不直接修改 `main` 或 `develop`。

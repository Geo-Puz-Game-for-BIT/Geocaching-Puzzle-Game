# 打卡结果查询与恢复契约（客户端已落地，服务端待 D）

> 状态：**客户端已实现**（R1），服务端路由与语义**待 D 落地**
> 关联：PR #10（C 相机闭环）、ADR 0004（照片核验）、`docs/sprint-01/2026-11-03-相机闭环整改-CN回复.md`

## 背景

客户端提交打卡后可能遇到网络中断 / 服务端 5xx / 挑战过期，需要查询「本次 attempt 是否已成功」，据此决定恢复、重试或换新尝试。当前服务端接口清单只有 `POST /checkins`，无查询路由（`GET /checkins` 现返回 405）。

## 查询接口（新增，待 D 落地路由）

```http
GET /checkins?attemptId=<attemptId>
Authorization: Bearer <DEMO_AUTH_TOKEN>
X-Request-Id: <uuid>
```

按 **attempt** 身份识别本次尝试结果，**不得**只拿「某地标的历史成功」替代本次 attempt 的结果。

## 响应语义与客户端判定（有类型四态）

| 场景 | HTTP | 响应 | 客户端 `CheckInQueryResult` |
|---|---|---|---|
| 本次 attempt 已成功 | 200 | `{ requestId, data: { checkInId, status, fragmentGrant } }` | `Success` |
| 已受理未落库 | 202 | `{ requestId, data: { status: "PROCESSING" } }` | `Processing` |
| 明确无记录 | 404 | `{ requestId, error: { code: "CHECKIN_NOT_FOUND", ... } }` | `NotFound` |
| 查询故障（普通 404 / 405 / 5xx / 超时 / 未配置） | 其它 | 任意 | `Unavailable` |

> **关键**：只有「404 + 错误码 `CHECKIN_NOT_FOUND`」才判为 `NotFound`（明确无记录）。
> 普通路由 404、代理 404、405、5xx、超时、以及「baseUrl 未配置」一律归为 `Unavailable`（查询故障，结果未知）。
> 未配置不得作为「明确未成功」的安全新尝试依据。

## 三态语义（客户端据此决定下一步）

| 查询结果 | 客户端语义 | 允许的动作 |
|---|---|---|
| `Success` | 已成功 | 发奖 / 清草稿 |
| `NotFound` | 明确未成功 | 可安全换新 `attemptId` 重拍（`CONFIRMED_ABSENT`） |
| `Processing` / `Unavailable` | 结果未知/处理中 | 保留照片与冻结内容，仅可同 attempt 再次查询恢复（`UNKNOWN`） |

## 查询无记录的竞态（需 D 保证）

「提交成功但查询却无记录」的窗口必须由服务端消除：`POST /checkins` 的事务提交与 `GET /checkins` 的可见性需保持一致（同库一致读 / 同事务「校验→消费→建记录」）。在此保证成立前，客户端对「提交结果未知 + 查询无记录」保持**未知**，不删除旧照、不换新 attempt。

## 安全新尝试条件

仅当「查询**确认无记录**（`NotFound`）」或「服务端明确拒绝」才允许换新 `attemptId` 并删除旧照；查询故障/处理中/未知一律不得触发新尝试。

## 拍前 challenge 与照片关联

- `POST /checkin-challenges` 签发的 challenge 与后续上传的 photo 通过 **attempt** 关联：`POST /checkins` 请求携带 `attemptId`（幂等键）、`challengeId`、`photoId`、冻结定位快照。
- 服务端「校验→消费→建记录」在同一事务内完成（challenge consumed 语义，ADR 0004），消费即失效，5 分钟过期。

## 客户端已落地（R1）

- `CheckInApi.queryCheckIn(attemptId): CheckInQueryResult`：HTTP 层按上表解析，核验 `CHECKIN_NOT_FOUND`，未配置/普通 404/405/5xx/超时归 `Unavailable`。
- `CheckInRepository.queryCheckIn(attemptId)`：接口签名按 attempt。
- `CheckInSubmitCoordinator.recover(attemptId)`：独立查询入口（不重放 POST、不依赖本地冻结内容），供 UNKNOWN 草稿恢复。
- `CheckInQueryResult` 四态模型见 `core/checkin/CheckInQueryResult.kt`。

## 服务端待办（D）

1. 新增 `GET /checkins?attemptId=` 路由（当前 405）。
2. 返回 200 / 202 / 404(`CHECKIN_NOT_FOUND`) 三态语义（含 PROCESSING）。
3. challenge `consumed` 语义（同事务「校验→消费→建记录」）。
4. `geo.ts` 精度 50m→30m（`validateLocationAccuracy` / `isWithinRadius` / `assertWithinCheckInArea`）。

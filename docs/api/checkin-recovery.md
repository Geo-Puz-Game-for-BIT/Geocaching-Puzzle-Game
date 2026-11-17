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

### PROCESSING 不得永久卡住（D-1/D-2）

打卡成功与 attempt 状态更新若分属不同请求，中间中断会留下「成功记录已存在、attempt 仍 PROCESSING」。要求服务端：

- `GET /checkins?attemptId=` 须**核对同用户 + 同 attempt 的成功记录**，命中即返回 `200` 成功回执（`checkInId`/`fragmentGrant`），**不得**因 attempt 状态为 PROCESSING 就永久返回 `202`。
- 若中断发生在真正打卡之前（孤儿 PROCESSING，无成功记录），须提供明确的恢复/补偿/封存语义，**不能**简单超时返回 `404` 后又允许迟到请求成功。

## 客户端语义（F3 定稿：终态保证成立前，NotFound ≠ 可安全新尝试）

`CheckInQueryResult.NotFound` 仅表示「此刻未查到记录」，**不等于**「旧 POST 已可靠结束、不会再成功」。在服务端交付「可靠终态 + 在途 POST 竞态测试」之前，客户端对 `NotFound` 的处置如下：

| 上下文 | 触发场景 | `NotFound` 处置 |
|---|---|---|
| 提交结果未知（`onUnknownOutcome`） | POST 网络中断 / 5xx 后查询 | 保持 `UNKNOWN`（不删照、不换 ID） |
| 恢复入口（`recover`） | UNKNOWN 草稿「重试恢复」 | 保持 `UNKNOWN`（**F3，本轮修正**） |
| 服务端明确拒绝（`CHALLENGE_EXPIRED` / `IDEMPOTENCY_CONFLICT`） | 提交被明确驳回后查询 | 可 `CONFIRMED_ABSENT`（换新 ID） |

> 关键变化：`recover` 对 `NotFound` 由「CONFIRMED_ABSENT」改为「UNKNOWN」，与 `onUnknownOutcome` 一致，消除「同一次无记录，首次查询保持未知、恢复查询却放开新 ID」的矛盾。仅当 D 落地可靠终态（含在途竞态测试）后，才允许把 `NotFound` 作为「明确未成功」放行新尝试。

`Processing` / `Unavailable` 在任何上下文一律保持 `UNKNOWN`（保留照片与冻结内容，仅可同 attempt 再次查询恢复）。

## 查询无记录的竞态（需 D 保证）

「提交成功但查询却无记录」的窗口必须由服务端消除：`POST /checkins` 的事务提交与 `GET /checkins` 的可见性需保持一致（同库一致读 / 同事务「校验→消费→建记录」）。在此保证成立前，客户端对「提交结果未知 + 查询无记录」保持**未知**，不删除旧照、不换新 attempt；`recover` 入口同样保持未知（F3）。

需额外覆盖：POST 尚在执行/尚未到达、GET 先返回无记录、旧 POST 随后写入的**在途竞态**——仅写「同库一致读/事务」不足以排除请求先后与在途处理窗口，需 D 交付对应竞态测试。

## 安全新尝试条件

- **服务端明确拒绝**（`CHALLENGE_EXPIRED` / `IDEMPOTENCY_CONFLICT` 等明确 4xx）→ 可换新 `attemptId` 并删除旧照。
- **查询 `NotFound`**：终态保证成立前**不**作为安全新尝试依据（保持 `UNKNOWN`）；待 D 落地可靠终态 + 在途竞态测试后，再由 `recover`/`onUnknownOutcome` 放行。
- 查询 `Processing` / `Unavailable` / 未知一律不得触发新尝试。

## 拍前 challenge 与照片关联

- **challenge 拍前绑定 attempt（C-4）**：`POST /checkin-challenges` 请求携带 `landmarkId` + `attemptId`，签发的 challenge 即与本次 attempt 绑定；客户端在**拍摄前**用候选 `attemptId` 领取 challenge，**拍摄成功后**才正式采用该 `attemptId` 提交，保证 `attemptId ↔ challengeId` 一致（避免「challenge 绑旧 ID、上传用新 ID」）。
- `POST /checkins` 请求携带 `attemptId`（幂等键）、`challengeId`、`photoId`、冻结定位快照。
- 服务端「校验→消费→建记录」在同一事务内完成（challenge consumed 语义，ADR 0004），消费即失效，5 分钟过期。
- **照片上传须绑定本次 challenge（D-3）**：`POST /checkin-photos` 必须携带并校验 `challengeId`，不得「可不传 challenge」就通过；`POST /checkins` 必须校验 `attemptId ↔ challengeId ↔ photoId` 三者关系，拒绝「未绑定照片 / 非本次 challenge 的照片」提交。

## 客户端已落地（R1）

- `CheckInApi.queryCheckIn(attemptId): CheckInQueryResult`：HTTP 层按上表解析，核验 `CHECKIN_NOT_FOUND`，未配置/普通 404/405/5xx/超时归 `Unavailable`。
- `CheckInRepository.queryCheckIn(attemptId)`：接口签名按 attempt。
- `CheckInSubmitCoordinator.recover(attemptId)`：独立查询入口（不重放 POST、不依赖本地冻结内容），供 UNKNOWN 草稿恢复；对 `NotFound` 保持 `UNKNOWN`（F3）。
- `CheckInQueryResult` 四态模型见 `core/checkin/CheckInQueryResult.kt`。

## 服务端待办（D）

1. 新增 `GET /checkins?attemptId=` 路由（当前 405）。
2. 返回 200 / 202 / 404(`CHECKIN_NOT_FOUND`) 三态语义（含 PROCESSING）。
3. challenge `consumed` 语义（同事务「校验→消费→建记录」）。
4. `geo.ts` 精度 50m→30m（`validateLocationAccuracy` / `isWithinRadius` / `assertWithinCheckInArea`）。
5. **可靠终态语义 + 在途 POST 竞态测试**（F3）：只有「可靠结束并阻止旧请求后续生效」才提供可安全放弃的终态；否则 `CHECKIN_NOT_FOUND` 仅表示「暂未查到」，客户端继续保护旧 attempt。
6. **成功后查询回执（D-1）**：GET 核对同用户同 attempt 的成功记录并返回 200 回执，不得永久 202。
7. **孤儿 PROCESSING 收敛（D-2）**：中断发生在真正打卡之前时，提供明确恢复/补偿/封存，不得超时 404 后又允许迟到请求成功。
8. **照片绑定挑战（D-3）**：上传必须带 challenge 并校验；提交校验 attempt↔challenge↔photo 关系。
9. **删除补偿与数据库证据（D-4）**：补偿筛选不得等满 24h 才启动，需补调度证据与真实数据库测试、迁移、部署版本。

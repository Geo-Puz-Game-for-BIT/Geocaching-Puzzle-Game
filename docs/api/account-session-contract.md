# 账号会话与业务隔离契约（C↔D1）

- 负责人：C（业务隔离与恢复），账号链路统筹 D
- 状态：**客户端已落地接口与纯规则，D1 提供真实实现前由 DemoAccountSession 兜底**
- 关联：`docs/api/README.md`、`docs/api/checkin-recovery.md`、`docs/design/`（账号/进度方案）

## 1. 目标

账号是全新引入的能力。C 的相机、上传、恢复、照片、草稿、探索、拼图此前均为「账号无感知」单例。
本契约约定 C 与 D1 的账号边界，使客户端在 D1 就绪前即可落地：

1. **动态鉴权**：网络层每次请求取「当前登录用户」的 token，不再依赖静态 `DEMO_AUTH_TOKEN`。
2. **存储分区**：草稿、探索进度、封存记录按 `accountId` 命名空间隔离，旧演示数据不自动归首个登录人。
3. **旧回调隔离**：换号/退出后，迟到响应不得更新新账号页面、不得踢回登录页、不得污染新账号草稿。
4. **退出前封存**：存在「服务端可能已成功、客户端尚未恢复」的在途打卡时，退出前先封存关键证据。
5. **分模块恢复**：地图进度、探索记录、拼图详情各自有加载/成功/失败/重试状态，互不污染。

## 2. 核心模型（`core/account`）

| 类型 | 字段/值 | 说明 |
|---|---|---|
| `AccountIdentity` | `accountId: String`, `sessionGeneration: Long` | 稳定账号标识 + 会话代次（每次登录/换号自增） |
| `AccountAuth` | `token: String`, `identity: AccountIdentity` | 请求鉴权；token 仅用于 `Authorization: Bearer` |
| `AccountState` | `SIGNED_OUT / SIGNING_IN / SIGNED_IN / LOCKED / INVALIDATED / OFFLINE` | 会话状态机 |
| `AccountContext` | `identity?`, `state`, `auth?` | 单一事实源快照；派生 `canUseProtected` / `canReadCacheOffline` / `isBlocked` |

## 3. `AccountSession` 接口（C↔D1 契约点）

```kotlin
interface AccountSession {
    val context: StateFlow<AccountContext>
    fun currentAuth(): AccountAuth?
    fun currentIdentity(): AccountIdentity?
}
```

C 不另维护全局登录态；前端页面也不各自维护一份「已登录/已切换」判断，统一依赖此接口。
D1 落地真实登录/登出/校验后提供实现；当前 `DemoAccountSession` 将 `BuildConfig.DEMO_AUTH_TOKEN` /
`DEMO_USER_ID` 映射为固定单账号会话（`sessionGeneration = 0`、`SIGNED_IN`），替换实现时调用方无需改动。

动态鉴权适配：

```kotlin
fun interface AuthTokenProvider { fun currentToken(): String? }
fun AccountSession.asTokenProvider(): AuthTokenProvider
```

`CheckInApi`、`CloudBaseApiClient` 已改为接收 `AuthTokenProvider`，每次请求取当前 token；
未登录/锁定/失效返回 `null` → 网络层抛 `AUTH_REQUIRED`（`retryable=false`）。

## 4. 存储分区（legacy 规则）

- 命名空间：账号数据前缀 `acc.<accountId>.`；无账号（历史演示数据）无前缀，归 legacy。
- 首个登录人读的是**新命名空间**（为空），旧演示数据不自动归新账号；是否清空/标记 legacy 由 E/D 拍板。
- 涉及 store：`CheckInDraftStore`（草稿）、`ExplorationProgressStore`（已完成地标集合）、
  `CheckInSealStore`（封存记录，键前缀 `seal.<accountId>.`）。

## 5. 旧回调隔离

- 请求发起时经 `accountSession.currentIdentity()` 冻结绑定（`AccountCallbackGuard.bind`）。
- 响应到达时 `AccountCallbackGuard.isStale(binding, current)`：
  - 未绑定（无账号时代 demo 请求）→ 不判 stale；
  - 当前无身份（已退出）/ 账号不同 / 代次不同 → stale，**丢弃**；
  - 同账号同代次 → 通过。
- 覆盖点：上传照片、POST 提交、查询恢复三条链路的响应回写，均先过隔离再更新 UI/草稿。

## 6. 退出前封存（UNKNOWN/在途草稿 × 退出）

`AccountLeaveRules.classify(draft, accountId, now)`：

| 草稿状态 | 结论 |
|---|---|
| 无草稿 / `EMPTY` | `NoPending`（直接远程退出） |
| `CONFIRMED` 或「明确未成功且未发送」 | `ConfirmedTerminal`（可安全退出） |
| `requestSent=true` / `UNKNOWN` / `UPLOADING` | `PreSeal`（先落盘封存，再进入远程退出） |

封存记录 `CheckInSeal` 只属于原账号；旧账号重新有效登录后，用**新会话**查询其原 `attemptId`，
取得可靠终态后按原规则更新并清理。封存持久化失败（`SealFailed`）必须阻止删除关键证据并说明原因。

## 7. 分模块恢复

`LoadState<T>`：`Loading` / `Success(data, empty)` / `Failure(error)`。
关键语义：**本地默认空集合不得代表「全部加载结果」**，必须区分「未加载/加载失败」与「确实为空」。

- `ExplorationProgressStore.completedLandmarkIdsLoadState: StateFlow<LoadState<Set<String>>>`：
  直接映射原始 DataStore 流，首次落盘值到达前 `Loading`，读取失败 `Failure`，成功后 `Success(empty=真零)`；
  账号切换或 `retryProgressLoad()` 重新进入 `Loading` 并重读。
- 拼图页：`Loading` → 加载态；`Failure` → 错误 + 「重试」；`Success` → 正常进度（含 `0` 为真零）。
- 某模块失败不注销账号、不清零其他已确认结果。

## 8. 待 D1 落地（本契约未覆盖，客户端已预留扩展点）

- 真实登录/登出/校验、`sessionGeneration` 自增、`LOCKED`/`INVALIDATED`/`OFFLINE` 状态迁移。
- `GET /checkins?attemptId=` 按当前 token 的 userId 过滤，防 attemptId 跨账号串数据（见 `checkin-recovery.md`）。
- 账号变化时旧账号照片/草稿/探索/拼图的归属切换与「退出 → 换号」收口顺序。

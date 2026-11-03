# 第一周 CloudBase 团队接入包

> 负责人：张博淳（D）
>
> 本文件可直接发团队群。真实 Token、数据库密码和服务端 API Key 不在此文件中。

## CloudBase 资源

| 资源 | ID / 值 |
|---|---|
| 环境 ID | `cloud-d0g0u4r64556b8177` |
| 环境别名 | `cloud` |
| 区域 | `ap-shanghai` |
| API 域名 | `cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com` |
| API 路径前缀 | `/api/v1` |
| API 基础地址 | `https://cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com/api/v1` |
| 健康检查地址 | `https://cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com/api/v1/health` |
| 云函数名称 | `api` |
| 云函数 ID | `lam-btbif7il` |
| 云函数类型 | HTTP |
| 运行时 | `Nodejs20.19` |
| PostgreSQL 实例 | `postgres-fvpp0eu8` |
| 数据库名 | `postgres-fvpp0eu8` |
| 照片桶 ID | `checkin-photos` |
| 契约版本 | `1.1.0-poc` |

`API 基础地址`只用于客户端、Postman 或 Bruno 拼接接口路径，不是可直接浏览的页面。浏览器验证服务时请打开“健康检查地址”。

## 测试数据 ID

| 对象 | ID / 值 |
|---|---|
| 测试用户 | `usr_demo01` |
| 测试地标 | `lmk_north_lake` |
| 测试任务 | `task_north_lake` |
| 测试拼图 | `puzzle_liangxiang_campus` |
| 测试碎片 | `fragment_north_lake` |
| 测试打卡 | `chk_1a264070-7cef-4718-b66a-73aa985986ef` |
| 测试照片 | `pho_11ed3ebb-c223-4cc3-81c0-ae77c0b9c3b3` |
| 测试碎片发放 | `grant_8aed6f71-c3f7-4756-8295-072b870c5ec6` |
| 地标坐标 | `39.7366, 116.1666` |
| 坐标系 | `GCJ02` |
| 打卡区域 | `circle / 55 Meters` |

## 数据编辑器字段分享规则

数据编辑器中的业务 ID 可以共享，因为 B/C/E 联调时需要它们对照请求和日志：

- `users.id`
- `landmarks.id`
- `tasks.id`
- `puzzles.id`
- `fragments.id`
- `checkins.id`
- `photos.id`
- `fragment_grants.id`
- `challenges.id`
- `photos.object_key`
- 每次请求的 `requestId`

测试数据中已固定的 ID 已列在上方。`challenges.id` 是动态创建的，每次联调重新生成，不需要共享固定值。

以下内容不需要发给群：

- `audit_logs.id` 这类仅用于数据库内部排序的自增 ID。
- 数据库内部 OID、连接信息和账号密码。
- Token、API Key 和其他鉴权凭据。
- 真实用户 ID、真实照片 ID、真实位置和真实打卡记录。

排障时优先共享 `requestId`、业务对象 ID 和时间戳，而不是数据库连接信息。

照片对象路径：

```text
checkins/<userId>/<photoId>.jpg
```

## 接口列表

```text
GET  /health
GET  /landmarks
POST /checkin-challenges
POST /checkin-photos
POST /checkins
GET  /puzzles/{puzzleId}
```

除 `/health` 外，请求需要：

```http
Authorization: Bearer <DEMO_AUTH_TOKEN>
X-Request-Id: <uuid>
```

`POST /checkins` 还必须携带：

```http
Idempotency-Key: <uuid>
```

完整字段、示例和错误码见 [openapi.yaml](../api/openapi.yaml)。

## 组员使用方式

| 成员 | 使用内容 |
|---|---|
| A | API 状态和错误状态，用于页面与异常态设计 |
| B | API 地址、OpenAPI、测试 Token、地标和打卡接口 |
| C | 照片上传、JPEG/5 MiB 限制、objectKey 规则、测试 Token |
| D | CloudBase 环境、数据库、存储、函数和部署维护 |
| E | 日志查询、测试 Token、契约和验收证据 |

B/C 只需要 HTTP API 和测试 Token，不需要数据库密码或 CloudBase 服务端 API Key。

E 按 requestId 查日志：

```powershell
tcb logs search -e cloud-d0g0u4r64556b8177 --query "<requestId>" --timeRange 2h --json
```

## 私密信息

以下内容单独通过密码管理器或私聊发送，不发群、不进仓库：

- `DEMO_AUTH_TOKEN`
- CloudBase 服务端 API Key
- PostgreSQL 管理密码
- 腾讯云 `SecretId`、`SecretKey`

## 群发消息

```text
【CloudBase 第一周联调信息】

环境 ID：cloud-d0g0u4r64556b8177
区域：ap-shanghai
API 基础地址：
https://cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com/api/v1

健康检查地址（可直接浏览器打开）：
https://cloud-d0g0u4r64556b8177-1510671296.ap-shanghai.app.tcloudbase.com/api/v1/health

云函数：api（lam-btbif7il）
数据库：postgres-fvpp0eu8 / postgres-fvpp0eu8
照片桶：checkin-photos
契约版本：1.1.0-poc

测试用户：usr_demo01
测试地标：lmk_north_lake
测试任务：task_north_lake
测试拼图：puzzle_liangxiang_campus
测试碎片：fragment_north_lake
坐标：39.7366, 116.1666（GCJ02）
打卡区域：circle / 55 Meters
拼图布局：北、东、南三个 section，每区 2x2，共 12 个地标和 12 个碎片

接口：
GET /health
GET /landmarks
POST /checkin-challenges
POST /checkin-photos
POST /checkins
GET /puzzles/{puzzleId}

鉴权：Authorization: Bearer <DEMO_AUTH_TOKEN>
打卡额外携带：Idempotency-Key
照片要求：JPEG，最大 5 MiB

DEMO_AUTH_TOKEN 不发群里，我会单独发给 B、C、E。
数据库密码和 CloudBase 服务端 API Key 不共享。
```

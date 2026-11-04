# 错误约定

失败响应统一为：

```json
{
  "requestId": "123e4567-e89b-42d3-a456-426614174000",
  "error": {
    "code": "OUT_OF_RANGE",
    "message": "Current location is outside the check-in area",
    "retryable": false,
    "details": {}
  }
}
```

| HTTP | code | 用途 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 请求体、参数或幂等头格式错误 |
| 400 | `LOCATION_REQUIRED` | 未提供位置 |
| 401 | `AUTH_REQUIRED` | 缺少或使用了无效 Bearer Token |
| 404 | `LANDMARK_NOT_FOUND` | 地标不存在 |
| 404 | `PUZZLE_NOT_FOUND` | 拼图不存在 |
| 404 | `CHECKIN_NOT_FOUND` | 当前用户的该 attempt 没有成功打卡记录；只有这个错误码表示“明确无记录” |
| 404 | `PHOTO_NOT_FOUND` | 照片不存在或不属于当前用户 |
| 409 | `CHALLENGE_EXPIRED` | challenge 已过期或已使用 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同一幂等键对应不同请求 |
| 409 | `CHECKIN_RESULT_UNKNOWN` | attempt 仍在处理中，不能删除唯一照片或开始新尝试 |
| 422 | `LOCATION_STALE` | 定位样本超过服务端允许的最大年龄 |
| 422 | `LOCATION_CLOCK_INVALID` | 设备墙钟、未来时间或单调时钟区间不一致 |
| 422 | `LOCATION_ACCURACY_TOO_LOW` | 定位精度差于 30 Meters |
| 422 | `OUT_OF_RANGE` | 位置超出地标的圆形或多边形打卡区域 |
| 422 | `PHOTO_REQUIRED` | 照片缺失或不属于当前用户 |
| 422 | `PHOTO_INVALID` | MIME、大小或 JPEG 内容不合法 |
| 503 | `AUDIT_WRITE_FAILED` | 打卡和奖励已持久化，但审计暂未写入；使用原幂等键重试 |
| 500 | `INTERNAL_ERROR` | 未分类服务端错误，可重试 |

客户端根据 HTTP 状态和 `error.code` 决定重试或恢复流程。`message` 只用于说明，不能作为业务判断依据。日志使用 `requestId` 串联，不记录 Token、数据库密码和完整定位轨迹。

重复打卡使用成功响应 `data.status` 表达，不再定义 `CHECKIN_ALREADY_COMPLETED` 错误码：

| 场景 | HTTP | 结果 |
|---|---:|---|
| 首次打卡 | 200 | `data.status=COMPLETED` |
| 同一 Idempotency-Key、相同请求 | 200 | 返回原 receipt，`data.status=COMPLETED` |
| 同一用户和地标、新 Idempotency-Key | 200 | 返回原 receipt，`data.status=ALREADY_COMPLETED` |
| 同一 Idempotency-Key、不同请求 | 409 | `IDEMPOTENCY_CONFLICT` |

## 打卡恢复查询

`GET /checkins?attemptId=` 只返回当前用户自己的 attempt：

| HTTP | 结果 |
|---:|---|
| 200 | 已成功，返回原 receipt |
| 202 | 仍在处理中，返回 `{ attemptId, status: "PROCESSING" }` |
| 404 + `CHECKIN_NOT_FOUND` | 明确没有成功记录，可按客户端状态机决定是否生成新 attempt |

普通路由 404、405、5xx、超时和代理错误不得映射成 `CHECKIN_NOT_FOUND`。

## 照片删除

`DELETE /checkin-photos/{photoId}` 先做归属校验和逻辑删除，再清理存储对象：

- 正常入口立即不可读取照片。
- 已完成的打卡、碎片和统计不因照片删除而撤销。
- 关联 attempt 仍为 `PROCESSING` 时返回 `409 CHECKIN_RESULT_UNKNOWN`，必须先恢复结果。
- 存储清理暂时失败时返回 202，数据库保留清理窗口并允许后续补偿。

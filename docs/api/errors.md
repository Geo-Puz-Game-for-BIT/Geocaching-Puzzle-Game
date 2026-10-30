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
| 409 | `CHALLENGE_EXPIRED` | challenge 已过期或已使用 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同一幂等键对应不同请求 |
| 422 | `LOCATION_ACCURACY_TOO_LOW` | 定位精度差于 50 Meters |
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

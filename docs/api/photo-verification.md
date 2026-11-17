# 照片质量核验服务边界

> 状态：待外部服务选型，服务端尚未标记为“已验证”
> 关联：D-N04、ADR 0004

## 已明确的产品边界

- 目标是确认照片中存在至少一张质量合格的人脸和可用于打卡的现场照片。
- 不做身份识别、不做人脸库比对、不引入人工审批。
- Android 端本地初检只用于拍摄反馈，不能替代服务端结论。

## 当前后端事实

当前 API 已实现 JPEG 类型、5 MiB 大小、JPEG 文件头、challenge/attempt 关联、拍照时间和地理范围校验。当前没有部署人脸质量或照片真实性模型，因此不得在 UI、PR 或部署记录中写成“服务端人脸已验证”。

## 待接入协议

建议由 E 评估后选择云厂商或自托管服务，并提供以下最小接口：

```http
POST <PHOTO_VERIFICATION_URL>
Content-Type: image/jpeg
X-Request-Id: <uuid>

<jpeg bytes>
```

响应：

```json
{
  "requestId": "uuid",
  "data": {
    "qualifiedFaceCount": 1,
    "faceCount": 1,
    "qualityPassed": true,
    "spoofRisk": "low",
    "providerVersion": "string"
  }
}
```

服务端验收条件：

- `qualityPassed=true` 且 `qualifiedFaceCount>=1` 才能标记照片质量通过。
- `spoofRisk` 阈值必须由 A/C/E 共同确认，D 不单独发明第二套阈值。
- 服务不可用时返回可重试错误，不得把未核验照片判定为通过。
- 日志不得保存原始照片、完整人脸特征或外部服务密钥。

## 当前阻塞

- 尚无照片核验服务账号、调用地址、配额和数据处理条款。
- 尚无人脸质量阈值的跨组书面确认。
- 在这些条件完成前，只能使用 C 的客户端初检作为体验提示，正式服务端状态保持“未核验”。

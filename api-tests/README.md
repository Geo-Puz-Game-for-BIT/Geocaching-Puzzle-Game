# Bruno 集合

使用 Bruno 桌面版 4.2.1 打开本目录，选择 local 环境。
本地服务启动后运行 health 文件夹。CLI 使用根目录 `npm run api:test`。
`.env.example` 是变量说明，CLI 不会自动把 dotenv 文件当作 Bruno 环境。

开发环境配置：

1. 新建 `environments/private/`，复制 `environments/development.bru` 为 `environments/private/development.bru`。
2. 只在私有副本填写真实 API_BASE_URL 和 TEST_TOKEN；提交的 development.bru 保持空值。
3. 根目录运行 `npm run api:test:development`；没有私有副本会失败，不会回退到其他地址。

health 回归检查 HTTP/JSON、响应结构、请求追踪和 UTC 时间。
其余文件是待评审请求模板，没有验收断言，不代表业务完成，默认命令不会运行它们。
正式 OpenAPI 定稿后，补齐请求体、认证、成功/失败断言，并扩展默认回归。

E 每晚保存提交 SHA、Node/CLI 版本、契约检查和回归结果。完整报告可能含响应数据，仅本地保存，提交前脱敏。

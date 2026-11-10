# 云端与本地开发

Node.js 固定 24.20.0；CloudBase CLI 3.8.7、Bruno CLI 4.2.1 写入本目录 devDependencies。
仓库使用 npm workspace，统一在根目录 `npm ci`，提交根目录 `package-lock.json`。
`.npmrc` 拒绝 Node 版本不匹配；`.nvmrc` 和 `.node-version` 供切换版本，不会自动安装 Node。

根目录 `npm run dev` 使用 Node 24 原生 TypeScript 支持启动完整本地 API，仅监听本机。
`functions/api/` 包含路由、处理器、认证、错误码、Data API 和云存储适配；`src/server.ts` 是本地 HTTP 适配层，`src/cloudbase-server.ts` 是云端 9000 端口入口。

第一周接口：

```text
GET  /health
GET  /landmarks
POST /checkin-challenges
POST /checkin-photos
POST /checkins
GET  /puzzles/{puzzleId}
```

本地 API 同时接受 `/health` 和 `/api/v1/health`。业务接口使用 Bearer Token，打卡额外使用 `Idempotency-Key`。

验证和构建：

```powershell
npm run typecheck
npm run test
npm run api:contract
npm run api:check
npm run build:cloud
```

云端部署使用 `cloudbaserc.json` 和 `deploy/`，运行时为 Node.js 20.19。真实环境 ID、Token 和 API Key 只放在被忽略的私有配置或云函数环境变量中；交付文档见 `docs/sprint-01/cloudbase-collaboration-handoff.md`。

自定义端口时将 `.env.example` 复制为 `.env`，同步覆盖 Bruno 的 API_BASE_URL。
登录或操作云环境前由 D/E 确认目标，通过 `npm run cloudbase -- ...` 使用锁定 CLI。
真实环境 ID、Token 和密钥只在被忽略的私有配置中填写。

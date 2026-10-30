# 地标寻宝软件

本仓库用于软件工程课程的地标寻宝游戏。第一阶段以 Android/Kotlin 离线核心为主，同时并行验证地图、拍照拼图和云端接口，最终通过稳定的实机演示和安装包验收。

## 当前状态

- `main` 用于稳定基线，`develop` 用于日常集成。
- 当前阶段：第 1 周，完成工程骨架、接口契约和各模块最小验证。
- c:geo 源码仅供架构参考，`cgeo-master/` 与压缩包不会提交到本仓库。
- 已有 GitHub 远程仓库，Android 骨架已进入 develop。新增本地 health、OpenAPI 与 Bruno 基线见下文；业务云接口仍待实现。

## 第一天统一环境与 API 验收

环境版本和官方下载见 [统一工具链](docs/toolchain.md)，接口规则见 [API 契约与回归](docs/api/README.md)。
Node.js 固定 24.20.0；CloudBase CLI 3.8.7、Bruno CLI 4.2.1 由项目安装。
在根目录运行：

```text
npm ci
npm run cloudbase -- --version
npm run bruno -- --version
npm run typecheck
npm run api:contract
npm run api:smoke
```

人工联调先 `npm run dev`，另一个终端 `npm run api:test`。
OpenAPI 是契约，Bruno 是可运行测试，后端是实现。按协作规范先评审合并契约，再提交实现与测试PR，并引用已经合并的契约。

## 提交位置

| 成员/职责 | 主要提交目录 | 第 1 周分支建议 |
|---|---|---|
| A：产品、流程、UI/UX | `docs/design/`、`design/`、`docs/sprint-01/` | `docs/product-ux-baseline` |
| B：Android 架构、地图主流程 | `android-app/`、`docs/adr/` | `feat/android-foundation` |
| C：相机、图片处理、拼图验证 | `android-app/`、`docs/testing/` | `feat/camera-puzzle-poc` |
| D：联网、认证、云函数、数据契约 | `cloudbase/`、`docs/api/` | `feat/cloudbase-api-poc` |
| E：测试、集成、CI、发布检查 | `.github/`、`docs/testing/`、`docs/sprint-01/` | `chore/quality-baseline` |

复杂功能由两人结对实现；上述目录表示主要责任区，不表示其他成员不能参与。接口、数据模型和公共配置的改动必须在 PR 中通知全组。

## 仓库结构

```text
android-app/             Android/Kotlin 客户端
cloudbase/functions/api/ 云函数 API 入口
cloudbase/shared/        云端共享类型和工具
cloudbase/tests/         云端测试
cloudbase/src/           本地 HTTP 开发入口
api-tests/               Bruno 集合、环境模板与回归
design/                  可交付的设计资产与清单
docs/api/                API、错误码和数据契约
docs/adr/                架构决策记录
docs/design/             用户流程、线框图和 UI 规范
docs/testing/            测试方案、设备矩阵和缺陷记录
docs/sprint-01/          第一周分工、验收和汇总材料
```

## 分支流程

1. `main` 只保存通过验收的稳定版本，不直接开发。
2. 首次基线提交后创建 `develop`，日常集成进入 `develop`。
3. 每人从 `develop` 创建自己的 `feat/*`、`docs/*`、`fix/*` 或 `chore/*` 分支。
4. 每日结束前推送个人分支；需要联调时向 `develop` 提交 PR。
5. 每周验收通过后，由集成人员将 `develop` 合并到 `main` 并打标签，例如 `week-01`。

## 仓库维护与审核

E 负责仓库维护、PR 审核、集成与验收记录。建议开启 main/develop 分支保护：禁止强推，至少一人审核，合并前解决讨论。
具体操作前确认平台权限；建议不代表远端保护已配置完成。

## 开始工作

```powershell
git switch develop
git pull --ff-only
git switch -c feat/your-task-name
```

提交前执行对应模块测试，并检查 `git status`，避免提交密钥、定位隐私数据、真实身份令牌、测试照片和构建产物。详细规则见 `CONTRIBUTING.md`。

## 协作文档

课程计划、人员分工、可行性分析和工具链长文档统一在飞书维护，不纳入 Git 仓库。仓库只保留与代码评审、接口契约、架构决策、测试和构建直接相关的文档。

# E交付、审核与撤销记录

日期：2026-10-21。验收项：`S1-API-01`（health）、`S1-BUILD-01`（本机构建）。

## 交付顺序

依据GitHub main/develop的CONTRIBUTING.md，保持原规范不变：不直接写main/develop，先合并契约，再合并实现与测试，至少一名责任区之外成员审核。

1. `docs/api-health-contract` → `develop`：只交OpenAPI、错误约定和契约说明；需D及相关B/C评审。
2. `chore/quality-baseline` → `docs/api-health-contract`：依赖前一PR的工具链、health实现、Bruno回归与E验收文档。契约合并到develop后，将此PR目标改为develop，再经非作者审核后合并。

两PR共同交付接口标准和实际测试，但不得跳过契约的先行审核、合并。

已创建并核对：[契约PR #2](https://github.com/Defiancecfldl/Geocaching-Puzzle-Game/pull/2) 为待审核状态；[质量PR #3](https://github.com/Defiancecfldl/Geocaching-Puzzle-Game/pull/3) 为依赖契约的草稿。
两分支均已推送，PR作者为harrier-dubois；main/develop未直接修改，尚未合并或获得非作者审核。
测试结果、请求/响应示例见 `docs/testing/day1-environment-api-regression.md` 与 `docs/api/README.md`。
无UI变更；真实云端、手机、第二机、GitHub CI均尚未验收。

## 保留记录

发布前的本地提交ba8dbd4、d02c199保留在本地分支 `backup/quality-before-publish-20261021`。
契约草稿另保留在 `backup/contract-before-publish-20261021`。
仓库外 `交付成果/发布前本地提交备份_2026-10-21.bundle` 已通过 `git bundle verify`，包含重组前完整历史。
本次对外分支从origin/develop整理，未带入A的本地设计提交2772da9；没有对远端强推、覆盖提交或直接合并。
本地备份文件不上传仓库；GitHub上的提交、PR差异和讨论构成团队可访问的记录。

## 撤销方法

- 尚未合并：关闭对应PR，保留分支、提交与讨论作为记录；不必删除或改写历史。
- 已合并：从最新develop新建 `revert/quality-baseline`，通过新PR撤销质量交付，再评估是否撤销契约。
- 若采用merge commit，用 `git revert -m 1 <实际质量PR合并提交SHA>`；若采用squash，用 `git revert <实际squash提交SHA>`。必须使用GitHub实际合并SHA，不用本地备份SHA替代。
- 撤销后重跑受影响的构建/回归，至少一名非作者审核后合并；不要用force push或reset覆盖已共享历史。
- 契约已有其他调用方依赖时，先协调迁移，不能独立撤销契约。

需要找回旧本地交付时，将bundle克隆到新的空目录，或从backup分支创建恢复分支；不覆盖当前工作区。

## 首次发布时的权限核查

连接账号：`harrier-dubois`；仓库拥有者：`Defiancecfldl`。
GitHub仓库元数据返回：push=true、pull=true、triage=true、admin=false、maintain=false。
通过本机已登录账号再次调用协作者权限接口，确认 `permission=write`、`role_name=write`。
结论：用户是可写协作者，具有推送/PR协作权限，不是仓库管理员；提交邮箱和user.name不决定仓库权限。
插件的角色查询和写入被集成权限以403拒绝，属于插件访问限制。本次通过用户本机Git登录已成功推送两个工作分支，并核验真实角色。
仅在本仓库为GitHub配置电脑现有代理，不修改全局Git配置。GitHub登录凭据由系统凭据管理器保管，未写入仓库、文档或日志。

## 仓库转移后的合并前复核（2026-10-21）

仓库现位于 [Geo-Puz-Game-for-BIT/Geocaching-Puzzle-Game](https://github.com/Geo-Puz-Game-for-BIT/Geocaching-Puzzle-Game)，原链接由 GitHub 重定向。
通过本机已登录账号查询协作者权限，`harrier-dubois` 当前 `permission=admin`、`role_name=admin`；上节保留首次发布时的历史权限，不代表当前权限。
两个 PR 当前均无冲突；PR #2 为 open，PR #3 为依赖契约的 draft。此次复核仍未取得非作者正式 Review，也没有两个提交对应的 GitHub 检查运行。
管理员操作权限不替代 CONTRIBUTING.md 要求的责任区外成员审核。先取得契约审核并合并 #2，再将 #3 的目标改为 develop、转为 ready，取得实现与测试的审核后合并；保留 merge commit 便于按前述方法撤销。

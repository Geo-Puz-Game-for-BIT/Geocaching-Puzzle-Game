# 第1天环境与接口回归

日期：2026-10-21；记录人：E。
原始本地验证基线：`ba8dbd407aa09600944c72bfc8ed81c7124577b6`，保存在发布前本地备份中，不作为远程可访问的证据。
工作分支：`chore/quality-baseline`。按远端规范重新拆分契约与实现PR，移除无关A设计提交；业务实现和Android代码未改动。
发布前再次检查类型、契约与本地API回归；Android沿用相同源码的验证结果。本记录不代表全员或云端验收通过。

| 检查 | 结果 | 证据摘要 |
| --- | --- | --- |
| Node / CLI | PASS | Node24.20.0、CloudBase CLI3.8.7、Bruno CLI4.2.1 |
| npm ci | PASS | 根目录锁文件可复现安装 |
| typecheck | PASS | TypeScript 检查通过 |
| api:contract | PASS | OpenAPI 3.0.3，health 契约0.1.0 |
| api:smoke | PASS | 临时本地服务，1请求、4/4测试通过 |
| api:test | PASS | 默认 localhost:3000，1请求、4/4测试通过 |
| 版本约束 | PASS | Node24.14.0 被 EBADENGINE 拒绝 |
| Android单测 | PASS | GeoMathTest 3项、0失败、0错误 |
| Android打包 | PASS | assembleDebug 成功 |
| Android Lint | PASS | lintDebug 成功，XML无issue |
| 手机/云端/第二台电脑 | NOT_RUN | 待对应设备、环境及团队提供证据 |
| GitHub CI/非作者审核 | NOT_RUN | 尚无本次工作流运行或审核证据 |

Android 完整检查使用 `android-app/scripts/verify-windows.ps1`，最终 BUILD SUCCESSFUL，2分42秒。
本机原始日志保存在仓库外的 `.devtools/verification/20261021-113941-android.txt`，不作为团队可访问的远程证据。
APK位于 `android-app/app/build/outputs/apk/debug/app-debug.apk`，不提交二进制到Git。
SHA-256：`CCC4497B7AD84AEC7117598468194C15EC8D6D5EEB6066E7F3C324C9CE0C0BDF`。
地图Key为空，此构建使用演示模式；真实地图未验收。

复现命令见 [统一工具链](../toolchain.md) 与 [API契约](../api/README.md)。
后续业务测试必须在接口契约定稿、实现交付后加入默认回归。

## 后续状态补记（2026-10-21）

上表保留首次本机验证时的快照。用户随后报告全员环境确认及第二台电脑构建已完成；第二台电脑结果待补完整 Commit、构建日志、电脑标识和版本信息后归档，不把口头确认写成已独立复核的 PASS。
GitHub 的正式 Review、合并、CI、手机及云端证据分别核实；用户报告已完成审核沟通不替代 GitHub 上的非作者审核记录。

# 统一开发环境（2026-10-21）

| 工具 | 锁定版本 | 管理位置 |
| --- | --- | --- |
| Node.js | 24.20.0 LTS | 根目录 .nvmrc、.node-version、package.json engines |
| CloudBase CLI | 3.8.7 | cloudbase/package.json devDependencies、根 package-lock.json |
| Bruno CLI | 4.2.1 | 同上 |
| Bruno 桌面版 | 4.2.1 | 全员从官网安装，记录版本 |
| Android Studio | 2026.2.1.8 | 全员从官网安装，记录完整版本 |
| JDK | 17 | Android 构建与 Gradle JDK |
| Gradle | 8.13 | android-app/gradle/wrapper/gradle-wrapper.properties |
| AGP | 8.13.2 | android-app/gradle/libs.versions.toml |
| Kotlin | 2.2.21 | 同上 |
| Android SDK | 36 | compileSdk/targetSdk，另装 Build Tools 35.0.0 |

Bruno 桌面版和 Android Studio 的版本不能由 npm 自动锁定，E 验收时检查 About 页面。
Android Studio 自带运行时与项目构建 JDK 可以不同；项目仍用 JDK 17。
全员安装 Git、Node、Bruno，具备 Android Studio、JDK 和 SDK 基础环境；A 侧重设计，B/C Android，D 云端，E 仓库维护、审核、回归与集成。

## 官方下载

- [Node.js 24.20.0](https://nodejs.org/dist/v24.20.0/)：Windows x64 选择 node-v24.20.0-x64.msi；已有其他版本时使用版本管理器或项目便携安装。
- [Bruno](https://www.usebruno.com/downloads)：选择 Windows x64 4.2.1；无需全局安装 CLI。
- [Android Studio](https://developer.android.com/studio)：选择 2026.2.1.8 Windows x64；历史版本可从官方归档查找。
- [Android Studio 归档](https://developer.android.com/studio/archive)。
- [JDK 17](https://www.azul.com/downloads/?version=java-17-lts&os=windows&architecture=x86-64-bit&package=jdk)：选择 JDK，不能只装 JRE。
- [Git](https://git-scm.com/install/windows)、[VS Code](https://code.visualstudio.com/download)、[Figma](https://www.figma.com/downloads/)。

## 第一天共同交付

1. 切换 Node 24.20.0，在仓库根目录 npm ci；提交 package-lock.json，不提交 node_modules。
2. 执行 npm run cloudbase -- --version 和 npm run bruno -- --version，核对 3.8.7 / 4.2.1。
3. npm run typecheck、npm run api:contract、npm run api:smoke 均通过。
4. Bruno 打开 api-tests，local 环境运行 health；开发环境真实地址由 D 提供，仅放私有环境副本。
5. B/C/E 运行 android-app/scripts/verify-windows.ps1，保存单测、APK 构建和 Lint 结果；A/D至少完成工程同步。

## E 每晚验收

- 记录 PR/提交 SHA、责任 A～D、实际软件版本、执行命令和结果；截图不能替代可运行交付。
- 接口有修改：先评审合并OpenAPI，再验收实现/测试PR；对照三者检查字段、空值、单位、认证与错误分支。
- 执行 npm ci、typecheck、api:contract；本地执行 api:smoke，D 提供开发地址后增加 api:test:development。
- Android 变更执行单测、assembleDebug、lintDebug，UI 变更补两台手机截图/录屏。
- 查看 PR 差异和 git status，检查真实环境 ID、Token、密钥、定位和照片是否误入提交。
- 仅 health 通过不能签署地标、打卡、进度或云端部署完成；按已实现范围分别记录通过/待完成。

私有目录 api-tests/environments/private/、api-tests/.env、cloudbase/.env、.env.local 均忽略。
已提交秘密不能仅靠后加 .gitignore 解决，须撤销/轮换并处理历史。

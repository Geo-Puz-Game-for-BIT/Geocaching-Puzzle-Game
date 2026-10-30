# 第一周责任人与提交入口

| 代号 | 姓名 | 主责 | 结对成员 | 工作分支 | 主要目录 |
|---|---|---|---|---|---|
| A | 待填写 | 产品、流程、UI/UX | B / C | `docs/product-ux-baseline` | `docs/design/`、`design/` |
| B | 余 | Android 架构、地图 | A / E | `feat/map-fog-of-war` | `android-app/`、`docs/adr/` |
| C | 待填写 | 相机、图像、拼图 | A / E | `feat/camera-puzzle-poc` | `android-app/`、`docs/testing/` |
| D | 张博淳 | API、联网、云端 | B / E | `feat/cloudbase-api-poc` | `cloudbase/`、`docs/api/` |
| E | 用户（E） | 测试、集成、CI、发布检查 | 全员 | `chore/quality-baseline` | `.github/`、`docs/testing/`、`docs/sprint-01/` |

姓名填写后，由 E 维护本表。出现跨目录修改时，PR 必须邀请对应主责成员审查。

E 同时负责 Git 仓库维护、PR 审核和验收记录。主要目录是日常交付入口；共享工具链、API 测试集合等跨目录变更按对应责任区协同评审。
E 第一周交付清单见 [质量基线](e-quality-baseline.md)，本机第1天验证结果见 [环境与接口回归记录](../testing/day1-environment-api-regression.md)。

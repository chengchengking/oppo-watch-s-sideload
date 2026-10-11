# PurrLaunch 的 OWS（OPPO Watch S）适配说明

上游：https://github.com/purrrock/PurrLaunch （v1.2.2，MIT，作者 purrrock）
上游面向 DT NO.1 G1（ATS3085S）；本目录是让它装上 OWW262 的最小改动。

## Delta（相对上游 src/）
1. `manifest.json` 增加 `"icon": "/assets/icon.png"` —— **OWS 真机安装强制校验 icon 字段**，
   缺失报 5005 "invalid package"（上游的 G1 不校验）
2. `manifest.json` 的 `versionCode` 按需调大（本仓库用 9 以覆盖此前临时安装的同包名应用）
3. `assets/icon.png` 猫爪图标（192×192）

## 安装
构建产物 `bundle.pkg` 经手机端"瑰元助手"安装（见仓库根 README）。
桌面入口：占位"木鱼"槽位（OWS 桌面菜单硬编码，见 docs/DEV_GUIDE.md §5）。
桌面图标的齿轮问题用 `icon_<包名>` 推送法解决（tools/ftprobe）。

## 已在 OWS 验证的功能
- 应用列表与启动（pkg.list / launch）✅
- 安装/覆盖（pkg.install 升级语义）✅（经瑰元助手侧验证升级路径）
- 文件管理器 / 系统信息 / 卸载页：构建通过，UI 功能未逐一回归

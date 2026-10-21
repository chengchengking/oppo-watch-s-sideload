# 瑰元助手（Glyphix Assistant）v1.2

手机端侧载安装器——**本仓库标准流程的核心工具**。它通过 OPPO 健康（com.heytap.health）
与手表桥接，提供「安装应用 / 启动应用 / 检查安装 / 设备日志」等调试通道能力。

- **原始发布页**：[pgyer.com/glyphix-assistant](https://www.pgyer.com/glyphix-assistant) —— 蒲公英应用分发页，包名 `com.glyphix.assistant`。
- **署名**：上述发布页**未标注作者 / 开发者**，本仓库同样无从核实，故不代填任何人名。版权归该工具作者所有，以发布页为准。
- **上游版本**：发布页当前为 **v1.3（build 3，2026-10-08）**，更新说明「支持 OHealth」；本仓库镜像是 **v1.2（build 2，2026-08-21）**。
  应用内「发现新版本 1.3」弹窗走的就是蒲公英更新检查接口（APK 内含 `pgyer.com/apiv2/app/check` 调用）。
- **镜像来源**：米坛社区教程帖 [bandbbs.cn/threads/29508](https://www.bandbbs.cn/threads/29508/) 的附件（需论坛账号）。
  该帖作者 libiao0418 是**教程作者**，不是本工具作者，仅为该工具用户。
- **谱系**：按教程原文，Glyphix OS 调试工具 `glyphixdebug` 自 2.5.0 起把「桥接模式」独立到了瑰元助手。
  APK 内使用 `com.glyphix.interconnect` 系列类（`GlyphixInterconnect` / `GlyphixDebug` / `GlyphixWear263`），
  即构建在 Glyphix 的 interconnect SDK 之上——这只说明用了什么 SDK，不构成作者归属证据。

> 发布页是怎么确定的、版本对照、以及「哪些字段能核对 / 哪些查不到」，见 [`docs/SOURCES.md` §1](../../docs/SOURCES.md)。

- **检索记录（2026-10-21）**：GitHub 检索「瑰元助手」「com.glyphix.assistant」「glyphix assistant」均**无相关仓库或源码**；
  该工具没有 GitHub 发布渠道，蒲公英页即其分发入口。
- **更正记录**：2026-10-21 依 issue #1 更正——原先把**教程作者**当成了工具作者，并挂了一个查无实据的第三方账号；
  相关表述已全部删除。现只保留可在发布页当场核对的字段（包名、版本、更新时间）。
- **版本**：镜像 v1.2（本仓库实测 versionCode 3；蒲公英侧 build 2）；上游已到 v1.3（build 3）
- **包名**：com.glyphix.assistant · 主 Activity：`com.xfaith.interconnect.MainActivity`
- **SHA256**：`5d7389524a494291251effb07fda67e06bbf43a89fa209b6824dd1d849661b27`
- **版权**：归其作者所有。本镜像仅为复现研究流程而分发，如作者要求移除请联系仓库维护者。

## 使用流程

1. 安装本 APK 到**手机**（不是手表——它不是手表应用）
2. 手表开机并与手机蓝牙连接（OPPO 手表出厂即与 OPPO 健康/快连服务绑定）
3. 打开瑰元助手：
   - **连接**：查询到 1 个服务 → `连接服务: com.heytap.health` → `连接成功`
   - **设备信息**：应返回 `{"brand":"OPPO","model":"OWW262",...}`（OWS 的身份）
4. **安装应用**：文件选择器选 `.pkg`/`.rpk` 包 → 日志出现
   `安装应用 成功 {"code":200,"msg":"success"}` 即装上
5. **启动应用**：输入包名 → 200 即启动（注意：可能不置前，从 PurrLaunch/桌面启动更直观）
6. **设备日志**：表端实时 RPC 日志流（排错利器，见仓库 DEV_GUIDE §6.2）

## 六个按钮

| 按钮 | 作用 | 备注 |
|---|---|---|
| 连接 | 绑定健康 App 桥接服务 | 每次重开后需重连 |
| 设备信息 | 读取手表身份（型号/版本/VID） | 验证桥接 |
| 检查安装 | 返回 200 + true/false | ⚠️ 语义不可靠，勿作安装判据 |
| 安装应用 | SAF 选文件 → 推包安装 | 文件名任意，内容决定一切 |
| 启动应用 | 按包名启动 | 5001 = 包不存在 |
| 设备日志 | 表端 RPC 日志流 | `app.life` / `lvgl` / `mas.ffi` 条目 |

## 已知行为（v1.2 实测）

- 「发现新版本 1.3」弹窗会反复出现，取消即可
- 安装**不会**注册桌面图标（齿轮问题）——需 `icon_<包名>` 推送，见仓库 DEV_GUIDE §5.3
- 不校验签名，可升级覆盖商店应用（versionCode 需不低于在装版本）

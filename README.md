# OPPO Watch S 侧载工具链（Glyphix OS）

把自研 JavaScript 应用装进 **OPPO Watch S（OWW262，Glyphix OS，非安卓）** 的完整工具链、
应用源码与逆向文档。**任何人按照本文从零开始，都能在 30 分钟内把自己的应用装上这块手表。**

> 全部流程在真机 OWW262（nucleus 0.7.0-44 / runtime 0.7.0-68）上验证通过。
> 本仓库不含任何 OPPO 专有代码；逆向笔记仅用于自有设备互操作研究。

## 你需要什么

| 硬件/软件 | 说明 |
|---|---|
| OPPO Watch S（OWW262） | Glyphix OS 手表；理论适配同代 Glyphix 手表，未逐一验证 |
| OPPO/一加 Android 手机 | 装 OPPO 健康（出厂自带）+ **瑰元助手**（本仓库 `tools/guiyuan-assistant/`） |
| 数据线 / 同一 WiFi | adb 调试手机 |
| PC | Node.js（npm 装 `glyphix-cli` 工具链）+ Python 3 + Android SDK（仅探针需要） |

## 从零到手表（六步）

### 第 0 步：装工具链（PC）
```bash
npm install -g glyphix-cli      # npm 包 latest = 0.8.0-rc，装出来的 gx 是 v0.11.0（两个号不是一回事）
gx list device                  # 出现 oppo-rtos-watch 等设备名即成功
```
> 本仓库所有构建用 `-d oppo-rtos-watch`（466×466 圆屏）。没有该设备配置时，
> 把本仓库 `devices/oppo-rtos-watch.yml` 复制到 `%APPDATA%\glyphix\devices\`（详见 docs/DEV_GUIDE.md §2.1），
> 或任选一个 `generic-watch-466x466` 亦可。

### 第 1 步：手机装瑰元助手
安装 `tools/guiyuan-assistant/glyphix-assistant-v1.2.apk`（详见该目录 README：发布页/来源/校验和）。
打开它 → 点「连接」→ 日志出现 `连接服务: com.heytap.health → 连接成功`；
点「设备信息」返回 OWW262 身份即桥接就绪。

### 第 2 步：写应用
参照 `apps/qrwatch/src/`（最小可用工程）：
```json
{
  "package": "com.yourname.app",
  "name": "你的应用",
  "icon": "/assets/icon.png",
  "versionName": "1.0.0",
  "versionCode": 1,
  "router": { "entry": "main", "pages": { "main": { "component": "index" } } }
}
```
⚠️ `"icon"` 是 **OWS 真机安装必填项**——缺失报 5005 "invalid package"（官方模板没写也能在
模拟器跑，别被骗）。页面写法、组件、API 见官方文档（github.com/glyphix-os/web-docs）
与 docs/DEV_GUIDE.md §3。

### 第 3 步：构建
```bash
cd apps/qrwatch && gx build -d oppo-rtos-watch -f
# 产物 .glyphix-work/dist/oppo-rtos-watch/<包名>/bundle.pkg ← 这就是安装包
```

### 第 4 步：侧载进手表
```bash
export MSYS_NO_PATHCONV=1       # Git Bash 必加，否则 /sdcard/... 会被转成本地路径
adb push bundle.pkg /sdcard/Download/anyname.rpk
# 让文件管理器/助手的「下载」里能看见它（ColorOS 需要主动触发一次扫描）
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/anyname.rpk
```
瑰元助手 →「安装应用」→ 文件 tab → 下载 → 选包 → 日志 `安装应用 成功 {"code":200}` 即装上。
「启动应用」填包名可启动（200=成功；5001=包不存在）。升级时直接重装，`versionCode` 必须比在装的大。

### 第 5 步：让它出现在桌面
OWS 桌面菜单是**硬编码**的，不动态扫描应用。解法：安装
[PurrLaunch](https://github.com/purrrock/PurrLaunch)（本仓库 `apps/purrlaunch-ows/` 是 OWS 适配版），
它会占位手表上的"木鱼"应用作为第三方启动器——你的应用会出现在它的列表里，点击即启动。

> ⚠️ PurrLaunch 与「木鱼」同包名（`com.xfaith.woodenfish`）。表上已有木鱼时，要么先卸载它，
> 要么直接用更高的 `versionCode` 覆盖（本仓库实测走的是后者，见 docs/DEV_GUIDE.md §7）。

### 第 6 步（可选）：修正桌面图标
侧载应用桌面默认显示齿轮（图标注册缺失）。用 `tools/ftprobe/` 协议探针推送
`icon_<你的包名>` 即可注册真图标——完整 SOP 见 docs/DEV_GUIDE.md §5.3（✅ 真机验证）。

## 成果一览

| 能力 | 状态 |
|---|---|
| 自研应用（JS/UX）构建并侧载进手表 | ✅ 二维码应用已在表上运行（每 10 秒自动换码） |
| 桌面入口 | ✅ PurrLaunch 占位木鱼槽位 |
| 桌面图标注册 | ✅ `icon_<包名>` 推送法（本项目真机实测确认，官方文档无记载） |
| 表端协议逆向（商店通道） | ✅ 传输层全通，档案见 docs/findings/ |

## 目录

```
docs/                 DEV_GUIDE.md（★ 完整开发指南）、PKG_FORMAT.md（CR 包格式规格）
docs/findings/        逆向档案（R14~R21 + master 汇总）
docs/SOURCES.md       外部资源出处 / 署名 / 核实日期（引用前先看这个）
apps/qrwatch/         二维码应用源码 + 预构建 bundle.pkg（改 src/main/qr-data.js 换码内容）
apps/library-qr/      生态示例：合肥市中心图书馆读者证动态码（离线生成）
apps/purrlaunch-ows/  PurrLaunch v1.2.2 的 OWS 适配 delta（上游: purrrock/PurrLaunch, MIT）
devices/              gx 设备配置（oppo-rtos-watch.yml，466×466）
tools/guiyuan-assistant/  瑰元助手 v1.2 APK（镜像分发；原始发布页 pgyer.com/glyphix-assistant）+ 使用说明
tools/ftprobe/        watchappservice_file 协议探针（含桌面图标注册 fireIconPush）
tools/pkg_tool.py     Glyphix CR 容器格式读写工具
```

## 关键结论（踩坑浓缩，细节见 docs/DEV_GUIDE.md）

1. **manifest 的 `icon` 字段是 OWS 真机安装的必填项**——缺失即 5005 "invalid package"
   （官方模板没写也能在模拟器跑，别被骗）
2. **桌面菜单硬编码**：第三方应用上桌面的唯一方式是占位一个已有槽位（社区方案 PurrLaunch 伪装木鱼）
3. **桌面图标是数据驱动的**：商店安装会单独传 `icon_<包名>` 注册；侧载要补这一步（tools/ftprobe）
4. 商店协议通道（PTC/OAF）对自研包的安装校验过不去——传输层全通也没用，**侧载直接走瑰元助手调试通道**
5. 「检查安装」接口语义不可靠；安装判据只认安装/启动的返回码

## FAQ

- **桌面显示齿轮图标？** 图标注册缺失，走上面第 6 步。
- **安装报 5005 invalid package？** 九成是 manifest 缺 `icon` 字段或必填字段不全。
- **启动报 5001 The package was not found？** 包没装上/包名拼错（「检查安装」的 true 不算数）。
- **手机连不上手表？** 表深睡断连需物理唤醒；重启瑰元助手重连。
- **装的表上没有？** 商店通道（健康 App）与调试通道是两条路；自研包只能走调试通道。

## 致谢与参考

- [purrrock/PurrLaunch](https://github.com/purrrock/PurrLaunch)（MIT）—— 占位启动器原创，`apps/purrlaunch-ows/` 是其 OWS 适配 delta
- [pgyer.com/glyphix-assistant](https://www.pgyer.com/glyphix-assistant) —— 瑰元助手**原始发布页**（蒲公英分发，包名 `com.glyphix.assistant`；页面未标注作者）
- [bandbbs.cn/threads/29508](https://www.bandbbs.cn/threads/29508/) —— OWS 侧载教程（教程作者 libiao0418）
  > 该帖作者是**教程作者**，不是瑰元助手作者（仅为该工具的用户）。瑰元助手的发布页见上一条。
- [glyphix-os/web-docs](https://github.com/glyphix-os/web-docs) —— Glyphix 官方文档

> 每项外部资源的出处、核实日期、以及"哪些字段能核对、哪些查不到"，都记在 [docs/SOURCES.md](docs/SOURCES.md)。

## 许可证与第三方资源

- 本仓库**原创部分**（文档、`apps/qrwatch`、`apps/library-qr`、`tools/pkg_tool.py`、`tools/ftprobe`、`devices/`）：
  [MIT](LICENSE)，Copyright (c) 2026 chengchengking。
- `apps/purrlaunch-ows/`：上游 [purrrock/PurrLaunch](https://github.com/purrrock/PurrLaunch) 为 MIT，本目录是其适配 delta，同样按 MIT 使用。
- `tools/guiyuan-assistant/` 里的 APK：**版权归该工具作者**，不适用本仓库 MIT。本仓库仅作镜像以便复现，
  原始发布页为 [pgyer.com/glyphix-assistant](https://www.pgyer.com/glyphix-assistant)；如作者要求移除，联系仓库维护者即删。
- OPPO / Glyphix 相关商标、系统组件归其各自权利人；本仓库不含其专有代码。

## 免责声明

仅供自有设备研究与互操作使用。逆向笔记描述的是个人设备上观察到的行为，不含任何专有代码；
瑰元助手 APK 版权归其作者所有、由本仓库镜像分发以便复现（原始发布页见 tools/guiyuan-assistant/README.md）；
使用本仓库内容产生的任何风险由使用者自担。

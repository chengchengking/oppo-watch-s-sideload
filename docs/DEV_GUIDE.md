# Glyphix 应用开发与侧载完全指南
### 目标平台：OPPO Watch S（OWW262）· Glyphix OS · 466×466 圆屏
> 2026-10-11 · 基于第 10~20 窗口的实测结论 · 配套：HANDOFF.md（快速交接）、work/findings_*.md（协议逆向档案）
> 所有标注 ✅ 的结论都在真机验证过；⚠️ 是踩过的坑。

---

## 1. 平台架构：搞清楚谁在干什么

```
┌─────────────┐  adb 驱动/推文件  ┌──────────────┐                  ┌──────────────┐
│ PC (本仓库)  │ ──────────────→ │ 手机 PJD110   │ ── BT/PTC 协议──→ │ 手表 OWW262  │
│ gx 工具链    │                 │ com.heytap.  │   (官方通道)       │ Glyphix      │
│ Python 工具  │                 │ health       │                  │ nucleus 0.7  │
└─────────────┘                 │ com.glyphix. │── 调试通道(白名单)─→│ runtime 0.7  │
                                │ assistant    │   ★ 侧载走这条    └──────────────┘
                                │ (瑰元助手)    │
                                └──────────────┘
```

- **Glyphix OS**：OPPO 的轻量 JS 应用系统（快应用风格：UX 模板 + @system.* API）。开源文档在 github.com/glyphix-os/web-docs（已克隆到 `work/glyphix_os/web-docs/`，**写代码前先查它**）。
- **安装通道有两条**：
  - **调试通道**（瑰元助手「安装应用」）→ ✅ 本指南的标准路线。不校验签名、不做包内容强校验（仅要求 manifest 合法 + 必填字段齐全）。
  - **商店通道**（健康 App / PTC 协议直推）→ R10~R18 窗口已把传输层逆向打通（文件 MD5 级送达 8+ 次），但表端对自研包的安装校验始终不放行（cid=5 是"暂存"而非 InstallDone）。**没有瑰元助手时才考虑，档案见第 8 节。**

---

## 2. 环境清单与注意事项

### 2.1 PC
| 项 | 位置/值 |
|---|---|
| gx 工具链 v0.11.0 | `G:\oppo\.toolchain\node_modules\@glyphix\cli-win32-x64\bin\gx.exe` |
| 设备配置 | `C:/Users/<user>\AppData\Roaming\glyphix\devices\oppo-rtos-watch.yml`（vendorID/productID 已定 0x0000/0x0000，勿再改回探针值 0x1234/0x5678） |
| 包格式工具 | `G:\oppo\pkg_tools\pkg_tool.py`（info/list/manifest/extract/pack/setdevice；CR 容器格式见 PKG_FORMAT.md） |
| 官方文档 | `G:\oppo\work\glyphix_os\web-docs\src\`（api/ components/ framework/ tutorials/） |
| Python | 可用（PIL 已装，生成图标用） |

⚠️ Git Bash 会把 `/sdcard/...` 转成本地路径：**所有 adb 文件操作前 `export MSYS_NO_PATHCONV=1`**。

### 2.2 手机（adb 设备 <adb-serial>，PJD110）
| 包 | 作用 |
|---|---|
| com.glyphix.assistant | **瑰元助手 v1.2**：侧载安装器（v1.3 已发布未验证）。UI 六键：连接/设备信息/检查安装/安装应用/启动应用/设备日志 |
| com.heytap.health | OPPO 健康：桥接宿主 + 手表应用商店 |

### 2.3 手表（OWW262）
- vendorId=17170433(0x01060001)，nucleus 0.7.0-44 / runtime 0.7.0-68，圆屏 466×466
- **连接条件**：戴表/抬腕保持活跃；深睡会断连，PC 唤不回
- **RPC 日志**（瑰元助手「设备日志」）= 表端实时日志流，排错第一入口（见第 6 节）

---

## 3. 应用工程规范

### 3.1 目录结构
```
<project>/
├─ src/
│  ├─ app.js               # 应用生命周期
│  ├─ manifest.json        # 应用清单（icon 必填！见 3.2）
│  ├─ assets/icon.png      # 图标（192×192 PNG 实测可过）
│  └─ main/
│     ├─ index.ux          # 页面 = <template> + <style> + <script>
│     └─ <data>.js         # 页面用数据模块（ES import）
```
参考工程：`G:\oppo\com.rtos.qrwatch\`（二维码应用）与 `G:\oppo\purrlaunch-lite\`（启动器）。

### 3.2 manifest.json 全字段（★=必填）
```json
{
  "package":    "com.example.app",
  "name":       "应用名",
  "icon":       "/assets/icon.png",
  "versionName":"1.0.0",
  "versionCode": 1,
  "features":   [],
  "router": {
    "entry": "main",
    "pages": { "main": { "component": "index" } }
  },
  "config":  { "designWidth": 466 },
  "permissions": [ { "name": "watch.permission.XXX" } ],
  "display": {}, "dial": {}, "widgets": []
}
```
- `package` ★ 全局唯一，推荐 com.xxx.yyy；避免 `-` `_` `.`
- `name` ★ ≤6 汉字，桌面显示用
- `icon` ★ **必填！缺失 → 表端 5005 invalid package（本项目最大的坑，见第 5 节）**
- `versionCode` ★ 整数，每次发布 +1（升级判定依据）
- `router` ★ 页面路由；`config.designWidth` OWS 用 466
- ⚠️ 官方模板没写 icon 也能在模拟器跑，但**真机安装必须补**

### 3.3 页面文件 index.ux 速览
- 模板指令：`for="{{(idx, item) in items}}"`、`if="{{cond}}"`、事件 `on:click="fn"`、属性绑定 `:value="{{expr}}"`、插值 `{{expr}}`
- 样式：CSS 子集；页面根建议显式 `width:466px; height:466px`
- 组件清单（web-docs components/）：div/p/stack/scroll/button/image/canvas/**qrcode**/barcode/input/slider/progress/marquee/picker…（qrcode 最高 QR v12 ≈ 300 字节载荷）
- **页面生命周期**（真机 RPC 日志实测顺序）：`onInit → onReady → onShow → (onHide → onShow…) → onDestroy`
- **应用生命周期**（app.js）：`onCreate/onShow/onHide/onDestroy/onError/onPageNotFound`
- 定时器：全局 `setInterval/clearInterval`（页面上记得在 onHide 清掉）

### 3.4 常用 API（`import pkg from '@system.package'` 风格）
| 模块 | 本项目验证过的用法 |
|---|---|
| @system.package | `list('app')→包名数组`、`getInfo(pkg)→{name,versionName,icon…}`、`install({src})`、`remove` |
| @system.launch | `launch(pkg)→Promise<bool>` 启动置前、`getRunning()`、`exit()` |
| @system.file | readText/writeText/list/copy/mkdir/stat…（以后做文件浏览器/图标缓存排查用） |
| @system.devtools | `command(cmd, fn)` 注册 `dev` 终端命令 |
| 其余 | ble/geolocation/media/notification/schedule/battery/cipher/fetch/i18n… 见 web-docs api/ |

---

## 4. 构建与安装 SOP（✅ 全流程可复制）

### 4.1 构建
```bash
cd <project>
../.toolchain/node_modules/@glyphix/cli-win32-x64/bin/gx.exe build -d oppo-rtos-watch -f
```
- `-d` 目标设备；**`-f` 全量构建**（⚠️ 增量构建不重烙 manifest 设备 ID——改过设备配置后必须 -f）
- 产物：`.glyphix-work/dist/oppo-rtos-watch/<package>/` → **bundle.pkg（这就是安装包）** + icon.png + manifest.json（商店提交三件套；侧载只要 bundle.pkg）
- 图片转换依赖 pngquant（generic-* 配置需要；oppo-rtos-watch 配置实测不需要）

### 4.2 安装（瑰元助手）
```bash
export MSYS_NO_PATHCONV=1
adb push <project>/.glyphix-work/dist/oppo-rtos-watch/<pkg>/bundle.pkg /sdcard/Download/xxx.rpk
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/xxx.rpk
```
然后：瑰元助手 → 安装应用 → 文件 tab → 下载 → 选包 → 日志见 `安装应用 成功 {"code":200}`。
- 文件名任意（内容决定一切）；成功后可立即「启动应用」（填包名，200=已启动）
- **升级**：直接重装，versionCode 必须比在装的大
- **覆盖商店应用**：✅ 实测可行（purrlaunch-lite 覆盖了商店版木鱼，调试通道不拦签名）

### 4.3 验收判据
- 安装判据 = **安装/启动返回码**（「检查安装」接口语义不可靠，别用它）
- 运行判据 = RPC 日志出现 `app.life: call onInit/onReady in pkg://<包名>/main/index.uo` + `eventLoop refresh`

---

## 5. 图标与桌面机制（✅ 已解决：`icon_<包名>` 注册法）

### 5.1 图标资源
- **com.rtos.qrwatch（二维码应用）**：白底 + 黑色二维码图案，192×192 PNG（843B）
- **purrlaunch / PurrLaunch（启动器，包名 com.xfaith.woodenfish）**：猫爪图案（官方版）/ 四色宫格（lite 版）
- 图标都在包内（`assets/icon.png` + manifest `icon` 字段）——运行时可见；**桌面显示另走一套注册机制（见 5.3）**

### 5.2 齿轮现象的根因（完整因果链，已实证）
1. OWS 桌面对侧载应用显示齿轮（安卓设置样图标）= 图标注册表缺该包的图标条目 → 兜底渲染
2. 图标注册表是**数据驱动**的：商店安装应用时，健康 App 在包文件之外**单独传输一个图标文件**并注册
   （真机商店日志 08:26：`pkg_com.xfaith.woodenfish` 传输完成后 ~120ms 发 `icon_com.xfaith.woodenfish`，11294B PNG）
3. 瑰元助手调试通道只送 bundle.pkg → 注册表没有图标 → 齿轮
4. 包内自带 icon、manifest 声明 icon 都**不能**让桌面显示——桌面只认注册表
5. PurrLaunch 作者（DT NO.1 G1）的桌面是"硬编码菜单+OS 烘焙图标"，与 OWS 不同构；OWS 这套是可从包侧/文件侧修复的

### 5.3 ★ 标准流程 SOP：给侧载应用注册/更换桌面图标（✅ 已验证）
> 验证记录：R21b（com.rtos.qrwatch，用户目视确认桌面图标变二维码图案）+ R21c（双图标同批次注册，wire 完成）

**前提**
- 探针 `ftprobe18`（R21c+ 构建）已装手机；手表已回连（在线）
- 手机**亮屏 + 解锁**状态（Android 14 会在锁屏/不可见时强杀探针 Activity：`did not call finish`）

**步骤**
1. 图标 PNG 放入 `ftprobe18/assets/`，工作名 = `icon_<目标包名>`（无扩展名；192×192 843B 实测可用）
2. `src/com/rtos/ftprobe/MainService.java` → `fireOp4AndUnlock()` 末尾的 `icons[][]` 数组加入
   `{资产名, "icon_<目标包名>"}` 对
3. `python build_probe.py` → `adb install -r build/FtProbe18.apk`
4. `adb shell settings put secure authentication_state 1`（探针冒充健康代理过框架认证必须；**跑完必须回 0**）
5. 亮屏解锁 → `adb shell am start -n com.rtos.ftprobe/.MainActivity` → keepalive：每 15s 重复 WAKEUP+am start，
   共约 3 分钟（防 ColorOS virtualFreeze；探针自动完成 cid=10 → pkg×3 → icon_<包名> → cid=3 全链）
6. wire 验证：`adb logcat` 抓 `[sfcmd]FT_SETUP_REQ sent,...fileName:icon_<包名>` +
   `handleTransferComplete: Completed :icon_<包名>`
7. **重启手表** → 桌面图标生效 ✅

**原理**：表端图标注册表按文件名路由——`icon_<包名>` + `FileInfo=appstore_uri_icon` 走
`wear:watchappservice_file:1.0`（sid=272）通道送达即注册，与包文件（`pkg_<包名>`+`appstore_uri_package`）
同通道不同路由键。对**已安装**的应用直接补送即可，无需重装应用；重启后持久。

### 5.4 边界与残留
- 该方法依赖探针（认证旁路），瑰元助手 v1.2 的安装不含图标注册（v1.3 未知——若 v1.3 自动带 icon.png 则更简单）
- 图标内容任意 PNG（192×192/843B 到 432×432/1.7KB 均可）；商店原图 11294B（尺寸不敏感）
- 桌面菜单条目本身仍是硬编码（第三方应用要"出现在桌面"仍需占位木鱼槽位——PurrLaunch 已解决）；
  图标注册解决的是"显示什么图"

---

## 6. 调试与排错

### 6.1 瑰元助手日志行解读
| 日志 | 含义 |
|---|---|
| `连接服务: com.heytap.health → 连接成功` | 桥接就绪 |
| `安装应用 进度 {cur_size,total_size}` | 包传输中 |
| `安装应用 成功 {"code":200}` | 装上了 |
| `安装应用 失败 {"code":5005,"msg":"invalid package"}` | manifest 非法（九成是缺 icon）或容器坏 |
| `启动应用 成功/失败 {"code":5001,"msg":"The package was not found"}` | 包名不存在（没装上/拼错） |
| `切换 rpc 日志 成功` | 开/关表端日志流 |

### 6.2 表端 RPC 日志关键行
| 行 | 含义 |
|---|---|
| `mas.ffi: exec ffi fun app_launch` | 启动命令已到表端 |
| `app.life: call onShow in pkg://<包名>` | 应用级 onShow |
| `app.life: call onInit/onReady in pkg://<包名>/main/index.uo` | 页面初始化/就绪 |
| `lvgl: exit application <包名> success` | 应用被退出（升级覆盖/exit） |
| `lvgl: eventLoop refresh N … stop it` | 渲染循环正常（静止后 5 帧停刷） |
| `gx_on_screen_on/off` | 表屏亮/灭（灭屏时的启动看不到画面是正常的） |
| `query network status / mas network … online` | 表端网络状态 |

### 6.3 错误码速查
| 码 | 出处 | 含义 |
|---|---|---|
| 200 | 助手各操作 | 成功 |
| 5001 | 启动应用 | 包不存在 |
| 5005 | 安装应用 | 包非法（缺 icon / 容器坏） |
| 10002 | R17 协议链 | urn 未带版本号 → profileId 解析错 |
| 10110 | R17 协议链 | sendMessage 帧缓冲不足（协议链特有） |

### 6.4 十坑速查（详见 HANDOFF.md 第 3 节）
icon 必填 / 商店通道装不上自研包 / 桌面齿轮图标 / 表端深睡断连 / ColorOS 冻结探针 / 模拟器缺 launcher / CDN 要签名 / UI 自动化弹窗插队+屏幕旋转 / MSYS_NO_PATHCONV / logcat 缓冲小。

---

## 7. 启动器 purrlaunch-lite（已在表上运行）
- 位置：`G:\oppo\purrlaunch-lite\`；包名 `com.xfaith.woodenfish`（占位原木鱼），versionCode 8
- 功能：`pkg.list('app')` 动态列表（系统包排后、隐藏自己）→ 点击 `launch()` 启动
- 已验证：升级覆盖商店版木鱼 ✅；页面生命周期/渲染全通 ✅；新装的应用自动出现在列表
- 改回木鱼：手表应用商店重装（会反过来覆盖启动器）；改启动器：改 src 后按第 4 节重装

---

## 8. 深水区档案索引（商店协议逆向——仅"绕过瑰元助手"时需要）
| 文件 | 内容 |
|---|---|
| work/findings_R14_transfer_success.md | 文件端到端送达 + 五个必须同时做对的修复 |
| work/findings_R15_cid10_protocol.md | cid=10 安装预检 protobuf 协议还原 |
| work/findings_R16_cid10_live.md | cid=10 实弹 |
| work/findings_R17_wireformat.md | MEX 帧契约（15B 头、offset=15、主路径唯一活路） |
| work/findings_R18_final.md | 完整安装链 1:1 复刻 + MsgBody 字段号权威表 |
| work/findings_R19_sideload_success.md | 5005 根因 + 调试通道成功全过程 |
| work/findings_R20_launcher.md | 自建启动器 + 升级覆盖商店应用 |
| ftprobe18/（versionCode 18） | 协议探针：cid=10 + Op4 文件推送 + cid=3 列表，一键复跑 |
| work/auto_verify.sh | 无人值守验证循环（已停，留作参考） |

何时需要：要在**无手机 UI 交互**的前提下静默安装/群发应用、或研究商店下载协议。日常开发**不需要**。

---

## 9. 文件地图（G:\oppo）
```
HANDOFF.md                ← 快速交接（10 坑清单+黄金路线）
DEV_GUIDE.md              ← 本文档
com.rtos.qrwatch/         ← 二维码应用（v2 已装：10 秒自动轮播）
purrlaunch-lite/          ← 启动器（已装：占位 com.xfaith.woodenfish）
com.rtos.pkg              ← 旧版商店格式包（历史产物，勿再用于安装——缺 icon 会 5005）
.toolchain/               ← gx v0.11.0
pkg_tools/                ← pkg_tool.py + PKG_FORMAT.md
work/
  ├─ glyphix_os/web-docs/ ← 官方文档克隆（api/ components/ framework/ tutorials/）
  ├─ findings_R1x*.md     ← 各窗口战报（R14~R20）
  ├─ glyphix_assistant/   ← 瑰元助手 APK + dex 字符串
  ├─ oafsrc/ routesrc/    ← 健康 App 反编译片段（hermes 产物）
  ├─ store_catalog.json   ← 手表商店 127 应用元数据
  └─ ftprobe*.*           ← 协议探针工程与日志
```

## 10. 参考链接
- 官方文档（本地）：work/glyphix_os/web-docs/src/；线上 github.com/glyphix-os
- 米坛教程：bandbbs.cn/threads/29508/（附件 10856.webp = Purrlaunch rpk，需论坛互动后下载）
- 作者渠道：B 站"苏州优质男青年"（瑰元助手/Purrlaunch 同作者 libiao0418）

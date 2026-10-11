# findings_R21 —— 官方 PurrLaunch（GitHub 源码构建）上表成功（2026-10-11 11:24-11:29）

## 来源
- 用户提示后定位：github.com/purrrock/PurrLaunch（MIT，"Launcher PKG for smartwatchs"）
- release v1.2.2 有构建好的 com.xfaith.woodenfish.pkg，但 github.com 下载被墙
- → 走 api.github.com 拉全源码树（17 文件），本地 gx 构建，OWS 适配两处：
  1. manifest 补 `icon: /assets/icon.png`（猫爪图，OWS 必填校验）
  2. versionCode 1 → **9**（覆盖本窗口早先自装的 purrlaunch-lite v8）

## 功能（作者 UNDER-THE-HOOD.md 亲述，DT NO.1 G1 上逆向所得）
- **桌面菜单是硬编码的，OS 不动态扫描包目录** → 第三方应用上桌面的唯一入口 = 占位硬编码槽位（木鱼）
- 点击槽位 → AppletKit(C++) 查 ADBT_Applet 库 → 把控制权交给该包名的 JS 应用
- 自带：应用列表（pkg.list+getInfo，排除自己/模拟器）、**USB 直装 pkg**（VFS 暂存 800ms I/O 屏障 →
  pkg.install({src,force:true,verify:false})，绕沙箱）、文件管理器、卸载页、系统信息、文本查看器
- 安装签名：`{src:"internal://files/app.pkg", force:true, verify:false}`（厂商 JSI 只认 src 键；
  pkg.remove 传字符串会静默崩溃——ATS3085S 的 JSI 异常，OWS 未必相同但可参考）

## 验证
```
11:28:01.594  安装应用 成功 (36512B，升级覆盖 lite v8)
11:29:29.092  启动应用 成功
```
- 源码（OWS 适配版）：G:/oppo/PurrLaunch/（含猫爪图标 assets）
- 同时发现用户自行安装了 com.rtos.libraryqr（7104B，11:18 装 11:21 启动）——管线已被复用 ✓

## 图标问题最终结论
- 桌面槽位图标 = OS 侧资源（硬编码菜单 + 商店安装时的 icon_<pkg> 单独注册）
- 侧载包（无论调试通道还是包内自带 icon）桌面一律回退齿轮——**包侧无解**，属 OWS 桌面缺陷
- 用户可用性无损：启动器列表有名字有包名，点击即启动
- 若未来要根治：探针已具备 icon_<pkg> 文件推送能力（ftprobe18 R20b 改造，本次未验证——
  探针启动被 Android 14 "did not call finish" 拦截，需解锁手机后跑），或期待瑰元助手 v1.3

---

## 附：icon_<包名> 推送实验（R21b，2026-10-11 11:55）——待重启验收

**假设**：OWS 桌面图标是数据驱动（木鱼商店装完有图标 → 我们调试覆盖后变齿轮 = 注册丢失），
商店的图标注册约定 = `icon_<包名>` 文件经 sid=272 文件通道（FileInfo=appstore_uri_icon）单独送达。

**实验**（ftprobe18 R20b 改造，assets 内置 432px 图标 PNG）：
```
handleAuthentication -> 1001（需 authentication_state=1 旁路；跑完已回 0）
peerFound=true liveAccId=9486
cid=10 预检 → pkg 推送×3 候选（transId 2102272838/1876337960/288563888，全部 handleTransferComplete）
icon 推送：transId 291786294，FT_SETUP_REQ fileName:icon_com.rtos.qrwatch，
           Total 843 Read 843 ✓ → handleTransferComplete: Completed :icon_com.rtos.qrwatch ✓
           md5:49d7136194c8c411f003343a5e88a8b7
```
**线上 1:1 复刻商店顺序（pkg→icon，同通道同约定），零错误。**

**验收**：重启手表 → 看桌面"启动器"槽位图标是否变为二维码图案。
- 若变 → 图标问题解决（icon_<包名> 推送 = 图标注册方法，可写进 DEV_GUIDE）
- 若仍齿轮 → OWS 桌面（nucleus 0.7.0-68）的图标源不是该约定（与 G1 不同构），
  包侧再无抓手，剩余路径仅：瑰元助手 v1.3 / 接受齿轮

**工程注意**：探针必须 authentication_state=1 才能过框架认证（1001），日常跑完记得回 0；
探针 Activity 在手机锁屏/灭屏时启动会被 Android 14 强杀（"did not call finish"），需亮屏解锁状态跑。

---

## ✅ R21b 验收结果（2026-10-11 12:2x）：**图标注册法生效**
用户目视确认：重启手表后，桌面"扫码卡片"（com.rtos.qrwatch）图标已变为二维码图案。
`icon_<包名>` 推送法正式转正为标准 SOP（DEV_GUIDE.md 5.3）。

## R21c 双图标批量注册（12:19）
```
12:19:11  FT_SETUP_REQ fileName:icon_com.rtos.qrwatch      (843B)  → handleTransferComplete ✅
12:19:23  FT_SETUP_REQ fileName:icon_com.xfaith.woodenfish (1364B) → handleTransferComplete ✅
```
- ftprobe18 已改为 icons[][] 多图标循环（fireIconPush(acc, peer, assetName, wireName)）
- 待用户重启确认启动器槽位图标（猫爪）
- 跑完 authentication_state 已回 0 ✓

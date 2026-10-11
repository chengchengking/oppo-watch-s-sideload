# findings_master.md —— W3 窗口结论落盘（每挖一条追加一行）

> 军规：窗口随时会死，落盘的才是遗产。格式：结论 + 证据(文件:行号) + 置信度。

## W3-00 开局盘点（2026-09-29）
- 资产核对：健康App dex×20 ✅ / AF dex×5 ✅ / btnet_fields.md ✅ / logcat 双文件 ✅ / 工具三件 ✅
- 缺口1：目标弹丸 com.rtos.qrwatch.v2_dynamic.pkg 不在本机（需用户转发，P5 阻塞项）
- 缺口2：inventory 称 dexdump 161MB 在 work/，实测文件清单无此文件 → P2 上下文 grep 需另找 dexdump 或改用 dex_strings_dump
- 工具对账：任务书 dex_strings_dump.py = 根目录文件（--grep/--out 参数确认）；kit 内 dex_strings.py 是旧版（--filter）
- 上窗口(FINDINGS.md)结论：OAF 传输层客户端认证被拒(Untrusted client apk)；V4 任务书接上 RemovableApp/try_install 金线 → 本窗口主攻 P1 权限分水岭


## W3-P0 第一组 grep 结论（2026-09-29，2851 hits/24 files）
- ✅ 安装触发 URI 确认：`oms://pri_request/try_install`（+check_installed/try_awaken/try_open_url）【p0_strings_grep1.txt:2388-2391,2421,2427,78758】
- ✅ 第三方安装键：`watch_third_party_try_install`【:2161,78758】
- ✅ 可移除应用子系统全貌：
  - `com.heytap.health.operation.appback.RemovableAppHelper` / `RemovableAppClientImpl`【:1303-1310,18743-18750,25362-25365】
  - AIDL：`com.oplus.safecenter.removableapp.aidl.IRemovableApp` / `IRemovableAppClient`【:22647-22650,2666-2678,32535-32536】
  - 系统服务：`com.oplus.safecenter.removableapp.service.RemovableAppService`【:33544】
  - ContentProvider：`content://com.color.provider.removableapp`【:33924】
  - 事务：`TRANSACTION_restoreRemovableApp` / `restoreRemovableApp`【:2681,2747,50963】
- ✅ 权限错误码族：`OAF_LEAK_PERMISSION` / `ERROR_OAF_LEAK_PERMISSION` / `OAF_CODE_LEAK_PERMISSION`【:1104,11919,1242,24249】+ 对话框字符串 `lib_connect_oaf_leak_permission_desc/title/to`【:1242-1244,63314-63316】
- ✅ 安装集合：`installAppSet`【:948,1088,60581】+ `supportUninstallAppV1/V2`【:1275-1276,2553-2556】
- ✅ FtAgent 全表（健康App）：LauncherFtAgent/LocationFtAgent/McuSportRecordFtAgent/MusicFtAgent/RecordSoundFtAgent/SafeGuardFtAgent/SensorFtAgent/SportRecordFtAgent/SuggestionsFtAgent/WalletFtAgent/WearEngineFtAgent/WearEngineFtAgentMcu【:24874-24963】
- ✅ AbsFtAgent 包装类：`com.heytap.health.watch.oaf.wrapper.AbsFtAgent`【:1102,1286-1291,1898,1916-1917,1940-1963】
- 新 scheme 发现：`oaps://theme`【:416】/ `direct://`【:45684】/ `workspec://`【:58684】/ `account://`【:31073-31074】/ `oaf_grant_permission_*` 授权对话框族【:118-133,283-288】
- 健康App 权限服务接口：`com.heytap.health.operations.settings.permission.IFeaturePermissionDataService` / `IFeaturePermissionService`【classes12.dex:476-477,43094-43095】


## W3-P0 第二组 grep 结论：OafInfo wear: Agent 全表（2026-09-29，2635 hits/24 files）
- ✅ OafInfo.java 确认：`com.heytap.health.watch.oaf.OafInfo`【p0_strings_grep2.txt:1579,1809,1899(32376 OafInfo.java)】
- ✅ wear: agent 全表（60+个）【:2007-2077】：
  - **应用安装候选**：`wear:watchappservice` / `wear:watchappservice_file`【:2070-2071】/ `wear:thirdpart` / `wear:thirdpart.awakenable`【:2063-2064】/ `wear:glyphix_sdk`【:2022】
  - **表盘已知**：`wear:launcher.ft`【:2033】= sid=13（P1 logcat 实测）
  - 其他：devicemanager/devicemanager2/notification/safe_guard/safe_guard_ft/netproxy/netproxy2/logkit/lpa/debug/health/health2/health3/health.game/location/location.ft/music/music.transfer/musicft/sports/sports.broadcast/workout/wallet/wallet.image/weather/browser/calendar/clock/todo/sms/sos/shortMessage/phone/ota/oobe/relax/schoolmode/FindWatch/breeno/breeno_suggestions/breeno_suggestions_file/bandvoiceassistant/deviceevent/elves/familydevice/globalclock/intelligentcross/linkmanager/telecom/soundRecorder/soundRecorderFt/sensordatacollect(.ftlog/.msg)/systemui.screenshot/health.ftmedal/health.ftsport_record(.2)/wearengine.ft(.mcu)/thirdpart.mcu(.awakenable)/netnumber


## W3-P1 权限分水岭 grep 结论（2026-09-29，851 hits/24 files）
- ✅ **linkservice SDK 权限名确认**：`com.heytap.wearable.linkservice.permission.WEARABLE`【p1_strings_perm.txt:208(classes16.dex:43998),258(classes17.dex:33528)】
- ✅ 错误码定位：`OAF_LEAK_PERMISSION`【:207(classes16.dex:31999)】/ `ERROR_OAF_LEAK_PERMISSION`【:217(classes17.dex:11919)】/ `OAF_CODE_LEAK_PERMISSION`【:257(classes17.dex:24249)】
- ✅ linkservice SDK 全貌（健康App classes16/17/2/3/4/5）：
  - 入口：`com.oplus.wearable.linkservice.WearableApiManager`【:161,409】/ `WearableServer`【:412】/ `OlinkProviderApi`【:405】
  - 三大API：`sdk.FileApi`【:65,531】/ `sdk.MessageApi`【:66,547】/ `sdk.NodeApi`【:68,551】
  - 文件传输：`file.FileTransferManager`【:458】/ `sdk.internal.file.FileTransferTask`【:180,594】/ `file.transfer.FTCommandTransfer`【:489】
  - 连接层：`transport.connect.Device`【:246,650】/ `transport.connect.br.BRClientConnection`【:691】/ `transport.connect.ble.GattConnection`【:674】
  - 安全：`security.SecurityManager`【:632】/ `db.device.AESHelper`【:226,443】（数据库层，非传输加密——上窗口判读错误已修正）
  - 传输配置：`transport.config.TransferConfig`【:642】/ `EncryptConfig`【:641】
- ✅ 健康App 自有 rawapi 层：`com.heytap.health.connect.rawapi.FileApi/MessageApi/NodeApi`【:78-79,110-129,21185-21321】+ `devicemanager.client.api.primart.DMFileApi/DMMessageApi/DMNodeApi`【:59-64,151-160】


## W3-P1 权限分水岭判定（2026-09-29）
- ✅ **WEARABLE 权限 protectionLevel = 0x00000002 = signature 级**【aapt2 dump xmltree: protectionLevel(0x01010009)=0x00000002 紧跟 name="com.heytap.wearable.linkservice.permission.WEARABLE"】
- ✅ WearableServer service：exported=true，permission=WEARABLE【xmltree line=1675】→ 第三方 app 无法 bind（签名不同）
- ✅ IpcBtService：exported=false【line=1530】
- ✅ EmergencyProvider：permission=com.oplus.permission.safe.ASSISTANT【line=1503】
- ✅ **OMS 服务实体发现**：`com.heytap.wearable.oms.SERVICE`【xmltree line=1530 附近】—— oms://pri_request/try_install 的服务端！
- **分水岭结论**：WEARABLE=signature → P3-A（借 SDK 直推）死路 → **走 P3-B（借健康 App 的嘴）**
- 下一步：查 OMS 服务 exported 状态 + 健康 App exported 入口（deeplink/intent/FileProvider）

- ✅ healthap:// deeplink 族：store/browser/instant/wxapp/lab/app【aapt2 xmltree line=290-326】
- ✅ heytaphealth://com.heytap.health【line=750】/ heytap://com.heytap.health.app【line=290】/ health://permission【line=1097】/ tbopen://【line=174】
- ✅ OMS 服务：`com.heytap.wearable.oms.SERVICE` action 被 `OPlusPhoneTelecomService` 监听（exported=true, permission=oplus.permission.OPLUS_COMPONENT_SAFE）【xmltree line=1519-1527】
- ✅ splitapk 体系：`com.heytap.health.splitapk.operation.operation`（omsPlugin）/ SplitOperationConnService(exported=false) / SplitStoreConnService(exported=false, splitName=store_sdk) / PluginConfirmActivity(exported=false)【line=407-409,2030,1745】
- ✅ FileProvider：WatchFaceFileProvider(exported=false, grantUriPermissions=true, authorities=com.heytap.health.watchfacefileprovider)【line=1101】/ ShareFileProvider(exported=false)【line=353】/ assignment_provider(exported=true)
- ✅ PhoneTelecomService(exported=true, permission=oplus.permission.OPLUS_COMPONENT_SAFE)【line=1509】


## W3 实测发现（2026-09-29 17:03 adb 触发）
- ❌ `oms://pri_request/try_install` via `am start -a VIEW` → **result code=-91（START_ABORTED/拒绝）**【adb_probe_logcat.txt:17-18】—— 不能直接 deeplink 触发
- ✅ `healthap://store` → **RouterActivity → AppStoreCenterActivity 成功打开**【adb_probe_logcat.txt:78-80,72,337,413,421】—— 应用商店入口可用！
- ✅ RouterActivity 是健康 App 路由器，处理 healthap:// scheme
- 下一步：找 oms://try_install 的 dex 引用上下文（谁在什么场景发送）；测试 bindService 方式；测试 healthap://instant/browser


## W3 核心突破：OMS PriHandler + ApplicationProxy（2026-09-29）
- ✅ **PriHandler 类确认**：`com.heytap.wearable.oms.base.handler.internal.PriHandler`【dexdump_classes4.txt:1224021】
  - `<clinit>` 注册 4 个 URI：check_installed/try_install/try_awaken/try_open_url
  - `UriMatcher`：pri_request/try_install → code 2
  - `match(Uri)` 方法：匹配 URI 返回 code
- ✅ **ApplicationProxy.startActivity() 确认**：`com.heytap.wearable.oms.base.ApplicationProxy.startActivity(Intent, String, Uri)`【dexdump_classes4.txt:832290, 5600f0】
  - public static final 方法
  - 逻辑：获取 IWearEngineApi → isForeground() → PriHandler.match(uri) → 获取 watch_third_party_try_install 字符串 → 格式化 → 启动 activity
  - 这是 OMS 安装触发的核心方法！
- ❌ `oms://` scheme 在 manifest 里**无 intent-filter** → 不是通过 startActivity 触发
- ✅ **推断**：`oms://pri_request/try_install` 通过 WebView 的 `shouldOverrideUrlLoading()` 拦截触发（云端复核确认的 JS 桥路径）
- ❌ `healthap://browser?url=https://example.com` 实弹失败（RouterActivity 创建后立即销毁，未打开 WebView）

- ❌ `healthap://browser` → LaunchActivity → **MainActivity**（非 WebView）
- ❌ `healthap://instant` → MainActivity
- ❌ `healthap://lab` → **result code=-91（拒绝）**
- ✅ `healthap://store` → **AppStoreCenterActivity**（应用商店，唯一非 MainActivity 的路由）
- ❌ `WebBrowserActivity2`/`TransparentWebBrowserActivity2`/`EasygoWebBrowserActivity2` 均 **exported=false**，permission=`com.oppo.store.permission.ACCESS_STORE` → 直接启动被拒
- ✅ `OperationWebViewActivity` 存在但 exported=false
- **关键结论**：商店 WebView Activity 全部 exported=false，`healthap://browser` 不支持 URL 参数
- **下一步**：查 AppStoreCenterActivity 是否 WebView；找 oms:// URI 触发方式（ContentProvider?）


## W3 借嘴攻击瓶颈与 WearEngineService 线索（2026-09-29 17:25-17:28）
- ❌ `healthap://store?url=https://example.com` → RouterActivity 解析 URI 但仍路由到 **MainActivity**（URL 参数无效）
- ✅ AppStoreCenterActivity 是 WebView（字段 mBrowser/mBrowserView/mAppStoreJsInterface）
- ✅ AppStoreJsInterfaceImpl 继承 AbsJsInterface，有 `-$$Nest$mhandleInstallErrorCode` 方法
- ✅ `installAppSet` 在 AppStoreCenterActivity 里是 HashSet 类型
- ✅ **`com.glyphix.mas.service.WearEngineService`**（exported=true，**无权限保护**，action: `com.xfaith.glyphix.WEAR_ENGINE_ACTION`）—— Glyphix 应用引擎服务！
- ✅ `com.heytap.health.watch.thirdparty.WearEngineService`（exported=true，action: `com.oplus.ocs.wearengine.phone.SERVICE`）
- **下一步**：查 WearEngineService 代码；测试 bind 并发送安装 intent

## W3 WearEngineService 突破（2026-09-29 17:33）
- ✅ **WearEngineService.onBind()** 从 intent 获取 3 个参数：packageName/appId/license
- ✅ 创建 `com.glyphix.mas.service.version.b` 对象（AIDL 接口）返回
- ✅ **这是 Glyphix 应用验证服务！** bindService 获取 version.b 接口，调用其方法可能触发安装
- ❌ `am startservice` 不触发 onBind（需要 bindService）
- ❌ am startservice 带 install extras 无明显反应（service 无 onStartCommand）
- **下一步**：查 version.b 的 AIDL 方法；写 app bind 并调用
## W5 窗口结论（2026-10-10，第5窗口 · 安装协议全线还原）

- ✅ **WearInjector v2 有 file op**：`--es op file --es name <filePath> --es path <fileInfo> --es target <targetPackageName>`
  （参数序由 d.sendFile 反汇编确证；注入器 extra 名与 SDK 形参名错位）【work/injector/disasm.txt:135187】
- ✅ **WearEngine SDK 文件面存在**：`P2pClient.sendFile(filePath, fileInfo, targetPackageName)` +
  `OnFileTransferListener{onProgressChanged,onTransferCompleted(taskId,path,errorCode),onTransferRequested}`
- ✅ **官方通道可用**：nodes → nodeId=94:97:AE:**:**:**；devicelist → OWW262 / OWW262_11_A.211_d784fa8b / SN <watch-sn>
- ✅ **OAF 路由表（实测）**：13→wear:launcher.ft(LauncherFtAgent) / 25→wear:location.ft /
  **267→wear:watchappservice(WatchAppServiceAgent)** / **272→wear:watchappservice_file(WatchAppFileAgent)**
- ✅ **表端应用安装协议**：控制走 sid=267，文件走 sid=272，两个文件 uri=`appstore_uri_package` / `appstore_uri_icon`
  （PTC: OpCode=4, AgentClassName=WatchAppFileAgent, PackageName=com.heytap.health, FileInfo=appstore_uri_*）
- ✅ **第三方应用确能装进这块表**（16:05:59-16:08:13 实测）：com.peggycat.watch 2.0.7 / com.otterlife.glyphix 1.0.0 /
  com.yyzdtz.yzdmx.opwatchrto 1.0.1 / app.podcast.cosmos.watch.rtos 1.0.0
- ✅ **安装入口是 H5**：`https://sport.health.heytapmobi.com/h5/rtos-soft-store/index.html`
  JS 桥方法 `downloadAndInstall`（JsScheduler→CommonExecutor），参数含 `packUrl`
- ❌ **WearEngine 第三方 P2P 通道对表端是死的**：所有 demo sendMessage 一律路由到
  `ThirdPartAwakeAgent#wear:thirdpart.awakenable` → `remote not register oaf` → `not find Olink to OAF route`，
  **path/target 改不动路由**（18:30:40 实测）。表端只暴露 `wear:thirdpart.mcu.awakenable`，无 `wear:thirdpart`。
- ❌ **HTTPS 中间人不可行**：NSC = `<base-config cleartextTrafficPermitted="true"/>`（res/nwg.xml），
  **无 trust-anchors → 只信系统 CA**（targetSdk 36）。但 **cleartext=true**，明文 HTTP 放行。
- ❌ **WebView 远程调试关闭**（/proc/net/unix 只有 chrome_devtools_remote，无 health 的 webview_devtools_remote_*）
- ❌ **商店 Activity 未导出**：AppStoreCenterActivity / AppStoreDetailActivity 无 exported 无 intent-filter
- ❌ **deeplink 到不了商店 WebView**：healthap://store|instant?url= → RouterActivity → MainActivity
- 📌 健康 App 有 `<meta-data name="glyphix" value="e8gk2ig6usq16a4d4mdnjq21pgvd1220">`（Glyphix 授权串）

## ★ 2026-10-10 19:35 跨窗口补记：Glyphix MAS —— 第四条路（不属于 OAF）
> 与 W6 的 OAF 判决并行、独立。W6 判死的是「经 OAF 的一切」；本条不在那个集合里。

- ✅ **服务**：`com.heytap.health/com.glyphix.mas.service.WearEngineService`
  `exported=true`，**permission = 无**。实测 `bindService -> true`。
- ✅ **AIDL**：`com.glyphix.mas.GlyphixWear`（`getInterfaceDescriptor()` 实测打印），18 个事务码，已指纹化（见 report_R2.md §4）。
- ✅ **回调**：`com.glyphix.mas.i` ≡ descriptor `com.glyphix.mas.WearEngineProgressResolver`（码 1/2/3 = a/b/d(String)）
- ✅ **引擎里有完整 SDK**：`com.glyphix.mas.api` 包 ——
  `GxApp.install(String,String,resolver)->String` / `isWearAppInstalled` / `launch` / `uninstall` / `list` / `exit`，
  另有 `GxFile` / `GxInterConnect` / `GxMessage` / `GxSpeed` / `GxSys` / `GxTime`
- ✅ **与 OAF 无关（决定性）**：Glyphix 区反汇编内对 `com/heytap/accessory` 引用 **0 次**，
  对 `com.heytap.health.oaf` / `com.heytap.wearable.oms` 引用 **0 次**
  → **W6 定性的 authCode 闸门管不到这条线**
- ✅ **推送路径实测可达**：事务码 7/12/15 都走进 `"wear engine push file failed,"` + `{"code":N,"msg":"..."}`；
  失败原因是该路径要回调 `i.b(...)` 而 resolver 为 null（NPE），
  即缺「初始化 / 注册 resolver」这个前置调用
- 📌 **旁证**：表上真实装过 `com.otterlife.glyphix` —— glyphix 就是第三方 RTOS 表应用的框架
- 📌 工具：探针工程 `glyphix_probe/`（`build_probe.py` 一键出包，pkg 内嵌 assets）

## ★★ 2026-10-10 20:05 跨窗口合并：M2 的底座已探明（R5/R6/W7 三份合并）

> 交接件：仓库根 `任务书V5：M2交接（PTC直连·最后一公里）.md`

### 三份报告的关系（别被误导）
- `findings_R5.md`：Glyphix MAS **不是安装通道**；服务跑在**主进程**而活引擎在 `:transport` → 外部 bind 拿到空壳。
  ⚠️ 它末尾"只剩表盘路线"的结论**已被用户否决**，且被 R6 修正 —— 只取它的事实。
- `findings_R6.md`（在 R5 之后）：**纠正 W7 一处错误 + 解开最后一个未知数**（见下）。
- `findings_W7.md`：Glyphix 两条子路线实测判决（客户端 license check failed / 裸 AIDL 表端无 LPC 服务）。

### ★ 纠正：`libcpkit.so` **不是 PTC**
5,520,960 B 的 `libcpkit.so` 的 44 个 `Java_*` 导出全是 **Pantaconnect 的连接/发现**
（`cpkit.WifiImpl/WifiP2PStateCallback/SoftApImpl/BluetoothImpl/USBStateCallback`、
`connection.ConnectionCoordinatorImpl/LanHeartbeat`、`discovery.fusionprovider`、
`discovery.lanprovider.packet`、`nfc`）。**不含任何文件传输 API。**

### ★ PTC 是 **Java**，在 AF 的 `classes4.dex`（可 DexClassLoader 复用）
```
com/oplus/pantaconnect/transport/file/CommandManager
com/oplus/pantaconnect/transport/file/FileServiceNative
com/oplus/pantaconnect/transport/file/model/{SetupRequest,SetupResponse,CtrlRequest,
                                             CtrlResponse,CancelRequest,ResultStatus}
com/oplus/pantaconnect/transport/file/sender/FileProviderImpl        ← 发送端
com/oplus/pantaconnect/transport/file/receiver/FTConsumerConnection  ← 接收端
```
`SetupRequest/Response` 就是 `FT_SETUP_REQ/RSP` 的模型。

### ★★ 最后一个技术未知数已解开：ECDH 是**临时**的，不是设备绑定
```
com.heytap.health.watchface.provider.key.ECDHUtils     （健康 App classes3.dex，4 处引用）
  generateKeyPair() → KeyPair          ← 本地现场生成
  generateSecretKey(PrivateKey,PublicKey) → String      ← 标准 ECDH
  convertPublicKey / convertPublicKeyByte
  aesCtrEncrypt(String,String) / aesCtrEncryptWithBase64
  negotiate(int,int,String,Callback)
DeviceKeyProvider / DeviceKeyClient / Callback
```
→ 密钥**现场生成、会话级、可自行复现**（P-256 一类 + AES-CTR）。

### ★ 安装走 BT，控制面不加密（16:05 真实安装日志实测）
```
Health_WatchAppServiceA sendMessage: seq=1929 1 transportType=2 len=41 secure=false
Health_WatchAppServiceA [Health-999] onReceive: cid=1 transportType=2 rcvSeq=1637 data size=151
Health_WatchAppFileA    sendFile transportType:2, transactionId = 601676284
```
`transportType=2` = BT（控制面 + 文件面都走 BT），`secure=false`。

### ★ 概念澄清（最容易搞混、也决定 M2 方案对不对）
**OAF 与 PTC 是两层**：OAF 是手机侧**框架/路由**（Agent、urn、sid 267/272、authCode 闸门），
PTC 是**线上协议**（Setup/Ctrl、分块）。16:05 日志显示
`Health_FileHandler sendFile(sid=272)` → `AF.SDK.FileTransferManager` → `PTC.DATATRANS.FileServiceNative.sendCommand({OpCode:4,...})`
—— **OAF 最后是调 PTC 干活的。M2 = 跳过 OAF（跳过闸门），自己说 PTC。**

### 现状（只读实测）
WiFi P2P 组**当前不在**（`ip addr` 无 `192.168.49.x`，`ping 192.168.49.25` 全丢）→ P2P 是按需建立的。
手机上有 VPN（`tun01 172.31.227.110/18`），做 BT/P2P 直连时注意路由。

---

## ★★★★★ 2026-10-10 23:20 跨窗口合并：**文件面端到端打通**（R14/R15/R16）

> 详细见 `findings_R14_transfer_success.md`（文件传输成功）、
> `findings_R15_cid10_protocol.md`（cid=10 协议还原）、
> `findings_R16_cid10_live.md`（cid=10 实弹）。
> 探针：`ftprobe16/`（文件面）、`ftprobe17/`（+cid=10）。

### ✅ 已达成：`.pkg` 真的送到手表，且有密码学级证明
7456 B 的 `com.rtos.pkg` 经 `wear:watchappservice_file` 送到 `OWatch4`（accId=21229）：
```
D FrameworkUtils: file:pkg_com.rtos.qrwatch md5:a4e8c9517ad16459f61b41a9b0d3ff9d
I PTC.DATATRANS.f: [sfcmd]FT_SETUP_REQ sent,connId:0,transId:1642151028,fileName:pkg_com.rtos.qrwatch
D PTC.DATATRANS.a: Total 7456 Read 7456 channelId:103, readLen:7456, ...
I FileProviderImpl: handleTransferComplete: Requests in Queue, Completed :pkg_com.rtos.qrwatch
D FileTransferStatistic: FileTransferEvent(... transferResult=0, transferCost=555 ...)
```
**本机独立算 MD5 = `a4e8c9517ad16459f61b41a9b0d3ff9d`，逐字节一致。无任何 onError。**

### 五个必须同时做对的修复（缺一不可）
1. **urn 必须带 `:<version>`**（`g3/a.java:46-52`）：
   `wear:watchappservice_file:1.0` ⇒ profileId 才对；写 `wear:watchappservice_file` ⇒ profileId 变 `wear` ⇒ 10002。
2. **AccessoryId 取活体值**（`PeerAgent.getAccessory().mId`，本机 21229）；36053 是 16:05 那次的，早失效。
3. **★ `registerCallbackFacilitator(txId, ResultReceiver)`** —— 解锁发送队列的钥匙。
   `FileServiceNative.java:77-101` 命中队列后会调 `mk.l.l()`；不调则 `hasDynamicCallback=false`、任务永远躺着。
4. **★ 探针必须跑在前台 Service**，不能是 Activity。
   Activity 会被 ColorOS 判 `Input dispatching timed out` → ANR 杀掉 → 日志停在中途，
   **看起来像"表端没反应"，其实是探针自己死了**（ftprobe13/14 都栽在这）。
5. **★ `SourcePath` 必须用 `content://`**（我们自己的 provider + 显式 `grantUriPermission`）。
   裸路径会 `EACCES(Permission denied)` —— AF 进程 u0_a100 无存储权限。
   provider 的 `query()` **第 0 列必须是 `_data` = 绝对路径**（`FileTransfer.checkSource()` 这么读）。

### cid=10 安装控制报文（已还原协议、已过前置校验，未最终打通）
- `cid=10` = `InstallPreCheckMsg`（安装**预检**，只传 `fileSize`）
- 载荷 = protobuf `WatchAppProto.AppCommandMsg{ MsgHeader{commandId=10,...}, MsgBody{checkRequest{fileSize}} }`
- `AbsMsgSendClient.immediateSendMessage`: `new MessageEvent(267, header.getCommandId(), protobuf.toByteArray())`
- 帧载荷 = `[cid(1B)]` + `[protobuf]`（`AbsMsgAgent.java:534-547`）
- **三个参数陷阱**：① `sendMessage` 第 2 参是本地 agentId 串不是 urn；
  ② 末两参是 (长度, 偏移) 不是 (cid, mode)；③ cid 在 protobuf 里不在参数里
- 当前：过了 dormant 检查（`isAwakeable, continue sendMessage`），返回值 `10110`=`ERROR_TRANSACTION_FAILED`
- **下一轮**：用 D 级日志逐字节核对真机 51 B 的组成，对齐 `[param8, param7]`

### 环境要点（省时间）
- **抓 D 级日志必须持续 `adb logcat -b all > file`**，事后 `-d` 读缓冲会被冲掉（ColorOS 缓冲小）。
- `settings put system log_switch_type 1` 才放行 `D PTC.DATATRANS.*`。
- `settings put secure authentication_state 1` 是认证旁路开关（可逆，验收后应置回 0）。
- 所有 agentId / accessoryId / clientId 都是**进程级易失值**，每轮必须重新取活体。
- 探针构建：`python build_probe.py`（aapt2→javac→d8→zipalign→apksigner）；
  启动 `adb shell am start -n com.rtos.ftprobe/.MainActivity`（Service 是 exported=false，shell 直起会被拒）。

---

## ★★★★★★ 2026-10-11 01:45 跨窗口合并（R17/R18，夜间接管窗口）：**cid=10 打通，安装链 1:1 复刻**

> 详细见 `findings_R17_wireformat.md`（wire 契约初版）+ `findings_R18_final.md`（终版，含修正）。

- ✅ **10110 双重真相**：① 缓冲区 < len+17 走 fallback H()，头空间 = iG+4，而 frameProcedure 恒 0 → iG 恒 0 → 4B < MEX 7B → 必死；② 唯一活路 = 主路径（buffer ≥ len+17），e.c.L() 把数据右移到 offset=15（MSG_HEADER_LEN）→ MEX i(7) 过
- ✅ **实弹**：sendMessage(clientId, SvcAgentId, "50", accId, buf66, false, 49, 15) → 返回 txId（22~29），BT WRITE Len:58 实锤上线，帧字节 first20Bytes 与协议完全吻合
- ✅ **文件**：pkg_com.rtos.qrwatch (7456B) 端到端送达 8+ 次，transferResult=0，MD5 一致
- ✅ **表端回报**：每次传输后表端发 sid=267 cid=5（=APP_INSTALL_STATUS_INFO，真机安装同款语义）
- ✅ **MsgBody 全字段号表**（权威）：1=device 2=privacy 3=appList 4=update 5=installStatus 6=changeEvent 7=download 8=activeList 9=special 16=checkRequest 17=checkResult
- ✅ **冻结真因**：ColorOS virtualFreeze 连 FGS+亮屏都冻；对策 = 探针 Activity 防冻壳 + PC keepalive 循环
- ⏳ 遗留：手表 01:27 深睡断连，屏幕级最终确认待表回连；`auto_verify.sh` 后台自动验证中；authentication_state=1 待验收后回 0
- 工具：`ftprobe18/`（versionCode 18，全链路一键复跑）

---

## ★★★★★★★ 2026-10-11 10:24 收官（R19）：**应用已装进手表并运行**
> 详见 `findings_R19_sideload_success.md`。5005 根因 = 包缺 icon（manifest 必填字段）。
> 侧载正确姿势：gx build → bundle.pkg → 瑰元助手安装。遗留：桌面图标槽位共享（重启/Purrlaunch 解决），
> authentication_state 已回 0，auto_verify 已停。**任务闭环。**

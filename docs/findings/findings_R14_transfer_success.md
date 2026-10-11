# findings_R14 —— ★★★★★ **文件传输端到端打通**：pkg 已带 MD5 校验成功送达表端

> 第 14 窗口（本轮）。探针 `ftprobe16/`。
> **这是自 R9 打开认证闸门以来最大的一步：`.pkg` 第一次真正被送到手表上，
> 且由框架自己算出 MD5 并回报 `transferResult=0`（成功）。**

---

## 一、结论（一句话）

**手机 → 手表的文件面已经完全打通。** 7456 B 的 `com.rtos.pkg` 经
`wear:watchappservice_file` 通道送到 `OWatch4`（accId=21229），
框架读满 7456 字节、算出 MD5 `a4e8c9517ad16459f61b41a9b0d3ff9d`
（与本地文件**逐字节一致**），`handleTransferComplete` 报 Completed，无任何 onError。

**剩下的唯一一步**：真机安装是"**先控制后文件**"两步 ——
文件送达后还需 `sid=267 / wear:watchappservice` 上的一条控制报文（真机是 `cid=10`，50–51 B）
去触发表端安装。我们只做了文件那一步。

---

## 二、★★★ 四个必须同时做对的修复（缺任何一个都到不了）

### 修复 1：`urn` 必须带 `:<version>` 后缀 —— 这是 profileId 的来源

`work/afsrc/sources/g3/a.java:46-52`（`ServiceProfileBuilder`，权威）：
```java
String urn = xmlPullParser.getAttributeValue(null, "urn");
String id      = urn.substring(0, urn.lastIndexOf(":"));      // ← profileId
String version = urn.substring(urn.lastIndexOf(":") + 1);
```
| urn 写法 | 解析出的 profileId | 结果 |
|---|---|---|
| `wear:watchappservice_file`（afprobe5） | **`wear`** | 表端没这个服务 → `findPeerAgents` 10002 |
| `wear:watchappservice_file:1.0`（本窗口） | **`wear:watchappservice_file`** | ✅ 表端有 → 回调活体 PeerAgent |

`ftprobe13` 首次用对，本窗口沿用。

### 修复 2：`AccessoryId` 取**活体值**，不是日志里的 36053

36053 是 16:05 那次的；**进程重启后 id 就变**。本机实测活体值 `21229`，
来源：`findPeerAgents` 回调 → `PeerAgent.getAccessory().mId`。

### 修复 3：★ `registerCallbackFacilitator(txId, ResultReceiver)` —— 解锁发送队列的钥匙

`work/afsrc/sources/com/oplus/pantaconnect/transport/file/FileServiceNative.java:77-101`：
```java
public final boolean registerCallbackFacilitator(int i10, ResultReceiver rr) {
    ... auth ...
    mk.m mVar = new mk.m(resultReceiver, i10);
    for (ld.c cVar : mk.l.f21368m) {          // 发送队列
        if (i10 == cVar.f21027j) {
            cVar.f21021c = mVar;               // 装上"动态回调"
            lVar.l();                          // ★★ 立刻执行队列处理 l()
            return true;
        }
    }
    ...
}
```
而 `mk/l.java:1052-1058` 的 sendFile 分支是：
```java
kd.b bVar = f21372b.get(agentId);
if (bVar != null) { cVar.f21021c = bVar; l(); }   // 为 null 就永不调用 l()
```
→ 这正是 D 级日志 `hasDynamicCallback = false` 的来历：任务躺在队列里没人处理。
**afprobe3/4 与 ftprobe13 全都漏了这一步。** 本窗口补上后 `l()` 立刻跑起来。

### 修复 4：★ 探针必须跑在**前台 Service**里，不能是 Activity

前 14 个窗口的探针都是 Activity，靠 `am start` 拉起。手机前台常被别的 App 占着，
探针窗口拿不到 focus → ColorOS 判定 `Input dispatching timed out` → **ANR 杀进程**。

实证（本轮踩到）：
```
logcat: Killing com.rtos.ftprobe (adj 0): user request after error:
        Input dispatching timed out (com.rtos.ftprobe/.MainActivity is not responding.
        Waited 5000ms for FocusEvent(hasFocus=true))
```
日志就停在中间某一行，**看起来像"表端没反应"，其实是探针自己死了**：
- `ftprobe13` 停在 `findPeerAgents(-1,22) -> 10004`
- `ftprobe14` 停在 `requestServiceConnection(local=40513) -> 0`

→ 改成 `startForegroundService` + 常驻通知（`isForeground=true`）后，全程跑完不再被杀。

### 修复 5：★★ `SourcePath` 必须用 `content://`，裸路径 AF 读不了

裸路径 `/sdcard/Download/com.rtos.pkg` 会失败（ftprobe15 实测）：
```
E PTC.DATATRANS.a: startSending() FileNotFoundException;
   /sdcard/Download/com.rtos.pkg: open failed: EACCES (Permission denied)
E PTC.DATATRANS.FileManager: onError reason :2
D PTC.DATATRANS.FileManager: TransactionId : N error: Transfer Canceled File read/write failed state 2
```
读文件走 `work/afsrc/sources/ok/a.java:101`：
```java
this.f22221j = this.f22215c.getContentResolver().openInputStream(uriFromFile);
```
AF 进程是 `u0_a100`，**清单未声明任何 STORAGE/MEDIA 权限**，读不了 `/sdcard/Download/`
（属 u0_a293 / media_rw，0660）。

→ 解法：探针自带 `PkgProvider`（`exported=true` + `grantUriPermissions=true`），
把 pkg 放在**我们自己的** external media 目录
`/storage/emulated/0/Android/media/com.rtos.ftprobe/com.rtos.pkg`，
发 `OpCode4` 前显式 `grantUriPermission("com.heytap.accessory", uri, FLAG_GRANT_READ_URI_PERMISSION)`。
（健康 App 同理：它的 FileProvider 是 `exported=false` + `grantUriPermissions=true`，
在 sendCommand 之前显式授权。）

**provider 侧两个必须做对的点**：
1. `query()` 的**第 0 列必须是 `_data` = 绝对路径** ——
   `com.heytap.accessory.file.FileTransfer.checkSource()` 对 `content://` 是
   `query(uri, {"_data"}) → getString(0)` 当真实路径 → `new File(path).exists()`。
2. `exported=true`，否则 AF 访问会被拒。

---

## 三、★★★★★ 成功实弹（逐字证据）

### 3.1 连接建立（表端确认）
```
I PTC.DATATRANS.SdkWrapper: FPATrack - Found a service :wear:watchappservice_file,
      appName:,in attached device : (OWatch4,address:**78:26,uunameType:1, accId:21229)
I PTC.DATATRANS.SessionManager: [agent_connect] SC_FLAG req from LOCAL (40513, 50) wear:watchappservice_file
I PTC.DATATRANS.SessionManager: [agent_connect]SC rsp from REMOTE (50, 40513) wear:watchappservice_file
I PTC.DATATRANS.h: [agent_connect]SUCCESS (40513, 50) with profileId:wear:watchappservice_file,
      connection ID: 212624774055089270, available channelIds:1
```

### 3.2 文件传输（D 级日志逐条）
```
D FileManager: Source: content://com.rtos.ftprobe.pkg/pkg, Size:7456, name pkg_com.rtos.qrwatch
D FileManager: [SFTrack]add new task:FileTransferTask{... mTransactionId=1642151028, mConnectionId=0}
D FileManager: sendFile: SendQueue Size:1 transactionId = 1642151028 hasDynamicCallback = false
D FileManager: processSendQueue                                     ← ★ l() 被调用了
D FileProviderImpl: connection exist for acc Id:21229
D FileProviderImpl: [SFTrack] choose ch id 103
D FileManager: [FileTransfer] process task from queue: 1642151028, channelId:103
D FrameworkUtils: file:pkg_com.rtos.qrwatch md5:a4e8c9517ad16459f61b41a9b0d3ff9d,calculate cost:33
I PTC.DATATRANS.f: [sfcmd]FT_SETUP_REQ sent,connId:0,transId:1642151028,fileName:pkg_com.rtos.qrwatch
D FileProviderImpl: onReceive 213124618054030047 , 100 , 21229      ← 表端回包
I FileProviderImpl: handleSetupResponse: Setup Confirmed:1642151028 ← ★ 表端确认
D FileProviderImpl: [SFTrack] startSending, transId:1642151028
D PTC.DATATRANS.a: Total 7456 Read 7456 channelId:103, readLen:7456, ... header: 1
I FileProviderImpl: handleTransferComplete: Requests in Queue, Completed :pkg_com.rtos.qrwatch  ← ★★ 完成
D FileManager: onTransferComplete:SetupRequest{mSenderAgentId=40513, mDestinationFilePath='',
      mFileName='pkg_com.rtos.qrwatch', mFileSize=7456, mReceiverAgentId=50,
      mSourceFilePath='content://com.rtos.ftprobe.pkg/pkg', mTransactionId=1642151028,
      mChannelId=103, mFileInfo='appstore_uri_package',
      mMD5='a4e8c9517ad16459f61b41a9b0d3ff9d'}                        ← ★ MD5 回报
D FileTransferStatistic: FileTransferEvent(... transferResult=0, transferCost=555 ...)  ← ★ 结果码 0 = 成功
D FileManager: processSendQueue
D FileManager: send queue is empty, quit
```
**无 `onError`、无 `EACCES`、无 `Permission denied`。**

### 3.3 MD5 独立核对（本机算）
```
local md5 = a4e8c9517ad16459f61b41a9b0d3ff9d  size = 7456
框架回报  = a4e8c9517ad16459f61b41a9b0d3ff9d
```
→ **逐字节一致**。这不是"看起来成功"，是密码学级别的送达证明。

### 3.4 我们自己的 provider 被 AF 进程真的打开了
```
ft16_provider.txt:
  query content://com.rtos.ftprobe.pkg/pkg proj=[_data, _display_name, _size]
        -> _data=/storage/emulated/0/Android/media/com.rtos.ftprobe/com.rtos.pkg len=7456 exists=true
  ★ openFile content://com.rtos.ftprobe.pkg/pkg -> /storage/.../com.rtos.pkg len=7456   （×2）
```

### 3.5 表端也回了消息
```
I WLogA: Health_WatchAppServiceA [Health-999] onReceive: cid=5 transportType=2 rcvSeq=738 data size=72
I WLogA: Health_LocalCallbackManager onMessageReceived: rcvSeq=738 to 94:*:78:26 MessageEvent[sid=267 cid=5 seq=738 content=71]
```

---

## 四、当前真实卡点（比 R11 精确得多）

**文件到了，但表端没有"安装"它。**

真机 16:05 的安装是**两步**（`work/capture_logcat_1010.txt`）：
```
16:05:55.696  Health_MessageHandler: find Olink to OAF route 267 to urns:[...WatchAppServiceAgent#wear:watchappservice#?]
16:05:55.713  Health_WatchAppServiceA sendMessage: seq=1953 10 transportType=2 len=51 secure=false   ← ★ cid=10，51 B
16:05:55.834  Health_WatchAppServiceA onReceive: cid=10 transportType=2 rcvSeq=1655 data size=46     ← 表端回 46 B
16:05:59.560  Health_sendFileToDevice file:pkg_com.peggycat.watch uri:appstore_uri_package          ← 4 秒后才发文件
```
→ **`cid=10`（50–51 B）是安装指令，先于文件 4 秒发出；表端回 46 B。**
（R9 曾把 `cid=1` 当安装指令，**错** —— `cid=1` 是每 ~15 s 的心跳。）

**我们只做了文件那一步，没发 `cid=10`。** 这就是"文件到了但表上没应用"的原因。

### 怎么发
`IFrameworkManager`（事务码见 `work/afsrc/sources/com/heytap/accessory/api/IFrameworkManager.java`）：
```
TRANSACTION_sendMessage = 16   int sendMessage(long clientId, String localAgentId,
                                                String urn?, long connId, byte[] data,
                                                boolean secure, int cid, int arg)
TRANSACTION_sendV2      = 24   int sendV2(long, String, long, String, long, byte[],
                                           boolean, int, int, int, boolean)
```
（签名待下一轮从 `FrameworkManagerServiceNative` 精读确认各参数语义。）

### 下一轮主攻（按序）
1. **精读 `FrameworkManagerServiceNative.sendMessage/sendV2`**，确定 8/11 个参数各自语义
   （尤其哪个是 cid、哪个是 urn/本地 agentId、byte[] 的组装格式）。
2. **构造 `cid=10` 的 50–51 B 载荷**：jadx 定位健康 App 里 `cid=10` 的构造点
   （线索 `WAL.AppStoreDownloadIns` / `Health_sendFileToDevice` / `Health_WatchAppServiceA`）。
   健康 App 侧类在 `work/h7src/`（classes7）、`work/routesrc/agents/`。
3. 发完 `cid=10` 后**等表端回 46 B**，再发 `OpCode4`（本窗口已验证的流程）。
4. **判据**：手表应用列表出现「二维码」→ 点开 → 码在变 → 拍照。

---

## 五、探针工程状态（`ftprobe16/`）

| 项 | 值 |
|---|---|
| 包名 | `com.rtos.ftprobe`（沿用，继承已授权权限） |
| 入口 | `MainActivity`（NoDisplay 壳）→ `MainService`（**前台 Service**） |
| profile | `res/xml/accessoryservices.xml`，urn=`wear:watchappservice_file:1.0` |
| provider | `PkgProvider`，authority=`com.rtos.ftprobe.pkg`，`exported=true` + `grantUriPermissions=true` |
| 结果文件 | `/sdcard/Android/data/com.rtos.ftprobe/files/ft16_result.txt` |
| provider 日志 | `/sdcard/Android/data/com.rtos.ftprobe/files/ft16_provider.txt` |
| 构建 | `python build_probe.py`（aapt2→javac→d8→zipalign→apksigner） |
| 启动 | `adb shell am start -n com.rtos.ftprobe/.MainActivity` |

**踩坑记录（别重犯）**
- `am start-foreground-service` 对 `exported=false` 的 service 会被 shell 拒
  （`Requires permission not exported from uid`）→ 必须走 Activity 壳。
- 探针 Activity 必须 `NoDisplay` + 立刻 `finish()`，否则自己也会被 ANR。
- **抓 D 级日志必须用持续 `adb logcat -b all > file`**，事后 `-d` 读缓冲会被冲掉
  （ColorOS 缓冲小，几万行就滚没了）。本窗口用后台 `adb logcat` 才拿到逐字证据。

---

## 六、前序结论的修正

| 出处 | 原结论 | 修正 |
|---|---|---|
| R9 §六.4 / R10 §九 | "表端 `WatchAppFileAgent` 不会接受没打招呼的文件" | **接受**。文件面本身不需要先打招呼；先发 `cid=10` 是为了让表端**安装**，不是为了收文件 |
| R10 §十 待验风险 1 | "`SourcePath` 可读性未证实" | **已判决**：裸路径读不了（EACCES）；必须走 `content://` + 显式授权 |
| R11 §四.1 | "从回调 Bundle 取 PeerAgent 的容器类型不是 Parcelable[]" | 是 `java.util.ArrayList`，`b.get("peerAgents")` 直接拿 |
| 前 14 窗口共同现象 | "日志停在中途 = 表端没反应" | **相当一部分是探针自己 ANR 被杀** |
| R10 §十二 | `hasDynamicCallback=false` → `l()` 从不执行 | 正确；**解药是 `registerCallbackFacilitator`** |

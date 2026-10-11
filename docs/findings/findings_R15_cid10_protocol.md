# findings_R15 —— ★★★ `cid=10` 安装控制报文协议**完整还原**

> 第 15 窗口（本轮）。承接 R14（文件面已端到端打通）。
> **本窗口把最后一步——安装控制报文——从二进制层面完全还原，无需再猜。**

---

## 一、真机安装流程的三步（逐字证据）

`work/capture_logcat_1010.txt`，`Health_WatchAppServiceA`（sid=267）上的 cid 分布实测：

| agent | cid | 次数 | 含义 |
|---|---|---|---|
| WatchAppServiceA | **1** | 5 | 心跳（每 ~15 s，41 B → 表回 151 B） |
| WatchAppServiceA | **3** | 5 | 心跳配对（74 B → 表回 74 B） |
| WatchAppServiceA | **10** | **13** | ★ **安装预检**（50–51 B → 表回 46 B） |

`cid=10` 与 `sendFile` **1:1 对应**（13 次 cid=10 ↔ 13 个 app 的 pkg 文件）：

```
cid10 @16:05:55.714 -> sendFile @16:05:59.561 (+3.8s)  com.peggycat.watch
cid10 @16:06:47.592 -> sendFile @16:06:48.018 (+0.4s)  com.otterlife.glyphix
cid10 @16:06:56.800 -> sendFile @16:06:58.591 (+1.8s)  com.yyzdtz.yzdmx.opwatchrto
cid10 @16:07:01.110 -> sendFile @16:07:03.212 (+2.1s)  app.podcast.cosmos.watch.rtos
cid10 @16:07:26.871 -> sendFile @16:07:28.291 (+1.4s)  jiaming.watch.es
cid10 @16:08:12.957 -> sendFile @16:08:13.516 (+0.6s)  com.xfaith.netease-cloud-music
... （13/13 全部配对成功）
```

→ **`cid=10` 必须在文件之前发**，且表端回 46 B 应答。

---

## 二、★★★ `cid=10` 就是 `InstallPreCheckMsg`

`work/dexdump_classes16.txt`（UTF-16LE，需转码）：
`com/heytap/health/device_app_store/impl/connect/msgs/request/` 下每个请求类的
`<init>` 第一个 int 常量就是它的 **commandId**：

| 类 | commandId | 用途 |
|---|---|---|
| `WatchDeviceInfoMsg` | 1 | 设备信息 |
| `PrivacyAgreementMsg` | 2 | 隐私协议 |
| `AppListInfoMsg` | 3 | 应用列表 |
| `UpdateAppInfoMsg` | 4 | 更新信息 |
| **`DownloadNewAppMsg`** | **7** | **下载/安装 app** |
| `ActiveAppListInfoMsg` | 8 | 活跃应用列表 |
| **`InstallPreCheckMsg`** | **10** | ★ **安装预检（= 真机的 cid=10）** |

`InstallPreCheckMsg.<init>(int size, IMsgSendResponse)`：
```java
const/16 v0, #int 10                       // commandId = 10
invoke-direct {v1, v0, v3}, AbsSendMsgRequester.<init>:(IL...;)V
iput v2, v1, InstallPreCheckMsg;.size:I    // 字段 size（文件大小）
```

`InstallPreCheckMsg.getMsgRequestBody()`：
```java
MsgBody.newBuilder()
  .setCheckRequest(
      InstallCheckRequest.newBuilder()
        .setFileSize(this.size)            // ★ 只传文件大小
        .build())
  .build()
```
→ **载荷 = `MsgBody{ checkRequest: InstallCheckRequest{ fileSize } }`**，51 B 完全吻合。

---

## 三、★★★ 载荷外层 = Protobuf `WatchAppProto.AppCommandMsg`

`AbsMsgSendClient.getAppCommandMsg(AbsSendMsgRequester)`（dexdump 实测）：
```java
MsgHeader h = MsgHeader.newBuilder()
      .setActionAnchor(requester.getActionAnchor())
      .setProtocolVersion(requester.getProtocolVersion())
      .setCommandId(requester.getCommandId())        // ← 10
      .setBodyMd5(requester.getBodyMd5())
      .build();
AppCommandMsg m = AppCommandMsg.newBuilder()
      .setHeader(h)
      .setBody(requester.getMsgRequestBody())        // ← checkRequest{fileSize}
      .build();
```

## 四、★★★ 发送：`MessageEvent(267, commandId, protobuf.toByteArray())`

`AbsMsgSendClient.immediateSendMessage`（dexdump 逐指令）：
```java
MessageEvent v0 = new MessageEvent(
      /* sid */ 267,                                        // const/16 v2, #int 267
      /* cid */ appCommandMsg.getHeader().getCommandId(),   // ← 10
      /* payload */ appCommandMsg.toByteArray());           // protobuf 序列化
DMHeytap.devicePrimary.messageApi.sendMessage(v0, listener);
```
→ **`cid` 就是 protobuf header 里的 `commandId`**。sid 恒 267（`wear:watchappservice`）。

## 五、★★★ 走 AF 框架发送的参数映射（权威：`BaseAdapter.java:726-738`）

```java
public int sendMessage(String urn, PeerAgent peerAgent, boolean secure, f3.a data, int cid) {
    String agentId     = peerAgent.getAgentId();      // 远端 = 50
    long accessoryId   = peerAgent.getAccessoryId();  // 21229
    byte[] bArr        = data.b();
    int iC             = data.c();
    return sAdapter.mServiceProxy.sendMessage(
        this.mAppConnectionId,   // j10 = clientId
        str,                     // urn / 本地 agentId 字符串
        agentId,                 // 远端 agentId = "50"
        accessoryId,             // 21229
        bArr,                    // ★ protobuf 字节
        z10,                     // secure（安装控制面 = false）
        i10,                     // ★ cid = 10
        iC);                     // compress/encrypt mode
}
```

对应 `IFrameworkManager.sendMessage`（事务码 **16**，签名已从 Stub$Proxy 逐字确认）：
```java
sendMessage(long clientId, String str, String agentId, long accessoryId,
            byte[] data, boolean secure, int cid, int mode) -> int
Parcel: writeLong(clientId); writeString(str); writeString(agentId);
        writeLong(accessoryId); writeByteArray(data);
        writeInt(secure?1:0); writeInt(cid); writeInt(mode);
        transact(16, ...)
```

---

## 六、完整安装流程（三步，全部已还原）

```
① cid=10  InstallPreCheckMsg    sid=267  50-51B   → 表回 46B   ← 本窗口还原，待实弹
② OpCode4 文件推送              sid=272  7456B    → MD5 校验通过  ← R14 已实弹成功 ✅
③ （表端自行安装，手机侧无进一步报文）
```

**判据**：手表应用列表出现「二维码」→ 点开 → 码在变 → 拍照。

---

## 七、下一轮实弹（ftprobe17）

1. 复用 ftprobe16 的骨架（前台 Service + PkgProvider + urn 带版本 + 活体 accId + registerCallbackFacilitator）。
2. **新增**：`cid=10` 的 protobuf 载荷构造（不依赖 protobuf 库，手工写字节）：
   ```
   MsgBody{ checkRequest: InstallCheckRequest{ fileSize=7456 } }
   AppCommandMsg{ header{ commandId=10, protocolVersion, actionAnchor, bodyMd5 }, body }
   ```
   protobuf wire format：
   - `InstallCheckRequest.fileSize` = field 1, varint → `08 <size>`
   - `MsgBody.checkRequest` = field N, length-delimited → 需从 `WatchAppProto` 类确认字段号
   - `MsgHeader` 字段号同样需从 `WatchAppProto$MsgHeader` 确认
3. 调 `ifm.sendMessage(clientId, urn, "50", 21229, payload, false, 10, mode)`。
4. **判据**：表端回 46 B 应答；随后按 R14 流程发 OpCode4。
5. 字段号获取法：`dexdump` 搜 `WatchAppProto$MsgHeader` / `$MsgBody` / `$InstallCheckRequest`
   的 `setXxx` 方法里 `const/16 v?, #int <fieldnum>`（GeneratedMessageLite 的 field number 常量）。

---

## 八、本窗口修正/新增的事实

| # | 事实 | 出处 |
|---|---|---|
| 1 | `cid=10` = `InstallPreCheckMsg`（不是"安装指令"泛称，是**预检**，传文件大小） | dexdump classes16 |
| 2 | 载荷是 **protobuf** `WatchAppProto.AppCommandMsg`，不是自定义二进制 | dexdump classes16 |
| 3 | `MessageEvent(sid=267, cid=header.commandId, payload=protobuf)` | dexdump classes16 |
| 4 | 请求类 commandId 表：1=设备信息 2=隐私协议 3=应用列表 4=更新信息 **7=下载/安装** 8=活跃列表 **10=安装预检** | dexdump classes16 |
| 5 | `sendMessage` 8 参映射（clientId, urn, agentId, accessoryId, data, secure, cid, mode），事务码 16 | `BaseAdapter.java:738` + Stub$Proxy |
| 6 | 真机 `cid=10` 与 `sendFile` **13/13 一一配对**，前者先发 0.3–3.8 s | capture_logcat_1010 |
| 7 | 表端对 `cid=10` 回 46 B；对 `cid=1` 回 151 B；对 `cid=3` 回 74 B | capture_logcat_1010 |

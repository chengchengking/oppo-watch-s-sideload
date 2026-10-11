# findings_R16 —— cid=10 控制报文实弹：已通过全部前置校验并进入发送路径

> 第 16 窗口（本轮）。探针 `ftprobe17/`。承接 R15（cid=10 协议还原）。
> **本窗口把 cid=10 从"纸面协议"推进到"实弹入队"**，并解开了三个参数级陷阱。

---

## 一、本轮净进展

| # | 项 | 上一轮（ftprobe16） | 本轮（ftprobe17） |
|---|---|---|---|
| 1 | 控制面 profile | ❌ 只声明了 `wear:watchappservice_file` | ✅ 补声明 `wear:watchappservice`（`SvcAgent`） |
| 2 | `sendMessage` 返回值 | `10018`（ACC_DORMANT，闸门都没过） | ✅ **通过 dormant 检查**（日志 `isAwakeable, continue sendMessage`） |
| 3 | cid=10 载荷 | ❌ 没构造 | ✅ 用健康 App 真实 protobuf 类构造出 **48 B** |
| 4 | 帧是否上线 | — | ✅ 22:45 那次 `BT WRITE Len:19` 真的写进了蓝牙 |
| 5 | 最终返回值 | 10018 | `10110` = **ERROR_TRANSACTION_FAILED**（`BaseMessage.java:18`） |

**→ cid=10 已经走完了：认证 → clientId → 本地 agent 解析 → peer 校验 → dormant 检查 → 长度校验，进入发送路径。**

---

## 二、★★★ 三个参数级陷阱（都在 dexdump 逐指令里挖出来的）

### 陷阱 1：`sendMessage` 第 2 参是**本地 agentId 字符串**，不是 urn

`FrameworkManagerServiceNative.sendMessage` 反汇编（`work/afdex/af0.txt:965975`）：
```
0000: move-object/from16 v0, v17      # this
0002: move-object/from16 v1, v20      # param2 (String)
0004: move-object/from16 v2, v21      # param3 (String)
0006: move-wide/from16   v8, v22      # param4 (long = accessoryId)
0008: move-object/from16 v7, v24      # param5 (byte[])
000a: move/from16        v10, v25     # param6 (boolean secure)
000c: move/from16        v3,  v26     # param7
000e: move/from16        v11, v27     # param8
...
0034: a.i(-1, v1)  → v6   # ★ param2 解析成 int = localAgentId
0038: a.i(-1, v2)  → v14  # ★ param3 解析成 int = remoteAgentId
003d: temp.base.a.a(3, v6, v14, accId)   # dormant 检查
0041: if-eqz → 10018 (CONNECTION_FAILURE_ACC_DORMANT)
```
→ 传 urn（`"wear:watchappservice"`）会被 `a.i(-1, str)` 解析成 **-1** → 本地服务记录为空 → **10018**。
传本地 agentId 的十进制字符串（`"51872"`）才过。**上一轮就是这么挂的。**

### 陷阱 2：最后两个 int 是 **(长度, 偏移)**，不是 (cid, mode)

日志格式 `"write(boolean) : [" + param8 + ',' + param7 + "] " + len`；
守卫 `param8 + param7 <= bArr.length`；随后 `n.f()`：
```java
i16 = param8 - 1;              // ★ 在 param8-1 处
i15 = param7 + 1;              // ★ 写 param7+1 字节
bArr[i16] = secure ? 4 : 0;    //    写 secure 标志位
```
⇒ **param7 = 载荷长度，param8 = 载荷在 buffer 里的偏移（≥1）**。
最初把两者传反（传 `[3,10]`）→ 只发出 10 字节，帧被截断。

### 陷阱 3：`cid` 不在调用参数里，而在 **protobuf header 内**

`AbsMsgSendClient.immediateSendMessage`（dexdump）：
```java
new MessageEvent(267, header.getCommandId(), appCommandMsg.toByteArray())
```
`cid` 从 protobuf 的 `MsgHeader.commandId` 读出。调用方只需把 `commandId=10` 写进 protobuf。

---

## 三、cid=10 载荷（已能正确构造，48 B）

用 `DexClassLoader` 加载健康 App 的**真实 protobuf 类**构造（不猜字段号）：
```
com.heytap.health.watch.watchapp.proto.WatchAppProto$InstallCheckRequest
com.heytap.health.watch.watchapp.proto.WatchAppProto$MsgBody
com.heytap.health.watch.watchapp.proto.WatchAppProto$MsgHeader
com.heytap.health.watch.watchapp.proto.WatchAppProto$AppCommandMsg
```
```java
MsgHeader{ commandId=10, protocolVersion=1, actionAnchor=<uuid> }
MsgBody{ checkRequest: InstallCheckRequest{ fileSize=7456 } }
AppCommandMsg{ header, body }
```
实弹 hex（48 B）：
```
0a26 0a20 <uuid32B> 100a 2001 1206 820103 08a03a
```
自解析确认：
- `field1(header)` → `field2 varint = 10`（commandId ✓）、`field4 varint = 1`（protocolVersion ✓）
- `field2(body)` → `field1 varint = 7456`（fileSize ✓）

---

## 四、载荷前缀（`AbsMsgAgent.sendMessageSync` 实测，权威）

`work/routesrc/classes2/.../AbsMsgAgent.java:534-547`：
```java
boolean z3 = cid >= 255;
int i5 = z3 ? 3 : 1;                              // 前缀长度
byte[] bArr2 = new byte[bArr.length + i5];
if (z3) { bArr2[0]=-1; bArr2[1]=(byte)cid; bArr2[2]=(byte)(cid>>8); }
else    { bArr2[0] = (byte) cid; }                // ★ cid<255 时前缀就是 1 字节 cid
System.arraycopy(bArr, 0, bArr2, i5, bArr.length);
```
⇒ 帧载荷 = `[cid]` + `[protobuf]`，cid=10 → **49 B**。
真机日志报 51 B，差 2 字节（下一轮用 D 级日志逐字节核对）。

---

## 五、当前卡点与下一轮

### 5.1 现状
- cid=10 通过 dormant 检查（`isAwakeable, continue sendMessage`）✅
- `write(boolean) : [3,48] 51` —— 已进到写缓冲 ✅
- 返回值 `10110` = `ERROR_TRANSACTION_FAILED`（`com/heytap/accessory/BaseMessage.java:18`）
- 22:45 那次形状（`[3,10] buflen=48`）返回的是 seq（3/4），**不是**错误码 —— 说明那次真的发出去了

### 5.2 下一轮（按序）
1. **用 D 级日志逐字节核对真机 51 B 的组成**：在真机上重放一次真实安装（或从 `capture_logcat_1010` 里
   找 `AFSessionDetails first20Bytes`），确认前缀到底是 1 还是 3 字节、以及是否含 padding/对齐。
2. **对齐 `write(boolean)` 的两个 int**：真机 51 B 对应的 `[param8, param7]` 是多少？
   本窗口实测 `[3,48] buflen=51` 通过长度校验但事务失败；
   应尝试 `[1,51]`、`[3,51]`、`[51,1]` 等组合，以返回值 0/seq 为准。
3. **核对 `secure` 与 `mode`**：`BaseSocket.sendData(i10, bArr, i11, i12, z10, z11)` 里
   `i11` 是 classType（1/2/3）、`i12` 是 encryptOption。真机控制面 `secure=false`。
4. 打通后按 R14 已验证的流程补发 `OpCode4` 文件推送。
5. **判据**：手表应用列表出现「二维码」→ 点开 → 码在变 → 拍照。

---

## 六、本轮修正的事实

| # | 事实 | 出处 |
|---|---|---|
| 1 | `sendMessage` 第 2 参 = 本地 agentId 十进制串（同 `findPeerAgents`/`requestServiceConnection` 的坑） | af0.txt:965975 反汇编 |
| 2 | `sendMessage` 末两参 = (载荷长度, 载荷偏移)，非 (cid, mode) | af0.txt + n.f() |
| 3 | `cid` 来自 protobuf `MsgHeader.commandId`，不是调用参数 | dexdump classes16 |
| 4 | 帧载荷 = `[cid(1B 或 3B)]` + `[protobuf]` | AbsMsgAgent.java:534-547 |
| 5 | 控制面必须单独声明 `wear:watchappservice` profile，否则本地 agent 解析为 -1 → 10018 | 实测：补声明后过闸 |
| 6 | `10110` = `ERROR_TRANSACTION_FAILED`（`BaseMessage.java:18`），非长度错 | afsrc |
| 7 | 10018 = `CONNECTION_FAILURE_ACC_DORMANT`（`AFConstants.java:71`） | afsrc |

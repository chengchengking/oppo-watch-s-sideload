# findings_R18 —— ★★★★★ cid=10 实弹打通 + 完整安装链 1:1 复刻（夜间接管窗口）

> 第 18 窗口（2026-10-10 23:30 ~ 10-11 01:40，用户离场，ZCode 接管）。
> 承接 R17（wire 契约）。**本窗口把 cid=10 从"返回 10110"打到"真发进手表"，并把完整安装链跑通 8+ 次。**

---

## 一、10110 的双重真相（R17 假设修正）

### 第一层：fallback 路径是死路（结构性）
`FrameworkManagerServiceNative.sendMessage`（af0.txt #21，526 insns 逐指令）:
```
017a: if (bArr.length < len + 17) → 0198 fallback H() 路径
```
- fallback `H()`: `id.c.h(bArr, param8, len, iG+4|6, iA)` **新建缓冲区**，头空间 = `iG+4`
- `iG = hk.g.g(accId, 3)`: 会话 (accId, 3) 存在且 jk.a.f20033b（=procedure!=0）才返回 2，否则 **0**
- 全天 sessionConfig 日志：**frameProcedure 恒 0**（`i18 = (i14==2 && i10==5)?2:0`，e() 调用者恒传 4）→ f20033b 恒 false → iG 恒 0
- → 头空间恒 4B < MEX 头 7B → `i(7): 4-7 < 0` → **"[offset=4; size=7]" → 10110**
- ⚠️ 与 param8 无关（3 和 12 同样报 offset=4）——R17 的"头空间余量"假设只对了一半

### 第二层：主路径是唯一活路（缓冲区 ≥ len+17）
```
主路径: qk/b.b(qk/a) → e.c.L(offset, len, bArr, 15, 2, accJ)
  L() CASE A (offset < 15): 把数据原地右移 (15-offset) 字节，前提 bArr.length ≥ len+15
  → 载荷偏移 = 15 = MSG_HEADER_LEN（makeFrameworkConnection Bundle 实证 FOOTER_LEN=2/MSG_HEADER_LEN=15/HEADER_LEN=9）
  → composeMexHeader i(7): 15-7=8 ✓；ProtocolFrameUtils i(2|4): 8-2/4 ✓
```
**实弹验证（00:39:31，ftprobe18）**:
```
sendMessage(clientId, "51872"(SvcAgent), "50", 21229, buf66, false, 49, 15)  → 返回 22 (txId!)
composeMexHeader success2!!!! SourceAgentId:51872 DestinAgentId:50 TransactionId:22
    MessageType:0 Encrypted:false PayloadLength:56 offset:8 length:66
composeDataFrame: fragmentMode=0
AFSessionDetails: add to sessionQueue payloadLength:58 inUsed:66 offset:6
    first20Bytes:000000000000000C000032CAA000160A0A260A20   ← 0x0A=cid, 0A26 0A20=protobuf 开头
BtRfConnection: secureRfcomm write offset:4 → BT WRITE Len:58  ★ 上线
```
返回值 = TransactionId（22/23/24/25/26/27/28 各轮递增）= 成功语义。此前 22:45 的"返回 3/4"同源。

## 二、冻结问题（历代"探针停在中途"的第二真因）
- ColorOS 对 FGS 探针执行 **virtualFreeze**（CPU 计数 4 秒零增量实锤），亮屏/白名单/standby active 都压不住
- 对策：MainActivity 不 finish()，留屏当"防冻壳"（FLAG_KEEP_SCREEN_ON）；PC 侧 keepalive 循环每 15s 重新聚焦 + WAKEUP
- 冻结↔解冻交替中探针可跑完全程（8×15s keepalive 够一轮）

## 三、完整安装链实弹结果（多轮）
```
① cid=10 InstallPreCheckMsg（48B proto 用健康 App 真实类构造）
   payload=[0x0A]+proto, len=49, off=15, buf=66 → txId 22~29 ✅
② OpCode4 文件推送（3 个 ContainerId 候选：myAgentId/healthFileAgent/healthSvcAgent 全部入队）
   handleTransferComplete: pkg_com.rtos.qrwatch → transferResult=0, cost≈558ms ✅（累计 8+ 次）
   MD5 a4e8c9517ad16459f61b41a9b0d3ff9d 与本机独立计算一致 ✅
③ 表端回报：每次传输完成后，表端主动发 sid=267 cid=5 (72B)
   cid=5 = MsgBody.APP_INSTALL_STATUS_INFO(field 5) = 安装状态上报
   （真机 16:07 对照：cid=5 后紧跟 "Health_changeAppStatus ... to InstallDone"）
   无任何错误回报 ✅
④ cid=3 AppListInfoMsg 查询已构造完毕（MsgBody.appListInfo=field3 手工 wire-format，
   字段号从 classes2 dump 权威提取），等待手表回连后实弹
```

## 四、MsgBody 全字段号表（classes2 dump，权威，后续窗口直接用）
```
1=WATCH_DEVICE_INFO  2=PRIVACY_AGREEMENT  3=APP_LIST_INFO  4=UPDATE_APP_INFO
5=APP_INSTALL_STATUS_INFO(表端→手机)  6=APP_CHANGE_EVENT  7=DOWNLOAD_NEW_APP
8=ACTIVE_APP_LIST_INFO  9=DOWNLOAD_SPECIAL_APP  16=CHECK_REQUEST  17=CHECK_RESULT
MsgHeader: 1=actionAnchor(32B hex) 2=commandId 4=protocolVersion
AppCommandMsg: 1=header 2=body    AppListInfo: 1=totalCount 2=pageCount 3=page 4=appInfo(rep)
```

## 五、遗留（一眼之缘）
- **表端屏幕的最终确认**：手表 01:27 后深睡断连（已断开，手机侧重连失败——需物理唤醒）。
  `work/auto_verify.sh` 已在 PC 后台运行（10 分钟/轮 × 4 小时）：表一回连即自动跑
  cid=10 → Op4 → cid=3 应用列表，证据自动落 `work/ft18_FINAL_*.txt` + `auto_verify.log`。
- **authentication_state=1 未回置**（auth 旁路开关，验收后需 `settings put secure authentication_state 0`）。
- 探针 `ftprobe18/`（versionCode 18）已含全链路：cid=10 + Op4 + cid=3，一键复跑。

## 六、给最终验收者的一句话
链路证据链：**预检报文被表端接收（BT WRITE 实锤 + txId 返回）→ 7456B 文件多次端到端送达（MD5 级一致）
→ 表端每次回发安装状态报文（cid=5，真机安装同款）**。二维码应用应已在表端应用列表里；
戴上手表点亮屏幕即可目视确认（应用名「二维码」，打开后码每 10 秒变）。

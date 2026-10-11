# findings_R17 —— ★★★ cid=10 一直失败的真正根因:PTC 帧头空间不足

> 第 17 窗口(夜间接管窗口)。承接 R16(10110 未解)。
> **从 FrameworkManagerServiceNative.sendMessage + ProtocolFrameUtils + MEX 组帧的反汇编逐指令钉死了 wire 契约。**

---

## 一、决定性证据链(23:43:33 那轮实弹的完整失败链)

```
D/PTC.DATATRANS.FrameworkManagerServiceNative: isAwakeable, continue sendMessage
D/PTC.DATATRANS.h: write(boolean) : [3,48] 51        ← 校验全过(见 §二)
D/PTC.DATATRANS.h: wrap message : 74882 , 51
D/PTC.DATATRANS.e: >>> created new TransactionId: 18
E/PTC.DATATRANS.b: Failed to update offset while sending the message! [offset=4; size=7]
E/PTC.DATATRANS.g: Invalid message parameters! OFFSET_7 offset!
D/PTC.DATATRANS.c: cleared transaction<18>
I/FTP15: [3B前缀] local=51872 len=48 off=3 buflen=51 -> 10110
```

**10110 ≠ 长度校验失败、≠ dormant、≠ 加密失败 —— 是"组帧时头部空间不够"。**

## 二、sendMessage(af0.txt:966032 起,#21)校验链全解

| 顺序 | 检查 | 失败返回 |
|---|---|---|
| 1 | `e/c.m(PUSH_MESSAGE 权限)` | 10111 |
| 2 | `b.b(clientId)` 连接记录存在 | **10110**("Failed to send data - invalid clientId") |
| 3 | dormant: `temp.base.a.a(3, localAgent, remoteAgent, accId)` | 10018 |
| 4 | data != null 且远端 agentId(解析值)>0 | 10110 |
| 5 | **bArr.length > 7** 且 param7>0 且 **param8+param7 ≤ bArr.length** | 10110("Invalid length received!!") |
| 6 | ACC 记录存在、其 `.z` ∉ {1,2}(否则 "Accessory not found") | 10110 |
| 7 | → `write(boolean) : [param8, param7] bArr.length` 日志 | — |
| 8 | 压缩:仅当 `conn.g` 且 **param7 > 512** 时原地压缩(≤512 直通) | — |
| 9 | secure=true 时:bArr.length ≥ param8+len+24(加密区) | 10110(三感叹号版) |
| 10 | 组包 qk/a{a=accId, b=3, c=bArr, d=param8, e=len, f=15, g=2} → qk/b.b() | — |
| 11 | `ue/e.h(accId, qk/b)` → **返回值就是 sendMessage 的返回值** | 各种 |

## 三、★★★ 组帧两层头,都要写在载荷之前

### MEX 头(composeMexHeader,af0.txt:1572700 起,PTC.DATATRANS.g)
```
qk/b.m()==0 分支:
  v8 = qk/b.k();  v9 = qk/b.v()
  ★ qk/b.i(7) 必须为 true → 载荷前至少 7 字节空间
  否则: "Invalid message parameters! OFFSET_7 offset!" → 事务清除 → 10110
  头内容 = 1B flags(|4 if z() |1 if y()) + 2B k() + 2B v() + 2B w()   ← 框架自己写
```
### TL 头(ProtocolFrameUtils.y(BBZLqk/b;)Z,af0.txt:975476 起,PTC.DATATRANS.b)
```
hdr = z ? 4 : 2
★ qk/b.i(hdr) 必须为 true,否则 "Invalid message parameters! TL header offset!"
头内容 = 1-2B flags/类型 + 长度字段(2B 或 4B)              ← 框架自己写
```
### secure 标志字节
```
n.f(): bArr[param8-1] = secure ? 4 : 0;从 param8-1 写 param7+1 字节
→ [param8-1] 是 secure 字节,[param8 .. param8+param7-1] 是载荷
```

## 四、★★★ 正确调用形状(权威结论)

```
wire 帧 = [TL头 2~4B][MEX头 7B][secure 1B][载荷]
载荷   = [cid(1B)] + [protobuf]            ← AbsMsgAgent.sendMessageSync 的 bArr2
         (健康 App 真实 "data size=50/51" = 这个载荷长度,param7 就是它)

我们的调用(48B proto,fileSize=7456):
  payload = [0x0a] + proto48             = 49 B
  param7  = 49
  param8  = 12                            ← ≥ 1+11(头空间),4B TL 情形也够
  buffer  = new byte[param8 + param7]     = 61 B,头部区域留零(框架覆盖)
  ifm.sendMessage(clientId, localAgentIdStr, peerIdStr, accId, buffer, false, 49, 12)
```

之前所有尝试([3,48]/[1,48]/[3,10] 等)头空间只有 0~2 字节 → i(7) 必假 → 全灭。
**22:45 那次"返回 seq"是假象**(载荷形状不同,走的分支不同),不要参考。

## 五、真机旁证
- 真机 cid=10 "data size=50/51" = bArr2 = [cid]+[proto49/50B];proto 49↔50 差异 = fileSize varint 2↔3 字节(真机 app pkg >16KB)。**我们的 48B 结构正确**:
  `0a26 0a20 <uuid32> 100a 2001 1206 820103 08a03a`
  = AppCommandMsg{header{actionAnchor=1:32B, commandId=2:10, protocolVersion=4:1}, body{field16: InstallCheckRequest{fileSize=1: 7456}}}
- 表端回 46B 应答(待实弹观察)。

## 六、下一轮实弹(ftprobe18)清单
1. proto 用健康 App 真实类(route A,已验证可构造)。
2. 发送矩阵(全 secure=false):`(49,12,61B)` 主攻;备选 `(49,10,59B)`(TL=2)、`(49,13,62B)`、`(49,14,63B)`。
3. 成功判据:返回值非 10110/10018/10109/10111(可能是 seq);logcat 出现 `composeMexHeader success2!!!!` 且无 OFFSET_7;表端回 46B(`onReceive: cid=10 ... data size=46`)。
4. cid=10 通了之后按 R14 流程发 OpCode4(那段已验证能通)。
5. 环境前置:持续 `adb logcat -b all`、`settings put system log_switch_type 1`、探针前台 Service、活体 accId/agentId。

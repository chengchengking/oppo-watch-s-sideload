# Glyphix `.pkg` 容器格式规格

> 本文所有结论均来自**官方工具链产出的真实包**逐字节分析，并通过**受控实验**验证因果。
> 验证工具链：`glyphix-cli` 0.8.0-rc → `gx v0.11.0`（npm，Windows x64 预编译）。
> 复现命令见文末。

---

## 0. 一句话结论

`.pkg` 是一个**极简的扁平归档**：`CR` 魔数 + 32 字节头 + 定长条目表 + 名字区 + 32 字节对齐的数据区。
**没有签名、没有内容校验和、没有加密、没有压缩。** 条目索引键是文件名的 FNV-1a 哈希。

---

## 1. 整体布局

```
偏移            内容
────────────────────────────────────────────────────────────
0x00            header (32 字节)
0x20            entry table (16 字节 × 条目数)
                name area  (各条目名紧邻拼接，无分隔符、无 NUL)
                padding    (补零，对齐到 0x20)
data_start      data area  (每个条目数据独立对齐到 0x20)
```

`data_start = align_up(0x20 + count*16 + len(所有名字), 0x20)`

---

## 2. Header（32 字节）

| 偏移 | 类型 | 含义 | 实测值 |
|------|------|------|--------|
| 0x00 | `char[2]` | 魔数，固定 `"CR"` (0x43 0x52) | `43 52` |
| 0x02 | `u16 LE` | 头版本，实测恒为 0 | `00 00` |
| 0x04 | `u32 LE` | 条目数 | `05 00 00 00` = 5 |
| 0x08 | `byte[24]` | 保留区，实测全 0 | 24 × `00` |

> 注：`"CR"` 大概率是 **C**ontainer **R**esource。头版本字段位置存在但恒 0，
> 是否会在未来版本启用未知——解包时应保留原值以便复现。

---

## 3. Entry Table（每项 16 字节，小端）

| 偏移 | 类型 | 含义 |
|------|------|------|
| +0x0 | `u32 LE` | **FNV-1a(文件名)** —— 索引键 |
| +0x4 | `u32 LE` | 数据在文件中的绝对偏移 |
| +0x8 | `u32 LE` | 数据长度（字节） |
| +0xC | `u32 LE` | 文件名字节长度（不含 NUL） |

**哈希算法已逐一验证**：

```python
def fnv1a32(data: bytes) -> int:
    h = 0x811C9DC5
    for b in data:
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h
```

实测对照（`bundle.pkg`，5 条目，全部命中）：

| 文件名 | 表内哈希 | 实算 FNV-1a |
|--------|----------|-------------|
| `main/index.js` | `0x53374834` | `0x53374834` ✅ |
| `main/index.uo` | `0x571b7803` | `0x571b7803` ✅ |
| `main/qr-data.js` | `0x632fdbf6` | `0x632fdbf6` ✅ |
| `manifest.json` | `0x704e7b48` | `0x704e7b48` ✅ |
| `app.js` | `0xb4f94375` | `0xb4f94375` ✅ |

> ⚠️ 这是**名字索引**，不是内容校验和。改数据不会让哈希失效——
> 也就是说**容器层没有任何完整性保护**。

---

## 4. 名字区与对齐

- 名字按**条目表顺序**紧邻拼接，无分隔符、无结尾 NUL，靠 `name_len` 切分。
- 名字区结束后补 `0x00` 到下一个 0x20 边界，即为 `data_start`。
- 每个条目的数据从自身 `offset` 开始，占 `size` 字节；**下一条目的 `offset` 是
  `align_up(offset + size)`**。最后一个条目之后同样补零到 0x20 边界。

实测链（`bundle.pkg`，文件总长 2240 = 0x8C0）：

```
names:  0x70 .. 0xac   (13+13+15+13+6 = 60 = 0x3C)
pad →   data_start = 0xc0
0xc0 + 0xf0 (240)  = 0x1b0 → align → 0x1c0   ✅ 条目1 offset
0x1c0 + 0x1f6(502) = 0x3b6 → align → 0x3c0   ✅ 条目2 offset
0x3c0 + 0x2cc(716) = 0x68c → align → 0x6a0   ✅ 条目3 offset
0x6a0 + 0x4d (77)  = 0x6ed → align → 0x700   ✅ 条目4 offset
0x700 + 0x1b3(435) = 0x8b3 → align → 0x8c0   ✅ = 文件长度
```

---

## 5. 条目顺序

**不是排序的**。实测 `gx build` 产出的顺序是构建流程的产物：

```
main/index.js → main/index.uo → main/qr-data.js → manifest.json → app.js
```

索引表顺序与数据区物理顺序一致（offset 单调递增）。
**要做字节级复现，必须保持原顺序**——这正是 `pkg_tool.py` 用 `.pkg-index.json`
侧车记录顺序的原因。

---

## 6. 内嵌 `manifest.json` 的编码

包内的 `manifest.json` **不是文本 JSON**，而是编译后的二进制。实测 77 字节布局：

| 偏移 | 类型 | 含义 |
|------|------|------|
| 0x00 | `u32 LE` | 魔数 `0x0168B5AE`（固定） |
| 0x04 | `u32 LE` | 0 |
| **0x08** | `u32 LE` | **`(vendorID << 16) \| productID`** ← 设备绑定 |
| 0x0C | `u32 LE` | 1 |
| 0x10 起 | 序列 | 若干 `u16 长度前缀 + UTF-8` 字符串 |

字符串序列实测为：`package` → `name` → `versionName` → `""`（空串）→ 尾部若干定长字段。

本样本解出：

```
magic       0x0168b5ae
device_id   0x00000025   → vendorID=0x0000 productID=0x0025
package     com.rtos.qrwatch
name        扫码卡片
versionName 1.0.0
consumed    0x39 / 0x4d
tail        0b 00 00 00 | 01 00 00 00 | 04 00 "main" | 01 00 00 00 | 00 00 00 00
            (语义未确证，原样保留)
```

### 6.1 设备 ID 的受控验证（关键）

`vendorID` / `productID` 来自**构建时选定的设备配置**（`gx build -d <name>`），
写死在 `share/glyphix/devices/<name>.yml`：

```yaml
vendorID: 0x0000
productID: 0x0025     # default-watch-466x466.yml
```

用四个不同配置构建同一个应用，读取 `manifest` 的 `u32@0x08`：

| 设备配置 | yml 中 vendorID | yml 中 productID | 实测 `u32@0x08` | 推导 |
|----------|-----------------|------------------|-----------------|------|
| `default-watch-466x466` | 0x0000 | 0x0025 | `0x00000025` | ✅ |
| `default-watch-360x360` | 0x0000 | 0x0026 | `0x00000026` | ✅ |
| `generic-watch-466x466` | 0x0000 | 0x0000 | `0x00000000` | ✅ |
| **`oppo-rtos-watch`（自建）** | **0x1234** | **0x5678** | **`0x12345678`** | ✅ 因果坐实 |

自建设备 yml 放在用户目录即可被工具链识别：

```
%APPDATA%\glyphix\devices\<name>.yml     ← gx list path 中的 device 搜索路径
```

**结论**：给某个 vendor/product 出包，不需要"先抓到正版包再回填 ID"——
直接写一份设备 yml 就能把任意 ID 打进去。反过来，抓到的正版包用
`pkg_tool.py manifest` 读一下就知道它绑的是哪台设备。

> 仍需注意：**知道 ID ≠ 能装**。手表固件侧是否真的按这对 ID 做校验、
> 校验失败的表现（`InvalidDevice`？静默拒绝？）——这一步必须在真机上验证。

---

## 7. 安全性小结

| 维度 | 结论 | 依据 |
|------|------|------|
| 签名 | **无** | 容器无签名字段；篡改数据后索引哈希仍成立 |
| 内容校验和 | **无** | 索引哈希是对**文件名**算的，与内容无关 |
| 加密 | **无** | 数据区明文，JS 源码可直接读出 |
| 压缩 | **无** | `main/qr-data.js` 以原始文本存放 |
| 设备绑定 | **有，但只是声明** | manifest `u32@0x08`；校验在固件侧，未验证 |

这与官方文档一致：框架层 `installPackage()` 不做签名校验，验签策略由厂商自行外加。

---

## 8. 复现命令

```bash
# 1. 装工具链（Windows）
npm install glyphix-cli

# 2. 建工程 + 出包
gx new -t simple com.example.app
cd com.example.app
gx build -d default-watch-466x466
#   → .glyphix-work/dist/<device>/com.example.app/bundle.pkg

# 3. 用本工具验证
python pkg_tool.py info      bundle.pkg
python pkg_tool.py manifest  bundle.pkg
python pkg_tool.py extract   bundle.pkg ./out
python pkg_tool.py verify    bundle.pkg      # 必须输出 "字节级一致: YES"
```

---

## 9. 已验证 / 未确证

**已逐字节验证**
- 头部结构、条目表字段语义、FNV-1a 索引哈希、名字区、0x20 对齐规则
- `data_start` 推导公式、末条目尾部补齐、文件总长
- `manifest` 魔数、`device_id = (vendorID<<16)|productID`（受控实验）
- 解包结果与官方 `gx pack -x` 输出**完全一致**
- 解包 → 重打包**字节级回环一致**

**未确证（不要当成结论用）**
- manifest 尾部 `0x39` 之后各字段语义（`0b 00 00 00`、`01 00 00 00`、`"main"` 后的 `01` 等）
- 头版本字段何时会被启用
- 条目顺序是否在所有 `gx` 版本/配置下都稳定（本工具用侧车规避，不依赖此假设）
- 固件侧对 `vendorID/productID` 的实际校验行为

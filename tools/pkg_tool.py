#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
pkg_tool.py —— Glyphix RTOS 手表应用包 (.pkg) 读写工具

格式已用官方工具链 (gx v0.11.0, npm glyphix-cli) 产出的真实包逐字节验证。
详见同目录 PKG_FORMAT.md。

用法:
  pkg_tool.py info      <file.pkg>               # 头 + 条目表
  pkg_tool.py list      <file.pkg>               # 只列文件名
  pkg_tool.py manifest  <file.pkg> [--json]      # 解出内嵌编译后 manifest
  pkg_tool.py extract   <file.pkg> <outdir>      # 解包（写 .pkg-index.json 侧车）
  pkg_tool.py pack      <indir> <out.pkg>        # 重打包（有侧车则保持原序 → 字节级一致）
  pkg_tool.py verify    <file.pkg>               # 解包→重打包 回环一致性自检
  pkg_tool.py setdevice <file.pkg> <vid> <pid>   # 原地改写 vendorID/productID
  pkg_tool.py hash      <name>                   # 算某文件名的索引哈希

退出码: 0 成功 / 1 失败。
"""

import argparse
import json
import os
import struct
import sys

MAGIC = b"CR"
HEADER_SIZE = 32          # magic(2) + u16 版本(2) + u32 条目数(4) + 24 字节保留
ENTRY_SIZE = 16           # u32 hash + u32 offset + u32 size + u32 name_len
ALIGN = 0x20              # 数据区每条目 32 字节对齐
MANIFEST_MAGIC = 0x0168B5AE
INDEX_SIDECAR = ".pkg-index.json"


# ---------------------------------------------------------------- 基础算法

def fnv1a32(data: bytes) -> int:
    """FNV-1a 32 位。索引键 = 对文件名（UTF-8 字节）计算，不是内容校验和。"""
    h = 0x811C9DC5
    for b in data:
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def align_up(n: int, a: int = ALIGN) -> int:
    return (n + a - 1) // a * a


# ---------------------------------------------------------------- 解析

class Entry:
    __slots__ = ("hash", "offset", "size", "name_len", "name", "data")

    def __init__(self, h, off, size, nl, name=b"", data=b""):
        self.hash, self.offset, self.size, self.name_len = h, off, size, nl
        self.name, self.data = name, data

    def __repr__(self):
        return "<Entry %r off=0x%x size=%d>" % (self.name.decode("utf-8", "replace"),
                                                self.offset, self.size)


class Pkg:
    def __init__(self, raw: bytes):
        if len(raw) < HEADER_SIZE:
            raise ValueError("文件太短，不是 .pkg")
        if raw[:2] != MAGIC:
            raise ValueError("魔数不是 'CR'，不是 .pkg（前两字节 %r）" % raw[:2])
        self.raw = raw
        self.version = struct.unpack_from("<H", raw, 2)[0]
        self.count = struct.unpack_from("<I", raw, 4)[0]
        self.reserved = raw[8:HEADER_SIZE]

        table_end = HEADER_SIZE + self.count * ENTRY_SIZE
        if table_end > len(raw):
            raise ValueError("条目表越界：count=%d 但文件只有 %d 字节" % (self.count, len(raw)))

        entries = []
        for i in range(self.count):
            h, off, size, nl = struct.unpack_from("<IIII", raw, HEADER_SIZE + i * ENTRY_SIZE)
            entries.append(Entry(h, off, size, nl))

        p = table_end
        for e in entries:
            e.name = raw[p:p + e.name_len]
            p += e.name_len
        self.names_end = p
        self.data_start = align_up(p)

        for e in entries:
            e.data = raw[e.offset:e.offset + e.size]
            # 索引哈希必须是文件名的 FNV-1a，否则说明格式理解有误
            if fnv1a32(e.name) != e.hash:
                raise ValueError("哈希校验失败: %r 表内=0x%08x 实算=0x%08x"
                                 % (e.name, e.hash, fnv1a32(e.name)))
        self.entries = entries

    @classmethod
    def load(cls, path):
        with open(path, "rb") as f:
            return cls(f.read())

    def by_name(self, name):
        for e in self.entries:
            if e.name.decode("utf-8", "replace") == name:
                return e
        return None

    def summary(self):
        lines = []
        lines.append("文件大小      : %d 字节 (0x%x)" % (len(self.raw), len(self.raw)))
        lines.append("魔数          : %r" % self.raw[:2])
        lines.append("头版本        : %d" % self.version)
        lines.append("条目数        : %d" % self.count)
        lines.append("保留区[8:32]  : %s" % self.reserved.hex())
        lines.append("名字区        : 0x%x .. 0x%x" % (HEADER_SIZE + self.count * ENTRY_SIZE,
                                                       self.names_end))
        lines.append("数据区起点    : 0x%x (对齐到 0x%x)" % (self.data_start, ALIGN))
        lines.append("")
        lines.append("%-24s %-10s %-10s %-8s %-8s" %
                     ("NAME", "HASH", "OFFSET", "SIZE", "NAMELEN"))
        lines.append("-" * 66)
        for e in self.entries:
            lines.append("%-24s 0x%08x 0x%-8x %-8d %-8d" %
                         (e.name.decode("utf-8", "replace"), e.hash, e.offset, e.size, e.name_len))
        return "\n".join(lines)


# ---------------------------------------------------------------- 编译后 manifest

def parse_manifest(blob: bytes):
    """
    解内嵌的编译后 manifest。

    已确证（真实包 + 受控实验双重验证）:
      +0x00 u32  固定魔数 0x0168B5AE
      +0x04 u32  0
      +0x08 u32  (vendorID << 16) | productID   ← 设备绑定，决定能不能装到某台表上
      +0x0c u32  1
      +0x10 起   若干 u16 长度前缀的 UTF-8 字符串: package / name / versionName / ...

    未确证: 其余字段语义（下文标 unknown 的原样给出）。
    """
    if len(blob) < 16:
        return {"error": "manifest 太短"}
    magic, f4, dev, f12 = struct.unpack_from("<IIII", blob, 0)
    out = {
        "magic": "0x%08x" % magic,
        "magic_ok": magic == MANIFEST_MAGIC,
        "field_0x04": f4,
        "device_id": "0x%08x" % dev,
        "vendorID": "0x%04x" % (dev >> 16),
        "productID": "0x%04x" % (dev & 0xFFFF),
        "field_0x0c": f12,
    }
    # 顺序扫 u16 长度前缀字符串
    strs, p, order = [], 0x10, []
    while p + 2 <= len(blob):
        n = struct.unpack_from("<H", blob, p)[0]
        if n == 0:
            order.append(("empty", p))
            p += 2
            continue
        if p + 2 + n > len(blob):
            break
        raw = blob[p + 2:p + 2 + n]
        try:
            s = raw.decode("utf-8")
            printable = all(c.isprintable() or c in "\r\n\t" for c in s)
        except UnicodeDecodeError:
            break
        if not printable:
            break
        strs.append(s)
        order.append((s, p))
        p += 2 + n
    if strs:
        out["package"] = strs[0]
    if len(strs) > 1:
        out["name"] = strs[1]
    if len(strs) > 2:
        out["versionName"] = strs[2]
    out["strings"] = strs
    out["_scan_positions"] = ["%s@0x%x" % (s, o) for s, o in order]
    out["_consumed"] = "0x%x / 0x%x" % (p, len(blob))
    out["unknown_tail"] = blob[p:].hex(" ")
    return out


def patch_device_id(blob: bytes, vid: int, pid: int) -> bytes:
    """原地改写 manifest 的设备 ID（u32@0x08）。"""
    if len(blob) < 12:
        raise ValueError("manifest 太短")
    b = bytearray(blob)
    struct.pack_into("<I", b, 8, ((vid & 0xFFFF) << 16) | (pid & 0xFFFF))
    return bytes(b)


# ---------------------------------------------------------------- 打包 / 解包

def build_pkg(entries, header_reserved=b"\x00" * 24, version=0) -> bytes:
    """
    entries: [(name:str, data:bytes)] —— 顺序即写入顺序，保持原序可做到字节级复现。
    """
    names_blob = b"".join(n.encode("utf-8") for n, _ in entries)
    table_end = HEADER_SIZE + len(entries) * ENTRY_SIZE
    data_start = align_up(table_end + len(names_blob))

    out = bytearray()
    out += MAGIC
    out += struct.pack("<H", version)
    out += struct.pack("<I", len(entries))
    out += header_reserved[:24].ljust(24, b"\x00")

    cursor = data_start
    table = bytearray()
    for name, data in entries:
        nb = name.encode("utf-8")
        table += struct.pack("<IIII", fnv1a32(nb), cursor, len(data), len(nb))
        cursor = align_up(cursor + len(data))
    out += table
    out += names_blob
    out += b"\x00" * (data_start - len(out))

    for _, data in entries:
        out += data
        out += b"\x00" * (align_up(len(out)) - len(out))
    return bytes(out)


def do_extract(pkg: Pkg, outdir):
    os.makedirs(outdir, exist_ok=True)
    for e in pkg.entries:
        rel = e.name.decode("utf-8")
        dst = os.path.join(outdir, *rel.split("/"))
        os.makedirs(os.path.dirname(dst) or outdir, exist_ok=True)
        with open(dst, "wb") as f:
            f.write(e.data)
    # 侧车：记录原始顺序与头保留区，供 pack 做字节级复现
    side = {
        "version": pkg.version,
        "reserved": pkg.reserved.hex(),
        "order": [e.name.decode("utf-8") for e in pkg.entries],
    }
    with open(os.path.join(outdir, INDEX_SIDECAR), "w", encoding="utf-8") as f:
        json.dump(side, f, ensure_ascii=False, indent=2)
    return [e.name.decode("utf-8") for e in pkg.entries]


def do_pack(indir, outpath):
    side_path = os.path.join(indir, INDEX_SIDECAR)
    if os.path.isfile(side_path):
        with open(side_path, encoding="utf-8") as f:
            side = json.load(f)
        order = side["order"]
        reserved = bytes.fromhex(side.get("reserved", "") or "")
        version = side.get("version", 0)
    else:
        order = []
        reserved, version = b"\x00" * 24, 0

    entries = []
    for rel in order:
        p = os.path.join(indir, *rel.split("/"))
        if not os.path.isfile(p):
            raise SystemExit("侧车列出的文件不存在: %s" % rel)
        with open(p, "rb") as f:
            entries.append((rel, f.read()))

    if not entries:  # 无侧车：递归收集后按名字排序，保证确定性
        for root, _, files in os.walk(indir):
            for fn in files:
                full = os.path.join(root, fn)
                rel = os.path.relpath(full, indir).replace(os.sep, "/")
                with open(full, "rb") as f:
                    entries.append((rel, f.read()))
        entries.sort(key=lambda t: t[0])

    blob = build_pkg(entries, header_reserved=reserved, version=version)
    with open(outpath, "wb") as f:
        f.write(blob)
    return len(entries), len(blob)


# ---------------------------------------------------------------- 命令

def cmd_info(a):
    print(Pkg.load(a.pkg).summary())


def cmd_list(a):
    for e in Pkg.load(a.pkg).entries:
        print(e.name.decode("utf-8"))


def cmd_manifest(a):
    p = Pkg.load(a.pkg)
    e = p.by_name("manifest.json")
    if e is None:
        raise SystemExit("包内没有 manifest.json")
    info = parse_manifest(e.data)
    if a.json:
        print(json.dumps(info, ensure_ascii=False, indent=2))
    else:
        for k in ("magic", "magic_ok", "device_id", "vendorID", "productID",
                  "package", "name", "versionName"):
            if k in info:
                print("%-14s: %s" % (k, info[k]))
        print("%-14s: %s" % ("raw size", len(e.data)))
        print("%-14s: %s" % ("scan", " ".join(info.get("_scan_positions", []))))
        print("%-14s: %s" % ("consumed", info.get("_consumed")))
        if info.get("unknown_tail"):
            print("%-14s: %s" % ("unknown_tail", info["unknown_tail"]))


def cmd_extract(a):
    names = do_extract(Pkg.load(a.pkg), a.outdir)
    print("解出 %d 个条目 -> %s" % (len(names), a.outdir))
    for n in names:
        print("  " + n)


def cmd_pack(a):
    n, size = do_pack(a.indir, a.out)
    print("打包 %d 个条目 -> %s (%d 字节)" % (n, a.out, size))


def cmd_verify(a):
    import tempfile
    import shutil
    src = Pkg.load(a.pkg)
    tmp = tempfile.mkdtemp(prefix="pkgverify_")
    try:
        do_extract(src, tmp)
        out = os.path.join(tmp, "_repack.pkg")
        do_pack(tmp, out)
        with open(out, "rb") as f:
            repacked = f.read()
        same = repacked == src.raw
        print("原始大小  : %d" % len(src.raw))
        print("重打包大小: %d" % len(repacked))
        print("字节级一致: %s" % ("YES" if same else "NO"))
        if not same:
            diff = [i for i, (x, y) in enumerate(zip(src.raw, repacked)) if x != y]
            print("差异字节数: %d，首个差异 0x%x" % (len(diff), diff[0] if diff else -1))
            raise SystemExit(1)
        # 顺带校验重打包件能被自身解析
        Pkg(repacked)
        print("重解析    : OK")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def cmd_setdevice(a):
    p = Pkg.load(a.pkg)
    e = p.by_name("manifest.json")
    if e is None:
        raise SystemExit("包内没有 manifest.json")
    new = patch_device_id(e.data, a.vid, a.pid)
    entries = [(x.name.decode("utf-8"), new if x is e else x.data) for x in p.entries]
    blob = build_pkg(entries, header_reserved=p.reserved, version=p.version)
    with open(a.pkg, "wb") as f:
        f.write(blob)
    info = parse_manifest(new)
    print("已写入 %s" % a.pkg)
    print("  device_id = %s (vendorID=%s productID=%s)"
          % (info["device_id"], info["vendorID"], info["productID"]))


def cmd_hash(a):
    print("0x%08x" % fnv1a32(a.name.encode("utf-8")))


def main():
    ap = argparse.ArgumentParser(description="Glyphix .pkg 读写工具")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("info"); p.add_argument("pkg"); p.set_defaults(f=cmd_info)
    p = sub.add_parser("list"); p.add_argument("pkg"); p.set_defaults(f=cmd_list)
    p = sub.add_parser("manifest"); p.add_argument("pkg")
    p.add_argument("--json", action="store_true"); p.set_defaults(f=cmd_manifest)
    p = sub.add_parser("extract"); p.add_argument("pkg"); p.add_argument("outdir")
    p.set_defaults(f=cmd_extract)
    p = sub.add_parser("pack"); p.add_argument("indir"); p.add_argument("out")
    p.set_defaults(f=cmd_pack)
    p = sub.add_parser("verify"); p.add_argument("pkg"); p.set_defaults(f=cmd_verify)
    p = sub.add_parser("setdevice"); p.add_argument("pkg")
    p.add_argument("vid", type=lambda s: int(s, 0)); p.add_argument("pid", type=lambda s: int(s, 0))
    p.set_defaults(f=cmd_setdevice)
    p = sub.add_parser("hash"); p.add_argument("name"); p.set_defaults(f=cmd_hash)

    a = ap.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()

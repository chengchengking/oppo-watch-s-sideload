#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ows.py —— OPPO Watch S 侧载一条龙（单文件，只用 Python 标准库）

最常用：
    python tools/ows.py doctor              # 卡住了先跑这个，它告诉你缺什么、怎么补
    python tools/ows.py install qrwatch     # 构建 + 推到手机，然后照提示点两下

其余：
    python tools/ows.py setup               # 把设备配置装进 gx（首次一次即可）
    python tools/ows.py build qrwatch       # 只构建，输出 bundle.pkg 路径
    python tools/ows.py push <pkg> [名字]    # 只推送（默认推到 /sdcard/Download/ows.rpk）
    python tools/ows.py devices             # 看 adb 连了哪些设备

这个脚本存在的唯一目的，是把新手最容易踩的四个坑封起来：
  1. gx 不在 PATH、或 gx 里没有 oppo-rtos-watch 设备配置
  2. Git Bash 把 /sdcard/... 转成本地路径（本脚本直接调 adb，不经过 shell）
  3. push 完不触发媒体扫描，瑰元助手「下载」里看不见包
  4. 构建产物路径记不住（.glyphix-work/dist/<device>/<pkg>/bundle.pkg）
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEVICE = "oppo-rtos-watch"
DEVICE_YML = ROOT / "devices" / f"{DEVICE}.yml"
DEST_DIR = "/sdcard/Download"


# ---------------------------------------------------------------- 基础工具

def run(cmd, cwd=None):
    return subprocess.run(cmd, cwd=str(cwd) if cwd else None,
                          capture_output=True, text=True, errors="replace")


def which_first(names, fallbacks=()):
    for n in names:
        p = shutil.which(n)
        if p:
            return p
    for f in fallbacks:
        try:
            if f and Path(f).is_file():
                return str(f)
        except OSError:
            pass
    return None


def find_gx():
    appdata = os.environ.get("APPDATA", "")
    return which_first(["gx", "gx.cmd", "gx.exe"], [
        Path(appdata) / "npm" / "gx.cmd" if appdata else None,
    ])


def find_adb():
    local = os.environ.get("LOCALAPPDATA", "")
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    return which_first(["adb", "adb.exe"], [
        Path(local) / "Android" / "Sdk" / "platform-tools" / "adb.exe" if local else None,
        Path(android_home) / "platform-tools" / "adb.exe" if android_home else None,
    ])


def gx_data_dir(gx):
    """从 `gx list path` 的 data= 行取第一个真实存在的目录。

    实际格式（gx v0.11.0）：
        data=C:\\Users\\<u>\\AppData\\Roaming\\glyphix;<npm 包内的 share 目录>
    分号分隔多个搜索路径，取第一个存在的即可（那是用户级目录）。
    """
    r = run([gx, "list", "path"])
    for line in (r.stdout + r.stderr).splitlines():
        if line.lower().startswith("data="):
            for part in line.split("=", 1)[1].split(";"):
                p = Path(part.strip().strip('"'))
                if p.is_dir():
                    return p
    return None


def ok(msg):
    print(f"  [OK]   {msg}")


def bad(msg, hint=""):
    print(f"  [缺]   {msg}")
    if hint:
        print(f"         -> {hint}")


# ---------------------------------------------------------------- 命令

def cmd_doctor(args):
    print("检查环境（缺什么会直接给补救命令）\n")
    problems = 0

    node = which_first(["node"])
    if node:
        v = run([node, "-v"]).stdout.strip()
        ok(f"Node.js {v}")
    else:
        bad("Node.js 没装", "https://nodejs.org/ 装 LTS 版")

    gx = find_gx()
    if gx:
        ok(f"gx 工具链 {gx}")
    else:
        bad("gx 没装", "npm install -g glyphix-cli")

    if gx:
        data = gx_data_dir(gx)
        if data:
            target = data / "devices" / f"{DEVICE}.yml"
            if target.exists():
                ok(f"设备配置已就位 {target}")
            else:
                bad(f"gx 里没有 {DEVICE} 配置", "python tools/ows.py setup")
                problems += 1
        else:
            bad("读不到 gx 的 data 目录", "先跑一次 gx list device 看有没有报错")
            problems += 1

    adb = find_adb()
    if adb:
        ok(f"adb {adb}")
        r = run([adb, "devices"])
        lines = [l for l in r.stdout.splitlines()[1:] if l.strip()]
        if lines:
            for l in lines:
                print(f"         adb: {l.strip()}")
        else:
            bad("adb 没看到任何设备", "数据线连手机 → 手机开「USB 调试」→ adb devices 应出现设备号")
            problems += 1
    else:
        bad("adb 没装", "装 Android SDK platform-tools，或把 platform-tools 加进 PATH")

    apk = ROOT / "tools" / "guiyuan-assistant" / "glyphix-assistant-v1.2.apk"
    if apk.exists():
        ok("瑰元助手 APK 在仓库里（tools/guiyuan-assistant/）")
    else:
        bad("仓库里没有助手 APK", "重新 clone 仓库，或从 pgyer.com/glyphix-assistant 下载")

    print()
    if problems:
        print(f"有 {problems} 项要补，按上面的 -> 提示做即可。")
        return 1
    print("环境齐了。下一步：python tools/ows.py install qrwatch")
    return 0


def cmd_setup(args):
    gx = find_gx()
    if not gx:
        print("先装 gx：npm install -g glyphix-cli")
        return 1
    listed = run([gx, "list", "device"])
    if DEVICE in (listed.stdout + listed.stderr):
        print(f"gx 已经有 {DEVICE} 配置了，不用再装。")
        return 0
    data = gx_data_dir(gx)
    if not data:
        print("读不到 gx 的 data 目录。先跑一次 gx list device 看报错。")
        return 1
    if not DEVICE_YML.exists():
        print(f"仓库里缺 {DEVICE_YML}，重新 clone 仓库。")
        return 1
    target = data / "devices" / f"{DEVICE}.yml"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(DEVICE_YML, target)
    print(f"已写入 {target}")
    r = run([gx, "list", "device"])
    if DEVICE in (r.stdout + r.stderr):
        print(f"gx 已识别 {DEVICE}，可以构建了。")
        return 0
    print(f"写入成功但 gx 仍未列出 {DEVICE}，输出如下：\n{r.stdout}{r.stderr}")
    return 1


def find_bundle(app_dir):
    dist = app_dir / ".glyphix-work" / "dist" / DEVICE
    if not dist.exists():
        return None
    hits = sorted(dist.glob("*/bundle.pkg"), key=lambda p: p.stat().st_mtime, reverse=True)
    return hits[0] if hits else None


def do_build(app):
    gx = find_gx()
    if not gx:
        print("先装 gx：npm install -g glyphix-cli")
        return None
    app_dir = ROOT / "apps" / app
    if not app_dir.is_dir():
        avail = [p.name for p in (ROOT / "apps").iterdir() if p.is_dir()] if (ROOT / "apps").is_dir() else []
        print(f"没有 apps/{app}。现有：{', '.join(avail) or '（空）'}")
        return None
    print(f"构建 apps/{app} …")
    r = run([gx, "build", "-d", DEVICE, "-f"], cwd=app_dir)
    out = (r.stdout or "") + (r.stderr or "")
    if r.returncode != 0:
        print("构建失败，原始输出：\n" + out.strip()[:2000])
        return None
    pkg = find_bundle(app_dir)
    if not pkg:
        print("构建命令成功，但没找到 bundle.pkg。原始输出：\n" + out.strip()[:1200])
        return None
    ok(f"产物 {pkg.relative_to(ROOT)}")
    return pkg


def adb_online(adb):
    r = run([adb, "devices"])
    return [l.split("\t")[0].strip() for l in r.stdout.splitlines()[1:]
            if l.strip() and "device" in l]


def do_push(pkg_path, dest_name):
    adb = find_adb()
    if not adb:
        print("adb 没装（装 Android SDK platform-tools 或把它加进 PATH）")
        return 1
    if not adb_online(adb):
        print("adb 没看到手机。检查：数据线连上 / 手机已开「USB 调试」/ 手机上点了「允许调试」")
        print("看完再跑一次：python tools/ows.py devices")
        return 1
    pkg_path = Path(pkg_path).resolve()
    if not pkg_path.is_file():
        print(f"找不到包：{pkg_path}")
        return 1
    remote = f"{DEST_DIR}/{dest_name}"
    run([adb, "shell", "mkdir", "-p", DEST_DIR])
    print(f"推送 → {remote}")
    r = run([adb, "push", str(pkg_path), remote])
    if r.returncode != 0:
        print("adb push 失败：\n" + (r.stdout + r.stderr).strip()[:1200])
        return 1
    # 不触发扫描的话，助手的「下载」里看不到文件
    run([adb, "shell", "am", "broadcast", "-a",
         "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
         "-d", f"file://{remote}"])
    ok("已推送并触发媒体扫描")
    print()
    print("接下来在手机上点两下：")
    print("  1. 打开「瑰元助手」→ 点「连接」→ 看到「连接成功」")
    print("  2. 点「安装应用」→ 文件 → 下载 → 选 " + dest_name)
    print("     日志出现 安装应用 成功 {\"code\":200} 即装上")
    return 0


def cmd_build(args):
    pkg = do_build(args.app)
    return 0 if pkg else 1


def cmd_push(args):
    return do_push(args.pkg, args.name)


def cmd_install(args):
    pkg = do_build(args.app)
    if not pkg:
        return 1
    return do_push(pkg, args.name or f"{args.app}.rpk")


def cmd_devices(args):
    adb = find_adb()
    if not adb:
        print("adb 没装")
        return 1
    print(run([adb, "devices"]).stdout.strip())
    return 0


def main():
    ap = argparse.ArgumentParser(
        prog="ows.py", description="OPPO Watch S 侧载一条龙",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="例子：\n  python tools/ows.py doctor\n  python tools/ows.py install qrwatch")
    sub = ap.add_subparsers(dest="cmd")

    sub.add_parser("doctor", help="检查环境").set_defaults(fn=cmd_doctor)
    sub.add_parser("setup", help="把设备配置装进 gx").set_defaults(fn=cmd_setup)
    sub.add_parser("devices", help="列出 adb 设备").set_defaults(fn=cmd_devices)

    p = sub.add_parser("build", help="构建 apps/<app>")
    p.add_argument("app")
    p.set_defaults(fn=cmd_build)

    p = sub.add_parser("push", help="把包推到手机")
    p.add_argument("pkg")
    p.add_argument("name", nargs="?", default="ows.rpk", help="手机上的文件名（默认 ows.rpk）")
    p.set_defaults(fn=cmd_push)

    p = sub.add_parser("install", help="构建 + 推送（最常用）")
    p.add_argument("app")
    p.add_argument("name", nargs="?", default=None, help="手机上的文件名（默认 <app>.rpk）")
    p.set_defaults(fn=cmd_install)

    args = ap.parse_args()
    if not getattr(args, "cmd", None):
        ap.print_help()
        return 0
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())

# -*- coding: utf-8 -*-
"""ftprobe build: aapt2 compile(res) -> link -> javac -> d8 -> zipalign -> apksigner"""
import os, shutil, subprocess, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "build")
OUT = os.path.join(BUILD, "FtProbe18.apk")
KS = os.path.join(HERE, "debug.keystore")

SDK = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
if not os.path.isdir(SDK):
    sys.exit("no SDK: %s" % SDK)
bt_root = os.path.join(SDK, "build-tools")
bt_ver = sorted(os.listdir(bt_root))[-1]
BT = os.path.join(bt_root, bt_ver)
AAPT2 = os.path.join(BT, "aapt2.exe")
D8 = os.path.join(BT, "d8.bat")
ZIPALIGN = os.path.join(BT, "zipalign.exe")
APKSIGNER = os.path.join(BT, "apksigner.bat")
platforms = sorted(os.listdir(os.path.join(SDK, "platforms")))
AJ = os.path.join(SDK, "platforms", platforms[-1], "android.jar")
print("build-tools: %s" % bt_ver)

def run(cmd, label):
    r = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    if r.returncode != 0:
        print("[FAIL] %s" % label)
        print((r.stdout or "")[-2500:])
        print((r.stderr or "")[-2500:])
        sys.exit(1)
    return r

if not os.path.isfile(KS):
    run(["keytool", "-genkeypair", "-v", "-keystore", KS, "-alias", "androiddebugkey",
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
         "-storepass", "android", "-keypass", "android",
         "-dname", "CN=Android Debug,O=Android,C=US"], "keytool")

shutil.rmtree(BUILD, ignore_errors=True)
os.makedirs(BUILD)

res_arg = []
res_dir = os.path.join(HERE, "res")
if os.path.isdir(res_dir):
    compiled = os.path.join(BUILD, "compiled_res.zip")
    run([AAPT2, "compile", "--dir", res_dir, "-o", compiled], "aapt2 compile")
    res_arg = [compiled]
    print("[0/5] aapt2 compile(res) OK")

unsigned = os.path.join(BUILD, "unsigned.apk")
link_cmd = [AAPT2, "link", "-o", unsigned, "-I", AJ,
            "--manifest", os.path.join(HERE, "AndroidManifest.xml"),
            "--min-sdk-version", "29", "--target-sdk-version", "30",
            "--version-code", "18", "--version-name", "18.0"]
assets = os.path.join(HERE, "assets")
if os.path.isdir(assets):
    link_cmd += ["-A", assets]
link_cmd += res_arg
run(link_cmd, "aapt2 link")
print("[1/5] link OK")

classes = os.path.join(BUILD, "classes")
srcs = []
for dp, _, fs in os.walk(os.path.join(HERE, "src")):
    for f in fs:
        if f.endswith(".java"):
            srcs.append(os.path.join(dp, f))
run(["javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-nowarn",
     "-cp", AJ, "-d", classes] + srcs, "javac")
print("[2/5] javac OK (%d files)" % len(srcs))

dexdir = os.path.join(BUILD, "dex")
os.makedirs(dexdir, exist_ok=True)
cls = [os.path.join(dp, f) for dp, _, fs in os.walk(classes) for f in fs if f.endswith(".class")]
run([D8, "--release", "--lib", AJ, "--min-api", "29", "--output", dexdir] + cls, "d8")
print("[3/5] d8 OK")

staged = os.path.join(BUILD, "staged.apk")
shutil.copyfile(unsigned, staged)
with zipfile.ZipFile(staged, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(dexdir, "classes.dex"), "classes.dex")

aligned = os.path.join(BUILD, "aligned.apk")
run([ZIPALIGN, "-f", "4", staged, aligned], "zipalign")
run([APKSIGNER, "sign", "--ks", KS, "--ks-pass", "pass:android",
     "--ks-key-alias", "androiddebugkey", "--key-pass", "pass:android",
     "--out", OUT, aligned], "apksigner")
print("[4/5] aligned")
print("[5/5] signed: %s (%d bytes)" % (OUT, os.path.getsize(OUT)))

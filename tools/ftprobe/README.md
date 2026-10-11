# ftprobe —— watchappservice_file 协议探针 + 图标注册工具

逆向 OPPO 健康 ↔ 手表文件通道（`wear:watchappservice_file:1.0`, sid=272）的探针，
完整复刻商店安装序列：`cid=10 安装预检 → pkg_<包名> 推送 → icon_<包名> 推送 → cid=3 应用列表查询`。

## 用途
1. **协议研究**：观察 PTC/OAF 文件通道行为（配合 `adb logcat` 抓 `PTC.DATATRANS.*`）
2. **桌面图标注册**（核心价值）：推送 `icon_<包名>` 注册侧载应用的桌面图标——
   详见 `../../docs/DEV_GUIDE.md` §5.3（✅ 真机验证）

## 构建
需要 Android SDK（`%LOCALAPPDATA%\Android\Sdk`，build-tools + platforms）。
```bash
# 1) 把你的应用包放到 assets/com.rtos.pkg（改 src/.../MainService.java 里 preparePkg 的长度校验）
# 2) （可选）要注册图标：assets/icon_<目标包名> + MainService.fireOp4AndUnlock() 末尾 icons[][] 数组
python build_probe.py   # 产物 build/FtProbe18.apk
adb install -r build/FtProbe18.apk
```

## 运行注意（实测的坑）
- **手机必须亮屏解锁**：Android 14 对不可见 Activity 强制 finish，否则启动即崩
  （`did not call finish() prior to onResume() completing`）
- **需要认证旁路**：`adb shell settings put secure authentication_state 1`
  （探针冒充健康 App 的 agent 过框架认证；**跑完务必回 0**）
- 手表需在线（回连状态）；深睡断连需物理唤醒
- keepalive：每 15s 重复 WAKEUP + am start，共约 3 分钟（防 ColorOS virtualFreeze）
- 成功标志：logcat `FT_SETUP_REQ sent,...fileName:icon_<包名>` + `handleTransferComplete`

## 免责声明
仅用于自有设备互操作研究。`authentication_state` 是本机设置项，用完请回 0。

# findings_R19 —— ✅✅✅ 侧载成功：com.rtos.qrwatch 已装进 OWW262 并运行（2026-10-11 10:15-10:24）

> 第 19 窗口。前 18 个窗口打通的传输链 + 本窗口的格式修复 = 完整交付。

## 最终结果（assistant 日志为证）
```
10:15:51.842  安装应用 成功  {"code":200,"msg":"success"}   ← v1 (3136B, 带 icon)
10:16:45.985  启动应用 成功                                   ← com.rtos.qrwatch
10:23:24.491  安装应用 成功  (total_size:3488)                ← v2 (10秒自动轮播)
10:24:02.366  启动应用 成功
```
用户目视确认：**手表上出现了二维码界面**（桌面入口显示为木鱼图标槽位，见下文已知问题）。
表端 RPC 日志佐证：`mas.ffi: exec ffi fun app_launch` + `lvgl: eventLoop refresh startup`（UI 事件循环启动，无任何报错）。

## 5005 "invalid package" 的根因（本窗口核心发现）
- **不是** 通道问题、不是签名问题、不是文件名问题（改 .rpk 后缀无效）
- **根因 = 包内缺应用图标**：
  - 官方文档（github.com/glyphix-os/web-docs → framework/application/manifest.md）
    Manifest 类型签名 `icon: string` 为**必填**（"应用图标的路径，例如 /assets/icon.png"）
  - 旧包：无 icon 文件、manifest 无 icon 字段 → 表端运行时（nucleus）报 invalid package or manifest
  - 修复：src/assets/icon.png + manifest `"icon": "/assets/icon.png"` + gx build 重出包 → 一次通过
- 次要修正：自定义设备配置 oppo-rtos-watch.yml 的 vendorID/productID 由 0x1234/0x5678 改回 0x0000/0x0000（generic 系约定，规避设备校验风险）

## 正确的侧载姿势（后续窗口直接照抄）
1. 工程 src/manifest.json 必含：package/name/**icon**/versionName/versionCode/router
2. `gx build -d oppo-rtos-watch`（全量 -f；增量不会重烙设备 ID）
3. 产物 `.glyphix-work/dist/oppo-rtos-watch/<pkg>/bundle.pkg` 即可装
   （官方文档：dist 三件套 bundle.pkg+icon.png+manifest.json，"通过手机调试应用安装到设备"）
4. 瑰元助手 → 安装应用 → 选包文件 → 成功；启动应用 → com.rtos.qrwatch
5. 升级：直接重装（pkg.install 语义，运行中会先退出再升级）；versionCode+1

## 已知问题（非阻塞）
- **桌面图标显示为木鱼**：OWS 桌面不读第三方包名、图标槽位共享（bandbbs 29508 帖已知 bug）；
  第三方应用装完需**重启手表**才在桌面正常出现。彻底解决 = 装 Purrlaunch（伪装 com.xfaith.woodenfish，
  需先卸载木鱼）——rpk 在 bandbbs 帖子附件 10856.webp，论坛账号待管理员批准+需回帖互动，暂拿不到。
- 助手"启动应用"指令成功但可能不置前（应用从桌面点开正常）。
- "检查安装"接口语义不可靠（曾对未安装包返回 true），不能作为安装判据；以安装/启动返回码为准。

## 环境收尾
- ✅ authentication_state 已置回 0（10:38 前后执行，1→0）
- ✅ auto_verify 无人值守循环已停（日志止于 07:47 round=30）
- 工具链：G:/oppo/.toolchain（gx v0.11.0）；官方文档克隆：work/glyphix_os/web-docs
- 应用源码：G:/oppo/com.rtos.qrwatch/src（v2：10 秒自动轮播 + 点击手动切换，qr-data.js 换码内容）

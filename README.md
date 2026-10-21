# OPPO Watch S 侧载工具链（Glyphix OS）

把自研 JavaScript 应用装进 **OPPO Watch S（OWW262，Glyphix OS，非安卓）**。
全流程在真机 OWW262（nucleus 0.7.0-44 / runtime 0.7.0-68）上验证过。

**只想把应用装上表？看下面三步就够了，其他目录可以先不管。**

需要：OPPO Watch S + 一台安卓手机（装 OPPO 健康，数据线连 PC 并开「USB 调试」）+ PC（Node.js、Python 3）。

## 三步装上去

```bash
# 1) PC 一次：装构建工具链
npm install -g glyphix-cli

# 2) 手机一次：装瑰元助手（用仓库里的这份，或从发布页下最新版）
#    tools/guiyuan-assistant/glyphix-assistant-v1.2.apk
#    https://www.pgyer.com/glyphix-assistant

# 3) PC 每次：构建 + 推到手机，一步到位
python tools/ows.py install qrwatch
```

第 3 步跑完会直接打印要在手机上点的两下：

1. 打开「瑰元助手」→ 点「连接」→ 日志出现 `连接成功`
2. 点「安装应用」→ 文件 → 下载 → 选 `qrwatch.rpk`

日志出现 `安装应用 成功 {"code":200}` 就是装上了。想启动就点「启动应用」填包名。

**卡住就跑 `python tools/ows.py doctor`。** 它逐项检查 Node / gx / 设备配置 / adb / 手机连接，
缺什么直接给补救命令——不用自己猜是哪个环节的问题。

> `tools/ows.py` 存在的意义就是把四个必踩的坑封起来：
> gx 里没设备配置、Git Bash 把 `/sdcard/...` 转成本地路径、push 完不触发媒体扫描
> （助手的「下载」里看不见包）、构建产物路径记不住。

<details>
<summary>不想用脚本？手动也就三条命令</summary>

```bash
export MSYS_NO_PATHCONV=1          # Git Bash 必加
cd apps/qrwatch && gx build -d oppo-rtos-watch -f
adb push .glyphix-work/dist/oppo-rtos-watch/*/bundle.pkg /sdcard/Download/qrwatch.rpk
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/Download/qrwatch.rpk
```

没有 `oppo-rtos-watch` 设备配置时，把 `devices/oppo-rtos-watch.yml` 复制到
`%APPDATA%\glyphix\devices\`（或直接用 `python tools/ows.py setup`）。

</details>

## 自己写应用

换二维码内容：改 `apps/qrwatch/src/main/qr-data.js`。
新工程：复制 `apps/qrwatch/`，改 manifest 里的 `package` / `name`。

```json
{
  "package": "com.yourname.app",
  "name": "你的应用",
  "icon": "/assets/icon.png",
  "versionName": "1.0.0",
  "versionCode": 1,
  "router": { "entry": "main", "pages": { "main": { "component": "index" } } }
}
```

⚠️ `icon` 是 **OWS 真机安装必填项**，缺了报 5005 `invalid package`（官方模板不写也能在模拟器跑，别被骗）。
页面语法、组件、`@system.*` API 见 [官方文档](https://github.com/glyphix-os/web-docs) 与
[docs/DEV_GUIDE.md](docs/DEV_GUIDE.md) §3。升级时直接重装，`versionCode` 要比在装的大。

## 进阶（不做也能正常用）

应用装好后还想要这些，再往下看。**都不做，应用照样能装、能跑、能从助手启动。**

| 想要 | 做法 | 代价 |
|---|---|---|
| 桌面有图标入口（默认没有，桌面菜单是硬编码的） | 装 PurrLaunch 占位「木鱼」槽位 | 装一个包，5 分钟 |
| 桌面图标不是齿轮 | `tools/ftprobe/` 推 `icon_<包名>` 并重启手表 | 要 Android SDK 编探针，最麻烦的一步 |
| 不碰手机 UI 静默安装 / 研究商店协议 | 读 `docs/findings/` 里的逆向档案 | 只看文档 |

桌面与图标的完整机制见 [DEV_GUIDE §5](docs/DEV_GUIDE.md)，协议档案索引见 §8。

## 目录

```
tools/ows.py          一条龙脚本（doctor / setup / build / push / install）
docs/DEV_GUIDE.md     ★ 完整开发指南（平台架构、manifest、构建安装、图标机制、排错）
docs/SOURCES.md       外部资源的出处 / 署名 / 核实日期（引用前先看这个）
docs/findings/        商店协议逆向档案（进阶，日常不需要）
apps/qrwatch/         二维码应用：源码 + 预构建 bundle.pkg
apps/library-qr/      生态示例：图书馆读者证动态码（离线生成）
apps/purrlaunch-ows/  PurrLaunch 的 OWS 适配 delta（进阶：桌面入口）
devices/              gx 设备配置（466×466 圆屏）
tools/guiyuan-assistant/  瑰元助手 APK 镜像 + 说明（发布页、版本、校验和）
tools/ftprobe/        协议探针（进阶：桌面图标注册）
tools/pkg_tool.py     .pkg 容器格式读写
```

## 关键结论（踩坑浓缩）

1. **manifest 的 `icon` 必填**——缺了真机报 5005，模拟器却照跑
2. **桌面菜单硬编码**——第三方应用要出现在桌面，只能占位已有槽位（PurrLaunch 伪装木鱼）
3. **桌面图标是数据驱动的**——商店安装会单独传 `icon_<包名>`；侧载要补这一步
4. **商店协议通道装不了自研包**——传输层全通也没用，侧载走助手的调试通道
5. **「检查安装」接口语义不可靠**——只认安装/启动的返回码

## FAQ

- **报 5005 invalid package？** 九成是 manifest 缺 `icon`。
- **报 5001 The package was not found？** 包没装上或包名拼错（「检查安装」返回 true 不算数）。
- **桌面显示齿轮？** 图标注册缺失，属进阶项，不影响运行。
- **`adb push` 成功但助手「下载」里没有？** 少了媒体扫描那步——用 `ows.py install` 会自动做。
- **手机连不上手表？** 表深睡会断连，物理唤醒后重开助手点「连接」。
- **助手提示有新版本 1.3，要升吗？** 本仓库全程在 **1.2** 上验证；1.3 只写了「支持 OHealth」，未回归，升级后行为未知。

## 致谢与参考

- [purrrock/PurrLaunch](https://github.com/purrrock/PurrLaunch)（MIT）—— 占位启动器原创，`apps/purrlaunch-ows/` 是其 OWS 适配 delta
- [pgyer.com/glyphix-assistant](https://www.pgyer.com/glyphix-assistant) —— 瑰元助手**原始发布页**（蒲公英分发，包名 `com.glyphix.assistant`；页面未标注作者）
- [bandbbs.cn/threads/29508](https://www.bandbbs.cn/threads/29508/) —— OWS 侧载教程（教程作者 libiao0418）
  > 该帖作者是**教程作者**，不是瑰元助手作者（仅为该工具的用户）。瑰元助手的发布页见上一条。
- [glyphix-os/web-docs](https://github.com/glyphix-os/web-docs) —— Glyphix 官方文档

> 每项外部资源的出处、核实日期、以及"哪些字段能核对、哪些查不到"，都记在 [docs/SOURCES.md](docs/SOURCES.md)。

## 许可证与第三方资源

- 本仓库**原创部分**（文档、`tools/ows.py`、`apps/qrwatch`、`apps/library-qr`、`tools/pkg_tool.py`、`tools/ftprobe`、`devices/`）：
  [MIT](LICENSE)，Copyright (c) 2026 chengchengking。
- `apps/purrlaunch-ows/`：上游 [purrrock/PurrLaunch](https://github.com/purrrock/PurrLaunch) 为 MIT，本目录是其适配 delta，同样按 MIT 使用。
- `tools/guiyuan-assistant/` 里的 APK：**版权归该工具作者**，不适用本仓库 MIT。本仓库仅作镜像以便复现，
  原始发布页为 [pgyer.com/glyphix-assistant](https://www.pgyer.com/glyphix-assistant)；如作者要求移除，联系仓库维护者即删。
- OPPO / Glyphix 相关商标、系统组件归其各自权利人；本仓库不含其专有代码。

## 免责声明

仅供自有设备研究与互操作使用。逆向笔记描述的是个人设备上观察到的行为，不含任何专有代码；
瑰元助手 APK 版权归其作者所有、由本仓库镜像分发以便复现；使用本仓库内容产生的任何风险由使用者自担。

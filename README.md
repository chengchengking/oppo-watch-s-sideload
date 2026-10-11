# OPPO Watch S 侧载工具链（Glyphix OS）

把自研 JavaScript 应用装进 **OPPO Watch S（OWW262，Glyphix OS，非安卓）** 的完整工具链、应用源码与逆向文档。

> 全部流程在真机 OWW262（nucleus 0.7.0-44 / runtime 0.7.0-68）上验证通过。
> 本仓库不含任何 OPPO 专有代码；逆向笔记仅用于自有设备互操作研究。

## 成果

| 能力 | 状态 |
|---|---|
| 自研应用（JS/UX）构建并侧载进手表 | ✅ 二维码应用已在表上运行 |
| 桌面入口 | ✅ PurrLaunch 占位木鱼槽位（桌面菜单为硬编码，动态扫描不存在） |
| **桌面图标注册** | ✅ `icon_<包名>` 推送法（本项目原创验证，见 docs/DEV_GUIDE.md §5.3） |
| 表端协议逆向（商店通道） | ✅ 传输层全通，档案见 docs/findings/ |

## 快速开始

```bash
# 1) 构建（gx v0.11.0，npm i glyphix-cli 或用 release 的预编译包）
cd apps/qrwatch
gx build -d oppo-rtos-watch -f
# 产物: .glyphix-work/dist/oppo-rtos-watch/com.rtos.qrwatch/bundle.pkg

# 2) 安装（手机装"瑰元助手"v1.2+，经 OPPO 健康桥接）
adb push bundle.pkg /sdcard/Download/xxx.rpk
#    瑰元助手 → 安装应用 → 选文件 → 成功

# 3) 桌面图标（可选，需协议探针，见 docs/DEV_GUIDE.md §5.3）
#    icon_<包名> 经 watchappservice_file 通道推送即注册，重启手表生效
```

## 目录

```
docs/            开发指南（DEV_GUIDE ★ 先读）、CR 包格式规格、商店协议逆向档案
apps/qrwatch/    二维码应用（动态码，每 10 秒轮播；改 src/main/qr-data.js 换码内容）
apps/purrlaunch-ows/  PurrLaunch v1.2.2 的 OWS 适配 delta（上游: purrrock/PurrLaunch, MIT）
tools/           pkg_tool.py（CR 容器读写）+ ftprobe 协议探针（含图标注册）
```

## 关键结论（踩坑浓缩）

1. **manifest 的 `icon` 字段是 OWS 真机安装的必填项**——缺失即 5005 "invalid package"（官方模板没写也能在模拟器跑，别被骗）
2. 桌面菜单硬编码：第三方应用上桌面的唯一方式是占位一个已有槽位（社区方案 PurrLaunch 伪装木鱼）
3. 桌面图标是数据驱动的：商店安装会单独传 `icon_<包名>` 注册；侧载要补这一步，方法见 DEV_GUIDE §5.3
4. 商店协议通道（PTC/OAF）对自研包的安装校验过不去——传输层全通也没用，**侧载直接走瑰元助手调试通道**

## 致谢与参考

- [purrrock/PurrLaunch](https://github.com/purrrock/PurrLaunch)（MIT）—— 占位启动器原创，本仓库 `apps/purrlaunch-ows/` 是其 OWS 适配 delta
- [bandbbs.cn/threads/29508](https://www.bandbbs.cn/threads/29508/) —— OWS 侧载教程（libiao0418，瑰元助手作者）
- [glyphix-os/web-docs](https://github.com/glyphix-os/web-docs) —— Glyphix 官方文档

## 免责声明

仅供自有设备研究与互操作使用。逆向笔记描述的是个人设备上观察到的行为，不含任何专有代码；
使用本仓库内容产生的任何风险由使用者自担。

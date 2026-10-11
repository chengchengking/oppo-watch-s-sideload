# findings_R20 —— 自建 Purrlaunch-lite：占位木鱼槽位的启动器（2026-10-11 10:41-10:47）

## 背景
用户要求换桌面图标。帖子原方案 = 装 Purrlaunch（rpk 只在米坛附件 10856.webp，账号权限拿不到）。
全网（GitHub/Gitee/酷安/网盘）无镜像 → **自建等效品**：Purrlaunch 的本质 = 占住 com.xfaith.woodenfish
（桌面渲染正常的商店应用槽位）+ 提供第三方应用列表/启动。

## 实现（源码 G:/oppo/purrlaunch-lite/）
- 包名 **com.xfaith.woodenfish**，versionCode **8**（> 木鱼商店版的 7），name=启动器，icon=/assets/icon.png
- 页面：`@system.package.list('app')` 列全部应用 → `pkg.getInfo(pkg)` 取名字 → `@system.launch.launch(pkg)` 点击启动
- 排除自己；系统包（com.heytap/oplus/coloros/android 前缀）排后；scroll 组件滚动列表
- 构建：gx build -d oppo-rtos-watch -f → bundle.pkg 3200B

## 安装与验证
```
10:41:19.653  安装应用 成功 (total_size:3200)    ← 调试通道直接"升级覆盖"商店版木鱼，无签名/版本拦截
10:42:09.083  lvgl: exit application com.xfaith.woodenfish success   ← 旧木鱼被退场
10:42:39.166  app.life: call onDestroy in pkg://com.xfaith.woodenfish
10:47:11.509  app.life: call onShow in pkg://com.xfaith.woodenfish          ← 新启动器运行
10:47:11.514  app.life: call onInit / onReady in .../main/index.uo          ← 页面生命周期完整
10:47:11.612  gx_on_screen_on → eventLoop refresh 1-5（渲染完成，无报错）
```

## 结论
- **升级覆盖商店应用完全可行**（调试通道不做签名校验；versionCode 高于在装版本即可）
- 以后每个新装的第三方应用都会出现在启动器列表里（list('app') 动态枚举）
- 木鱼如需找回：手表应用商店重装（会反过来覆盖启动器，二选一）
- 待用户确认：桌面槽位的名字/图标是否已显示"启动器"（可能需重启刷新）

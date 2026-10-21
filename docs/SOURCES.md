# 外部资源出处与署名（SOURCES）

> **原则**：只写能当场点开核对的字段。查不到就写「未标注」，**不代填人名**。
> 每条都带核实日期；如与上游不一致，以上游为准，欢迎开 issue 指出。

---

## 1. 瑰元助手（Glyphix Assistant）

| 项 | 值 | 核对方式 |
|---|---|---|
| 包名 | `com.glyphix.assistant` | 发布页 / APK |
| **原始发布页** | https://www.pgyer.com/glyphix-assistant | 直接打开 |
| 上游最新版 | **v1.3（build 3，2026-10-08）**，更新说明「支持 OHealth」 | 发布页 |
| 本仓库镜像 | **v1.2（build 2，2026-08-21）** | 发布页历史版本 |
| 作者 / 开发者 | **发布页未标注，本仓库不代填** | 网页与蒲公英接口都没有该字段 |
| 谱系 | 按教程原文：`glyphixdebug` 自 2.5.0 起把「桥接模式」独立到瑰元助手 | 米坛帖 29508 |
| GitHub | 无相关仓库或源码 | 2026-10-21 检索「瑰元助手」「com.glyphix.assistant」「glyphix assistant」均为空 |

**这个发布页是怎么确定的**（可复现，不是搜出来的）：

```bash
# 1) 从镜像 APK 里取出应用自带的更新检查地址
unzip -p glyphix-assistant-v1.2.apk classes.dex | strings \
  | grep -o 'pgyer.com/apiv2/app/check[^"]*'
# 2) 用同一个 appKey 回查蒲公英应用信息
curl 'https://www.pgyer.com/apiv2/app/view?_api_key=<第1步的 api_key>&appKey=<第1步的 appKey>'
```

第 2 步返回的 JSON 里：`buildName`=瑰元助手、`buildIdentifier`=com.glyphix.assistant、
`buildShortcutUrl`=glyphix-assistant → 即上面的分发页地址；`buildVersion`/`buildCreated` 对上表版本与日期。

> `api_key` 是该应用内置的读取用 key，本仓库不转贴，避免被滥用。

---

## 2. 米坛教程：`[教程]OPPO WatchS 如何侧载应用`

- 链接：https://www.bandbbs.cn/threads/29508/
- 作者：**libiao0418**（米坛 UID 3485823，帖子页可见）
- **仅为教程作者**，不是瑰元助手的作者，只是该工具的用户 —— 由本人在 issue #1 中澄清
- 帖内 2026-10-10 补充了教程所用工具的网盘（密码见原帖），Purrlaunch rpk 等附件按原帖提示取

---

## 3. PurrLaunch

- 上游：https://github.com/purrrock/PurrLaunch （MIT，v1.2.2）
- 作者：**purrrock**（GitHub 显示为 PurrRock CatsBand）
- 本仓库关系：`apps/purrlaunch-ows/` 是让它装上 OWW262 的最小 delta，非原创
- 与上面第 2 条的教程作者**没有**署名关系

---

## 4. Glyphix 官方文档

- https://github.com/glyphix-os/web-docs
- 用途：页面语法、组件清单、`@system.*` API 的权威来源

---

## 5. glyphix-cli（构建工具链）

- npm 包：`glyphix-cli`，registry 显示 latest = **0.8.0-rc**（2026-03-24 更新）
- 装出来的 `gx` 版本为 **v0.11.0**（两个号不是一回事，README 与 DEV_GUIDE 已按此表述）

---

## 6. 曾出现的错误署名（留档，避免再犯）

- 曾把「教程作者」写成「瑰元助手作者」，并在此之上挂了一个**查无实据**的第三方平台账号
  （GitHub、米坛帖、Purrlaunch 仓库均无任何关联；具体账号名不再复述，避免二次传播）。
- 2026-10-21 依 issue #1 全部删除。现仓库内除「教程作者 libiao0418」外，不含任何个人署名。

---

**维护约定**：新增任何第三方工具/资源，先在本表登记出处与核实日期，再写进正文；
拿不到出处就只写链接 + 「未标注」，不写名字。

# Miuix 官方文档镜像（离线参考）

本目录是 **Miuix 官方文档站的中文版正文**（纯文本，已去掉导航/侧栏/变更日志）。
用途只有一个：写 UI 时能直接 grep 到**真实签名、真实默认值**，而不是凭记忆猜 API。

## 来源与抓取方式

- 上游：<https://compose-miuix-ui.github.io/miuix/zh_CN/>
- 抓取日期：2026-10-25
- 对应库版本：**0.9.4**（与 `app/build.gradle` 里 `top.yukonga.miuix.kmp:*:0.9.4` 一致）

页面列表来自文档站自己的侧栏（11 篇 guide + 55 个组件页）。**没有**抓 `example/` 源码
——GitHub 在本机网络下连不通（`git ls-remote` 超时），所以只有文档、没有示例工程。

重新抓取（在能联网时）：

```bash
B=https://compose-miuix-ui.github.io/miuix/zh_CN
mkdir -p raw
for p in guide/{best-practices,getting-started,theme,colors,textstyles,icons,blur,squircle,utils,miuix-nav,multiplatform}; do
  curl -sS -o "guide-${p##*/}.html" "$B/$p" &
done
# 组件页名列表见本目录文件名（去掉 .md 即是路径最后一段）
```

## 结构

| 文件 | 内容 |
|---|---|
| `guide-*.md` | 主题系统、颜色、文本样式、图标、模糊、平滑圆角、导航、工具函数、最佳实践 |
| `<component>.md` | 单个组件的属性表 + 代码示例（如 `card.md`、`textfield.md`、`scaffold.md`） |

代码块用 ``` 包裹并保留原始缩进；属性表按「属性名 / 类型 / 说明 / 默认值 / 是否必须 / 适用范围」六列排开。

## 使用纪律

1. **先查这里，再写代码**。Miuix 的 API 与 Material3 完全不同，猜参数名基本必错。
2. 本目录是**只读参考**，不参与构建、不被 Kotlin/Java 引用。
3. 与工程既有约定冲突时，以工程代码为准（例如本工程刻意不用 `NavigationBar`，
   改用 `NavigationRail` + `miuix-nav`）；文档只解决「怎么写才对」，不决定「要不要用」。
4. 版本敏感：文档对应 0.9.4。升级 Miuix 后本目录必须重抓，否则示例会失真。

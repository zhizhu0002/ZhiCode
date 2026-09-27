# 安全说明

## 这是什么，以及为什么它的安全模型值得单独读一遍

蜘蛛 (ZhiCode) **不是普通应用**。按设计它就能：

- 在一个**自带的 Linux 环境**（内置 Termux）里执行任意命令；
- 让一个**语言模型**决定要执行什么命令，并按你选的权限模式自动放行一部分；
- 用一个**虚拟化沙箱**在应用进程里运行**任意 APK**（免安装）；
- 做原生调试与动态注入（Frida / 内存读写）。

也就是说：**它会把「执行任意代码」当成正常功能来用**。下面写清楚边界在哪、
哪些是你自己的责任。

## 权限模式：这是最主要的闸门

Agent 的工具调用受 `PermissionMode` 门控（设置页 / 输入器页脚可切）：

| 模式 | 含义 |
| --- | --- |
| 每次询问 | 每个工具调用都要你确认（最保守） |
| 自动编辑 | 自动允许文件编辑，其他仍需确认 |
| 规划 | 先给计划，你批准后才执行 |
| 自动 | 由模型判断哪些需要确认 |
| **不询问** | 不再弹确认；高风险操作仍会提示 |
| **跳过权限** | **跳过全部检查** —— 仅限你完全信任的环境 |

⚠️ 「跳过权限」意味着模型可以不经确认执行任何命令。**别在装有重要数据的设备上随手开着它。**

## 密钥是怎么存的

API Key 走 **Android Keystore** 的 AES-GCM（见
`app/src/main/java/com/termux/app/zhicode/security/AndroidSecretStore.java`）：

- 密钥本体由系统持有且**不可导出** —— 它不会随应用数据被备份出去；
- 应用私有目录里只有密文与 IV；
- GCM 带完整性校验，密文被改动会直接解不出来，而不是解出一段垃圾。

已知限制：**root 设备上无法保护**。如果你的设备已被 root 且给了应用 root，
任何「应用内加密」都拦不住能读进程内存的攻击者。

## 网络

- 只与你**自己配置的** API 端点通信（`Base URL` + `API Key`）。
- 工程内**没有内置任何第三方统计 / 上报端点**（有一条 `NoBundledThirdPartyEndpointTest` 结构测试在守这件事）。
- ⚠️ 设置里有「**允许明文 HTTP**」开关。默认只走 https；打开后你会把 API Key
  以明文发给该地址。只在连本地/内网服务时才打开。

## 声明了哪些权限，为什么

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 调用你配置的模型 API |
| `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` / `WAKE_LOCK` | Agent 长时间任务不被系统掐掉 |
| `READ/WRITE/MANAGE_EXTERNAL_STORAGE` | 读写你指定的项目目录（终端与文件面板） |
| `REQUEST_INSTALL_PACKAGES` | 把你构建出的 APK 交给系统安装器（**由系统弹窗确认，不是静默安装**） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 长任务续跑 |
| `VIBRATE` | 长按等操作的触感反馈 |

**声明的组件里只有启动用的 `MainActivity` 是 `exported="true"`** —— 没有对外暴露的
Service / Receiver / Provider。

## 已知的、作者认为可接受的取舍

- **沙箱内的 guest 应用并非安全隔离边界**。它们的目的是「免安装运行 / 调试」，
  不是为了把恶意软件关起来。别往里面装你不信任的东西然后以为它出不来。
- **bootstrap 与部分原生库是预编译二进制**（内含 GPL/Apache 许可的第三方程序），
  它们不随本仓库源码一起可审计。清单见 `NOTICE`。
- **模型会犯错**。即使权限模式是「每次询问」，也请把命令读一遍再确认 —— 特别是
  `rm`、`dd`、`curl | sh`、`chmod` 这类。

## 报告漏洞

**请不要开公开 issue。**

请通过 GitHub 的
[私密漏洞报告](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
（仓库 → Security → Report a vulnerability）提交，或在仓库里找作者的联系方式私下告知。

请尽量附上：

- 受影响的版本（`versionName` / 提交号）
- 复现步骤与最小复现
- 影响范围（能读到什么 / 能执行什么）
- 你认为的严重程度

## 不在范围内

- 你自己在「跳过权限」模式下让模型执行的命令造成的后果。
- 你在沙箱里运行的第三方 APK 的行为。
- 已被 root 且给了应用 root 的设备上的本地攻击者。
- 你配置的第三方 API 服务端的行为与它如何处理你的数据。

# 安全说明

## 1. 这是什么，以及为什么它的安全模型值得单独读一遍

ZhiCode **不是普通应用**。按设计它就能：

- 在一个**自带的 Linux 环境**（内置 Termux）里执行任意命令；
- 让一个**语言模型**决定执行什么命令，并按你选的权限模式自动放行一部分；
- 用一个**虚拟化运行时**在本进程体系内免安装运行**任意 APK**，并对 guest 做原生调试与动态注入（Frida / 内存读写）；
- 读写你授权的外部存储目录。

也就是说：**它把「执行任意代码」当成正常功能**。下面写清楚边界在哪、哪些是你自己的责任，
以及**当前实现里尚未闭合的问题**。

> 本文件只描述代码与清单里的可核对事实；凡涉及法律或「是否构成隔离边界」的判断，都会写明依据。

---

## 2. 权限模式：最主要的闸门

工具调用受 `PermissionMode` 门控（`compose/model/UiModels.kt` 定义，`core/PermissionGate.java` 执行）。
六个模式的标签与说明**逐字取自代码**：

| 模式 | 代码里的含义 |
| --- | --- |
| `ASK` 每次询问 | 每个工具调用都需要你确认 |
| `ACCEPT_EDITS` 自动编辑 | 自动允许文件编辑，其他调用仍需确认 |
| `PLAN` 规划 | 先产出计划，批准后再执行 |
| `AUTO` 自动 | 由模型判断哪些调用需要确认 |
| `DONT_ASK` 不询问 | 不再弹出确认，高风险操作仍会提示 |
| `BYPASS` 跳过权限 | **跳过全部权限检查**，仅限受信环境 |

`PermissionGate.require()` 的判定顺序（`core/PermissionGate.java`）值得抄一遍，因为**顺序本身就是安全语义**：

```java
// ① 工具这次申报「必须批准」→ 除了 BYPASS，一律先问（PLAN 直接拒绝）
if (tool.requiresApproval(call.input) && !PermissionModePolicy.BYPASS.equals(mode)) {
    if (PermissionModePolicy.PLAN.equals(mode)) return false;
    return ask(call, kind, mode, listener);
}
// ② 只读类工具永远放行
if (isAlwaysAllowed(kind)) return true;
// ③ 其余按模式分流
if (PermissionModePolicy.BYPASS.equals(mode)) return true;      // 不问就做
if (PermissionModePolicy.DONT_ASK.equals(mode)) return true;    // 不问就做（高危已在 ① 问过）
if (PermissionModePolicy.PLAN.equals(mode)) return false;       // 只读语义：需要落盘的都不执行
```

⚠️ ① 必须在 ② 之前：`MCP` 属于 `NETWORK`，而 `NETWORK` 在 `isAlwaysAllowed` 里**永远放行**；
把逐工具审批放到它后面，那个开关就会「点得动、存得下、界面也对，只是从不拦截任何东西」。

⚠️ 「跳过权限」意味着模型可以不经确认执行任何命令。**别在装着重要数据的设备上长期开着它。**

---

## 3. 密钥是怎么存的

API Key 走 **Android Keystore** 的 AES-GCM，实现在
`app/src/main/java/com/termux/app/zhicode/security/AndroidSecretStore.java`。可核对的事实：

- Keystore 提供者 `AndroidKeyStore`，别名 `zhicode_api_key_v1`，算法 `AES/GCM/NoPadding`，认证标签 128 位。
- 密钥本体由系统持有、**不可导出**；应用私有目录的 `SharedPreferences` 文件 `zhicode_secrets` 里只有
  密文（`*_ciphertext`）与 IV（`*_iv`），都是 Base64。
- 密文被改动会直接解不出来（GCM 完整性），而不是解出一段垃圾。
- 写入用 `commit()` 而不是 `apply()`：密钥是本应用里最典型的「填完就大退」输入，异步写会丢。
- 「读失败」与「没设置」都会返回空串，但读失败会记在 `lastError()` 里 —— 否则设备凭据变更导致解不开时，
  界面只会显示「未配置 API Key」，没人知道真正发生了什么。
- 密钥按 profile 分槽，槽名由 profile id 与 `credentialRevision` 派生的**纯函数**决定；
  改算法等于让所有已存密钥失效。

已知限制：

- **root 设备上无法保护**。能读进程内存的攻击者不受「应用内加密」约束。
- 应用已声明 `android:allowBackup="false"`，不参与系统备份。

---

## 4. 权限与组件：**以合并后的清单为准**（当前实现的重要问题）

### 4.1 `app` 模块自己声明的权限（10 条）

`app/src/main/AndroidManifest.xml` 声明的正是这 10 条：

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 调用你自己配置的模型 API |
| `VIBRATE` | 交互触感反馈 |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` | 读写你指定的项目目录（终端与文件面板） |
| `WAKE_LOCK` | Agent 长任务期间的唤醒锁 |
| `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` | 前台保活与它的通知（Android 13+） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 保活引导 |
| `REQUEST_INSTALL_PACKAGES` | 把你构建出的 APK 交给系统安装器（由系统弹窗确认，不是静默安装） |

### 4.2 合并之后的实际结果是另一回事 ⚠️

`Bcore` 是库模块，它**自己的清单里有 323 条 `uses-permission`**，且没有做任何 `tools:node="remove"` 过滤。
合并进 APK 后的实测结果（debug 变体）：

```bash
# 合并后的清单
app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml

grep -c '<uses-permission' "$MERGE"                                   # → 315
grep -o 'android:name="android.permission[^"]*"' "$MERGE" | sort -u | wc -l   # → 139
grep -c '<uses-permission' Bcore/src/main/AndroidManifest.xml         # → 323
```

其中不少是 signature / privileged 级别、普通应用拿不到（例如 `INSTALL_PACKAGES`、`WRITE_SETTINGS`、
`PACKAGE_USAGE_STATS`、`READ_LOGS`）。但**危险权限会真实出现在安装页的权限列表里**，实测至少包含：

`SYSTEM_ALERT_WINDOW`、`CAMERA`、`RECORD_AUDIO`、`READ_SMS`、`SEND_SMS`、`READ_CONTACTS`、
`READ_CALL_LOG`、`READ_PHONE_STATE`、`ACCESS_FINE_LOCATION`、`QUERY_ALL_PACKAGES`。

这与代码里那句「刻意不带 `SYSTEM_ALERT_WINDOW`」并不矛盾 —— 那是 `app` 模块的意图，
但**合并后的 APK 里实际上带着它**（来自 Bcore 的清单）。

**这是当前实现的一个已知问题，不是文档遗漏**：它意味着安装页会向用户索取一批本应用并不需要的能力。
可行的收口方式（尚未执行）：在 `app` 模块用 `tools:node="remove"` 逐条裁掉库带来的权限，
然后以合并清单的差集作为验收依据。

### 4.3 组件导出面 ⚠️

`app` 模块里导出（`exported="true"`）的只有两个：

| 组件 | 说明 |
| --- | --- |
| `MainActivity` | 启动入口，必须导出 |
| `ZhiDocumentsProvider` | 把 HOME 通过 SAF 发布给系统文件管理器；`android:permission="android.permission.MANAGE_DOCUMENTS"`（signature\|privileged，只有系统持有），访问控制靠这条权限 |

其余自研组件都不导出：`EditorActivity`（`exported="false"`，独立进程 `:editor`）、`SandboxBoard`、
`SandboxKeeper`、`SandboxRpcService`（`${applicationId}.sandbox.control`，`exported="false"`，跑在 `:zhisandbox`）、
`ZhiFileProvider`、`KeepAliveService`。AndroidX Startup 的 `InitializationProvider` 与
`ProfileInstallReceiver` 已用 `tools:node="remove"` 摘掉。

**但合并后的清单远不止这些**：Bcore 合并进来约 200 个导出的 `Proxy*` 组件（3×50 个 `ProxyActivity` /
`TransparentProxyActivity` / `ProxyPendingActivity`、2×50 个 `ProxyService` / `ProxyJobService`、
50 个 `ProxyContentProvider`，以及 `ProxyBroadcastReceiver`）。

```bash
# 统计合并清单里导出的组件（用 node 或任意脚本过滤 exported="true" 的节点）
grep -c 'exported="true"' "$MERGE"
```

这些是 guest 启动机制的一部分（免安装运行 APK 需要一组可被框架实例化的代理组件），
但它们的存在同样扩大了对外可见面，**目前没有做过逐条的可利用性评估**。这一点按「已知未完成项」记录，
不要把它当成已经审计过。

---

## 5. 网络

- 只与你**自己配置的** API 端点通信（`Base URL` + `API Key`）。
- 清单里 `android:usesCleartextTraffic="true"` 是打开的（`targetSdk 28` 下发版），
  加上设置页的明文 HTTP 开关：**打开后 API Key 会以明文发给你填的那个地址**。只在对本地 / 内网服务时才用。
- 工程内没有内置第三方统计 / 上报端点。**这条现在没有自动化断言守着**（曾经的
  `app/tests/NoBundledThirdPartyEndpointTest.java` 已随整套源码文本级断言删除），
  只能靠 grep 复核：`grep -rnE 'https?://[^"'"'"' ]+' app/src/main/java | grep -viE 'github|apache|android|schema|localhost'`。
- `targetSdk 28`：`android:requestLegacyExternalStorage="true"` 让存储走旧语义。

---

## 6. 已知的、作者认为可接受的取舍

- **内置沙箱不是安全隔离边界**。它的目的是「免安装运行 / 调试」，不是为了把恶意软件关起来。
  别往里面装你不信任的东西然后以为它出不来。
- **bootstrap 与部分原生库是预编译二进制**（内含 GPL / Apache 许可的第三方程序），
  它们不随本仓库源码一起可审计。清单见 [`NOTICE`](NOTICE)。
- **模型会犯错**。即使权限模式是「每次询问」，也请把命令读一遍再确认 —— 特别是
  `rm`、`dd`、`curl | sh`、`chmod` 这类。
- **`BYPASS` 下没有闸门**：这条模式的存在本身就是一份信任声明。

---

## 7. 报告漏洞

**请不要开公开 issue。**

请通过 GitHub 的
[私密漏洞报告](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
（仓库 → Security → Report a vulnerability）提交，或在仓库里找到作者的联系方式私下告知。

请尽量附上：

- 受影响的版本（`versionName` / 提交号）
- 复现步骤与最小复现
- 影响范围（能读到什么 / 能执行什么）
- 你认为的严重程度

---

## 8. 不在范围内

- 你自己在「跳过权限」模式下让模型执行的命令所造成的后果。
- 你在沙箱里运行的第三方 APK 的行为。
- 已被 root 且给了应用 root 的设备上的本地攻击者。
- 你配置的第三方 API 服务端的行为与它如何处理你的数据。

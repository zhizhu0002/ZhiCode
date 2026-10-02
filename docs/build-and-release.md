# 构建、混淆与发布

本文件收的是「**做发布时才需要、但做错了很难查**」的那些事：工具链版本为什么被锁死、
R8 规则每条挡的是什么、Termux 上必须多配的一处、以及签名为什么不能用环境变量图省事。

日常构建命令在 [`README`](../README.md) 里。

---

## 1. 工具链

| 项 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **21** | AGP 9 的硬要求 |
| Gradle | **9.3.1** | 由 wrapper 提供 |
| AGP | **9.1.1** | |
| Kotlin + compose 插件 | **2.4.0** | |
| Miuix | **0.9.4** | `miuix-{ui,core,icons,preference,shader,squircle,blur-android}` |
| compileSdk / minSdk / targetSdk | **37 / 24 / 28** | |
| ABI | **arm64-v8a** | 内置 Termux 只提供 arm64 用户空间 |

`gradle.properties` 里的 `android.suppressUnsupportedCompileSdk=37` 是必须的。

### 为什么整条链被锁在 AGP 9 / Gradle 9

`BreadcrumbBar` 只在 Miuix 0.9.4 里有，而 0.9.4 拉入 Compose 1.12.0，后者要求 AGP ≥ 9.1.0。
升级带来三处必须改的写法：

1. 不再需要 `org.jetbrains.kotlin.android` 插件 —— AGP 9 内建 Kotlin 支持，留着会报
   `plugin is no longer required`；
2. `android.applicationVariants` 已移除，自定义 APK 输出名改用 `androidComponents { onVariants(…) }`；
3. JDK 21 成为必需。

⚠️ **不要单独升级其中一个**：这条链是互相约束的，动一个会散架。

---

## 2. 只有 Termux / bionic 环境才需要的一处设置

AGP 自带的 aapt2 在 Termux（bionic libc）里跑不起来，必须换成 Termux 的 aapt2。
**这是本机配置、不在仓库里** —— 它是一条绝对路径，提交进去会让别人 clone 后指向不存在的文件：

```bash
# ~/.gradle/gradle.properties
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

也可临时传：

```bash
./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

普通 Linux / macOS / Windows 开发机**不需要**这一项。

---

## 3. R8（混淆 + 资源压缩）

`app` 与 `Bcore` 的 release 都开着 `minifyEnabled true`；`app` 还开着 `shrinkResources true`。
**debug 一律不混淆**（要能对着栈直接看源码）。

### 实测体积

| 阶段 | release APK |
| --- | --- |
| 首次开 R8 之前 | 43,210,837 字节 |
| app 开 R8 之后 | 35,911,932 字节（−16.9%） |
| Bcore 也开 R8（+ baseline profile） | 36,128,623 字节 |

**Bcore 那一档基本不省包体**，这是预期内的：它自己的 class jar 从 4,304,880 降到 2,176,121 字节
（−49.5%），但最终 APK 只少约 9.3 KB —— 因为 **app 自己也开 R8，Bcore 的类本来就会被 app 的 R8
处理一遍**，库模块的 minify 有很大一部分被使用方重复做了。

> 算术：加了 baseline profile 之后 APK 是 36,128,435 → 36,128,623（+188 字节），
> 而 `assets/dexopt/baseline.prof` 是 **Stored 未压缩**的 9,531 字节 ——
> 差值 9,343 就是 Bcore R8 的净贡献。

开它的实际意义是「全模块一致」与中间产物更小，而不是包体。

### 规则不是照抄模板

本工程有反射与原生 hook，所以 `app/proguard-rules.pro` 与 `Bcore/proguard-rules.pro` 里
**每条规则前面都写了它挡的是什么**：

- **JNI 名字绑定**。`app/src/main/jniLibs/arm64-v8a/libtermux.so` 是预编译产物，导出符号写死为
  `Java_com_termux_terminal_JNI_createSubprocess` 这种形式。R8 改名后终端**永远起不来**，
  报的是运行时 `UnsatisfiedLinkError` 而不是编译错误。除了
  `-keepclasseswithmembernames class * { native <methods>; }`，还必须**整类 keep**
  `com.termux.terminal.JNI` —— 它的方法只被原生侧符号引用，Java 代码里看不到调用者，会被当死代码删掉。
- **注解驱动的反射**。Bcore 的 black-reflection 按 `@BClass` / `@BMethod(name = …)` 反射成员。
  这几条 keep **原先只写在 `Bcore/proguard-rules.pro`**，而那个文件只作用于 Bcore 自己的构建；
  作为库被依赖时传给使用方的是 `consumer-rules.pro`。这是个真实缺口，已补齐。
- **`-dontwarn`**。Bcore 要 hook 的本来就是 `android.jar` 里不存在的类（`ActivityThread`、
  `ServiceManager`、`dalvik.system.*`、`libcore.*`），R8 报的 "Missing class" 是假警报 ——
  这些类在**运行时一定存在**。正确做法是关掉警告，而不是用 `-keep` 去"保住"一个不存在的类。

> 保留行号（`-keepattributes SourceFile, LineNumberTable`）是有意的：混淆后的崩溃栈若没有行号，
> 拿到手也定位不了。代价几 KB。

验证方式：开 R8 后重新构建并**在沙箱里实测** —— 应用启动、界面与资源完整、终端出 `bash-5.3$`
并能执行命令（覆盖 `createSubprocess` / `setPtyWindowSize` / `waitFor` / `close`），签名仍是 v2-only。

`app/tests/R8ConfigTest.java` 守着「R8 被关掉」「非它不可的 keep 被删」「Bcore 的 minify 与它自己的
`-dontwarn` 不同时在场」这几类问题。

---

## 4. Baseline Profile

`app/src/main/baseline-prof.txt` 告诉 ART「这些方法在冷启动路径上」，安装时（或后台空闲时）
把它们 AOT 编译好，省掉首屏的 JIT 预热。

格式是人类可读的（未混淆）类名 + 方法描述符，构建时由 AGP 按 mapping 重写：

```
Lcom/example/Foo;->bar(I)V
Lcom/example/Foo;-><init>()V          # 构造器
Lcom/example/Foo;-><clinit>()V        # static {}
```

⚠️ **每条方法规则必须带 `H` / `S` / `P` 至少一个 flag**（Hot / Startup / PostStartup）。
漏了构建会直接失败：

```
Error parsing baseline-prof.txt : baseline-prof.txt:7313:1 error:
At least one of flags 'H', 'S', 'P' must be specified for a method rule
```

### 这份是手写的，不是设备实测出来的

真正常用的做法是在真机上跑一遍启动、用 `am profile` 或 Macrobenchmark 采出 profile。
本工程没有那个条件（没有 x86_64 宿主、也没有 CI 设备），所以只覆盖**由代码结构就能确定**的那一段：
Application → Activity → ViewModel 构造与首次载入 → 主题 → 根 Composable。

因此三条纪律（也写在文件头部）：

1. **每一条都在编译产物里核对过**（用 `javap` 读 debug 的 `classes.jar`），不是照抄类名猜的；
2. **不写库里的类** —— Compose / Miuix / AndroidX 各自随包发布了 profile，AGP 会自动合并
   （产物里的 `app/build/outputs/apk/release/baselineProfiles/{0,1}/` 就是它们）。在这里重复一遍
   不但没用，还会让这份清单失去「可核对」这个唯一价值；
3. **宁可少写** —— 写错了不崩，只会白占几 KB。

### 为什么显式依赖 profileinstaller

```groovy
implementation 'androidx.profileinstaller:profileinstaller:1.4.0'
```

它本来由 `activity-compose` 间接带进来，"不写也能用"。但不写就**没人替它说话**：哪天上游不再依赖它，
打好的 `assets/dexopt/baseline.prof` 会静静躺在包里没人安装，而收益消失得毫无痕迹
（profile 的收益本来就没人能"看出来"）。

为什么需要它：**API 26~27 没有系统级的 profile 安装流程**，要靠它注册的 Startup Initializer 在进程
启动时把 profile 交给 ART；API 28+ 由系统自己处理。本工程 `minSdk` 是 24 —— 也就是说
**最低支持的那几档系统正是最需要它的时候**。

`R8ConfigTest` 守着前两条纪律与这条依赖。

---

## 5. 发布与签名

```bash
./gradlew :app:assembleRelease --offline
# 产物：app/build/outputs/apk/release/ZhiCode-release.apk
```

混淆映射表在 `app/build/outputs/mapping/release/mapping.txt`，**发版时要一并留存** ——
没有它，用户报的崩溃栈无法还原成源码位置。

### 签名方案：只启用 v2

v1 / v3 / v4 都关。v2 校验的是**整个 APK 文件**而不是 JAR 条目，能挡住 v1 时代「改一个字节仍通过校验」
那类篡改；而 `minSdk 24` 起所有目标设备都支持 v2，所以 v1 没有必要，留着只会多一份可被旧式攻击面
利用的签名。签名信息只保留 `CN=zhizhu0002`（自签，RSA 4096 / SHA256withRSA，有效期 30 年）。复核：

```bash
$ANDROID_HOME/build-tools/<版本>/apksigner verify --verbose \
    app/build/outputs/apk/release/ZhiCode-release.apk
# Verified using v2 scheme (APK Signature Scheme v2): true
```

### 密钥不进版本库

签名配置从**仓库之外**读取，按优先级：

1. `release.properties`（本机专属，**已 gitignore**）；
2. 环境变量 `ZHICODE_STORE_FILE` / `ZHICODE_STORE_PASSWORD` / `ZHICODE_KEY_ALIAS` /
   `ZHICODE_KEY_PASSWORD`（CI 用）。

两者都读不到时 `assembleRelease` **仍能构建**，只是产物未签名并打印一条告警 ——
别人 clone 之后不会因为缺密钥而卡住。

### ⚠️ 本机用环境变量签名：先 `--stop`，否则会静默签不上

**Gradle 守护进程不会接收客户端新加的环境变量。** 实测（用 init 脚本打印 `System.getenv`）：

| 场景 | `System.getenv("ZHICODE_STORE_FILE")` |
| --- | --- |
| 守护进程先启动（当时没这些变量） | `null` |
| **复用**该守护进程，客户端这次带上变量 | **`null`** ← 变量没传进去 |
| `--no-daemon`（全新进程） | 正常读到 |

后果很隐蔽：构建**成功**，只是产物未签名，而本机没人替你验证。所以本机用环境变量签名时要先

```bash
./gradlew --stop        # 让下一个构建重新拉起守护进程，带上新变量
```

或者直接给那一次构建加 `--no-daemon`。**用 `release.properties`（文件）不受影响** ——
文件是守护进程自己去读的，这也是本机推荐的做法。

### CI 签名（缺 secret 会直接失败）

`.github/workflows/build.yml` 用的 secret 名与 `ControlLayoutConverter` 一致，两个仓库配一次即可：

| secret | 值 |
| --- | --- |
| `RELEASE_KEYSTORE` | 密钥库的 base64，**单行**：`base64 -w0 ~/.android-keys/zhicode-release.jks` |
| `KEYSTORE_PASSWORD` | `release.properties` 里的 `storePassword` |
| `KEY_ALIAS` | 同上，`keyAlias` |
| `KEY_PASSWORD` | 同上，`keyPassword` |

添加到 **仓库 → Settings → Secrets and variables → Actions → New repository secret**。

**四个缺任何一个，CI 会在开始构建前直接失败**，而不是产出一个「看起来正常、其实没签名」的 release 包。
原来是后者：CI 全绿，下载下来的 APK 却没有签名 —— 属于「失败静默通过」，所以改成失败要响。
唯一例外是 **fork 的 PR**（GitHub 不给 fork 传 secret），那种情况只警告，产物名带 `-unsigned` 后缀。

CI 里 release 产物**总是**构建（映射表因此始终有），并额外断言：签名是 v2-only（v1/v3/v4 均为 false）、
签名者 `CN=zhizhu0002`、签名者数量为 1。换一把密钥签出来的包与已发布版本签名不符，用户无法覆盖安装，
这几条断言把那种事故挡在发布之前。

CI 不受上面那个守护进程问题困扰：`ZHICODE_*` 声明在**作业级** `env` 里，本作业第一个 `gradlew`
启动守护进程时就已经带着它们了（放步骤级就会踩那个坑，workflow 里有同样的说明）。

> ⚠️ **密钥库与口令必须单独备份**（放在仓库之外，例如 `~/.android-keys/`）。
> Android 只认签名、不认人：密钥丢了就**再也发不出同一个应用的更新** —— 签名不同的 APK 无法覆盖安装，
> 用户必须先卸载，等于清空他们的数据。

---

## 6. 沙箱里实测

改完构建相关的东西，光看「构建成功」不够 —— 沙箱里跑一遍才算数：

```bash
iqsandbox install com.zhizhu.code "{\"path\":\"<apk 绝对路径>\"}"
iqsandbox launch  com.zhizhu.code
iqsandbox screenshot com.zhizhu.code
```

Compose 在 `dump_ui` 里只会呈现一个 `AndroidComposeView`（无法定位内部节点），因此验证界面
**必须靠截图**。`Sandbox action=tap` 的 `x/y` 是 **window 像素**（直接派发 `MotionEvent` 给 decor view），
不是截图像素；本机截图为 688×1452、window 为 1080×2279，换算系数约 **1.5696**。

细节见 [`sandbox-host.md`](sandbox-host.md)。

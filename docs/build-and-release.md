# 构建、混淆与发布

本文件收的是「**做发布时才需要、但做错了很难查**」的那些事：工具链为什么被锁、
R8 规则每条挡的是什么、Termux 上必须多配的一处、签名与 CI 的真实行为。

日常构建命令在 [`README`](../README.md) 里。

---

## 1. 工具链

| 项 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **21** | AGP 9 的要求；`app` 的 `compileOptions` 也是 21 |
| Gradle | **9.3.1** | 由 wrapper 提供（`gradle/wrapper/gradle-wrapper.properties`） |
| AGP | **9.1.1** | `build.gradle` 的 plugins 块 |
| Kotlin / Compose 插件 | **2.4.0** | `app` 只应用 `org.jetbrains.kotlin.plugin.compose` |
| Miuix | **0.9.4** | `miuix-{ui,core,icons,preference,shader,squircle,nav,blur-android}` |
| Sora Editor | **0.24.6** | `io.github.rosemoe:editor` + `language-textmate` |
| compileSdk / minSdk / targetSdk | **37 / 24 / 28** | `compileSdkVersion`/`targetSdkVersion`/`minSdk` 也放在根 `build.gradle` 的 `ext` 里 |
| ABI | **arm64-v8a** | `ndk { abiFilters 'arm64-v8a' }` —— 内置 Termux 只有 arm64 用户空间 |

`gradle.properties` 里的这些开关是有意的：`android.useAndroidX`、`android.nonTransitiveRClass`、
`android.suppressUnsupportedCompileSdk=37`（compileSdk 37 会触发 AGP 的未支持警告，
不抑制就是一条噪声）、`org.gradle.parallel/caching/configuration-cache/daemon`、
`org.gradle.workers.max=2`（移动设备上无界并发会内存抖动）。

### 为什么这条链被锁住

`Miuix 0.9.4` 拉入 Compose 1.12，后者要求 AGP ≥ 9.1.0；AGP 9 又要求 JDK 21，并带来两处必须改的写法：

1. Kotlin 支持已内建，`org.jetbrains.kotlin.android` 插件不再需要（留着会报 `plugin is no longer required`）；
2. `android.applicationVariants` 已移除，自定义 APK 输出名改用 `androidComponents { onVariants(…) }`
   （见 `app/build.gradle` 末尾）。

⚠️ **不要单独升级其中一个**：这条链互相约束，动一个会散架。

> 根 `build.gradle` 里还保留着 `ext.compileSdkVersion/targetSdkVersion/minSdk/iqNdkVersion`，
> 以及一段关于「为什么不在 plugins 块里声明 `com.android.library`」的说明 —— Bcore 是**应用插件的方式**
> 拿到的（AGP 本体已在 classpath 上），不是插件 marker。别顺手"整理"掉。

---

## 2. 只有 Termux / bionic 环境才需要的一处设置

AGP 自带的 aapt2 在 Termux（bionic libc）里跑不起来，必须换成 Termux 的 aapt2。
**这是本机配置、不在仓库里** —— 它是一条绝对路径，提交进去会让别人 clone 后指向不存在的文件：

```bash
# ~/.gradle/gradle.properties
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

也可以临时传：

```bash
./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

普通 Linux / macOS / Windows 开发机**不需要**这一项。CI 反而要**确保它不存在**：
`.github/workflows/build.yml` 里有一条步骤专门把它从 `gradle.properties` 里删掉，
删不掉就 fail（否则会覆盖 runner 上 SDK 的 aapt2）。

---

## 3. R8（混淆 + 资源压缩）

| 模块 | release 配置 |
| --- | --- |
| `app` | `minifyEnabled true` + `shrinkResources true`，`proguard-android-optimize.txt` + `app/proguard-rules.pro` |
| `Bcore` | `minifyEnabled true`，`consumerProguardFiles "consumer-rules.pro"` |
| debug | **一律不混淆**（要能对着栈直接看源码） |

### 规则不是照抄模板

本工程有反射与原生 hook，所以规则得对着真实调用点写。关键三条：

- **JNI 名字绑定**。`app/src/main/jniLibs/arm64-v8a/libtermux.so` 是预编译产物，导出符号写死为
  `Java_com_termux_terminal_JNI_createSubprocess` 这类形式。除了
  `-keepclasseswithmembernames class * { native <methods>; }`，还必须**整类 keep**
  `com.termux.terminal.JNI` —— 它的方法只被原生侧符号引用，Java 代码里看不到调用者，会被当死代码删掉。
  改名之后终端**永远起不来**，报的是运行时 `UnsatisfiedLinkError` 而不是编译错误。
- **注解驱动的反射**。Bcore 的 black-reflection 按 `@BClass` / `@BMethod(name = …)` 反射成员，
  这些 keep 必须出现在**被使用方继承的** `consumer-rules.pro` 里 —— 只写在模块自己的
  `proguard-rules.pro` 里时，作为库被依赖并不生效。
- **`-dontwarn`**。Bcore 要 hook 的本来就是 `android.jar` 里不存在的类（`ActivityThread`、`ServiceManager`、
  `dalvik.system.*`、`libcore.*`），R8 报的 "Missing class" 是假警报 —— 这些类在**运行时一定存在**。
  正确做法是关掉警告，而不是用 `-keep` 去"保住"一个不存在的类。

保留 `SourceFile, LineNumberTable` 是有意的：混淆后的崩溃栈若没有行号，拿到手也定位不了。

⚠️ 原先有一条 `app/tests/R8ConfigTest.java` 守着「R8 被关掉」「非它不可的 keep 被删」这类问题
（它们只在**运行期**炸：终端起不来、沙箱打不开，而编译与单测全绿）。**它已随 `app/tests/` 整套删除。**
所以改 `proguard-rules.pro` / `consumer-rules.pro` 之后，唯一的验证方式是**真的装一次并确认
终端能起到提示符、沙箱能打开**，而不是看构建成功。

### 关于包体数字

文档里**不再写死体积数字** —— 它们每次构建都不同，抄进文档就会变成假话。要看就自己量：

```bash
ls -l app/build/outputs/apk/release/ZhiCode-release.apk
# R8 前后对比：把 app/build.gradle 的 minifyEnabled 临时关掉构建一次，再打开构建一次
```

参照量级（同一台机器上导出、**仅作参考**）：debug 产物约 55 MB、release 产物约 38 MB
（`app/build/outputs/apk/*/*.apk`）。**别把这两个数当承诺。**

---

## 4. Baseline Profile

`app/src/main/baseline-prof.txt`（当前 75 行）告诉 ART「这些方法在冷启动路径上」，安装时（或后台空闲时）
把它们 AOT 编译好，省掉首屏的 JIT 预热。

格式是人类可读的（未混淆）类名 + 方法描述符，构建时由 AGP 按 mapping 重写：

```
Lcom/example/Foo;->bar(I)V
Lcom/example/Foo;-><init>()V          # 构造器
Lcom/example/Foo;-><clinit>()V        # static {}
```

⚠️ **每条方法规则必须带 `H` / `S` / `P` 至少一个 flag**（Hot / Startup / PostStartup），漏了构建直接失败：

```
Error parsing baseline-prof.txt : baseline-prof.txt:7313:1 error:
At least one of flags 'H', 'S', 'P' must be specified for a method rule
```

### 这份是手写的，不是设备实测出来的

常规做法是在真机上跑一遍启动、用 `am profile` 或 Macrobenchmark 采出 profile。本工程没有那个条件，
所以只覆盖**由代码结构就能确定**的那一段：Application → Activity → ViewModel 构造与首次载入 → 主题 → 根 Composable。

因此三条纪律（也写在文件头部）：

1. **每一条都在编译产物里核对过**（用 `javap` 读 debug 的 classes），不是照抄类名猜的；
2. **不写库里的类** —— Compose / Miuix / AndroidX 各自随包发布了 profile，AGP 会自动合并；
   在这里重复一遍不但没用，还会让这份清单失去「可核对」这个唯一价值；
3. **宁可少写** —— 写错了不崩，只会白占几 KB。

### 为什么显式依赖 profileinstaller

```groovy
implementation 'androidx.profileinstaller:profileinstaller:1.4.0'
```

它本来由 `activity-compose` 间接带进来（"不写也能用"），但不写就**没人替它说话**：哪天上游不再依赖它，
打好的 `assets/dexopt/baseline.prof` 会静静躺在包里没人安装，而收益消失得毫无痕迹。

为什么需要它：**Android 8.0 / 8.1（API 26、27）没有系统级的 profile 安装流程**，要靠它注册的
Startup Initializer 在进程启动时把 profile 交给 ART；API 28+ 由系统自己处理。
本工程 `minSdk` 是 24，而**最低支持的那几档系统正是最需要它的时候**。

⚠️ 上面三条纪律原先由 `app/tests/R8ConfigTest.java` 守着，**它已随 `app/tests/` 整套删除**。
现在改 `baseline-prof.txt` 只能靠自己按这三条核对 —— 写错了不崩，只会白占几 KB 且没人发现。

---

## 5. 发布与签名

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/ZhiCode-release.apk
```

混淆映射表在 `app/build/outputs/mapping/release/mapping.txt`，**发版时要一并留存** ——
没有它，用户报的崩溃栈无法还原成源码位置。

### 签名方案：只启用 v2

`app/build.gradle` 里对 release 签名配置显式写了：

```groovy
enableV1Signing false
enableV2Signing true
enableV3Signing false
enableV4Signing false
```

v2 校验的是**整个 APK 文件**而不是 JAR 条目，能挡住 v1 时代「改一个字节仍通过校验」那类篡改；
而 `minSdk 24` 起所有目标设备都支持 v2，v1 只会多留一份可被旧式问题利用的签名。
v3 是密钥轮换用的（现在用不到），v4 会额外生成 `.idsig`（发布物里不需要）。

复核：

```bash
$ANDROID_HOME/build-tools/<版本>/apksigner verify --verbose \
    app/build/outputs/apk/release/ZhiCode-release.apk
```

### 密钥不进版本库

签名配置从**仓库之外**读取，按优先级：

1. `release.properties`（本机专属，**已 gitignore**）；
2. 环境变量 `ZHICODE_STORE_FILE` / `ZHICODE_STORE_PASSWORD` / `ZHICODE_KEY_ALIAS` / `ZHICODE_KEY_PASSWORD`。

两者都读不到时 `assembleRelease` **仍能构建**，只是产物未签名并打印一条警告 ——
别人 clone 之后不会因为缺密钥而卡住。

> ⚠️ 一个实测到的坑：**Gradle 守护进程不会接收客户端新加的环境变量**。
> 如果守护进程是在这些变量还不存在时启动的，之后复用它的构建会读不到变量 ——
> 表现是「构建成功，但产物未签名」。本机用环境变量签名时先 `./gradlew --stop`（或给那一次构建加
> `--no-daemon`）；用 `release.properties`（文件）不受影响，因为文件是守护进程自己去读的。

---

## 6. CI 的真实行为

`.github/workflows/build.yml`：

| 步骤 | 做什么 |
| --- | --- |
| 环境 | JDK 21（temurin）、`android-actions/setup-android`，装 `platform-tools`、`platforms;android-37.0`、`build-tools;36.0.0` |
| 归一化本机设置 | 删 `local.properties`；从 `gradle.properties` 删掉 `android.aapt2FromMavenOverride`（删不掉就 fail） |
| 准备签名（可选） | 读 4 个 secret；**齐全**时解码 `release.keystore` 并用 `keytool -list` 校验 |
| 构建 | `./gradlew --no-daemon --stacktrace --console=plain :app:assembleDebug :app:assembleRelease` |
| 校验签名（仅签名齐全时） | `apksigner verify --verbose` 并断言：v2 为 `true`、v1/v3/v4 为 `false`、`Number of signers: 1` |
| 上传产物 | debug APK（7 天）、已签名 release APK（14 天）**或** `zhicode-release-apk-unsigned`（14 天）、`mapping.txt`（14 天） |

**四个 secret 缺任何一个时，CI 不会失败**：那一步只输出 `signed=false` 并说明「release 将是未签名的」，
构建照常进行，产物名带 `-unsigned` 后缀。这是为 fork 的 PR 准备的（GitHub 不给 fork 传 secret）。

secret 名与 `ControlLayoutConverter` 一致，两个仓库配一次即可：

| secret | 值 |
| --- | --- |
| `RELEASE_KEYSTORE` | 密钥库的 base64，**单行**：`base64 -w0 ~/.android-keys/<keystore>.jks` |
| `KEYSTORE_PASSWORD` | `release.properties` 里的 `storePassword` |
| `KEY_ALIAS` | 同上，`keyAlias` |
| `KEY_PASSWORD` | 同上，`keyPassword` |

添加位置：**仓库 → Settings → Secrets and variables → Actions → New repository secret**。

CI 里 `ZHICODE_*` 声明在**作业级** `env`（不是步骤级）—— 放步骤级会踩上面那个守护进程不接环境变量的坑。
CI 还用 `--no-daemon` 再兜一道。

> ⚠️ **密钥库与口令必须单独备份**（放在仓库之外，例如 `~/.android-keys/`）。
> Android 只认签名、不认人：密钥丢了就**再也发不出同一个应用的更新** —— 签名不同的 APK 无法覆盖安装，
> 用户必须先卸载，等于清空他们的数据。

---

## 7. 构建相关的改动怎么验

改完构建配置，光看「构建成功」不够：

```bash
./gradlew :app:assembleDebug
# 在沙箱里装一遍（命令与用法见 docs/sandbox-host.md）
zhisandbox install com.zhizhu.code "{\"path\":\"<apk 绝对路径>\"}"
zhisandbox launch  com.zhizhu.code
zhisandbox screenshot com.zhizhu.code
```

Compose 界面在 `dump_ui` 里通常只呈现一个 `AndroidComposeView`，所以界面验证**必须靠截图**。

> ⚠️ 这一段现在是**唯一**能验证构建配置改动的办法：原来那套 `app/tests/` 结构断言
> （`R8ConfigTest`、`Sandbox*Test` 等）已整套删除，构建配置与沙箱不变式都**没有自动化防线**了。

依赖版本、`consumer-rules` 与 baseline profile 这类东西的错误都只在**运行期**暴露（终端起不来、沙箱打不开），
所以除了截图，还应当在沙箱里确认终端能起到提示符、能执行命令。

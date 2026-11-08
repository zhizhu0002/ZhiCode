# 蜘蛛（ZhiCode）的 R8 规则。
#
# 原则：这里**只写开 R8 之后真的会坏的东西**。
#
# Compose / AndroidX / MiuiX 不需要在这里重复声明，它们要么自带 consumer 规则，
# 要么本来就不靠反射。往这里堆"以防万一"的 -keep 只会让 R8 白干。
#
# 判断方法：构建后翻 app/build/outputs/mapping/release/mapping.txt，看某个类
# 是不是真的被改了名；先有证据，再加规则。


# ---------------------------------------------------------------------------
# 1) 原生库按名字反查 Java 成员 —— 漏了必崩，且不是编译错误
# ---------------------------------------------------------------------------
#
# app/src/main/jniLibs/arm64-v8a/libtermux.so 是**预编译**产物，走的是 JNI 名字
# 绑定，导出符号已经写死在 .so 里：
#
#     Java_com_termux_terminal_JNI_createSubprocess
#     Java_com_termux_terminal_JNI_setPtyWindowSize
#     Java_com_termux_terminal_JNI_setPtyUTF8Mode
#     Java_com_termux_terminal_JNI_waitFor
#     Java_com_termux_terminal_JNI_close
#
# ART 是按「Java_ + 包名/类名/方法名（点换下划线）」去 .so 里找符号的。R8 一旦把
# com.termux.terminal.JNI 或其方法改名，终端就永远起不来，报的是运行时
# UnsatisfiedLinkError 而不是编译失败 —— 这类问题最难查，所以显式钉住。
#
# 两条都需要：
#   -keepclasseswithmembernames 保住**名字**；
#   -keep class … {*;} 保住**类本身**不被裁掉 —— JNI.java 里那几个方法只被原生
#   侧的符号引用，Java 代码里看不到调用者，R8 会当成死代码删掉。
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.termux.terminal.JNI { *; }


# ---------------------------------------------------------------------------
# 2) 注解要能在运行期读到
# ---------------------------------------------------------------------------
#
# 本工程的运行期注解消费方现在只剩 Shizuku 的 AIDL 生成物（Stub/Proxy 靠
# 注解与签名描述符互相识别）。沙盒（Bcore / black-reflection）已整体移除，
# 它那套 @BClass / @BMethod(name = "…") 的反射入口不存在了。
#
# 但这条规则**仍然必须保留**：注解属性一旦在压缩阶段被当成"没人读"丢弃，
# 任何「靠注解名去反射」的路径都会静默失效 —— 而这类失效不会报错，只会在
# 运行期返回 null。留着的成本是几 KB，去掉的代价是查不出来的空指针。
-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, Signature, InnerClasses, EnclosingMethod


# ---------------------------------------------------------------------------
# 3) 让崩溃栈还能看
# ---------------------------------------------------------------------------
#
# 不保留行号的话，拿到的栈全是 "SourceFile:0"，定位不了任何东西。代价是几 KB，
# 对一个自己长期用、还要开源给人读的工具完全值得。
#
# renamesourcefileattribute 把源文件名统一成 SourceFile，免得把本机路径
# （/data/user/0/com.iqge/...）泄进发布产物。
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile


# ---------------------------------------------------------------------------
# 4) 隐藏 API 的引用
# ---------------------------------------------------------------------------
#
# Termux 用户空间里的 ELF 与 Shizuku 的 AIDL 会引用 android.jar 里**不存在**的东西
# （ActivityThread、ServiceManager、dalvik.system.* 等）。R8 拿编译期 android.jar
# 当参照，找不到这些类就会报 "Missing class"，而按 AGP 的默认严格度会直接让构建失败。
#
# 这些类在**运行时一定存在**（就是设备上的 framework），所以警告本身是假警报，
# 用 -dontwarn 关掉是正确做法，而不是用 -keep 去"保住"不存在的东西。
-dontwarn android.**
-dontwarn com.android.**
-dontwarn dalvik.**
-dontwarn libcore.**
-dontwarn mirror.**
-dontwarn sun.**
-dontwarn org.apache.**
-dontwarn org.slf4j.**


# ---------------------------------------------------------------------------
# 5) Shizuku（无 Root 的 shell 身份）
# ---------------------------------------------------------------------------
#
# 这一段是**必须**的，而且理由不是"以防万一"：
#
# 1) `moe.shizuku.server.*` 是 AIDL 生成的 Stub/Proxy。我们调用的是
#    `IShizukuService.Stub.asInterface(binder)`，而它在**对端**（Shizuku 服务进程）
#    才展开成 onTransact 分派。Binder 的分派靠 `Parcel` 里的接口描述符字符串，
#    那个字符串是编译期常量、不会被改名 —— 但只要 R8 把 `Stub` 本身裁掉或改名，
#    我们这侧就没有可用的代理类，`getVersion()`/`newProcess()` 会直接
#    NoSuchMethodError。这两个类是本工具唯一的执行通路，体积只有几 KB。
#
# 2) `rikka.shizuku.ShizukuProvider` 由**系统**按名字实例化（清单里那条 provider），
#    R8 看不到任何 Java 调用点。AGP 通常会替清单组件自动加 keep，但那条规则
#    覆盖的是"清单里出现的组件类"，而 provider 内部还会反射回调
#    `Shizuku.onBinderReceived(...)`，这一层没有任何静态引用。
#
# 3) `Shizuku` 自己用反射读隐藏的 `android.os.SystemProperties`（见
#    `ShizukuSystemProperties`），并在 API 11 之前的版本上走另一条
#    Parcel 手写路径；这些都只按名字发生，静态分析看不到。
#
# 结论：把这两个包整体钉住。它们是第三方库的外壳（不是我们的业务代码），
# 总量在 30KB 量级，换的是"打开 R8 之后工具仍然可用"。
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }

# ---------------------------------------------------------------------------
# 6) BlackBox 虚拟运行时（Bcore）—— IQ 沙箱的引擎
# ---------------------------------------------------------------------------
#
# 这一段与上面 Shizuku 那段是同一类理由，但**后果更严重**：R8 在这里出错不会
# 表现为某个功能不可用，而是**沙箱进程起不来**、或者装进去的应用一启动就崩，
# 而你从宿主界面上只能看到一句"引擎初始化失败"。
#
# 具体需要钉住的东西，逐条对着真实调用点：
#
# 1) `top.niunaijun.blackbox.**` —— 整体的反射面。
#    BlackBox 的核心手法就是"反射 + 动态代理"：它按**类名字符串**加载被 hook 的
#    系统服务代理（`...fake/service/I*Proxy`）、按名字找 `*Manager` 单例、
#    按名字构造 guest 的 `Application`/`Activity`。这些名字全部是运行期拼出来的，
#    R8 看不到调用点。整个包必须 keep。
#
# 2) JNI 入口。`libblackbox.so` 是**预编译冻结**的二进制（本机 aarch64 上无法
#    重新编译，见 Bcore/build.gradle），它按名字找 Java 侧的 native 方法与回调类。
#    只要 R8 改一个名字，`.so` 就再也连不上，而这一侧完全没有编译期约束。
#      · NativeCore 的 6 个 native 方法：init / enableIO / addIORule /
#        hideXposed / disableHiddenApi / disableResourceLoading
#      · JNI 回调的 setter（`.so` 反向调用 Java）
-keepclasseswithmembernames,allowoptimization class top.niunaijun.** {
    native <methods>;
}
-keep class top.niunaijun.blackbox.core.NativeCore { *; }
-keep class top.niunaijun.jnihook.jni.JniHook { *; }
-keep class top.niunaijun.jnihook.MethodUtils { *; }

# 3) AIDL 生成的 Stub/Proxy。BlackBox 把系统服务换成自己的代理对象，靠的是
#    `IInterface.Stub.asInterface(binder)` 与 `onTransact` 的接口描述符字符串。
#    stub 被裁掉就没有可用的代理类。
-keep class **.Stub { *; }
-keep class **.Stub$* { *; }
-keep class **.Proxy { *; }

# 4) 清单里那三个组件由系统按名字实例化（R8 看不到 Java 调用点）。
#    路径必须与 AndroidManifest.xml 一致。
-keep class com.zhizhu.zhicode.sandbox.SandboxControlProvider { *; }
-keep class com.zhizhu.zhicode.sandbox.SandboxDashboardActivity { *; }
-keep class com.zhizhu.zhicode.sandbox.SandboxGuardService { *; }
-keep class com.zhizhu.zhicode.sandbox.ZhiSandboxEngine { *; }

# 5) `black-reflection` 的注解驱动反射层。
#
# 🔴 这一段曾经**漏了两个注解**，后果是沙箱里装任何 APK 都失败：
#
#   java.lang.RuntimeException: Not found @BlackClass or @BlackStrClass
#       at xn.d(SourceFile:36)                      ← 混淆后的 BlackReflection
#       at PackageParserCompat.createParser(...)
#       at BPackageManagerService.parserApk(...)
#   → parser apk error.
#
# 机制：注解处理器把每个 `@BClassName` 接口再生成两个**带 NotProcess 注解**的
# 子接口（`...Context` / `...Static`），运行期真正被 `getAnnotation()` 读取的是
# 后两者。原来的规则只 keep 了 `@BClassName`，于是这两个子接口被 R8 裁掉/改名，
# 注解读不到 → 抛 RuntimeException → 被 `parserApk` 的 catch(Throwable) 吞成
# 一句 `parser apk error.`，从上层完全看不出是注解丢了。
#
# 所以下面**必须同时包含 NotProcess 变体**，缺一个都可能让某条反射路径失效。
-keep @top.niunaijun.blackreflection.annotation.BClassName class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BClassNameNotProcess class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BClassInvoke class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BField class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BFieldNotProcess class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BFieldCheckNotProcess class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BFieldSetNotProcess class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BMethod class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BMethodCheckNotProcess class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BStaticField class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BStaticMethod class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BConstructor class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BConstructorNotProcess class * { *; }

# 注解处理器生成的 `BR*` 桥接类，以及 `black.**` 下的接口本体。
# 前者由 Bcore 的代码 `import` 引用，后者靠字符串查找（`black.android.content.pm.XXX`），
# R8 看不到后者的调用点。
-keep class black.** { *; }
-keep class top.niunaijun.blackreflection.** { *; }

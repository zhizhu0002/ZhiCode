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
# Bcore 的 black-reflection 是**注解驱动**的反射：靠 @BClass / @BMethod(name = "…")
# 这些运行期注解决定去反射哪个成员。注解属性若在压缩阶段被当成"没人读"丢掉，
# 反射会静默失效。
#
# 注意 Bcore 的 consumer 规则里已有一份类似的 keep —— 但那是针对 **Bcore 的类**
# 写规则名；注解属性是被使用方（就是本模块）保留的，所以这里必须再声明一次。
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
# Bcore 要 hook 的本来就是 android.jar 里**不存在**的东西（ActivityThread、
# ServiceManager、dalvik.system.* 等）。R8 拿编译期 android.jar 当参照，找不到
# 这些类就会报 "Missing class"，而按 AGP 的默认严格度会直接让构建失败。
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
-dontwarn top.niunaijun.blackbox.**
-dontwarn top.niunaijun.blackreflection.**
-dontwarn top.niunaijun.jnihook.**

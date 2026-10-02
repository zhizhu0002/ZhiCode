# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class top.niunaijun.blackbox.** {*; }
-keep class top.niunaijun.jnihook.** {*; }
-keep class mirror.** {*; }
-keep class android.** {*; }
-keep class com.android.** {*; }

-keep class top.niunaijun.blackreflection.** {*; }
-keep @top.niunaijun.blackreflection.annotation.BClass class * {*;}
-keep @top.niunaijun.blackreflection.annotation.BClassName class * {*;}
-keep @top.niunaijun.blackreflection.annotation.BClassNameNotProcess class * {*;}
-keepclasseswithmembernames class * {
    @top.niunaijun.blackreflection.annotation.BField.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldSetNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldCheckNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BMethod.* <methods>;
    @top.niunaijun.blackreflection.annotation.BStaticField.* <methods>;
    @top.niunaijun.blackreflection.annotation.BStaticMethod.* <methods>;
    @top.niunaijun.blackreflection.annotation.BMethodCheckNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BConstructor.* <methods>;
    @top.niunaijun.blackreflection.annotation.BConstructorNotProcess.* <methods>;
}

# ---------------------------------------------------------------------------
# 5) 隐藏 API：这些类是**故意**在编译期找不到的
# ---------------------------------------------------------------------------
#
# 本模块的 release 现在开了 R8（见 build.gradle 里那段说明），于是本节成了必需项：
# R8 拿**编译期 android.jar** 当参照，而 Bcore 要 hook 的本来就是 android.jar 里
# 不存在的东西 —— ActivityThread、ServiceManager、dalvik.system.*、libcore.* 等。
# 找不到这些类时 AGP 的默认严格度会让**构建直接失败**。
#
# 它们在**运行时一定存在**（就是设备上的 framework），所以这是假警报：
# 正确做法是 -dontwarn 关掉警告，而不是用 -keep 去"保住"一个不存在的类
# （后者只会让 R8 白干，还可能把真正的缺口掩盖掉）。
#
# ⚠️ 这份列表要与 `app/proguard-rules.pro` 的第 4 节保持一致：
# 两边是同一批类，只是分别作用于两个模块各自的 R8 运行。
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
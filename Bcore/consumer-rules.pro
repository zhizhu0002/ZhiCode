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

# ---------------------------------------------------------------------------
# black.** —— BlackReflection 的注解驱动反射层
# ---------------------------------------------------------------------------
#
# 🔴 这一条曾经缺失，后果是**沙箱里装任何 APK 都失败**：
#
#   java.lang.RuntimeException: Not found @BlackClass or @BlackStrClass
#       at PackageParserCompat.createParser(...)
#       at BPackageManagerService.parserApk(...)
#   → parser apk error.
#
# 机制：`black.android.content.pm` 下的 `PackageParserMarshmallow` 等接口带
# `@BClassName` / `@BClassNameNotProcess` 注解，运行期由
# `BlackReflection.getClassNameByBlackClass()` 用 `clazz.getAnnotation(...)` 读取。
# R8 一旦把 `black.**` 裁掉或改名，注解就读不到，抛上面那个 RuntimeException，
# 而它又被 `parserApk` 的 catch(Throwable) 吞成一句 `parser apk error.` ——
# 从上层完全看不出是注解丢了。
#
# 所以这里必须 keep 整个 `black.**`，**并且保留注解属性**：
# 只 keep 类、不 keep `RuntimeVisibleAnnotations`，注解照样会丢。
-keep class black.** {*; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault



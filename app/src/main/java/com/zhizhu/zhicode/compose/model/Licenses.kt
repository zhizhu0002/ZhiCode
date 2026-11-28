package com.zhizhu.zhicode.compose.model

data class LicenseEntry(
    val name: String,
    val license: String,
    val version: String,
    val detail: String,
    val group: String,
    val author: String,
    val homepage: String? = null,
)

/** 全部第三方组件。 */
val THIRD_PARTY_LICENSES: List<LicenseEntry> = listOf(
    LicenseEntry(
        name = "IQ Code",
        license = "MIT",
        version = "—",
        group = "起点与致谢",
        author = "iqisge-gif",
        homepage = "https://github.com/iqisge-gif/IQ-Code-Android",
        detail = "本工程的起点。协议层、Agent 主循环与工具集、沙箱宿主层的早期实现都从它那里起步，" +
            "之后被分批重写。重写不免除 MIT 的署名义务，这份致谢是**自愿**保留的 —— " +
            "归属度量工具一直在拿本工程与原版逐行比对，派生关系本来就是自证的，" +
            "把出处写清楚比含糊过去更诚实。",
    ),
    LicenseEntry(
        name = "BlackBox 虚拟化引擎（Bcore / black-reflection / compiler）",
        license = "Apache-2.0",
        version = "—",
        group = "起点与致谢",
        author = "ALEX5402 / FBlackBox",
        homepage = "https://github.com/ALEX5402/NewBlackbox",
        detail = "IQ 沙箱的 Android 虚拟运行时：guest 进程管理、Activity/Service 代理、系统服务 hook，" +
            "以及两个辅助模块（反射与编译期注解处理）。经上游 IQ Code 的 vendor 副本搬入本仓库。" +
            "本工程改过的是 Bcore/build.gradle（移除 ndk-build 改预编译 .so、适配 AGP 9 与 SDK 35），" +
            "源码本身未改动。详见根 NOTICE 第 2 节。",
    ),
    LicenseEntry(
        name = "静态链接在 libblackbox.so 内的 Dobby",
        license = "Apache-2.0",
        version = "—",
        group = "起点与致谢",
        author = "jmpews",
        homepage = "https://github.com/jmpews/Dobby",
        detail = "内联 hook 引擎，**不是独立文件** —— 它被静态链接进 Bcore 的预编译原生库" +
            " app/src/main/jniLibs/arm64-v8a/libblackbox.so。因此它不会出现在依赖列表里，" +
            "只能从二进制内容上认出来（核对命令见根 NOTICE 第 2.1 节，可重跑）。",
    ),
    LicenseEntry(
        name = "Termux 终端模拟与视图",
        license = "Apache-2.0",
        version = "—",
        group = "运行环境",
        author = "termux",
        homepage = "https://github.com/termux/termux-app",
        detail = "terminal-emulator 与 terminal-view 两个子库，以及原生 PTY 桥接库 libtermux.so。" +
            "termux-app 整体是 GPLv3-only，但其根许可证明确给这两个子库开了 Apache-2.0 的例外 —— " +
            "libtermux.so 是被 System.load 链接进本应用进程的，所以这一点决定了本工程不必整体转为 GPL。",
    ),
    LicenseEntry(
        name = "termux-shared · TermuxConstants",
        license = "MIT",
        version = "—",
        group = "运行环境",
        author = "termux",
        homepage = "https://github.com/termux/termux-app",
        detail = "该库的许可证把 termux-shared 整体定为 MIT，但其中 termux/ 目录是 GPLv3-only，" +
            "并点名排除 TermuxConstants.java 与 TermuxPropertyConstants.java。" +
            "本工程用的是后者，且**整体重写**过：路径在运行期由包名派生，不再写成常量。",
    ),
    LicenseEntry(
        name = "PRoot",
        license = "GPL-2.0+",
        version = "5.1.107.102",
        group = "运行环境",
        author = "PRoot / Termux Contributors",
        homepage = "https://github.com/termux/proot",
        detail = "用户态 chroot/bind 挂载实现（libproot.so 与 libprootloader.so，静态链接 talloc），" +
            "作为独立子进程在无 Root 环境下承载 Ubuntu 24.04 容器。",
    ),
    LicenseEntry(
        name = "Ubuntu 24.04 容器镜像与内置 Node.js / Pi 运行时",
        license = "各组件各自的许可",
        version = "Ubuntu 24.04 · Node 22 · Pi 1.6.1",
        group = "运行环境",
        author = "Canonical / Termux / OpenJS / Earendil Works",
        homepage = "https://github.com/earendil-works/pi",
        detail = "OOBE 引导从清华大学 TUNA、中科大 USTC 或 GitHub (termux/proot-distro) 按需云端下载 Ubuntu 24.04 LTS arm64 根文件系统，" +
            "并叠加 APK 内置 Node.js v22.23.5（MIT）与 @earendil-works/pi-coding-agent 1.6.1（MIT），以独立子进程运行。",
    ),
    LicenseEntry(
        name = "XZ for Java (org.tukaani:xz)",
        license = "0BSD / Public Domain",
        version = "1.12",
        group = "运行环境",
        author = "Lasse Collin (Tukaani Project)",
        homepage = "https://tukaani.org/xz/java.html",
        detail = "纯 Java XZ/LZMA2 流式解压实现，用于解压 GitHub proot-distro 源提供的 Ubuntu 24.04 `.tar.xz` 根文件系统。",
    ),
    LicenseEntry(
        name = "Miuix",
        license = "Apache-2.0",
        version = "0.9.4",
        group = "界面框架",
        author = "compose-miuix-ui",
        homepage = "https://github.com/compose-miuix-ui/miuix",
        detail = "整个界面的组件库与设计语言：ui / core / icons / preference / shader / " +
            "squircle / nav / blur-android 八个 artifact。不修改、不内联源码，许可随构件分发。" +
            "本工程的 OS3 渐变背景、磨砂玻璃、圆角方块与导航转场都由它提供。",
    ),
    LicenseEntry(
        name = "Jetpack Compose",
        license = "Apache-2.0",
        version = "随构架落版",
        group = "界面框架",
        author = "Android Open Source Project",
        homepage = "https://developer.android.com/jetpack/compose",
        detail = "全部界面代码的基础：声明式 UI、状态快照、布局与绘制管线。" +
            "由 Compose BOM 统一落版，本工程不直接钉版本号。",
    ),
    LicenseEntry(
        name = "AndroidX",
        license = "Apache-2.0",
        version = "随构架落版",
        group = "界面框架",
        author = "Android Open Source Project",
        homepage = "https://developer.android.com/jetpack/androidx",
        detail = "activity-compose（返回手势与 activity 结果通道）、" +
            "lifecycle-viewmodel-compose 与 lifecycle-runtime-compose（状态与协程作用域）、" +
            "profileinstaller（把 baseline.prof 交给 ART）。均为 Apache-2.0。",
    ),
    LicenseEntry(
        name = "Mochi Compose",
        license = "Apache-2.0",
        version = "0.3.0",
        group = "界面框架",
        author = "feice11",
        homepage = "https://github.com/feice11/mochi-compose",
        detail = "提供 iOS 26 式列表/菜单、组件（mochi-compose / mochi-icons）与 G2 连续圆角的组件库。" +
            "已知缺口：它是按 backdrop 1.0.6 编译的，而本工程显式用了 2.0.1；" +
            "目前尚未调用它的玻璃组件，一旦启用需先核对方法签名，否则会是 NoSuchMethodError。",
    ),
    LicenseEntry(
        name = "AndroidLiquidGlass · backdrop",
        license = "Apache-2.0",
        version = "2.0.1",
        group = "界面框架",
        author = "Kyant0",
        homepage = "https://github.com/Kyant0/AndroidLiquidGlass",
        detail = "液态玻璃的取样 / 折射 / 高光 / 内外阴影基础设施（不含现成组件）。" +
            "显式声明而**不**依赖 Mochi 的传递依赖，因为版本由消费方决定，需要一处可检查的意图。" +
            "本工程 `ui/effects/Lens.kt` 与 `Vibrancy.kt` 是它的移植，跑在 miuix-blur 的管线之上。",
    ),
    LicenseEntry(
        name = "Rosemoe Sora Editor",
        license = "LGPL-2.1",
        version = "0.24.6",
        group = "编辑器与语法",
        author = "Rosemoe",
        homepage = "https://github.com/Rosemoe/sora-editor",
        detail = "编辑器使用它的增量文本布局、缩放、滚动与 TextMate 语法高亮；未复制其源码。" +
            "已知缺口：LGPL 通常还涉及「允许使用者替换为修改后的库版本」这一条，" +
            "本工程尚未评估这一点。",
    ),
    LicenseEntry(
        name = "TextMate 语法、语言配置与主题",
        license = "MIT / Apache-2.0",
        version = "—",
        group = "编辑器与语法",
        author = "Microsoft 等",
        homepage = "https://github.com/microsoft/vscode-textmate",
        detail = "随应用分发的数据文件（assets/sora/textmate）。来源包含 Visual Studio Code、" +
            "vscode-kotlin、vscode-logfile-highlighter 等上游项目，已核实的四项均为 MIT；" +
            "另有若干项尚未核到来源或许可。逐项清单在那份文件自带的 NOTICE.md 里。",
    ),
    LicenseEntry(
        name = "Shizuku",
        license = "Apache-2.0",
        version = "13.1.5",
        group = "功能库",
        author = "Rikka",
        homepage = "https://github.com/RikkaApps/Shizuku",
        detail = "无 Root 时拿一个 shell 身份来跑 pm / am / dumpsys / input 这类系统命令。" +
            "api 提供授权申请与状态查询，provider 负责接收管理端投递的 shell 服务 Binder。" +
            "不声明那个 provider，`Shizuku.pingBinder()` 永远是 false。",
    ),
    LicenseEntry(
        name = "JLaTeXMath Android",
        license = "GPL-2.0（Classpath 例外）",
        version = "0.2.0",
        group = "功能库",
        author = "noties",
        homepage = "https://github.com/noties/jlatexmath-android",
        detail = "对话里的数学公式渲染。它是 `org.scilab.forge:jlatexmath` 的 Android 移植，" +
            "把上游画到 AWT 的那套换成 `android.graphics.Canvas`。" +
            "自写排版引擎不可行 —— 上下标、分式、根式、矩阵、括号伸缩的规模远超本项目。",
    ),
    LicenseEntry(
        name = "Material Symbols 图标",
        license = "Apache-2.0",
        version = "—",
        group = "资源与致谢",
        author = "Google",
        homepage = "https://fonts.google.com/icons",
        detail = "不是依赖，而是把上游字形的路径数据拷进了源码（ZhiMaterialIcons.kt）。" +
            "路径数据本身逐字未改，只多了一层视口换算。仓库里有重新取用的脚本，" +
            "重跑后 git diff 应为空 —— 那是核对「文件里这份数据还是上游那份」的方法。",
    ),
    LicenseEntry(
        name = "MaterialFiles 交互参考",
        license = "GPL-3.0 · 未复制源码",
        version = "—",
        group = "资源与致谢",
        author = "zhanghai",
        homepage = "https://github.com/zhanghai/MaterialFiles",
        detail = "本工程参考它的独立文件页与顶部层级交互形态，**未复制**其任何源码或资源。" +
            "因此这一项是设计参考，不是随发行物链接或内嵌的第三方代码。",
    ),
    LicenseEntry(
        name = "CC Switch",
        license = "MIT",
        version = "—",
        group = "资源与致谢",
        author = "farion1231 (Jason Young)",
        homepage = "https://github.com/farion1231/cc-switch",
        detail = "借鉴并适配其 Pi 多供应商预设清单、thinkingLevelMap 推理档位配置，" +
            "以及供应商列表使用的品牌矢量图标资源（ic_provider_*.xml 与 ProviderBrandIcons.kt）。",
    ),
    LicenseEntry(
        name = "DeepSeek Harness 交互参考",
        license = "MIT · 未复制源码",
        version = "—",
        group = "资源与致谢",
        author = "DeepSeek",
        homepage = "https://github.com/deepseek-ai/deepseek-harness",
        detail = "侧边栏按工作区目录聚合会话列表及每组默认折叠保留最近 5 条的视觉结构参考，" +
            "基于 Compose / Miuix 独立实现，未复制其 TypeScript 或前端源码。",
    ),
    LicenseEntry(
        name = "ZhiCode 自身",
        license = "MIT",
        version = "—",
        group = "起点与致谢",
        author = "zhizhu0002",
        homepage = PROJECT_URL,
        detail = "Compose 界面层、沙箱宿主层、引擎与工具集、诊断与安全边界。" +
            "第三方组件**不受**该许可约束；分发时必须同时提供 LICENSE、NOTICE 与 " +
            "THIRD-PARTY-LICENSES/ 三份文件。",
    ),
)

/** 本工程自身的许可（「关于」页那一行显示它）。 */
const val OWN_LICENSE = "MIT"

/* `PROJECT_URL` 已搬到 `model/ProjectRepo.kt`。 */

/** 赞助页（爱发电）。关于页「应用」分组里的「赞助」指向这里。 */
const val DONATE_URL = "https://afdian.com/a/zhizhu0001"

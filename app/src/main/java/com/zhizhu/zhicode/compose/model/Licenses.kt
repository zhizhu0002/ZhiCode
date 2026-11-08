package com.zhizhu.zhicode.compose.model

/**
 * 第三方组件清单（「关于 → 第三方组件许可」的数据源）。
 *
 * ## 为什么不是一个依赖
 *
 * rikkahub 与 Miuix 示例都用 `aboutlibraries` 插件在构建期扫依赖、生成 JSON。
 * 本工程**不引入**它，理由有三，且都是具体的：
 *
 * 1. 这个环境是离线的（`--offline` 构建），新插件拉不下来；
 * 2. 它只扫得到**声明在 Gradle 里**的依赖，而本工程最需要声明的几项恰恰不是依赖：
 *    `libtermux.so`、Termux bootstrap 归档、
 *    `assets/sora/textmate` 的数据文件、内联进 `ZhiMaterialIcons.kt` 的图标路径；
 * 3. 许可名仍然要人写映射（插件给的是坐标，不是中文可读的许可条款名）。
 *
 * 所以这里手写一份**可核对**的清单：每条的 [name] 必须能在根目录 `NOTICE` 里找到同名小节
 * （`LicensesTest` 会逐条比对这个字符串），[license] 必须与 `NOTICE` 里那一节声明的一致。
 * 手写的东西靠测试与源文档对齐，而不是靠"我记得"。
 *
 * ## 字段含义
 *
 * @property name 组件名（= `NOTICE` 里的小节标题，去掉编号）
 * @property license 许可（与 `NOTICE` 一致）
 * @property version 版本或"随发行物"一类的说明；没有版本的写 `—`
 * @property detail 给用户看的补充：这项为什么在这里、有没有改动、有什么已知缺口
 * @property author 上游作者/组织。列表行里与 [license] 拼成一行摘要
 *   （`author · license`），扫读时先看"谁的、什么许可"再决定点不点进去。
 * @property homepage 上游主页。**没有可核实地址时给 null**，界面就不画箭头、
 *   点击改为弹出 [detail] —— 宁可少一个箭头，也不编一个打不开的链接。
 * @property group 分组名。同一组的条目在界面上合成一张卡，组标题浮在卡片之上。
 *   分组按**首次出现顺序**渲染，所以这里的条目顺序同时决定了组顺序。
 */
data class LicenseEntry(
    val name: String,
    val license: String,
    val version: String,
    val detail: String,
    val group: String,
    val author: String,
    val homepage: String? = null,
)

/**
 * 全部第三方组件。
 *
 * <p>顺序与根 `NOTICE` 的小节顺序一致 —— 这不是巧合，而是刻意的：
 * 两份文档并排看时顺序一致，核对的人就不用来回跳。
 */
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
        name = "Termux 用户空间（bootstrap 归档）",
        license = "各程序各自的许可",
        version = "—",
        group = "运行环境",
        author = "termux",
        homepage = "https://github.com/termux/termux-packages",
        detail = "归档内是数百个独立可执行程序，各自适用各自的许可，其中包含 GPL-3.0（bash / coreutils / " +
            "sed / grep）、GPL-2.0+（apt / dpkg）、Apache-2.0（openssl）以及 LGPL / MIT / BSD 变体。" +
            "它们是**独立可执行文件**，由本应用以子进程方式启动，与本工程代码不构成衍生作品 —— " +
            "GPL 明确允许把它们与其它许可的程序聚合分发。",
    ),
    /*
     * ⚠️ 这一组原来只有一条「通过 Gradle 依赖引入的组件」，把八个库糊在一段文字里。
     * 那对用户没有用 —— 想确认"我看到的这个模糊效果是谁做的、什么许可"，
     * 得先去读一段长句。现在按 `app/build.gradle` 里**真实声明**的依赖逐条拆开，
     * 每条自带作者与主页。
     *
     * 名字必须与根 `NOTICE` 第 6 节逐条对应（`LicensesTest` 会核对）。
     */
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
        version = "0.2.0",
        group = "界面框架",
        author = "feice11",
        homepage = "https://github.com/feice11/mochi-compose",
        detail = "提供 iOS 26 式列表/菜单与 G2 连续圆角的组件库。" +
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

/*
 * `PROJECT_URL` 已搬到 `model/ProjectRepo.kt`。
 *
 * 它原来就在这一行，和 `THIRD_PARTY_LICENSES` 摆在一起 —— 只有「关于页的外链」
 * 一个使用者时那样没问题。现在「检查更新」也要用它派生 API 地址与 owner/repo，
 * 两处各写一份迟早会指向不同仓库（比如界面指向 A、更新检查指向 B），
 * 所以收成一处。见 `ProjectRepo.kt` 头部关于「与 IQ Code 那一项的区别」的说明。
 */

/** 赞助页（爱发电）。关于页「应用」分组里的「赞助」指向这里。 */
const val DONATE_URL = "https://afdian.com/a/zhizhu0001"

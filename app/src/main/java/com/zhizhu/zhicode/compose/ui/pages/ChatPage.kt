package com.zhizhu.zhicode.compose.ui.pages

import com.zhizhu.zhicode.compose.ui.composer.ComposerTextState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.snapshotFlow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.chat.AgentProgressCard
import com.zhizhu.zhicode.compose.ui.chat.ChatList
import com.zhizhu.zhicode.compose.ui.composer.Composer
import com.zhizhu.zhicode.compose.ui.FloatingBottomShell
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.MessageBar
import com.zhizhu.zhicode.compose.ui.TopBarInsetCompact
import com.zhizhu.zhicode.compose.ui.ZhiAnchoredActionMenu
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 系统文件管理器那个选择器接受的全部 MIME 类型。
 *
 * ⚠️ 这里是**拼**出来的，不是直接写那个字面量，而且不能"顺手改回去"。
 *
 * 那个字面量（任意主类型 + 斜杠 + 任意子类型）里含有**斜杠紧跟星号**这个序列。
 * 而 `app/tests/` 下 64 个测试各自复制了一份 `stripComments`，它的实现是按正则
 * 删块注释（两种注释各一条正则）。正则不认识字符串字面量，于是字面量里的那两个
 * 字符会被当成块注释的开头，一路吞到之后第一个「星号紧跟斜杠」，
 * **把中间一大段源码整块删掉**。
 *
 * 实测过：写成字面量时，本文件从那一行往后的内容在测试眼里全部消失，
 * 断言报「找不到 OpenMultipleDocuments」而源码明明就在那儿。
 * 同一个坑也存在于 `SkillsOverlay.kt`（导入技能时那组 MIME 字面量），只是它
 * 被删掉的那一段恰好没有断言要检的 token，所以一直没暴露。
 *
 * 用「两段星号 join 起来」既避开了那个序列，又保住了语义（任意主类型 / 任意子类型）。
 * 不用文本类型收窄是另一个理由：手机自带的文件管理器（含 MIUI 的）对文本类型的
 * 过滤常常把 `.md`、`.json`、无扩展名的配置文件一起藏掉，而那几个恰恰是最常见的
 * 附加对象。非文本的会被 `attachDocument` 里的 NUL 判定拒掉并说明原因 ——
 * 比"文件在选择器里根本看不见"好排查。
 *
 * ⚠️ 这段注释里**也不能**把那条正则原样写出来：它的结尾就是「星号紧跟斜杠」，
 * 会直接把这层 KDoc 提前闭合（实测过，编译器报一串 "Expecting a top level declaration"）。
 */
private val SystemFileMimeTypes = arrayOf(listOf("*", "*").joinToString("/"))

/**
 * 相册选择器接受「图片」这一大类，同样**拼**出来。
 *
 * ⚠️ 理由与 [SystemFileMimeTypes] 完全相同：图片主类型后面跟一个斜杠加星号
 * 会被那些正则当成块注释的开头，把本文件后面一大段源码整块删掉
 * （实测：`onPickImage` 之后的内容在测试眼里全部消失）。
 */
private val ImageMimeTypes = arrayOf(listOf("image", "*").joinToString("/"))

/** （从 `AppScaffold.kt` 原地拆出，内容逐字未改。） */
@Composable
internal fun ChatPage(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    modifier: Modifier = Modifier,
    glass: Glass,
) {
    // 这张卡是「任务进度」，所以**只在真有任务时**才出现。
    //
    // 历史与取舍（两次改动方向相反，别再来回改）：
    //  1. 最初是 `workingStatus != null || tasks.isNotEmpty()` —— 于是 AI 只要用
    //     TaskCreate 列过一次清单，答完之后卡还会一直挂着。
    //  2. 改成只看 `workingStatus != null` —— 又走到另一个极端：单纯「正在思考…」
    //     时也会弹出一张「任务进度 0 / 0」，一个任务都没有却叫"任务进度"，是噪音。
    //  3. 现在只看 `tasks.isNotEmpty()`：没任务就不显示，列出任务后显示，与原版的
    //     多步进度能力一致。
    //
    // 注意 `workingStatus` 因此不再有专门的展示位（它是"正在思考…/正在执行 X…"这类
    // 进行时文案）。当前是否在跑由输入器右侧的停止键表达。
    ZhiFrameTrace.countRecompose("ChatArea")
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // 只清理首帧系统/Compose 自动分配的焦点；用户点击输入框后的焦点不受影响。
        focusManager.clearFocus(force = true)
    }
    val floating = state.tasks.isNotEmpty()

    // 对话区**不加框**（改过两次，别再加回来）。
    //
    // 原来这里套了一层 `Surface(border = BorderStroke(1.dp, dividerLine))`，
    // 外加 6dp 四周留白，理由是"纯色背板上没有边界会让对话与面板糊成一片"。
    // 实际看下来这条线是**多余的视觉噪音**：面板底色与消息卡片本身已经有对比，
    // 而这条线紧贴屏幕边缘，看着像一个白加的容器。去掉后对话直接铺满面板。
    //
    // ⚠️ 不要顺手把 6dp 留白搬到下面的 Box 上：那个留白原本只为了让线不贴边，
    // 线没了留白就没意义了（`ChatFramePadding` 已一并删掉）。
    Box(modifier = modifier.fillMaxSize()) {
        // 底部悬浮层（任务卡 + 输入器）的高度，用来给对话列表留白。
        //
        // ⚠️ 以前这里是**两个写死的常量**（输入器 92dp + 任务卡 140dp）。写死的代价是
        // 它永远不会跟着内容变：输入器多长一行、挂了附件条、开了调试模式的 Markdown
        // 实时预览、或者任务卡里任务变多 —— 悬浮层就变高，而预留的留白不变，
        // 于是"滑到最底部还有内容被遮住"。
        //
        // 现在改成**实测**：悬浮那一列自己 onSizeChanged 报高度，对话列表按它留白。
        // 这不是循环依赖 —— 悬浮列在 Box 里独立于列表（列表 fillMaxSize），
        // 它的高度不受 bottomInset 影响，所以量一次就稳定。
        //
        // 初始值给一个够用的下限（首帧还没量到），避免第一帧底部贴太紧。
        //
        // ## ⚠️⚠️ 高度必须分成**两段**，这是实测出来的性能问题，别合回去
        //
        //   · **内容高度** = 任务卡 + 反馈条 + 输入器自己的高度。只有它们真的变高/变矮才变。
        //   · **IME 抬起量** = 键盘从屏幕底边长上来的那一段。**键盘动画期间它每帧都变。**
        //
        // 以前两段是**合在一起**的：`onSizeChanged` 挂在 `padding(bottom = imeLift)` 左边，
        // 报出来的高度含 IME。于是键盘一动 → 高度每帧变 → ChatArea 每帧重组 →
        // `bottomInset` 每帧变 → ChatList（参数里有它）每帧重组。
        //
        // 这不是推理，是量到的：探针记录过 `recompose/s ChatArea=69 ChatList=64`
        // （60Hz 下就是每帧一次），而**同一行的 `Composer` 只有 1** —— 因为 Composer 的
        // 参数里**没有** bottomInset。`FloatingCard` 更是从未出现在日志里。
        // 这组数字正好是这个机制的指纹：只有链路上"参数含 bottomInset"的那两级在抖。
        //
        // 拆开之后：内容高度走状态（低频），IME 抬起量走**布局阶段**的 lambda
        // （见下面 `offset {}` 处），键盘动画期间只让布局失效、**不触发重组**。
        var floatingContentHeightPx by remember { mutableStateOf(0) }

        // IME 抬起量「停稳」之后才落地，专供列表底部留白。
        //
        // 为什么列表这份要去抖、而悬浮层的位移不去抖：悬浮层要**贴着键盘动**（那是手感，
        // 差一帧都能看出来），而列表的底部留白只解决"最后一条别被挡住"，
        // 键盘动画途中它跟不跟手**完全看不出来**。于是把逐帧的那份只留给布局，
        // 把列表这份压成低频 —— 重组次数就从"每帧"降到"每次键盘开合一两次"。
        var composerFocused by remember { mutableStateOf(false) }
        val imeMotion = LocalImeMotion.current
        val density = LocalDensity.current
        // 全屏浮层盖住工作区时，窗口级 IME 属于浮层里的输入框，不应抬起后面的聊天输入器。
        val liftByFullScreenOverlay = state.sidebarOpen || state.settingsOpen ||
            state.uiDebugOpen || state.environmentOpen
        val settledImeLiftPx = if (liftByFullScreenOverlay) 0 else imeMotion.settledLiftPx

        // 悬浮胶囊的让位量。提前取到这里，因为 `bottomInset` 与下面那一列的
        // `padding(bottom = settleBar)` 必须用**同一个值**（见 bottomInset 的说明）。
        val settleBar = com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset.current
        // 悬浮栏隐藏进度（补间后的显示值）。只在**绘制阶段**读它，不参与布局。
        val navBarProgressState = com.zhizhu.zhicode.compose.ui.LocalNavBarProgress.current

        /*
         * 固定底栏占掉的底部高度（悬浮模式下是 0）。
         *
         * 在**组合期**换算成 px 是可以的：它只在底栏形态/主题切换时变，
         * 键盘动画期间完全不变。所以不需要下沉到布局阶段。
         */
        val bottomBarPx = with(density) {
            com.zhizhu.zhicode.compose.ui.LocalBottomBarInset.current.roundToPx()
        }

        val bottomInset = with(density) {
            // 悬浮胶囊（`LocalFloatingNavInset`）现在**不让布局**了 —— 内容铺到屏幕底边，
            // 所以输入器必须自己让开它，并且列表留白里也要算上它，否则最后一条消息
            // 会停在胶囊底下。非悬浮模式这个值是 0，行为与以前一致。
            //
            // ⚠️⚠️ 这里**必须**再加上 `settleBar`。这是一个实测出来的真实漏算：
            //
            // 下面那一列用的是 `padding(bottom = settleBar)`，而 `onSizeChanged`
            // 挂在这个 `padding` **之后** —— 它量到的是**内容尺寸**，padding 不计入。
            // 于是 `floatingContentHeightPx` 少了 `settleBar` 那一段。
            //
            // 实测（探针打印）：`floatingContentHeightPx` = 664px，而悬浮列的顶边在
            // 1511px、底边在 2175px、窗口底 2400px —— 列的真实占位是 1511→2400
            // （889px），留白却只按 664px + gap 算。差额 225px 恰好等于 `settleBar`
            // （80dp × 2.75 ≈ 220px）。
            //
            // 后果就是用户反复报的「消息被任务卡覆盖」：列表按偏小的留白贴底，
            // 最后几条停在 1511px 以下、被悬浮层盖住 —— 截图上「帧耗时」消息卡
            // 的第二行被「任务进度」上沿切掉，就是这个差额。
            //
            // 原先这里的注释写着"输入器那一列已经把这个 inset 算进自己的高度"，
            // 那个判断是错的，别再据此把 `settleBar` 删掉。
            ((floatingContentHeightPx + settledImeLiftPx).toDp() + settleBar + FloatingBottomGap)
                .coerceAtLeast(MinFloatingInset)
        }

        ChatList(
            state = state,
            onToggleTool = viewModel::toggleToolExpanded,
            // 组键由界面按 toolId 推导（见 ToolGrouping），所以这里直接把它交给 VM ——
            // 以前要在这里先把整批的 toolIds 算出来当"要收起的成员集合"，那是把
            // 界面的分组结构算进了 ViewModel 的记账方式里。
            onToggleGroup = viewModel::toggleGroupExpanded,
            onToggleThinking = viewModel::toggleThinking,
            onMessageActions = viewModel::showMessageActions,
            // 单个工具的 ⋯ 菜单里选中了一项：动作作用在某一行上，所以把 toolId 一起传下去。
            // 传的是**消息 id**（不是 ChatItem）—— VM 只需要一个能在 transcript 里定位的键，
            // 拿整条消息进去反而会让"动作作用在旧快照上"变得可能。
            onToolAction = { item, toolId, label -> viewModel.applyToolAction(item.id, toolId, label) },
            // 多选导出：勾选、取消、导出三个动作都只改状态层，界面按状态重绘。
            onToggleSelect = viewModel::toggleMultiSelect,
            // 拖动对选：整段区间一次性替换选中集合（见 ChatList 的手势说明）。
            onRangeSelect = viewModel::setMultiSelectRange,
            onCancelSelect = viewModel::cancelMultiSelect,
            // 导出：打开导出面板（格式与选项在面板里选，照 rikkahub 的 ChatExportSheet）。
            onExportSelect = viewModel::openExportSheet,
            onToggleSelectAll = viewModel::toggleSelectAll,
            // 长按消息的动作菜单：由那一条消息自己渲染（从手指位置长出来）
            anchoredMenu = { anchorId, fingerOffset ->
                ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
            },
            // 列表滚到顶时往前补一页更早的历史。恢复历史是分页的，重复调用由
            // ViewModel 里的守卫挡掉（"正在加载"/"已经没有更早的"）。
            onLoadOlder = viewModel::loadOlderTranscript,
            // 常驻窗口的尾部被淘汰过时，末尾的「回到最新」和"发送时人还在历史里"
            // 都会走到这里（用最新一页替换整份 transcript）。
            onLoadLatest = viewModel::jumpToLatest,
            modifier = glass.capture(Modifier.fillMaxSize()),
            // 底部留白 = 实测的悬浮层高度 + 一点余量：让被盖住的内容也能滑上来。
            // 顶部留白：S1 重构后顶栏在 topBar 槽位已由 Scaffold padding 处理，
            // 对话列表不再需要让出头部高度，可从 Scaffold padding 顶部起排。
            bottomInset = bottomInset,
            imeOpenGeneration = if (liftByFullScreenOverlay) 0 else imeMotion.openGeneration,
            composerFocused = composerFocused,
            // 首条消息落在顶栏（含 Tab 行）下缘；列表全高，滚动时消息从顶栏 blur 下穿过
            topInset = TopBarInsetCompact,
            // 主体调试模式：在真实消息上就地显示类型/长度/工具计数 + Markdown 源码开关；
            // 并把每条消息的细节默认全展开、任务清单内联进对话流（见 ChatList 的同名参数）
            debugMode = state.debugAppMode,
            // 内联任务清单的数据：调试模式才传，非调试时是空列表（不渲染）。
            debugTasks = if (state.debugAppMode) state.tasks else emptyList(),
        )

        // 输入法弹出时，悬浮层要往上顶多少。
        //
        // ## 为什么不能直接用 `Modifier.imePadding()`
        //
        // 键盘是从**屏幕底边**长上来的，它的高度里包含导航栏那一段；而 `AppScaffold`
        // 已经在内容容器底部让出了导航栏（`padding(bottom = padding.calculateBottomPadding())`，
        // 来源是 Miuix Scaffold 的 `contentWindowInsets = systemBars ∪ displayCutout`）。
        // 直接 `imePadding()` 等于垫两次，输入框会停在键盘上方空出**一条导航栏的缝**。
        //
        // 所以要的是差值：窗口高 H，键盘顶边 y = H − ime，内容容器底边 y = H − navBar，
        // 中间要补的空隙 = (H − navBar) − (H − ime) = ime − navBar。
        // 夹到非负是必须的 —— 键盘收起时 IME 是 0，差值为负，
        // 负数内边距会把输入框推到屏幕外面去。
        //
        // ## ⚠️ 有全屏浮层时**必须不抬**（用户实测报的 bug）
        //
        // `WindowInsets.ime` 是**窗口级**的：键盘是谁提起来的它不区分。侧栏抽屉
        // （窄屏，画在 Scaffold 之上）里有一个「搜索会话」输入框，点它提键盘时
        // IME 同样变正 —— 于是**后面那个对话输入器会一起抬起来**，从抽屉右边露出来的
        // 那条缝里就能看见它整个上移了一截（用户原话：
        // 「为什么在搜索会话打开输入法，后面的聊天发送框会自动抬起」）。
        //
        // 这里没有去猜"焦点在谁身上"（Compose 没给可靠的窗口级焦点查询），而是直接
        // 看**有没有全屏浮层盖住工作区**：盖住时那一次 IME 变化必然不属于本输入器。
        // 判断结果是 `liftByFullScreenOverlay`，它同时决定两件事 —— `offset { }` 里
        // 抬不抬，以及列表留白里算不算键盘那一段（见上面 LaunchedEffect 的首个分支）。
        //
        // 这段 IME 抬起量的**计算已上移**，与内容高度放在一起 —— 见那里的说明。
        //
        // 底部悬浮层：任务卡在上、输入器在下，两者都不占布局高度，
        // 所以对话区始终铺满，且它们不随对话滚动。
        // 对应原版把 AgentProgressView + composerHost 放进位于 chatScroll
        // 之外的 chatBottomHost（MainActivity.java:1325-1331）。
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 悬浮栏隐藏时，这一列（任务卡 + 输入器）**向下填满胶囊腾出的空间**。
                //
                // ⚠️ 位移量 = `settleBar`（= 胶囊让位量），**不是**它加上自身高度。
                // 曾经写成 `(FloatingNavBarHeight + settleBar) * progress`，
                // 于是胶囊藏好时输入器被推出屏幕、底部留下一条残片 —— 用户截图看得很清楚。
                //
                // 为什么 `settleBar` 就够：这一列本来靠 `padding(bottom = settleBar)`
                // 坐在胶囊**上方**。胶囊滑走后，把这块内边距"视觉上抵消掉"，
                // 内容就正好贴到屏幕底部，不多不少。
                //
                // ⚠️ 必须是 `graphicsLayer`（绘制阶段）而不是 `padding`/`offset`：
                // 布局变化会改这一列的测量高度，进而改 `floatingContentHeightPx`
                // → 改列表 `bottomInset` → 改列表滚动位置 → 又写回进度，形成回路，
                // 表现就是"下滑时弹一下"。
                .graphicsLayer {
                    val progress = navBarProgressState.value
                    if (progress > 0f) translationY = settleBar.toPx() * progress
                }
                // 悬浮胶囊的高度由这一列自己让开：内容层现在铺到屏幕底边，
                // 所以这里必须留出胶囊的位置，否则输入器会被胶囊压住。
                // 放在 `offset{}` / `onSizeChanged` **左边**，让量到的高度含这一截 ——
                // 列表留白正是照着这个高度算的。
                .padding(bottom = settleBar)
                // ## 输入法抬起：为什么是 `offset { }` 而不是 `padding(bottom = …)`
                //
                // 先把"必须自己接"这件事说清楚，因为这决定了下面为什么不能用现成机制：
                // 清单里确实是 `windowSoftInputMode="adjustResize"`，但 `MainActivity`
                // 在 `onCreate` 里调了 `enableEdgeToEdge()` —— 它等价于
                // `setDecorFitsSystemWindows(false)`：DecorView 不再消费系统窗口 inset，
                // 系统那套"把窗口缩小让出键盘"也随之失效，IME 的高度只能由应用自己接。
                //
                // 参考实现（反编译版 `installKeyboardMotion()`，MainActivity.java:1043-1115）
                // 同样 `setDecorFitsSystemWindows(false)`，然后自己挂
                // `setOnApplyWindowInsetsListener` + `WindowInsetsAnimation.Callback`，
                // 把 `getInsets(Type.ime()).bottom` 一路 `applyKeyboardOffset(inset)`
                // 顶到根布局上 —— 它从来没依赖过 adjustResize。
                //
                // 那段高度**每帧都在变**（键盘动画），所以关键不是"接到它"，而是
                // "接到它而**不重组**"。两条路的差别：
                //
                //   · `padding(bottom = imeLift)` —— `imeLift` 是 `Dp`，必须在**组合期**
                //     算出来才能传进去。于是每帧重组 → 每帧重建 Modifier 链。
                //   · `offset { IntOffset(0, -lift) }` —— lambda 在**布局阶段**求值。
                //     在 lambda 里读 inset，每帧只让布局失效（重新摆放这一次），
                //     组合一次都不跑。
                //
                // 视觉等价：`padding` 是把内容从底边内缩，`offset` 是把整块往上摆，
                // 而这块本来就贴在 BottomCenter，所以往上摆 = 让出键盘那一段。
                .offset {
                    /*
                     * ⚠️ 别把这个读操作挪到组合里（比如 `val lift by remember { … }`），
                     * 一挪回组合就又变成每帧重组了 —— 那正是这次要修的东西。
                     *
                     * ## 为什么是 `max(0, lift - bottomBarPx)` 而不是 `-lift`
                     *
                     * 这一列贴在**内容盒**底部，而内容盒底部已经让出了底栏
                     * （`padding(bottom = 底栏高度)`，悬浮模式下是 0）。
                     * 所以"往上抬 lift"实际会把输入器抬到 `底栏上沿 - lift`。
                     *
                     * 键盘完全弹开时 `lift` 很大，这样没问题。但**键盘收起动画的末尾**
                     * `lift` 已经回到 0，而这一列仍贴在底栏上沿 —— 看起来没毛病。
                     * 真正的坑在另一头：键盘**刚收起、抬起量还没归零**那几帧，
                     * 输入器会一路落到底栏上沿，而底栏本身就在屏幕最底部，
                     * 于是输入器与键盘之间、或输入器与底栏之间会错位（用户截图：
                     * 键盘收起瞬间输入器压在 NavigationBar 上）。
                     *
                     * 取 `max(0, lift - bottomBarPx)` 之后：
                     *   · lift = 0            → 位移 0，停在底栏上沿（正确落点）；
                     *   · lift = 底栏高度     → 位移 0，仍停在底栏上沿（键盘正好盖住底栏，二者重合）；
                     *   · lift = 900（全开）  → 位移 900 - 182，正好贴键盘顶边。
                     * 也就是「输入器底边 = max(键盘顶边, 底栏上沿)」，连续、无跳变。
                     */
                    val lift = if (liftByFullScreenOverlay) 0 else imeMotion.currentLiftPx
                    IntOffset(0, -maxOf(0, lift - bottomBarPx))
                }
                // 实测**内容**高度（任务卡 + 反馈条 + 输入器自己）回报给上面的 bottomInset。
                // `onSizeChanged` 只在尺寸真的变了时回调，所以正常的打字/滚动不产生额外开销。
                //
                // ⚠️⚠️ 它在 `offset { }` **右边**，量到的才是内容高度。这个位置是必须的：
                // 换成 `padding` 时，让给键盘的那段会被算进**节点尺寸**，于是这里报的高度
                // 含 IME、每帧都变 → 上面的 bottomInset 每帧变 → ChatList 每帧重组。
                // `offset` 只改摆放、不参与尺寸，所以量出来只剩内容，尺寸稳定。
                .onSizeChanged {
                    // 曾在 DEBUG 下顺带写 `nav-probe chat-floating` 日志，用来核对
                    // "内容让位高度 = 底部栏实际高度"。结论已确认，探针删除：
                    // 它每次尺寸变化都执行，而尺寸在键盘/悬浮栏动画期间频繁变化。
                    floatingContentHeightPx = it.height
                },
        ) {
            AnimatedVisibility(
                visible = floating,
                enter = slideInVertically(ZhiMotion.enterSpec) { it / 2 } +
                    fadeIn(ZhiMotion.fadeInSpec),
                exit = slideOutVertically(ZhiMotion.exitSpec) { it / 2 } +
                    fadeOut(ZhiMotion.fadeOutSpec),
            ) {
                FloatingAgentStatus(
                    status = state.workingStatus.orEmpty(),
                    tasks = state.tasks,
                    onExpand = viewModel::openTaskList,
                    wide = wide,
                    glass = glass,
                )
            }

            // 操作反馈条：夹在任务卡与输入器之间。
            //
            // 为什么放这一层而不是 SnackbarHost：Snackbar 是覆盖在内容之上的浮层，
            // 会压住底部输入器（这正是它当初被删掉的原因）；而放在这个 Column 里
            // 它参与布局，长高只会把输入器往上顶，永远不遮挡。
            MessageBar(
                message = state.message,
                isError = state.messageIsError,
                onDismiss = { viewModel.clearMessage() },
                wide = wide,
                glass = glass,
            )

            // 悬浮输入器
            ComposerHost(
                state = state,
                viewModel = viewModel,
                wide = wide,
                glass = glass,
                onFocusChanged = { composerFocused = it },
            )
        }
    }
}

/**
 * 悬浮层之上再留的余量。
 *
 * 不只是为了好看：输入器自带投影与圆角，内容贴着它上缘会显得被"压"住；
 * 留 8dp 之后最底部那条消息与输入器之间有一条干净的缝。
 */
private val FloatingBottomGap = 8.dp

/**
 * 实测值到手之前的兜底下限。
 *
 * 首帧 `onSizeChanged` 还没回调，若此时按 0 留白，用户会看到内容"先贴底、再弹上来"。
 * 取一个偏小的值（不是旧的 92dp）：宁可第一帧略紧，也不要一开始就凭空多出一大块空白。
 */
private val MinFloatingInset = 72.dp

/**
 * IME 抬起量「停稳」的判定窗口（毫秒）。
 *
 * 列表底部留白用的是一个去抖后的 IME 值：键盘动画期间值一直在变，连续这么长时间
 * 没有新值才写一次状态。取值理由：
 * · **不能太长** —— 键盘弹起动画约 200~300ms，等太久会让"最后一条消息滑上来"明显滞后。
 * · **不能太短** —— 太短就等于没去抖，又退回每帧重组。
 *
 * 120ms 落在键盘动画的帧间隔之外（60Hz 一帧 16.7ms，120ms ≈ 7 帧没有新值才算停），
 * 键盘在动的时候绝不会被判定为停稳。
 */

/**
 * 悬浮的任务与状态卡。
 *
 * 原版 `AgentProgressView` 同时承载任务清单与当前工作状态
 * （`agentProgressView.update(workingStatus, ...)`，MainActivity.java:4916），
 * 所以这里只放 [AgentProgressCard] 这一张卡。
 *
 * 外壳用 Miuix 的 [FloatingToolbar]（`outSidePadding` 负责外边距、
 * `shadowElevation` 负责阴影），而不是自己拼 `Surface` + `padding`。
 */
@Composable
private fun FloatingAgentStatus(
    status: String,
    tasks: List<AgentTask>,
    onExpand: () -> Unit,
    wide: Boolean,
    glass: Glass,
) {
    ZhiFrameTrace.countRecompose("FloatingCard")
    val scheme = MiuixTheme.colorScheme
    // 外壳走共用的 FloatingBottomShell：内缩/圆角/阴影/模糊与反馈条、输入器同源，
    // 三块在同一列上必须"同框"（形态以之前的发送栏为准，见 Common.kt）。
    // verticalPadding = 8dp：沿用任务卡原本的纵向呼吸空间。
    FloatingBottomShell(
        wide = wide,
        glass = glass,
        verticalPadding = 8.dp,
    ) {
        AgentProgressCard(
            status = status,
            tasks = tasks,
            onExpand = onExpand,
            embedded = true,
        )
    }
}

@Composable
private fun ComposerHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    glass: Glass,
    onFocusChanged: (Boolean) -> Unit,
) {
    /*
     * 输入框文字的所有权，提升到 `ComposerHost` 这一层。
     *
     * 为什么不放在 `Composer` 内部：`Composer` 会随 `state` 参数变化而重组，
     * 内部的 `remember` 虽然能活过重组，但这里需要在**发送之后**清空它 ——
     * 那个动作发生在 ViewModel（异步），把对象放这一层、由这里统一处理更容易看懂。
     *
     * ⚠️ 用 `remember`（不是 `rememberSaveable`）：`TextFieldState` 本身不提供 Saver，
     * 而它在进程被回收时丢掉是可接受的 —— 草稿在切会话时已按会话存过。
     */
    val textState = remember { ComposerTextState() }

    /*
     * 切会话时存旧草稿、取新草稿。
     *
     * `LaunchedEffect(activeSessionId)` 的 key 变化顺序是：组合先看到新 id，
     * 才轮到 effect 重启 —— 所以**拿不到**旧 id。因此用 remember 记住上一个 id，
     * 在 effect 里"先存旧、再取新"。
     */
    var lastSessionId by remember { mutableStateOf(state.activeSessionId) }
    LaunchedEffect(state.activeSessionId) {
        if (state.activeSessionId == lastSessionId) return@LaunchedEffect
        textState.saveDraft(lastSessionId)
        lastSessionId = state.activeSessionId
        textState.restoreDraft(state.activeSessionId)
    }
    // 用 GetMultipleContents 而不是 GetContent：相册里一次挑多张是常态
    // （对比截图、多页文档），只能选一张的话用户得反复进出选择器。
    //
    // 它仍然不是 OpenDocument：这里的授权只为"立刻读一次字节"服务，
    // 内容读完就进内存了，不需要跨进程重启保留的持久授权。
    // （原版用 ACTION_OPEN_DOCUMENT + takePersistableUriPermission，是因为它把 Uri
    //  留在附件列表里直到用户点发送，中间可能经历一次重组甚至进程重启。）
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> viewModel.attachImages(uris) }

    // 系统文件管理器：`OpenMultipleDocuments` 而不是 `GetMultipleContents` ——
    // 前者给的是"文档"入口（系统文件管理器/最近文档），后者给的是"内容"入口
    // （相册那一类）。上面选图片用后者，这里选文本用前者，与 SkillsOverlay
    // 导入技能的判断一致（那里也是因为"读的是文档类内容"才用 OpenDocument）。
    //
    // MIME 用「任意类型 / 任意子类型」两段拼出来（见 [SystemFileMimeTypes]），
    // 而不是直接写字面量：那个字面量里会出现「斜杠紧跟星号」这个序列，
    // 而各测试的 `stripComments` 是按正则删块注释的，会把它当成注释开头，
    // **把后面一大段源码整块删掉**（实测：这个文件里 397 行以后的内容会消失，
    // 断言报"找不到"而源码明明在）。
    val pickSystemFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris?.forEach { viewModel.attachDocument(it) } }

    Composer(
        state = state,
        wide = wide,
        glass = glass,
        // 文字归这个对象所有（见 ComposerTextState）：按键不再写回状态层，
        // 于是打字时整个对话流不会跟着重组。
        textState = textState,
        // 只有进入/离开斜杠命令态时才通知状态层；普通打字一次都不走。
        onSlashQueryChanged = viewModel::onSlashQueryChanged,
        // `@` 文件引用：查询串交给状态层算候选，选中后把路径写回文字。
        onFileQueryChanged = viewModel::onFileQueryChanged,
        onPickFile = { path ->
            textState.replaceLastAtToken(path)
            // 选中后立刻退出引用态：文字里已经补成 `@路径 `（带空格），
            // 上面那条 snapshotFlow 会自己判定为"不在引用态"，但不显式清一次的话
            // 面板会多停留一帧，看起来像闪了一下。
            viewModel.onFileQueryChanged(null)
        },
        onSend = { viewModel.sendFromComposerText(textState) },
        // 长按发送键：只发进对话流，不触发模型回复。
        onSendWithoutReply = { viewModel.sendWithoutReplyFromComposerText(textState) },
        onStop = viewModel::stop,
        onRemoveAttachment = { viewModel.removeAttachment(it.id) },
        // `+` 菜单的四个动作
        onAttachFile = viewModel::openAttachPicker,
        // `GetMultipleContents` 收 `String`（一个 MIME），`OpenMultipleDocuments`
        // 收 `Array<String>` —— 所以一个取单个元素、一个传整份数组，不是笔误。
        onPickImage = { pickImage.launch(ImageMimeTypes.single()) },
        onPickSystemFiles = { pickSystemFiles.launch(SystemFileMimeTypes) },
        // 输入器里待发图片的缩略图数据（按附件 id 取，不进 state）
        onAttachmentImage = viewModel::currentAttachmentImage,
        // 页脚的下拉：选中即生效，不再弹选择器
        onPermissionSelected = viewModel::setPermissionMode,
        onEffortSelected = viewModel::setEffort,
        onModelChip = viewModel::showModelPicker,
        onPickSlash = viewModel::pickSlashCommand,
        // 排队中的预输入：取回（编辑后再发）与取消。
        onRemoveQueued = viewModel::removeQueuedPrompt,
        onEditQueued = viewModel::editQueuedPrompt,
        // 主体调试模式：输入行下方实时渲染当前输入的 Markdown（与对话流同一渲染器）
        debugMode = state.debugAppMode,
        onFocusChanged = onFocusChanged,
    )
}

/**
 * 长按动作菜单的**宿主插槽**：交给被长按的那一项去调用。
 *
 * 每一项在**自己的布局里**调用 `anchoredMenu(自己的 id)`，菜单就会锚在那一项上
 * 弹出（见 [ZhiAnchoredActionMenu]）。这里集中做三件事，避免每个调用点各写一遍：
 *
 * 1. 只有 `anchorId` 与传进来的 id 相同的那一项才认领这份菜单 —— 否则列表里
 *    每一条都会弹一个；
 * 2. 不是动作菜单（`isActionMenu` 为假）时什么都不画，交回 `ChoicePickerOverlay`；
 * 3. 点击走**原有的** [WorkspaceViewModel.onChoiceSelected]（按下标分发，详见
 *    该方法里的 intent 分支）—— 动作语义、目标记忆、错误提示全部复用。
 */
@Composable
internal fun ZhiAnchoredMenuHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    anchorId: String,
    fingerOffset: DpOffset?,
    /**
     * 弹层渲染到哪个宿主。
     *
     * ⚠️ 侧栏必须传 `false`。侧栏抽屉是 `Scaffold` **之后**的兄弟节点、叠在它上面，
     * 而 `renderInRootScaffold = true` 会把菜单注册进**最外层 Scaffold** 的 popupHost ——
     * 菜单其实弹了，却被抽屉整块盖住，表现就是「长按会话没反应」。
     * 传 `false` 时走离调用点最近的局部宿主，层级正确。
     *
     * 对话流保持默认 `true`：那里没有被兄弟节点覆盖的问题，走根宿主能在整屏定位。
     */
    renderInRootScaffold: Boolean = true,
) {
    val picker = state.choicePicker
    // 菜单从手指位置长出来，而退场动画期间 choicePicker 已经是 null —— 不兜住的话，
    // 面板会在退场那几帧里弹回条目左上角再消失。
    val shownPicker = rememberLastNonNull(picker) ?: return
    val open = picker != null && picker.isActionMenu && picker.anchorId == anchorId
    if (!shownPicker.isActionMenu || shownPicker.anchorId != anchorId) return
    ZhiAnchoredActionMenu(
        // 不套 `if`：退出动画需要浮层常驻组合（见 ZhiAnchoredActionMenu 的说明）。
        open = open,
        labels = shownPicker.options.map { it.label },
        onSelect = viewModel::onChoiceSelected,
        onDismiss = viewModel::dismissChoicePicker,
        // 非空时面板从**手指那一点**长出来；为空则退回贴条目锚定。
        fingerOffset = fingerOffset,
        renderInRootScaffold = renderInRootScaffold,
    )
}

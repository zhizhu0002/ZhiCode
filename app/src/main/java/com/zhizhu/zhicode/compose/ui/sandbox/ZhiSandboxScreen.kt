package com.zhizhu.zhicode.compose.ui.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「ZhiCode 沙箱」管理界面的 Compose 主体。
 *
 * ## 为什么要有这个文件（原来的界面为什么被换掉）
 *
 * 这一屏原本是 {@code SandboxBoard.java} 里手写的 View 树（`LinearLayout` / `TextView` /
 * 平台 `Switch` / `AlertDialog`）。它有两个绕不开的问题：
 *
 * 1. **它不在这套主题里**。那个 Activity 没有声明 `android:theme`，于是继承应用级主题
 *    `@android:style/Theme.Material.NoActionBar.Fullscreen` —— 也就是框架的 Material v1。
 *    所以它的开关是**青色**（Material v1 的默认 accent），弹窗是平台对话框样式，
 *    和 Miuix 那一套完全不是一路。这不是配色没调好，是压根没进 Miuix 主题树。
 * 2. **几何是手写死的**。行高固定 48/44/54dp、开关被塞进 64×44dp 的盒子（于是滑块被压变形）、
 *    按钮 `setTextSize(10)`、空态是裸 `TextView` + 固定 120dp。这些数字各自独立，
 *    一改布局就互相打架。
 *
 * 现在这一屏跟其余界面走**同一套主题栈**（见 `SandboxBoard` 里的 `setContent`）：
 * `LocalZhiDark` → `MiuixTheme(colors = …backdrop(), textStyles = zhiTextStyles())`，
 * 圆角统一走 [ZhiRadius]、字阶统一走 [ZhiTextScale]，控件全部是 Miuix 组件。
 *
 * ## 这个文件只负责画
 *
 * 它不做任何 RPC、不碰 `SandboxRpc`、不持有线程 —— 那些留在 `SandboxBoard` 里。
 * 界面拿到的是一份不可变的 [SandboxBoardUiState]，所有动作通过回调往上抛。
 * 因此「界面怎么写」与「后端怎么调」互不影响，改样式不会碰到启动重试与回滚逻辑。
 */

/** 状态行语义色。原来靠 `SandboxPalette` 的 success/danger，现在交给主题。 */
enum class SandboxStatusTone { NORMAL, SUCCESS, DANGER }

/**
 * 界面状态。**只读快照**：每次变化由 `SandboxBoard` 整份替换。
 *
 * `*_interactive` 是那两个开关的「处理中」互斥位：请求在飞的时候必须压住交互，
 * 否则用户连点会让界面显示的状态和服务端最终生效的值不一致。回滚的语义
 * （失败回到原值）由 `SandboxBoard` 负责，这里只表达"能不能点"。
 */
data class SandboxBoardUiState(
    val status: String = "",
    val statusTone: SandboxStatusTone = SandboxStatusTone.NORMAL,
    val packages: List<String> = emptyList(),
    val hideRoot: Boolean = true,
    val hideRootInteractive: Boolean = false,
    val floatingLog: Boolean = true,
    val floatingLogInteractive: Boolean = false,
    /** 后端不可用时内联展示的完整原因（含最后启动阶段）；正常时为 null。 */
    val errorDetail: String? = null,
    val dialog: SandboxDialog? = null,
)

/** 当前弹出的对话框。用密封类型而不是若干 boolean —— 同时只可能有一个。 */
sealed interface SandboxDialog {
    /** Root 隐藏的确认框；[hidden] 是用户**想要**切到的那一侧。 */
    data class RootVisibility(val hidden: Boolean) : SandboxDialog

    /** 破坏性操作的确认框（清数据 / 卸载）。 */
    data class ConfirmAction(
        val title: String,
        val packageName: String,
        val action: String,
        val successText: String,
    ) : SandboxDialog

    /**
     * Frida Gadget 首次安装的确认框。
     *
     * 它**不能**复用 [ConfirmAction]：后者点确定后会把 `action` 当成后端动作名发出去，
     * 而安装 Gadget 是本地下载 + 校验，不走 RPC。
     */
    data class FridaInstall(val packageName: String, val pid: Int, val version: String) : SandboxDialog

    /** 长文本面板（进程/SO 基址、Frida 结果、诊断）。[loading] 时显示占位而不是空面板。 */
    data class Detail(val title: String, val body: String, val loading: Boolean) : SandboxDialog
}

/**
 * 沙箱管理界面。
 *
 * 参数全是回调，没有一个是「直接调后端」的 —— 见文件头的说明。
 */
@Composable
fun ZhiSandboxScreen(
    state: SandboxBoardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDiagnostics: () -> Unit,
    onToggleHideRoot: (Boolean) -> Unit,
    onToggleFloatingLog: (Boolean) -> Unit,
    onPickApk: () -> Unit,
    onLaunch: (String) -> Unit,
    onStop: (String) -> Unit,
    onClearData: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onProcesses: (String) -> Unit,
    onFrida: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmRootVisibility: (Boolean) -> Unit,
    onConfirmAction: (SandboxDialog.ConfirmAction) -> Unit,
    onConfirmFridaInstall: (SandboxDialog.FridaInstall) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MiuixTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(onBack = onBack, onDiagnostics = onDiagnostics)
            ZhiHorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { StatusLine(state.status, state.statusTone) }

                // 两个开关放同一张卡片，与设置页的「分组卡片」观感一致。
                // 原来它们各占一行、开关被挤在 64dp 宽的固定盒子里，滑块视觉上溢出。
                item {
                    Card(
                        cornerRadius = ZhiRadius.card,
                        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        SwitchPreference(
                            title = "隐藏 Root",
                            summary = if (state.hideRootInteractive) {
                                "正在应用…"
                            } else {
                                "Guest 看不到常见 Root 路径与管理包"
                            },
                            checked = state.hideRoot,
                            onCheckedChange = { wanted ->
                                // 处理中直接丢弃点击：连点会让界面值与服务端值错位
                                if (state.hideRootInteractive) return@SwitchPreference
                                onToggleHideRoot(wanted)
                            },
                        )
                        SwitchPreference(
                            title = "日志悬浮窗",
                            summary = if (state.floatingLogInteractive) {
                                "正在应用…"
                            } else {
                                "在 Guest 界面上叠一层日志与返回控制栏"
                            },
                            checked = state.floatingLog,
                            onCheckedChange = { wanted ->
                                if (state.floatingLogInteractive) return@SwitchPreference
                                onToggleFloatingLog(wanted)
                            },
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimaryButton(
                            text = "导入 APK",
                            onClick = onPickApk,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "刷新",
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (state.errorDetail != null) {
                    item { ErrorDetail(state.errorDetail) }
                } else if (state.packages.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(state.packages, key = { it }) { pkg ->
                        AppCard(
                            packageName = pkg,
                            onLaunch = onLaunch,
                            onStop = onStop,
                            onClearData = onClearData,
                            onUninstall = onUninstall,
                            onProcesses = onProcesses,
                            onFrida = onFrida,
                        )
                    }
                }
            }
        }
    }

    SandboxDialogHost(
        dialog = state.dialog,
        onDismiss = onDismissDialog,
        onConfirmRootVisibility = onConfirmRootVisibility,
        onConfirmAction = onConfirmAction,
        onConfirmFridaInstall = onConfirmFridaInstall,
    )
}

/**
 * 顶栏：返回 + 标题 + 诊断。
 *
 * 返回用「上箭头旋转 -90°」而不是新画一个图标：`ZhiIcons` 里没有专门的返回箭头，
 * 而 ArrowUp 与左箭头是同一个字形旋转关系，转一下比再塞一个自绘 path 更省。
 */
@Composable
private fun TopBar(onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZhiIconButton(
            icon = ZhiIcons.upLevel,
            description = "返回",
            onClick = onBack,
            iconSize = 18.dp,
            compact = 36.dp,
            modifier = Modifier.rotate(-90f),
        )
        Text(
            text = "ZhiCode 沙箱",
            color = scheme.onBackground,
            fontSize = ZhiTextScale.TitleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        TextButton(
            text = "诊断",
            onClick = onDiagnostics,
            cornerRadius = ZhiRadius.button,
        )
    }
}

/** 状态行：后端状态、重试进度、错误原因都落在这里。 */
@Composable
private fun StatusLine(status: String, tone: SandboxStatusTone) {
    val scheme = MiuixTheme.colorScheme
    val color = when (tone) {
        SandboxStatusTone.NORMAL -> scheme.onSurfaceVariantSummary
        SandboxStatusTone.SUCCESS -> scheme.primary
        SandboxStatusTone.DANGER -> scheme.error
    }
    if (status.isEmpty()) return
    Text(
        text = status,
        color = color,
        fontSize = ZhiTextScale.Caption,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
    )
}

/**
 * 后端不可用时的内联说明。
 *
 * 旧实现把这段文字直接塞进列表区（红色 `TextView`，可选中）。这里保留「内联、可选中、
 * 带完整启动阶段」这三个性质 —— 后端没起来时，这段文字就是唯一能说明原因的东西，
 * 不能藏进需要再点一下的弹窗里。
 */
@Composable
private fun ErrorDetail(detail: String) {
    val scheme = MiuixTheme.colorScheme
    SelectionContainer {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = ZhiRadius.card,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            colors = CardDefaults.defaultColors(
                color = scheme.surfaceContainer,
                contentColor = scheme.error,
            ),
        ) {
            Text(
                text = detail,
                color = scheme.error,
                fontSize = ZhiTextScale.Caption,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 空态。原来是一句裸文本 + 固定 120dp 高，飘在一大片空白里；现在给一个真正的容器。 */
@Composable
private fun EmptyState() {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainer,
            contentColor = scheme.onSurfaceVariantSummary,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "还没有沙箱应用",
                color = scheme.onBackground,
                fontSize = ZhiTextScale.Subheading,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "导入一个 APK，或让 Agent 调用 Sandbox install。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Caption,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 单个沙箱应用。
 *
 * 六个动作分两行：Miuix `ButtonDefaults` 的最小尺寸是 58×40dp（实测），
 * 一行塞六个会低于最小宽度而把文字挤断，所以运行/停止/清数据一行、卸载与两个调试入口一行。
 * 每颗按钮 `weight(1f)` 等分，行内高度由 Miuix 自己的 minHeight 决定 —— 不再手写 40/44dp。
 */
@Composable
private fun AppCard(
    packageName: String,
    onLaunch: (String) -> Unit,
    onStop: (String) -> Unit,
    onClearData: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onProcesses: (String) -> Unit,
    onFrida: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = packageName,
                color = scheme.onBackground,
                fontSize = ZhiTextScale.Body,
                fontWeight = FontWeight.Bold,
            )
            ActionRow {
                Action("运行", Modifier.weight(1f)) { onLaunch(packageName) }
                Action("停止", Modifier.weight(1f)) { onStop(packageName) }
                Action("清数据", Modifier.weight(1f)) { onClearData(packageName) }
            }
            ActionRow {
                Action("卸载", Modifier.weight(1f)) { onUninstall(packageName) }
                Action("进程 / SO 基址", Modifier.weight(1f)) { onProcesses(packageName) }
                Action("Frida", Modifier.weight(1f)) { onFrida(packageName) }
            }
        }
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

@Composable
private fun Action(text: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        text = text,
        onClick = onClick,
        cornerRadius = ZhiRadius.button,
        modifier = modifier,
    )
}

/** 三个对话框的宿主。同一时刻只可能有一个，所以用 `state.dialog` 的 `when` 分发。 */
@Composable
private fun SandboxDialogHost(
    dialog: SandboxDialog?,
    onDismiss: () -> Unit,
    onConfirmRootVisibility: (Boolean) -> Unit,
    onConfirmAction: (SandboxDialog.ConfirmAction) -> Unit,
    onConfirmFridaInstall: (SandboxDialog.FridaInstall) -> Unit,
) {
    OverlayDialog(
        show = dialog != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Regular,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        when (val current = dialog) {
            null -> Unit

            is SandboxDialog.RootVisibility -> DialogShell(
                title = if (current.hidden) "开启 Root 隐藏？" else "关闭 Root 隐藏？",
                actions = {
                    SecondaryButton(text = "取消", onClick = onDismiss)
                    PrimaryButton(
                        text = "确定",
                        onClick = { onConfirmRootVisibility(current.hidden) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
            ) {
                Text(
                    text = rootVisibilityEffect(current.hidden) +
                        "\n\n所有正在运行的 Guest 将停止，重新启动后生效。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.BodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is SandboxDialog.ConfirmAction -> DialogShell(
                title = current.title,
                actions = {
                    SecondaryButton(text = "取消", onClick = onDismiss)
                    PrimaryButton(
                        text = "确定",
                        onClick = { onConfirmAction(current) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
            ) {
                Text(
                    text = current.packageName,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is SandboxDialog.FridaInstall -> DialogShell(
                title = "安装 Frida Gadget " + current.version,
                actions = {
                    SecondaryButton(text = "取消", onClick = onDismiss)
                    PrimaryButton(
                        text = "安装并加载",
                        onClick = { onConfirmFridaInstall(current) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
            ) {
                Text(
                    text = current.packageName + "\n\n首次使用需要从 Frida 官方发布页下载 arm64 Gadget，" +
                        "并校验固定的 SHA-256。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.BodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is SandboxDialog.Detail -> DialogShell(
                title = current.title,
                actions = { SecondaryButton(text = "关闭", onClick = onDismiss) },
            ) {
                if (current.loading) {
                    Text(
                        text = "正在读取…",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.BodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 等宽字体：这几块内容都是「基址 / 阶段」这类对齐敏感的文本，
                    // 比例字体会让列对不齐 —— 与旧实现用 MONOSPACE 是同一个理由。
                    //
                    // ⚠ 这里**不能**再套 verticalScroll：DialogShell 已经把 body 放进了
                    // 竖向滚动容器，嵌套会让内层拿到无限大高度约束并抛
                    // Infinite maximum height constraints 直接崩界面
                    // （就是 DialogScrollNestingTest 守的那个坑，本轮刚修过一次）。
                    Text(
                        text = current.body,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Root 隐藏两侧各自的实际影响。文案与旧实现逐字一致（有测试钉住后半句）。 */
internal fun rootVisibilityEffect(hidden: Boolean): String = if (hidden) {
    "Guest 将隐藏常见 Root 路径和管理包。"
} else {
    "Guest 将可以发现并请求设备上的 su，授权仍由设备 Root 管理器决定。"
}

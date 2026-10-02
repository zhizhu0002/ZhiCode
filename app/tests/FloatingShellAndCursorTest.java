import java.nio.file.*;
import java.util.*;

/**
 * 底部三块悬浮层「同框」+ 发送栏光标 + 全屏图片查看器 的守卫。
 *
 * <p>这一批是用户在真机上指出的三类"不报错、但看起来不对"的界面缺陷：
 *
 * <h2>1. 底部三块不是同宽的（输入器 / 任务卡 / 反馈条）</h2>
 *
 * 三块各自调 Miuix {@code FloatingToolbar}，几何上横向内缩都是 12dp
 * （截图实测：输入器卡 x=22..666、任务卡 x=24..664），但阴影档位、模糊
 * 参数各写各的 —— {@code FloatingToolbar} 的阴影画在卡片边界**外面**，
 * 阴影档位不同，"可见边缘"就差出几 dp，看上去就是"不是同宽的"。
 *
 * <p>修法是收一个 [FloatingBottomShell]（Common.kt）：内缩、圆角、阴影、
 * 模糊半径都在这一处定死，三块想不一致都不行。这里钉住：
 * <ul>
 *   <li>外壳必须存在，且必须继续用 {@code floatingHorizontalInset} 做横向内缩；</li>
 *   <li>三个调用方（Composer / ChatArea / MessageBar）不得再自己调
 *       {@code FloatingToolbar(} 或传 {@code shadowElevation}。</li>
 * </ul>
 *
 * <h2>2. 发送栏没有光标</h2>
 *
 * Miuix {@code TextField} 的光标默认画成 {@code SolidColor(colors.borderColor)}
 * （核过 0.9.4 源码），而输入器为了"只留外层方角框"把 {@code borderColor}
 * 设成了透明 —— 那一下连光标也一起透明了。修法是 {@code ZhiTextField}
 * 显式给 {@code cursorBrush}，默认值取主题 primary、与描边无关。
 * 钉住默认值**不得**引用 {@code borderColor}，否则下一次"把描边调成透明"
 * 又会把光标一起带走。
 *
 * <h2>3. 图片预览做得奇怪 / 在别的设备上显示过大</h2>
 *
 * 原实现 {@code Image(fillMaxWidth) + ContentScale.Fit} 只约束了宽，
 * **高没有约束**；而 Miuix {@code OverlayDialog} 的
 * {@code heightIn(max = windowHeight * 2/3)} 只在 {@code isLargeScreen} 分支
 * 生效（核过 0.9.4 的 {@code DialogContentLayout}），于是竖屏长截图按比例
 * 撑到六七百 dp、直接越过屏幕。弹窗自带的圆角 / 内边距 / squircle 底又把
 * 图片包成一张小卡片。修法是照原版 {@code showFullImage()} 的形态：
 * 全屏黑底 + {@code FIT_CENTER} + 边距圆角全清零。
 */
public final class FloatingShellAndCursorTest {

    private static final String COMMON =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Common.kt";
    private static final String COMPOSER =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/composer/Composer.kt";
    private static final String CHAT_AREA =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ChatArea.kt";
    private static final String MESSAGE_BAR =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/MessageBar.kt";
    private static final String ZHI_IMAGE =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ZhiImage.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** 取出 {@code 签名 … 配对右花括号} 之间的函数体；找不到就返回空串（调用点会立刻报错）。 */
    private static String functionBody(String code, String signature) {
        int at = code.indexOf(signature);
        if (at < 0) return "";
        int depth = 0;
        boolean seen = false;
        for (int i = at; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') { depth++; seen = true; }
            else if (c == '}') {
                depth--;
                if (seen && depth == 0) return code.substring(at, i + 1);
            }
        }
        return code.substring(at);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String common = stripComments(read(root, COMMON));
        String composer = stripComments(read(root, COMPOSER));
        String chatArea = stripComments(read(root, CHAT_AREA));
        String messageBar = stripComments(read(root, MESSAGE_BAR));
        String zhiImage = stripComments(read(root, ZHI_IMAGE));
        String sqCommon = squash(common);
        String sqComposer = squash(composer);
        String sqChatArea = squash(chatArea);
        String sqMessageBar = squash(messageBar);
        String sqZhiImage = squash(zhiImage);

        // ---- 1. 外壳存在，且是圆角 / 阴影 / 内缩摆放的**唯一**来源 -----------
        // 形态以**之前的发送栏**为准：内缩做在布局层（卡片贴住布局框）、
        // outSidePadding 清零、阴影 12dp。
        String shell = functionBody(common, "internal fun FloatingBottomShell(");
        require(!shell.isEmpty(),
                COMMON + " 里找不到 FloatingBottomShell —— 三块悬浮层的同框约定没了支点");
        require(squash(shell).contains("FloatingToolbar("),
                "FloatingBottomShell 应该包着 Miuix FloatingToolbar：删掉它就丢了"
                        + " squircle 底与阴影的统一实现，三块又要各写各的。");
        require(squash(shell).contains("padding(horizontal=floatingHorizontalInset(wide))"),
                "外壳的横向内缩必须做在**布局层**（.padding(horizontal = floatingHorizontalInset(wide))）："
                        + "改走 outSidePadding 的话卡片会从布局框里再缩一圈，"
                        + "三块就不再是发送栏的形态了。");
        require(squash(shell).contains("outSidePadding=PaddingValues(0.dp)"),
                "外壳的 outSidePadding 必须清零：卡片贴住布局框是发送栏的原始形态，"
                        + "不许在 outSidePadding 里再加边距。");
        require(squash(shell).contains("shadowElevation=12.dp"),
                "外壳必须统一给 shadowElevation = 12dp（发送栏原本的档位）："
                        + "三块阴影档位不一致正是「看起来不是同宽」的根因"
                        + "（阴影画在卡片边界外面）。");

        // ---- 2. 三个调用方必须走外壳，不得再自己开 FloatingToolbar ----------
        // ⚠️ 只守这三块生产界面；调试页（UiDebugPage / DebugHud）自己用
        // FloatingToolbar 是合法的，不在这里的管辖范围。
        require(sqComposer.contains("FloatingBottomShell(wide=wide,glass=glass){"),
                COMPOSER + " 的输入器卡片必须走 FloatingBottomShell(wide = wide, glass = glass)"
                        + " —— 与任务卡、反馈条同一个圆角/阴影/内缩来源");
        require(!composer.contains("FloatingToolbar("),
                COMPOSER + " 不得再直接调 FloatingToolbar —— 那正是三块不同宽的起点");
        require(!composer.contains("shadowElevation"),
                COMPOSER + " 不得自带 shadowElevation：阴影档位由外壳统一给");
        require(!composer.contains("floatingHorizontalInset("),
                COMPOSER + " 不得自己做横向内缩：输入器的横向内缩由外壳统一给，"
                        + "外层 Column 只留纵向间距");

        // （匹配到 ".dp," 为止，容忍调用处的尾随逗号。）
        require(sqChatArea.contains("FloatingBottomShell(wide=wide,glass=glass,verticalPadding=8.dp,"),
                CHAT_AREA + " 的任务卡必须走 FloatingBottomShell 且纵向间距 8dp（沿用原值）");
        require(!chatArea.contains("FloatingToolbar("),
                CHAT_AREA + " 不得再直接调 FloatingToolbar");
        require(!chatArea.contains("shadowElevation"),
                CHAT_AREA + " 不得自带 shadowElevation：阴影档位由外壳统一给");

        require(sqMessageBar.contains("FloatingBottomShell(wide=wide,glass=glass,verticalPadding=6.dp)"),
                MESSAGE_BAR + " 的反馈条必须走 FloatingBottomShell 且纵向间距 6dp（沿用原值）");
        require(!messageBar.contains("FloatingToolbar("),
                MESSAGE_BAR + " 不得再直接调 FloatingToolbar");
        require(!messageBar.contains("shadowElevation"),
                MESSAGE_BAR + " 不得自带 shadowElevation：阴影档位由外壳统一给");

        // ---- 3. 光标颜色不得绑在描边颜色上 ----------------------------------
        // Miuix TextField 的光标默认 SolidColor(colors.borderColor)，而输入器把
        // borderColor 调成了透明 —— 那一下连光标也一起透明了（"发送栏没有光标"）。
        // 所以 ZhiTextField 必须显式给一个与描边无关的默认 cursorBrush。
        String field = functionBody(common, "fun ZhiTextField(");
        require(!field.isEmpty(), COMMON + " 里找不到 ZhiTextField —— 断言过期，请更新守卫");
        require(squash(field).contains("cursorBrush:Brush=SolidColor(MiuixTheme.colorScheme.primary)"),
                "ZhiTextField 必须显式声明 cursorBrush 且默认取主题 primary："
                        + "Miuix 的默认值是 SolidColor(colors.borderColor)，而工程里有输入框"
                        + "把 borderColor 调成透明 —— 那一下连光标也一起透明了。");
        // 防回归的具体形态：默认值里不许出现 borderColor。
        int cursorAt = squash(field).indexOf("cursorBrush:");
        require(cursorAt >= 0, "ZhiTextField 里找不到 cursorBrush 参数 —— 断言过期，请更新守卫");
        String tail = squash(field).substring(cursorAt,
                Math.min(squash(field).length(), cursorAt + 120));
        require(!tail.contains("borderColor"),
                "cursorBrush 的默认值不得引用 borderColor：光标颜色必须与描边颜色无关，"
                        + "否则下一次「把描边调成透明」又会把光标一起带走。");
        require(squash(field).contains("cursorBrush=cursorBrush"),
                "ZhiTextField 必须把 cursorBrush 透传给 Miuix TextField："
                        + "不透传的话参数就是摆设，光标还是走 Miuix 默认的 borderColor。");

        // ---- 4. 图片查看器必须全屏黑底（照原版 showFullImage 的形态） --------
        String viewer = functionBody(zhiImage, "internal fun ZhiImageViewer(");
        require(!viewer.isEmpty(),
                ZHI_IMAGE + " 里找不到 ZhiImageViewer —— 断言过期，请更新守卫");
        String sqViewer = squash(viewer);
        // 边距 / 圆角全清零 + 纯黑背景：这是"全屏"的四个支柱，少一个就退回小卡片。
        // （清零值走 ViewerZeroInsets 常量而不是 DpSize 字面量 —— 后者被
        // LayoutConsistencyTest 禁止，这里两守卫各自盯自己那半。）
        require(sqViewer.contains("outsideMargin=ViewerZeroInsets"),
                "ZhiImageViewer 的 outsideMargin 必须清零（ViewerZeroInsets）："
                        + "留着边距图片就被包成一张小卡片");
        require(sqViewer.contains("insideMargin=ViewerZeroInsets"),
                "ZhiImageViewer 的 insideMargin 必须清零（ViewerZeroInsets）："
                        + "留着内边距图片四周会有一圈弹窗底色");
        require(sqViewer.contains("cornerRadius=0.dp"),
                "ZhiImageViewer 的圆角必须清零：全屏看图不该有圆角卡片感");
        require(sqViewer.contains("backgroundColor=Color.Black"),
                "ZhiImageViewer 的背景必须是纯黑：照原版 showFullImage() 的"
                        + " Theme_Black_NoTitleBar_Fullscreen 形态");
        require(sqViewer.contains("defaultWindowInsetsPadding=false"),
                "ZhiImageViewer 必须关掉弹窗自带的 insets 避让：全屏层的避让由自己的"
                        + " statusBars/navigationBars padding 负责，× 按钮单独避让");
        // 高度必须有约束：fillMaxSize + Fit 才不会在窄屏长图上撑爆屏幕。
        require(sqViewer.contains("contentScale=ContentScale.Fit"),
                "ZhiImageViewer 必须用 ContentScale.Fit：硬裁(Crop)会把长截图的内容藏起来");
        require(sqViewer.contains("contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize()"),
                "ZhiImageViewer 的 Image 必须 fillMaxSize：只约束宽(fillMaxWidth)的话"
                        + " 高没有约束，而 Miuix 弹窗的高度上限只在平板分支生效 ——"
                        + " 竖屏长截图会按比例撑过整块屏幕（「显示过大」的真因）");
        require(!sqViewer.contains("fillMaxWidth()"),
                "ZhiImageViewer 里不许出现 fillMaxWidth()：那就是「高无约束」的起点");
    }
}

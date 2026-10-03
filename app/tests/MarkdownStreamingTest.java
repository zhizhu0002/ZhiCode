import java.nio.file.*;
import java.util.*;

/**
 * 流式渲染热路径的守卫。
 *
 * <p>这里守的每一条都**不会**让编译失败，也不会让界面「坏掉」—— 只会让老设备变慢。
 * 而"变慢"没有主人：它不报错、不崩溃，只是每次回复都掉帧。所以只能用静态断言钉住。
 *
 * <h2>被守的三件事</h2>
 *
 * <ol>
 *   <li><b>不要在每次 delta 时重解析整篇。</b>正文每个 delta 变一次
 *       （{@code ZhiEngineController.DELTA_MERGE_MS = 32}，约每秒 31 次），
 *       而渲染层原本是 {@code remember(source) { parseMarkdown(source) }} ——
 *       整篇累积文本每秒被完整重解析 31 次，一条 20 KB 的回复累计 O(n²)。
 *       现在按「已完结前缀」切分，只有跨块时才重解析（几十次）。
 *       把 key 改回 {@code source} 就等于把优化整个撤掉。</li>
 *   <li><b>行内解析要走 {@code rememberInline}。</b>{@code inline()} 非 @Composable、
 *       内部跑 {@code parseInline} + {@code buildAnnotatedString}；
 *       直接在参数位置调用会让每个段落**每次重组**都重跑一遍。</li>
 *   <li><b>流式期间不挂 {@code animateContentSize}。</b>内容每 32ms 长高一次，
 *       尺寸动画会被反复重新触发，整张卡在整个回复期间持续重测量。</li>
 * </ol>
 *
 * <p>切点函数 {@code settledPrefixLength} 的**行为**由 JVM 单测守
 * （{@code MarkdownStreamSplitTest}：围栏、CRLF、空行、"尾部只有空白"这些边界）。
 * 本文件只管源码结构，两者互补。
 */
public final class MarkdownStreamingTest {

    private static final String MARKDOWN = "app/src/main/java/com/zhizhu/zhicode/compose/ui/Markdown.kt";
    private static final String PARSE = "app/src/main/java/com/zhizhu/zhicode/compose/ui/MarkdownParse.kt";
    private static final String CARDS = "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/MessageCards.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 去注释。
     *
     * <p>⚠️ 与那几个守卫同样的坑：字符串里的 {@code /*} 会触发块注释正则，把真正的源码吃掉，
     * 于是断言在"源码已经改坏"的情况下依然通过。本文件断言的片段里没有这种字面量，
     * 但仍然按同样的方式处理，避免以后有人往注释里写示例代码时踩到。
     */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    /** 去掉所有空白：断言只关系代码结构，不该因为一次重新缩进就红。 */
    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** 取出 {@code 签名 … 配对右花括号} 之间的函数体；找不到就用整个文件（后面会立刻报错）。 */
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

        // ---- 1. 切点函数存在，且必须是「无分配」的 --------------------------
        String parse = stripComments(read(root, PARSE));
        String settled = functionBody(parse, "internal fun settledPrefixLength(");
        require(!settled.isEmpty(),
                PARSE + " 里找不到 settledPrefixLength —— 流式切分被整个删掉了，"
                        + "正文会退回「每个 delta 重解析整篇」的 O(n²) 路径");
        // 它每次 delta 都会被调用（约 31 次/秒），在里面 substring 会把省下来的分配
        // 又花回去 —— 所以这里不许出现 substring。行内容的判断一律走下标。
        require(!settled.contains("substring("),
                "settledPrefixLength 里出现了 substring：它每秒被调用约 31 次，"
                        + "一旦开始按行切字符串，这条路径的分配量就回到优化之前。"
                        + "行内容一律用下标判断。");
        require(!settled.contains(".split("),
                "settledPrefixLength 里出现了 split：理由同上（会建出所有行的数组）");
        // 空行判据里必须同时处理 \r，否则 CRLF 的回复整条都切不动。
        require(settled.contains("'\\r'"),
                "settledPrefixLength 必须把行尾的 \\r 去掉（CRLF），否则带 \\r\\n 的回复"
                        + "永远找不到块边界、优化对它们完全失效");

        // ---- 2. 解析的 key 必须落在「已完结前缀」上 -------------------------
        String md = stripComments(read(root, MARKDOWN));
        String mdSquashed = squash(md);
        require(md.contains("streaming: Boolean = false"),
                MARKDOWN + " 的 ZhiMarkdown 必须有一个 streaming 参数（默认 false）："
                        + "只有流式中的正文才需要分段渲染，已定稿的消息不该被拆开解析");
        require(mdSquashed.contains("parseMarkdown(split.settled)"),
                "parseMarkdown 必须按**已完结前缀**取 key（split.settled）。"
                        + "写成 remember(source) 会让整篇每秒被重解析 31 次 —— "
                        + "这正是本次优化要消掉的那件事。");
        require(!md.contains("remember(source)"),
                MARKDOWN + " 里又出现了 remember(source)："
                        + "那是「每个 delta 重解析整篇」，等于把优化撤销了");
        require(md.contains("settledPrefixLength("),
                MARKDOWN + " 必须实际调用 settledPrefixLength 来算切点");
        require(mdSquashed.contains("if(!streaming)returnMdStreamSplit(source,\"\")"),
                "非流式（streaming=false）必须走整段解析：尾部是按行内渲染的，"
                        + "定稿后不整段重解析一次，最后那段的标题/列表/代码块拿不到块级样式");

        // ---- 3. 行内解析不许裸调（必须走 rememberInline） -------------------
        String rememberInline = functionBody(md, "private fun rememberInline(");
        require(!rememberInline.isEmpty(),
                MARKDOWN + " 里找不到 rememberInline —— 行内解析不再被缓存");
        require(squash(rememberInline).contains("remember(text,styles){inline(text,styles)}"),
                "rememberInline 必须用 (text, styles) 作 key 并缓存 inline() 的结果");
        // 4 个调用点原本全都是 `text = inline(…)`，现在必须一律走 rememberInline。
        // 断言具体的调用形态而不是计数：计数拦不住"只还原其中一个调用点"。
        require(!md.contains("text = inline("),
                "出现了绕过 rememberInline 的裸调用 `text = inline(…)`："
                        + "inline() 非 @Composable，内部跑 parseInline + buildAnnotatedString，"
                        + "裸调会让该段落**每次重组**都重跑一遍。");
        int rememberedCalls = countOccurrences(md, "rememberInline(");
        require(rememberedCalls >= 6,
                "rememberInline 至少要出现在 6 处（定义 1 + 标题/段落/列表项/表格单元格/"
                        + "流式尾部 5 个调用点），现在只有 " + rememberedCalls + " 处");

        // ---- 4. 流式期间不挂 animateContentSize -----------------------------
        String cards = stripComments(read(root, CARDS));
        String assistant = functionBody(cards, "fun AssistantCard(");
        require(!assistant.isEmpty(), CARDS + " 里找不到 AssistantCard");
        require(assistant.contains("streaming = item.streaming"),
                "AssistantCard 必须把 item.streaming 传给 ZhiMarkdown —— "
                        + "不传的话 Markdown 层只能整段解析，优化等于没接上");
        // ---- 4a. 流式期间**也**挂 animateContentSize（用户要的「流式正文平滑增长」）----
        //
        // ⚠️ 这一条在本轮**反向改过一次**，两个方向的理由都留在这里。
        //
        // 原来钉的是「两边分写」：streaming 时返回裸 Modifier、不带动画。理由是
        // "正文每 32ms 长高一次，尺寸动画会被反复重新触发，整张卡持续重测量"。
        // 但那个理由**站不住**：`animateContentSize` 不是"每次变化都重放一遍补间"，
        // 目标一变它就从**当前动画值**继续跑到新目标 —— 后果只是尺寸略微滞后，
        // 不是重新触发风暴。而流式正文"每 32ms 多一行"这件事本身，
        // 不挂动画就是**一帧一跳**的硬跳 —— 用户报的正是「文字是直接没动画」。
        //
        // ⚠️ 这是本轮**唯一可能反向影响性能**的一项：代价是流式期间每帧一次测量+布局，
        // 而那是"想要平滑增长"的必然成本。**回退点就是这一处** ——
        // 若真机上出现"流式一顿一顿"，把它改回按 item.streaming 分支即可，
        // 其余几项动画各自独立、不受影响。
        //
        // 断言写成"必须直接挂"而不是"必须出现某个字符串"：后者在改回分支写法时
        // 也可能因为别处恰好有同串而变绿。同时给反向断言，防两种写法并存。
        require(squash(assistant).contains(
                        ".clipToBounds().animateContentSize(animationSpec=ZhiMotion.sizeSpec),"),
                "AssistantCard 的正文列必须**直接**挂 .animateContentSize(animationSpec = ZhiMotion.sizeSpec)"
                        + "（流式期间也挂 —— 用户要的「流式正文平滑增长」）。"
                        + "⚠️ 回退点：若真机上流式变得一顿一顿，就是这一处改回"
                        + "按 item.streaming 分支；其余动画不受影响");
        require(!squash(assistant).contains("if(item.streaming){Modifier}else{"),
                "AssistantCard 不得退回「streaming 时不挂动画」的分支写法："
                        + "那样流式正文每 32ms 一帧一跳（用户报的「文字是直接没动画」）");

        // ---- 4b. ToolGroupCard：竖向排列 + 收起时内容跟着收 -------------------
        //
        // ⚠️ 这一段在本轮被**整段重写**过，因为原来那三条断言守的是一个**没修好的方案**：
        // 它们的原话里就写着「用户看到的正是『文字没跟着卡片的动画展开/缩回』」——
        // 也就是用户后来重新报的同一件事。守着一个不成立的方案，比没有断言更糟：
        // 它会挡住正确的改法。
        //
        // 用户这次的报法是「工具卡收回，文字就应该跟着收回」。查下去发现底下有**两层**问题：
        //
        //  ① **布局回归**（真正的元凶）：d693dd3 为了去掉卡片底，把根容器从
        //     `Card(...)` 换成了 `Box(...)`。Miuix 的 `Card` 内部就是 `Column(`，
        //     它**同时**提供"竖向排列"与"内边距"；换成 `Box` 时只补回了内边距和
        //     动画挂点，**竖向排列没人补** —— 而 `Box` 是叠放，
        //     于是「表头 + 副标题 + 展开内容」三个兄弟节点落在同一个原点互相盖住。
        //     全程没有任何编译错误，表现只是"标题被内容压住、收起后文字对不上"。
        //     官方那一处是 `linearLayoutVbox3 = vbox()`（竖向 LinearLayout），竖排才是本意。
        //
        //  ② **收起时的动画是坏的**：`if (expanded) { … }` 一翻，内容当帧就被移出组合，
        //     于是只剩外层的框在缩小、里面已经是空的。展开时看着还行（内层按自然高度
        //     绘制、被外层裁剪逐帧露出来），所以这个 bug 只在**收起**方向出现。
        //
        // 修法：根容器改回竖向（`Column`），展开内容交给 `AnimatedVisibility`
        // 自己驱动高度，外层不再挂 `animateContentSize` —— **高度只能有一个来源**，
        // 两层各挂一次就是当初那句「文字在卡片里自己飘」。
        String group = functionBody(cards, "fun ToolGroupCard(");
        require(!group.isEmpty(), CARDS + " 里找不到 ToolGroupCard");
        // ① 根容器必须竖向。⚠️ 反向断言一起给：只查正向的话，
        // 把 `Box` 和 `Column` 各写一个（一个死代码）照样绿。
        require(squash(group).contains("Column(modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)"),
                "ToolGroupCard 的根容器必须是 Column（竖向排列）：官方那一处是 vbox()，"
                        + "而 Box 是**叠放** —— 表头/副标题/展开内容会落在同一个原点互相盖住。"
                        + "⚠️ 别改成 Box：Card→Box 的替换就是这么坏掉的"
                        + "（Miuix 的 Card 内部就是 Column，它在提供内边距的同时也在提供竖排）");
        require(!squash(group).contains("Box(modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)"),
                "ToolGroupCard 的根容器不得是 Box（叠放语义，同上）");
        // ② 展开/收起由内容自己动。
        require(squash(group).contains("AnimatedVisibility(visible=expanded,enter=expandVertically("),
                "ToolGroupCard 的展开内容必须走 AnimatedVisibility(visible = expanded, "
                        + "enter = expandVertically(...), exit = shrinkVertically(...))："
                        + "`if (expanded)` 一翻内容当帧就被移出组合，收起时只剩外框在缩小、"
                        + "里面已经空了 —— 用户报的「工具卡收回，文字没跟着收回」正是这个。"
                        + "⚠️ 这个 bug 只在**收起**方向出现，展开方向看着是好的");
        require(squash(group).contains("shrinkVertically(animationSpec=ZhiMotion.sizeSpec,shrinkTowards=Alignment.Top)"),
                "ToolGroupCard 的收起必须是 shrinkVertically(shrinkTowards = Alignment.Top)："
                        + "内容长在表头下面，要用默认的 Bottom 会看起来像内容把卡片往上顶");
        // ③ 高度只能有一个来源。
        require(!squash(group).contains("if(done){Modifier.animateContentSize("),
                "ToolGroupCard 不许再按 done 门控外层 animateContentSize："
                        + "高度现在由 AnimatedVisibility 一处负责；两层各挂一次会让"
                        + "外框与内层按不同曲线走，看起来就是「文字在卡片里自己飘」");
        require(!squash(group).contains("Modifier.animateContentSize(animationSpec=ZhiMotion.sizeSpec)"),
                "ToolGroupCard 外层不得再挂 animateContentSize（同上：高度只能有一个来源）");
        require(!squash(group).contains("if(expanded){Column(modifier=Modifier.animateContentSize("),
                "ToolGroupCard 的展开列表不许再挂自己的 animateContentSize（同上）");

        String toolRow = functionBody(cards, "private fun ToolRow(");
        require(!toolRow.isEmpty(), CARDS + " 里找不到 ToolRow");
        require(squash(toolRow).contains("if(activity.completed){Modifier.animateContentSize("),
                "ToolRow 必须按 activity.completed 门控 animateContentSize（理由同 ToolGroupCard）");

        // ---- 5. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("MarkdownStreamingTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("MarkdownStreamingTest PASS"
                + "（切点无分配 · 解析按前缀取 key · 行内解析走 remember · 流式不挂尺寸动画）");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }
}

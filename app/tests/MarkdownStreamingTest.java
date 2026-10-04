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
        // ⚠️ 这条断言原先写的是「整个文件里不许出现 remember(source)」，
        //    理由是"那是每个 delta 重解析整篇"。但真正要拦的是**重解析**，
        //    而不是这个 key 本身：前沿淡入需要一个"每 delta 取样一次"的位置
        //    （见 §5 的速率自适应），那里 `remember(source)` 只做一次除法，
        //    完全不碰 parseMarkdown。所以判据收紧到它真正的意图 ——
        //    **解析**不许按 source 取 key。原来的写法会把正当用法一起拦下。
        require(!md.contains("remember(source) { parseMarkdown("),
                MARKDOWN + " 里又出现了 remember(source) { parseMarkdown(…) }："
                        + "那是「每个 delta 重解析整篇」，等于把优化撤销了");
        require(countOccurrences(mdSquashed, "parseMarkdown(") == 1,
                "parseMarkdown 只该有一个调用点（按 split.settled 取 key）："
                        + "多出来的那个会让整篇在流式期间被反复重解析");
        require(md.contains("settledPrefixLength("),
                MARKDOWN + " 必须实际调用 settledPrefixLength 来算切点");
        require(mdSquashed.contains("if(!streaming)returnMdStreamSplit(source,\"\")"),
                "非流式（streaming=false）必须走整段解析：尾部是按行内渲染的，"
                        + "定稿后不整段重解析一次，最后那段的标题/列表/代码块拿不到块级样式");

        // ---- 3. 行内解析不许裸调（必须走 rememberInline / rememberInlineFading）----
        //
        // ⚠️ 本轮多了一个同样带缓存的入口 [rememberInlineFading]（前沿淡入）。
        //    它内部**也是** `remember(text, styles, base, fade) { inline(…) }`，
        //    所以"必须缓存"这条不变，只是计数要认两个名字。
        //    把这两个入口一起算，而不是把阈值调低 —— 调低就变成"少一处也绿"。
        String rememberInline = functionBody(md, "private fun rememberInline(");
        require(!rememberInline.isEmpty(),
                MARKDOWN + " 里找不到 rememberInline —— 行内解析不再被缓存");
        require(squash(rememberInline).contains("remember(text,styles){inline(text,styles)}"),
                "rememberInline 必须用 (text, styles) 作 key 并缓存 inline() 的结果");
        String rememberInlineFading = functionBody(md, "private fun rememberInlineFading(");
        require(!rememberInlineFading.isEmpty(),
                MARKDOWN + " 里找不到 rememberInlineFading（前沿淡入的入口）");
        require(squash(rememberInlineFading).contains("remember(text,styles,base,fade,ramp){"),
                "rememberInlineFading 必须用 (text, styles, base, fade, ramp) 作 key 并缓存："
                        + "它是第二个行内解析入口，不缓存的话同样会每次重组重跑 parseInline");
        // 4 个调用点原本全都是 `text = inline(…)`，现在必须一律走这两个缓存入口。
        // 断言具体的调用形态而不是计数：计数拦不住"只还原其中一个调用点"。
        require(!md.contains("text = inline("),
                "出现了绕过缓存的裸调用 `text = inline(…)`："
                        + "inline() 非 @Composable，内部跑 parseInline + buildAnnotatedString，"
                        + "裸调会让该段落**每次重组**都重跑一遍。");
        int rememberedCalls = countOccurrences(md, "rememberInline(")
                + countOccurrences(md, "rememberInlineFading(");
        require(rememberedCalls >= 6,
                "两个缓存入口加起来至少要出现在 6 处（定义 2 + 标题/列表项/表格单元格 3 个 "
                        + "rememberInline 调用点 + 段落/流式尾部 2 个 rememberInlineFading 调用点），"
                        + "现在只有 " + rememberedCalls + " 处");

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

        // ---- 5. 流式正文的「前沿淡入」---------------------------------------
        //
        // 用户要的是「淡入那种」。这一节钉住实现方式是**空间斜坡**（透明度是位置的函数）
        // 而不是**定时器逐字揭示** —— 这两者观感相近，代价差一个量级：
        //
        //   · 定时器（官方 20ms/字、GetStream 30ms/词）每秒多 20~50 次重组，
        //     而本轮刚把 ChatArea 从 69 次/秒压到 2 次/秒；还会在模型出字快时积压、
        //     越写越追不上（官方自己都得把间隔从 20ms 退化到 32/48ms）；
        //     本质是"文字早到了却压着不放"，等于给用户加延迟。
        //   · 空间斜坡：零额外帧（只在文本变化时重算，而那是流式的固有成本）、
        //     恒不落后、零延迟。
        //
        // ⚠️⚠️ 这一节第二重要的是**宽度必须按时间给**。第一版把宽度写死成 8 个字，
        //    用户反馈"完全看不到"。原因不是没生效，是物理：
        //
        //        可见时长 = 斜坡宽度 ÷ 到达速率
        //
        //    8 个字在 100 字/秒下只有 80ms，而真实流式每个 delta 常一次带进好几个字
        //    —— 最新那几个字往往同一帧内就来齐了，斜坡瞬间走完。
        //    所以下面把"自适应"这条钉死，而不只是钉"有淡入"。
        String mdFading = stripComments(md);

        // ① 两个落点都要有：切过的长消息淡在尾部；没切的短消息淡在最后一个块上。
        //
        // 缺了后者是**静默失效**：短于 MinSettledChars 的正文整段都在 blocks 里，
        // split.tail 是空串 —— 只淡尾部的话短消息一个字都不淡，而且不报任何错。
        require(squash(mdFading).contains(
                        "fadeTail=streaming&&split.tail.isEmpty()&&index==blocks.lastIndex"),
                MARKDOWN + " 的 blocks 循环必须给**最后一个块**传 fadeTail，"
                        + "且判据里要有 split.tail.isEmpty()："
                        + "短于 MinSettledChars 的正文不会被切分，只淡尾部的话短消息一个字都不淡"
                        + "（而且不报任何错）");
        require(squash(mdFading).contains("fade=true,ramp=fadeRamp"),
                MARKDOWN + " 的在写尾部必须真的开淡入（fade = true），并把自适应宽度传进去");

        // ② 淡入**不可以**靠动画或定时器实现 —— 这是这一节的性能契约。
        String fadeBody = functionBody(mdFading, "private fun fadeTailOf(");
        require(!fadeBody.isEmpty(), MARKDOWN + " 里找不到 fadeTailOf");
        String fadingBody = functionBody(mdFading, "private fun rememberInlineFading(");
        require(!fadingBody.isEmpty(), MARKDOWN + " 里找不到 rememberInlineFading");
        for (String banned : new String[]{
                "animateFloat", "animateDp", "animateTo", "Animatable",
                "rememberInfiniteTransition", "withFrameNanos", "withFrameMillis", "delay(",
        }) {
            require(!fadeBody.contains(banned) && !fadingBody.contains(banned),
                    MARKDOWN + " 的淡入实现里不得出现 " + banned + "："
                            + "那说明它退化成了定时器/动画逐帧揭示 —— 每秒多几十次重组，"
                            + "而本轮刚把逐帧重组消灭掉（见 ChatArea 与 DebugHudStructureTest §33）");
        }

        // ③ 宽度必须**按时间**换算，而不是写死字数（第一版就是这里错的）。
        //
        // 这一条与 ④ 是本节的核心：只钉"有淡入"是不够的 —— 第一版确实有淡入，
        // 用户完全看不到。真正决定"看不看得见"的是"斜坡宽度 ÷ 到达速率"。
        String rateBody = functionBody(mdFading, "private class TailFadeRate {");
        require(!rateBody.isEmpty(), MARKDOWN + " 里找不到 TailFadeRate（宽度自适应的估计器）");
        require(squash(rateBody).contains("charsPerSecond*TailFadeMillis/1000f"),
                MARKDOWN + " 的斜坡宽度必须由**到达速率 × 目标时长**算出"
                        + "（charsPerSecond * TailFadeMillis / 1000f）："
                        + "写死字数会让快模型下的淡入只有几十毫秒，肉眼看不见");
        require(squash(rateBody).contains(".coerceIn(TailFadeMinChars,TailFadeMaxChars)"),
                MARKDOWN + " 的斜坡宽度必须夹在 TailFadeMinChars~TailFadeMaxChars："
                        + "不夹的话极快/极慢的流会给出一像素宽或几屏宽的斜坡");
        // ⚠️ 同一份文本重复组合时必须原样返回：否则 dLen = 0 → 速率被 EMA 拉向 0
        //    → 斜坡莫名缩短（而且越重组越短）。
        require(squash(rateBody).contains("if(length==lastLength)returnwidth"),
                MARKDOWN + " 的 TailFadeRate 在同一份文本重复组合时必须原样返回"
                        + "（if (length == lastLength) return width）："
                        + "否则那次 dLen = 0，速率被 EMA 拉向 0，斜坡会越重组越短");
        // 变短 = 跨过块边界/开了新的一段，不该重估（否则换段时淡入忽长忽短）。
        require(squash(rateBody).contains("prevAt==0L||length<prevLength"),
                MARKDOWN + " 的 TailFadeRate 必须在首帧或**文本变短**时沿用旧宽度："
                        + "文本变短 = 跨过块边界开了新的一段，重估会让换段时淡入忽长忽短");
        // EMA：单次突发的 chunk 不该让斜坡瞬间变宽。
        require(squash(rateBody).contains("charsPerSecond*0.6f+rate*0.4f"),
                MARKDOWN + " 的 TailFadeRate 必须对速率做 EMA 平滑："
                        + "一次来 40 个字的突发 chunk 不该让斜坡瞬间变宽");
        // ⚠️ 估计器只能用**普通字段**，不能用 snapshot state。
        require(!rateBody.contains("mutableStateOf") && !rateBody.contains("MutableState"),
                MARKDOWN + " 的 TailFadeRate 不得使用 snapshot state："
                        + "写 state 会自己触发重组，形成「写 → 重组 → 再写」的回路"
                        + "（本工程在 FrameTrace 记过这个坑）");

        // ④ 速率必须按**整段正文**估，且只在文本变化时算。
        //
        // 按 split.tail 估的话，跨过块边界时 tail 归零重算，速率会被估成负数 → 抖。
        // 不在 remember(source) 里算的话，就变成每次重组都取样 → 又是逐帧开销。
        require(squash(mdFading).contains("remember(source){fadeRate.widthFor(source.length,System.nanoTime())}"),
                MARKDOWN + " 的斜坡宽度必须在 remember(source) 里按**整段正文**的长度算："
                        + "key 必须是 source（不是 split.tail —— tail 跨块会归零，速率会被估成负数），"
                        + "且必须包在 remember 里（否则每次重组都取样，又变成逐帧开销）");

        // ⑤ 斜坡只作用末尾 ramp 个字，且**跳过自带语法色的字符**。
        //
        // 跳过这一条是本功能最容易造成的**观感倒退**：不跳的话斜坡的颜色会盖掉
        // 行内「代码」的 primary 色与链接色 —— 末尾那半行里的代码会短暂掉色，
        // 看起来像闪了一下，比不淡还糟。
        require(squash(fadeBody).contains("if(colored.any{index>=it.start&&index<it.end})continue"),
                MARKDOWN + " 的 fadeTailOf 必须跳过已有语法色的字符："
                        + "否则斜坡颜色会盖掉行内代码色/链接色，末尾那半行会短暂掉色");
        require(squash(fadeBody).contains("it.item.color!=null||it.item.background!=null"),
                MARKDOWN + " 识别「自带颜色」必须同时看 color 与 background："
                        + "行内高亮只给底色不给前景色，漏掉 background 就会把高亮盖成正文色");
        require(squash(fadeBody).contains("(length-ramp).coerceAtLeast(0)untillength"),
                MARKDOWN + " 的斜坡必须只扫末尾 ramp 个字："
                        + "整段扫的话长正文每 delta 要加几千个 span");
        require(squash(fadeBody).contains("returnif(touched)builder.toAnnotatedString()elsesource"),
                MARKDOWN + " 在没有可淡的字符时不得返回新对象（整段被语法色占住时省一次拷贝）");

        // ⑥ 斜坡两端必须正好落在「下限」与「全实」上。
        //
        // 写成除以 ramp 的话两端都够不到：末尾那个字不是下限、最前一个也到不了 1
        // —— 那样 TailFadeFloor 这个可调参数就失去意义了（改了看不出变化），
        // 而且注释与代码会对不上。这条是自查时真的写出过这个 bug 才加的。
        String fadeAlphaBody = functionBody(mdFading, "private fun fadeAlpha(");
        require(!fadeAlphaBody.isEmpty(), MARKDOWN + " 里找不到 fadeAlpha");
        require(squash(fadeAlphaBody).contains("(fromEnd-1).toFloat()/(ramp-1)"),
                MARKDOWN + " 的 fadeAlpha 分母必须是 ramp - 1："
                        + "除以 ramp 的话末尾那个字够不到 TailFadeFloor、"
                        + "最前一个也到不了 1.0（下限就变成了一个不起作用的参数）");
        require(squash(fadeAlphaBody).contains("if(fromEnd>=ramp)return1f"),
                MARKDOWN + " 的 fadeAlpha 超出斜坡必须直接返回 1f："
                        + "否则每 delta 都要为整段正文算一遍透明度");

        // ⑦ 「看得见」的几个数：宽度下限、宽度上限、下限透明度、长间隔阈值。
        //
        // 这几个数不是随手取的，它们是**可见性**与**停顿时可读性**之间的取值结果，
        // 所以按值钉住 —— 随手调小任何一个，都会退回"看不见"那一版。
        require(squash(mdFading).contains("privateconstvalTailFadeMinChars=12"),
                MARKDOWN + " TailFadeMinChars 应为 12：太小（比如 8）在快模型下就只有几十毫秒，"
                        + "肉眼看不出淡入 —— 第一版正是这么错的");
        // ⚠️⚠️ 上限这一条是本文件里最容易被"好心调小"的一条：直觉会说"斜坡越宽停顿越难看，
        //    那就压窄一点"，而压窄会**顺带把高到达速率下的时长也压短** ——
        //    第二版把上限设成 28，用户反馈「输出太快会导致动画不明显」正是这个。
        //
        //        可见时长 = 斜坡宽度 ÷ 到达速率
        //
        //    120 字保证 TailFadeMillis 一直守到 285 字/秒，覆盖真实模型与调试 provider
        //    （本工程「调试 · 本地模拟」是 22ms/6 字 ≈ 273 字/秒，必然撞上限）。
        require(squash(mdFading).contains("privateconstvalTailFadeMaxChars=120"),
                MARKDOWN + " TailFadeMaxChars 应为 120："
                        + "它的作用**不是**压短停顿时的半透明那一截（那样做会顺带压短"
                        + "高到达速率下的时长：40 字/秒→420ms、100 字/秒→280ms、"
                        + "273 字/秒→103ms，用户报的「输出太快动画不明显」就是这个），"
                        + "而只是给极端突发兜底。120 字 ≈ 3 行，且 TailFadeMillis 一直守到 285 字/秒");
        require(squash(mdFading).contains("privateconstvalTailFadeFloor=0.30f"),
                MARKDOWN + " TailFadeFloor 应为 0.30f：第一版的 0.52 只有半档变化、"
                        + "本来就看不出；调得更低又会让停顿期间那几个字读不清");
        // ⚠️ 与"上限"同源的另一个坑：**模型思考的停顿**会污染速率估计。
        //    停顿结束后第一个 delta 的间隔里大部分是思考时间，拿它算会得出几字/秒，
        //    于是斜坡塌到下限、接下来几个 delta 才被 EMA 拉回来 —— 观感就是
        //    "刚恢复输出那一下，淡入突然变窄又变宽"。这个样本说的不是流式节奏，直接不要。
        require(squash(rateBody).contains("if(seconds>TailFadeMaxGapSeconds)returnwidth"),
                MARKDOWN + " 的 TailFadeRate 必须在采样间隔过长时跳过估计"
                        + "（if (seconds > TailFadeMaxGapSeconds) return width）："
                        + "模型思考的停顿会被算成「出字很慢」，让斜坡塌到下限，"
                        + "恢复输出那一下淡入会突然变窄再变宽");

        // ⑧ 定稿的消息一分钱都不多付：fade 为假时必须原样返回 inline 的结果。
        require(squash(fadingBody).contains("if(fade)fadeTailOf(annotated,base,ramp)elseannotated"),
                MARKDOWN + " 的 rememberInlineFading 必须在 fade 为假时原样返回："
                        + "定稿/历史消息占绝大多数，不该为它们建 span 或拷贝 AnnotatedString");

        // ⑨ 淡入只落在段落上，别顺手摊到其它块类型。
        String paragraph = squash(functionBody(mdFading, "is MdBlock.Paragraph ->"));
        require(!paragraph.isEmpty(), MARKDOWN + " 里找不到 Paragraph 分支");
        require(paragraph.contains("fade=fadeTail,ramp=fadeRamp"),
                MARKDOWN + " 的 Paragraph 必须接上 fadeTail 与 fadeRamp"
                        + "（在写正文最常见、也是唯一常见的形状）");
        require(!squash(mdFading).contains("fade=true,ramp=fadeRamp,},"),
                MARKDOWN + " 不得把淡入无条件摊到所有块类型上："
                        + "标题/列表/引用/代码块在被写出来的一瞬间本来就是跳着出现的，"
                        + "给它们加前沿淡入看不出差别，只会多付 span 开销");


        // ---- 6. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("MarkdownStreamingTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("MarkdownStreamingTest PASS"
                + "（切点无分配 · 解析按前缀取 key · 行内解析走 remember · "
                + "流式正文前沿淡入且不引入定时器）");
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

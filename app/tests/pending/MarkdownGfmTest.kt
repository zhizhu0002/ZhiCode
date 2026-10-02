package com.zhizhu.zhicode.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 的 **GFM 合规**测试。
 *
 * ## 为什么拿规范当断言，而不是"看着对就行"
 *
 * GFM 0.29-gfm 是一份**带示例的正式规范**（每个例子都给出 Markdown 与 HTML 对照）。
 * 拿它转写成断言有三个好处：
 *
 * 1. **"支持 GFM"变成可验证的**：否则它只是一句话，谁都能说自己支持；
 * 2. **边界情况有唯一答案**：`*a **b** c*`、`~x~`、`www.a.com.` 这类地方靠肉眼吵不出结论，
 *    而规范给的是确定答案；
 * 3. **回归能被挡住**：改解析器最容易犯的错不是"崩"，而是**某些输入悄悄变了意思**
 *    （例如把 `\*` 又当成强调标记），那种错编译通过、界面不报错。
 *
 * 用例名与注释里的小节号（§6.4、§4.10…）指向 GFM 规范，方便逐条回查。
 *
 * ## 投影：为什么断言写成"伪 HTML"
 *
 * 我们的产出是 AST 而不是 HTML，所以这里把 AST 投影成**伪 HTML** 再断言：
 * `<em>` / `<strong>` / `<code>` / `<del>` / `<a href>`，块级用 `h1(x)` / `p(x)` / `li0(x)`。
 * 之所以不把行内片段还原成 `**粗**` 这类标记：那种投影**有歧义** ——
 * `**foo*` 与"一个字面星号 + 斜体"投影出来是同一个字符串，断言就失去了分辨力。
 * 用 <em> 这种显式括号，一条断言只对应一种树。
 *
 * 投影只存在于本测试文件，**不参与生产渲染**。
 *
 * ## 明确不在本文件里的
 *
 * 规范里与我们渲染模型无关的部分：HTML 块 / 行内 HTML / 危险标签过滤 / 图片像素输出。
 * 这些取舍写在 `MarkdownParse.kt` 文件头的"与 GFM 的差异"一节。
 */
class MarkdownGfmTest {

    // ------------------------------------------------------------------ 投影

    /** 行内片段 → 伪 HTML。连续同目标的片段合并进一个 `<a>`。 */
    private fun inl(src: String, links: Map<String, MdLink> = emptyMap()): String =
        project(parseInline(src, links))

    private fun project(spans: List<MdSpan>): String {
        val out = StringBuilder()
        var i = 0
        while (i < spans.size) {
            val href = spans[i].href
            if (href != null) {
                val group = ArrayList<MdSpan>()
                while (i < spans.size && spans[i].href == href) {
                    group += spans[i]
                    i++
                }
                out.append("<a href=\"").append(href).append("\">")
                    .append(group.joinToString("") { tag(it) })
                    .append("</a>")
            } else {
                out.append(tag(spans[i]))
                i++
            }
        }
        return out.toString()
    }

    /**
     * 单个片段的样式括号。
     *
     * 组合样式（`***x***`）在 AST 里是一个片段（`BOLD_ITALIC`），
     * 而规范给的是嵌套（`<em><strong>x</strong></em>`）。渲染结果两者等价，
     * 这里按规范那种嵌套形状投影，好让断言读起来仍像规范。
     */
    private fun tag(span: MdSpan): String = when (span.style) {
        MdStyle.PLAIN, MdStyle.LINK -> span.text
        MdStyle.BOLD -> "<strong>${span.text}</strong>"
        MdStyle.ITALIC -> "<em>${span.text}</em>"
        MdStyle.BOLD_ITALIC -> "<em><strong>${span.text}</strong></em>"
        MdStyle.CODE -> "<code>${span.text}</code>"
        MdStyle.STRIKE -> "<del>${span.text}</del>"
    }

    /** 整篇 → 紧凑文本；块之间用 `|` 分隔。块内容保持**源码原文**（行内另测）。 */
    private fun dump(src: String): String = dumpBlocks(parseMarkdown(src))

    private fun dumpBlocks(blocks: List<MdBlock>): String = blocks.joinToString("|") { block ->
        when (block) {
            is MdBlock.Heading -> "h${block.level}(${block.text})"
            is MdBlock.Paragraph -> "p(${block.text})"
            is MdBlock.Bullet -> {
                val task = when (block.task) {
                    true -> "[x]"
                    false -> "[ ]"
                    null -> ""
                }
                "li${block.depth}$task(${block.marker}${block.text})"
            }
            is MdBlock.Quote -> "quote(${dumpBlocks(block.blocks)})"
            MdBlock.Rule -> "hr"
            is MdBlock.Code -> "code(${block.lang}){${block.body}}"
            is MdBlock.Table -> {
                val align = block.aligns.joinToString("") { it.name.first().toString().lowercase() }
                "table($align)[" + block.header.joinToString(",") + "]" +
                    block.rows.joinToString("") { "[" + it.joinToString(",") + "]" }
            }
        }
    }

    // ================================================================== §4.1 分隔线

    @Test
    fun `§4-1 三种标记都是分隔线`() {
        assertEquals("hr|hr|hr", dump("***\n---\n___"))
    }

    @Test
    fun `§4-1 不满足条件的不算分隔线`() {
        assertEquals("p(+++)", dump("+++"))
        assertEquals("p(--)", dump("--"))
        assertEquals("p(**)", dump("**"))
        assertEquals("p(__)", dump("__"))
    }

    @Test
    fun `§4-1 允许零到三个空格缩进`() {
        assertEquals("hr|hr|hr", dump("***\n ***\n  ***"))
    }

    @Test
    fun `§4-1 四个空格是缩进代码块（示例 18）`() {
        assertEquals("code(){***}", dump("    ***"))
    }

    @Test
    fun `§4-1 字符之间可以有空格但不得混入其它字符`() {
        assertEquals("hr", dump("- - -"))
        assertEquals("p(_ _ _ _ a)", dump("_ _ _ _ a"))
    }

    @Test
    fun `§4-1 星号既可能是分隔线也可能是强调（示例 26）`() {
        assertEquals("p(*-*)", dump("*-*"))
        assertEquals("<em>-</em>", inl("*-*"))
    }

    @Test
    fun `§4-1 Setext 优先于分隔线（示例 29）`() {
        assertEquals("h2(Foo)|p(bar)", dump("Foo\n---\nbar"))
    }

    @Test
    fun `§4-1 分隔线优先于列表项（示例 30）`() {
        assertEquals("li0(*Foo)|hr|li0(*Bar)", dump("* Foo\n* * *\n* Bar"))
    }

    // ================================================================== §4.2 ATX 标题

    @Test
    fun `§4-2 一到六级标题`() {
        assertEquals(
            "h1(a)|h2(b)|h3(c)|h4(d)|h5(e)|h6(f)",
            dump("# a\n## b\n### c\n#### d\n##### e\n###### f"),
        )
    }

    @Test
    fun `§4-2 七个井号不是标题（示例 33）`() {
        assertEquals("p(####### foo)", dump("####### foo"))
    }

    @Test
    fun `§4-2 井号后必须有空格（示例 34）`() {
        assertEquals("p(#5 bolt)|p(#hashtag)", dump("#5 bolt\n#hashtag"))
    }

    @Test
    fun `§4-2 被转义的井号不是标题（示例 35）`() {
        assertEquals("p(## foo)", dump("\\## foo"))
    }

    @Test
    fun `§4-2 标题内容按行内解析（示例 36）`() {
        assertEquals("h1(foo *bar* \\*baz\\*)", dump("# foo *bar* \\*baz\\*"))
        assertEquals("foo <em>bar</em> *baz*", inl("foo *bar* \\*baz\\*"))
    }

    @Test
    fun `§4-2 允许零到三个空格缩进，四个是缩进代码块（示例 38）`() {
        assertEquals("h3(foo)|h2(foo)|h1(foo)", dump("   ### foo\n  ## foo\n # foo"))
        assertEquals("code(){# foo}", dump("    # foo"))
    }

    @Test
    fun `§4-2 结尾的井号序列要有空格分隔且只能跟在内容后`() {
        assertEquals("h2(foo)", dump("## foo ##"))
        assertEquals("h1(foo)", dump("# foo ##################################"))
        assertEquals("h3(foo ### b)", dump("### foo ### b"))
    }

    // ================================================================== §4.3 Setext 标题

    @Test
    fun `§4-3 两种下划线给出一级和二级`() {
        assertEquals("h1(Foo)|h2(Bar)", dump("Foo\n===\nBar\n---"))
    }

    @Test
    fun `§4-3 标题可以跨多行`() {
        assertEquals("h2(Foo bar)", dump("Foo\nbar\n---"))
    }

    @Test
    fun `§4-3 前面没有段落时下划线不是标题`() {
        assertEquals("hr|p(baz)", dump("===\nbaz"))
    }

    // ================================================================== §4.4 缩进代码块

    @Test
    fun `§4-4 四个空格开始缩进代码块（示例 1）`() {
        assertEquals("code(){a simple\n  indented code block}", dump("    a simple\n      indented code block"))
    }

    @Test
    fun `§4-4 缩进代码块内部可以有空行（示例 8）`() {
        assertEquals("code(){chunk1\n\nchunk2}|p(更多)", dump("    chunk1\n\n    chunk2\n\n更多"))
    }

    @Test
    fun `§4-4 不能打断段落（示例 66）`() {
        assertEquals("p(Foo\n    bar)", dump("Foo\n    bar"))
    }

    @Test
    fun `§4-4 缩进代码块里的空格原样保留`() {
        assertEquals("code(){  two leading spaces}", dump("      two leading spaces"))
    }

    @Test
    fun `§4-4 单元格里不解析块级结构`() {
        assertEquals("table(l)[# not heading]", dump("| # not heading |\n| --- |"))
    }

    // ================================================================== §4.10 表格

    @Test
    fun `§4-10 基本表格（示例 198）`() {
        assertEquals("table(ll)[foo,bar][baz,bim]", dump("| foo | bar |\n| --- | --- |\n| baz | bim |"))
    }

    @Test
    fun `§4-10 分隔行的冒号决定对齐（示例 199）`() {
        assertEquals("table(cr)[abc,defghi][bar,baz]", dump("| abc | defghi |\n:-: | -----------:\nbar | baz"))
    }

    @Test
    fun `§4-10 表格可以不带首尾竖线`() {
        assertEquals("table(ll)[foo,bar][baz,bim]", dump("foo | bar\n--- | ---\nbaz | bim"))
    }

    @Test
    fun `§4-10 单元格里的竖线要转义（示例 200）`() {
        assertEquals(
            "table(l)[f\\|oo][b `\\|` az][b **\\|** im]",
            dump("| f\\|oo |\n| ------ |\n| b `\\|` az |\n| b **\\|** im |"),
        )
        // 转义要活到行内解析：代码里、强调里都应为字面竖线
        assertEquals("|", inl("\\|"))
        assertEquals("<code>|</code>", inl("`\\|`"))
        assertEquals("<strong>|</strong>", inl("**\\|**"))
    }

    @Test
    fun `§4-10 遇到另一个块级结构时表格结束（示例 201）`() {
        assertEquals(
            "table(ll)[abc,def][bar,baz]|quote(p(bar))",
            dump("| abc | def |\n| --- | --- |\n| bar | baz |\n> bar"),
        )
    }

    @Test
    fun `§4-10 没有竖线的行仍是表格行（示例 202）`() {
        assertEquals(
            "table(ll)[abc,def][bar,baz][bar,]|p(bar)",
            dump("| abc | def |\n| --- | --- |\n| bar | baz |\nbar\n\nbar"),
        )
    }

    @Test
    fun `§4-10 表头与分隔行格数不一致就不是表格（示例 203）`() {
        assertEquals("p(| abc | def |\n| --- |)|p(| bar |)", dump("| abc | def |\n| --- |\n| bar |"))
    }

    @Test
    fun `§4-10 正文行多余单元格被忽略，缺少的补空（示例 204）`() {
        assertEquals(
            "table(ll)[abc,def][bar,][bar,baz]",
            dump("| abc | def |\n| --- | --- |\n| bar |\n| bar | baz | boo |"),
        )
    }

    @Test
    fun `§4-10 没有正文行也是合法表格（示例 205）`() {
        assertEquals("table(ll)[abc,def]", dump("| abc | def |\n| --- | --- |"))
    }

    // ================================================================== §5.1 引用块

    @Test
    fun `§5-1 引用里可以有块级元素（示例 228）`() {
        assertEquals("quote(h1(Foo)|p(bar\nbaz))", dump("> # Foo\n> bar\n> baz"))
    }

    @Test
    fun `§5-1 段落行可以省略尖括号（懒续行，示例 229）`() {
        assertEquals("quote(h1(Foo)|p(bar\nbaz))", dump("> # Foo\n> bar\nbaz"))
    }

    @Test
    fun `§5-1 引用里的缩进是相对于引用的（示例 232）`() {
        assertEquals("quote(code(){code})|quote(p(not code))", dump(">     code\n\n>    not code"))
    }

    // ================================================================== §5.2/§5.4 列表

    @Test
    fun `§5-2 两格缩进构成嵌套（示例 264）`() {
        assertEquals("li0(*a)|li1(*b)", dump("- a\n  - b"))
    }

    @Test
    fun `§5-2 四格缩进也只加一层（相对父项内容缩进）`() {
        assertEquals("li0(*a)|li1(*b)|li2(*c)", dump("- a\n    - b\n        - c"))
    }

    @Test
    fun `§5-2 有序列表的标记宽度决定嵌套缩进`() {
        assertEquals("li0(1. a)|li1(*b)", dump("1. a\n   - b"))
    }

    @Test
    fun `§5-2 列表项可以有多个段落（示例 257）`() {
        assertEquals("li0(- foo\n\n  bar)", dump("- foo\n\n  bar"))
    }

    @Test
    fun `§5-4 换标记符就是新列表（示例 301）`() {
        assertEquals("li0(*foo)|li0(*bar)", dump("- foo\n- bar"))
        assertEquals("li0(-baz)", dump("+ baz"))
    }

    @Test
    fun `§5-4 起始编号由第一项决定，后续编号按顺序（示例 304）`() {
        assertEquals("li0(3. a)|li0(4. b)|li0(5. c)", dump("3. a\n4. b\n5. c"))
    }

    @Test
    fun `§5-4 后续编号一律重排，不沿用源码里的数字（示例 306）`() {
        assertEquals("li0(1. a)|li0(2. b)", dump("1. a\n1. b"))
    }

    @Test
    fun `§5-4 编号最多九位（示例 309）`() {
        assertEquals("li0(123456789. ok)", dump("123456789. ok"))
        assertEquals("p(1234567890. not ok)", dump("1234567890. not ok"))
    }

    // ================================================================== §5.3 任务项

    @Test
    fun `§5-3 任务项标记（示例 279）`() {
        assertEquals("li0[ ](*foo)|li0[x](*bar)", dump("- [ ] foo\n- [x] bar"))
    }

    @Test
    fun `§5-3 大写 X 也算（示例 281）`() {
        assertEquals("li0[x](*foo)|li0[x](*bar)|li0[ ](*baz)", dump("- [X] foo\n- [x] bar\n- [ ] baz"))
    }

    @Test
    fun `§5-3 方括号后必须紧跟空白（示例 282）`() {
        assertEquals("li0(*[ ]foo)", dump("- [ ]foo"))
    }

    @Test
    fun `§5-3 标记必须是该段落的第一件事（示例 283）`() {
        assertEquals("li0(*foo\n  [ ] bar)", dump("- foo\n  [ ] bar"))
    }

    @Test
    fun `§5-3 嵌套任务项`() {
        assertEquals("li0[ ](*a)|li1[x](*b)", dump("- [ ] a\n  - [x] b"))
    }

    // ================================================================== §6.1 反斜杠转义

    @Test
    fun `§6-1 全部 ASCII 标点都可以转义（示例 12）`() {
        val punct = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        assertEquals(punct, inl(punct.map { "\\$it" }.joinToString("")))
    }

    @Test
    fun `§6-1 转义只对 ASCII 标点生效（示例 13）`() {
        assertEquals("\\A", inl("\\A"))
    }

    @Test
    fun `§6-1 转义的反斜杠后面的强调仍然生效（示例 14）`() {
        assertEquals("\\<em>emphasis</em>", inl("\\\\*emphasis*"))
    }

    @Test
    fun `§6-1 转义的强调标记按普通字符输出`() {
        assertEquals("*not emphasized*", inl("\\*not emphasized*"))
    }

    // ================================================================== §6.2 实体

    @Test
    fun `§6-2 命名实体与数字实体都要解码（示例 25）`() {
        assertEquals("& < > \" ' ' ©", inl("&amp; &lt; &gt; &quot; &apos; &#39; &copy;"))
    }

    @Test
    fun `§6-2 十六进制数字实体`() {
        assertEquals("A", inl("&#x41;"))
    }

    @Test
    fun `§6-2 无效实体原样保留（示例 30）`() {
        assertEquals("&MadeUpEntity;", inl("&MadeUpEntity;"))
        assertEquals("&amp", inl("&amp"))
    }

    @Test
    fun `§6-2 代码里不解析实体（示例 35）`() {
        assertEquals("<code>&amp;</code>", inl("`&amp;`"))
    }

    @Test
    fun `§6-2 链接目标里的实体要解码（示例 33）`() {
        assertEquals("<a href=\"/f\u00f6\u00f6\">a</a>", inl("[a](/f&ouml;&ouml;)"))
    }

    // ================================================================== §6.3 行内代码

    @Test
    fun `§6-3 反引号数量必须匹配（示例 331）`() {
        assertEquals("<code>foo</code>", inl("`foo`"))
        assertEquals("<code>foo ` bar</code>", inl("`` foo ` bar ``"))
    }

    @Test
    fun `§6-3 首尾各去掉一个空格（示例 334）`() {
        assertEquals("<code>foo</code>", inl("` foo `"))
        assertEquals("<code> foo </code>", inl("`  foo  `"))
    }

    @Test
    fun `§6-3 未闭合的反引号原样输出（示例 340）`() {
        assertEquals("`foo", inl("`foo"))
    }

    // ================================================================== §6.4 强调

    @Test
    fun `§6-4 基本强调与加粗（示例 350）`() {
        assertEquals("<em>foo bar</em>", inl("*foo bar*"))
        assertEquals("<strong>foo bar</strong>", inl("**foo bar**"))
    }

    @Test
    fun `§6-4 开标记后是空白则不能开启（示例 361）`() {
        assertEquals("a * foo bar*", inl("a * foo bar*"))
    }

    @Test
    fun `§6-4 只有一半的标记按字面输出（示例 374）`() {
        // `**foo*` 不是"加粗失败"：规范给的是"一个字面星号 + 一个斜体"
        assertEquals("*<em>foo</em>", inl("**foo*"))
    }

    @Test
    fun `§6-4 外层短标记包住内层长标记`() {
        assertEquals("<em>a <strong>b</strong> c</em>", inl("*a **b** c*"))
    }

    @Test
    fun `§6-4 相邻分隔符串按最合适的方式配对（示例 407）`() {
        assertEquals("<em>foo<strong>bar</strong>baz</em>", inl("*foo**bar**baz*"))
        assertEquals("<em>foo**bar</em>", inl("*foo**bar*"))
        assertEquals("<strong>foo*bar</strong>", inl("**foo*bar**"))
    }

    @Test
    fun `§6-4 三连星号既是斜体又是加粗（示例 410）`() {
        assertEquals("<em><strong>foo</strong></em>", inl("***foo***"))
        assertEquals("foo<em><strong>bar</strong></em>baz", inl("foo***bar***baz"))
    }

    @Test
    fun `§6-4 下划线不参与词内强调（示例 360）`() {
        assertEquals("foo_bar_baz", inl("foo_bar_baz"))
        assertEquals("<em>foo_bar</em>", inl("_foo_bar_"))
    }

    @Test
    fun `§6-4 星号参与词内强调（示例 361 附近）`() {
        assertEquals("5<em>6</em>78", inl("5*6*78"))
    }

    @Test
    fun `§6-4 标点包围时分隔符串仍可配对（示例 393）`() {
        assertEquals("<em>(<em>foo</em>)</em>", inl("*(*foo*)*"))
        assertEquals("<em>foo</em>bar", inl("*foo*bar"))
    }

    @Test
    fun `§6-4 强调里可以嵌链接，链接文字里可以嵌强调`() {
        assertEquals("<em>foo <a href=\"/url\">bar</a></em>", inl("*foo [bar](/url)*"))
        assertEquals("<a href=\"/u\"><strong>bold</strong> link</a>", inl("[**bold** link](/u)"))
    }

    // ================================================================== §6.5 删除线

    @Test
    fun `§6-5 两个波浪号是删除线（规范示例）`() {
        assertEquals(
            "<del>Hi</del> Hello, <del>there</del> world!",
            inl("~~Hi~~ Hello, ~there~ world!"),
        )
    }

    @Test
    fun `§6-5 一个波浪号也是删除线（规范示例）`() {
        assertEquals("<del>Hi</del> Hello, world!", inl("~Hi~ Hello, world!"))
    }

    @Test
    fun `§6-5 三个及以上波浪号不是删除线（规范示例）`() {
        assertEquals("This will ~~~not~~~ strike.", inl("This will ~~~not~~~ strike."))
    }

    @Test
    fun `§6-5 必须是数量相当的一对（"matching pair"）`() {
        // 规范原文是 "wrapped in a matching pair of one or two tildes"：
        // 数量不等就不算一对。规范示例没给出这种输入，判据取自这句话本身。
        assertEquals("~~foo~", inl("~~foo~"))
        assertEquals("~foo~~", inl("~foo~~"))
    }

    @Test
    fun `§6-5 删除线不跨段落（规范示例）`() {
        assertEquals("p(This ~~has a)|p(new paragraph~~.)", dump("This ~~has a\n\nnew paragraph~~."))
    }

    // ================================================================== §6.6/§6.7 链接与图片

    @Test
    fun `§6-6 行内链接与标题（示例 481）`() {
        assertEquals("<a href=\"/uri\">link</a>", inl("[link](/uri \"title\")"))
        // 空目标：不是可点链接（href 为 null），但文字仍是链接样式
        assertEquals("link", inl("[link]()"))
    }

    @Test
    fun `§6-6 尖括号包住的链接目标（示例 489）`() {
        assertEquals("<a href=\"/my uri\">link</a>", inl("[link](</my uri>)"))
    }

    @Test
    fun `§6-7 图片输出替代文字与目标（示例 573）`() {
        // 刻意不出像素：不联网加载远程图片，替代文字做成可点链接（见文件头差异一节）
        assertEquals("<a href=\"/url\">foo</a>", inl("![foo](/url \"title\")"))
    }

    @Test
    fun `§6-6 引用式链接定义不产出块，三种写法都能解析`() {
        val doc = parseDocument("[foo]: /url \"title\"\n\n[foo]\n\n[foo][]\n\n[bar][foo]")
        assertEquals("p([foo])|p([foo][])|p([bar][foo])", dumpBlocks(doc.blocks))
        val links = doc.links
        assertEquals("<a href=\"/url\">foo</a>", inl("[foo]", links))
        assertEquals("<a href=\"/url\">foo</a>", inl("[foo][]", links))
        assertEquals("<a href=\"/url\">bar</a>", inl("[bar][foo]", links))
    }

    @Test
    fun `§6-6 引用标签大小写不敏感、空白折叠`() {
        val doc = parseDocument("[FOO  Bar]: /url\n\n[x]")
        assertEquals("<a href=\"/url\">x</a>", inl("[foo bar]", doc.links))
    }

    @Test
    fun `§6-6 没有定义的引用按字面输出（示例 546）`() {
        assertEquals("[foo]", inl("[foo]"))
        assertEquals("[foo][bar]", inl("[foo][bar]"))
    }

    // ================================================================== §6.8/§6.9 自动链接

    @Test
    fun `§6-8 尖括号自动链接与邮箱（示例 594）`() {
        assertEquals("<a href=\"https://foo.bar.baz\">https://foo.bar.baz</a>", inl("<https://foo.bar.baz>"))
        assertEquals(
            "<a href=\"mailto:foo@bar.example.com\">foo@bar.example.com</a>",
            inl("<foo@bar.example.com>"),
        )
    }

    @Test
    fun `§6-9 www 自动链接补全协议（示例 621）`() {
        assertEquals(
            "<a href=\"http://www.commonmark.org\">www.commonmark.org</a>",
            inl("www.commonmark.org"),
        )
    }

    @Test
    fun `§6-9 www 后面可以跟路径（示例 622）`() {
        assertEquals(
            "Visit <a href=\"http://www.commonmark.org/help\">www.commonmark.org/help</a> for more information.",
            inl("Visit www.commonmark.org/help for more information."),
        )
    }

    @Test
    fun `§6-9 结尾标点不并入链接（示例 623）`() {
        assertEquals(
            "Visit <a href=\"http://www.commonmark.org\">www.commonmark.org</a>.",
            inl("Visit www.commonmark.org."),
        )
        assertEquals(
            "Visit <a href=\"http://www.commonmark.org/a.b\">www.commonmark.org/a.b</a>.",
            inl("Visit www.commonmark.org/a.b."),
        )
    }

    @Test
    fun `§6-9 结尾右括号按括号配平（示例 624）`() {
        val url = "www.google.com/search?q=Markup+(business)"
        val link = "<a href=\"http://$url\">$url</a>"
        assertEquals(link, inl(url))
        assertEquals("$link))", inl("$url))"))
        assertEquals("($link)", inl("($url)"))
        assertEquals("($link", inl("($url"))
    }

    @Test
    fun `§6-9 括号都在内部时不做特殊处理（示例 625）`() {
        val mid = "www.google.com/search?q=(business))+ok"
        assertEquals("<a href=\"http://$mid\">$mid</a>", inl(mid))
    }

    @Test
    fun `§6-9 像实体的结尾要排除（示例 626）`() {
        assertEquals(
            "<a href=\"http://www.google.com/search?q=commonmark&hl=en\">" +
                "www.google.com/search?q=commonmark&hl=en</a>",
            inl("www.google.com/search?q=commonmark&hl=en"),
        )
        assertEquals(
            "<a href=\"http://www.google.com/search?q=commonmark\">" +
                "www.google.com/search?q=commonmark</a>&hl;",
            inl("www.google.com/search?q=commonmark&hl;"),
        )
    }

    @Test
    fun `§6-9 小于号终止自动链接（示例 627）`() {
        assertEquals("<a href=\"http://www.commonmark.org/he\">www.commonmark.org/he</a><lp", inl("www.commonmark.org/he<lp"))
    }

    @Test
    fun `§6-9 带协议的扩展自动链接（示例 628）`() {
        assertEquals(
            "<a href=\"http://commonmark.org\">http://commonmark.org</a>",
            inl("http://commonmark.org"),
        )
        val url = "https://encrypted.google.com/search?q=Markup+(business)"
        assertEquals("(Visit <a href=\"$url\">$url</a>)", inl("(Visit $url)"))
    }

    @Test
    fun `§6-9 邮箱自动链接（示例 629-631）`() {
        assertEquals("<a href=\"mailto:foo@bar.baz\">foo@bar.baz</a>", inl("foo@bar.baz"))
        assertEquals(
            "<a href=\"mailto:hello+xyz@mail.example\">hello+xyz@mail.example</a>",
            inl("hello+xyz@mail.example"),
        )
        assertEquals(
            "<a href=\"mailto:a.b-c_d@a.b\">a.b-c_d@a.b</a>.",
            inl("a.b-c_d@a.b."),
        )
        assertEquals("a.b-c_d@a.b-", inl("a.b-c_d@a.b-"))
    }

    @Test
    fun `§6-9 自动链接只能在行首或特定字符之后`() {
        assertEquals("foohttps://bar", inl("foohttps://bar"))
    }

    // ================================================================== §6.12/§6.13 换行

    @Test
    fun `§6-12 行尾反斜杠是硬换行，反斜杠本身消失（示例 636）`() {
        assertEquals("p(foo\nbaz)", dump("foo\\\nbaz"))
    }

    @Test
    fun `§6-12 行尾两个空格也是硬换行（示例 637）`() {
        assertEquals("p(foo\nbaz)", dump("foo  \nbaz"))
    }

    @Test
    fun `§6-13 软换行按换行渲染（规范允许行尾或空格两种）`() {
        // 规范原文允许"行尾"或"空格"两种渲染；本工程选行尾，
        // 理由是提示卡用 \n 表达"一行一项"（合并成空格会糊成一段）
        assertEquals("p(foo\nbaz)", dump("foo\nbaz"))
    }

    // ================================================================== 不丢字符

    @Test
    fun `未闭合的围栏与强调都不能丢字符`() {
        val rendered = dump("```kotlin\nval unclosed = 1\n\n**没有收尾 与 *只有一半\n")
        assertTrue("未闭合围栏必须仍然产出代码块：$rendered", rendered.contains("code(kotlin)"))
        assertTrue("未闭合的强调必须原样留着：$rendered", rendered.contains("**没有收尾"))
    }
}

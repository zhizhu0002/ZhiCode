import java.nio.file.*;

/**
 * 终端面板的**行为契约**：外壳改用 Compose 重写（批 F 2/6）之后，
 * 这些字符串与语义必须一个不少。
 *
 * <p>为什么需要它：终端是唯一一块「重写后无法用单测证明行为没变」的地方 ——
 * 它是手势 + IME + PTY 的组合。编译通过只能说明类型对得上，说明不了
 * 「按 ESC 发出去的还是 `\u001b`」「CTRL 按一次仍然只管一个键」。
 *
 * <p>所以这里钉住三件**改坏了不报错、只会在真机上表现为"终端不好用"** 的东西：
 * <ol>
 *   <li>12 个扩展键的转义序列精确值 —— 这是发给 shell 的字节，差一个字符
 *       就是"按方向键出现 ^[[A"这类让人莫名其妙的现象；</li>
 *   <li>11 项快捷动作的**标签与顺序** —— 调用方按下标分发，重排会让
 *       "字体变大"点成"杀掉 shell"（不可逆）；</li>
 *   <li>四个修饰键的"读后即清"语义 —— TerminalView 依赖它，改成不清会让
 *       CTRL 永久卡住，之后每个键都被当成控制键。</li>
 * </ol>
 *
 * <p>检查的是「这个文件最终会做什么」而不是「作者把这些字打在哪儿」：
 * 因此对字面量做拼接式检查（见 {@link #literals}），换行与缩进不构成契约。
 */
public final class TerminalPaneContractTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** 去掉全部空白后再比较：断言关心的是标识符与先后关系，不该被空格/换行左右。 */
    private static String squash(String source) { return source.replaceAll("\\s+", ""); }

    /**
     * 把源码里所有双引号字面量的内容按出现次序拼起来。
     *
     * <p>转义序列在源码里是 `"\u001b[5~"` 这种写法，而断言要检查的是**实际发出去的字节**。
     * 对源码直接 contains 会把「用的是 `\u001b` 还是 `\033`」变成契约 ——
     * 两者等价，重写不该因为换个写法就红。
     */
    private static String literals(String source) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            if (source.charAt(i) != '"') { i++; continue; }
            i++;
            while (i < source.length()) {
                char c = source.charAt(i);
                if (c == '\\') {
                    if (i + 1 >= source.length()) break;
                    char next = source.charAt(i + 1);
                    switch (next) {
                        case 'u':
                            // 反斜杠 u + 四位十六进制 → 真正的字符
                            // （注意：这段注释里不能出现那个序列本身，
                            //   javac 连注释里的它都会当转义处理）
                            if (i + 5 < source.length()) {
                                try {
                                    out.append((char) Integer.parseInt(source.substring(i + 2, i + 6), 16));
                                    i += 6;
                                    continue;
                                } catch (NumberFormatException ignored) {
                                    // 不是合法码点就按普通转义处理
                                }
                            }
                            out.append(next);
                            i += 2;
                            continue;
                        case 't': out.append('\t'); i += 2; continue;
                        case 'r': out.append('\r'); i += 2; continue;
                        case 'n': out.append('\n'); i += 2; continue;
                        case '\\': out.append('\\'); i += 2; continue;
                        case '"': out.append('"'); i += 2; continue;
                        default: out.append(next); i += 2; continue;
                    }
                }
                if (c == '"') { i++; break; }
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 去掉注释后的源码。
     *
     * <p>负向断言（"绝不能出现 X"）必须看**代码**而不是注释：本文件多处注释刻意写下了
     * 历史上出过的错（例如 `onDispose { pane.closeAll() }`），把注释也算进去的话，
     * 一段"解释为什么不那么写"的注释反而会把断言弄红。
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length()
                        && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String hostPath = "app/src/main/java/com/zhizhu/zhicode/TermuxTerminalPane.java";
        String host = Files.readString(root.resolve(hostPath));
        String hostSquashed = squash(host);
        // 转义序列是"字符"而不是"文本"，只能用真实字面量比对，不能用 squash 后的源码。
        String hostLiterals = literals(host);

        String chromePath = "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/TerminalChrome.kt";
        String chrome = Files.readString(root.resolve(chromePath));
        String dialogsPath = "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/TerminalDialogs.kt";
        String dialogs = Files.readString(root.resolve(dialogsPath));
        String panePath = "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/TerminalPane.kt";
        String pane = Files.readString(root.resolve(panePath));
        // 只留代码：这个文件好几处注释刻意写了历史 bug 的写法，不能算进负向断言。
        String paneCode = stripComments(pane);

        // ---- 1. 扩展键发出去的字节 ----
        //
        // 这里必须用解码后的字面量做**相邻性**检查（`"ESC"` 后面紧跟它要发的那个字符），
        // 而不是"文件里同时出现过 ESC 和 0x1b" —— 后者在 ESC 与 TAB 的序列被互换时
        // 仍然会通过，而那正是要防的那类错。
        String[][] sequences = {
            {"ESC", "\u001b"},
            {"TAB", "\t"},
            {"ENTER", "\r"},
            {"HOME", "\u001b[H"},
            {"END", "\u001b[F"},
            {"PGUP", "\u001b[5~"},
            {"PGDN", "\u001b[6~"},
            {"LEFT", "\u001b[D"},
            {"RIGHT", "\u001b[C"},
            {"UP", "\u001b[A"},
            {"DOWN", "\u001b[B"},
        };
        for (String[] pair : sequences) {
            require(hostLiterals.contains(pair[0] + pair[1]),
                "扩展键 " + pair[0] + " 必须仍然发出它原来的字节序列");
        }
        require(hostLiterals.contains("BKSPBACKSPACE\u007f"),
            "BKSP / BACKSPACE 两个别名都必须映射到 DEL");
        require(hostSquashed.contains("case\"BKSP\":case\"BACKSPACE\""),
            "BKSP 与 BACKSPACE 必须落在同一个分支");

        // ---- 2. 四个修饰键：按一次只作用一个键，被读走即清 ----
        // 左边是 TerminalViewClient 的方法名后缀，右边是它背后那个字段
        // （Control 的方法叫 readControlKey，字段却叫 ctrl —— 两个名字对不上是正常的）。
        String[][] latches = {
            {"Control", "ctrl"},
            {"Alt", "alt"},
            {"Shift", "shift"},
            {"Fn", "fn"},
        };
        for (String[] latch : latches) {
            require(hostSquashed.contains("booleanread" + latch[0] + "Key()"),
                "read" + latch[0] + "Key 必须在（TerminalViewClient 依赖它）");
            require(hostSquashed.contains("booleanvalue=" + latch[1] + ";" + latch[1] + "=false;"),
                "read" + latch[0] + "Key 必须读后即清，否则这个修饰键会永久卡住");
        }
        require(hostSquashed.contains("ctrl=alt=shift=fn=false;"),
            "发一个键之后所有修饰键都要清掉（与 TerminalView 的读法一致）");

        // ---- 3. 环境变量：少了 LD_LIBRARY_PATH 终端里所有二进制都起不来 ----
        require(hostSquashed.contains("env.put(\"LD_LIBRARY_PATH\",prefix+\"/lib\")"),
            "LD_LIBRARY_PATH 必须仍然设置：内置 ELF 的 DT_RUNPATH 指向不存在的路径");
        require(hostSquashed.contains("env.put(\"PREFIX\",prefix)"),
            "PREFIX 必须仍然传给 PTY");
        require(hostSquashed.contains("newString[]{shell,\"-l\"}"),
            "必须是 login shell（-l）：否则 ~/.profile 与 ~/.bashrc 都不加载");

        // ---- 4. attach 必须等首次 layout ----
        require(hostSquashed.contains("view.post(()->{")
                && hostSquashed.contains("if(terminalView!=view||selected!=index||index>=sessions.size())return;"),
            "attach 必须在 post 里做且有三重校验：早 attach 会让 JNI 错误从 onSizeChanged 逃出去杀掉 Activity");

        // ---- 5. 属性解析：续行与 back-key ----
        require(hostSquashed.contains("if(trimmed.endsWith(\"\\\\\"))"),
            "termux.properties 的续行（行尾反斜杠）必须仍然处理");
        require(hostSquashed.contains("\"escape\".equalsIgnoreCase(back.trim())"),
            "back-key=escape 的判定必须保留");

        // ---- 6. 11 项快捷动作：标签与顺序都是契约 ----
        String[] actions = {
            "Paste", "Copy selection", "Reset terminal", "New session", "Rename session",
            "Close session", "Kill shell", "Font smaller", "Font larger",
            "Reload termux.properties", "Toggle wake lock",
        };
        int cursor = -1;
        for (String action : actions) {
            int at = dialogs.indexOf("\"" + action + "\"");
            require(at > 0, "快捷动作里缺少 " + action);
            require(at > cursor, "快捷动作的顺序不能变（调用方按下标分发）：" + action + " 位置不对");
            cursor = at;
        }
        // 下标分发必须覆盖 11 个分支，一个都不能少。
        for (int i = 0; i <= 10; i++) {
            require(squash(pane).contains(i + "->"),
                "快捷动作下标 " + i + " 没有对应的分发分支");
        }

        // ---- 7. 会话抽屉的动作与文案 ----
        String[] chromeTexts = {
            "Termux sessions", "＋  New session", "⌨  Toggle keyboard", "↻  Reload properties",
            "running", "finished", "●  ", "○  ",
        };
        for (String text : chromeTexts) {
            require(chrome.contains("\"" + text + "\""),
                "抽屉里缺少它原来的文字：" + text);
        }

        // ---- 8. DRAWER 与 KEYBOARD 这两个动作的目标是界面而不是 PTY ----
        require(chrome.isEmpty() || pane.contains("\"DRAWER\""),
            "扩展键里的 DRAWER 必须由 Compose 那一层接住：宿主碰不到抽屉");
        require(hostSquashed.contains("case\"KEYBOARD\":toggleKeyboard();return;"),
            "KEYBOARD 仍然要切软键盘");

        // ---- 9. 渲染层仍然是上游 View，不是自绘 ----
        require(paneCode.contains("AndroidView("),
            "终端渲染必须仍然承载上游 TerminalView（Canvas 逐字符绘制 + CSI + 选区手势）");
        require(paneCode.contains("terminalHolder.obtain(context)"),
            "实例必须从 holder 取（跨 Tab 保活），不能在组合里 new");
        require(squash(paneCode).contains("onDispose{detachFromParent(pane)}"),
            "离开组合只能摘视图，不能 closeAll()：切走再切回来不能变成空终端");
        require(!squash(paneCode).contains("closeAll()"),
            "界面层不能调 closeAll()：那是 ViewModel.onCleared() 的职责"
                + "（历史上在 onDispose 里调过，导致切走再回来永远是空终端）");

        // ---- 10. 重写掉的那一层不许回来 ----
        Path uiMotion = root.resolve("app/src/main/java/com/zhizhu/zhicode/UiMotion.java");
        require(!Files.exists(uiMotion),
            "UiMotion 的全部调用点都在终端外壳里，外壳改成 Compose 后它应当是死代码并已删除");

        System.out.println("PASS  TerminalPaneContractTest");
    }
}

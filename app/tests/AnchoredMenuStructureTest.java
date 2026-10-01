import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * 长按动作菜单（Miuix 下拉菜单）的**接线**守卫。
 *
 * <p>为什么需要它：这套东西由四个文件各出一半拼起来，任何一半掉了都**不会编译失败**，
 * 只会表现为「长按之后什么都不弹」或「菜单和居中对话框同时弹」：
 *
 * <ol>
 *   <li>{@code WorkspaceViewModel} 写入 {@code anchorId}（否则没有条目会认领菜单）；</li>
 *   <li>{@code Common.kt} 的 {@code ZhiAnchoredActionMenu} 负责渲染，
 *       且必须显式 {@code collapseOnSelection = true}（默认值是
 *       {@code entries.size <= 1}，本工程恰好只建一个 entry 才碰巧为 true ——
 *       将来按分组拆成两个 entry，默认值就变 false，表现是「点了不收」，不报错）；</li>
 *   <li>{@code ChatList} 与 {@code Sidebar} 在**每一项目的布局里**调用插槽
 *       （这是菜单能锚在那一项上的唯一原因）；</li>
 *   <li>{@code AppScaffold}（ui 拆分后为 {@code OverlayHost.kt}，挂载点仍在
 *       AppScaffold）按 {@code isActionMenu} 把居中对话框与背景模糊让位
 *       （不让位就会两者同时可见）。</li>
 * </ol>
 */
public final class AnchoredMenuStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";
    private static final String COMMON = SRC + "ui/Common.kt";
    private static final String MODEL = SRC + "model/UiModels.kt";
    private static final String VIEW_MODEL = SRC + "state/WorkspaceViewModel.kt";
    private static final String APP_SCAFFOLD = SRC + "ui/AppScaffold.kt";
    private static final String OVERLAY_HOST = SRC + "ui/OverlayHost.kt";
    private static final String CHAT_AREA = SRC + "ui/ChatArea.kt";
    private static final String CHAT_LIST = SRC + "ui/chat/ChatList.kt";
    private static final String SIDEBAR = SRC + "ui/Sidebar.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    /** 取某个函数/函数的实现片段（从签名出现处到配对的花括号结束）。 */
    private static String bodyOf(String code, String signature) {
        int at = code.indexOf(signature);
        if (at < 0) return "";
        int depth = 0;
        boolean seenBrace = false;
        for (int i = at; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') { depth++; seenBrace = true; }
            else if (c == '}') {
                depth--;
                if (seenBrace && depth == 0) return code.substring(at, i + 1);
            }
        }
        return code.substring(at);
    }

    private static List<String> kotlinSources(String root, String dir) throws Exception {
        try (Stream<Path> walk = Files.walk(Paths.get(root, dir))) {
            return walk.filter(p -> p.toString().endsWith(".kt"))
                    .map(p -> Paths.get(root).relativize(p).toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 状态里必须有锚点字段与判定 -----------------------------------
        String model = stripComments(read(root, MODEL));
        require(model.contains("val anchorId"),
                MODEL + " 的 ChoicePickerState 必须有 anchorId（长按菜单靠它认领条目）");
        require(model.contains("val isActionMenu"),
                MODEL + " 必须有 isActionMenu 判定（弹窗挂载宿主靠它让位）");
        require(model.contains("anchorId != null"),
                "isActionMenu 必须把 anchorId 也算进去：只看 intent 的话，"
                        + "「intent 是动作菜单但没锚点」会让菜单与对话框**都不画**");

        // ---- 2. 两个 show*Actions 必须写入锚点 -------------------------------
        String vm = stripComments(read(root, VIEW_MODEL));
        require(vm.contains("anchorId = item.id"),
                VIEW_MODEL + " 的 showMessageActions 必须 anchorId = item.id，"
                        + "否则没有任何一条消息会认领菜单");
        require(vm.contains("anchorId = session.id"),
                VIEW_MODEL + " 的 showSessionActions 必须 anchorId = session.id");

        // ---- 3. 渲染集中在转发层，且必须显式 collapseOnSelection --------------
        String common = stripComments(read(root, COMMON));
        require(common.contains("fun ZhiAnchoredActionMenu("),
                COMMON + " 必须定义 ZhiAnchoredActionMenu");
        require(common.contains("OverlayDropdownPopup("),
                "ZhiAnchoredActionMenu 必须转发到 Miuix 的 OverlayDropdownPopup"
                        + "（它是库文档里明确说明可用于动作菜单的那个组件）");
        require(common.contains("collapseOnSelection = true"),
                "ZhiAnchoredActionMenu 必须**显式**传 collapseOnSelection = true。"
                        + "它的默认值是 entries.size <= 1，本工程恰好只建一个 entry 才碰巧"
                        + "为 true；将来按分组拆成两个 entry，默认值就变 false，"
                        + "表现是「点了不收」，而这不是编译错误。");

        // ---- 3b. 手指追踪：只观察、绝不消费 -----------------------------------
        require(common.contains("fun Modifier.zhiObservePointer("),
                COMMON + " 必须定义 zhiObservePointer（长按菜单要跟随手指）");
        String observer = bodyOf(common, "fun Modifier.zhiObservePointer(");
        require(observer.contains("PointerEventPass.Initial"),
                "zhiObservePointer 必须用 PointerEventPass.Initial：先于子组件看到事件，"
                        + "这样不抢手势也能拿到坐标");
        // 这条是本次最关键的守卫：一旦消费事件，Card 自己的点击/长按与无障碍语义
        // 全部失效，而界面只是"点了没反应"，没有任何编译错误。
        require(!observer.contains("consume"),
                "zhiObservePointer 里**绝不能**出现 consume()：它必须是只读观察者。"
                        + "消费事件会顶掉 Card 的 combinedClickable —— 侧栏的「点击打开会话」"
                        + "与卡片的「长按出菜单」会一起失效，且不会有编译错误。");

        // 手指偏移必须在**非空**时才施加：0 位移会把锚点拉回条目左上角，反而跑偏
        String menu = bodyOf(common, "fun ZhiAnchoredActionMenu(");
        require(menu.contains("fingerOffset == null"),
                "ZhiAnchoredActionMenu 必须在 fingerOffset 非空时才施加位移");
        require(menu.contains("absoluteOffset"),
                "手指位置是绝对像素，必须用 absoluteOffset（offset 在 RTL 下会镜像）");

        // OverlayDropdownPopup 只允许出现在转发层（与 TextField 同一套约定）
        List<String> popupUsers = new ArrayList<>();
        for (String file : kotlinSources(root, SRC)) {
            if (file.endsWith("ui/Common.kt")) continue;
            if (stripComments(read(root, file)).contains("OverlayDropdownPopup")) {
                popupUsers.add(file);
            }
        }
        require(popupUsers.isEmpty(),
                "OverlayDropdownPopup 只能出现在 " + COMMON + "，以下文件绕过了转发层："
                        + popupUsers);

        // ---- 4. 两个列表必须把插槽调进「每一项自己的布局里」 ------------------
        for (String file : new String[]{CHAT_LIST, SIDEBAR}) {
            String text = stripComments(read(root, file));
            require(text.contains("anchoredMenu"),
                    file + " 必须接收并调用 anchoredMenu 插槽（菜单靠它锚在被长按的那一项上）");
            require(text.contains("anchoredMenu("),
                    file + " 只是声明了 anchoredMenu 却没在条目里调用它，"
                            + "菜单将无处渲染");
            // 手指追踪也必须挂上：只传插槽不挂观察者的话，菜单会退化成"贴条目"，
            // 不报错但不符合"跟随手指"的要求。
            require(text.contains("rememberFingerTracker()"),
                    file + " 必须为每一条挂 rememberFingerTracker()，否则拿不到手指位置");
            require(text.contains("finger.modifier"),
                    file + " 必须把 finger.modifier 挂到条目上（只 remember 不挂 = 永远收不到事件）");
        }
        String sidebar = stripComments(read(root, SIDEBAR));
        require(sidebar.contains("anchoredMenu(session.id, fingerOffset)"),
                SIDEBAR + " 必须在会话条目里用 anchoredMenu(session.id, fingerOffset) 调用"
                        + "（两个参数分别是锚点标识与手指位置）");
        String chatList = stripComments(read(root, CHAT_LIST));
        require(chatList.contains("anchoredMenu(item.id, fingerOffset)"),
                CHAT_LIST + " 必须在消息条目里用 anchoredMenu(item.id, fingerOffset) 调用");

        // ---- 5. 「让位」逻辑必须按 isActionMenu 存在，且接线不断 ------------
        // 让位块原先内联在 AppScaffold；ui 结构重构（AppScaffold 拆分）后整块
        // 搬进 OverlayHost.kt，AppScaffold 只留一行 ZhiOverlayHost 挂载。
        // 守卫随之更新文件引用，但语义断言逐字保留：
        //  ① 让位块必须恰好落在 AppScaffold / OverlayHost 之一（双份会双重模糊，
        //     缺失会让位失效）；
        //  ② 两处字面让位写法必须原样存在；
        //  ③ 若让位块不在 AppScaffold，挂载点 ZhiOverlayHost 必须还在；
        //  ④ 跨「挂载宿主 + 菜单认领（ChatArea 的 ZhiAnchoredMenuHost）」计数
        //     isActionMenu >= 2，防止哪一半被单独删掉。
        String scaffold = stripComments(read(root, APP_SCAFFOLD));
        String overlayHost = stripComments(read(root, OVERLAY_HOST));
        boolean inScaffold = scaffold.contains("val anchoredActionMenu");
        boolean inOverlay = overlayHost.contains("val anchoredActionMenu");
        require(inScaffold ^ inOverlay,
                "「让位」块（val anchoredActionMenu/modalOpen）必须恰好出现在 "
                        + APP_SCAFFOLD + " 或 " + OVERLAY_HOST + " 之一："
                        + "两处都写会双重模糊，都不写会让位失效");
        String holder = inScaffold ? scaffold : overlayHost;
        String holderPath = inScaffold ? APP_SCAFFOLD : OVERLAY_HOST;
        require(holder.contains("if (anchoredActionMenu) null else state.choicePicker"),
                holderPath + " 必须把 anchored 菜单从 ChoicePickerOverlay 里排除，"
                        + "否则长按后「下拉菜单 + 居中对话框」同时可见");
        require(holder.contains("state.choicePicker != null && !anchoredActionMenu"),
                holderPath + " 必须把 anchored 菜单从 modalOpen 里排除，"
                        + "否则会给工作区多加一层与弹层自身重复的背景模糊");
        if (!inScaffold) {
            require(scaffold.contains("ZhiOverlayHost("),
                    APP_SCAFFOLD + " 不再内联让位块时必须挂载 " + OVERLAY_HOST
                            + "（ZhiOverlayHost），否则弹窗整体消失");
        }
        int siteCount = 0;
        Matcher m = Pattern.compile("isActionMenu").matcher(
                overlayHost + stripComments(read(root, CHAT_AREA)));
        while (m.find()) siteCount++;
        require(siteCount >= 2,
                "让位宿主（OverlayHost）与菜单认领（ChatArea 的 ZhiAnchoredMenuHost）"
                        + "合计至少要有两处 isActionMenu。当前只有 " + siteCount + " 处。");

        // ---- 6. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("AnchoredMenuStructureTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("AnchoredMenuStructureTest PASS"
                + "（锚点接线完整 · 弹层单入口 · 对话框与模糊均已让位）");
    }
}

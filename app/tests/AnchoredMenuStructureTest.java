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
 *   <li>{@code AppScaffold} 按 {@code isActionMenu} 把居中对话框与背景模糊让位
 *       （不让位就会两者同时可见）。</li>
 * </ol>
 */
public final class AnchoredMenuStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";
    private static final String COMMON = SRC + "ui/Common.kt";
    private static final String MODEL = SRC + "model/UiModels.kt";
    private static final String VIEW_MODEL = SRC + "state/WorkspaceViewModel.kt";
    private static final String APP_SCAFFOLD = SRC + "ui/AppScaffold.kt";
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
                MODEL + " 必须有 isActionMenu 判定（AppScaffold 靠它让位）");
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
        }
        String sidebar = stripComments(read(root, SIDEBAR));
        require(sidebar.contains("anchoredMenu(session.id)"),
                SIDEBAR + " 必须在会话条目里用 anchoredMenu(session.id) 调用"
                        + "（参数即锚点标识，只有被长按那一条会认领）");

        // ---- 5. AppScaffold 必须按 isActionMenu 让位 ------------------------
        String scaffold = stripComments(read(root, APP_SCAFFOLD));
        int siteCount = 0;
        Matcher m = Pattern.compile("isActionMenu").matcher(scaffold);
        while (m.find()) siteCount++;
        require(siteCount >= 2,
                APP_SCAFFOLD + " 至少要有两处 isActionMenu：一处把居中对话框让位"
                        + "（ChoicePickerOverlay 的 picker 传 null），一处把背景模糊让位"
                        + "（modalOpen）。当前只有 " + siteCount + " 处。");
        require(scaffold.contains("if (anchoredActionMenu) null else state.choicePicker"),
                APP_SCAFFOLD + " 必须把 anchored 菜单从 ChoicePickerOverlay 里排除，"
                        + "否则长按后「下拉菜单 + 居中对话框」同时可见");
        require(scaffold.contains("state.choicePicker != null && !anchoredActionMenu"),
                APP_SCAFFOLD + " 必须把 anchored 菜单从 modalOpen 里排除，"
                        + "否则会给工作区多加一层与弹层自身重复的背景模糊");

        // ---- 6. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("AnchoredMenuStructureTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("AnchoredMenuStructureTest PASS"
                + "（锚点接线完整 · 弹层单入口 · 对话框与模糊均已让位）");
    }
}

import java.nio.file.*;
import java.util.*;

/**
 * 表单错误行的守卫：**时机**与**对齐**（用户反馈 #4 / #16）。
 *
 * <h2>1. 没碰过的字段不该飘红</h2>
 *
 * 打开「添加 MCP 服务器」时立刻出现两行红字（「请填写服务器名称」
 * 「stdio 服务器必须填写启动命令」）—— 用户还没开始填就被指责，
 * 看上去像页面坏了。所以错误行必须受 `touched` 约束。
 *
 * <h2>2. 错误行必须与同组输入框左对齐</h2>
 *
 * 原先四处调用点各写一份 `Text(...) + padding(top = 4.dp)`，**没有横向内缩**，
 * 而同组输入框是 `padding(horizontal = 12.dp)` —— 于是错误行比上面的输入框、
 * 比 `SmallTitle` 都往左凸出一截，表单左边缘参差不齐。
 *
 * <p>两条都不会编译失败、也不会崩，只会「看着不对」，所以静态钉住。
 */
public final class FieldErrorAlignmentTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";
    private static final String COMMON = SRC + "ui/Common.kt";
    private static final String DIALOGS = SRC + "ui/dialogs/";

    private static final String[] FORMS = {
            "McpConfigOverlay.kt", "ApiConfigOverlay.kt",
            "SkillsOverlay.kt", "RoleCardsOverlay.kt",
    };

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


    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String common = stripComments(read(root, COMMON));
        String sqCommon = squash(common);

        // ---- 1. 公共组件：必须有 touched，且横向内缩 12dp --------------------
        require(sqCommon.contains("funZhiFieldError(message:String?,touched:Boolean=true)"),
                COMMON + " 必须提供 ZhiFieldError(message, touched = true)："
                        + "四处表单共用同一个错误行，才不会各自长出一套缩进");
        require(sqCommon.contains("if(message==null||!touched)return"),
                "ZhiFieldError 必须在 !touched 时**不渲染**："
                        + "否则没碰过的字段照样飘红（用户看到的是一打开表单就一片红字）");
        require(sqCommon.contains("start=12.dp,end=12.dp"),
                "ZhiFieldError 的横向内缩必须是 12dp：与同组输入框的 "
                        + "padding(horizontal = 12.dp)、SmallTitle 对齐。"
                        + "少这一项，错误行会比输入框往左凸出一截。");
        require(sqCommon.contains("color=MiuixTheme.colorScheme.error"),
                "ZhiFieldError 必须用主题的 error 色，而不是 ZhiColors.red()："
                        + "手写红色在浅色模式下与前者的观感不一致");

        // ---- 2. 四处表单都走公共组件，且传 touched ---------------------------
        for (String form : FORMS) {
            String text = stripComments(read(root, DIALOGS + form));
            require(text.contains("ZhiFieldError") || text.contains("FieldError("),
                    DIALOGS + form + " 的错误行必须走公共组件（ZhiFieldError）");
            // 不许再自己拼一个**字段校验**错误行：那正是「各写一份缩进」的起点。
            //
            // ⚠️ 锚定的是**旧写法的具体形态**，而不是「文件里出现了 error 色」
            // 或「出现了 padding(top = …)」—— 后两者在这几个文件里都有大量合法用法
            //（连接失败提示、停用项标题、普通间距），把它们算进来会让守卫因为
            // 正确代码而变红，然后被人删掉。
            require(text.contains("FieldError("),
                    DIALOGS + form + " 必须走 ZhiFieldError 渲染字段错误行");
            require(!text.contains("nameError?.let") && !text.contains("commandError?.let")
                            && !text.contains("urlError?.let") && !text.contains("envError?.let")
                            && !text.contains("headersError?.let"),
                    DIALOGS + form + " 里还有 `xxxError?.let { ... }` 手写错误行："
                            + "换成 ZhiFieldError(message, touched) 才会受 touched 约束、"
                            + "并拿到与输入框一致的 12dp 内缩");
            require(!text.contains("val error = draft.") && !text.contains("val error = editor."),
                    DIALOGS + form + " 里还有 `val error = draft.xxxError` 这种手写选行："
                            + "把错误行统一交给 ZhiFieldError 才能保证缩进与时机一致");
        }

        // ---- 3. 「没碰过」必须真的被记录起来 --------------------------------
        // 只有 touched 参数而没有状态，等于永远 false（错误永远不显示）或
        // 永远 true（回到原样）。两头都是 bug，所以要求有状态 + 在 onValueChange 里置真。
        String[][] expectations = {
                {"McpConfigOverlay.kt", "nameTouched"},
                {"ApiConfigOverlay.kt", "nameTouched"},
                {"SkillsOverlay.kt", "nameTouched"},
                {"RoleCardsOverlay.kt", "nameTouched"},
        };
        for (String[] pair : expectations) {
            String text = stripComments(read(root, DIALOGS + pair[0]));
            String sq = squash(text);
            require(sq.contains("var" + pair[1] + "byremember("),
                    DIALOGS + pair[0] + " 必须有 " + pair[1] + " 状态（remember 建）");
            require(sq.contains(pair[1] + "=true"),
                    DIALOGS + pair[0] + " 必须在 onValueChange 里把 " + pair[1] + " 置真："
                            + "不置真的话它永远是 false，错误提示再也不显示");
        }
    }
}

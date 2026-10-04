import java.nio.file.*;
import java.util.*;

/**
 * ＋ 菜单撤掉「打开文件工作区」，保留其他附件动作。
 *
 * <p>用户要求从输入器 ＋ 菜单删除工作区快捷入口，文件仍可从底部工作区导航访问；
 * 其余附件动作保持可用。
 *
 * <p>这类改动不会有编译错误，因此用静态断言守住菜单内容和回调清理。
 */
public final class ComposerMenuTest {

    private static final String COMPOSER =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/composer/Composer.kt";
    private static final String SETTINGS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/settings/SettingsDialog.kt";
    private static final String VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";

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

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String composer = stripComments(read(root, COMPOSER));
        String settings = stripComments(read(root, SETTINGS));
        String vm = stripComments(read(root, VM));

        // ---- 1. ＋ 菜单与回调链不再提供工作区快捷项 -------------------------
        require(!composer.contains("打开文件工作区") && !composer.contains("onOpenFilesTab"),
                COMPOSER + " 不应再包含工作区菜单项或其无用回调");

        // ---- 2. 其他附件动作必须保留 -----------------------------------------
        for (String label : new String[]{"附加项目文件", "文件管理器", "上传照片"}) {
            require(composer.contains("\"" + label + "\""),
                    COMPOSER + " 的 ＋ 菜单里少了「" + label + "」—— 只应移除工作区快捷入口");
        }
    }
}

import java.nio.file.*;
import java.util.*;

/**
 * ＋ 菜单不得再放「Skill 管理器」（#13）。
 *
 * <p>用户要求把 ＋ 菜单里的那一项撤掉。撤掉的**前提**是技能还有别的入口，
 * 否则就是把手功能藏进抽屉 —— 所以这里同时守住「入口仍在」。
 *
 * <p>这类改动不会有编译错误（删一个菜单项、留一个入口，两边都合法），
 * 唯一会出错的方式是**顺手把入口也删了**，所以用静态断言钉住。
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

        // ---- 1. ＋ 菜单里不该再有技能项 ------------------------------------
        require(!composer.contains("\"Skill 管理器\"") && !composer.contains("\"技能\""),
                COMPOSER + " 的 ＋ 菜单里不该再有 Skill 管理器：用户明确要求撤掉它。"
                        + "（它是**设置里的配置对象**，不是输入器的常用动作。）");
        require(!composer.contains("onOpenSkills"),
                COMPOSER + " 不该再收 onOpenSkills 参数：参数还在就意味着菜单项随时会被加回来，"
                        + "而且调用点会一直传一个用不到的回调");

        // ---- 2. 但技能入口必须仍然存在（撤的只是这一个入口）---------------
        require(settings.contains("onNavigate(\"skills\")"),
                SETTINGS + " 的设置页必须仍然有技能入口：撤掉 ＋ 菜单那一项的前提是"
                        + "别处还能进得去，否则就是把功能藏没了");
        require(vm.contains("\"skills\" -> openSkills()"),
                VM + " 必须仍然能从设置页导航到技能页");
        require(vm.contains("\"/skills\""),
                VM + " 必须仍然保留 /skills 斜杠命令入口");

        // ---- 3. ＋ 菜单剩下的三项不能顺手丢 --------------------------------
        for (String label : new String[]{"附加项目文件", "打开文件工作区", "上传照片"}) {
            require(composer.contains("\"" + label + "\""),
                    COMPOSER + " 的 ＋ 菜单里少了「" + label + "」—— 只该删技能那一条");
        }
    }
}

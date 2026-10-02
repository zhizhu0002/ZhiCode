import java.nio.file.*;
import java.util.*;

/**
 * 页脚下拉菜单 × 输入法 的守卫。
 *
 * <p>用户实测：「打开输入法再打开下拉菜单，下拉菜单会显示在下层」——被键盘
 * 盖住。根因在 Miuix 0.9.4：浮层定位（rememberListPopupLayoutInfo）计算
 * windowBounds 时只扣状态栏 / 导航栏 / 刘海，**不认识 IME**；而本工程是
 * edge-to-edge，窗口不随键盘缩小，于是「窗口底 = 屏幕底」，菜单正好落进
 * 键盘区域。
 *
 * <p>修法：打开菜单的瞬间收起输入法（onExpandedChange 里 hide），锚点随
 * composer 上移、浮层跟着锚点走。这条约定删掉不会编译失败，只会让菜单
 * 再一次被键盘盖住，所以静态钉住。
 */
public final class DropdownImeGuardTest {

    private static final String COMMON =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Common.kt";

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
        String sq = squash(common);

        require(common.contains("LocalSoftwareKeyboardController.current"),
                COMMON + " 必须拿 LocalSoftwareKeyboardController：打开下拉菜单时收起输入法");
        require(sq.contains("onExpandedChange={expanded->if(expanded)keyboard?.hide()}"),
                COMMON + " 的 ZhiIconDropdownMenu 必须在展开时收起键盘："
                        + "Miuix 浮层定位不认识 IME，键盘开着时菜单会被盖在键盘底下");
    }
}

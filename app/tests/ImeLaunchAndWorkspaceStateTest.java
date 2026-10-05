import java.nio.file.Files;
import java.nio.file.Path;

public final class ImeLaunchAndWorkspaceStateTest {
    private static final String ROOT = "app/src/main/";

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        String activity = read(root.resolve(ROOT + "java/com/zhizhu/zhicode/compose/MainActivity.kt"));
        String motion = read(root.resolve(ROOT + "java/com/zhizhu/zhicode/compose/ui/ImeMotion.kt"));
        String chatArea = read(root.resolve(ROOT + "java/com/zhizhu/zhicode/compose/ui/ChatArea.kt"));
        String chatList = read(root.resolve(ROOT + "java/com/zhizhu/zhicode/compose/ui/chat/ChatList.kt"));
        String layouts = read(root.resolve(ROOT + "java/com/zhizhu/zhicode/compose/ui/WorkspaceLayouts.kt"));
        String vm = read(root.resolve(ROOT + "java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt"));
        String manifest = read(root.resolve("app/src/main/AndroidManifest.xml"));

        require(activity.contains("SOFT_INPUT_STATE_ALWAYS_HIDDEN"),
                "主 Activity 必须明确禁止启动时自动打开输入法");
        require(activity.contains("decorView.isFocusableInTouchMode = true")
                        && activity.contains("decorView.requestFocus()"),
                "启动焦点必须先落在非输入控件的 DecorView，不能让发送框抢焦点");
        require(activity.contains("WindowInsetsAnimationCompat.Callback")
                        && activity.contains("imeMotion.onPrepare")
                        && activity.contains("imeMotion.onProgress")
                        && activity.contains("imeMotion.onEnd"),
                "IME 动画必须由 Activity 统一接收");
        require(motion.contains("currentLiftPx") && motion.contains("settledLiftPx")
                        && motion.contains("openGeneration"),
                "IME 必须分离实时位移、稳定留白和打开代数");
        require(chatArea.contains("LocalImeMotion.current")
                        && !chatArea.contains("imeFollowToken")
                        && !chatArea.contains("collectLatest")
                        && chatArea.contains("imeMotion.currentLiftPx"),
                "ChatArea 不得再保留旧的去抖 token 竞争链路");
        require(chatList.contains("composerFocused")
                        && chatList.contains("imeOpenGeneration")
                        && chatList.contains("autoFollowState.value")
                        && chatList.contains("listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)"),
                "吸底必须绑定发送框焦点、打开代数和原有跟随状态");
        require(!chatList.contains("imeFollowToken"),
                "ChatList 不得再接收旧 imeFollowToken");
        require(manifest.contains("stateAlwaysHidden|adjustResize"),
                "清单必须声明 stateAlwaysHidden|adjustResize");
        require(vm.contains("fun resetTabForLaunch()")
                        && vm.contains("tab = WorkspaceTab.CHAT"),
                "冷启动必须回到对话页");
        require(layouts.contains("val secondaryTabs = remember { secondary }")
                        && layouts.contains("selected = secondaryTabs.getOrNull(secondaryPagerState.currentPage)"),
                "宽屏副栏的 TAB 高亮必须来自副栏自己的页码，不得显示对话");
        System.out.println("ImeLaunchAndWorkspaceStateTest PASS");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

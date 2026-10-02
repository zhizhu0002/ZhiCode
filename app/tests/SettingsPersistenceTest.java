import java.nio.file.*;
import java.util.*;

/**
 * 设置**必须落盘**的守卫（用户报告：「设置保存很不完善」）。
 *
 * <h2>守的是什么</h2>
 *
 * 引擎侧早就有一张完整的持久化表（`ApiSettingsStore.SETTINGS`，键与
 * `SessionConfig` 的字段一一对应），读路径也在跑 —— 但**写路径从来没被调用过**：
 * `ApiSettingsStore.save(SessionConfig)` 在整个工程里零调用方。
 * 于是权限模式、推理档、上下文窗口、项目目录、联网搜索一整套、自动压缩、
 * 自定义提示词、沙箱全权、Root、保活，全部重启即丢。
 *
 * <p>这类问题不会报错、不会崩、单测也不红 —— 它的表现只是「这软件的设置记不住」，
 * 而「读得到默认值」正好掩盖了「从来没写进去过」。所以只能静态钉住：
 * <ol>
 *   <li>写路径必须存在且被调用（`store.save(...)`）；</li>
 *   <li>每个设置组必须有键 + 读取项（否则写进去也读不回来）；</li>
 *   <li>不在 `SessionConfig` 里的界面设置（主题、搜索密钥）必须有自己的
 *       读**和**写两条路径 —— 这一类最容易只做一半。</li>
 * </ol>
 */
public final class SettingsPersistenceTest {

    private static final String STORE =
            "app/src/main/java/com/termux/app/zhicode/storage/ApiSettingsStore.java";
    private static final String CONTROLLER =
            "app/src/main/java/com/zhizhu/zhicode/compose/engine/ZhiEngineController.kt";
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
        String store = stripComments(read(root, STORE));
        String controller = stripComments(read(root, CONTROLLER));
        String vm = stripComments(read(root, VM));

        // ---- 1. 写路径必须真的被调用 ----------------------------------------
        // 这是本次事故的核心：save() 存在、能被调、但没人调。
        require(store.contains("public synchronized void save(SessionConfig config)"),
                STORE + " 里找不到 save(SessionConfig) —— 断言过期，请更新守卫");
        require(controller.contains("store.save(base)"),
                CONTROLLER + " 的 configure() 必须在末尾调用 store.save(base)："
                        + "只写内存与引擎、不落盘，用户看到的就是「设置重启就丢」。"
                        + "（修之前 save() 在整个工程里零调用方。）");

        // ---- 2. 每一组设置都要有「键 + 读取项」成对出现 ----------------------
        // 只写不读 = 存了等于没存；只读不写 = 永远是默认值。两者都要在。
        String[][] groups = {
                {"PERMISSION_MODE", "permissionMode"},
                {"EFFORT", "effort"},
                {"CONTEXT_WINDOW", "contextWindowTokens"},
                {"PROJECT_DIRECTORY", "projectDirectory"},
                {"WEB_SEARCH_ENABLED", "webSearchEnabled"},
                {"WEB_SEARCH_PROVIDER", "webSearchProvider"},
                {"WEB_SEARCH_MAX_RESULTS", "webSearchMaxResults"},
                {"WEB_SEARCH_SEARXNG_URL", "webSearchBaseUrl"},
                {"AUTO_COMPACT", "autoCompact"},
                {"CUSTOM_PROMPT", "customSystemPrompt"},
                {"SANDBOX_FULL_ACCESS", "sandboxAgentFullAccess"},
                {"ROOT_EXECUTION", "rootExecutionEnabled"},
                {"FORCED_KEEP_ALIVE", "forcedKeepAliveEnabled"},
                {"VISION_ENABLED", "visionEnabled"},
        };
        List<String> missing = new ArrayList<>();
        for (String[] group : groups) {
            String key = group[0];
            String field = group[1];
            // 键常量必须存在，且必须出现在 SETTINGS.put(...) 的注册里。
            if (!store.contains("String " + key + " =")) {
                missing.add(key + "（缺少键常量）");
            } else if (!store.contains("SETTINGS.put(Key." + key)) {
                missing.add(key + "（没有注册进 SETTINGS 表 → 写进去也读不回来）");
            }
            if (!store.contains(field)) {
                missing.add(key + "（SETTINGS 表里没有指向 " + field + "）");
            }
        }
        require(missing.isEmpty(),
                "以下设置项缺少「键 + 注册 + 字段」三者之一，持久化会静默失效：\n  "
                        + String.join("\n  ", missing));

        // ---- 3. 不在 SessionConfig 里的界面设置：读写都要有 -----------------
        // 主题与搜索密钥属于这一类。它们最容易只做一半（比如只读了默认值）。
        require(store.contains("setThemeMode(") && store.contains("getThemeMode("),
                STORE + " 必须同时提供主题的读与写：主题不在 SessionConfig 里，"
                        + "configure() 那次落盘罩不到它，只做一半就是「主题重启就丢」");
        require(store.contains("THEME_MODE = \"theme_mode\""),
                STORE + " 的主题键必须叫 theme_mode（改键名 = 丢掉用户已有选择）");
        require(vm.contains("persistTheme(") && vm.contains("restoreUiSettings()"),
                VM + " 必须在改动时写主题（persistTheme）并在启动时读回（restoreUiSettings）："
                        + "两者缺一都会让主题设置看起来没生效");

        // 搜索密钥：必须走**加密**槽，不能随手写进明文 prefs。
        require(store.contains("setWebSearchKey(") && store.contains("getWebSearchKey("),
                STORE + " 必须提供搜索密钥的读与写");
        require(store.contains("AndroidSecretStore") && store.contains("websearch:"),
                STORE + " 的搜索密钥必须走 AndroidSecretStore 的加密槽（websearch:<服务>）："
                        + "API 配置的密钥是加密存的，搜索密钥没理由更宽松");
        require(!store.contains("putString(Key.WEB_SEARCH_KEY"),
                STORE + " 不得把搜索密钥写进明文 prefs");
        require(vm.contains("persistWebSearchKey") && vm.contains("getWebSearchKey"),
                VM + " 必须在设置变更时写搜索密钥、启动时读回");
    }
}

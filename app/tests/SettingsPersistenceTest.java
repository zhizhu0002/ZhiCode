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
    /** 宿主 Activity：`onStop` 要兑现去抖窗口里那次设置改动（"点完就大退"的兜底）。 */
    private static final String ACTIVITY =
            "app/src/main/java/com/zhizhu/zhicode/compose/MainActivity.kt";

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

    /**
     * 取 `signature` 起配对花括号之间的函数体（跳过字符串字面量）。
     * 找不到签名时抛错 —— 断言过期要立刻看得见，而不是静默返回空串让后面的
     * `contains` 变成"永远为假"或"永远为真"。
     */
    private static String functionBody(String text, String signature) {
        int at = text.indexOf(signature);
        if (at < 0) throw new AssertionError("找不到函数：" + signature + "（断言过期，请更新守卫）");
        int brace = text.indexOf('{', at + signature.length());
        if (brace < 0) throw new AssertionError("找不到函数体：" + signature);
        int depth = 0;
        for (int i = brace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(brace, i + 1);
            }
        }
        return text.substring(brace);
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

        // ---- 5. 设置页改一项就**当场落盘**（R5 修的根因）---------------------
        //
        // 用户真机症状：「设置很多自动保存完全没修好，当直接大退软件，
        // 这些保存恢复到未修改的样子」。
        //
        // 根因有两半，两半都必须有守卫，否则只会修掉一半：
        //
        // (a) **写**：`applySettingsDraft` 当时只落盘了主题 / 搜索密钥 / 终端字符模式
        //     三项。上面第 2 节那 14 组设置全在 SessionConfig 里，唯一的落盘点是
        //     `configure()`，而 `configure()` 只在**发消息**或启动时才走 ——
        //     「改完直接大退」= 一次都没写盘。
        // (b) **读**：启动路径是 `restore* → syncActiveProfile() → configure()`，
        //     而 `configure()` 会落盘、写下去的是界面此刻的状态。界面若还是默认值，
        //     那一刻盘上的用户设置就被默认值冲掉。所以凡是会被 `configure()`
        //     反写的字段，都必须在它**之前**读回来。
        //
        // 只修 (a) 的话表现会变成「偶尔记得住、偶尔丢失」—— 取决于改完之后有没有
        // 发过消息。"顺序"与"覆盖范围"都得钉住。
        require(vm.contains("scheduleSettingsPersist("),
                VM + " 的 applySettingsDraft 必须走 scheduleSettingsPersist()："
                        + "否则改完设置直接大退，SessionConfig 那一整套（上下文窗口、项目目录、"
                        + "提示词、自动压缩、联网搜索、沙箱全权/Root/保活）一次都没写盘");

        String applyDraft = functionBody(vm, "fun applySettingsDraft(");
        require(applyDraft.contains("scheduleSettingsPersist("),
                "applySettingsDraft 里必须真的安排落盘（不是只定义了函数）");
        require(applyDraft.indexOf("scheduleSettingsPersist(") > applyDraft.indexOf("draft.applyTo"),
                "持久化必须在状态更新**之后**：先写盘再更新内存，落盘的会是被夹取前的脏值");

        // ⚠️ 去抖：`SettingsTextField` 的 onValueChange 是**逐字符**触发的，
        // 不去抖的话在「自定义头部提示词」里打一句话就是几十次全量落盘
        // （用户原话：「卡顿更加多了」）。只有自由文本才允许延后。
        require(applyDraft.contains("differsOnlyInFreeText("),
                "applySettingsDraft 必须区分「自由文本输入中」与「离散改动」："
                        + "文本输入要延后合并（否则逐字符全量落盘 = 卡顿），"
                        + "开关/下拉要立刻写（否则「点完就大退」会丢）");
        require(vm.contains("private fun differsOnlyInFreeText("),
                VM + " 必须有 differsOnlyInFreeText()（去抖判据）");
        // ⚠️ 它是**表达式体**函数（`= a.copy(...) == b`，没有花括号），
        // 所以这里不能走 functionBody —— 它会去找下一个 `{`，把隔壁函数的正文算进来。
        int ftAt = vm.indexOf("private fun differsOnlyInFreeText(");
        require(ftAt >= 0, VM + " 里找不到 differsOnlyInFreeText");
        int ftEnd = vm.indexOf("private fun scheduleSettingsPersist(", ftAt);
        String freeText = vm.substring(ftAt, ftEnd > ftAt ? ftEnd : vm.length());
        for (String field : new String[]{"customSystemPrompt", "projectPath", "contextWindow"}) {
            require(freeText.contains(field),
                    "differsOnlyInFreeText 必须覆盖 " + field + "：它是设置页里的自由文本输入框，"
                            + "逐字符触发 onValueChange；漏一个就会在那一栏上重新卡回去");
        }

        // ---- 5b. 落盘必须**同步**（这是"大退丢设置"的真正根因）---------------
        //
        // 上一轮只补了"调用落盘"，设置照丢 —— 因为底层写入是 `apply()`：
        // 它只保证内存可见，磁盘写交给后台线程；从最近任务划掉应用时进程被杀，
        // 没人等那个后台写。所以守卫必须钉住 commit() 这一层，不能只钉调用点。
        String store2 = stripComments(read(root, STORE));
        require(store2.contains("public synchronized void saveDurable(SessionConfig config)"),
                STORE + " 必须提供 saveDurable()：apply() 的磁盘写是异步的，"
                        + "划掉应用时会被丢；用户设置要走 commit()");
        require(store2.contains("save(SessionConfig config, boolean durable)"),
                STORE + " 的 save 必须带 durable 分支（save/saveDurable 共用同一段逻辑，"
                        + "避免两条路径漂移）");
        require(!store2.matches("(?s).*saveGlobal\\(SessionConfig config\\)\\s*\\{[^}]*editor\\.apply\\(\\);\\s*\\}.*"),
                STORE + " 的 saveGlobal 不能只剩 apply()：全部 14 组设置项都由它写出，"
                        + "写成 apply() 时划掉应用会整批丢");
        int commitInSaveGlobal = store2.indexOf("if (durable) editor.commit();");
        require(commitInSaveGlobal > 0,
                STORE + " 的 saveGlobal 必须有 `if (durable) editor.commit();` 分支");
        String saveGlobalBody = functionBody(store2, "private void saveGlobal(SessionConfig config, boolean durable)");
        require(saveGlobalBody.contains("editor.commit()"),
                "saveGlobal 的 durable 分支必须真的 commit()");
        require(saveGlobalBody.contains("editor.apply()"),
                "saveGlobal 的非 durable 分支保留 apply()：configure() 可能在主线程上，"
                        + "那里用 commit() 会把磁盘写压到 UI 帧上");
        String persistNow = functionBody(vm, "private fun persistSettingsNow(");
        require(persistNow.contains("saveDurable("),
                "persistSettingsNow 必须调 saveDurable()（= commit()）："
                        + "调普通 save() 的话 apply() 的异步写会在「大退」时丢掉");
        require(persistNow.contains("Dispatchers.IO"),
                "persistSettingsNow 必须在 IO 线程上：commit() 阻塞到磁盘写完，"
                        + "压在点击那一帧上就是一次卡顿");

        // ---- 5c. 去抖窗口必须能被兑现（否则"改完就走"仍丢）--------------------
        require(vm.contains("fun flushSettings()"),
                VM + " 必须提供 flushSettings()：去抖窗口还开着时用户就划掉应用，"
                        + "那一次改动会丢");
        String closeSettings = functionBody(vm, "fun closeSettings()");
        require(closeSettings.contains("flushSettings()"),
                "closeSettings() 必须先 flushSettings()：用户关掉设置页时那次改动还没到"
                        + "去抖窗口就丢了");
        String activity = stripComments(read(root, ACTIVITY));
        require(activity.contains("override fun onStop()") && activity.contains("flushSettings()"),
                ACTIVITY + " 的 onStop 必须调 viewModel.flushSettings()："
                        + "「点一下开关就直接大退」是最常见的动作，"
                        + "onStop 是应用不再可见时最早的可靠兑现时机");

        String restore = functionBody(vm, "private suspend fun restoreRuntimeChoices(");
        require(restore.contains("readEngineSettings("),
                "restoreRuntimeChoices 必须走 readEngineSettings()：只读回权限/推理两项不够 —— "
                        + "其余被 configure() 反写的字段每启动一次就被默认值冲一次");
        require(restore.contains("rootExecutionEnabled") && restore.contains("sandboxAgentFullAccess"),
                "启动读回必须覆盖到沙箱全权 / Root / 保活：这三项原来连静态 setter 都没有，"
                        + "是「大退就恢复成未修改」里最显眼的一组");
        require(restore.contains("contextWindow") && restore.contains("autoCompact"),
                "启动读回必须覆盖上下文窗口与自动压缩（同上）");

        // (b) 的顺序约束：读回必须早于那个会落盘的 configure()。
        String init = functionBody(vm, "private fun initSessionState()");
        int restoreAt = init.indexOf("restoreRuntimeChoices()");
        int syncAt = init.indexOf("syncActiveProfile()");
        require(restoreAt >= 0 && syncAt >= 0,
                "initSessionState 里必须同时有 restoreRuntimeChoices() 与 syncActiveProfile()");
        require(restoreAt < syncAt,
                "restoreRuntimeChoices() 必须在 syncActiveProfile() **之前**："
                        + "后者会 configure() 并落盘，界面此刻还是默认值的话，"
                        + "盘上刚存的用户设置会被默认值当场冲掉");

        System.out.println("SettingsPersistenceTest PASS"
                + "（键/注册/字段齐全 · 主题与密钥走专用槽 · 设置页改动当场落盘"
                + " · 启动读回覆盖全部字段且早于 configure()）");
    }
}

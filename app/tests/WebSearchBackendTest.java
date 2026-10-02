import java.nio.file.*;
import java.util.*;

/**
 * 搜索服务（RikkaHub 形态改造 + 结果数 1-50）的守卫。
 *
 * <p>守的是三条「删掉不会编译失败、只会悄悄退化」的约定：
 *
 * <h2>1. 密钥制服务必须**互斥**，不许静默回落</h2>
 *
 * 用户点名 Tavily/Exa/Brave/SearXNG 时，缺 Key / 缺地址要**报错并指路设置页**，
 * 绝不能掉回 DuckDuckGo ——「配了 Tavily 却拿到免费后端的结果」比一次失败难查得多。
 * 因此 keyedSearch 的分发必须出现在免费后端之前，且错误直接 return。
 *
 * <h2>2. 结果数上限 1-50 是三处同心圆</h2>
 *
 * 设置页标题、AppSettings 的 clamp、工具的 MAX_RESULTS 必须一致；
 * 任何一处还停在 10，用户在设置里填 20 都会被悄悄夹回 10。
 *
 * <h2>3. 密钥 / 实例地址必须真的从设置流到工具</h2>
 *
 * AppSettings → SettingsDraft → EngineOverrides → SessionConfig → WebSearchTool，
 * 中间任何一环断掉（字段加了但不透传）都是「配了也白配」，且编译器不管。
 */
public final class WebSearchBackendTest {

    private static final String MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/SettingsModels.kt";
    private static final String DIALOG =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/settings/SettingsDialog.kt";
    private static final String TOOL =
            "app/src/main/java/com/termux/app/zhicode/tools/WebSearchTool.java";
    private static final String JSON =
            "app/src/main/java/com/termux/app/zhicode/tools/WebSearchJson.java";
    private static final String SESSION =
            "app/src/main/java/com/termux/app/zhicode/model/SessionConfig.java";
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

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String models = stripComments(read(root, MODELS));
        String dialog = stripComments(read(root, DIALOG));
        String tool = stripComments(read(root, TOOL));
        String json = stripComments(read(root, JSON));
        String session = stripComments(read(root, SESSION));
        String controller = stripComments(read(root, CONTROLLER));
        String vm = stripComments(read(root, VM));
        String sqTool = squash(tool);
        String sqModels = squash(models);

        // ---- 1. 服务枚举：密钥制 / 自建服务与它们的配置标记 ------------------
        for (String wire : new String[]{"TAVILY", "EXA", "BRAVE", "SEARXNG"}) {
            require(sqModels.contains(wire + "(\""),
                    MODELS + " 里找不到服务 " + wire + " —— 搜索服务列表不完整");
        }
        require(sqModels.contains("needsKey:Boolean=false") && sqModels.contains("needsKey=true")
                        && sqModels.contains("needsBaseUrl=true"),
                MODELS + " 的 WebSearchProvider 必须带 needsKey/needsBaseUrl 标记："
                        + "设置页靠它们决定追加哪些输入框");
        // 结果数上限：常量与 clamp 是同一处定义。
        require(sqModels.contains("WEB_RESULTS_MAX=50"),
                MODELS + " 的 WEB_RESULTS_MAX 必须是 50（用户要求从 10 放宽）");
        require(sqModels.contains("webSearchKeys:Map<WebSearchProvider,String>"),
                MODELS + " 必须按服务各存一份 Key：单一 Key 字段会在切换服务时互相覆盖");

        // ---- 2. 设置页：条件输入框 + 标题 1-50 ------------------------------
        require(squash(dialog).contains("title=\"搜索服务\""),
                DIALOG + " 必须有「搜索服务」选择项（RikkaHub 形态）");
        require(dialog.contains("needsKey") && dialog.contains("needsBaseUrl"),
                DIALOG + " 必须按 needsKey/needsBaseUrl 条件渲染 Key / 实例地址输入框："
                        + "固定显示会让所有服务都带着无关输入框");
        require(dialog.contains("webSearchKeys[draft.webSearchProvider]"),
                DIALOG + " 的 Key 输入框必须读写当前选中服务的条目（切换服务不丢）");
        require(dialog.contains("默认搜索结果数（1-50）"),
                DIALOG + " 的结果数标题必须与实际上限一致（1-50）");

        // ---- 3. 工具：上限、分发、互斥、缺配置报错 ---------------------------
        require(sqTool.contains("MAX_RESULTS=50"),
                TOOL + " 的 MAX_RESULTS 必须是 50：设置放宽了但工具还在夹回 10，"
                        + "用户填 20 永远拿到 10 条");
        require(sqTool.contains("keyedSearch("),
                TOOL + " 必须有 keyedSearch 分发（密钥制/自建服务的公共入口）");
        for (String provider : new String[]{"\"tavily\"", "\"exa\"", "\"brave\"", "\"searxng\""}) {
            require(sqTool.contains(provider), TOOL + " 缺少服务 " + provider + " 的实现");
        }
        // 互斥：keyedSearch 的调用（及其错误 return）必须出现在免费后端之前。
        int keyedAt = tool.indexOf("raw = keyedSearch(");
        int duckAt = tool.indexOf("raw = duckDuckGo(");
        require(keyedAt >= 0 && duckAt >= 0 && keyedAt < duckAt,
                TOOL + " 里 keyedSearch 必须先于免费后端：密钥制服务失败必须直接报错，"
                        + "不能掉进 auto 的免费回落里");
        require(tool.contains("requireKey(") && tool.contains("设置 → 联网搜索 → 搜索服务"),
                TOOL + " 缺 Key 时必须报错并指路设置页，而不是静默回落免费后端");
        // 解析层独立成类（无 android import），四家都有。
        for (String parser : new String[]{"parseTavily", "parseExa", "parseBrave", "parseSearxng"}) {
            require(json.contains(parser), JSON + " 缺少解析器 " + parser);
        }

        // ---- 4. 配置链路：每一环都必须透传 ---------------------------------
        require(session.contains("webSearchApiKey") && session.contains("webSearchBaseUrl"),
                SESSION + " 必须有 webSearchApiKey/webSearchBaseUrl 字段");
        int copyAt = session.indexOf("copy.webSearchApiKey = webSearchApiKey;");
        require(copyAt >= 0,
                SESSION + " 的 copy() 必须透传 webSearchApiKey：不透传的话换会话就丢配置");
        require(controller.contains("webSearchApiKey") && controller.contains("webSearchBaseUrl"),
                CONTROLLER + " 的 EngineOverrides 必须带 Key/实例地址");
        require(controller.contains("config.webSearchApiKey = it"),
                CONTROLLER + " 必须把 Key 写进 SessionConfig（字段加了不透传 = 配了也白配）");
        require(vm.contains("WebSearchProvider.TAVILY -> \"tavily\"")
                        && vm.contains("WebSearchProvider.SEARXNG -> \"searxng\""),
                VM + " 的协议映射必须覆盖新服务");
        require(vm.contains("webSearchKeys[s.settings.webSearchProvider]"),
                VM + " 必须取**当前选中服务**的 Key：发错服务的 Key 比不发更糟");
    }
}

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
    private static final String SEARCH_PAGE =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/dialogs/SearchServicesOverlay.kt";
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

        // ---- 2. 设置页只留入口；字段渲染在独立的服务页 ----------------------
        //
        // ⚠️ 这一版把「设置页里一个下拉 + 一个 Key 框」换成了 RikkaHub 形态的
        // **独立整页**（多服务列表 + 每服务专属选项）。所以旧断言（"设置页必须按
        // needsKey 渲染 Key 框"）已经过期 —— 守的不变量换成下面三条。
        require(squash(dialog).contains("onClick={onNavigate(\"searchServices\")}"),
                DIALOG + " 必须留「搜索服务」入口行（指向独立整页）："
                        + "服务列表与每服务选项都在那一页里");
        require(dialog.contains("默认搜索结果数（1-50）"),
                DIALOG + " 的结果数标题必须与实际上限一致（1-50）");

        // 服务页按类型声明渲染字段（而不是每种类型一段 if），并按需显示 Key / 地址。
        String overlay = stripComments(read(root, SEARCH_PAGE));
        require(squash(overlay).contains("draft.type.fields.forEach"),
                SEARCH_PAGE + " 必须按服务类型声明渲染字段（type.fields.forEach）："
                        + "每加一家服务都要在这里再写一段 if 的话，迟早会漏");
        require(overlay.contains("SearchFieldName.API_KEY"),
                SEARCH_PAGE + " 的 Key 输入框必须按字段名判断（API_KEY）");
        require(overlay.contains("field.options"), 
                SEARCH_PAGE + " 必须支持「只能选几个值」的字段（depth / topic 用下拉）");

        // 列表 + 当前生效项 + 增删改选：RikkaHub 那套交互必须有。
        for (String op : new String[]{"onSelect", "onEdit", "onDelete", "onNew"}) {
            require(overlay.contains(op),
                    SEARCH_PAGE + " 缺少 " + op + " —— 服务列表必须具备增删改与「设为当前」");
        }

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
        // 解析层独立成类（无 android import，可 JVM 单测），每家一个解析器。
        for (String parser : new String[]{
                "parseTavily", "parseExa", "parseBrave", "parseSearxng",
                "parsePerplexity", "parseLinkup", "parseFirecrawl",
                "parseBocha", "parseMetaso", "parseZhipu",
                "parsePlainText", "parseCustom"}) {
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

        // ---- 5. 生效服务必须真的送到引擎（RikkaHub 形态的关键接线）---------
        //
        // 「列表里能切、实际请求还用老配置」是这次改造最容易出的问题：
        // 服务页一切正常、守卫也全绿，只有发出去的请求还是旧后端。
        require(vm.contains("SearchServiceStore.active(getApplication())"),
                VM + " 的 engineOverrides 必须读**当前生效**的搜索服务："
                        + "读不到的话，用户在列表里切来切去，实际发出去还是旧配置");
        require(squash(vm).contains("webSearchProvider=activeService?.type?.name?.lowercase()"),
                VM + " 必须把生效服务的类型名作为 provider 下发："
                        + "服务类型的小写名就是引擎侧的 provider（见 WebSearchTool.providerName）");
        require(vm.contains("webSearchServiceConfig"),
                VM + " 必须把每服务选项（depth/topic/…）下发给引擎："
                        + "只下发 provider 名的话，用户在编辑页填的选项全部无效");
        require(vm.contains("getSearchServiceKey(getApplication(), it.id)"),
                VM + " 必须取**生效服务那条记录**的密钥（按 id，不能按类型）："
                        + "同一种类型可以配多条（比如两个 SearXNG 实例）");

        // 切换服务之后要立刻生效，不等下一次进设置。
        require(vm.contains("syncSearchServiceToEngine()"),
                VM + " 在选择/保存/删除搜索服务之后必须重新下发配置："
                        + "否则用户切了服务却要等下一次发消息才生效（而界面已经显示「使用中」）");
    }
}

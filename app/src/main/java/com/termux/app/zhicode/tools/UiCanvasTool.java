package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.zhizhu.zhicode.UiCanvasController;
import com.zhizhu.zhicode.UiCanvasStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * 运行期改变界面外观，不用重新构建 APK。
 *
 * <h3>它为什么配得上一个工具</h3>
 * 改一个颜色、挪一个块的顺序，本来是「改源码 → 编译 → 装 APK」三步。
 * 这个工具把它变成一次调用：改动只写在一份画布文档里，界面对着文档渲染。
 * 因此它只能**动允许动的东西** —— 文档里描述的是一组已经存在于界面上的具名槽位，
 * 不能凭空造出新功能。真要做功能改动，仍然得改源码。
 *
 * <h3>三组操作</h3>
 * <ul>
 *   <li><b>读写画布</b>：{@code get} 返回当前文档；{@code export} 额外附上导出的文本，
 *       用户可以把结果贴回源码里固化下来。</li>
 *   <li><b>改画布</b>：{@code set_palette} 与 {@code patch}/{@code add}/{@code remove}/
 *       {@code move}/{@code reparent}/{@code set} 都会落盘（{@link UiCanvasStore#save}）。</li>
 *   <li><b>不改画布</b>：{@code preview} 只发布不落盘；{@code undo}/{@code redo} 走历史；
 *       {@code reset} 回到初始文档。</li>
 * </ul>
 * 这个区分是有意的：{@code preview} 让模型可以先看一眼再决定要不要留下 ——
 * 而在真机上「先改了再撤销」的成本远高于「先预览」。
 *
 * <h3>为什么返回附加内容而不是纯文本</h3>
 * {@code ui_canvas} 类型的附加内容块会被界面直接消费并渲染，不需要模型复述一遍文档。
 * 把整份文档塞进给模型看的文本里，只会白烧 token。
 */
final class UiCanvasTool implements ZhiTool {

    /** 改画布并落盘的操作。 */
    private static final List<String> SAVING_OPERATIONS = Arrays.asList(
        "set_palette", "patch", "add", "remove", "move", "reparent", "set");

    /** 只改内存、发布给界面看的操作。 */
    private static final String PREVIEW = "preview";

    /** 单个操作可以不带 {@code operations} 数组、自身就是一次操作的操作名。 */
    private static final List<String> SINGLE_OPERATION = Arrays.asList(
        "add", "remove", "move", "reparent", "set");

    /** 调色板里允许被改的键。名单是allow-list：不让模型往文档里塞任意键。 */
    private static final List<String> PALETTE_KEYS = Arrays.asList(
        "background", "surface", "text", "muted", "accent", "green");

    private static final String DONE = "UI 画布操作已完成。";
    private static final String PREVIEWED = "UI 画布预览已验证。";
    private static final String EXPORTED = "UI 画布导出成功。";

    private final android.content.Context context;

    public UiCanvasTool(android.content.Context context) {
        // 只留 Application 上下文：工具的存活时间可能比任何 Activity 都长。
        this.context = context.getApplicationContext();
    }

    @Override public String name() { return "ui_canvas"; }

    @Override public String description() {
        return "Get, preview, patch, undo, redo, reset, or export bounded runtime UI canvas changes"
            + " without rebuilding the APK.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject operations = new JSONObject()
                .put("type", "array")
                .put("maxItems", 80)
                .put("items", ToolSchemas.freeObject(
                    "Node patch: id plus visibility, padding, margin, or textSize"));
            JSONObject properties = new JSONObject()
                .put("operation", ToolSchemas.enumString("Canvas operation",
                    "get", "set_palette", "patch", "preview", "add", "remove", "move",
                    "reparent", "set", "undo", "redo", "reset", "export"))
                .put("palette", ToolSchemas.freeObject("Allow-listed ARGB colors"))
                .put("operations", operations);
            return ToolSchemas.object(properties, "operation");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String operation = input.optString("operation", "get");
        JSONObject current = UiCanvasStore.load(context);
        JSONObject result = current;

        if ("undo".equals(operation)) {
            result = UiCanvasStore.undo(context);
        } else if ("redo".equals(operation)) {
            result = UiCanvasStore.redo(context);
        } else if ("reset".equals(operation)) {
            UiCanvasStore.reset(context);
            // reset 之后必须重新读一次：拿 reset 之前的对象返回会让界面
            // 又画出刚被丢掉的那份文档。
            result = UiCanvasStore.load(context);
        } else if (SAVING_OPERATIONS.contains(operation) || PREVIEW.equals(operation)) {
            result = patched(current, input, operation);
            if (PREVIEW.equals(operation)) UiCanvasStore.publishPreview(result);
            else UiCanvasStore.save(context, result);
        }

        if ("export".equals(operation)) {
            return document(EXPORTED, current.put("export", UiCanvasStore.export(current)));
        }
        return document(PREVIEW.equals(operation) ? PREVIEWED : DONE, result);
    }

    /**
     * 把入参里的调色板与操作数组应用到当前文档上。
     *
     * <p>先深拷一份当前文档再改：{@link UiCanvasStore#load} 的返回值会被
     * 「没做任何改动」的分支（比如 {@code get}）原样返回，就地修改会污染它。
     */
    private static JSONObject patched(JSONObject current, JSONObject input, String operation)
            throws Exception {
        JSONObject updated = new JSONObject(current.toString());
        mergePalette(updated, input.optJSONObject("palette"));

        JSONArray operations = input.optJSONArray("operations");
        // add/remove/move/reparent/set 允许把操作直接写成顶层参数（不带 operations 数组），
        // 这是最常见的一次改一个块的用法。
        if (operations == null && SINGLE_OPERATION.contains(operation)) {
            operations = new JSONArray().put(input);
        }
        return operations == null ? updated : UiCanvasController.applyOperations(updated, operations);
    }

    /** 只合并名单内的键；未知键忽略（文档由界面渲染，塞进未知键只会被忽略或报错）。 */
    private static void mergePalette(JSONObject document, JSONObject requested) throws Exception {
        if (requested == null) return;
        JSONObject palette = document.optJSONObject("palette");
        if (palette == null) {
            palette = new JSONObject();
            document.put("palette", palette);
        }
        for (String key : PALETTE_KEYS) {
            if (requested.has(key)) palette.put(key, requested.getInt(key));
        }
    }

    /** 包装成界面能直接消费的附加内容块。 */
    private static ToolExecutionResult document(String message, JSONObject document) throws Exception {
        JSONArray content = new JSONArray()
            .put(new JSONObject().put("type", "ui_canvas").put("document", document));
        return ToolExecutionResult.okWithAdditionalContent(message, content);
    }
}

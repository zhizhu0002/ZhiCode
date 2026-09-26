package com.zhizhu.zhicode;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 把一份画布文档套用到真实的 View 树上，并支持「先预览、再落盘」的两段式修改。
 *
 * <h3>为什么自定义节点只能是「呈现层」</h3>
 * 画布由模型生成，所以它写下的东西必须视为不可信输入。这里的处理方式是**限制它能碰到什么**，
 * 而不是去过滤它能写什么：自定义节点只能创建 {@link FrameLayout} / {@link TextView} /
 * {@link Space} 这三种壳，属性只能落在可见性、文本、文字尺寸、颜色、内边距、外边距、权重、
 * 顺序与父节点这些**已知名字**上。它**不能**创建任意 View、不能绑定监听器、不能改 id、
 * 也不能替换既有控件的语义 —— 也就是说「模型改得动样子，改不动行为」。
 *
 * <h3>两段式的意义</h3>
 * {@link #applyOperations} 只在文档对象上做增删改（纯函数，可单测），
 * 由调用方决定是 {@code UiCanvasStore.publishPreview}（只广播给前台的预览）还是
 * {@code save}（落盘）。模型误操作时，用户丢掉预览即可，磁盘上的文档没有被改过。
 *
 * <h3>边界（都写在这里，因为它们决定「模型能造成多大破坏」）</h3>
 * <ul>
 *   <li>一次最多 {@value #MAX_OPS} 个操作、节点数不超过 {@value #MAX_NODES}；</li>
 *   <li>稳定 id 必须非空、不超过 {@value #MAX_ID} 字符、不含路径分隔符与控制字符，且不能叫
 *       {@code root}（那是整棵树的根，不能被当成节点改）；</li>
 *   <li>超出范围的值不是报错，而是**忽略**：模型给 {@code textSize: 999} 时保留原样，
 *       而不是把界面搞成无法阅读 —— 报错会让整次修改失败，忽略只损失那一项。</li>
 * </ul>
 */
public final class UiCanvasController {

    /** 一次 {@code applyOperations} 接受的操作用量上限（与工具 schema 一致）。 */
    public static final int MAX_OPS = 80;

    private static final int MAX_NODES = 400;
    private static final int MAX_ID = 64;

    /** 可写属性的取值范围。越界的值一律忽略，见类注释里的第三条边界。 */
    private static final float TEXT_SIZE_MIN = 8f;
    private static final float TEXT_SIZE_MAX = 48f;
    private static final int SPACING_MIN = 0;
    private static final int SPACING_MAX = 128;
    private static final float WEIGHT_MIN = 0f;
    private static final float WEIGHT_MAX = 20f;

    /** {@code set} 允许写进节点的字段名 —— 这是一张白名单，不是「除某某之外都行」。 */
    private static final String[] SETTABLE = {
            "parent", "visibility", "text", "textSize", "textColor",
            "padding", "margin", "weight", "index", "removed", "type"
    };


    private UiCanvasController() {}

    // ------------------------------------------------------------------ 预览

    /** 把文档里的节点逐个套到 {@code root} 上。文档不合法时**什么都不做**（预览不该炸界面）。 */
    public static void apply(View root, JSONObject document) {
        if (root == null || document == null) return;
        JSONArray nodes = nodesOf(document);
        if (nodes == null || nodes.length() > MAX_NODES) return;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null) applyNode(root, node);
        }
    }

    /** 文档里的节点数组，兼容旧版 {@code screens.chat.nodes} 的位置。 */
    private static JSONArray nodesOf(JSONObject document) {
        JSONArray nodes = document.optJSONArray("nodes");
        if (nodes != null) return nodes;
        JSONObject screens = document.optJSONObject("screens");
        if (screens == null) return null;
        JSONObject chat = screens.optJSONObject("chat");
        return chat == null ? null : chat.optJSONArray("nodes");
    }

    private static void applyNode(View root, JSONObject node) {
        String id = node.optString("id", "");
        if (!isSafeId(id)) return;

        View view = findById(root, id);
        if (node.optBoolean("removed", false)) {
            // `…root` 是既有的根容器：它被「删除」时只隐藏，不能真的从树里摘掉，
            // 否则后续的预览会失去挂载点。
            if (view != null && view.getTag() != null && !id.endsWith(".root")) {
                view.setVisibility(View.GONE);
            }
            return;
        }
        if (view == null && node.has("parent")) view = createNode(root, node);
        if (view == null) return;
        if (node.has("parent")) attachToParent(root, view, node.optString("parent", ""));
        if (view.getParent() instanceof ViewGroup && node.has("index")) {
            ViewGroup group = (ViewGroup) view.getParent();
            int current = group.indexOfChild(view);
            int wanted = clampIndex(node.optInt("index", current), group.getChildCount() - 1);
            if (current >= 0 && wanted != current) {
                group.removeViewAt(current);
                group.addView(view, wanted);
            }
        }
        applyProperties(view, node);
    }

    /** 换父节点。只有 {@code parent} 真的是一个 ViewGroup 时才动，且不重复挂载。 */
    private static void attachToParent(View root, View view, String parentId) {
        View parent = findById(root, parentId);
        if (!(parent instanceof ViewGroup) || view.getParent() == parent) return;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        ((ViewGroup) parent).addView(view);
    }

    /** 只有这三种壳可以被创建。**不**接受类型名来自文档 —— 那是「改得动样子，改不动行为」的前提。 */
    private static View createNode(View root, JSONObject node) {
        View parent = findById(root, node.optString("parent", ""));
        if (!(parent instanceof ViewGroup)) return null;
        String type = node.optString("type", "slot");
        View created;
        if ("text".equals(type) || "action".equals(type)) {
            TextView text = new TextView(root.getContext());
            text.setText(node.optString("text", ""));
            text.setTextColor(node.optInt("textColor", Color.WHITE));
            created = text;
        } else if ("spacer".equals(type)) {
            created = new Space(root.getContext());
        } else {
            created = new FrameLayout(root.getContext());
        }
        created.setTag(node.optString("id"));
        int height = "spacer".equals(type) ? 1 : ViewGroup.LayoutParams.WRAP_CONTENT;
        ((ViewGroup) parent).addView(created, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return created;
    }

    private static void applyProperties(View view, JSONObject node) {
        applyVisibility(view, node.optString("visibility", ""));
        boolean isText = view instanceof TextView;
        if (isText && node.has("text")) ((TextView) view).setText(node.optString("text", ""));
        if (isText && node.has("textSize")) {
            float size = (float) node.optDouble("textSize", -1);
            if (size >= TEXT_SIZE_MIN && size <= TEXT_SIZE_MAX) ((TextView) view).setTextSize(size);
        }
        if (isText && node.has("textColor")) ((TextView) view).setTextColor(node.optInt("textColor", Color.WHITE));
        if (node.has("padding")) {
            int padding = node.optInt("padding", -1);
            if (padding >= SPACING_MIN && padding <= SPACING_MAX) view.setPadding(padding, padding, padding, padding);
        }
        applyLayoutMargins(view, node);
    }

    private static void applyVisibility(View view, String visibility) {
        if ("visible".equals(visibility)) view.setVisibility(View.VISIBLE);
        else if ("gone".equals(visibility)) view.setVisibility(View.GONE);
        else if ("invisible".equals(visibility)) view.setVisibility(View.INVISIBLE);
    }

    private static void applyLayoutMargins(View view, JSONObject node) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            int margin = node.optInt("margin", -1);
            if (margin >= SPACING_MIN && margin <= SPACING_MAX) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.leftMargin = margin;
                margins.topMargin = margin;
                margins.rightMargin = margin;
                margins.bottomMargin = margin;
                view.setLayoutParams(margins);
            }
        }
        if (node.has("weight") && params instanceof LinearLayout.LayoutParams) {
            float weight = (float) node.optDouble("weight", -1);
            if (weight >= WEIGHT_MIN && weight <= WEIGHT_MAX) {
                ((LinearLayout.LayoutParams) params).weight = weight;
                view.setLayoutParams(params);
            }
        }
    }

    private static int clampIndex(int index, int lastIndex) {
        return Math.max(0, Math.min(lastIndex, index));
    }

    // ------------------------------------------------------------ 文档修改

    /**
     * 在一份文档的**副本**上执行操作表，返回新文档。
     *
     * <p>纯函数：不改入参、不碰 View、不碰磁盘。所以它可以被 JVM 单测直接验证
     * （见 {@code app/src/test/.../UiCanvasLogicTest.java}）—— 这一层的规则有
     * 「哪些操作被支持」「越界怎么处理」这类容易被改错、又只在运行时才暴露的分支。
     *
     * <p>操作按数组顺序执行，后面的操作看得到前面操作的结果；同一个 id 重复出现时
     * 以最后一次为准（{@code add} 对已存在的 id 会直接报错，避免静默覆盖）。
     */
    public static JSONObject applyOperations(JSONObject document, JSONArray operations) {
        try {
            JSONObject result = new JSONObject(document.toString());
            JSONArray nodes = result.optJSONArray("nodes");
            if (nodes == null) {
                nodes = new JSONArray();
                result.put("nodes", nodes);
            }
            if (operations == null || operations.length() > MAX_OPS) {
                throw new IllegalArgumentException("operations limited to 80");
            }
            for (int i = 0; i < operations.length(); i++) {
                applyOperation(nodes, operations.optJSONObject(i));
            }
            return result;
        } catch (IllegalArgumentException rejected) {
            throw rejected;
        } catch (Exception broken) {
            throw new IllegalArgumentException("invalid canvas operation", broken);
        }
    }

    private static void applyOperation(JSONArray nodes, JSONObject operation) throws org.json.JSONException {
        if (operation == null) throw new IllegalArgumentException("operation must be object");
        String action = operation.optString("action", operation.optString("op", "set"));
        String id = operation.optString("id", "");
        if (!isSafeId(id)) throw new IllegalArgumentException("invalid stable id");

        int at = indexOf(nodes, id);
        switch (action) {
            case "add":
                if (at >= 0) throw new IllegalArgumentException("node already exists");
                nodes.put(nodeFrom(operation));
                return;
            case "remove":
                if (at >= 0) nodes.optJSONObject(at).put("removed", true);
                return;
            case "move":
            case "reparent":
                if (at < 0) at = appendPlaceholder(nodes, id, operation);
                place(nodes.optJSONObject(at), action, operation);
                return;
            case "set":
                if (at < 0) at = appendCopy(nodes, operation);
                copySettable(nodes.optJSONObject(at), operation);
                return;
            default:
                throw new IllegalArgumentException("unsupported canvas action");
        }
    }

    /** 操作里除了「动作」字段，其余都可以当成节点内容复制过去。 */
    private static JSONObject nodeFrom(JSONObject operation) throws org.json.JSONException {
        JSONObject node = new JSONObject(operation.toString());
        node.remove("action");
        node.remove("op");
        return node;
    }

    /**
     * {@code move} / {@code reparent} 指向一个还不存在的 id 时，先用这个 id 建一个**最小**节点。
     * 注意它与 {@link #appendCopy} 不同，而且这个差别是刻意的：
     * 移动一个还不存在的节点时，操作里带的 {@code index}/{@code parent} 是**给它安排的位置**，
     * 不是节点内容；把它们当内容抄进去，会凭空造出一个携带位置字段的节点。
     */
    private static int appendPlaceholder(JSONArray nodes, String id, JSONObject operation) throws org.json.JSONException {
        JSONObject placeholder = new JSONObject()
                .put("id", id)
                .put("type", operation.optString("type", "slot"));
        nodes.put(placeholder);
        return nodes.length() - 1;
    }

    /** {@code set} 指向一个还不存在的 id 时，把整条操作（去掉动作字段）当成该节点的内容。 */
    private static int appendCopy(JSONArray nodes, JSONObject operation) throws org.json.JSONException {
        nodes.put(nodeFrom(operation));
        return nodes.length() - 1;
    }

    private static void place(JSONObject node, String action, JSONObject operation) throws org.json.JSONException {
        if ("move".equals(action)) {
            node.put("index", Math.max(0, Math.min(MAX_NODES - 1, operation.optInt("index", 0))));
        } else {
            node.put("parent", operation.optString("parent", ""));
        }
    }

    private static void copySettable(JSONObject node, JSONObject operation) throws org.json.JSONException {
        for (String key : SETTABLE) {
            if (operation.has(key)) node.put(key, operation.get(key));
        }
    }

    private static int indexOf(JSONArray nodes, String id) {
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null && id.equals(node.optString("id"))) return i;
        }
        return -1;
    }

    /**
     * 稳定 id 的判据。与 {@code UiCanvasStore} 里那份是**同一件事**（两边都要拦恶意文档），
     * 但两处的用途不同：这里决定「要不要对界面动手」，那里决定「要不要落盘」，
     * 所以各自检查一遍而不是互相信任 —— 少一处检查就多一条绕过路径。
     */
    private static boolean isSafeId(String id) {
        if (id == null || id.trim().isEmpty() || id.length() > MAX_ID) return false;
        if (id.indexOf('/') >= 0 || id.indexOf('\\') >= 0) return false;
        if ("root".equals(id)) return false;
        return !id.matches(".*[\\p{Cntrl}].*");
    }

    private static View findById(View view, String id) {
        if (id.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findById(group.getChildAt(i), id);
                if (found != null) return found;
            }
        }
        return null;
    }
}

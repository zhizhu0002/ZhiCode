package com.zhizhu.zhicode;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 画布文档的持久化、校验与历史（撤销/重做）。
 *
 * <h3>为什么这里同时管「校验」</h3>
 * 画布文档有两个来源：模型通过工具生成的，和用户磁盘上已有的（旧版本写的）。
 * 两者都不能当成可信输入 —— 前者是模型生成的，后者可能来自更老的格式。
 * 所以**所有**进出的文档都过一遍 {@link #validate}：入口是 {@code save} 与 {@code export}，
 * 出口是 {@code load}。校验通过后落盘的文档一定带当前版本号。
 *
 * <h3>「失败就退回默认值」是刻意的</h3>
 * {@code load} 读到一个坏文档时返回默认画布，而不是抛异常。理由是这里的失败模式：
 * 抛异常会让界面起不来（一份坏文档把整个画布功能变成崩溃），退回默认值只损失那一次修改，
 * 而且用户下次保存就会覆盖掉它。反过来 {@code save} 会**抛**：用户在同一个进程里
 * 明确要求保存，静默丢掉他的修改更糟。
 *
 * <h3>不变式</h3>
 * <ul>
 *   <li>落盘的文档字节数不超过 {@link #MAX_BYTES}，节点数不超过 {@link #MAX_NODES}；</li>
 *   <li>历史栈每边最多 {@link #MAX_HISTORY} 步，且**每次保存都会清空重做栈**
 *       （分支历史会让「撤销」变成一件无法预测的事）；</li>
 *   <li>广播给监听者的文档总是**副本**：监听者改它不能影响存储层，
 *       否则一次预览就会把磁盘上的内容也改了。</li>
 * </ul>
 */
public final class UiCanvasStore {

    /** 历史步数上限。20 步足够覆盖「试几次预览」的用法，又不至于把 prefs 撑大。 */
    private static final int MAX_HISTORY = 20;
    /** 文档字节上限：与 {@code MAX_NODES} 一起，保证 prefs 里存不下一个能把界面拖死的东西。 */
    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_NODES = 400;
    private static final int MAX_ID = 64;

    /**
     * 当前文档版本。v2 是「只有 screens.chat」的旧结构，v3 才是完整的场景图
     * （{@code nodes}）；读取旧文档时由 {@link #migrate} 补齐。
     */
    private static final int VERSION = 3;

    /**
     * 存盘位置。名字里带 {@code BRAND_SLUG} 是**有意的**：它是 prefs 文件名的一部分，
     * 而 prefs 文件名在改名后不会自动跟着变 —— 写死字面量会让改名那天悄悄丢掉用户的画布。
     */
    private static final String PREFS = TermuxConstants.BRAND_SLUG + "_ui_canvas";
    private static final String DOCUMENT = "document";
    private static final String UNDO = "undo";
    private static final String REDO = "redo";

    /** 默认节点的稳定 id 与类型。它们是 Agent 与界面之间的约定（按 id 找插槽），不是随意取的。 */
    private static final String[] DEFAULT_SLOTS = {
            "workspace.root|scene",
            "workspace.global_bar|slot",
            "workspace.sidebar|slot",
            "workspace.primary|slot",
            "workspace.secondary|slot",
            "workspace.session_area|slot",
            "agent_progress|slot",
            "composer|slot",
    };

    /** 调色板的键与默认值。键名进文档，模型可以按名字引用它们。 */
    private static final String[] PALETTE_KEYS = {"background", "surface", "text", "muted", "accent", "green"};
    private static final int[] PALETTE_VALUES = {0xff12110f, 0xff191816, 0xfff0ece5, 0xffa49d93, 0xffd97757, 0xff7eb288};

    /** 可创建的节点类型。与 {@code UiCanvasController} 里创建壳的分支一一对应。 */
    private static final String NODE_TYPES = "scene|container|stack|slot|text|action|spacer|overlay";

    /** 画布变化的订阅者。用并发容器是因为预览可能在任意线程触发。 */
    public interface ChangeListener {
        void onCanvasChanged(JSONObject document, boolean preview);
    }

    private static final CopyOnWriteArrayList<ChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

    private UiCanvasStore() {}

    // ------------------------------------------------------------ 订阅与广播

    public static void addChangeListener(ChangeListener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void removeChangeListener(ChangeListener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    /** 只广播，不落盘 —— 「先预览、再保存」的前半段。 */
    public static void publishPreview(JSONObject document) {
        broadcast(validate(document), true);
    }

    private static void broadcast(JSONObject document, boolean preview) {
        for (ChangeListener listener : LISTENERS) {
            try {
                // 给每个监听者一份副本：否则监听者手里的引用与存储层是同一个对象，
                // 它随手改一下就把还没保存的内容也改了（而预览本应是可丢掉的）。
                listener.onCanvasChanged(new JSONObject(document.toString()), preview);
            } catch (Exception ignored) {
                // 一个监听者抛错不能影响别的监听者，也不能影响已经完成的持久化。
            }
        }
    }

    // ------------------------------------------------------------ 读写

    public static JSONObject load(Context context) {
        return read(context, DOCUMENT, defaults());
    }

    public static synchronized void save(Context context, JSONObject document) {
        JSONObject checked = validate(document);
        SharedPreferences prefs = prefs(context);
        JSONArray undo = readArray(prefs, UNDO);
        push(undo, load(context));
        prefs.edit()
                .putString(DOCUMENT, checked.toString())
                .putString(UNDO, undo.toString())
                .putString(REDO, "[]")
                .commit();
        broadcast(checked, false);
    }

    public static synchronized JSONObject undo(Context context) {
        return step(context, UNDO, REDO);
    }

    public static synchronized JSONObject redo(Context context) {
        return step(context, REDO, UNDO);
    }

    public static synchronized void reset(Context context) {
        save(context, defaults());
    }

    /** 导出成字符串。同样过校验 —— 导出的东西要能被另一处再读回来。 */
    public static String export(JSONObject document) {
        return validate(document).toString();
    }

    public static int maxBytes() {
        return MAX_BYTES;
    }

    /**
     * 在两条历史栈之间走一步：从 {@code from} 取上一份文档，把当前文档压进 {@code to}。
     *
     * <p>栈里为空时返回当前文档而**不广播**：什么都没变，通知监听者会让界面白刷一次。
     * 这一点与原实现一致，也是这里唯一一处「看起来像漏了通知」的地方，所以写明。
     */
    private static JSONObject step(Context context, String from, String to) {
        SharedPreferences prefs = prefs(context);
        JSONArray source = readArray(prefs, from);
        if (source.length() == 0) return load(context);

        JSONObject current = load(context);
        JSONObject next = source.optJSONObject(source.length() - 1);
        source.remove(source.length() - 1);
        if (next == null) return current;

        JSONArray target = readArray(prefs, to);
        push(target, current);
        prefs.edit()
                .putString(DOCUMENT, next.toString())
                .putString(from, source.toString())
                .putString(to, target.toString())
                .commit();
        broadcast(next, false);
        return next;
    }

    // ------------------------------------------------------------ 默认文档

    /** 一份全新的画布：版本、根 id、调色板、节点表，以及给旧版读取路径留的 {@code screens}。 */
    public static JSONObject defaults() {
        try {
            JSONObject palette = new JSONObject();
            for (int i = 0; i < PALETTE_KEYS.length; i++) palette.put(PALETTE_KEYS[i], PALETTE_VALUES[i]);
            return new JSONObject()
                    .put("version", VERSION)
                    .put("root", "workspace.root")
                    .put("palette", palette)
                    .put("nodes", defaultNodes())
                    .put("screens", new JSONObject().put("chat", new JSONObject().put("nodes", new JSONArray())));
        } catch (Exception impossible) {
            // 上面键值都是常量，抛错只可能来自 JSON 实现本身。包成运行时异常说清楚来源。
            throw new IllegalStateException("cannot build the default canvas", impossible);
        }
    }

    private static JSONArray defaultNodes() throws Exception {
        JSONArray nodes = new JSONArray();
        for (String slot : DEFAULT_SLOTS) {
            int bar = slot.indexOf('|');
            nodes.put(new JSONObject().put("id", slot.substring(0, bar)).put("type", slot.substring(bar + 1)));
        }
        return nodes;
    }

    /**
     * 把旧文档补成当前版本。
     *
     * <p>只做**加法**：旧文档里的 {@code screens.chat.nodes} 若存在，就升格成顶层 {@code nodes}。
     * 读坏的旧文档时静默退回默认值 —— 迁移是尽力而为的一步，它失败不该让读取也失败。
     */
    public static JSONObject migrate(JSONObject legacy) {
        JSONObject document = defaults();
        if (legacy == null) return document;
        try {
            JSONObject palette = legacy.optJSONObject("palette");
            if (palette != null) document.put("palette", new JSONObject(palette.toString()));
            JSONArray old = legacyNodes(legacy);
            if (old != null) document.put("nodes", new JSONArray(old.toString()));
        } catch (Exception ignored) {
            // 见上面的注释：迁移失败就保留默认值。
        }
        return document;
    }

    private static JSONArray legacyNodes(JSONObject document) {
        JSONObject screens = document.optJSONObject("screens");
        if (screens == null) return null;
        JSONObject chat = screens.optJSONObject("chat");
        return chat == null ? null : chat.optJSONArray("nodes");
    }

    // ------------------------------------------------------------ 校验

    /**
     * 校验并返回一份**副本**（顺带把版本号写成当前值）。
     *
     * <p>返回副本而不是入参，是为了让调用方手里的文档与落盘的内容不是同一个对象：
     * 否则校验完之后调用方再改一下，磁盘上就会存着没校验过的东西。
     */
    private static JSONObject validate(JSONObject input) {
        if (input == null) throw new IllegalArgumentException("document");
        try {
            JSONObject document = new JSONObject(input.toString());
            int bytes = document.toString().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_BYTES) throw new IllegalArgumentException("UI canvas exceeds 64 KiB");
            document.put("version", VERSION);
            checkPalette(document.optJSONObject("palette"));
            JSONArray nodes = document.optJSONArray("nodes");
            if (nodes != null) {
                if (nodes.length() > MAX_NODES) throw new IllegalArgumentException("too many canvas nodes");
                checkNodes(nodes);
            }
            return document;
        } catch (IllegalArgumentException rejected) {
            throw rejected;
        } catch (Exception broken) {
            throw new IllegalArgumentException("invalid UI canvas", broken);
        }
    }

    /** 颜色只能是整数。{@code optInt} 拿到非整数时返回哨兵值，用它把「不是颜色」检出来。 */
    private static void checkPalette(JSONObject palette) {
        if (palette == null) return;
        for (String key : PALETTE_KEYS) {
            if (!palette.has(key)) continue;
            if (palette.optInt(key, Integer.MIN_VALUE) == Integer.MIN_VALUE) {
                throw new IllegalArgumentException("invalid color: " + key);
            }
        }
    }

    private static void checkNodes(JSONArray nodes) {
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) throw new IllegalArgumentException("node must be object");
            if (!isStableId(node.optString("id", ""))) throw new IllegalArgumentException("invalid stable node id");
            String type = node.optString("type", "slot");
            if (!type.matches(NODE_TYPES)) throw new IllegalArgumentException("unsupported node type");
        }
    }

    /**
     * 稳定 id 的判据：非空（去空白后）、不超过 {@value #MAX_ID} 字符、不含路径分隔符与控制字符。
     * 这些限制都是为了它后面要被当作查找键用 —— 带分隔符的 id 会与路径式查找撞车。
     */
    private static boolean isStableId(String id) {
        if (id.trim().isEmpty() || id.length() > MAX_ID) return false;
        if (id.indexOf('/') >= 0 || id.indexOf('\\') >= 0) return false;
        return !id.matches(".*[\\p{Cntrl}].*");
    }

    // ------------------------------------------------------------ prefs 细节

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 读一份文档。读到坏数据时退回 {@code fallback}，而不是抛 —— 见类注释里的第二条。 */
    private static JSONObject read(Context context, String key, JSONObject fallback) {
        try {
            String stored = prefs(context).getString(key, "");
            if (stored == null || stored.isEmpty()) return fallback;
            return validate(migrate(new JSONObject(stored)));
        } catch (Exception unreadable) {
            return fallback;
        }
    }

    private static JSONArray readArray(SharedPreferences prefs, String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (Exception broken) {
            return new JSONArray();
        }
    }

    /** 压栈。满了就淘汰**最旧**的一步：用户想撤销的总是最近那几步。 */
    private static void push(JSONArray stack, JSONObject document) {
        if (stack.length() >= MAX_HISTORY) stack.remove(0);
        stack.put(document);
    }
}

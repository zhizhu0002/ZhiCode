package com.zhizhu.zhicode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * 画布两层里**纯逻辑**那一半的行为测试：文档校验、迁移，以及操作表的语义。
 *
 * <p>为什么这一层需要 JVM 单测而不是源码文本断言：这两处最容易改错的东西都是
 * 「写错了也能编译过、只在运行时才暴露」的分支 ——
 * 越界的值是被忽略还是报错、{@code move} 与 {@code set} 指向不存在的 id 时各建什么样的节点、
 * {@code add} 撞上已有 id 该不该静默覆盖、历史栈满了淘汰哪一端。
 * 这些差异不会让编译失败，也不会让界面崩，只会让模型的修改**看起来没生效**。
 *
 * <p>覆盖不到的部分要说清楚：{@code apply(View,…)} 需要真实的 View 树、
 * {@code load/save/undo/redo} 需要 {@code SharedPreferences}，两者都不在 JVM 单测范围里。
 * 本文件只测不依赖 Android 的部分（{@code applyOperations} / {@code export} /
 * {@code migrate} / {@code defaults}）。
 */
public final class UiCanvasLogicTest {

    private static JSONObject documentWith(String... ids) throws Exception {
        JSONArray nodes = new JSONArray();
        for (String id : ids) nodes.put(new JSONObject().put("id", id).put("type", "slot"));
        return new JSONObject().put("nodes", nodes);
    }

    private static JSONArray ops(String json) throws Exception {
        return new JSONArray(json);
    }

    private static String idAt(JSONObject document, int index) {
        return document.optJSONArray("nodes").optJSONObject(index).optString("id");
    }

    // ------------------------------------------------------------ 操作表语义

    @Test
    public void addAppendsAndStripsTheActionFields() throws Exception {
        JSONObject out = UiCanvasController.applyOperations(documentWith("a"), ops(
                "[{\"action\":\"add\",\"id\":\"b\",\"type\":\"text\",\"text\":\"hi\"}]"));
        assertEquals(2, out.optJSONArray("nodes").length());
        JSONObject added = out.optJSONArray("nodes").optJSONObject(1);
        assertEquals("b", added.optString("id"));
        assertFalse("动作字段不该被写进节点", added.has("action"));
        assertFalse("旧写法 op 也不该被写进节点", added.has("op"));
    }

    @Test
    public void addOnAnExistingIdIsRejectedRatherThanSilentlyOverwriting() throws Exception {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> UiCanvasController.applyOperations(documentWith("a"), ops(
                        "[{\"action\":\"add\",\"id\":\"a\",\"type\":\"slot\"}]")));
        assertTrue(rejected.getMessage().contains("already exists"));
    }

    @Test
    public void removeMarksTheNodeInsteadOfDeletingIt() throws Exception {
        // 「标记为 removed」而不是删掉，是为了让撤销能靠历史栈整份文档回退，
        // 同时让预览阶段的删除保持可逆（真实 View 只是被隐藏）。
        JSONObject out = UiCanvasController.applyOperations(documentWith("a", "b"), ops(
                "[{\"action\":\"remove\",\"id\":\"a\"}]"));
        assertEquals("节点数不变", 2, out.optJSONArray("nodes").length());
        assertTrue(out.optJSONArray("nodes").optJSONObject(0).optBoolean("removed"));
    }

    @Test
    public void moveOnAMissingIdCreatesAPlaceholderCarryingOnlyIdAndType() throws Exception {
        JSONObject out = UiCanvasController.applyOperations(documentWith(), ops(
                "[{\"action\":\"move\",\"id\":\"ghost\",\"type\":\"slot\",\"index\":3,\"text\":\"ignored\"}]"));
        JSONObject created = out.optJSONArray("nodes").optJSONObject(0);
        assertEquals("ghost", created.optString("id"));
        assertEquals(3, created.optInt("index"));
        assertFalse("位置参数不是内容：不该被当成节点字段抄进来", created.has("text"));
    }

    @Test
    public void setOnAMissingIdCopiesTheWholeOperationExceptTheAction() throws Exception {
        JSONObject out = UiCanvasController.applyOperations(documentWith(), ops(
                "[{\"action\":\"set\",\"id\":\"new\",\"type\":\"text\",\"text\":\"hello\",\"padding\":4}]"));
        JSONObject created = out.optJSONArray("nodes").optJSONObject(0);
        assertEquals("hello", created.optString("text"));
        assertEquals(4, created.optInt("padding"));
        assertFalse(created.has("action"));
    }

    @Test
    public void setOnlyWritesWhitelistedFields() throws Exception {
        JSONObject out = UiCanvasController.applyOperations(documentWith("a"), ops(
                "[{\"action\":\"set\",\"id\":\"a\",\"text\":\"ok\",\"evil\":\"payload\",\"onClickListener\":\"x\"}]"));
        JSONObject node = out.optJSONArray("nodes").optJSONObject(0);
        assertEquals("ok", node.optString("text"));
        assertFalse("白名单之外的字段不得进文档", node.has("evil"));
        assertFalse("尤其不能带进任何像监听器的东西", node.has("onClickListener"));
    }

    @Test
    public void opIsAcceptedAsAnAliasForActionAndDefaultsToSet() throws Exception {
        JSONObject viaAlias = UiCanvasController.applyOperations(documentWith("a"), ops(
                "[{\"op\":\"set\",\"id\":\"a\",\"text\":\"x\"}]"));
        assertEquals("x", viaAlias.optJSONArray("nodes").optJSONObject(0).optString("text"));
        JSONObject viaDefault = UiCanvasController.applyOperations(documentWith("a"), ops(
                "[{\"id\":\"a\",\"text\":\"y\"}]"));
        assertEquals("少了 action/op 时默认当 set", "y", viaDefault.optJSONArray("nodes").optJSONObject(0).optString("text"));
    }

    @Test
    public void unsafeIdsAreRejected() throws Exception {
        for (String bad : new String[]{"", "   ", "root", "a/b", "a\\b", "a\u0001b", "x".repeat(65)}) {
            assertThrows("应当拒绝 id: " + bad, IllegalArgumentException.class,
                    () -> UiCanvasController.applyOperations(documentWith(), ops(
                            "[{\"action\":\"set\",\"id\":\"" + bad.replace("\\", "\\\\") + "\"}]")));
        }
    }

    @Test
    public void operationsAreCappedAtEighty() throws Exception {
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 81; i++) {
            if (i > 0) many.append(',');
            many.append("{\"action\":\"set\",\"id\":\"n").append(i).append("\"}");
        }
        many.append(']');
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> UiCanvasController.applyOperations(documentWith(), ops(many.toString())));
        assertTrue(rejected.getMessage().contains("80"));
        // 正好 80 个必须通过 —— 否则上限实际是 79，而工具 schema 说的是 80。
        StringBuilder exact = new StringBuilder("[");
        for (int i = 0; i < 80; i++) {
            if (i > 0) exact.append(',');
            exact.append("{\"action\":\"set\",\"id\":\"n").append(i).append("\"}");
        }
        exact.append(']');
        assertEquals(80, UiCanvasController.applyOperations(documentWith(), ops(exact.toString()))
                .optJSONArray("nodes").length());
        assertEquals("工具 schema 里的上限必须与实现一致", 80, UiCanvasController.MAX_OPS);
    }

    @Test
    public void unknownActionIsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> UiCanvasController.applyOperations(
                documentWith("a"), ops("[{\"action\":\"destroy\",\"id\":\"a\"}]")));
    }

    @Test
    public void operationsSeeEachOthersEffectsInOrder() throws Exception {
        JSONObject out = UiCanvasController.applyOperations(documentWith(), ops(
                "[{\"action\":\"set\",\"id\":\"a\",\"text\":\"1\"},"
                        + "{\"action\":\"add\",\"id\":\"b\"},"
                        + "{\"action\":\"move\",\"id\":\"a\",\"index\":1}]"));
        assertEquals("a", idAt(out, 0));
        assertEquals("b", idAt(out, 1));
        assertEquals("同一 id 的后续操作应落在已有节点上", 2, out.optJSONArray("nodes").length());
    }

    @Test
    public void theInputDocumentIsNotModified() throws Exception {
        JSONObject document = documentWith("a");
        UiCanvasController.applyOperations(document, ops("[{\"action\":\"set\",\"id\":\"a\",\"text\":\"changed\"}]"));
        assertNull("applyOperations 必须是纯函数：预览可丢弃的前提就是它不碰入参",
                document.optJSONArray("nodes").optJSONObject(0).optString("text", null));
    }

    @Test
    public void nodesArrayIsCreatedWhenTheDocumentHasNone() throws Exception {
        JSONObject out = UiCanvasController.applyOperations(new JSONObject(), ops(
                "[{\"action\":\"set\",\"id\":\"a\"}]"));
        assertEquals(1, out.optJSONArray("nodes").length());
    }

    // ------------------------------------------------------------ 文档校验与迁移

    @Test
    public void defaultsHaveEveryPaletteKeyAndTheDocumentedSlots() throws Exception {
        JSONObject defaults = UiCanvasStore.defaults();
        assertEquals(3, defaults.optInt("version"));
        JSONObject palette = defaults.optJSONObject("palette");
        for (String key : new String[]{"background", "surface", "text", "muted", "accent", "green"}) {
            assertTrue("默认调色板缺 " + key, palette.has(key));
        }
        JSONArray nodes = defaults.optJSONArray("nodes");
        assertEquals(8, nodes.length());
        assertEquals("workspace.root", nodes.optJSONObject(0).optString("id"));
        assertEquals("scene", nodes.optJSONObject(0).optString("type"));
        for (int i = 0; i < nodes.length(); i++) {
            assertEquals("默认节点必须是 slot/场景这两种既有类型",
                    i == 0 ? "scene" : "slot", nodes.optJSONObject(i).optString("type"));
        }
    }

    @Test
    public void exportRejectsOversizedDocumentsAndStampsTheVersion() throws Exception {
        JSONObject small = new JSONObject().put("nodes", new JSONArray());
        assertEquals(3, new JSONObject(UiCanvasStore.export(small)).optInt("version"));

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 70000; i++) huge.append('x');
        JSONObject tooBig = new JSONObject().put("padding", huge.toString());
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> UiCanvasStore.export(tooBig));
        assertTrue(rejected.getMessage().contains("64 KiB"));
    }

    @Test
    public void exportRejectsBadNodeIdsTypesAndPalette() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> UiCanvasStore.export(
                new JSONObject().put("palette", new JSONObject().put("text", "not-a-color"))));
        assertThrows(IllegalArgumentException.class, () -> UiCanvasStore.export(
                new JSONObject().put("nodes", new JSONArray().put(42))));
        assertThrows(IllegalArgumentException.class, () -> UiCanvasStore.export(
                new JSONObject().put("nodes", new JSONArray()
                        .put(new JSONObject().put("id", "a").put("type", "webview")))));
        assertThrows(IllegalArgumentException.class, () -> UiCanvasStore.export(
                new JSONObject().put("nodes", new JSONArray().put(new JSONObject().put("id", "a/b")))));
        assertThrows(IllegalArgumentException.class, () -> UiCanvasStore.export(null));
    }

    @Test
    public void exportAcceptsEveryDocumentedNodeType() throws Exception {
        for (String type : new String[]{"scene", "container", "stack", "slot", "text", "action", "spacer", "overlay"}) {
            UiCanvasStore.export(new JSONObject().put("nodes", new JSONArray()
                    .put(new JSONObject().put("id", "n").put("type", type))));
        }
    }

    @Test
    public void migrateLiftsTheLegacyScreensChatNodesAndKeepsAForeignPalette() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("palette", new JSONObject().put("text", 0xff000000))
                .put("screens", new JSONObject().put("chat", new JSONObject()
                        .put("nodes", new JSONArray().put(new JSONObject().put("id", "old").put("type", "slot")))));
        JSONObject migrated = UiCanvasStore.migrate(legacy);
        assertEquals(0xff000000, migrated.optJSONObject("palette").optInt("text"));
        assertEquals("旧结构里的节点必须被升格到顶层", 1, migrated.optJSONArray("nodes").length());
        assertEquals("old", migrated.optJSONArray("nodes").optJSONObject(0).optString("id"));
    }

    @Test
    public void migrateOfNothingOrOfJunkStillYieldsAUsableDocument() throws Exception {
        assertEquals(8, UiCanvasStore.migrate(null).optJSONArray("nodes").length());
        // 旧文档的形状完全不对时：保留默认值，不抛 —— 读取路径不能因为一份坏文档而失败。
        JSONObject junk = new JSONObject().put("screens", "not-an-object").put("palette", 7);
        assertEquals(8, UiCanvasStore.migrate(junk).optJSONArray("nodes").length());
    }
}

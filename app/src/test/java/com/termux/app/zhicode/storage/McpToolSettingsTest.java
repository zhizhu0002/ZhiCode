package com.termux.app.zhicode.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * MCP 每工具开关在 {@link McpConfigStore.Server} 上的语义。
 *
 * <p>为什么这些必须有 JVM 单测而不是只靠源码断言：它们全是「写错了不会编译失败」
 * 的地方，而且症状都很隐蔽 ——
 *
 * <ul>
 *   <li><b>缺省写反</b>：把"没有记录"当成禁用，升级一次就把用户所有的 MCP 工具
 *       悄悄关掉。界面上只是几个开关关着，看不出发生过什么。</li>
 *   <li><b>序列化漏一处</b>：{@code toJson} 漏了 {@code tools} → 设完开关重启就没了；
 *       {@code fromJson} 漏了 → 同上；{@code copy} 漏了 → 改了副本、原件没变。</li>
 *   <li><b>默认值也写进文件</b>：配置会因为一次测试连接被塞满默认值，
 *       用户打开 mcp.json 会看到一堆自己没设过的东西。</li>
 * </ul>
 *
 * <p>这些用例只碰 {@code Server} 这个纯数据类，不读盘、不起进程。
 */
public class McpToolSettingsTest {

    private static McpConfigStore.Server serverWith(String json) throws Exception {
        return McpConfigStore.Server.fromJson(new JSONObject(json));
    }

    // ---------- 缺省语义 ----------

    @Test
    public void 没有记录的工具默认启用() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        // 这条是升级路径的全部保障：已有配置里没有 tools 字段。
        assertTrue("缺省必须是启用，否则升级一次会把用户所有 MCP 工具悄悄关掉",
            server.isToolEnabled("some_tool"));
        assertFalse(server.isToolApprovalRequired("some_tool"));
    }

    @Test
    public void 空的tools对象也算全部启用() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\",\"tools\":{}}");
        assertTrue(server.isToolEnabled("x"));
        assertFalse(server.isToolApprovalRequired("x"));
    }

    // ---------- 写入与读回 ----------

    @Test
    public void 关掉一个工具之后它能被读回() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        server.setToolOptions("dangerous", false, false);
        assertFalse(server.isToolEnabled("dangerous"));
        // 同一个服务器上的别的工具不受影响 —— 用整份工具设置覆盖过去就会踩到这里。
        assertTrue(server.isToolEnabled("safe"));
    }

    @Test
    public void 打开审批之后它能被读回() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        server.setToolOptions("risky", true, true);
        assertTrue(server.isToolEnabled("risky"));
        assertTrue(server.isToolApprovalRequired("risky"));
    }

    @Test
    public void 存进文件再读回来设置还在() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        server.setToolOptions("t", false, true);
        McpConfigStore.Server reloaded = McpConfigStore.Server.fromJson(server.toJson());
        assertFalse("toJson/fromJson 漏掉 tools 就会在这里露出来（设了开关重启就没了）",
            reloaded.isToolEnabled("t"));
        assertTrue(reloaded.isToolApprovalRequired("t"));
    }

    @Test
    public void 拷贝也带上工具设置() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        server.setToolOptions("t", false, false);
        assertFalse("copy() 漏掉 tools 就是「改了副本、原件没变」的反面：副本丢了设置",
            server.copy().isToolEnabled("t"));
    }

    @Test
    public void 拷贝是深拷贝改副本不影响原件() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        McpConfigStore.Server clone = server.copy();
        clone.setToolOptions("t", false, false);
        assertTrue("深拷贝：改副本不该改到原件", server.isToolEnabled("t"));
        assertFalse(clone.isToolEnabled("t"));
    }

    // ---------- 配置文件保持干净 ----------

    @Test
    public void 改回默认值时不留下记录() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        server.setToolOptions("t", false, true);
        assertTrue(server.tools.has("t"));
        server.setToolOptions("t", true, false);
        // 两个值都回到默认就删掉记录：配置文件应当只记录用户真正改过的东西。
        assertFalse("改回默认应当删掉记录，而不是写一份默认值进 mcp.json",
            server.tools.has("t"));
        assertTrue(server.isToolEnabled("t"));
    }

    @Test
    public void 空工具名被忽略() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\"}");
        server.setToolOptions("", false, false);
        server.setToolOptions(null, false, false);
        assertEquals(0, server.tools.length());
    }

    // ---------- 宽容读取 ----------

    @Test
    public void tools字段类型不对时不影响服务器本体() throws Exception {
        // 用户手写过 mcp.json 是常态。一个坏字段不该让整台服务器失效。
        McpConfigStore.Server server = serverWith("{\"name\":\"a\",\"tools\":\"这不是对象\"}");
        assertEquals("a", server.name);
        assertTrue("坏字段退回默认（全部启用），而不是让这台服务器不可用",
            server.isToolEnabled("t"));
    }

    @Test
    public void 单条记录类型不对时退回默认() throws Exception {
        McpConfigStore.Server server = serverWith("{\"name\":\"a\",\"tools\":{\"t\":\"坏\"}}");
        assertTrue("坏记录退回默认（启用）", server.isToolEnabled("t"));
        assertFalse(server.isToolApprovalRequired("t"));
    }
}

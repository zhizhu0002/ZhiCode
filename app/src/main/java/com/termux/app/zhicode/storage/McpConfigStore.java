package com.termux.app.zhicode.storage;

import com.termux.app.zhicode.json.JsonItems;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 服务器配置的持久化，落在 {@code ~/.zhicode/mcp.json}。
 *
 * <h3>这里最重要的一件事：坏文件绝不能变成「没有配置」</h3>
 * 调用方的固定流程是「{@link #load()} → 改 → {@link #save(List)}」。
 * 如果 load 在解析失败时静默返回空列表，那么下一次 save 就会把一个空配置写回去 ——
 * 用户的全部 MCP 服务器配置被一次解析错误抹掉，而且没有任何提示。
 *
 * <p>所以解析失败时**不**假装配置是空的：坏文件被改名留档
 * （{@code mcp.json.corrupt-<时间戳>}），然后才返回空列表。数据还在磁盘上、
 * 可人工恢复，而且因为留档时把它移走了，下一轮 save 也不会覆盖它。
 *
 * <h3>写入同样原子（走 {@link AtomicFiles}）</h3>
 * 原先的做法是「先删旧文件再 rename」，那会在两步之间留下一个
 * 「配置文件不存在」的窗口 —— 此刻进程被杀，用户的配置就真的没了。
 * rename 本身就能覆盖目标，不需要那个空档。
 */
public final class McpConfigStore {

    /** 一台 MCP 服务器的配置。字段与 mcp.json 的键一一对应。 */
    public static final class Server {

        private static final String KEY_NAME = "name";
        private static final String KEY_TYPE = "type";
        private static final String KEY_COMMAND = "command";
        private static final String KEY_URL = "url";
        private static final String KEY_ARGS = "args";
        private static final String KEY_ENV = "env";
        private static final String KEY_HEADERS = "headers";
        private static final String KEY_SCOPE = "scope";
        private static final String KEY_ENABLED = "enabled";
        private static final String KEY_TOOLS = "tools";

        /** {@link #tools} 里每一项的键。 */
        private static final String TOOL_ENABLED = "enabled";
        private static final String TOOL_APPROVAL = "approval";

        private static final String DEFAULT_TYPE = "stdio";
        private static final String DEFAULT_SCOPE = "user";

        public String name = "";
        /** {@code stdio} / {@code http} / {@code sse}。 */
        public String type = DEFAULT_TYPE;
        /** stdio 模式的启动命令。 */
        public String command = "";
        /** http / sse 模式的地址。 */
        public String url = "";
        public final List<String> args = new ArrayList<>();
        public JSONObject env = new JSONObject();
        public JSONObject headers = new JSONObject();
        /** {@code user} 或 {@code project}。 */
        public String scope = DEFAULT_SCOPE;
        public boolean enabled = true;

        /**
         * 单个工具的设置：工具名 → {@code {"enabled":bool,"approval":bool}}。
         *
         * <h3>缺省必须是「启用」</h3>
         * 没有这条记录 = 启用、不需审批。这条规则的重要性在于**升级路径**：
         * 已有配置里根本没有 {@code tools} 字段，若把"没记录"当成禁用，
         * 升级一次就会把用户所有的 MCP 工具悄悄关掉，而界面上那几个开关
         * 只是关着、看不出发生过什么。
         *
         * <p>只在用户**显式关掉或显式打开审批**时才写记录 —— 这样配置文件也不会
         * 因为一次测试连接就被塞满默认值。
         */
        public JSONObject tools = new JSONObject();

        /** 深拷贝。改副本不会影响已保存的对象，反之亦然。 */
        public Server copy() {
            Server clone = new Server();
            clone.name = name;
            clone.type = type;
            clone.command = command;
            clone.url = url;
            clone.args.addAll(args);
            clone.env = deepCopy(env);
            clone.headers = deepCopy(headers);
            clone.scope = scope;
            clone.enabled = enabled;
            clone.tools = deepCopy(tools);
            return clone;
        }

        /** 某个工具是否启用。没有记录就是启用（见 {@link #tools} 的说明）。 */
        public boolean isToolEnabled(String toolName) {
            JSONObject entry = toolEntry(toolName);
            return entry == null || entry.optBoolean(TOOL_ENABLED, true);
        }

        /**
         * 某个工具是否「每次调用都要用户确认」。
         *
         * <p>这个标志只有在 {@code McpTool} 把它转成 {@code requiresApproval} 之后
         * 才有实际效果 —— 光存在配置里是不会拦任何东西的。
         */
        public boolean isToolApprovalRequired(String toolName) {
            JSONObject entry = toolEntry(toolName);
            return entry != null && entry.optBoolean(TOOL_APPROVAL, false);
        }

        /**
         * 写入一个工具的设置。两个值都是默认值时**删掉记录**而不是写一份默认值：
         * 配置文件应当只记录用户真正改过的东西。
         */
        public void setToolOptions(String toolName, boolean toolEnabled, boolean approvalRequired) {
            if (toolName == null || toolName.trim().isEmpty()) return;
            try {
                if (tools == null) tools = new JSONObject();
                if (toolEnabled && !approvalRequired) {
                    tools.remove(toolName);
                    return;
                }
                tools.put(toolName, new JSONObject()
                    .put(TOOL_ENABLED, toolEnabled)
                    .put(TOOL_APPROVAL, approvalRequired));
            } catch (Throwable ignored) {
                // JSONObject.put 在这里不会失败；真失败也只能保留原设置。
            }
        }

        private JSONObject toolEntry(String toolName) {
            if (toolName == null || tools == null) return null;
            return tools.optJSONObject(toolName);
        }

        JSONObject toJson() throws Exception {
            JSONArray argArray = new JSONArray();
            for (String arg : args) argArray.put(arg);
            return new JSONObject()
                .put(KEY_NAME, name)
                .put(KEY_TYPE, type)
                .put(KEY_COMMAND, command)
                .put(KEY_URL, url)
                .put(KEY_ARGS, argArray)
                .put(KEY_ENV, env == null ? new JSONObject() : env)
                .put(KEY_HEADERS, headers == null ? new JSONObject() : headers)
                .put(KEY_SCOPE, scope)
                .put(KEY_ENABLED, enabled)
                .put(KEY_TOOLS, tools == null ? new JSONObject() : tools);
        }

        /**
         * 宽容读取。
         *
         * <p>任何一个字段缺失或类型不对都用默认值，**不**因为一个坏条目丢掉整份配置：
         * 用户手写过 mcp.json 是常态，而一条服务器写错不该让其它服务器一起失效。
         */
        static Server fromJson(JSONObject source) {
            Server server = new Server();
            server.name = source.optString(KEY_NAME, "");
            server.type = source.optString(KEY_TYPE, DEFAULT_TYPE);
            server.command = source.optString(KEY_COMMAND, "");
            server.url = source.optString(KEY_URL, "");
            JSONArray args = source.optJSONArray(KEY_ARGS);
            if (args != null) {
                for (int i = 0; i < args.length(); i++) server.args.add(args.optString(i, ""));
            }
            JSONObject env = source.optJSONObject(KEY_ENV);
            if (env != null) server.env = deepCopy(env);
            JSONObject headers = source.optJSONObject(KEY_HEADERS);
            if (headers != null) server.headers = deepCopy(headers);
            server.scope = source.optString(KEY_SCOPE, DEFAULT_SCOPE);
            // 默认启用：一份「存在但被禁用」的配置在界面上与不存在很难区分，
            // 而用户写下它就是要用它。
            server.enabled = source.optBoolean(KEY_ENABLED, true);
            JSONObject tools = source.optJSONObject(KEY_TOOLS);
            if (tools != null) server.tools = deepCopy(tools);
            return server;
        }
    }

    /** 配置文件格式版本。 */
    private static final int VERSION = 1;

    /** 配置体积上限。这是一份服务器清单，到这个量级说明文件已损坏。 */
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;

    private static final String CONFIG_FILE = "mcp.json";
    private static final String KEY_VERSION = "version";
    private static final String KEY_SERVERS = "servers";
    private static final String CORRUPT_SUFFIX = ".corrupt-";
    private static final int WRITE_INDENT = 2;

    private final File file;

    /** 见 {@link #lastLoadFailure()}。 */
    private volatile String lastLoadFailure = "";

    public McpConfigStore() {
        File directory = TermuxConstants.dataDir();
        directory.mkdirs();
        file = new File(directory, CONFIG_FILE);
    }

    public File getFile() {
        return file;
    }

    /**
     * 读取全部服务器配置。
     *
     * <p>返回空列表时有两种可能：真的没有配置，或文件坏了 —— 后者已经通过把坏文件
     * 留档来保证数据不丢，可读的原因见 {@link #lastLoadFailure()}。
     */
    public synchronized List<Server> load() {
        List<Server> servers = new ArrayList<>();
        if (!file.isFile()) {
            recordFailure("");
            return servers;
        }
        try {
            String text = new String(AtomicFiles.readBytes(file, MAX_CONFIG_BYTES),
                StandardCharsets.UTF_8);
            JSONArray array = new JSONObject(text).optJSONArray(KEY_SERVERS);
            if (array != null) {
                for (JSONObject entry : JsonItems.of(array)) servers.add(Server.fromJson(entry));
            }
            recordFailure("");
            return servers;
        } catch (Throwable failure) {
            // 关键：先把坏文件留档，绝不静默当成空配置。
            String kept = setAsideCorruptFile();
            recordFailure(failure.getClass().getSimpleName() + ": " + failure.getMessage()
                + (kept.isEmpty() ? "" : "（原文件已留档为 " + kept + "）"));
            return servers;
        }
    }

    /** 原子写入。见类注释。 */
    public synchronized void save(List<Server> servers) throws Exception {
        JSONObject root = new JSONObject().put(KEY_VERSION, VERSION);
        JSONArray array = new JSONArray();
        if (servers != null) {
            for (Server server : servers) array.put(server.toJson());
        }
        root.put(KEY_SERVERS, array);

        File parent = file.getParentFile();
        if (parent != null) AtomicFiles.ensureDirectory(parent, "MCP 配置目录");
        AtomicFiles.publishText(file, root.toString(WRITE_INDENT));
        recordFailure("");
    }

    /** 按名字找一台服务器（不区分大小写）。 */
    public synchronized Server find(String name) {
        if (name == null) return null;
        for (Server server : load()) {
            if (server.name.equalsIgnoreCase(name)) return server;
        }
        return null;
    }

    /**
     * 最近一次加载失败的原因；空串表示上次加载没有出错。
     *
     * <p>要把这条信息保留下来，是因为 {@link #load()} 的返回类型是列表，
     * 没有地方能说「我没读成功」。界面要提示「配置读取失败，原文件已留档」，
     * 就得有这个字段。
     */
    public String lastLoadFailure() {
        return lastLoadFailure;
    }

    /**
     * 把无法解析的文件改名留档。
     *
     * <p>用改名而不是复制：改名之后 {@code mcp.json} 就不存在了，
     * 于是下一轮 save 会新建一份干净的配置，而坏数据完整地留在磁盘上等着人工处理。
     *
     * @return 留档后的文件名；改名失败返回空串
     */
    private String setAsideCorruptFile() {
        String keptName = file.getName() + CORRUPT_SUFFIX + System.currentTimeMillis();
        File kept = new File(file.getParentFile(), keptName);
        return file.renameTo(kept) ? keptName : "";
    }

    private void recordFailure(String reason) {
        lastLoadFailure = reason == null ? "" : reason;
    }

    /** 深拷一份 JSONObject；对端不可读时给空对象（调用方不必判空）。 */
    private static JSONObject deepCopy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Throwable unreadable) {
            return new JSONObject();
        }
    }
}

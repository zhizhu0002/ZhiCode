package com.termux.app.zhicode.storage;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 服务器配置的持久化，落在 {@code ~/.iq/mcp.json}。
 *
 * <h3>这里最重要的一件事：坏文件绝不能变成「没有配置」</h3>
 * 调用方的固定流程是「{@link #load()} → 改 → {@link #save(List)}」。
 * 如果 load 在解析失败时静默返回空列表，那么下一次 save 就会把一个空配置写回去 ——
 * 用户的全部 MCP 服务器配置被一次解析错误抹掉，而且没有任何提示。
 *
 * <p>因此解析失败时<b>不会</b>假装配置是空的：坏文件被改名留档
 * （{@code mcp.json.corrupt-<时间戳>}），然后才返回空列表。
 * 这样数据还在磁盘上、可人工恢复，而且下一轮 save 不会覆盖任何东西。
 *
 * <h3>写入同样是原子的</h3>
 * 先写 .tmp、fsync、再 rename。原先的做法是「先删旧文件再 rename」，
 * 那会在两步之间留下一个「配置文件不存在」的窗口 ——
 * 此时如果进程被杀，用户的配置就真的没了。
 */
public final class McpConfigStore {

    /** 一台 MCP 服务器的配置。字段与 mcp.json 的键一一对应。 */
    public static final class Server {
        public String name = "";
        /** stdio / http / sse。 */
        public String type = "stdio";
        /** stdio 模式的启动命令。 */
        public String command = "";
        /** http / sse 模式的地址。 */
        public String url = "";
        public final List<String> args = new ArrayList<>();
        public JSONObject env = new JSONObject();
        public JSONObject headers = new JSONObject();
        /** user 或 project。 */
        public String scope = "user";
        public boolean enabled = true;

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
            return clone;
        }

        JSONObject toJson() throws Exception {
            JSONArray argArray = new JSONArray();
            for (String arg : args) argArray.put(arg);
            return new JSONObject()
                    .put("name", name)
                    .put("type", type)
                    .put("command", command)
                    .put("url", url)
                    .put("args", argArray)
                    .put("env", env == null ? new JSONObject() : env)
                    .put("headers", headers == null ? new JSONObject() : headers)
                    .put("scope", scope)
                    .put("enabled", enabled);
        }

        /** 宽容读取：任何一个字段缺失或类型不对都用默认值，不因为一个坏条目丢掉整份配置。 */
        static Server fromJson(JSONObject source) {
            Server server = new Server();
            server.name = source.optString("name", "");
            server.type = source.optString("type", "stdio");
            server.command = source.optString("command", "");
            server.url = source.optString("url", "");
            JSONArray args = source.optJSONArray("args");
            if (args != null) {
                for (int i = 0; i < args.length(); i++) server.args.add(args.optString(i, ""));
            }
            JSONObject env = source.optJSONObject("env");
            if (env != null) server.env = deepCopy(env);
            JSONObject headers = source.optJSONObject("headers");
            if (headers != null) server.headers = deepCopy(headers);
            server.scope = source.optString("scope", "user");
            server.enabled = source.optBoolean("enabled", true);
            return server;
        }
    }

    /** 配置文件格式版本。 */
    private static final int VERSION = 1;

    /** 配置体积上限。这是一份服务器清单，超过这个量级说明文件已损坏。 */
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;

    private static final String CONFIG_DIR = ".iq";
    private static final String CONFIG_FILE = "mcp.json";

    private final File file;

    public McpConfigStore() {
        File directory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, CONFIG_DIR);
        directory.mkdirs();
        file = new File(directory, CONFIG_FILE);
    }

    public File getFile() {
        return file;
    }

    /**
     * 读取全部服务器配置。
     *
     * <p>返回值语义有三档，调用方需要知道的是「空列表可能是真的没有配置，
     * 也可能是文件坏了」——后者已经通过把坏文件留档来保证数据不丢，
     * {@link #lastLoadFailure()} 则给出可读的原因。
     */
    public synchronized List<Server> load() {
        List<Server> servers = new ArrayList<>();
        if (!file.isFile()) {
            recordFailure("");
            return servers;
        }
        try {
            String text = readConfigText();
            JSONObject root = new JSONObject(text);
            JSONArray array = root.optJSONArray("servers");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject entry = array.optJSONObject(i);
                    if (entry != null) servers.add(Server.fromJson(entry));
                }
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

    private String readConfigText() throws Exception {
        long length = file.length();
        if (length <= 0) return "";
        if (length > MAX_CONFIG_BYTES) throw new IllegalStateException("MCP 配置超出体积上限: " + length + " 字节");
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) length)) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_CONFIG_BYTES) throw new IllegalStateException("MCP 配置在读取期间增长超出上限");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** 把无法解析的文件改名留档，返回留档后的名字；改名失败返回空串。 */
    private String setAsideCorruptFile() {
        String keptName = CONFIG_FILE + ".corrupt-" + System.currentTimeMillis();
        File kept = new File(file.getParentFile(), keptName);
        return file.renameTo(kept) ? keptName : "";
    }

    /**
     * 原子写入。
     *
     * <p>不先删旧文件：删与 rename 之间的窗口里，配置文件是不存在的，
     * 此刻进程被杀就会丢掉用户的全部配置。rename 本身就能覆盖目标，
     * 不需要中间的空档。
     */
    public synchronized void save(List<Server> servers) throws Exception {
        JSONObject root = new JSONObject().put("version", VERSION);
        JSONArray array = new JSONArray();
        if (servers != null) {
            for (Server server : servers) array.put(server.toJson());
        }
        root.put("servers", array);

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("无法创建 MCP 配置目录: " + parent);
        }
        byte[] data = root.toString(2).getBytes(StandardCharsets.UTF_8);
        File temporary = new File(parent, CONFIG_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        } catch (Throwable failure) {
            temporary.delete();
            throw failure;
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IllegalStateException("无法保存 MCP 配置文件: " + file);
        }
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
     * <p>之所以要把这条信息保留下来：{@code load()} 的返回类型是列表，
     * 没有地方能说「我没读成功」。界面要提示「配置读取失败，原文件已留档」，
     * 就得有这个字段。
     */
    public String lastLoadFailure() {
        return lastLoadFailure;
    }

    private volatile String lastLoadFailure = "";

    private void recordFailure(String reason) {
        lastLoadFailure = reason == null ? "" : reason;
    }

    private static JSONObject deepCopy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Throwable unreadable) {
            return new JSONObject();
        }
    }
}

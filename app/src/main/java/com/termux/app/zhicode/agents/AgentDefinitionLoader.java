package com.termux.app.zhicode.agents;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 读子代理定义（带 YAML frontmatter 的 Markdown），并把四个来源按优先级合并。
 *
 * <h3>优先级：后来的覆盖先来的</h3>
 * <ol>
 *   <li>内置五个（general-purpose / Explore / Plan / verification / zhi-code-guide）</li>
 *   <li>用户级：{@code $HOME/.zhicode/agents}</li>
 *   <li>项目级：从**最外层**工程目录往内，越接近当前目录越优先</li>
 * </ol>
 * 「父目录先、子目录后」这条顺序是有意的：monorepo 的根可能定义了通用规则，
 * 而子包的 {@code .zhicode/agents} 要能覆盖其中某一项。反过来的话子包的定义
 * 会被根上的同名定义盖掉 —— 那正是「我以为改了但没生效」的典型来源。
 *
 * <h3>合并用 {@link LinkedHashMap} + 小写键</h3>
 * 键小写是为了让 {@code Explore} 与 {@code explore} 视为同一个代理（模型对名字的
 * 大小写并不稳定）。用 LinkedHashMap 而不是 HashMap 是为了让「同名覆盖」时的
 * 顺序仍然稳定：{@link #loadAll} 的返回值顺序会进入界面列表。
 *
 * <h3>解析失败 = 忽略这一个文件</h3>
 * 用户手写的 frontmatter 出错是常态。让一次笔误让**所有**代理都消失，
 * 会表现为「子代理功能坏了」，而真正的原因只是某一个文件里少了个冒号。
 */
final class AgentDefinitionLoader {

    /** 没有指定类型时用的名字。 */
    public static final String DEFAULT_AGENT = "general-purpose";

    private static final String AGENTS_DIR_NAME = "agents";
    private static final String MARKDOWN_SUFFIX = ".md";
    private static final String FRONTMATTER_OPEN = "---";
    /** frontmatter 结束标记在正文里的形态（前面必须有换行）。 */
    private static final String FRONTMATTER_CLOSE_INLINE = "\n---";
    /** 跳过起始标记那一行时要走的字符数。 */
    private static final int FRONTMATTER_OPEN_LENGTH = 3;
    /** 结束标记的长度含前导换行：{@code "\n---"}。 */
    private static final int FRONTMATTER_CLOSE_LENGTH = 4;

    private static final String SOURCE_BUILT_IN = "built-in";
    private static final String SOURCE_USER = "user";
    private static final String SOURCE_PROJECT = "project";
    private static final String SOURCE_CUSTOM = "custom";

    /** 会带列表值的三个键。 */
    private static final List<String> LIST_KEYS = Arrays.asList("tools", "disallowedTools", "skills");

    /**
     * 内置代理的工具集，按「段」拆开。
     *
     * <p>顺序**必须逐字保留**：{@code SubagentManager.resolveAllowedTools} 会把它们
     * 放进一个保持插入顺序的集合，最终影响发给模型的工具清单次序 ——
     * 而提示词内容变了就会让上游的提示词缓存失效。所以这里不重排、
     * 只把原来那一长串按原来的相对次序拆成可复用的段。
     */
    private static final List<String> WORKSPACE_TOOLS =
        Arrays.asList("Read", "ReadMany", "Stat", "Tree", "Glob", "Grep", "LS");
    private static final List<String> TASK_TOOLS =
        Arrays.asList("TaskCreate", "TaskGet", "TaskList", "TaskUpdate");
    private static final List<String> WEB_TOOLS = Arrays.asList("WebSearch", "WebFetch");

    /** 按给定次序拼接若干段。 */
    @SafeVarargs
    private static List<String> tools(List<String>... groups) {
        List<String> all = new ArrayList<>();
        for (List<String> group : groups) all.addAll(group);
        return all;
    }

    private AgentDefinitionLoader() {}

    // ================================================================== 加载

    /** 全部可用代理，按优先级合并后的顺序。 */
    public static List<AgentDefinition> loadAll(String projectDirectory) {
        // 低优先级的先放，后面的同名项覆盖前面 —— 见类注释。
        Map<String, AgentDefinition> merged = new LinkedHashMap<>();
        for (AgentDefinition builtIn : builtIns()) put(merged, builtIn);
        loadDirectory(merged, userAgentsDirectory(), SOURCE_USER);
        for (File directory : projectDirectoriesOutermostFirst(projectDirectory)) {
            loadDirectory(merged, agentsDirectoryIn(directory), SOURCE_PROJECT);
        }
        return new ArrayList<>(merged.values());
    }

    /** 按名字找代理（不区分大小写）。找不到返回 {@code null}。 */
    public static AgentDefinition resolve(String projectDirectory, String name) {
        String wanted = name == null || name.trim().isEmpty() ? DEFAULT_AGENT : name.trim();
        for (AgentDefinition definition : loadAll(projectDirectory)) {
            if (definition.name.equalsIgnoreCase(wanted)) return definition;
        }
        return null;
    }

    /**
     * 工程目录链，从最外层到当前目录。
     *
     * <p>走父链而不是只读当前目录：monorepo 里各子包可以有各自的代理定义，
     * 而根上的那份应当作为兜底。
     */
    private static List<File> projectDirectoriesOutermostFirst(String projectDirectory) {
        List<File> chain = new ArrayList<>();
        if (projectDirectory == null) return chain;
        for (File cursor = new File(projectDirectory); cursor != null; cursor = cursor.getParentFile()) {
            chain.add(cursor);
        }
        Collections.reverse(chain);
        return chain;
    }

    private static File userAgentsDirectory() {
        return agentsDirectoryIn(dataDirFor(new File(TermuxConstants.TERMUX_HOME_DIR_PATH)));
    }

    private static File agentsDirectoryIn(File directory) {
        return new File(dataDirFor(directory), AGENTS_DIR_NAME);
    }

    private static File dataDirFor(File directory) {
        return TermuxConstants.dataDirIn(directory);
    }

    private static void put(Map<String, AgentDefinition> merged, AgentDefinition definition) {
        if (definition == null || definition.name.isEmpty()) return;
        merged.put(definition.name.toLowerCase(Locale.US), definition);
    }

    /** 读一个目录下的全部 {@code .md}。排序是为了让覆盖结果不依赖文件系统返回顺序。 */
    private static void loadDirectory(Map<String, AgentDefinition> merged, File directory, String source) {
        if (directory == null || !directory.isDirectory()) return;
        File[] files = directory.listFiles((dir, name) ->
            name.toLowerCase(Locale.US).endsWith(MARKDOWN_SUFFIX));
        if (files == null) return;
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            try {
                put(merged, parse(file, source));
            } catch (Exception invalid) {
                // 见类注释：一个坏文件不该让其它代理一起消失。
            }
        }
    }

    // ================================================================== 解析

    /**
     * 解析一个定义文件。
     *
     * <p>没有 frontmatter（或没找到结束标记）时，**整份正文就是提示词**，
     * 名字取文件名。这是刻意的宽容：只写一段提示词就当作一个代理，是最常见的用法。
     */
    public static AgentDefinition parse(File file, String source) throws Exception {
        String text = read(file);
        AgentDefinition definition = new AgentDefinition();
        definition.source = source == null ? SOURCE_CUSTOM : source;
        definition.sourceFile = file;
        definition.name = stripExtension(file.getName());

        int frontmatterEnd = text.startsWith(FRONTMATTER_OPEN)
            ? text.indexOf(FRONTMATTER_CLOSE_INLINE, FRONTMATTER_OPEN_LENGTH)
            : -1;
        if (frontmatterEnd < 0) {
            definition.prompt = text.trim();
            return definition;
        }
        // yaml 从第一行之后开始，到结束标记之前。
        String yaml = text.substring(text.indexOf('\n') + 1, frontmatterEnd);
        definition.prompt = text.substring(frontmatterEnd + FRONTMATTER_CLOSE_LENGTH).trim();
        parseFrontmatter(yaml, definition);
        // frontmatter 里写了空名字（{@code name: ""}）时回落到文件名。
        if (definition.name.trim().isEmpty()) definition.name = stripExtension(file.getName());
        return definition;
    }

    /**
     * 解析 frontmatter。
     *
     * <p>要处理两种列表写法：
     * <pre>
     *   tools: [Read, Grep]        ← 行内
     *   tools:
     *     - Read                   ← 块状
     *     - Grep
     * </pre>
     * 块状靠 {@code activeListKey} 记住「上一个只写了键、没写值的列表键」，
     * 后续的 {@code - xxx} 都归到它。这就是为什么循环里必须先判断
     * 「这一行是不是列表项」再判断「是不是 key: value」——
     * 顺序反了会把 {@code - Read} 当成一个键名是 {@code - Read} 的条目。
     *
     * <p>不认识的键静默忽略，不报错：定义文件可能来自更新版本的格式，
     * 而为它失败会让用户升级后所有代理都用不了。
     */
    private static void parseFrontmatter(String yaml, AgentDefinition definition) {
        String activeListKey = null;
        for (String raw : yaml.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("-") && activeListKey != null) {
                addListValue(definition, activeListKey, unquote(line.substring(1).trim()));
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) continue;

            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            // 只有「列表键 + 值为空」才进入块状模式；其它情况清掉前一个列表键。
            activeListKey = value.isEmpty() && isListKey(key) ? key : null;
            if (activeListKey != null) continue;

            switch (key) {
                case "name": definition.name = unquote(value); break;
                case "description": definition.description = unquote(value); break;
                case "model": definition.model = unquote(value); break;
                case "effort": definition.effort = unquote(value); break;
                case "permissionMode": definition.permissionMode = unquote(value); break;
                case "isolation": definition.isolation = unquote(value); break;
                case "color": definition.color = unquote(value); break;
                case "memory": definition.memory = unquote(value); break;
                case "background": definition.background = parseBoolean(value); break;
                case "maxTurns": parseMaxTurns(value, definition); break;
                case "tools": addList(definition.tools, value); break;
                case "disallowedTools": addList(definition.disallowedTools, value); break;
                case "skills": addList(definition.skills, value); break;
                default: break;
            }
        }
    }

    /** 轮数上限写错时保持默认值（0 = 用引擎默认），不因为一个坏数字丢掉整个定义。 */
    private static void parseMaxTurns(String value, AgentDefinition definition) {
        try {
            definition.maxTurns = Integer.parseInt(unquote(value));
        } catch (Exception invalid) {
            // 保持默认。
        }
    }

    private static boolean isListKey(String key) {
        return LIST_KEYS.contains(key);
    }

    private static void addListValue(AgentDefinition definition, String key, String value) {
        if ("tools".equals(key)) definition.tools.add(value);
        else if ("disallowedTools".equals(key)) definition.disallowedTools.add(value);
        else if ("skills".equals(key)) definition.skills.add(value);
    }

    /** 行内列表：{@code [a, b, c]} 或 {@code a, b, c} 两种写法都要认。 */
    private static void addList(Set<String> target, String raw) {
        String value = unquote(raw.trim());
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        for (String part : value.split(",")) {
            String item = unquote(part.trim());
            if (!item.isEmpty()) target.add(item);
        }
    }

    /**
     * 去掉一层成对的引号。
     *
     * <p>只处理长度 ≥2 且首尾同种引号的情况：{@code "a} 这种不成对的**保留原样**，
     * 因为去掉首引号会得到 {@code a}（少一个字符），而那看起来像「我写的引号被吃了」。
     */
    private static String unquote(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        boolean doubleQuoted = trimmed.length() >= 2
            && trimmed.startsWith("\"") && trimmed.endsWith("\"");
        boolean singleQuoted = trimmed.length() >= 2
            && trimmed.startsWith("'") && trimmed.endsWith("'");
        return doubleQuoted || singleQuoted
            ? trimmed.substring(1, trimmed.length() - 1)
            : trimmed;
    }

    /** YAML 的布尔写法有好几种，都要认。 */
    private static boolean parseBoolean(String value) {
        String normalized = unquote(value);
        return "true".equalsIgnoreCase(normalized)
            || "yes".equalsIgnoreCase(normalized)
            || "1".equals(normalized);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String read(File file) throws Exception {
        byte[] buffer = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < buffer.length && (read = in.read(buffer, offset, buffer.length - offset)) > 0) {
                offset += read;
            }
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }

    // ================================================================== 内置

    /**
     * 五个内置代理。
     *
     * <p>顺序即它们在界面上的顺序。名字与提示词都是**用户可见的契约**
     * （界面按名字显示、提示词决定它的行为），所以两者都逐字保留。
     */
    private static List<AgentDefinition> builtIns() {
        return Arrays.asList(generalPurpose(), explore(), plan(), verification(), guide());
    }

    private static AgentDefinition generalPurpose() {
        AgentDefinition definition = base(DEFAULT_AGENT,
            "General-purpose agent for complex research and multi-step coding tasks.");
        definition.tools.add("*");
        definition.prompt = "You are a general-purpose ZhiCode subagent. Complete the delegated task fully. "
            + "Search broadly when necessary, analyze relevant files, make scoped changes when asked, verify "
            + "meaningful changes, and return a concise report with essential findings and actions. Do not "
            + "create documentation unless explicitly requested.";
        return definition;
    }

    private static AgentDefinition explore() {
        AgentDefinition definition = base("Explore",
            "Fast read-only agent specialized for searching and understanding codebases.");
        // haiku 只表示「这一档模型」，见 SubagentManager.applyModel：
        // 它会被映射回父会话实际用的模型，而不是一个硬编码的模型名。
        definition.model = "haiku";
        definition.permissionMode = "plan";
        definition.tools.addAll(tools(WORKSPACE_TOOLS, Arrays.asList("GitStatus"), WEB_TOOLS, TASK_TOOLS));
        definition.prompt = "You are the Explore subagent. Work strictly read-only. Search the codebase "
            + "thoroughly using Read, Glob, Grep, Tree, Stat, LS and GitStatus. Never create, edit, delete, "
            + "move, copy or install anything. Adapt depth to the requested quick/medium/very thorough level "
            + "and return a concise evidence-based report.";
        return definition;
    }

    private static AgentDefinition plan() {
        AgentDefinition definition = base("Plan",
            "Read-only software architecture and implementation planning agent.");
        definition.permissionMode = "plan";
        definition.tools.addAll(tools(WORKSPACE_TOOLS, Arrays.asList("GitStatus"), WEB_TOOLS, TASK_TOOLS));
        definition.prompt = "You are the Plan subagent. Work strictly read-only. Understand requirements, "
            + "explore existing architecture and conventions, identify affected files and risks, then return "
            + "a concrete implementation plan with verification steps. Do not modify project or system state.";
        return definition;
    }

    private static AgentDefinition verification() {
        AgentDefinition definition = base("verification",
            "Verification-only agent for checking implementation correctness.");
        // 默认后台跑：验证通常较慢，而调用方往往想同时继续做别的。
        definition.background = true;
        // Bash 是必需的（要跑测试），位置在 WebFetch 之后、任务表之前 ——
        // 见 WORKSPACE_TOOLS 的说明。仍然不允许改文件：由 permissionMode 与
        // 提示词里那句「Do not edit project files」共同约束。
        definition.tools.addAll(tools(WORKSPACE_TOOLS, Arrays.asList("GitStatus"), WEB_TOOLS,
            Arrays.asList("Bash"), TASK_TOOLS));
        definition.prompt = "You are a verification-only ZhiCode subagent. Inspect changes and run relevant "
            + "tests/checks. Do not edit project files. End with exactly one of VERDICT: PASS, VERDICT: FAIL, "
            + "or VERDICT: PARTIAL, followed by concise evidence.";
        return definition;
    }

    private static AgentDefinition guide() {
        AgentDefinition definition = base("zhi-code-guide",
            "Helper agent for questions about ZhiCode behavior and configuration.");
        definition.model = "haiku";
        // dontAsk = 只读、且不问用户 —— 回答关于本应用的问题不该弹确认框。
        definition.permissionMode = "dontAsk";
        definition.tools.addAll(tools(WORKSPACE_TOOLS, WEB_TOOLS));
        definition.prompt = "You are a ZhiCode guide subagent. Answer questions using available local "
            + "project and bundled documentation evidence. Prefer exact configuration paths, command names "
            + "and actionable examples. Do not modify files.";
        return definition;
    }

    private static AgentDefinition base(String name, String description) {
        AgentDefinition definition = new AgentDefinition();
        definition.name = name;
        definition.description = description;
        definition.source = SOURCE_BUILT_IN;
        return definition;
    }
}

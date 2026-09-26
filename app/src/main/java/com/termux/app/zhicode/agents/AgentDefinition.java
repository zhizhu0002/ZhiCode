package com.termux.app.zhicode.agents;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一个子代理（subagent）的定义 —— 从内置表、项目目录或用户目录里读来。
 *
 * <h3>三个集合的语义</h3>
 * {@link #tools} 与 {@link #disallowedTools} 最后会**求差**：先按 tools 取一组，
 * 再从中去掉 disallowedTools。允许与禁止分开写，是因为来源不同：
 * 「这个代理需要什么」写在代理定义里，「这个环境不许用什么」常常是项目级策略，
 * 让后者去改前者的列表会互相覆盖。
 *
 * <p>{@link #tools} 为空或含 {@code "*"} 都表示「不限制」—— 见
 * {@link #inheritsAllTools()}。这两种写法都出现过：省略不写，或者显式写通配。
 *
 * <p>三个集合都用 {@link LinkedHashSet}：顺序会影响工具清单进入提示词的次序，
 * 而提示词内容的任何变化都会让缓存前缀失效，所以顺序必须稳定。
 */
public final class AgentDefinition {

    public String name = "";
    public String description = "";
    /** 系统提示词。 */
    public String prompt = "";
    /** 模型名，{@code inherit} 表示跟随主会话。 */
    public String model = "inherit";
    public String effort = "";
    /** 权限模式；空串表示跟随主会话。 */
    public String permissionMode = "";
    /** 隔离方式（例如 worktree）；空串表示不隔离。 */
    public String isolation = "";
    /** 界面上的标识色。 */
    public String color = "";
    /** 记忆作用域；空串表示不额外加载记忆。 */
    public String memory = "";
    public boolean background;
    /** 轮数上限；0 表示用引擎的默认值。 */
    public int maxTurns;
    /** 来源标记：{@code builtin} / {@code project} / {@code user} / {@code custom}。 */
    public String source = "custom";
    /** 定义文件本身；内置定义没有文件。 */
    public File sourceFile;
    public final Set<String> tools = new LinkedHashSet<>();
    public final Set<String> disallowedTools = new LinkedHashSet<>();
    public final Set<String> skills = new LinkedHashSet<>();

    /** 没有写 tools，或者写了通配 {@code *}，都表示不限制工具。 */
    public boolean inheritsAllTools() {
        return tools.isEmpty() || tools.contains("*");
    }

    /** 只读视图，供界面展示；要改请改 {@link #tools} 本身。 */
    public Set<String> immutableTools() {
        return Collections.unmodifiableSet(tools);
    }

    /**
     * 深拷一份。
     *
     * <p>必须拷集合而不是共享引用：定义会被不同作用域覆盖后再合并，
     * 共享集合会让「项目级定义」改到「内置定义」。
     * {@link #sourceFile} 是只读的路径对象，直接共享。
     */
    public AgentDefinition copy() {
        AgentDefinition copy = new AgentDefinition();
        copy.name = name;
        copy.description = description;
        copy.prompt = prompt;
        copy.model = model;
        copy.effort = effort;
        copy.permissionMode = permissionMode;
        copy.isolation = isolation;
        copy.color = color;
        copy.memory = memory;
        copy.background = background;
        copy.maxTurns = maxTurns;
        copy.source = source;
        copy.sourceFile = sourceFile;
        copy.tools.addAll(tools);
        copy.disallowedTools.addAll(disallowedTools);
        copy.skills.addAll(skills);
        return copy;
    }
}

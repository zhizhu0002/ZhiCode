package com.termux.app.zhicode.api;

/**
 * 模型目录里的一项：一个要发给服务端的 id，和一个给人看的名字。
 *
 * <h3>为什么两样都要</h3>
 * 服务端的 {@code id} 常常是给人看的名字加一串版本后缀（{@code …-2024-08-06}），
 * 也可能整个就是内部代号。反过来，只存显示名就没法发请求。
 * 用一个对象携带两者，是为了让界面不必自己猜「显示什么、发什么」——
 * 那种猜法在 {@code name} 字段缺失或与 {@code id} 不同时会出错。
 *
 * <h3>不可变</h3>
 * 目录对象会被多个界面线程读取，做成可变对象就需要额外的同步约定。
 *
 * <h3>两个字段都不为 null</h3>
 * {@link #displayName} 在缺失时回落到 {@link #id}：界面上至少还能看出是哪个模型，
 * 而不是一行空白或字面的 {@code "null"}。这个回落写在这里而不是每个调用点，
 * 是因为调用点分散在 Java 引擎侧与 Compose 界面侧，谁漏判都会露出空白项。
 */
public final class ModelDescriptor {

    /** 发给服务端的真实标识，非空。 */
    public final String id;

    /** 界面上显示的文本，非空。缺失时等于 {@link #id}。 */
    public final String displayName;

    public ModelDescriptor(String id, String displayName) {
        this.id = id;
        this.displayName = usableName(displayName) ? displayName.trim() : id;
    }

    /** 显示名是否有内容（只判空，长度截断由调用方负责）。 */
    private static boolean usableName(String displayName) {
        return displayName != null && !displayName.trim().isEmpty();
    }
}

package com.termux.app.zhicode.api;

/**
 * 模型目录里的一项。
 *
 * <p>{@code id} 是发给服务端的真实标识；{@code displayName} 只用于界面显示。
 * 两者用一个对象携带，是为了让界面不必自己猜测「显示什么、发什么」——
 * 那种猜法在遇到 {@code name} 字段缺失或与 {@code id} 不同时会出错。
 *
 * <p>不可变：目录对象会被多个界面线程读取，做成可变对象就需要额外的同步约定。
 */
public final class ModelDescriptor {

    public final String id;
    public final String displayName;

    public ModelDescriptor(String id, String displayName) {
        this.id = id;
        // 显示名缺失时回落到 id：界面上至少还能看出是哪个模型，
        // 而不是一行空白或 "null"。
        this.displayName = displayName == null || displayName.trim().isEmpty() ? id : displayName.trim();
    }
}

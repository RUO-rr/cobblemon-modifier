package com.cobblemon.modifier.model;

/**
 * 一个招式的当前数值（不可变值对象）。
 *
 * <p>数值都以**原始字面量字符串**保存：招式脚本里 {@code accuracy} 既可能是
 * {@code 100}，也可能是 {@code true}（必中），保留原文可以原样写回、不改变语义。
 */
public class MoveData {

    private final String id;
    private final String name;
    private final String type;
    private final String basePower;
    private final String accuracy;
    private final String pp;
    private final String priority;
    private final boolean overridden;
    private final String sourceLabel;

    public MoveData(String id, String name, String type, String basePower, String accuracy,
                    String pp, String priority, boolean overridden, String sourceLabel) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.basePower = basePower;
        this.accuracy = accuracy;
        this.pp = pp;
        this.priority = priority;
        this.overridden = overridden;
        this.sourceLabel = sourceLabel;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String basePower() {
        return basePower;
    }

    public String accuracy() {
        return accuracy;
    }

    public String pp() {
        return pp;
    }

    public String priority() {
        return priority;
    }

    /** 是否已经有我们的覆盖文件（保存过 / 还没还原）。 */
    public boolean overridden() {
        return overridden;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    /** 去掉引号的属性名，便于界面直接显示（{@code "Fire"} → {@code Fire}）。 */
    public String displayType() {
        return unquote(type);
    }

    /** 去掉引号的招式英文名。 */
    public String displayName() {
        return unquote(name);
    }

    private static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2
            && (value.charAt(0) == '"' || value.charAt(0) == '\'')
            && value.charAt(value.length() - 1) == value.charAt(0)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

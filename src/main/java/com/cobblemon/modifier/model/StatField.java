package com.cobblemon.modifier.model;

import java.util.Objects;

/**
 * UI 字段定义 —— 替代原来分散的 FIELD_NAMES + FIELD_LABELS + convertFieldValue。
 *
 * 一个 StatField 描述 PluginPanel 中的一个输入行：
 * - key: 字段的编程键名（如 "hp_base"），用于 Map 存取
 * - label: 用户看到的标签文字（如 "基础形态 - HP"）
 * - valueType: 值的 Java 类型（Integer.class / String.class / Float.class）
 */
public final class StatField {

    private final String key;
    private final String label;
    private final Class<?> valueType;

    public StatField(String key, String label, Class<?> valueType) {
        this.key = Objects.requireNonNull(key);
        this.label = Objects.requireNonNull(label);
        this.valueType = Objects.requireNonNull(valueType);
    }

    /**
     * 将字符串输入值转换为正确的类型。
     */
    public Object convertValue(String input) {
        String trimmed = input.trim();
        if (valueType == Integer.class) {
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (valueType == Float.class) {
            try {
                return Float.parseFloat(trimmed);
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }
        return trimmed; // String 或其他
    }

    public String key() { return key; }
    public String label() { return label; }
    public Class<?> valueType() { return valueType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatField that)) return false;
        return key.equals(that.key) && label.equals(that.label) && valueType.equals(that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, label, valueType);
    }

    @Override
    public String toString() {
        return "StatField{key='" + key + "', label='" + label + "'}";
    }

    // ---- 工厂方法（便捷创建） ----

    public static StatField ofInt(String key, String label) {
        return new StatField(key, label, Integer.class);
    }

    public static StatField ofString(String key, String label) {
        return new StatField(key, label, String.class);
    }

    public static StatField ofFloat(String key, String label) {
        return new StatField(key, label, Float.class);
    }
}

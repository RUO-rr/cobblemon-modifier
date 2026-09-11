package com.cobblemon.modifier.ui;

import com.cobblemon.modifier.core.JsonModifier;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件面板 —— 根据插件动态构建字段编辑器的数据模型（纯 POJO，不含 UI）。
 *
 * <p>Phase 3 起由 Minecraft Screen 消费 {@link #getEditors()}，
 * 每个 {@link FieldEditor} 对应界面上的一个输入框。
 */
public class PluginPanel {

    private final List<FieldEditor> editors = new ArrayList<>();

    public PluginPanel() {
    }

    /**
     * 解析 JSON 并重建字段编辑器列表（纯数据操作）。
     */
    public void renderPluginPanel(JsonModifier plugin, JsonObject jsonObject) {
        editors.clear();

        Map<String, Object> currentValues = plugin.parseJson(jsonObject);

        for (String fieldName : plugin.getFieldNames()) {
            Object raw = currentValues.get(fieldName);
            String rawValue = raw != null ? String.valueOf(raw) : "";
            String label = plugin.getFieldLabel(fieldName);
            Class<?> valueType = inferType(plugin, fieldName);
            List<String> choices = plugin.getFieldChoices(fieldName);

            editors.add(new FieldEditor(fieldName, label, valueType, rawValue, choices));
        }
    }

    /**
     * 清空当前字段（切换插件 / 目录时调用）。
     */
    public void clearEditors() {
        editors.clear();
    }

    /**
     * 收集所有字段的新值。
     */
    public Map<String, Object> getPluginNewValues(JsonModifier plugin) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldEditor editor : editors) {
            result.put(editor.key, plugin.convertFieldValue(editor.key, editor.getValue()));
        }
        return result;
    }

    /**
     * 获取当前字段编辑器列表。
     */
    public List<FieldEditor> getEditors() {
        return editors;
    }

    /**
     * 单个字段编辑器 —— 封装 key + label + valueType + currentValue。
     */
    public static class FieldEditor {
        public final String key;
        public final String labelText;
        public final Class<?> valueType;
        public final List<String> choices;
        private String currentValue;

        public FieldEditor(String key, String labelText, Class<?> valueType, String currentValue) {
            this(key, labelText, valueType, currentValue, List.of());
        }

        public FieldEditor(String key, String labelText, Class<?> valueType, String currentValue,
                           List<String> choices) {
            this.key = key;
            this.labelText = labelText;
            this.valueType = valueType;
            this.currentValue = currentValue;
            this.choices = choices == null ? List.of() : List.copyOf(choices);
        }

        public String getValue() {
            return currentValue != null ? currentValue.trim() : "";
        }

        public void setValue(String value) {
            this.currentValue = value;
        }
    }

    /**
     * 推断字段值类型 —— 通过尝试 convertFieldValue 来判断。
     */
    private static Class<?> inferType(JsonModifier plugin, String fieldName) {
        Object converted = plugin.convertFieldValue(fieldName, "0");
        if (converted instanceof Integer) {
            return Integer.class;
        }
        if (converted instanceof Float) {
            return Float.class;
        }
        return String.class;
    }
}

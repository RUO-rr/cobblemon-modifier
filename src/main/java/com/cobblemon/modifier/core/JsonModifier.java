package com.cobblemon.modifier.core;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public interface JsonModifier {
    // 插件名称
    String getPluginName();

    // 目标 JSON 路径（必须实现）
    List<String> getTargetJsonPaths();

    // 字段名列表
    List<String> getFieldNames();

    // 字段显示标签
    String getFieldLabel(String fieldName);

    /**
     * 字段的可选值；返回非空列表时界面渲染为下拉框而不是输入框。
     */
    default List<String> getFieldChoices(String fieldName) {
        return List.of();
    }

    // 解析 JSON，获取原始值
    Map<String, Object> parseJson(JsonObject jsonObject);

    // 修改 JSON，写入新值
    JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues);

    // 字段值转换（字符串→对应类型，解决 PluginPanel 调用报错）
    Object convertFieldValue(String fieldName, String inputValue);
    
    /**
     * 该插件是否需要自定义保存流程（默认 false，走 ModifyService 单文件写入）。
     */
    default boolean usesCustomSave() {
        return false;
    }

    // 新增：检查 JSON 是否包含有效的种族值字段（默认实现返回 true，可选实现）
    default boolean hasValidStatsFields(JsonObject jsonObject) {
        return true;
    }
}

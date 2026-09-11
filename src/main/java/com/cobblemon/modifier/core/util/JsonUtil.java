package com.cobblemon.modifier.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * JSON 操作公共工具方法。
 *
 * <p>从 BaseStatsModifier 和 AbilityModifier 中提取的重复代码。
 * 所有方法均为纯函数，无副作用。</p>
 */
public final class JsonUtil {

    private JsonUtil() {
        // 工具类禁止实例化
    }

    /**
     * 从 JsonArray 中按 name 字段查找表单。
     */
    public static JsonObject findFormByName(JsonArray forms, String name) {
        for (JsonElement elem : forms) {
            if (elem.isJsonObject()) {
                JsonObject f = elem.getAsJsonObject();
                if (name.equals(getString(f, "name"))) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * 安全地从 JsonObject 读取字符串值（不存在或 null 时返回 null）。
     */
    public static String getString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull()
            ? json.get(key).getAsString() : null;
    }
}

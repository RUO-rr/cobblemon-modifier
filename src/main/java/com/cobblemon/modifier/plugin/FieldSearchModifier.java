package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段查找 —— 按图鉴编号 / 名字 / 特性搜索宝可梦。
 *
 * <p>匹配规则：
 * <ul>
 *     <li>图鉴编号：{@code nationalPokedexNumber} 整数精确匹配</li>
 *     <li>名字：{@code name} 英文包含匹配，忽略大小写与符号（Nidoran-M 可用 nidoranm 命中）</li>
 *     <li>特性：{@code abilities} 英文 ID 包含匹配，忽略大小写与符号，自动去掉隐藏特性前缀 {@code h:}</li>
 * </ul>
 *
 * <p>该类仍实现 {@link JsonModifier} 以便被 ServiceLoader 发现，
 * 但不会出现在修改插件下拉框中，只由主界面的字段查找按钮使用。
 */
public class FieldSearchModifier implements JsonModifier {

    public static final String PLUGIN_NAME = "字段查找";

    /** 可搜索的字段。 */
    public enum SearchField {
        POKEDEX_NUMBER("图鉴编号"),
        NAME("名字"),
        ABILITY("特性");

        private final String displayName;

        SearchField(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public static SearchField fromDisplayName(String name) {
            for (SearchField field : values()) {
                if (field.displayName.equals(name)) {
                    return field;
                }
            }
            return POKEDEX_NUMBER;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private SearchField searchField = SearchField.POKEDEX_NUMBER;
    private String targetValue = "";

    public FieldSearchModifier() {
    }

    public void setSearchField(SearchField searchField) {
        this.searchField = searchField != null ? searchField : SearchField.POKEDEX_NUMBER;
    }

    public SearchField getSearchField() {
        return searchField;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue != null ? targetValue.trim() : "";
    }

    public String getTargetValue() {
        return targetValue;
    }

    /**
     * 核心匹配方法：判断一份宝可梦 JSON 是否符合当前搜索条件。
     */
    public boolean matchesSearchCriteria(JsonObject jsonObject) {
        if (jsonObject == null || targetValue.isEmpty()) {
            return false;
        }
        switch (searchField) {
            case POKEDEX_NUMBER:
                return matchesPokedexNumber(jsonObject, targetValue);
            case NAME:
                return matchesName(jsonObject, targetValue);
            case ABILITY:
                return findMatchedAbility(jsonObject) != null;
            default:
                return false;
        }
    }

    private static boolean matchesPokedexNumber(JsonObject jsonObject, String target) {
        if (!jsonObject.has("nationalPokedexNumber")
            || !jsonObject.get("nationalPokedexNumber").isJsonPrimitive()) {
            return false;
        }
        try {
            return jsonObject.get("nationalPokedexNumber").getAsInt() == Integer.parseInt(target.trim());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean matchesName(JsonObject jsonObject, String target) {
        String normalizedTarget = normalize(target);
        if (normalizedTarget.isEmpty()) {
            return false;
        }
        String name = getString(jsonObject, "name");
        return name != null && normalize(name).contains(normalizedTarget);
    }

    /**
     * 返回命中的特性 ID（已去掉 h: 前缀）；未命中返回 null。
     */
    public String findMatchedAbility(JsonObject jsonObject) {
        if (jsonObject == null || targetValue.isEmpty()) {
            return null;
        }
        String normalizedTarget = normalize(targetValue);
        if (normalizedTarget.isEmpty()) {
            return null;
        }
        String matched = findAbilityIn(jsonObject, normalizedTarget);
        if (matched != null) {
            return matched;
        }
        if (jsonObject.has("forms") && jsonObject.get("forms").isJsonArray()) {
            for (JsonElement element : jsonObject.getAsJsonArray("forms")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                matched = findAbilityIn(element.getAsJsonObject(), normalizedTarget);
                if (matched != null) {
                    return matched;
                }
            }
        }
        return null;
    }

    private static String findAbilityIn(JsonObject jsonObject, String normalizedTarget) {
        if (!jsonObject.has("abilities") || !jsonObject.get("abilities").isJsonArray()) {
            return null;
        }
        JsonArray abilities = jsonObject.getAsJsonArray("abilities");
        for (JsonElement element : abilities) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String ability = element.getAsString();
            if (ability.startsWith("h:")) {
                ability = ability.substring(2);
            }
            if (normalize(ability).contains(normalizedTarget)) {
                return ability;
            }
        }
        return null;
    }

    /**
     * 归一化：只保留字母和数字并转小写，便于 nidoranm 命中 Nidoran-M。
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private static String getString(JsonObject jsonObject, String key) {
        if (!jsonObject.has(key) || !jsonObject.get(key).isJsonPrimitive()) {
            return null;
        }
        return jsonObject.get(key).getAsString();
    }

    // ---- JsonModifier 接口（保留 ServiceLoader 兼容，不再作为下拉框编辑器） ----

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public List<String> getTargetJsonPaths() {
        return List.of("data/cobblemon/species");
    }

    @Override
    public List<String> getFieldNames() {
        return List.of("search_field", "search_value");
    }

    @Override
    public String getFieldLabel(String fieldName) {
        if ("search_field".equals(fieldName)) {
            return "搜索字段";
        }
        if ("search_value".equals(fieldName)) {
            return "搜索值";
        }
        return fieldName;
    }

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("search_field", searchField.displayName());
        values.put("search_value", targetValue);
        return values;
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        return originalJson;
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        return inputValue != null ? inputValue.trim() : "";
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        return jsonObject != null && (jsonObject.has("name") || jsonObject.has("nationalPokedexNumber"));
    }
}

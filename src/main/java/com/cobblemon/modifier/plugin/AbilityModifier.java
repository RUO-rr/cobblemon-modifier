package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.core.util.NameMappingUtil;
import com.cobblemon.modifier.model.FormInfo;
import com.cobblemon.modifier.model.StatField;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * 宝可梦特性修改插件。
 *
 * Phase 2 改造：用 FormInfo + StatField 替代重复内部类和分散的字段定义。
 */
public class AbilityModifier implements JsonModifier {

    private static final String PLUGIN_NAME = "宝可梦特性修改";
    private static final String ABILITY_MAPPING_FILE = "ability_name_mapping.json";
    private static final String BASE_SUFFIX = "_base";

    // 当前展示的数据（parseJson 时填充）
    private volatile List<StatField> currentFields = List.of();
    private volatile List<FormInfo> currentForms = List.of();
    private volatile Map<String, String> currentAbilityKeys = Map.of(); // fieldKey → rawAbilityName

    // ---- JsonModifier 接口 ----

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
        return currentFields.stream().map(StatField::key).toList();
    }

    @Override
    public String getFieldLabel(String fieldName) {
        String rawKey = currentAbilityKeys.get(fieldName);
        if (rawKey != null) {
            String chinese = lookupChineseName(rawKey);
            String formLabel = getFormLabel(fieldName);
            if (!formLabel.isEmpty()) {
                return formLabel + " - " + chinese;
            }
            return chinese;
        }
        // fallback: use StatField label
        return currentFields.stream()
            .filter(f -> f.key().equals(fieldName))
            .findFirst()
            .map(StatField::label)
            .orElse(formatFallbackLabel(fieldName));
    }

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        // 1. 提取形态
        List<FormInfo> forms = jsonObject.has("forms")
            ? FormInfo.extractFromJson(jsonObject.getAsJsonArray("forms"))
            : List.of();

        // 2. 读取所有特性值
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> abilityKeys = new LinkedHashMap<>();

        readAbilities(jsonObject, BASE_SUFFIX, values, abilityKeys);

        if (!forms.isEmpty() && jsonObject.has("forms")) {
            JsonArray formsArray = jsonObject.getAsJsonArray("forms");
            for (FormInfo form : forms) {
                JsonObject formJson = JsonUtil.findFormByName(formsArray, form.name());
                if (formJson != null) {
                    readAbilities(formJson, form.fieldSuffix(), values, abilityKeys);
                }
            }
        }

        // 3. 构建字段定义
        this.currentFields = buildAbilityFields(forms, values.keySet());
        this.currentForms = forms;
        this.currentAbilityKeys = abilityKeys;

        return values;
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        JsonObject modified = originalJson.deepCopy();
        writeAbilities(modified, BASE_SUFFIX, newValues);

        if (modified.has("forms") && !currentForms.isEmpty()) {
            JsonArray formsArray = modified.getAsJsonArray("forms");
            for (int i = 0; i < formsArray.size(); i++) {
                JsonElement elem = formsArray.get(i);
                if (!elem.isJsonObject()) continue;

                JsonObject form = elem.getAsJsonObject();
                String name = JsonUtil.getString(form, "name");
                if (name == null) continue;

                for (FormInfo fi : currentForms) {
                    if (fi.name().equals(name)) {
                        writeAbilities(form, fi.fieldSuffix(), newValues);
                        formsArray.set(i, form);
                        break;
                    }
                }
            }
        }
        return modified;
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        return inputValue.trim();
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        if (jsonObject == null) {
            return false;
        }
        if (jsonObject.has("abilities")) {
            return true;
        }
        // 只带 forms 的覆盖文件（魔改 Mega 石）：特性写在形态里，同样可编辑
        return hasFormAbilities(jsonObject);
    }

    private static boolean hasFormAbilities(JsonObject jsonObject) {
        if (!jsonObject.has("forms") || !jsonObject.get("forms").isJsonArray()) {
            return false;
        }
        for (JsonElement element : jsonObject.getAsJsonArray("forms")) {
            if (element.isJsonObject() && element.getAsJsonObject().has("abilities")) {
                return true;
            }
        }
        return false;
    }

    // ================================================================
    // 内部辅助
    // ================================================================

    private void readAbilities(JsonObject json, String suffix,
                                Map<String, Object> values, Map<String, String> keys) {
        if (!json.has("abilities")) return;

        JsonArray abilities = json.getAsJsonArray("abilities");
        int slotIndex = 0;

        for (JsonElement elem : abilities) {
            String abilityStr = elem.getAsString();
            if (abilityStr.startsWith("h:")) {
                String key = "ability_hidden" + suffix;
                String raw = abilityStr.substring(2);
                values.put(key, raw);
                keys.put(key, raw);
            } else {
                slotIndex++;
                String key = "ability_slot_" + slotIndex + suffix;
                values.put(key, abilityStr);
                keys.put(key, abilityStr);
            }
        }
    }

    private void writeAbilities(JsonObject json, String suffix, Map<String, Object> newValues) {
        // 该形态原本没有 abilities 字段（表示继承本体），且界面上也没有它的输入框时，
        // 必须保持原样。旧实现会无条件写入，结果是给形态塞进一个空数组，
        // 让这个形态的特性凭空消失（例如索罗亚克改特性会连累洗翠形态）。
        if (!json.has("abilities") && !hasAbilityKeys(newValues, suffix)) {
            return;
        }

        JsonArray abilities = new JsonArray();

        int slotIndex = 1;
        while (true) {
            String key = "ability_slot_" + slotIndex + suffix;
            if (newValues.containsKey(key)) {
                String value = (String) newValues.get(key);
                if (value != null && !value.trim().isEmpty()) {
                    abilities.add(value);
                }
                slotIndex++;
            } else {
                break;
            }
        }

        String hiddenKey = "ability_hidden" + suffix;
        if (newValues.containsKey(hiddenKey)) {
            String value = (String) newValues.get(hiddenKey);
            if (value != null && !value.trim().isEmpty()) {
                abilities.add("h:" + value);
            }
        }

        json.add("abilities", abilities);
    }

    /** 判断 newValues 里是否存在属于指定形态后缀的特性字段。 */
    private static boolean hasAbilityKeys(Map<String, Object> newValues, String suffix) {
        if (newValues == null || suffix == null || suffix.isEmpty()) {
            return false;
        }
        for (String key : newValues.keySet()) {
            if (key.startsWith("ability_") && key.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static List<StatField> buildAbilityFields(List<FormInfo> forms, Set<String> fieldKeys) {
        List<StatField> fields = new ArrayList<>();

        for (String key : fieldKeys) {
            String label = fieldKeysToLabel(key, forms);
            fields.add(StatField.ofString(key, label));
        }
        return fields;
    }

    private static String fieldKeysToLabel(String key, List<FormInfo> forms) {
        String suffix = detectSuffix(key, forms);
        String formLabel = suffixToFormLabel(suffix, forms);

        if (key.contains("hidden")) {
            return formLabel.isEmpty() ? "隐藏特性" : formLabel + " - 隐藏特性";
        }
        if (key.contains("slot_1")) {
            return formLabel.isEmpty() ? "特性 1" : formLabel + " - 特性 1";
        }
        if (key.contains("slot_2")) {
            return formLabel.isEmpty() ? "特性 2" : formLabel + " - 特性 2";
        }
        return key;
    }

    private static String detectSuffix(String key, List<FormInfo> forms) {
        if (key.endsWith(BASE_SUFFIX)) return BASE_SUFFIX;
        for (FormInfo form : forms) {
            if (key.endsWith(form.fieldSuffix())) return form.fieldSuffix();
        }
        return "";
    }

    private static String suffixToFormLabel(String suffix, List<FormInfo> forms) {
        if (BASE_SUFFIX.equals(suffix)) return "基础形态";
        for (FormInfo form : forms) {
            if (form.fieldSuffix().equals(suffix)) return form.displayName();
        }
        return "";
    }

    private String getFormLabel(String fieldName) {
        String suffix = detectSuffix(fieldName, currentForms);
        return suffixToFormLabel(suffix, currentForms);
    }

    private String lookupChineseName(String rawKey) {
        Map<String, String> chiMap = NameMappingUtil.getEnglishToChineseMap(ABILITY_MAPPING_FILE);
        return chiMap.getOrDefault(rawKey, rawKey);
    }

    private String formatFallbackLabel(String fieldName) {
        String formLabel = getFormLabel(fieldName);
        if (fieldName.contains("hidden")) {
            return formLabel.isEmpty() ? "隐藏特性" : formLabel + " - 隐藏特性";
        }
        if (fieldName.contains("slot_1")) {
            return formLabel.isEmpty() ? "特性 1" : formLabel + " - 特性 1";
        }
        if (fieldName.contains("slot_2")) {
            return formLabel.isEmpty() ? "特性 2" : formLabel + " - 特性 2";
        }
        return fieldName;
    }

}

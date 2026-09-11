package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.model.FormInfo;
import com.cobblemon.modifier.model.PokemonStats;
import com.cobblemon.modifier.model.StatField;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * 种族值修改插件。
 *
 * Phase 2 改造：用 PokemonStats + FormInfo + StatField 替代裸 Map 和重复内部类。
 */
public class BaseStatsModifier implements JsonModifier {

    private static final String PLUGIN_NAME = "种族值修改";
    private static final String BASE_SUFFIX = "_base";

    // 当前展示的数据（在 parseJson 时填充，供 getFieldNames/getFieldLabel 使用）
    private volatile List<StatField> currentFields = List.of();
    private volatile List<FormInfo> currentForms = List.of();

    // ---- JsonModifier 接口实现 ----

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
        return currentFields.stream()
            .filter(f -> f.key().equals(fieldName))
            .findFirst()
            .map(StatField::label)
            .orElse(fieldName);
    }

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        // species_additions 这类覆盖文件没有 baseStats / forms，
        // 种族值插件对它没有意义：显示为空，保存按钮自然保持不可点，
        // 免得把 0 或误填的值写进覆盖文件、反过来顶掉物种本体的种族值。
        if (jsonObject == null || (!jsonObject.has("baseStats") && !jsonObject.has("forms"))) {
            this.currentFields = List.of();
            this.currentForms = List.of();
            return Map.of();
        }

        // 1. 提取形态信息（纯函数）
        List<FormInfo> forms = jsonObject.has("forms")
            ? FormInfo.extractFromJson(jsonObject.getAsJsonArray("forms"))
            : List.of();

        // 2. 构建字段定义（纯函数，基于 forms 列表）
        List<StatField> fields = buildFields(forms);

        // 3. 读取所有字段值
        Map<String, Object> values = readAllValues(jsonObject, forms);

        // 4. 缓存字段和形态信息（供 getFieldNames/getFieldLabel 后续调用）
        this.currentFields = fields;
        this.currentForms = forms;

        return values;
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        JsonObject modified = originalJson.deepCopy();

        // 基础形态：原本没有 baseStats 时保持原样，绝不凭空创建
        JsonObject originalBase = asObject(originalJson, "baseStats");
        if (originalBase != null) {
            collectStats(newValues, BASE_SUFFIX).writeToJson(modified.getAsJsonObject("baseStats"));
        }

        JsonArray formsArray = asArray(modified, "forms");
        if (formsArray == null) {
            return modified;
        }

        // forms 的字段后缀由 parseJson 缓存；缓存缺失时按同一规则现算，避免写错字段
        List<FormInfo> forms = currentForms.isEmpty()
            ? FormInfo.extractFromJson(formsArray)
            : currentForms;
        // 没有 baseStats 的形态表示"继承本体"，界面上显示的就是本体值
        PokemonStats inherited = PokemonStats.fromJson(originalBase);

        for (int i = 0; i < formsArray.size(); i++) {
            JsonElement formElem = formsArray.get(i);
            if (!formElem.isJsonObject()) continue;

            JsonObject form = formElem.getAsJsonObject();
            String formName = JsonUtil.getString(form, "name");
            if (formName == null) continue;

            FormInfo formInfo = findForm(forms, formName);
            if (formInfo == null) continue;

            PokemonStats target = collectStats(newValues, formInfo.fieldSuffix());
            if (asObject(form, "baseStats") == null) {
                // 该形态原本继承本体：只有在用户确实改动了某一项时才落地，
                // 否则必须保持继承关系——旧实现会写入一串 0，让对战里的形态变成废人。
                if (target.equals(inherited)) continue;
                form.add("baseStats", new JsonObject());
            }
            target.writeToJson(form.getAsJsonObject("baseStats"));
        }

        return modified;
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        try {
            return Integer.parseInt(inputValue.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        if (jsonObject.has("baseStats")) {
            PokemonStats stats = PokemonStats.fromJson(jsonObject.getAsJsonObject("baseStats"));
            if (stats.hasAnyPositiveValue()) return true;
        }
        if (jsonObject.has("forms")) {
            JsonArray forms = jsonObject.getAsJsonArray("forms");
            for (JsonElement elem : forms) {
                if (elem.isJsonObject()) {
                    JsonObject f = elem.getAsJsonObject();
                    if (f.has("baseStats")) {
                        PokemonStats fs = PokemonStats.fromJson(f.getAsJsonObject("baseStats"));
                        if (fs.hasAnyPositiveValue()) return true;
                    }
                }
            }
        }
        return false;
    }

    // ================================================================
    // 内部辅助（纯函数）
    // ================================================================

    /**
     * 构建字段定义列表 —— 纯函数，不修改任何状态。
     */
    private static List<StatField> buildFields(List<FormInfo> forms) {
        List<StatField> fields = new ArrayList<>();

        // 基础形态的 6 项种族值
        for (String statKey : PokemonStats.FIELD_NAMES) {
            fields.add(StatField.ofInt(
                statKey + BASE_SUFFIX,
                "基础形态 - " + statLabel(statKey)));
        }

        // 各形态的 6 项种族值
        for (FormInfo form : forms) {
            for (String statKey : PokemonStats.FIELD_NAMES) {
                fields.add(StatField.ofInt(
                    statKey + form.fieldSuffix(),
                    form.displayName() + " - " + statLabel(statKey)));
            }
        }
        return fields;
    }

    /**
     * 读取所有字段值 —— 纯函数。
     */
    private static Map<String, Object> readAllValues(JsonObject json, List<FormInfo> forms) {
        Map<String, Object> values = new LinkedHashMap<>();

        // 基础种族值
        JsonObject baseStats = json.has("baseStats")
            ? json.getAsJsonObject("baseStats") : new JsonObject();
        PokemonStats base = PokemonStats.fromJson(baseStats);
        for (Map.Entry<String, Integer> e : base.toFieldMap(BASE_SUFFIX).entrySet()) {
            values.put(e.getKey(), e.getValue());
        }

        // 各形态种族值
        if (json.has("forms")) {
            JsonArray formsArray = json.getAsJsonArray("forms");
            for (FormInfo form : forms) {
                JsonObject formJson = JsonUtil.findFormByName(formsArray, form.name());
                // 形态没有自己的 baseStats 时表示继承本体，界面上应显示本体的值，
                // 而不是显示 0（旧实现显示 0，用户直接保存就会把形态写成全 0）。
                JsonObject formBaseStats = (formJson != null && formJson.has("baseStats")
                    && formJson.get("baseStats").isJsonObject())
                    ? formJson.getAsJsonObject("baseStats")
                    : baseStats;
                PokemonStats formStats = PokemonStats.fromJson(formBaseStats);
                for (Map.Entry<String, Integer> e : formStats.toFieldMap(form.fieldSuffix()).entrySet()) {
                    values.put(e.getKey(), e.getValue());
                }
            }
        }

        return values;
    }

    /**
     * 从 newValues Map 中收集指定后缀的统计值。
     */
    private static PokemonStats collectStats(Map<String, Object> newValues, String suffix) {
        int hp = getIntValue(newValues, PokemonStats.HP + suffix);
        int atk = getIntValue(newValues, PokemonStats.ATTACK + suffix);
        int def = getIntValue(newValues, PokemonStats.DEFENCE + suffix);
        int spa = getIntValue(newValues, PokemonStats.SPECIAL_ATTACK + suffix);
        int spd = getIntValue(newValues, PokemonStats.SPECIAL_DEFENCE + suffix);
        int sp = getIntValue(newValues, PokemonStats.SPEED + suffix);
        return new PokemonStats(hp, atk, def, spa, spd, sp);
    }

    private static int getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    /** 取出 JSON 对象字段，不存在或类型不对时返回 null。 */
    private static JsonObject asObject(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonObject()
            ? json.getAsJsonObject(key) : null;
    }

    /** 取出 JSON 数组字段，不存在或类型不对时返回 null。 */
    private static JsonArray asArray(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonArray()
            ? json.getAsJsonArray(key) : null;
    }

    private static FormInfo findForm(List<FormInfo> forms, String name) {
        for (FormInfo form : forms) {
            if (form.name().equals(name)) {
                return form;
            }
        }
        return null;
    }

    private static String statLabel(String key) {
        return switch (key) {
            case PokemonStats.HP -> "HP";
            case PokemonStats.ATTACK -> "攻击";
            case PokemonStats.DEFENCE -> "防御";
            case PokemonStats.SPECIAL_ATTACK -> "特攻";
            case PokemonStats.SPECIAL_DEFENCE -> "特防";
            case PokemonStats.SPEED -> "速度";
            default -> key;
        };
    }
}

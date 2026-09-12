package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.model.FormInfo;
import com.cobblemon.modifier.model.StatField;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 宝可梦属性修改插件 —— 编辑物种 JSON 里的 {@code primaryType} / {@code secondaryType}。
 *
 * <p>数据格式（实测本体 1025 个物种全部如此，值为**小写**）：
 * <pre>
 * "primaryType": "fire",
 * "secondaryType": "flying"     // 单属性物种没有这个字段
 * </pre>
 *
 * <p>形态各自带自己的属性（喷火龙 Mega-X 是 fire / dragon），所以每个形态单独一组字段；
 * 形态**没有** {@code primaryType} 时（表示继承本体）不生成字段，
 * 避免"编辑本体却把形态写成空属性"这类事故（与种族值/特性插件的处理一致）。
 *
 * <p>界面用下拉框限制取值（18 种属性 + 副属性的"（无副属性）"），
 * 写入 JSON 时统一转回小写；整合包里出现的自定义属性名会原样保留，不会被改掉。
 */
public class TypeModifier implements JsonModifier {

    private static final Logger log = LoggerFactory.getLogger(TypeModifier.class);

    private static final String PLUGIN_NAME = "宝可梦属性修改";
    private static final String BASE_SUFFIX = "_base";
    private static final String PRIMARY_PREFIX = "type_primary";
    private static final String SECONDARY_PREFIX = "type_secondary";

    /** 副属性下拉框里表示"单属性"的选项。 */
    private static final String NO_SECONDARY = "（无副属性）";

    /** 18 种属性（界面显示用大写开头，写入 JSON 时转小写）。 */
    private static final List<String> TYPES = List.of(
        "Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison",
        "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark",
        "Steel", "Fairy");

    private static final Set<String> LOWER_TYPES = new LinkedHashSet<>(
        TYPES.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList());

    private static final List<String> SECONDARY_CHOICES;

    static {
        List<String> withNone = new ArrayList<>();
        withNone.add(NO_SECONDARY);
        withNone.addAll(TYPES);
        SECONDARY_CHOICES = List.copyOf(withNone);
    }

    // ---- 当前展示的数据（parseJson 时填充） ----
    private volatile List<StatField> currentFields = List.of();
    private volatile List<FormInfo> currentForms = List.of();
    private volatile Map<String, List<String>> currentChoices = Map.of();

    // ================================================================
    // JsonModifier 接口
    // ================================================================

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
            .filter(field -> field.key().equals(fieldName))
            .findFirst()
            .map(StatField::label)
            .orElse(fieldName);
    }

    @Override
    public List<String> getFieldChoices(String fieldName) {
        List<String> choices = currentChoices.get(fieldName);
        return choices == null ? PRIMARY_CHOICES : choices;
    }

    private static final List<String> PRIMARY_CHOICES = TYPES;

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        List<FormInfo> forms = jsonObject.has("forms")
            ? FormInfo.extractFromJson(jsonObject.getAsJsonArray("forms"))
            : List.of();

        Map<String, Object> values = new LinkedHashMap<>();
        List<StatField> fields = new ArrayList<>();
        Map<String, List<String>> choices = new LinkedHashMap<>();

        readTypes(jsonObject, BASE_SUFFIX, "基础形态", values, fields, choices);

        if (!forms.isEmpty() && jsonObject.has("forms")) {
            JsonArray formsArray = jsonObject.getAsJsonArray("forms");
            for (FormInfo form : forms) {
                JsonObject formJson = JsonUtil.findFormByName(formsArray, form.name());
                if (formJson != null) {
                    readTypes(formJson, form.fieldSuffix(), form.displayName(), values, fields, choices);
                }
            }
        }

        this.currentFields = List.copyOf(fields);
        this.currentForms = forms;
        this.currentChoices = choices;
        return values;
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        JsonObject modified = originalJson.deepCopy();
        writeTypes(modified, BASE_SUFFIX, newValues);

        if (modified.has("forms") && !currentForms.isEmpty()) {
            JsonArray formsArray = modified.getAsJsonArray("forms");
            for (int index = 0; index < formsArray.size(); index++) {
                JsonElement element = formsArray.get(index);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject form = element.getAsJsonObject();
                String name = JsonUtil.getString(form, "name");
                if (name == null) {
                    continue;
                }
                for (FormInfo info : currentForms) {
                    if (info.name().equals(name)) {
                        writeTypes(form, info.fieldSuffix(), newValues);
                        formsArray.set(index, form);
                        break;
                    }
                }
            }
        }
        return modified;
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        return inputValue == null ? "" : inputValue.trim();
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        return jsonObject != null && jsonObject.has("primaryType");
    }

    // ================================================================
    // 读取
    // ================================================================

    private void readTypes(JsonObject json, String suffix, String formLabel, Map<String, Object> values,
                           List<StatField> fields, Map<String, List<String>> choices) {
        // 没有 primaryType 的对象（例如只覆盖 moves 的 species_additions）不生成字段：
        // 在这里编辑会凭空造出属性，把本体的属性顶掉。
        if (json == null || !json.has("primaryType") || json.get("primaryType").isJsonNull()) {
            return;
        }
        String primary = asDisplayType(JsonUtil.getString(json, "primaryType"));
        String secondary = json.has("secondaryType") && !json.get("secondaryType").isJsonNull()
            ? asDisplayType(json.get("secondaryType").getAsString())
            : NO_SECONDARY;

        String primaryKey = PRIMARY_PREFIX + suffix;
        String secondaryKey = SECONDARY_PREFIX + suffix;
        values.put(primaryKey, primary);
        values.put(secondaryKey, secondary);
        fields.add(StatField.ofString(primaryKey, formLabel + " - 主属性"));
        fields.add(StatField.ofString(secondaryKey, formLabel + " - 副属性"));

        // 整合包里的自定义属性名不在 18 种之内时，把它放进选项，避免下拉框回退成 Normal 把数据改坏
        choices.put(primaryKey, withCurrent(PRIMARY_CHOICES, primary));
        choices.put(secondaryKey, withCurrent(SECONDARY_CHOICES, secondary));
    }

    private static List<String> withCurrent(List<String> base, String current) {
        if (current == null || base.contains(current)) {
            return base;
        }
        List<String> result = new ArrayList<>();
        result.add(current);
        result.addAll(base);
        return List.copyOf(result);
    }

    // ================================================================
    // 写入
    // ================================================================

    private void writeTypes(JsonObject json, String suffix, Map<String, Object> newValues) {
        String primaryKey = PRIMARY_PREFIX + suffix;
        String secondaryKey = SECONDARY_PREFIX + suffix;
        boolean hasPrimary = newValues.containsKey(primaryKey);
        boolean hasSecondary = newValues.containsKey(secondaryKey);
        if (!json.has("primaryType") || json.get("primaryType").isJsonNull()) {
            // 原本没有 primaryType（形态继承本体，或这是只覆盖别的字段的 species_additions）
            // —— 保持原样，绝不凭空造出属性字段，否则会把本体的属性顶掉。
            return;
        }

        String originalPrimary = JsonUtil.getString(json, "primaryType");
        String originalSecondary = json.has("secondaryType") && !json.get("secondaryType").isJsonNull()
            ? json.get("secondaryType").getAsString()
            : null;

        String primary = hasPrimary
            ? toJsonType(String.valueOf(newValues.get(primaryKey)), originalPrimary, false)
            : originalPrimary;
        if (primary == null || primary.isBlank()) {
            throw new IllegalArgumentException(labelFor(suffix) + " 的主属性不能为空");
        }

        String secondary = originalSecondary;
        if (hasSecondary) {
            String raw = String.valueOf(newValues.get(secondaryKey));
            if (raw == null || raw.isBlank() || NO_SECONDARY.equals(raw.trim())) {
                secondary = null;
            } else if (raw.trim().equalsIgnoreCase(primary)) {
                throw new IllegalArgumentException(
                    labelFor(suffix) + " 的主属性与副属性不能相同；"
                        + "只想保留单属性请把副属性设为" + NO_SECONDARY);
            } else {
                secondary = toJsonType(raw, originalSecondary, true);
            }
        }

        json.addProperty("primaryType", primary.toLowerCase(Locale.ROOT));
        if (secondary == null || secondary.isBlank()) {
            json.remove("secondaryType");
        } else {
            json.addProperty("secondaryType", secondary.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 把界面上的属性名转成写进 JSON 的值。
     *
     * @param original 原本的值；整合包自定义属性名（不在 18 种内）允许原样保留
     */
    private static String toJsonType(String raw, String original, boolean secondary) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || NO_SECONDARY.equals(value)) {
            return secondary ? null : "";
        }
        if (LOWER_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (original != null && value.equalsIgnoreCase(original)) {
            log.warn("保留整合包自定义属性名：{}", value);
            return original;
        }
        throw new IllegalArgumentException("未知属性：" + value + "（可用：Normal / Fire / … / Fairy）");
    }

    /** JSON 里的值（小写）→ 下拉框里的显示值。 */
    private static String asDisplayType(String raw) {
        if (raw == null || raw.isBlank()) {
            return NO_SECONDARY;
        }
        String value = raw.trim();
        if (LOWER_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
            String lower = value.toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
        return value;
    }

    /** 字段后缀 → 形态显示名（用于报错信息）。 */
    private String labelFor(String suffix) {
        if (suffix == null || suffix.isBlank() || BASE_SUFFIX.equals(suffix)) {
            return "基础形态";
        }
        for (FormInfo form : currentForms) {
            if (form.fieldSuffix().equals(suffix)) {
                return form.displayName();
            }
        }
        return "该形态";
    }
}

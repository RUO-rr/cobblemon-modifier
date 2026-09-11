package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.service.MoveCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 技能修改插件 —— 编辑物种 JSON 里的 {@code moves} 列表。
 *
 * <p>数据格式（实测本整合包 1394 个物种文件、共 15.7 万条记录）：
 * <pre>
 * "1:blitzstrike"   学到等级 1
 * "tm:aerialace"    TM 技能
 * "egg:..." / "tutor:..." / "legacy:..." / "special:..." / "form_change:..."
 * </pre>
 *
 * <p>界面沿用现有字段编辑器：开头几个空槽用于新增技能，后面按顺序列出已有技能，
 * 清空某一格就等于删掉那条技能。保存前会校验格式与招式 id，
 * 拼错时直接拒绝保存并说明是哪个词写错了（避免"改完进不去世界"）。
 */
public class MoveModifier implements JsonModifier {

    private static final String PLUGIN_NAME = "技能修改";

    /** 已有技能的字段前缀。 */
    private static final String EXISTING_PREFIX = "move_";

    /** 新增技能的空槽前缀。 */
    private static final String NEW_PREFIX = "move_new_";

    /** 空槽数量，放在最前面方便直接填。 */
    private static final int EXTRA_SLOTS = 4;

    /**
     * 合法格式：等级数字或六种来源前缀，加冒号，加招式 id。
     * 前缀取自实测数据里的分布（等级 / tm / egg / tutor / legacy / special / form_change）。
     */
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "^(?:\\d{1,3}|tm|egg|tutor|legacy|special|form_change):[a-z0-9]+$");

    private volatile List<String> currentKeys = List.of();
    private volatile MoveCatalog moveCatalog;

    public MoveModifier() {
    }

    /** 注入已知招式表（DI 装配时调用）；没有也能用，只是不校验招式 id。 */
    public void setMoveCatalog(MoveCatalog moveCatalog) {
        this.moveCatalog = moveCatalog;
    }

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
        return currentKeys;
    }

    @Override
    public String getFieldLabel(String fieldName) {
        if (fieldName.startsWith(NEW_PREFIX)) {
            return "新增技能 " + (indexOf(fieldName, NEW_PREFIX) + 1);
        }
        if (fieldName.startsWith(EXISTING_PREFIX)) {
            return "技能 " + (indexOf(fieldName, EXISTING_PREFIX) + 1);
        }
        return fieldName;
    }

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();

        // 先放空槽：打开界面第一页就能直接填新技能
        for (int i = 0; i < EXTRA_SLOTS; i++) {
            String key = NEW_PREFIX + i;
            keys.add(key);
            values.put(key, "");
        }

        JsonArray moves = (jsonObject != null && jsonObject.has("moves")
            && jsonObject.get("moves").isJsonArray())
            ? jsonObject.getAsJsonArray("moves") : new JsonArray();
        for (int i = 0; i < moves.size(); i++) {
            JsonElement element = moves.get(i);
            String key = EXISTING_PREFIX + i;
            keys.add(key);
            values.put(key, element != null && element.isJsonPrimitive() ? element.getAsString() : "");
        }

        this.currentKeys = List.copyOf(keys);
        return values;
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        JsonObject modified = originalJson.deepCopy();

        List<String> existing = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (String key : currentKeys) {
            String text = textOf(newValues, key);
            if (text.isEmpty()) {
                continue; // 清空 = 删除该条
            }
            String normalized = normalize(text);
            validate(normalized, text);
            if (key.startsWith(NEW_PREFIX)) {
                added.add(normalized);
            } else {
                existing.add(normalized);
            }
        }

        if (existing.isEmpty() && added.isEmpty()) {
            throw new IllegalArgumentException("技能列表不能为空，至少保留一条");
        }

        // 已有技能保持原顺序，新加的追加到末尾
        JsonArray moves = new JsonArray();
        existing.forEach(moves::add);
        added.forEach(moves::add);
        modified.add("moves", moves);
        return modified;
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        return inputValue != null ? inputValue.trim() : "";
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        return jsonObject != null && jsonObject.has("moves");
    }

    // ================================================================
    // 内部辅助
    // ================================================================

    private void validate(String normalized, String rawText) {
        if (!ENTRY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "技能写法不对：" + rawText + "（应形如 1:dragondance 或 tm:dragondance）");
        }
        String moveId = normalized.substring(normalized.indexOf(':') + 1);
        MoveCatalog catalog = this.moveCatalog;
        if (catalog != null && catalog.isReady() && !catalog.knownMoveIds().contains(moveId)) {
            throw new IllegalArgumentException(
                "未知招式 id：" + moveId + "，请检查拼写（多半是拼错或用了中文名）");
        }
    }

    private static String textOf(Map<String, Object> values, String key) {
        Object raw = values == null ? null : values.get(key);
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    /** 统一成小写、去空格、去下划线等分隔符，与数据里的写法一致。 */
    private static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace(" ", "");
        int colon = lower.indexOf(':');
        if (colon < 0) {
            return lower;
        }
        String prefix = lower.substring(0, colon);
        String rest = lower.substring(colon + 1).replaceAll("[^a-z0-9]", "");
        return prefix + ":" + rest;
    }

    private static int indexOf(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}

package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.model.SpawnEntry;
import com.cobblemon.modifier.service.SpawnRateService;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷新率修改插件。
 *
 * <p>界面以物种为单位，提供两个字段：
 * <ul>
 *   <li>稀有等级（bucket）：下拉框，选择后把该宝可梦的全部刷新条目移动到该稀有等级；</li>
 *   <li>桶内权重（weight）：该宝可梦在所属稀有等级内部的相对权重。</li>
 * </ul>
 * 稀有等级本身的占比由全局配置 best-spawner-config.json 决定，见
 * {@code SpawnConfigService} 与界面上的"稀有等级权重"按钮。</p>
 */
public class SpawnRateModifier implements JsonModifier {

    private static final String PLUGIN_NAME = "刷新率修改";

    /** 下拉框中表示"不改动现有稀有等级"的选项。 */
    public static final String KEEP_BUCKET = "保持不变";

    private static final List<String> FIELD_NAMES = List.of("bucket", "weight");
    private static final List<String> BUCKET_CHOICES = List.of(
        KEEP_BUCKET, "common", "uncommon", "rare", "ultra-rare");
    private static final Map<String, String> FIELD_LABELS = Map.of(
        "bucket", "稀有等级（覆盖全部条目）",
        "weight", "桶内权重（同等级内比较）");

    private SpawnRateService spawnRateService;

    public SpawnRateModifier() {
    }

    public void setSpawnRateService(SpawnRateService spawnRateService) {
        this.spawnRateService = spawnRateService;
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
        return FIELD_NAMES;
    }

    @Override
    public String getFieldLabel(String fieldName) {
        return FIELD_LABELS.getOrDefault(fieldName, fieldName);
    }

    @Override
    public List<String> getFieldChoices(String fieldName) {
        return "bucket".equals(fieldName) ? BUCKET_CHOICES : List.of();
    }

    @Override
    public boolean usesCustomSave() {
        return true;
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        return jsonObject != null && jsonObject.has("name");
    }

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        Map<String, Object> values = new HashMap<>();
        values.put("bucket", currentBucket(jsonObject));
        values.put("weight", currentWeight(jsonObject));
        return values;
    }

    /**
     * 所有刷新条目同属一个稀有等级时返回该等级；混合或没有条目时返回"保持不变"。
     */
    private String currentBucket(JsonObject speciesJson) {
        List<SpawnEntry> entries = entriesOf(speciesJson);
        if (entries.isEmpty()) {
            return KEEP_BUCKET;
        }
        String first = entries.get(0).bucket();
        if (first == null || first.isBlank()) {
            return KEEP_BUCKET;
        }
        for (SpawnEntry entry : entries) {
            if (!first.equals(entry.bucket())) {
                return KEEP_BUCKET;
            }
        }
        return first;
    }

    /**
     * 当前权重取该物种所有条目中的最小值；没有刷新条目时返回空字符串。
     */
    private String currentWeight(JsonObject speciesJson) {
        List<SpawnEntry> entries = entriesOf(speciesJson);
        if (entries.isEmpty()) {
            return "";
        }
        float min = Float.MAX_VALUE;
        for (SpawnEntry entry : entries) {
            min = Math.min(min, entry.weight());
        }
        return formatWeight(min);
    }

    private List<SpawnEntry> entriesOf(JsonObject speciesJson) {
        if (spawnRateService == null || speciesJson == null) {
            return List.of();
        }
        return spawnRateService.getEntries(JsonUtil.getString(speciesJson, "name"));
    }

    public static String formatWeight(float weight) {
        if (weight == Math.round(weight)) {
            return String.valueOf((int) weight);
        }
        return String.valueOf(weight);
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        // 刷新率走自定义保存流程（见 MainController.onSave），这里不做修改。
        return originalJson.deepCopy();
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        if (inputValue == null) {
            return null;
        }
        if ("bucket".equals(fieldName)) {
            return inputValue.trim();
        }
        if (!"weight".equals(fieldName)) {
            return inputValue;
        }
        try {
            return Float.parseFloat(inputValue.trim());
        } catch (NumberFormatException e) {
            // 返回原字符串，让保存流程能识别出非法输入
            return inputValue.trim();
        }
    }
}

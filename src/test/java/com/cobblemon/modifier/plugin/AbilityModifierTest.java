package com.cobblemon.modifier.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AbilityModifierTest {

    private AbilityModifier plugin;

    @Before
    public void setUp() {
        plugin = new AbilityModifier();
    }

    @Test
    public void getPluginName_shouldReturnCorrectName() {
        assertEquals("宝可梦特性修改", plugin.getPluginName());
    }

    @Test
    public void getTargetJsonPaths_shouldReturnSpeciesPath() {
        assertEquals(List.of("data/cobblemon/species"), plugin.getTargetJsonPaths());
    }

    @Test
    public void convertFieldValue_shouldReturnTrimmedString() {
        assertEquals("overgrow", plugin.convertFieldValue("any", "  overgrow  "));
    }

    // ---- parseJson: basic abilities ----

    @Test
    public void parseJson_shouldExtractSlotAbilities() {
        JsonObject json = speciesWithAbilities("overgrow", null);

        Map<String, Object> values = plugin.parseJson(json);

        assertEquals("overgrow", values.get("ability_slot_1_base"));
        assertEquals(1, values.size());
    }

    @Test
    public void parseJson_shouldExtractMultipleSlots() {
        JsonObject json = speciesWithAbilities("overgrow", "chlorophyll");

        Map<String, Object> values = plugin.parseJson(json);

        assertEquals("overgrow", values.get("ability_slot_1_base"));
        assertEquals("chlorophyll", values.get("ability_slot_2_base"));
        assertEquals(2, values.size());
    }

    @Test
    public void parseJson_shouldExtractHiddenAbility() {
        JsonObject json = new JsonObject();
        JsonArray abilities = new JsonArray();
        abilities.add("overgrow");
        abilities.add("h:chlorophyll");
        json.add("abilities", abilities);

        Map<String, Object> values = plugin.parseJson(json);

        assertEquals("overgrow", values.get("ability_slot_1_base"));
        assertEquals("chlorophyll", values.get("ability_hidden_base"));
    }

    // ---- parseJson: with forms ----

    @Test
    public void parseJson_shouldExtractFormAbilities() {
        JsonObject json = new JsonObject();
        JsonArray baseAbilities = new JsonArray();
        baseAbilities.add("overgrow");
        json.add("abilities", baseAbilities);

        JsonArray forms = new JsonArray();
        JsonObject megaX = new JsonObject();
        megaX.addProperty("name", "Mega-X");
        JsonArray megaAbilities = new JsonArray();
        megaAbilities.add("blaze");
        megaX.add("abilities", megaAbilities);
        forms.add(megaX);
        json.add("forms", forms);

        Map<String, Object> values = plugin.parseJson(json);

        assertEquals("overgrow", values.get("ability_slot_1_base"));
        assertEquals("blaze", values.get("ability_slot_1_mega_x"));
        assertEquals(2, values.size());
    }

    @Test
    public void getFieldNames_afterParse_shouldIncludeAllFields() {
        JsonObject json = speciesWithAbilities("overgrow", "chlorophyll");
        plugin.parseJson(json);

        List<String> names = plugin.getFieldNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("ability_slot_1_base"));
        assertTrue(names.contains("ability_slot_2_base"));
    }

    // ---- modifyJson ----

    @Test
    public void modifyJson_shouldReplaceAbilityName() {
        JsonObject original = speciesWithAbilities("overgrow", null);
        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("ability_slot_1_base", "blaze");

        JsonObject modified = plugin.modifyJson(original, newValues);
        JsonArray abilities = modified.getAsJsonArray("abilities");
        assertEquals(1, abilities.size());
        assertEquals("blaze", abilities.get(0).getAsString());
    }

    @Test
    public void modifyJson_shouldHandleHiddenAbility() {
        JsonObject original = speciesWithAbilities("overgrow", null);
        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("ability_slot_1_base", "overgrow");
        newValues.put("ability_hidden_base", "chlorophyll");

        JsonObject modified = plugin.modifyJson(original, newValues);
        JsonArray abilities = modified.getAsJsonArray("abilities");
        assertEquals(2, abilities.size());
        assertTrue(abilities.get(1).getAsString().startsWith("h:"));
    }

    @Test
    public void modifyJson_shouldSkipEmptyValues() {
        JsonObject original = speciesWithAbilities("overgrow", null);
        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("ability_slot_1_base", "");  // empty string

        JsonObject modified = plugin.modifyJson(original, newValues);
        JsonArray abilities = modified.getAsJsonArray("abilities");
        assertEquals(0, abilities.size());
    }

    // ---- 没有 abilities 的形态（继承本体）----
    // 回归：旧实现会无条件写回，给这类形态塞一个空数组，导致特性凭空消失

    @Test
    public void modifyJson_shouldNotClearFormThatHasNoAbilities() {
        JsonObject original = new JsonObject();
        JsonArray baseAbilities = new JsonArray();
        baseAbilities.add("illusion");
        original.add("abilities", baseAbilities);

        JsonArray forms = new JsonArray();
        JsonObject hisui = new JsonObject();
        hisui.addProperty("name", "Hisui");
        forms.add(hisui);            // 该形态没有 abilities 字段 = 继承本体
        original.add("forms", forms);

        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("ability_slot_1_base", "blaze");

        JsonObject modified = plugin.modifyJson(original, newValues);

        JsonArray modifiedBase = modified.getAsJsonArray("abilities");
        assertEquals("blaze", modifiedBase.get(0).getAsString());

        JsonObject modifiedForm = modified.getAsJsonArray("forms").get(0).getAsJsonObject();
        assertFalse("继承本体的形态不应被写入 abilities", modifiedForm.has("abilities"));
    }

    @Test
    public void modifyJson_shouldStillUpdateFormThatHasOwnAbilities() {
        JsonObject original = new JsonObject();
        JsonArray baseAbilities = new JsonArray();
        baseAbilities.add("illusion");
        original.add("abilities", baseAbilities);

        JsonArray forms = new JsonArray();
        JsonObject megaX = new JsonObject();
        megaX.addProperty("name", "Mega-X");
        JsonArray megaAbilities = new JsonArray();
        megaAbilities.add("blaze");
        megaX.add("abilities", megaAbilities);
        forms.add(megaX);
        original.add("forms", forms);

        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("ability_slot_1_base", "illusion");
        newValues.put("ability_slot_1_mega_x", "drought");

        JsonObject modified = plugin.modifyJson(original, newValues);

        JsonObject modifiedForm = modified.getAsJsonArray("forms").get(0).getAsJsonObject();
        assertTrue(modifiedForm.has("abilities"));
        assertEquals("drought", modifiedForm.getAsJsonArray("abilities").get(0).getAsString());
    }

    // ---- hasValidStatsFields ----

    @Test
    public void hasValidStatsFields_shouldReturnTrueWhenHasAbilities() {
        JsonObject json = speciesWithAbilities("overgrow", null);
        assertTrue(plugin.hasValidStatsFields(json));
    }

    @Test
    public void hasValidStatsFields_shouldReturnFalseWithoutAbilities() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "noAbility");
        assertFalse(plugin.hasValidStatsFields(json));
    }

    /**
     * 魔改 Mega 石的覆盖文件：顶层只有 target + forms，特性写在形态里
     * （mushiromega 的莱希拉姆 Mega 就是 {@code "abilities": ["overload", "h:overload"]}）。
     */
    @Test
    public void hasValidStatsFields_shouldAcceptFormAbilities() {
        JsonObject json = new JsonObject();
        json.addProperty("target", "cobblemon:reshiram");
        JsonObject form = new JsonObject();
        form.addProperty("name", "Mega");
        JsonArray abilities = new JsonArray();
        abilities.add("overload");
        abilities.add("h:overload");
        form.add("abilities", abilities);
        JsonArray forms = new JsonArray();
        forms.add(form);
        json.add("forms", forms);

        assertTrue(plugin.hasValidStatsFields(json));
    }

    @Test
    public void parseJson_shouldReadFormAbilitiesFromOverlay() {
        JsonObject json = new JsonObject();
        json.addProperty("target", "cobblemon:reshiram");
        JsonObject form = new JsonObject();
        form.addProperty("name", "Mega");
        JsonArray abilities = new JsonArray();
        abilities.add("overload");
        abilities.add("h:overload");
        form.add("abilities", abilities);
        JsonArray forms = new JsonArray();
        forms.add(form);
        json.add("forms", forms);

        Map<String, Object> values = plugin.parseJson(json);

        // 形态没有自己的后缀规则匹配时按索引命名（Mega → _mega_0）
        assertEquals("overload", values.get("ability_slot_1_mega_0"));
        assertEquals("overload", values.get("ability_hidden_mega_0"));
    }

    // ---- helpers ----

    private static JsonObject speciesWithAbilities(String slot1, String slot2) {
        JsonObject json = new JsonObject();
        JsonArray abilities = new JsonArray();
        abilities.add(slot1);
        if (slot2 != null) {
            abilities.add(slot2);
        }
        json.add("abilities", abilities);
        return json;
    }
}

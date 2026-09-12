package com.cobblemon.modifier.plugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 属性修改插件测试。
 *
 * <p>真实数据格式：{@code "primaryType": "fire"} / {@code "secondaryType": "flying"}（小写），
 * 单属性物种没有 {@code secondaryType}；形态各自带属性（喷火龙 Mega-X 是 fire/dragon）。
 */
public class TypeModifierTest {

    private static final String CHARIZARD = """
        {
          "name": "charizard",
          "primaryType": "fire",
          "secondaryType": "flying",
          "baseStats": { "hp": 78, "attack": 84 },
          "abilities": ["blaze", "h:solarpower"],
          "forms": [
            {
              "name": "Mega-X",
              "primaryType": "fire",
              "secondaryType": "dragon",
              "baseStats": { "hp": 78, "attack": 130 }
            },
            {
              "name": "Gmax",
              "baseStats": { "hp": 78, "attack": 84 }
            }
          ]
        }
        """;

    private static final String SINGLE_TYPE = """
        {
          "name": "pikachu",
          "primaryType": "electric",
          "baseStats": { "hp": 35 }
        }
        """;

    /** 只覆盖 moves 的 species_additions（没有属性字段）。 */
    private static final String OVERLAY = """
        {
          "target": "cobblemon:garchomp",
          "moves": [ "1:dragondance" ]
        }
        """;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    // ================================================================
    // 读取
    // ================================================================

    @Test
    public void parseJsonExposesBaseAndFormTypes() {
        TypeModifier modifier = new TypeModifier();
        Map<String, Object> values = modifier.parseJson(json(CHARIZARD));

        assertEquals("Fire", values.get("type_primary_base"));
        assertEquals("Flying", values.get("type_secondary_base"));
        assertEquals("Fire", values.get("type_primary_mega_x"));
        assertEquals("Dragon", values.get("type_secondary_mega_x"));
        // Gmax 没有属性字段 → 不生成字段，避免"编辑本体却把形态写成空属性"
        assertFalse(values.containsKey("type_primary_gmax"));

        List<String> fields = modifier.getFieldNames();
        assertEquals(4, fields.size());
    }

    @Test
    public void fieldLabelsCarryFormNameAndSlot() {
        TypeModifier modifier = new TypeModifier();
        modifier.parseJson(json(CHARIZARD));

        assertEquals("基础形态 - 主属性", modifier.getFieldLabel("type_primary_base"));
        assertEquals("基础形态 - 副属性", modifier.getFieldLabel("type_secondary_base"));
        assertEquals("Mega-X - 主属性", modifier.getFieldLabel("type_primary_mega_x"));
        assertEquals("Mega-X - 副属性", modifier.getFieldLabel("type_secondary_mega_x"));
    }

    @Test
    public void singleTypeSpeciesShowsNoneForSecondary() {
        TypeModifier modifier = new TypeModifier();
        Map<String, Object> values = modifier.parseJson(json(SINGLE_TYPE));

        assertEquals("Electric", values.get("type_primary_base"));
        assertEquals("（无副属性）", values.get("type_secondary_base"));
    }

    @Test
    public void choicesCoverAllTypesAndAllowSingleType() {
        TypeModifier modifier = new TypeModifier();
        modifier.parseJson(json(CHARIZARD));

        List<String> primaryChoices = modifier.getFieldChoices("type_primary_base");
        assertEquals(18, primaryChoices.size());
        assertTrue(primaryChoices.contains("Fire"));
        assertTrue(primaryChoices.contains("Fairy"));

        List<String> secondaryChoices = modifier.getFieldChoices("type_secondary_base");
        assertEquals(19, secondaryChoices.size());
        assertEquals("（无副属性）", secondaryChoices.get(0));
    }

    @Test
    public void unknownCustomTypeIsKeptAndOffered() {
        TypeModifier modifier = new TypeModifier();
        Map<String, Object> values = modifier.parseJson(json("""
            { "name": "custom", "primaryType": "cosmic", "baseStats": {} }
            """));

        assertEquals("cosmic", values.get("type_primary_base"));
        assertTrue("自定义属性名必须出现在下拉框里，否则会被回退成 Normal",
            modifier.getFieldChoices("type_primary_base").contains("cosmic"));
    }

    @Test
    public void overlayWithoutTypesHasNoEditableFields() {
        TypeModifier modifier = new TypeModifier();
        Map<String, Object> values = modifier.parseJson(json(OVERLAY));

        assertTrue(values.isEmpty());
        assertTrue(modifier.getFieldNames().isEmpty());
        assertFalse(modifier.hasValidStatsFields(json(OVERLAY)));
        assertTrue(modifier.hasValidStatsFields(json(CHARIZARD)));
    }

    // ================================================================
    // 写入
    // ================================================================

    @Test
    public void modifyChangesBaseTypesOnly() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(CHARIZARD);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_primary_base", "Dragon");
        values.put("type_secondary_base", "Flying");

        JsonObject modified = modifier.modifyJson(original, values);

        assertEquals("dragon", modified.get("primaryType").getAsString());
        assertEquals("flying", modified.get("secondaryType").getAsString());
        // 形态不受影响
        JsonObject mega = modified.getAsJsonArray("forms").get(0).getAsJsonObject();
        assertEquals("fire", mega.get("primaryType").getAsString());
        assertEquals("dragon", mega.get("secondaryType").getAsString());
        // 其它字段原样
        assertEquals(84, modified.getAsJsonObject("baseStats").get("attack").getAsInt());
        assertEquals(2, modified.getAsJsonArray("abilities").size());
    }

    @Test
    public void modifyCanChangeFormTypes() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(CHARIZARD);
        Map<String, Object> values = modifier.parseJson(original);
        // Mega-X 原本是 fire/dragon，把主属性改成 Ice 就变成 ice/dragon
        values.put("type_primary_mega_x", "Ice");

        JsonObject modified = modifier.modifyJson(original, values);

        JsonObject mega = modified.getAsJsonArray("forms").get(0).getAsJsonObject();
        assertEquals("ice", mega.get("primaryType").getAsString());
        assertEquals("dragon", mega.get("secondaryType").getAsString());
        assertEquals("fire", modified.get("primaryType").getAsString());
    }

    @Test
    public void modifyCanTurnSpeciesIntoSingleType() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(CHARIZARD);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_secondary_base", "（无副属性）");

        JsonObject modified = modifier.modifyJson(original, values);

        assertEquals("fire", modified.get("primaryType").getAsString());
        assertFalse(modified.has("secondaryType"));
    }

    @Test
    public void modifyAddsSecondaryTypeToSingleTypeSpecies() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(SINGLE_TYPE);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_secondary_base", "Flying");

        JsonObject modified = modifier.modifyJson(original, values);

        assertEquals("electric", modified.get("primaryType").getAsString());
        assertEquals("flying", modified.get("secondaryType").getAsString());
    }

    @Test
    public void modifyKeepsUnknownCustomTypeUnchanged() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json("""
            { "name": "custom", "primaryType": "cosmic", "secondaryType": "fire" }
            """);
        Map<String, Object> values = modifier.parseJson(original);

        JsonObject modified = modifier.modifyJson(original, values);

        assertEquals("cosmic", modified.get("primaryType").getAsString());
        assertEquals("fire", modified.get("secondaryType").getAsString());
    }

    @Test
    public void modifyLeavesOverlayWithoutTypesAlone() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(OVERLAY);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_primary_base", "Dragon");

        JsonObject modified = modifier.modifyJson(original, values);

        assertFalse("覆盖文件不该被凭空塞进属性字段", modified.has("primaryType"));
        assertTrue(modified.has("moves"));
    }

    @Test
    public void modifyRejectsDuplicateTypes() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(CHARIZARD);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_primary_base", "Dragon");
        values.put("type_secondary_base", "Dragon");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> modifier.modifyJson(original, values));
        assertTrue(error.getMessage().contains("不能相同"));
    }

    @Test
    public void modifyRejectsUnknownType() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(CHARIZARD);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_primary_base", "Banana");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> modifier.modifyJson(original, values));
        assertTrue(error.getMessage().contains("未知属性"));
    }

    @Test
    public void modifyRejectsEmptyPrimaryType() {
        TypeModifier modifier = new TypeModifier();
        JsonObject original = json(CHARIZARD);
        Map<String, Object> values = modifier.parseJson(original);
        values.put("type_primary_base", "  ");

        assertThrows(IllegalArgumentException.class, () -> modifier.modifyJson(original, values));
    }

    @Test
    public void convertFieldValueTrimsInput() {
        TypeModifier modifier = new TypeModifier();

        assertEquals("Fire", modifier.convertFieldValue("type_primary_base", "  Fire "));
        assertEquals("", modifier.convertFieldValue("type_primary_base", null));
        assertNull(null);
    }
}

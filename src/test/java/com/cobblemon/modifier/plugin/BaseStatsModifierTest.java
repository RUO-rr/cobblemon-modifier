package com.cobblemon.modifier.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class BaseStatsModifierTest {

    private BaseStatsModifier plugin;

    @Before
    public void setUp() {
        plugin = new BaseStatsModifier();
    }

    @Test
    public void getPluginName_shouldReturnCorrectName() {
        assertEquals("种族值修改", plugin.getPluginName());
    }

    @Test
    public void getTargetJsonPaths_shouldReturnSpeciesPath() {
        List<String> paths = plugin.getTargetJsonPaths();
        assertEquals(1, paths.size());
        assertEquals("data/cobblemon/species", paths.get(0));
    }

    @Test
    public void convertFieldValue_shouldParseValidInteger() {
        assertEquals(42, plugin.convertFieldValue("hp_base", "42"));
    }

    @Test
    public void convertFieldValue_shouldReturnZeroForNonNumeric() {
        assertEquals(0, plugin.convertFieldValue("hp_base", "abc"));
    }

    // ---- parseJson: base stats only ----

    @Test
    public void parseJson_shouldExtractAllSixBaseStats() {
        JsonObject json = speciesJson(45, 49, 65, 80, 70, 60);

        Map<String, Object> values = plugin.parseJson(json);

        assertEquals(45, values.get("hp_base"));
        assertEquals(49, values.get("attack_base"));
        assertEquals(65, values.get("defence_base"));
        assertEquals(80, values.get("special_attack_base"));
        assertEquals(70, values.get("special_defence_base"));
        assertEquals(60, values.get("speed_base"));
        assertEquals(6, values.size());
    }

    @Test
    public void parseJson_shouldReturnNoFieldsWhenNeitherBaseStatsNorForms() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "pikachu");
        // 既没有 baseStats 也没有 forms —— 不是种族值可编辑的数据

        Map<String, Object> values = plugin.parseJson(json);

        assertTrue(values.isEmpty());
        assertTrue(plugin.getFieldNames().isEmpty());
    }

    /**
     * species_additions 这类覆盖文件（只有 target + moves 等）不该让种族值插件显示 6 个 0 ——
     * 否则用户一保存就会把 0 写进覆盖文件，反过来顶掉物种本体的种族值。
     */
    @Test
    public void parseJson_shouldShowNoFieldsForSpeciesAddition() {
        JsonObject addition = new JsonObject();
        addition.addProperty("target", "cobblemon:garchomp");
        addition.add("moves", new JsonArray());

        Map<String, Object> values = plugin.parseJson(addition);

        assertTrue(values.isEmpty());
        assertTrue(plugin.getFieldNames().isEmpty());
    }

    // ---- parseJson: with forms ----

    @Test
    public void parseJson_shouldExtractMegaFormStats() {
        JsonObject json = speciesWithMegaForms();

        Map<String, Object> values = plugin.parseJson(json);

        // base form
        assertEquals(78, values.get("hp_base"));
        assertEquals(84, values.get("attack_base"));
        // mega-x
        assertEquals(78, values.get("hp_mega_x"));
        assertEquals(130, values.get("attack_mega_x"));
        // mega-y
        assertEquals(78, values.get("hp_mega_y"));
        assertEquals(104, values.get("attack_mega_y"));
        // 6 base + 6 mega_x + 6 mega_y = 18
        assertEquals(18, values.size());
    }

    @Test
    public void getFieldNames_afterParse_shouldIncludeAllForms() {
        plugin.parseJson(speciesWithMegaForms());
        List<String> names = plugin.getFieldNames();
        assertEquals(18, names.size());
        assertTrue(names.contains("hp_base"));
        assertTrue(names.contains("attack_mega_x"));
        assertTrue(names.contains("speed_mega_y"));
    }

    @Test
    public void getFieldLabel_shouldContainFormName() {
        plugin.parseJson(speciesWithMegaForms());

        String baseLabel = plugin.getFieldLabel("hp_base");
        assertTrue(baseLabel.contains("基础形态"));
        assertTrue(baseLabel.contains("HP"));

        String megaLabel = plugin.getFieldLabel("attack_mega_x");
        assertTrue(megaLabel.contains("Mega-X"));
        assertTrue(megaLabel.contains("攻击"));
    }

    // ---- modifyJson ----

    @Test
    public void modifyJson_shouldUpdateBaseStatsValues() {
        JsonObject original = speciesJson(45, 49, 65, 80, 70, 60);
        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("hp_base", 100);
        newValues.put("attack_base", 90);
        newValues.put("defence_base", 80);
        newValues.put("special_attack_base", 70);
        newValues.put("special_defence_base", 60);
        newValues.put("speed_base", 50);

        JsonObject modified = plugin.modifyJson(original, newValues);
        JsonObject bs = modified.getAsJsonObject("baseStats");

        assertEquals(100, bs.get("hp").getAsInt());
        assertEquals(90, bs.get("attack").getAsInt());
        assertEquals(50, bs.get("speed").getAsInt());
    }

    @Test
    public void modifyJson_shouldUpdateMegaFormStats() {
        JsonObject json = speciesWithMegaForms();
        plugin.parseJson(json);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("hp_mega_x", 0);
        newValues.put("attack_mega_x", 200);
        newValues.put("defence_mega_x", 0);
        newValues.put("special_attack_mega_x", 0);
        newValues.put("special_defence_mega_x", 0);
        newValues.put("speed_mega_x", 0);

        JsonObject modified = plugin.modifyJson(json, newValues);
        JsonArray forms = modified.getAsJsonArray("forms");

        JsonObject megaX = null;
        for (int i = 0; i < forms.size(); i++) {
            JsonObject f = forms.get(i).getAsJsonObject();
            if ("Mega-X".equals(f.get("name").getAsString())) {
                megaX = f;
                break;
            }
        }
        assertNotNull(megaX);
        assertEquals(200, megaX.getAsJsonObject("baseStats").get("attack").getAsInt());
    }

    @Test
    public void modifyJson_shouldPreserveUnrelatedFields() {
        JsonObject original = speciesJson(35, 55, 40, 50, 45, 90);
        original.addProperty("name", "pikachu");
        original.addProperty("primaryType", "Electric");
        original.addProperty("nationalPokedexNumber", 25);
        plugin.parseJson(original);

        Map<String, Object> newValues = Map.of(
            "hp_base", 100, "attack_base", 0, "defence_base", 0,
            "special_attack_base", 0, "special_defence_base", 0, "speed_base", 0);

        JsonObject modified = plugin.modifyJson(original, newValues);
        assertEquals("pikachu", modified.get("name").getAsString());
        assertEquals("Electric", modified.get("primaryType").getAsString());
        assertEquals(25, modified.get("nationalPokedexNumber").getAsInt());
    }

    // ---- 继承本体的形态（没有 baseStats）----
    // 回归：旧实现会凭空创建 baseStats 并写入全 0，导致 Gmax / 地区形态在对战里变成废人

    @Test
    public void parseJson_shouldShowBaseValuesForFormWithoutOwnBaseStats() {
        JsonObject json = speciesWithInheritedForm();

        Map<String, Object> values = plugin.parseJson(json);

        // 形态没有 baseStats = 继承本体，界面应显示本体值而不是 0
        assertEquals(80, values.get("hp_form_0"));
        assertEquals(82, values.get("attack_form_0"));
        assertEquals(80, values.get("speed_form_0"));
    }

    @Test
    public void modifyJson_shouldNotCreateBaseStatsForInheritedForm() {
        JsonObject original = speciesWithInheritedForm();
        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("hp_base", 255);
        newValues.put("attack_base", 82);
        newValues.put("defence_base", 83);
        newValues.put("special_attack_base", 100);
        newValues.put("special_defence_base", 100);
        newValues.put("speed_base", 80);
        // 形态字段保持继承来的值，等于没动过
        newValues.put("hp_form_0", 80);
        newValues.put("attack_form_0", 82);
        newValues.put("defence_form_0", 83);
        newValues.put("special_attack_form_0", 100);
        newValues.put("special_defence_form_0", 100);
        newValues.put("speed_form_0", 80);

        JsonObject modified = plugin.modifyJson(original, newValues);

        assertEquals(255, modified.getAsJsonObject("baseStats").get("hp").getAsInt());
        JsonObject form = findForm(modified, "Gmax");
        assertNotNull(form);
        assertFalse("继承本体的形态不应被写入 baseStats", form.has("baseStats"));
    }

    @Test
    public void modifyJson_shouldCreateBaseStatsWhenUserActuallyChangesForm() {
        JsonObject original = speciesWithInheritedForm();
        plugin.parseJson(original);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("hp_base", 80);
        newValues.put("attack_base", 82);
        newValues.put("defence_base", 83);
        newValues.put("special_attack_base", 100);
        newValues.put("special_defence_base", 100);
        newValues.put("speed_base", 80);
        newValues.put("hp_form_0", 120);   // 用户确实改了形态的 HP
        newValues.put("attack_form_0", 82);
        newValues.put("defence_form_0", 83);
        newValues.put("special_attack_form_0", 100);
        newValues.put("special_defence_form_0", 100);
        newValues.put("speed_form_0", 80);

        JsonObject modified = plugin.modifyJson(original, newValues);

        JsonObject form = findForm(modified, "Gmax");
        assertNotNull(form);
        assertTrue("用户改动了形态，应落地 baseStats", form.has("baseStats"));
        assertEquals(120, form.getAsJsonObject("baseStats").get("hp").getAsInt());
        assertEquals(82, form.getAsJsonObject("baseStats").get("attack").getAsInt());
    }

    // ---- hasValidStatsFields ----

    @Test
    public void hasValidStatsFields_shouldReturnTrueForPositiveStats() {
        assertTrue(plugin.hasValidStatsFields(speciesJson(45, 49, 65, 80, 70, 60)));
    }

    @Test
    public void hasValidStatsFields_shouldReturnFalseForAllZero() {
        assertFalse(plugin.hasValidStatsFields(speciesJson(0, 0, 0, 0, 0, 0)));
    }

    @Test
    public void hasValidStatsFields_shouldReturnFalseForEmptyJson() {
        assertFalse(plugin.hasValidStatsFields(new JsonObject()));
    }

    @Test
    public void hasValidStatsFields_shouldCheckFormsToo() {
        JsonObject json = new JsonObject();
        JsonArray forms = new JsonArray();
        JsonObject form = new JsonObject();
        form.addProperty("name", "Alola");
        JsonObject fs = new JsonObject();
        fs.addProperty("hp", 50);
        form.add("baseStats", fs);
        forms.add(form);
        json.add("forms", forms);
        // no top-level baseStats, but form has positive stats

        assertTrue(plugin.hasValidStatsFields(json));
    }

    // ---- helpers ----

    private static JsonObject speciesJson(int hp, int atk, int def, int spa, int spd, int sp) {
        JsonObject json = new JsonObject();
        json.addProperty("name", "testmon");
        JsonObject bs = new JsonObject();
        bs.addProperty("hp", hp);
        bs.addProperty("attack", atk);
        bs.addProperty("defence", def);
        bs.addProperty("special_attack", spa);
        bs.addProperty("special_defence", spd);
        bs.addProperty("speed", sp);
        json.add("baseStats", bs);
        return json;
    }

    private static JsonObject speciesWithMegaForms() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "charizard");

        JsonObject bs = new JsonObject();
        bs.addProperty("hp", 78);
        bs.addProperty("attack", 84);
        bs.addProperty("defence", 78);
        bs.addProperty("special_attack", 109);
        bs.addProperty("special_defence", 85);
        bs.addProperty("speed", 100);
        json.add("baseStats", bs);

        JsonArray forms = new JsonArray();

        JsonObject megaX = new JsonObject();
        megaX.addProperty("name", "Mega-X");
        JsonObject mx = new JsonObject();
        mx.addProperty("hp", 78); mx.addProperty("attack", 130);
        mx.addProperty("defence", 111); mx.addProperty("special_attack", 130);
        mx.addProperty("special_defence", 85); mx.addProperty("speed", 100);
        megaX.add("baseStats", mx);
        forms.add(megaX);

        JsonObject megaY = new JsonObject();
        megaY.addProperty("name", "Mega-Y");
        JsonObject my = new JsonObject();
        my.addProperty("hp", 78); my.addProperty("attack", 104);
        my.addProperty("defence", 78); my.addProperty("special_attack", 159);
        my.addProperty("special_defence", 115); my.addProperty("speed", 100);
        megaY.add("baseStats", my);
        forms.add(megaY);

        json.add("forms", forms);
        return json;
    }

    /**
     * 带一个"没有 baseStats 的形态"的物种 —— 对应本体里妙蛙花 Gmax 那种写法：
     * 形态不带 baseStats 就表示继承本体，绝不能给它写全 0。
     */
    private static JsonObject speciesWithInheritedForm() {
        JsonObject json = speciesJson(80, 82, 83, 100, 100, 80);
        json.addProperty("name", "venusaur");

        JsonArray forms = new JsonArray();
        JsonObject gmax = new JsonObject();
        gmax.addProperty("name", "Gmax");
        gmax.addProperty("aspects", "gmax");
        forms.add(gmax);

        json.add("forms", forms);
        return json;
    }

    private static JsonObject findForm(JsonObject species, String formName) {
        if (!species.has("forms")) {
            return null;
        }
        for (JsonElement elem : species.getAsJsonArray("forms")) {
            if (!elem.isJsonObject()) continue;
            JsonObject form = elem.getAsJsonObject();
            if (form.has("name") && formName.equals(form.get("name").getAsString())) {
                return form;
            }
        }
        return null;
    }
}

package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.service.MoveCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class MoveModifierTest {

    private MoveModifier plugin;

    @Before
    public void setUp() {
        plugin = new MoveModifier();
        plugin.setMoveCatalog(catalog("tackle", "dragondance", "blitzstrike", "aerialace"));
    }

    @Test
    public void getPluginName_shouldReturnCorrectName() {
        assertEquals("技能修改", plugin.getPluginName());
    }

    @Test
    public void getTargetJsonPaths_shouldReturnSpeciesPath() {
        assertEquals(List.of("data/cobblemon/species"), plugin.getTargetJsonPaths());
    }

    // ---- parseJson ----

    @Test
    public void parseJson_shouldPutEmptySlotsFirstThenExistingMoves() {
        JsonObject json = speciesWithMoves("1:tackle", "tm:aerialace");

        Map<String, Object> values = plugin.parseJson(json);

        List<String> keys = plugin.getFieldNames();
        assertEquals(4 + 2, keys.size());
        assertEquals("move_new_0", keys.get(0));
        assertEquals("move_new_3", keys.get(3));
        assertEquals("move_0", keys.get(4));
        assertEquals("move_1", keys.get(5));
        assertEquals("", values.get("move_new_0"));
        assertEquals("1:tackle", values.get("move_0"));
        assertEquals("tm:aerialace", values.get("move_1"));
    }

    @Test
    public void parseJson_shouldWorkWithoutMovesArray() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "testmon");

        plugin.parseJson(json);

        assertEquals(4, plugin.getFieldNames().size());
    }

    @Test
    public void getFieldLabel_shouldSeparateNewSlotsFromExisting() {
        plugin.parseJson(speciesWithMoves("1:tackle"));

        assertEquals("新增技能 1", plugin.getFieldLabel("move_new_0"));
        assertEquals("技能 1", plugin.getFieldLabel("move_0"));
    }

    // ---- modifyJson ----

    @Test
    public void modifyJson_shouldAppendNewMoveAtTheEnd() {
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_new_0", "1:dragondance");

        JsonObject modified = plugin.modifyJson(original, values);

        JsonArray moves = modified.getAsJsonArray("moves");
        assertEquals(2, moves.size());
        assertEquals("1:tackle", moves.get(0).getAsString());
        assertEquals("1:dragondance", moves.get(1).getAsString());
    }

    @Test
    public void modifyJson_shouldKeepExistingOrder() {
        JsonObject original = speciesWithMoves("1:tackle", "tm:aerialace", "40:blitzstrike");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));

        JsonObject modified = plugin.modifyJson(original, values);

        JsonArray moves = modified.getAsJsonArray("moves");
        assertEquals("1:tackle", moves.get(0).getAsString());
        assertEquals("tm:aerialace", moves.get(1).getAsString());
        assertEquals("40:blitzstrike", moves.get(2).getAsString());
    }

    @Test
    public void modifyJson_shouldDeleteMoveWhenFieldCleared() {
        JsonObject original = speciesWithMoves("1:tackle", "tm:aerialace");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_0", "");

        JsonObject modified = plugin.modifyJson(original, values);

        JsonArray moves = modified.getAsJsonArray("moves");
        assertEquals(1, moves.size());
        assertEquals("tm:aerialace", moves.get(0).getAsString());
    }

    @Test
    public void modifyJson_shouldAcceptAllKnownPrefixes() {
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_new_0", "egg:tackle");
        values.put("move_new_1", "tutor:tackle");
        values.put("move_new_2", "legacy:tackle");
        values.put("move_new_3", "special:tackle");

        JsonObject modified = plugin.modifyJson(original, values);

        assertEquals(5, modified.getAsJsonArray("moves").size());
    }

    @Test
    public void modifyJson_shouldPreserveOtherFields() {
        JsonObject original = speciesWithMoves("1:tackle");
        original.addProperty("name", "garchomp");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));

        JsonObject modified = plugin.modifyJson(original, values);

        assertEquals("garchomp", modified.get("name").getAsString());
    }

    // ---- 校验 ----

    @Test
    public void modifyJson_shouldRejectBadFormat() {
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_new_0", "dragondance"); // 缺少前缀

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> plugin.modifyJson(original, values));
        assertTrue(error.getMessage().contains("写法不对"));
    }

    @Test
    public void modifyJson_shouldRejectUnknownMoveId() {
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_new_0", "1:nosuchmove");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> plugin.modifyJson(original, values));
        assertTrue(error.getMessage().contains("未知招式"));
        assertTrue(error.getMessage().contains("nosuchmove"));
    }

    @Test
    public void modifyJson_shouldNormalizeSeparatorsAndCase() {
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_new_0", "1:Dragon-Dance");

        JsonObject modified = plugin.modifyJson(original, values);

        assertEquals("1:dragondance", modified.getAsJsonArray("moves").get(1).getAsString());
    }

    @Test
    public void modifyJson_shouldSkipIdCheckWhenCatalogNotReady() {
        MoveModifier noCatalog = new MoveModifier();
        noCatalog.setMoveCatalog(new MoveCatalog() {
            @Override
            public Set<String> knownMoveIds() {
                return Set.of();
            }

            @Override
            public boolean isReady() {
                return false;
            }
        });
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(noCatalog.parseJson(original));
        values.put("move_new_0", "1:whatever");

        JsonObject modified = noCatalog.modifyJson(original, values);

        assertEquals(2, modified.getAsJsonArray("moves").size());
    }

    @Test
    public void modifyJson_shouldRejectEmptyMoveList() {
        JsonObject original = speciesWithMoves("1:tackle");
        Map<String, Object> values = new LinkedHashMap<>(plugin.parseJson(original));
        values.put("move_0", "");

        assertThrows(IllegalArgumentException.class, () -> plugin.modifyJson(original, values));
    }

    // ---- hasValidStatsFields ----

    @Test
    public void hasValidStatsFields_shouldRequireMoves() {
        assertTrue(plugin.hasValidStatsFields(speciesWithMoves("1:tackle")));
        assertFalse(plugin.hasValidStatsFields(new JsonObject()));
    }

    // ---- helpers ----

    private static JsonObject speciesWithMoves(String... moves) {
        JsonObject json = new JsonObject();
        json.addProperty("name", "testmon");
        JsonArray array = new JsonArray();
        for (String move : moves) {
            array.add(move);
        }
        json.add("moves", array);
        return json;
    }

    private static MoveCatalog catalog(String... ids) {
        Set<String> known = Set.of(ids);
        return new MoveCatalog() {
            @Override
            public Set<String> knownMoveIds() {
                return known;
            }

            @Override
            public boolean isReady() {
                return true;
            }
        };
    }
}

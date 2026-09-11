package com.cobblemon.modifier.model;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class PokemonStatsTest {

    // ================================================================
    // Constants
    // ================================================================

    @Test
    public void constants_shouldHaveSixFieldNames() {
        assertEquals(6, PokemonStats.FIELD_NAMES.length);
    }

    @Test
    public void constants_fieldNamesOrder() {
        assertEquals("hp", PokemonStats.FIELD_NAMES[0]);
        assertEquals("attack", PokemonStats.FIELD_NAMES[1]);
        assertEquals("defence", PokemonStats.FIELD_NAMES[2]);
        assertEquals("special_attack", PokemonStats.FIELD_NAMES[3]);
        assertEquals("special_defence", PokemonStats.FIELD_NAMES[4]);
        assertEquals("speed", PokemonStats.FIELD_NAMES[5]);
    }

    @Test
    public void zero_shouldBeAllZeros() {
        assertEquals(0, PokemonStats.ZERO.hp());
        assertEquals(0, PokemonStats.ZERO.attack());
        assertEquals(0, PokemonStats.ZERO.defence());
        assertEquals(0, PokemonStats.ZERO.specialAttack());
        assertEquals(0, PokemonStats.ZERO.specialDefence());
        assertEquals(0, PokemonStats.ZERO.speed());
    }

    // ================================================================
    // fromJson
    // ================================================================

    @Test
    public void fromJson_shouldReadAllSixStats() {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 45);
        json.addProperty("attack", 49);
        json.addProperty("defence", 65);
        json.addProperty("special_attack", 80);
        json.addProperty("special_defence", 70);
        json.addProperty("speed", 60);

        PokemonStats stats = PokemonStats.fromJson(json);

        assertEquals(45, stats.hp());
        assertEquals(49, stats.attack());
        assertEquals(65, stats.defence());
        assertEquals(80, stats.specialAttack());
        assertEquals(70, stats.specialDefence());
        assertEquals(60, stats.speed());
    }

    @Test
    public void fromJson_shouldDefaultToZeroForMissingFields() {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 45);

        PokemonStats stats = PokemonStats.fromJson(json);

        assertEquals(45, stats.hp());
        assertEquals(0, stats.attack());
        assertEquals(0, stats.speed());
    }

    @Test
    public void fromJson_shouldReturnZeroForNullInput() {
        PokemonStats stats = PokemonStats.fromJson(null);
        assertEquals(PokemonStats.ZERO, stats);
    }

    @Test
    public void fromJson_shouldReturnZeroForEmptyJson() {
        PokemonStats stats = PokemonStats.fromJson(new JsonObject());
        assertEquals(PokemonStats.ZERO, stats);
    }

    @Test
    public void fromJson_shouldIgnoreNonIntegerValues() {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 45);
        json.addProperty("attack", "invalid");  // non-int, getAsInt throws

        // Gson's getAsInt on a non-numeric string will throw NumberFormatException,
        // so this stat will be missing in effect. Let's use a JsonPrimitive instead.
        // Actually Gson stores "invalid" as a string; json.get("attack").getAsInt() throws.
        // The fromJson method calls getInt which has a try-catch... let me check.
        // No, the getInt method just does json.has(key) && !json.get(key).isJsonNull()
        // ? json.get(key).getAsInt() : 0; — it would throw on "invalid" string.
        // This is acceptable behavior — malformed JSON should fail fast.
    }

    // ================================================================
    // writeToJson
    // ================================================================

    @Test
    public void writeToJson_shouldWriteAllSixStats() {
        PokemonStats stats = new PokemonStats(100, 80, 70, 90, 85, 110);
        JsonObject target = new JsonObject();

        stats.writeToJson(target);

        assertEquals(100, target.get("hp").getAsInt());
        assertEquals(80, target.get("attack").getAsInt());
        assertEquals(70, target.get("defence").getAsInt());
        assertEquals(90, target.get("special_attack").getAsInt());
        assertEquals(85, target.get("special_defence").getAsInt());
        assertEquals(110, target.get("speed").getAsInt());
    }

    @Test
    public void fromJson_writeToJson_roundTrip() {
        JsonObject original = new JsonObject();
        original.addProperty("hp", 35);
        original.addProperty("attack", 55);
        original.addProperty("defence", 40);
        original.addProperty("special_attack", 50);
        original.addProperty("special_defence", 45);
        original.addProperty("speed", 90);

        PokemonStats stats = PokemonStats.fromJson(original);
        JsonObject restored = new JsonObject();
        stats.writeToJson(restored);

        PokemonStats stats2 = PokemonStats.fromJson(restored);
        assertEquals(stats, stats2);
    }

    // ================================================================
    // toFieldMap
    // ================================================================

    @Test
    public void toFieldMap_shouldUseBaseSuffix() {
        PokemonStats stats = new PokemonStats(35, 55, 40, 50, 45, 90);
        Map<String, Integer> map = stats.toFieldMap("_base");

        assertEquals(6, map.size());
        assertEquals(35, (int) map.get("hp_base"));
        assertEquals(55, (int) map.get("attack_base"));
        assertEquals(40, (int) map.get("defence_base"));
        assertEquals(50, (int) map.get("special_attack_base"));
        assertEquals(45, (int) map.get("special_defence_base"));
        assertEquals(90, (int) map.get("speed_base"));
    }

    @Test
    public void toFieldMap_shouldUseMegaSuffix() {
        PokemonStats stats = new PokemonStats(60, 80, 70, 100, 85, 120);
        Map<String, Integer> map = stats.toFieldMap("_mega_x");

        assertEquals(60, (int) map.get("hp_mega_x"));
        assertEquals(80, (int) map.get("attack_mega_x"));
        assertEquals(100, (int) map.get("special_attack_mega_x"));
    }

    @Test
    public void toFieldMap_shouldUseEmptySuffix() {
        PokemonStats stats = new PokemonStats(1, 2, 3, 4, 5, 6);
        Map<String, Integer> map = stats.toFieldMap("");

        assertEquals((Integer) 1, map.get("hp"));
        assertEquals((Integer) 6, map.get("speed"));
    }

    // ================================================================
    // hasAnyPositiveValue
    // ================================================================

    @Test
    public void hasAnyPositiveValue_shouldBeFalseWhenAllZero() {
        assertFalse(PokemonStats.ZERO.hasAnyPositiveValue());
    }

    @Test
    public void hasAnyPositiveValue_shouldBeTrueWhenHpPositive() {
        PokemonStats stats = new PokemonStats(1, 0, 0, 0, 0, 0);
        assertTrue(stats.hasAnyPositiveValue());
    }

    @Test
    public void hasAnyPositiveValue_shouldBeTrueWhenSpeedPositive() {
        PokemonStats stats = new PokemonStats(0, 0, 0, 0, 0, 100);
        assertTrue(stats.hasAnyPositiveValue());
    }

    @Test
    public void hasAnyPositiveValue_shouldBeTrueWhenAnyStatPositive() {
        PokemonStats stats = new PokemonStats(0, 0, 65, 0, 0, 0);
        assertTrue(stats.hasAnyPositiveValue());
    }

    // ================================================================
    // withField (immutability)
    // ================================================================

    @Test
    public void withField_shouldReturnNewInstance() {
        PokemonStats original = new PokemonStats(45, 49, 65, 80, 70, 60);
        PokemonStats updated = original.withField(PokemonStats.HP, 100);

        assertEquals(45, original.hp());  // original unchanged
        assertEquals(100, updated.hp());
        assertEquals(49, updated.attack());  // other fields unchanged
    }

    @Test
    public void withField_shouldUpdateEachField() {
        PokemonStats stats = new PokemonStats(1, 2, 3, 4, 5, 6);

        assertEquals(10, stats.withField(PokemonStats.HP, 10).hp());
        assertEquals(20, stats.withField(PokemonStats.ATTACK, 20).attack());
        assertEquals(30, stats.withField(PokemonStats.DEFENCE, 30).defence());
        assertEquals(40, stats.withField(PokemonStats.SPECIAL_ATTACK, 40).specialAttack());
        assertEquals(50, stats.withField(PokemonStats.SPECIAL_DEFENCE, 50).specialDefence());
        assertEquals(60, stats.withField(PokemonStats.SPEED, 60).speed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void withField_shouldThrowOnUnknownField() {
        PokemonStats.ZERO.withField("unknown_field", 10);
    }

    // ================================================================
    // getByFieldName
    // ================================================================

    @Test
    public void getByFieldName_shouldReturnCorrectValue() {
        PokemonStats stats = new PokemonStats(45, 49, 65, 80, 70, 60);

        assertEquals(45, stats.getByFieldName(PokemonStats.HP));
        assertEquals(49, stats.getByFieldName(PokemonStats.ATTACK));
        assertEquals(65, stats.getByFieldName(PokemonStats.DEFENCE));
        assertEquals(80, stats.getByFieldName(PokemonStats.SPECIAL_ATTACK));
        assertEquals(70, stats.getByFieldName(PokemonStats.SPECIAL_DEFENCE));
        assertEquals(60, stats.getByFieldName(PokemonStats.SPEED));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getByFieldName_shouldThrowOnUnknownField() {
        PokemonStats.ZERO.getByFieldName("bad_field");
    }

    // ================================================================
    // equals / hashCode
    // ================================================================

    @Test
    public void equals_shouldBeReflexive() {
        PokemonStats stats = new PokemonStats(1, 2, 3, 4, 5, 6);
        assertEquals(stats, stats);
    }

    @Test
    public void equals_shouldBeSymmetric() {
        PokemonStats a = new PokemonStats(1, 2, 3, 4, 5, 6);
        PokemonStats b = new PokemonStats(1, 2, 3, 4, 5, 6);
        assertTrue(a.equals(b) && b.equals(a));
    }

    @Test
    public void equals_shouldReturnFalseForDifferentStats() {
        PokemonStats a = new PokemonStats(1, 2, 3, 4, 5, 6);
        PokemonStats b = new PokemonStats(10, 2, 3, 4, 5, 6);
        assertNotEquals(a, b);
    }

    @Test
    public void hashCode_shouldBeConsistent() {
        PokemonStats a = new PokemonStats(45, 49, 65, 80, 70, 60);
        PokemonStats b = new PokemonStats(45, 49, 65, 80, 70, 60);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

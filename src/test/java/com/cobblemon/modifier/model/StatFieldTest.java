package com.cobblemon.modifier.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class StatFieldTest {

    // ================================================================
    // Factories
    // ================================================================

    @Test
    public void ofInt_shouldCreateWithIntegerType() {
        StatField field = StatField.ofInt("hp_base", "HP");
        assertEquals("hp_base", field.key());
        assertEquals("HP", field.label());
        assertEquals(Integer.class, field.valueType());
    }

    @Test
    public void ofString_shouldCreateWithStringType() {
        StatField field = StatField.ofString("ability", "Ability");
        assertEquals("ability", field.key());
        assertEquals(String.class, field.valueType());
    }

    @Test
    public void ofFloat_shouldCreateWithFloatType() {
        StatField field = StatField.ofFloat("spawnRate", "Spawn Rate");
        assertEquals(Float.class, field.valueType());
    }

    // ================================================================
    // convertValue — Integer
    // ================================================================

    @Test
    public void convertValue_shouldParseValidInteger() {
        StatField field = StatField.ofInt("hp_base", "HP");
        Object result = field.convertValue("42");
        assertTrue(result instanceof Integer);
        assertEquals(42, result);
    }

    @Test
    public void convertValue_shouldParseNegativeInteger() {
        StatField field = StatField.ofInt("hp_base", "HP");
        assertEquals(-1, field.convertValue("-1"));
    }

    @Test
    public void convertValue_shouldParseZeroInteger() {
        StatField field = StatField.ofInt("hp_base", "HP");
        assertEquals(0, field.convertValue("0"));
    }

    @Test
    public void convertValue_shouldReturnZeroForInvalidInteger() {
        StatField field = StatField.ofInt("hp_base", "HP");
        Object result = field.convertValue("not_a_number");
        assertTrue(result instanceof Integer);
        assertEquals(0, result);
    }

    @Test
    public void convertValue_shouldTrimWhitespaceForInt() {
        StatField field = StatField.ofInt("hp_base", "HP");
        assertEquals(99, field.convertValue("  99  "));
    }

    @Test
    public void convertValue_shouldReturnZeroForEmptyInteger() {
        StatField field = StatField.ofInt("hp_base", "HP");
        assertEquals(0, field.convertValue(""));
    }

    // ================================================================
    // convertValue — Float
    // ================================================================

    @Test
    public void convertValue_shouldParseValidFloat() {
        StatField field = StatField.ofFloat("spawnRate", "Rate");
        Object result = field.convertValue("3.14");
        assertTrue(result instanceof Float);
        assertEquals(3.14f, (Float) result, 0.001f);
    }

    @Test
    public void convertValue_shouldParseIntegerAsFloat() {
        StatField field = StatField.ofFloat("weight", "Weight");
        Object result = field.convertValue("5");
        assertEquals(5.0f, (Float) result, 0.001f);
    }

    @Test
    public void convertValue_shouldReturnZeroForInvalidFloat() {
        StatField field = StatField.ofFloat("spawnRate", "Rate");
        Object result = field.convertValue("abc");
        assertTrue(result instanceof Float);
        assertEquals(0.0f, (Float) result, 0.001f);
    }

    // ================================================================
    // convertValue — String
    // ================================================================

    @Test
    public void convertValue_shouldReturnTrimmedString() {
        StatField field = StatField.ofString("ability", "Ability");
        assertEquals("overgrow", field.convertValue("  overgrow  "));
    }

    @Test
    public void convertValue_shouldReturnEmptyString() {
        StatField field = StatField.ofString("ability", "Ability");
        assertEquals("", field.convertValue(""));
    }

    @Test
    public void convertValue_shouldReturnSameString() {
        StatField field = StatField.ofString("name", "Name");
        assertEquals("pikachu", field.convertValue("pikachu"));
    }

    // ================================================================
    // Constructor validation
    // ================================================================

    @Test(expected = NullPointerException.class)
    public void constructor_shouldRejectNullKey() {
        new StatField(null, "label", Integer.class);
    }

    @Test(expected = NullPointerException.class)
    public void constructor_shouldRejectNullLabel() {
        new StatField("key", null, Integer.class);
    }

    @Test(expected = NullPointerException.class)
    public void constructor_shouldRejectNullValueType() {
        new StatField("key", "label", null);
    }

    // ================================================================
    // equals / hashCode
    // ================================================================

    @Test
    public void equals_shouldReturnTrueForSameValues() {
        StatField a = StatField.ofInt("hp_base", "HP");
        StatField b = StatField.ofInt("hp_base", "HP");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equals_shouldReturnFalseForDifferentKey() {
        StatField a = StatField.ofInt("hp_base", "HP");
        StatField b = StatField.ofInt("atk_base", "HP");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_shouldReturnFalseForDifferentType() {
        StatField a = StatField.ofInt("hp", "HP");
        StatField b = StatField.ofString("hp", "HP");
        assertNotEquals(a, b);
    }
}

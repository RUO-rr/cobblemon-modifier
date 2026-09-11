package com.cobblemon.modifier.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class JarResourcePathTest {

    // ================================================================
    // parse — valid cases
    // ================================================================

    @Test
    public void parse_shouldExtractJarNameAndPath() {
        JarResourcePath path = JarResourcePath.parse("[cobblemon.jar]data/species/pikachu.json");
        assertEquals("cobblemon.jar", path.jarName());
        assertEquals("data/species/pikachu.json", path.jsonPath());
    }

    @Test
    public void parse_shouldHandleJarNameWithHyphens() {
        JarResourcePath path = JarResourcePath.parse(
            "[mushiromega-fabric-1.3.1.jar]data/cobblemon/species/mewtwo.json");
        assertEquals("mushiromega-fabric-1.3.1.jar", path.jarName());
    }

    @Test
    public void parse_shouldHandleDeeplyNestedPath() {
        JarResourcePath path = JarResourcePath.parse(
            "[mod.jar]a/b/c/d/e/deep.json");
        assertEquals("mod.jar", path.jarName());
        assertEquals("a/b/c/d/e/deep.json", path.jsonPath());
    }

    @Test
    public void parse_shouldHandleJsonPathWithJsonExtension() {
        JarResourcePath path = JarResourcePath.parse("[a.jar]something.json");
        assertEquals("a.jar", path.jarName());
        assertEquals("something.json", path.jsonPath());
    }

    // ================================================================
    // parse — error cases
    // ================================================================

    @Test(expected = IllegalArgumentException.class)
    public void parse_shouldThrowOnNoBrackets() {
        JarResourcePath.parse("no_brackets_here/file.json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_shouldThrowOnEmptyJarName() {
        JarResourcePath.parse("[]data/file.json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_shouldThrowOnEmptyJsonPath() {
        JarResourcePath.parse("[jar.jar]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_shouldThrowOnReversedBrackets() {
        JarResourcePath.parse("]jarName[path");
    }

    // ================================================================
    // tryParse
    // ================================================================

    @Test
    public void tryParse_shouldReturnPathForValidInput() {
        JarResourcePath path = JarResourcePath.tryParse("[cobblemon.jar]data/pikachu.json");
        assertNotNull(path);
        assertEquals("cobblemon.jar", path.jarName());
    }

    @Test
    public void tryParse_shouldReturnNullForInvalidInput() {
        assertNull(JarResourcePath.tryParse("not valid"));
        assertNull(JarResourcePath.tryParse("[]data.json"));
        assertNull(JarResourcePath.tryParse(""));
    }

    // ================================================================
    // parseFromDisplayText
    // ================================================================

    @Test
    public void parseFromDisplayText_shouldExtractRawPath() {
        // Format: [001] [06-16 00:01] [cobblemon.jar]data/species/pikachu.json
        String display = "[001] [06-16 00:01] [cobblemon.jar]data/species/pikachu.json";
        JarResourcePath path = JarResourcePath.parseFromDisplayText(display);
        assertEquals("cobblemon.jar", path.jarName());
        assertEquals("data/species/pikachu.json", path.jsonPath());
    }

    @Test
    public void parseFromDisplayText_shouldFallbackToDirectParse() {
        // No display prefix — just the raw format
        String raw = "[test.jar]data/file.json";
        JarResourcePath path = JarResourcePath.parseFromDisplayText(raw);
        assertEquals("test.jar", path.jarName());
        assertEquals("data/file.json", path.jsonPath());
    }

    // ================================================================
    // extractPokemonName
    // ================================================================

    @Test
    public void extractPokemonName_shouldExtractFromJsonPath() {
        JarResourcePath path = JarResourcePath.parse(
            "[cobblemon.jar]data/cobblemon/species/pikachu.json");
        assertEquals("pikachu", path.extractPokemonName());
    }

    @Test
    public void extractPokemonName_shouldHandleNameWithHyphen() {
        JarResourcePath path = JarResourcePath.parse(
            "[mod.jar]data/species/tapu-koko.json");
        assertEquals("tapu-koko", path.extractPokemonName());
    }

    @Test
    public void extractPokemonName_shouldReturnFullPathWhenFlat() {
        // flat path without directory — returns the full jsonPath as-is
        JarResourcePath path = JarResourcePath.parse("[mod.jar]bulbasaur.json");
        assertEquals("bulbasaur.json", path.extractPokemonName());
    }

    @Test
    public void extractPokemonName_shouldReturnFullPathWhenNoSlash() {
        JarResourcePath path = JarResourcePath.parse("[mod.jar]simple_name_no_dot");
        assertEquals("simple_name_no_dot", path.extractPokemonName());
    }

    // ================================================================
    // toRawString (round-trip)
    // ================================================================

    @Test
    public void toRawString_shouldRoundTrip() {
        String original = "[cobblemon-fabric-1.0.jar]data/cobblemon/species/eevee.json";
        JarResourcePath path = JarResourcePath.parse(original);
        assertEquals(original, path.toRawString());
    }

    @Test
    public void toRawString_shouldMatchExpectedFormat() {
        JarResourcePath path = JarResourcePath.parse("[test.jar]path/file.json");
        assertEquals("[test.jar]path/file.json", path.toRawString());
    }

    // ================================================================
    // equals / hashCode
    // ================================================================

    @Test
    public void equals_shouldReturnTrueForSameJarAndPath() {
        JarResourcePath a = JarResourcePath.parse("[jar.jar]data/file.json");
        JarResourcePath b = JarResourcePath.parse("[jar.jar]data/file.json");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equals_shouldReturnFalseForDifferentJar() {
        JarResourcePath a = JarResourcePath.parse("[a.jar]data/file.json");
        JarResourcePath b = JarResourcePath.parse("[b.jar]data/file.json");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_shouldReturnFalseForDifferentPath() {
        JarResourcePath a = JarResourcePath.parse("[jar.jar]data/a.json");
        JarResourcePath b = JarResourcePath.parse("[jar.jar]data/b.json");
        assertNotEquals(a, b);
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    public void toString_shouldEqual_toRawString() {
        JarResourcePath path = JarResourcePath.parse("[test.jar]a/b.json");
        assertEquals(path.toRawString(), path.toString());
    }
}

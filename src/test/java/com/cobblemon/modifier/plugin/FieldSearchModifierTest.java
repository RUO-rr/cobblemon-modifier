package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.plugin.FieldSearchModifier.SearchField;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FieldSearchModifierTest {

    private static final String NIDORAN = "{\"nationalPokedexNumber\":32,\"name\":\"Nidoran-M\",\"abilities\":[\"poisonpoint\",\"rivalry\",\"h:hustle\"]}";

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static FieldSearchModifier modifier(SearchField field, String value) {
        FieldSearchModifier modifier = new FieldSearchModifier();
        modifier.setSearchField(field);
        modifier.setTargetValue(value);
        return modifier;
    }

    @Test
    public void pokedexNumberMatchesExactly() {
        JsonObject nidoran = json(NIDORAN);
        assertTrue(modifier(SearchField.POKEDEX_NUMBER, "32").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.POKEDEX_NUMBER, "3").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.POKEDEX_NUMBER, "132").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.POKEDEX_NUMBER, "abc").matchesSearchCriteria(nidoran));
    }

    @Test
    public void nameMatchesByContainedEnglish() {
        JsonObject nidoran = json(NIDORAN);
        assertTrue(modifier(SearchField.NAME, "nidoran").matchesSearchCriteria(nidoran));
        assertTrue(modifier(SearchField.NAME, "NIDORAN-M").matchesSearchCriteria(nidoran));
        assertTrue(modifier(SearchField.NAME, "nidoranm").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.NAME, "pikachu").matchesSearchCriteria(nidoran));
    }

    @Test
    public void abilityMatchesIncludingHiddenPrefix() {
        JsonObject nidoran = json(NIDORAN);
        assertTrue(modifier(SearchField.ABILITY, "poison").matchesSearchCriteria(nidoran));
        assertTrue(modifier(SearchField.ABILITY, "HUSTLE").matchesSearchCriteria(nidoran));
        assertTrue(modifier(SearchField.ABILITY, "poison point").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.ABILITY, "levitate").matchesSearchCriteria(nidoran));
    }

    @Test
    public void findMatchedAbilityStripsHiddenPrefix() {
        JsonObject nidoran = json(NIDORAN);
        assertEquals("hustle", modifier(SearchField.ABILITY, "hustle").findMatchedAbility(nidoran));
        assertEquals("poisonpoint", modifier(SearchField.ABILITY, "poison").findMatchedAbility(nidoran));
        assertNull(modifier(SearchField.ABILITY, "levitate").findMatchedAbility(nidoran));
    }

    @Test
    public void abilitySearchIncludesForms() {
        JsonObject json = json("{\"name\":\"Test\",\"abilities\":[\"overgrow\"],\"forms\":[{\"name\":\"Mega\",\"abilities\":[\"thickfat\"]}]}");
        assertTrue(modifier(SearchField.ABILITY, "thick").matchesSearchCriteria(json));
        assertEquals("thickfat", modifier(SearchField.ABILITY, "thick").findMatchedAbility(json));
    }

    @Test
    public void emptyOrNullTargetNeverMatches() {
        JsonObject nidoran = json(NIDORAN);
        assertFalse(modifier(SearchField.NAME, "").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.NAME, "   ").matchesSearchCriteria(nidoran));
        assertFalse(modifier(SearchField.NAME, "nidoran").matchesSearchCriteria(null));
    }
}

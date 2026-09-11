package com.cobblemon.modifier.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class PokemonJsonFilterTest {

    // ---- 物种本体 ----

    @Test
    public void isSpeciesData_shouldRequireBaseStats() {
        JsonObject json = new JsonObject();
        json.add("baseStats", new JsonObject());

        assertTrue(PokemonJsonFilter.isSpeciesData(json));
        assertFalse(PokemonJsonFilter.isSpeciesData(new JsonObject()));
        assertFalse(PokemonJsonFilter.isSpeciesData(null));
    }

    @Test
    public void isSpeciesData_shouldRejectAdditionEvenWithStats() {
        // 覆盖文件即使带了 baseStats，也不是"物种本体"
        JsonObject json = addition("moves");
        json.add("baseStats", new JsonObject());

        // 仍然算"可编辑的覆盖文件"，但不是本体
        assertTrue(PokemonJsonFilter.isEditableAddition(json));
    }

    // ---- 物种覆盖文件（species_additions）----

    @Test
    public void isEditableAddition_shouldAcceptMoves() {
        JsonObject json = addition("moves");
        json.add("moves", new JsonArray());

        assertTrue(PokemonJsonFilter.isEditableAddition(json));
    }

    @Test
    public void isEditableAddition_shouldRejectFieldsWeCannotEdit() {
        // 只改骑乘/掉落的覆盖文件，本工具改不动，不必列出来刷屏
        JsonObject ridingOnly = addition("riding");
        JsonObject dropsOnly = addition("drops");

        assertFalse(PokemonJsonFilter.isEditableAddition(ridingOnly));
        assertFalse(PokemonJsonFilter.isEditableAddition(dropsOnly));
    }

    @Test
    public void isEditableAddition_shouldRequireTarget() {
        JsonObject json = new JsonObject();
        json.add("moves", new JsonArray());

        assertFalse(PokemonJsonFilter.isEditableAddition(json));
    }

    @Test
    public void isEditableAddition_shouldRejectPlainSpeciesFile() {
        JsonObject species = new JsonObject();
        species.addProperty("name", "garchomp");
        species.add("baseStats", new JsonObject());
        species.add("moves", new JsonArray());

        assertFalse(PokemonJsonFilter.isEditableAddition(species));
        assertTrue(PokemonJsonFilter.isSpeciesData(species));
    }

    @Test
    public void isEditableAddition_shouldRejectNull() {
        assertFalse(PokemonJsonFilter.isEditableAddition(null));
    }

    private static JsonObject addition(String firstField) {
        JsonObject json = new JsonObject();
        json.addProperty("target", "cobblemon:garchomp");
        json.add(firstField, new JsonObject());
        return json;
    }
}

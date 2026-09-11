package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.model.SpawnEntry;
import com.cobblemon.modifier.service.SpawnRateService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpawnRateModifierTest {

    @Test
    public void parseJson_returnsMinimumWeight() {
        SpawnRateService service = mock(SpawnRateService.class);
        when(service.getEntries("Bulbasaur")).thenReturn(List.of(entry(6.0f), entry(2.5f)));
        SpawnRateModifier modifier = new SpawnRateModifier();
        modifier.setSpawnRateService(service);

        JsonObject species = JsonParser.parseString("{\"name\":\"Bulbasaur\"}").getAsJsonObject();

        assertEquals("2.5", modifier.parseJson(species).get("weight"));
    }

    @Test
    public void parseJson_returnsBucketWhenAllEntriesShareIt() {
        SpawnRateService service = mock(SpawnRateService.class);
        when(service.getEntries("Bulbasaur")).thenReturn(List.of(
            entry(6.0f, "ultra-rare"), entry(2.5f, "ultra-rare")));
        SpawnRateModifier modifier = new SpawnRateModifier();
        modifier.setSpawnRateService(service);

        JsonObject species = new JsonObject();
        species.addProperty("name", "Bulbasaur");

        assertEquals("ultra-rare", modifier.parseJson(species).get("bucket"));
    }

    @Test
    public void parseJson_returnsKeepBucketWhenBucketsAreMixed() {
        SpawnRateService service = mock(SpawnRateService.class);
        when(service.getEntries("Eevee")).thenReturn(List.of(
            entry(6.0f, "ultra-rare"), entry(2.5f, "uncommon")));
        SpawnRateModifier modifier = new SpawnRateModifier();
        modifier.setSpawnRateService(service);

        JsonObject species = new JsonObject();
        species.addProperty("name", "Eevee");

        assertEquals(SpawnRateModifier.KEEP_BUCKET, modifier.parseJson(species).get("bucket"));
    }

    @Test
    public void parseJson_returnsEmptyWhenNoEntries() {
        SpawnRateService service = mock(SpawnRateService.class);
        when(service.getEntries(anyString())).thenReturn(List.of());
        SpawnRateModifier modifier = new SpawnRateModifier();
        modifier.setSpawnRateService(service);

        JsonObject species = JsonParser.parseString("{\"name\":\"Mewtwo\"}").getAsJsonObject();

        assertEquals("", modifier.parseJson(species).get("weight"));
    }

    @Test
    public void parseJson_withoutServiceReturnsEmpty() {
        SpawnRateModifier modifier = new SpawnRateModifier();

        JsonObject species = JsonParser.parseString("{\"name\":\"Bulbasaur\"}").getAsJsonObject();

        assertEquals("", modifier.parseJson(species).get("weight"));
    }

    @Test
    public void convertFieldValue_parsesFloatAndKeepsInvalidText() {
        SpawnRateModifier modifier = new SpawnRateModifier();

        assertEquals(3.5f, modifier.convertFieldValue("weight", " 3.5 "));
        assertEquals("abc", modifier.convertFieldValue("weight", "abc"));
    }

    @Test
    public void metadata_isCorrect() {
        SpawnRateModifier modifier = new SpawnRateModifier();

        assertEquals("刷新率修改", modifier.getPluginName());
        assertEquals(List.of("bucket", "weight"), modifier.getFieldNames());
        assertEquals(List.of("保持不变", "common", "uncommon", "rare", "ultra-rare"),
            modifier.getFieldChoices("bucket"));
        assertTrue(modifier.getFieldChoices("weight").isEmpty());
        assertEquals(List.of("data/cobblemon/species"), modifier.getTargetJsonPaths());
        assertTrue(modifier.usesCustomSave());
        assertTrue(modifier.hasValidStatsFields(
            JsonParser.parseString("{\"name\":\"X\"}").getAsJsonObject()));
        assertFalse(modifier.hasValidStatsFields(new JsonObject()));
        assertEquals("6", SpawnRateModifier.formatWeight(6.0f));
        assertEquals("2.5", SpawnRateModifier.formatWeight(2.5f));
    }

    private static SpawnEntry entry(float weight) {
        return entry(weight, "common");
    }

    private static SpawnEntry entry(float weight, String bucket) {
        return new SpawnEntry("bulbasaur", "bulbasaur", "cobblemon.jar",
            "data/cobblemon/spawn_pool_world/0001_bulbasaur.json", 0, weight,
            bucket, "1-10", "plains");
    }
}

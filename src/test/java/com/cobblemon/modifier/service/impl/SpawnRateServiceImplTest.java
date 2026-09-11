package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.SpawnEntry;
import com.cobblemon.modifier.repository.impl.JarRepositoryImpl;
import com.cobblemon.modifier.repository.impl.OverrideRepositoryImpl;
import com.cobblemon.modifier.service.SpawnRateService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

public class SpawnRateServiceImplTest {

    private static final String SPAWN_PATH =
        "data/cobblemon/spawn_pool_world/0001_bulbasaur.json";

    private static final String SPAWN_JSON = "{"
        + "\"enabled\":true,\"spawns\":["
        + "{\"id\":\"bulbasaur-1\",\"pokemon\":\"bulbasaur\",\"bucket\":\"ultra-rare\","
        + "\"level\":\"5-32\",\"weight\":6.0,\"condition\":{\"biomes\":[\"#cobblemon:is_jungle\"]}},"
        + "{\"id\":\"bulbasaur-2\",\"pokemon\":\"bulbasaur\",\"bucket\":\"common\","
        + "\"level\":\"1-10\",\"weight\":2.5,\"condition\":{\"biomes\":[\"#minecraft:plains\"]}},"
        + "{\"id\":\"pikachu-1\",\"pokemon\":\"pikachu\",\"bucket\":\"common\","
        + "\"level\":\"1-10\",\"weight\":3.0,\"condition\":{\"biomes\":[\"#minecraft:forest\"]}}"
        + "]}";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File modsDir;
    private Path staging;
    private SpawnRateServiceImpl service;

    @Before
    public void setUp() throws Exception {
        modsDir = tempFolder.newFolder("mods");
        staging = tempFolder.newFolder("overrides").toPath();
        createJar(modsDir, "cobblemon.jar", SPAWN_PATH, SPAWN_JSON);
        service = new SpawnRateServiceImpl(
            new JarRepositoryImpl(), new OverrideRepositoryImpl(staging));
    }

    @Test
    public void ensureIndexed_buildsEntriesBySpecies() throws Exception {
        service.ensureIndexed(modsDir);

        assertTrue(service.isIndexed());
        List<SpawnEntry> entries = service.getEntries("Bulbasaur");
        assertEquals(2, entries.size());
        assertEquals("bulbasaur", entries.get(0).speciesKey());
        assertEquals(6.0f, entries.get(0).weight(), 0.001f);
        assertEquals(2.5f, entries.get(1).weight(), 0.001f);
        assertEquals("ultra-rare", entries.get(0).bucket());
        assertEquals(1, service.getEntries("pikachu").size());
    }

    @Test
    public void getEntries_unknownSpeciesReturnsEmpty() throws Exception {
        service.ensureIndexed(modsDir);
        assertTrue(service.getEntries("mewtwo").isEmpty());
        assertTrue(service.getEntries(null).isEmpty());
    }

    @Test
    public void applyWeight_updatesAllEntriesOfSpeciesAndWritesOverride() throws Exception {
        service.ensureIndexed(modsDir);

        int changed = service.applyWeight("bulbasaur", 99.0f, modsDir);

        assertEquals(2, changed);
        Path override = staging.resolve(SPAWN_PATH);
        assertTrue(Files.isRegularFile(override));
        JsonObject json = JsonParser.parseString(Files.readString(override)).getAsJsonObject();
        assertEquals(99.0f, json.getAsJsonArray("spawns").get(0).getAsJsonObject()
            .get("weight").getAsFloat(), 0.001f);
        assertEquals(99.0f, json.getAsJsonArray("spawns").get(1).getAsJsonObject()
            .get("weight").getAsFloat(), 0.001f);
        assertEquals(3.0f, json.getAsJsonArray("spawns").get(2).getAsJsonObject()
            .get("weight").getAsFloat(), 0.001f);

        List<SpawnEntry> entries = service.getEntries("bulbasaur");
        assertEquals(99.0f, entries.get(0).weight(), 0.001f);
        assertEquals(99.0f, entries.get(1).weight(), 0.001f);
    }

    @Test
    public void applyWeight_unknownSpeciesReturnsZero() throws Exception {
        service.ensureIndexed(modsDir);
        assertEquals(0, service.applyWeight("mewtwo", 50.0f, modsDir));
    }

    @Test
    public void applyBucket_movesAllEntriesAndWritesOverride() throws Exception {
        service.ensureIndexed(modsDir);

        int changed = service.applyBucket("bulbasaur", "uncommon", modsDir);

        assertEquals(2, changed);
        Path override = staging.resolve(SPAWN_PATH);
        JsonObject json = JsonParser.parseString(Files.readString(override)).getAsJsonObject();
        assertEquals("uncommon", json.getAsJsonArray("spawns").get(0).getAsJsonObject()
            .get("bucket").getAsString());
        assertEquals("uncommon", json.getAsJsonArray("spawns").get(1).getAsJsonObject()
            .get("bucket").getAsString());
        assertEquals("common", json.getAsJsonArray("spawns").get(2).getAsJsonObject()
            .get("bucket").getAsString());
        assertEquals("uncommon", service.getEntries("bulbasaur").get(0).bucket());
    }

    @Test
    public void applyChanges_updatesBucketAndWeightTogether() throws Exception {
        service.ensureIndexed(modsDir);

        int changed = service.applyChanges("bulbasaur", "rare", 77.0f, modsDir);

        assertEquals(2, changed);
        SpawnEntry first = service.getEntries("bulbasaur").get(0);
        assertEquals("rare", first.bucket());
        assertEquals(77.0f, first.weight(), 0.001f);
    }

    @Test
    public void applyChanges_withNoChangesReturnsZero() throws Exception {
        service.ensureIndexed(modsDir);
        assertEquals(0, service.applyChanges("bulbasaur", null, null, modsDir));
        assertFalse(Files.exists(staging.resolve(SPAWN_PATH)));
    }

    @Test
    public void restore_deletesOverrideAndRestoresOriginalWeights() throws Exception {
        service.ensureIndexed(modsDir);
        service.applyWeight("bulbasaur", 99.0f, modsDir);

        int deleted = service.restore("bulbasaur", modsDir);

        assertEquals(1, deleted);
        assertFalse(Files.exists(staging.resolve(SPAWN_PATH)));
        List<SpawnEntry> entries = service.getEntries("bulbasaur");
        assertEquals(6.0f, entries.get(0).weight(), 0.001f);
        assertEquals(2.5f, entries.get(1).weight(), 0.001f);
    }

    @Test
    public void ensureIndexed_prefersExistingOverride() throws Exception {
        JsonObject override = JsonParser.parseString(SPAWN_JSON).getAsJsonObject();
        override.getAsJsonArray("spawns").get(0).getAsJsonObject().addProperty("weight", 42.0f);
        new OverrideRepositoryImpl(staging).writeOverride(SPAWN_PATH, override);

        service.ensureIndexed(modsDir);

        List<SpawnEntry> entries = service.getEntries("bulbasaur");
        assertEquals(42.0f, entries.get(0).weight(), 0.001f);
    }

    @Test
    public void normalizeSpecies_handlesNamespaceFormsAndSpecialNames() {
        assertEquals("qwilfishhisuian", SpawnRateService.normalizeSpecies("Qwilfish Hisuian"));
        assertEquals("qwilfish", SpawnRateService.firstTokenKey("Qwilfish Hisuian"));
        assertEquals("bulbasaur", SpawnRateService.normalizeSpecies("Cobblemon:Bulbasaur"));
        assertEquals("nidoranf", SpawnRateService.normalizeSpecies("Nidoran♀"));
        assertEquals("nidoranm", SpawnRateService.normalizeSpecies("Nidoran♂"));
        assertEquals("mrrime", SpawnRateService.normalizeSpecies("Mr. Rime"));
        assertEquals("farfetchd", SpawnRateService.normalizeSpecies("Farfetch'd"));
        assertEquals("porygonz", SpawnRateService.normalizeSpecies("Porygon-Z"));
        assertEquals("", SpawnRateService.normalizeSpecies(null));
    }

    @Test
    public void ensureIndexed_indexesFormUnderBaseSpecies() throws Exception {
        File mods2 = tempFolder.newFolder("mods2");
        String json = "{\"spawns\":[{\"pokemon\":\"qwilfish hisuian\",\"weight\":4.0}]}";
        createJar(mods2, "cobblemon.jar",
            "data/cobblemon/spawn_pool_world/0002_qwilfish.json", json);
        SpawnRateServiceImpl formService = new SpawnRateServiceImpl(
            new JarRepositoryImpl(),
            new OverrideRepositoryImpl(tempFolder.newFolder("overrides2").toPath()));

        formService.ensureIndexed(mods2);

        assertEquals(1, formService.getEntries("Qwilfish").size());
        assertEquals(1, formService.getEntries("Qwilfish Hisuian").size());
    }

    private static void createJar(File dir, String jarName, String... pathContentPairs)
            throws Exception {
        File jarFile = new File(dir, jarName);
        try (FileOutputStream fos = new FileOutputStream(jarFile);
             JarOutputStream jos = new JarOutputStream(fos)) {
            for (int i = 0; i < pathContentPairs.length; i += 2) {
                jos.putNextEntry(new JarEntry(pathContentPairs[i]));
                jos.write(pathContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
    }
}

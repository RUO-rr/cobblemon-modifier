package com.cobblemon.modifier.repository.impl;

import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class OverrideRepositoryImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path staging;
    private OverrideRepositoryImpl repository;

    @Before
    public void setUp() throws Exception {
        staging = tempFolder.newFolder("overrides").toPath();
        repository = new OverrideRepositoryImpl(staging);
    }

    @Test
    public void writeThenReadRoundTrip() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 120);

        repository.writeOverride("data/cobblemon/species/generation1/nidoranm.json", json);
        JsonObject loaded = repository.readOverride(
            "data/cobblemon/species/generation1/nidoranm.json");

        assertNotNull(loaded);
        assertEquals(120, loaded.get("hp").getAsInt());
    }

    @Test
    public void readMissingOverrideReturnsNull() {
        assertNull(repository.readOverride("data/cobblemon/species/nope.json"));
    }

    @Test
    public void syncToDatapackCopiesFilesAndWritesPackMeta() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 99);
        repository.writeOverride("data/cobblemon/species/generation1/nidoranm.json", json);

        Path worldDatapacks = tempFolder.newFolder("world", "datapacks").toPath();
        repository.syncToDatapack(worldDatapacks);

        Path copied = worldDatapacks.resolve(
            "cobblemonmodifier/data/cobblemon/species/generation1/nidoranm.json");
        Path meta = worldDatapacks.resolve("cobblemonmodifier/pack.mcmeta");
        assertTrue(Files.isRegularFile(copied));
        assertTrue(Files.isRegularFile(meta));
        String metaText = Files.readString(meta, StandardCharsets.UTF_8);
        assertTrue(metaText.contains("pack_format"));
        assertTrue(metaText.contains("48"));
    }

    @Test
    public void syncRemovesStaleOverrides() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 1);
        repository.writeOverride("data/cobblemon/species/a.json", json);

        Path worldDatapacks = tempFolder.newFolder("world2", "datapacks").toPath();
        repository.syncToDatapack(worldDatapacks);
        Path stale = worldDatapacks.resolve("cobblemonmodifier/data/cobblemon/species/a.json");
        assertTrue(Files.isRegularFile(stale));

        Files.delete(staging.resolve("data/cobblemon/species/a.json"));
        repository.syncToDatapack(worldDatapacks);
        assertFalse(Files.exists(stale));
    }

    @Test
    public void pathTraversalIsRejected() {
        JsonObject json = new JsonObject();
        try {
            repository.writeOverride("../evil.json", json);
            fail("expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    @Test
    public void deleteOverride_removesFileAndCleansEmptyDirs() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("hp", 1);
        repository.writeOverride("data/cobblemon/species/generation1/nidoranm.json", json);

        boolean deleted = repository.deleteOverride(
            "data/cobblemon/species/generation1/nidoranm.json");

        assertTrue(deleted);
        assertFalse(Files.exists(
            staging.resolve("data/cobblemon/species/generation1/nidoranm.json")));
        assertFalse(Files.exists(staging.resolve("data")));
    }

    @Test
    public void deleteOverride_missingReturnsFalse() throws Exception {
        assertFalse(repository.deleteOverride("data/cobblemon/species/nope.json"));
        assertFalse(repository.deleteOverride("../evil.json"));
    }
}

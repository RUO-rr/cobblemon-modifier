package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.SpawnBucketConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SpawnConfigServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void load_missingFileReturnsDefaults() throws Exception {
        Path config = tempFolder.getRoot().toPath().resolve("missing.json");
        SpawnConfigServiceImpl service = new SpawnConfigServiceImpl(config);

        SpawnBucketConfig loaded = service.load();

        assertEquals(94.3f, loaded.weightOf("common"), 0.001f);
        assertEquals(0.2f, loaded.weightOf("ultra-rare"), 0.001f);
        assertEquals(1.0f, loaded.positionWeightOf("grounded"), 0.001f);
        assertTrue(loaded.replaceWithNewVersion());
    }

    @Test
    public void load_parsesBucketsPositionsAndFlag() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("replaceWithNewVersion", false);
        JsonObject positions = new JsonObject();
        positions.addProperty("grounded", 2.0);
        positions.addProperty("surface", 0.5);
        root.add("spawnablePositionTypeWeights", positions);
        JsonArray buckets = new JsonArray();
        buckets.add(bucket("common", 74));
        buckets.add(bucket("ultra-rare", 1));
        root.add("buckets", buckets);
        Path config = writeConfig(root);
        SpawnConfigServiceImpl service = new SpawnConfigServiceImpl(config);

        SpawnBucketConfig loaded = service.load();

        assertEquals(74.0f, loaded.weightOf("common"), 0.001f);
        assertEquals(1.0f, loaded.weightOf("ultra-rare"), 0.001f);
        assertEquals(0.0f, loaded.weightOf("rare"), 0.001f);
        assertEquals(2.0f, loaded.positionWeightOf("grounded"), 0.001f);
        assertEquals(0.5f, loaded.positionWeightOf("surface"), 0.001f);
        assertFalse(loaded.replaceWithNewVersion());
    }

    @Test
    public void save_updatesKnownBucketsAndPreservesUnknownFields() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("replaceWithNewVersion", true);
        root.addProperty("customField", "keep-me");
        JsonObject positions = new JsonObject();
        positions.addProperty("grounded", 1.0);
        root.add("spawnablePositionTypeWeights", positions);
        JsonArray buckets = new JsonArray();
        JsonObject common = bucket("common", 74);
        common.addProperty("note", "keep");
        buckets.add(common);
        buckets.add(bucket("uncommon", 20));
        root.add("buckets", buckets);
        Path config = writeConfig(root);
        SpawnConfigServiceImpl service = new SpawnConfigServiceImpl(config);

        Map<String, Float> newPositions = new LinkedHashMap<>();
        newPositions.put("grounded", 3.0f);
        newPositions.put("submerged", 1.0f);
        SpawnBucketConfig updated = new SpawnBucketConfig(newPositions, List.of(
            new SpawnBucketConfig.Bucket("common", 50.0f),
            new SpawnBucketConfig.Bucket("uncommon", 30.0f),
            new SpawnBucketConfig.Bucket("rare", 10.0f),
            new SpawnBucketConfig.Bucket("ultra-rare", 10.0f)
        ), true);

        service.save(updated);

        JsonObject saved = JsonParser.parseString(Files.readString(config, StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertEquals("keep-me", saved.get("customField").getAsString());
        assertEquals(1, saved.get("version").getAsInt());
        assertEquals(3.0f, saved.getAsJsonObject("spawnablePositionTypeWeights")
            .get("grounded").getAsFloat(), 0.001f);
        assertEquals(1.0f, saved.getAsJsonObject("spawnablePositionTypeWeights")
            .get("submerged").getAsFloat(), 0.001f);
        assertEquals(50.0f, bucketWeight(saved, "common"), 0.001f);
        assertEquals("keep", bucket(saved, "common").get("note").getAsString());
        assertEquals(10.0f, bucketWeight(saved, "rare"), 0.001f);
        assertEquals(10.0f, bucketWeight(saved, "ultra-rare"), 0.001f);
        assertFalse(Files.exists(config.resolveSibling(config.getFileName() + ".tmp")));
    }

    @Test
    public void save_rejectsNegativeAndZeroTotals() throws Exception {
        Path config = tempFolder.getRoot().toPath().resolve("config.json");
        SpawnConfigServiceImpl service = new SpawnConfigServiceImpl(config);

        SpawnBucketConfig negative = new SpawnBucketConfig(Map.of("grounded", 1.0f),
            List.of(new SpawnBucketConfig.Bucket("common", -1.0f)), true);
        try {
            service.save(negative);
            fail("negative weight should be rejected");
        } catch (IllegalArgumentException expected) {
            // 预期
        }

        SpawnBucketConfig zero = new SpawnBucketConfig(Map.of("grounded", 1.0f),
            List.of(new SpawnBucketConfig.Bucket("common", 0.0f)), true);
        try {
            service.save(zero);
            fail("zero total should be rejected");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void reloadBestSpawner_returnsFalseWhenCobblemonAbsent() {
        SpawnConfigServiceImpl service = new SpawnConfigServiceImpl(
            tempFolder.getRoot().toPath().resolve("config.json"));
        assertFalse(service.reloadBestSpawner());
    }

    private Path writeConfig(JsonObject root) throws Exception {
        Path file = tempFolder.getRoot().toPath().resolve("best-spawner-config.json");
        Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private static JsonObject bucket(String name, float weight) {
        JsonObject bucket = new JsonObject();
        bucket.addProperty("name", name);
        bucket.addProperty("weight", weight);
        return bucket;
    }

    private static JsonObject bucket(JsonObject root, String name) {
        for (var element : root.getAsJsonArray("buckets")) {
            JsonObject bucket = element.getAsJsonObject();
            if (bucket.get("name").getAsString().equals(name)) {
                return bucket;
            }
        }
        fail("bucket not found: " + name);
        return new JsonObject();
    }

    private static float bucketWeight(JsonObject root, String name) {
        return bucket(root, name).get("weight").getAsFloat();
    }
}

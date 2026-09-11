package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.SpawnBucketConfig;
import com.cobblemon.modifier.service.SpawnConfigService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 读写 config/cobblemon/spawning/best-spawner-config.json。
 *
 * <p>写回时只更新 buckets 与 spawnablePositionTypeWeights，保留 version、
 * replaceWithNewVersion 以及任何未知字段，避免破坏 Cobblemon 自身的配置。</p>
 */
public class SpawnConfigServiceImpl implements SpawnConfigService {

    private static final Logger log = LoggerFactory.getLogger(SpawnConfigServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BEST_SPAWNER_CLASS =
        "com.cobblemon.mod.common.api.spawning.BestSpawner";

    private final Path configPath;

    public SpawnConfigServiceImpl() {
        this(FabricLoader.getInstance().getConfigDir()
            .resolve("cobblemon")
            .resolve("spawning")
            .resolve("best-spawner-config.json"));
    }

    public SpawnConfigServiceImpl(Path configPath) {
        this.configPath = configPath.toAbsolutePath().normalize();
    }

    @Override
    public Path getConfigPath() {
        return configPath;
    }

    @Override
    public SpawnBucketConfig load() throws Exception {
        if (!Files.isRegularFile(configPath)) {
            log.warn("刷新配置文件不存在，返回默认值：{}", configPath);
            return SpawnBucketConfig.defaults();
        }
        JsonObject root = readRoot();
        Map<String, Float> positions = readPositions(root);
        List<SpawnBucketConfig.Bucket> buckets = readBuckets(root);
        if (buckets.isEmpty()) {
            buckets = SpawnBucketConfig.defaults().buckets();
        }
        boolean replace = !root.has("replaceWithNewVersion")
            || root.get("replaceWithNewVersion").getAsBoolean();
        return new SpawnBucketConfig(positions, buckets, replace);
    }

    @Override
    public void save(SpawnBucketConfig config) throws Exception {
        validate(config);
        JsonObject root = Files.isRegularFile(configPath) ? readRoot() : new JsonObject();
        if (!root.has("version")) {
            root.addProperty("version", 1);
        }
        if (!root.has("replaceWithNewVersion")) {
            root.addProperty("replaceWithNewVersion", config.replaceWithNewVersion());
        }

        JsonObject positions = childObject(root, "spawnablePositionTypeWeights");
        for (Map.Entry<String, Float> entry : config.positionTypeWeights().entrySet()) {
            positions.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("spawnablePositionTypeWeights", positions);

        JsonArray buckets = childArray(root, "buckets");
        Set<String> updated = new HashSet<>();
        for (JsonElement element : buckets) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject bucket = element.getAsJsonObject();
            if (!bucket.has("name")) {
                continue;
            }
            String name = bucket.get("name").getAsString();
            SpawnBucketConfig.Bucket configured = findBucket(config, name);
            if (configured != null) {
                bucket.addProperty("weight", configured.weight());
                updated.add(name);
            }
        }
        for (SpawnBucketConfig.Bucket bucket : config.buckets()) {
            if (updated.contains(bucket.name())) {
                continue;
            }
            JsonObject created = new JsonObject();
            created.addProperty("name", bucket.name());
            created.addProperty("weight", bucket.weight());
            buckets.add(created);
        }
        root.add("buckets", buckets);

        writeAtomically(root);
        log.info("已写入刷新配置：{}", configPath);
    }

    @Override
    public boolean reloadBestSpawner() {
        try {
            Class<?> type = Class.forName(BEST_SPAWNER_CLASS);
            Field instanceField = type.getField("INSTANCE");
            Object instance = instanceField.get(null);
            Method reload = type.getMethod("reloadConfig");
            reload.invoke(instance);
            log.info("Cobblemon BestSpawner 配置已热重载");
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("未找到 Cobblemon BestSpawner，无法热重载：{}", e.getMessage());
        } catch (ReflectiveOperationException e) {
            log.warn("调用 BestSpawner.reloadConfig 失败：{}", e.getMessage());
        }
        return false;
    }

    private JsonObject readRoot() throws IOException {
        String text = Files.readString(configPath, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) {
            throw new IOException("刷新配置根节点不是 JSON 对象：" + configPath);
        }
        return parsed.getAsJsonObject();
    }

    private static Map<String, Float> readPositions(JsonObject root) {
        Map<String, Float> positions = new LinkedHashMap<>();
        if (!root.has("spawnablePositionTypeWeights")
            || !root.get("spawnablePositionTypeWeights").isJsonObject()) {
            return positions;
        }
        for (Map.Entry<String, JsonElement> entry
                : root.getAsJsonObject("spawnablePositionTypeWeights").entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                positions.put(entry.getKey(), value.getAsFloat());
            }
        }
        return positions;
    }

    private static List<SpawnBucketConfig.Bucket> readBuckets(JsonObject root) {
        List<SpawnBucketConfig.Bucket> buckets = new ArrayList<>();
        if (!root.has("buckets") || !root.get("buckets").isJsonArray()) {
            return buckets;
        }
        for (JsonElement element : root.getAsJsonArray("buckets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject bucket = element.getAsJsonObject();
            if (!bucket.has("name") || !bucket.has("weight")) {
                continue;
            }
            try {
                buckets.add(new SpawnBucketConfig.Bucket(
                    bucket.get("name").getAsString(),
                    bucket.get("weight").getAsFloat()));
            } catch (RuntimeException ignored) {
                log.warn("忽略无法解析的稀有等级配置：{}", bucket);
            }
        }
        return buckets;
    }

    private static SpawnBucketConfig.Bucket findBucket(SpawnBucketConfig config, String name) {
        for (SpawnBucketConfig.Bucket bucket : config.buckets()) {
            if (bucket.name().equals(name)) {
                return bucket;
            }
        }
        return null;
    }

    private static JsonObject childObject(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonObject()) {
            return root.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private static JsonArray childArray(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonArray()) {
            return root.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    private void writeAtomically(JsonObject root) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, configPath,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validate(SpawnBucketConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("刷新配置不能为空");
        }
        float bucketTotal = 0.0f;
        for (SpawnBucketConfig.Bucket bucket : config.buckets()) {
            if (bucket.name() == null || bucket.name().isBlank()) {
                throw new IllegalArgumentException("稀有等级名称不能为空");
            }
            if (!Float.isFinite(bucket.weight()) || bucket.weight() < 0.0f) {
                throw new IllegalArgumentException("稀有等级权重必须是大于等于 0 的数字：" + bucket.name());
            }
            bucketTotal += bucket.weight();
        }
        if (bucketTotal <= 0.0f) {
            throw new IllegalArgumentException("至少一个稀有等级权重要大于 0");
        }
        for (Map.Entry<String, Float> entry : config.positionTypeWeights().entrySet()) {
            Float weight = entry.getValue();
            if (weight == null || !Float.isFinite(weight) || weight < 0.0f) {
                throw new IllegalArgumentException("生成位置权重必须是大于等于 0 的数字：" + entry.getKey());
            }
        }
    }
}

package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.SpawnEntry;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.repository.OverrideRepository;
import com.cobblemon.modifier.service.SpawnRateService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刷新率服务实现。
 *
 * <p>索引来自所有模组 JAR 中的 data/cobblemon/spawn_pool_world/*.json；
 * 若某文件已有覆盖文件，则优先读取覆盖文件，保证界面显示的是当前生效值。</p>
 */
public class SpawnRateServiceImpl implements SpawnRateService {

    private static final Logger log = LoggerFactory.getLogger(SpawnRateServiceImpl.class);
    private static final String SPAWN_PATH = "data/cobblemon/spawn_pool_world";

    private final JarRepository jarRepo;
    private final OverrideRepository overrideRepo;
    private final Map<String, List<SpawnEntry>> index = new ConcurrentHashMap<>();
    private volatile File indexedFolder;

    public SpawnRateServiceImpl(JarRepository jarRepo, OverrideRepository overrideRepo) {
        this.jarRepo = jarRepo;
        this.overrideRepo = overrideRepo;
    }

    @Override
    public synchronized void ensureIndexed(File modsFolder) throws Exception {
        if (modsFolder == null || !modsFolder.isDirectory()) {
            return;
        }
        if (modsFolder.equals(indexedFolder) && !index.isEmpty()) {
            return;
        }

        Map<String, List<SpawnEntry>> built = new HashMap<>();
        File[] jars = modsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                List<String> paths;
                try {
                    paths = jarRepo.listJsonFiles(jar, List.of(SPAWN_PATH));
                } catch (Exception e) {
                    log.warn("读取刷新池失败：{} - {}", jar.getName(), e.getMessage());
                    continue;
                }
                for (String path : paths) {
                    JsonObject json = readEffective(jar, path);
                    if (json != null) {
                        parseEntries(json, jar.getName(), path, built);
                    }
                }
            }
        }

        index.clear();
        for (Map.Entry<String, List<SpawnEntry>> entry : built.entrySet()) {
            index.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        indexedFolder = modsFolder;

        int total = index.values().stream().mapToInt(List::size).sum();
        log.info("刷新池索引完成：{} 个物种 / {} 条刷新条目", index.size(), total);
    }

    @Override
    public boolean isIndexed() {
        return !index.isEmpty();
    }

    @Override
    public List<SpawnEntry> getEntries(String speciesName) {
        String key = SpawnRateService.normalizeSpecies(speciesName);
        if (key.isEmpty()) {
            return List.of();
        }
        return index.getOrDefault(key, List.of());
    }

    @Override
    public int applyChanges(String speciesName, String bucket, Float weight, File modsFolder)
            throws Exception {
        if (bucket == null && weight == null) {
            return 0;
        }
        String key = SpawnRateService.normalizeSpecies(speciesName);
        List<SpawnEntry> entries = getEntries(key);
        if (entries.isEmpty()) {
            return 0;
        }
        if (bucket != null && bucket.isBlank()) {
            throw new IllegalArgumentException("稀有等级不能为空");
        }
        if (weight != null && (!Float.isFinite(weight) || weight <= 0.0f)) {
            throw new IllegalArgumentException("权重必须是大于 0 的数字");
        }

        Map<String, List<SpawnEntry>> byFile = new LinkedHashMap<>();
        for (SpawnEntry entry : entries) {
            byFile.computeIfAbsent(entry.jarName() + "|" + entry.filePath(),
                ignored -> new ArrayList<>()).add(entry);
        }

        int changed = 0;
        for (List<SpawnEntry> fileEntries : byFile.values()) {
            SpawnEntry first = fileEntries.get(0);
            JsonObject json = readEffective(new File(modsFolder, first.jarName()), first.filePath());
            if (json == null || !json.has("spawns") || !json.get("spawns").isJsonArray()) {
                continue;
            }
            JsonArray spawns = json.getAsJsonArray("spawns");
            for (SpawnEntry entry : fileEntries) {
                if (entry.entryIndex() >= 0 && entry.entryIndex() < spawns.size()
                    && spawns.get(entry.entryIndex()).isJsonObject()) {
                    JsonObject spawn = spawns.get(entry.entryIndex()).getAsJsonObject();
                    if (bucket != null) {
                        spawn.addProperty("bucket", bucket);
                    }
                    if (weight != null) {
                        spawn.addProperty("weight", weight);
                    }
                    changed++;
                }
            }
            overrideRepo.writeOverride(first.filePath(), json);
        }

        reindexSpecies(key, entries, modsFolder);
        return changed;
    }

    @Override
    public int restore(String speciesName, File modsFolder) throws Exception {
        String key = SpawnRateService.normalizeSpecies(speciesName);
        List<SpawnEntry> entries = getEntries(key);
        if (entries.isEmpty()) {
            return 0;
        }

        Set<String> files = new LinkedHashSet<>();
        for (SpawnEntry entry : entries) {
            files.add(entry.filePath());
        }
        int deleted = 0;
        for (String file : files) {
            if (overrideRepo.deleteOverride(file)) {
                deleted++;
            }
        }
        reindexSpecies(key, entries, modsFolder);
        return deleted;
    }

    /**
     * 修改/还原后只重建该物种的索引，避免整目录重扫。
     */
    private void reindexSpecies(String key, List<SpawnEntry> entries, File modsFolder) {
        if (modsFolder == null) {
            return;
        }
        Map<String, List<SpawnEntry>> rebuilt = new HashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SpawnEntry entry : entries) {
            String fileKey = entry.jarName() + "|" + entry.filePath();
            if (!seen.add(fileKey)) {
                continue;
            }
            JsonObject json = readEffective(new File(modsFolder, entry.jarName()), entry.filePath());
            if (json != null) {
                parseEntries(json, entry.jarName(), entry.filePath(), rebuilt);
            }
        }
        index.put(key, List.copyOf(rebuilt.getOrDefault(key, List.of())));
    }

    private JsonObject readEffective(File jar, String path) {
        JsonObject override = overrideRepo.readOverride(path);
        if (override != null) {
            return override;
        }
        try {
            return jarRepo.readJson(jar, path);
        } catch (Exception e) {
            return null;
        }
    }

    private static void parseEntries(JsonObject json, String jarName, String path,
                                     Map<String, List<SpawnEntry>> out) {
        if (json == null || !json.has("spawns") || !json.get("spawns").isJsonArray()) {
            return;
        }
        JsonArray spawns = json.getAsJsonArray("spawns");
        for (int i = 0; i < spawns.size(); i++) {
            JsonElement element = spawns.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject spawn = element.getAsJsonObject();
            String pokemon = stringOf(spawn, "pokemon");
            if (pokemon.isBlank()) {
                continue;
            }
            String key = SpawnRateService.normalizeSpecies(pokemon);
            if (key.isEmpty()) {
                continue;
            }
            float weight = spawn.has("weight") && spawn.get("weight").isJsonPrimitive()
                ? spawn.get("weight").getAsFloat() : 1.0f;
            SpawnEntry entry = new SpawnEntry(
                pokemon, key, jarName, path, i, weight,
                stringOf(spawn, "bucket"), stringOf(spawn, "level"),
                summarizeCondition(spawn.get("condition")));
            out.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
            String firstKey = SpawnRateService.firstTokenKey(pokemon);
            if (!firstKey.isEmpty() && !firstKey.equals(key)) {
                out.computeIfAbsent(firstKey, ignored -> new ArrayList<>()).add(entry);
            }
        }
    }

    private static String stringOf(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static String summarizeCondition(JsonElement condition) {
        if (condition == null || !condition.isJsonObject()) {
            return "";
        }
        JsonObject obj = condition.getAsJsonObject();
        if (!obj.has("biomes") || !obj.get("biomes").isJsonArray()) {
            return "";
        }
        JsonArray biomes = obj.getAsJsonArray("biomes");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < biomes.size() && i < 3; i++) {
            names.add(biomes.get(i).getAsString().replace("#", ""));
        }
        if (names.isEmpty()) {
            return "";
        }
        String summary = String.join(", ", names);
        if (biomes.size() > 3) {
            summary += " 等" + biomes.size() + "个群系";
        }
        return summary;
    }
}

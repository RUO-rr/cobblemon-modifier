package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.repository.DataSourceLocator;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.service.SpeciesOverrideIndex;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 扫描所有数据源，建立 {@code 物种标识符 → 本体文件 / 覆盖文件列表} 的索引（结果缓存）。
 */
public class SpeciesOverrideIndexImpl implements SpeciesOverrideIndex {

    private static final Logger log = LoggerFactory.getLogger(SpeciesOverrideIndexImpl.class);

    private final JarRepository jarRepo;

    private volatile File indexedFolder;
    private volatile Map<String, JarResourcePath> speciesFiles = Map.of();
    private volatile Map<String, List<JarResourcePath>> additions = Map.of();

    public SpeciesOverrideIndexImpl(JarRepository jarRepo) {
        this.jarRepo = jarRepo;
    }

    @Override
    public List<JarResourcePath> otherSourcesOf(File modsFolder, String speciesId, String exceptJsonPath) {
        if (modsFolder == null || speciesId == null || speciesId.isBlank()) {
            return List.of();
        }
        ensureIndexed(modsFolder);

        List<JarResourcePath> result = new ArrayList<>();
        JarResourcePath species = speciesFiles.get(speciesId.toLowerCase(Locale.ROOT));
        if (species != null && !species.jsonPath().equals(exceptJsonPath)) {
            result.add(species);
        }
        for (JarResourcePath addition
                : additions.getOrDefault(speciesId.toLowerCase(Locale.ROOT), List.of())) {
            if (!addition.jsonPath().equals(exceptJsonPath)) {
                result.add(addition);
            }
        }
        return result;
    }

    private synchronized void ensureIndexed(File modsFolder) {
        if (modsFolder.equals(indexedFolder) && !additions.isEmpty()) {
            return;
        }
        long startedAt = System.currentTimeMillis();

        Map<String, JarResourcePath> speciesIndex = new LinkedHashMap<>();
        // 同一个 zip 在 resourcepacks 与 global_packs 各有一份，按 JSON 路径去重，
        // 否则扇出会对同一份覆盖文件写两次。
        Map<String, Map<String, JarResourcePath>> additionIndex = new HashMap<>();

        for (File root : DataSourceLocator.roots(modsFolder)) {
            for (File archive : DataSourceLocator.archives(root, modsFolder)) {
                List<String> candidates;
                try {
                    candidates = jarRepo.listEntries(archive, path -> path.endsWith(".json")
                        && (path.contains("/species/") || path.contains("/species_additions/")));
                } catch (Exception e) {
                    continue;
                }
                Map<String, JsonObject> jsons;
                try {
                    jsons = jarRepo.readJsonBatch(archive, candidates);
                } catch (Exception e) {
                    continue;
                }
                for (String path : candidates) {
                    JsonObject json = jsons.get(path);
                    if (json == null) {
                        continue;
                    }
                    if (path.contains("/species_additions/")) {
                        String target = JsonUtil.getString(json, "target");
                        if (target != null && !target.isBlank()) {
                            additionIndex
                                .computeIfAbsent(target.toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>())
                                .putIfAbsent(path, JarResourcePath.of(archive.getName(), path));
                        }
                    } else {
                        String id = SpeciesOverrideIndex.speciesIdOfPath(path);
                        if (id != null) {
                            // 高优先级来源先到，先到者胜
                            speciesIndex.putIfAbsent(id, JarResourcePath.of(archive.getName(), path));
                        }
                    }
                }
            }
        }

        Map<String, List<JarResourcePath>> frozen = new HashMap<>();
        additionIndex.forEach((k, v) -> frozen.put(k, List.copyOf(v.values())));

        this.speciesFiles = Collections.unmodifiableMap(speciesIndex);
        this.additions = Collections.unmodifiableMap(frozen);
        this.indexedFolder = modsFolder;
        log.info("物种来源索引完成：{} 个本体 / {} 个物种有覆盖文件（耗时 {} ms）",
            speciesIndex.size(), frozen.size(), System.currentTimeMillis() - startedAt);
    }
}

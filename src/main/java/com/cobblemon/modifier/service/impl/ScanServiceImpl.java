package com.cobblemon.modifier.service.impl;

import com.google.gson.JsonObject;
import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.repository.DataSourceLocator;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.service.ScanService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * JAR 扫描与 JSON 验证服务实现。
 * 从原 MainFrame 中提取的扫描/过滤/验证逻辑。
 */
public class ScanServiceImpl implements ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanServiceImpl.class);

    private final JarRepository jarRepo;

    /**
     * 宝可梦数据的路径规则：{@code data/<命名空间>/species/**} 与
     * {@code data/<命名空间>/species_additions/**}。
     *
     * <p>**命名空间必须放开**：整合包与魔改模组会把物种数据放进自己的命名空间，
     * 例如 mushiromega 的 Mega-Z / Mega-M 形态就在
     * {@code data/newsmega/species_additions/generation1/charizard.json}，
     * ZA 的超进化在 {@code data/cobblemon/species_additions/...}。
     * 以前只收 {@code data/cobblemon/...}，于是"喷火龙进化石Z / 烈咬陆鲨M"这类
     * 魔改形态根本不出现在列表里，自然也就改不了。
     *
     * <p>同时用目录位置（命名空间后紧接 species）排除了
     * {@code data/<命名空间>/rewards/species/...}、{@code data/cobblemon/dex_entries/...}
     * 这类"名字里有 species 但不是物种数据"的条目。
     */
    private static final Pattern POKEMON_DATA_PATH =
        Pattern.compile("^data/[^/]+/(?:species|species_additions)/.+");

    public ScanServiceImpl(JarRepository jarRepo) {
        this.jarRepo = jarRepo;
    }

    @Override
    public ScanResult scanAndValidate(File folder,
                                      Predicate<String> jarFilter,
                                      Predicate<JsonObject> validator,
                                      ProgressCallback onProgress) throws Exception {
        // 第一步：收集候选，并按"所属压缩包"分组
        Map<File, List<String>> candidates = gatherCandidates(folder, jarFilter, onProgress);
        int totalCount = candidates.values().stream().mapToInt(List::size).sum();

        // 第二步：逐个压缩包批量读取 + 校验
        // （一个包只打开一次，这是"加载宝可梦数据"耗时的大头）
        List<JarResourcePath> validPaths = new ArrayList<>();
        int skipped = 0;
        int processed = 0;

        for (Map.Entry<File, List<String>> entry : candidates.entrySet()) {
            File archive = entry.getKey();
            List<String> jsonPaths = entry.getValue();

            Map<String, JsonObject> jsons;
            try {
                jsons = jarRepo.readJsonBatch(archive, jsonPaths);
            } catch (Exception e) {
                log.warn("Failed to read archive: {} - {}", archive.getName(), e.getMessage());
                jsons = Map.of();
            }

            for (String jsonPath : jsonPaths) {
                processed++;
                JsonObject json = jsons.get(jsonPath);
                if (json != null && validator.test(json)) {
                    validPaths.add(JarResourcePath.of(archive.getName(), jsonPath));
                } else {
                    skipped++;
                }
                onProgress.onProgress(processed, totalCount,
                    String.format("validated:%d valid:%d", processed, validPaths.size()));
            }
        }

        return new ScanResult(validPaths, totalCount, skipped);
    }

    /**
     * 收集所有候选文件，按所属压缩包分组。
     *
     * <p>依次扫描各个数据源（数据包优先，mods 最后），并对同一个 JSON 路径去重：
     * 同一份数据在 resourcepacks 与 global_packs 里各存了一份，去重后列表里只留
     * 优先级最高的那条，避免列表里出现两条一模一样的宝可梦。
     */
    private Map<File, List<String>> gatherCandidates(File folder,
                                                     Predicate<String> jarFilter,
                                                     ProgressCallback onProgress) throws Exception {
        Map<File, List<String>> byArchive = new LinkedHashMap<>();
        if (!folder.exists() || !folder.isDirectory()) {
            return byArchive;
        }

        long startedAt = System.currentTimeMillis();
        log.info("开始收集候选文件：目录 {}", folder);

        // 已经收录过的 JSON 路径；先到的是高优先级来源，后到的直接跳过
        Map<String, Boolean> seenJsonPaths = new LinkedHashMap<>();
        // 同一个 zip 在 resourcepacks 与 global_packs 各有一份，按文件名跳过重复的那份
        Set<String> seenArchiveNames = new HashSet<>();
        for (File root : DataSourceLocator.roots(folder)) {
            boolean isModsRoot = root.equals(folder);
            int archivesInRoot = 0;
            for (File archive : DataSourceLocator.archives(root, folder)) {
                // JAR 名称过滤器只作用于 mods 目录；数据包里的魔改宝可梦全部收下
                if (isModsRoot && jarFilter != null && !jarFilter.test(archive.getName())) {
                    continue;
                }
                if (!seenArchiveNames.add(archive.getName())) {
                    // 已经处理过同名的压缩包（高优先级那份），跳过
                    continue;
                }
                archivesInRoot++;
                if (onProgress != null) {
                    onProgress.onProgress(0, 0, "收集候选：" + archive.getName());
                }
                long archiveStart = System.currentTimeMillis();
                List<String> jsonFiles;
                try {
                    // 按路径规则列出（任何命名空间），不再依赖调用方传进来的前缀
                    jsonFiles = jarRepo.listEntries(archive, ScanServiceImpl::isPokemonDataPath);
                } catch (Exception e) {
                    log.warn("Failed to read archive: {} - {}", archive.getName(), e.getMessage());
                    continue;
                }

                List<String> kept = new ArrayList<>();
                for (String jsonPath : jsonFiles) {
                    if (seenJsonPaths.putIfAbsent(jsonPath, Boolean.TRUE) == null) {
                        kept.add(jsonPath);
                    }
                }
                if (!kept.isEmpty()) {
                    byArchive.put(archive, kept);
                }
                long archiveMs = System.currentTimeMillis() - archiveStart;
                if (archiveMs > 1500) {
                    log.warn("压缩包 {} 列出条目耗时 {} ms（候选 {} 个）",
                        archive.getName(), archiveMs, kept.size());
                }
            }
            log.info("数据源 {}：扫描 {} 个压缩包（累计候选 {} 个，已用 {} ms）",
                root, archivesInRoot, byArchive.values().stream().mapToInt(List::size).sum(),
                System.currentTimeMillis() - startedAt);
        }
        log.info("候选文件收集完成：{} 个压缩包 / {} 条候选，耗时 {} ms",
            byArchive.size(), byArchive.values().stream().mapToInt(List::size).sum(),
            System.currentTimeMillis() - startedAt);
        return byArchive;
    }

    /**
     * 是否是宝可梦数据：
     * <ul>
     *   <li>{@code data/<命名空间>/species/**} —— 物种本体；</li>
     *   <li>{@code data/<命名空间>/species_additions/**} —— 整合包/魔改模组用于整体覆盖物种字段的文件
     *       （实测 4693 个，其中 2082 个覆盖了招式表）。</li>
     * </ul>
     *
     * <p>命名空间不限定为 {@code cobblemon}，理由见 {@link #POKEMON_DATA_PATH}。
     */
    static boolean isPokemonDataPath(String path) {
        return path != null && path.endsWith(".json") && POKEMON_DATA_PATH.matcher(path).matches();
    }
}

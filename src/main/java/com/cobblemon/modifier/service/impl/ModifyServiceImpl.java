package com.cobblemon.modifier.service.impl;

import com.google.gson.JsonObject;
import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.repository.DataSourceLocator;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.repository.OverrideRepository;
import com.cobblemon.modifier.service.ModifyService;
import com.cobblemon.modifier.service.SpeciesOverrideIndex;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON 修改服务实现。
 *
 * <p>读取时优先返回覆盖文件；保存时写入覆盖文件而不是模组 JAR，
 * 避免游戏运行时改写正在使用的 JAR。
 */
public class ModifyServiceImpl implements ModifyService {

    private final JarRepository jarRepo;
    private final OverrideRepository overrideRepo;
    private final SpeciesOverrideIndex overrideIndex;

    private static final Logger log = LoggerFactory.getLogger(ModifyServiceImpl.class);

    public ModifyServiceImpl(JarRepository jarRepo, OverrideRepository overrideRepo) {
        this(jarRepo, overrideRepo, null);
    }

    public ModifyServiceImpl(JarRepository jarRepo, OverrideRepository overrideRepo,
                             SpeciesOverrideIndex overrideIndex) {
        this.jarRepo = jarRepo;
        this.overrideRepo = overrideRepo;
        this.overrideIndex = overrideIndex;
    }

    @Override
    public Map<String, Object> readAndParse(JarResourcePath path, File baseFolder,
                                             JsonModifier plugin) throws Exception {
        JsonObject json = readRaw(path, baseFolder);
        return plugin.parseJson(json);
    }

    @Override
    public JsonObject readRaw(JarResourcePath path, File baseFolder) throws Exception {
        JsonObject override = overrideRepo.readOverride(path.jsonPath());
        if (override != null) {
            return override;
        }
        // 数据包（resourcepacks / global_packs）与 mods 里的文件同名时取优先级最高的那份
        File targetJar = DataSourceLocator.resolve(baseFolder, path.jarName());
        if (targetJar == null) {
            throw new FileNotFoundException("找不到数据源文件: " + path.jarName());
        }
        return jarRepo.readJson(targetJar, path.jsonPath());
    }

    @Override
    public int modifyAndSave(JarResourcePath path, File baseFolder,
                             JsonModifier plugin, Map<String, Object> newValues) throws Exception {
        JsonObject original = readRaw(path, baseFolder);
        JsonObject modified = plugin.modifyJson(original, newValues);
        overrideRepo.writeOverride(path.jsonPath(), modified);
        return syncToOtherSources(path, baseFolder, original, modified);
    }

    @Override
    public int restoreOverrides(JarResourcePath path, File baseFolder) throws Exception {
        // 先解析物种标识符，再删文件：读取时会优先拿覆盖文件，两种来源的 target 是一致的
        String speciesId = null;
        try {
            speciesId = resolveSpeciesId(path, readRaw(path, baseFolder));
        } catch (Exception e) {
            speciesId = SpeciesOverrideIndex.speciesIdOfPath(path.jsonPath());
        }

        int removed = 0;
        if (overrideRepo.deleteOverride(path.jsonPath())) {
            removed++;
        }
        if (overrideIndex == null || speciesId == null) {
            return removed;
        }

        // 扇出写过的其它来源文件也要一起清掉，否则会留下"半还原"状态
        for (JarResourcePath other
                : overrideIndex.otherSourcesOf(baseFolder, speciesId, path.jsonPath())) {
            try {
                if (overrideRepo.deleteOverride(other.jsonPath())) {
                    removed++;
                }
            } catch (Exception e) {
                log.warn("删除来源覆盖失败：{} - {}", other.jsonPath(), e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("还原物种 {} 的原版数据：删除 {} 个覆盖文件", speciesId, removed);
        }
        return removed;
    }

    // ================================================================
    // B1 扇出：把改动过的字段同步到该物种的其它来源文件
    // ================================================================

    /**
     * Cobblemon 应用 {@code species_additions} 的顺序是 HashMap 顺序（与数据包优先级无关），
     * 同一字段被多个覆盖文件接管时谁生效不可预测（实测 1025 个物种的招式表都是这种状态）。
     * 所以这里把改动过的字段写进该物种的**所有**来源文件，无论谁最后生效都是用户的值。
     *
     * @return 实际同步的文件数
     */
    private int syncToOtherSources(JarResourcePath path, File baseFolder,
                                   JsonObject original, JsonObject modified) {
        if (overrideIndex == null) {
            return 0;
        }
        Map<String, JsonElement> changed = changedFields(original, modified);
        if (changed.isEmpty()) {
            return 0;
        }
        String speciesId = resolveSpeciesId(path, modified);
        if (speciesId == null) {
            return 0;
        }

        int synced = 0;
        for (JarResourcePath other
                : overrideIndex.otherSourcesOf(baseFolder, speciesId, path.jsonPath())) {
            JsonObject target;
            try {
                target = readRaw(other, baseFolder);
            } catch (Exception e) {
                log.warn("读取来源文件失败：{} - {}", other.jsonPath(), e.getMessage());
                continue;
            }
            boolean touched = false;
            for (Map.Entry<String, JsonElement> entry : changed.entrySet()) {
                touched |= applyField(target, entry.getKey(), entry.getValue());
            }
            if (touched) {
                try {
                    overrideRepo.writeOverride(other.jsonPath(), target);
                    synced++;
                } catch (Exception e) {
                    log.warn("同步来源文件失败：{} - {}", other.jsonPath(), e.getMessage());
                }
            }
        }
        if (synced > 0) {
            log.info("已把 {} 个字段同步到 {} 个其它来源文件（物种 {}）",
                changed.keySet(), synced, speciesId);
        }
        return synced;
    }

    /** 顶层字段里值发生过变化的那些。 */
    private static Map<String, JsonElement> changedFields(JsonObject original, JsonObject modified) {
        Map<String, JsonElement> changed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : modified.entrySet()) {
            JsonElement before = original.get(entry.getKey());
            if (!Objects.equals(before, entry.getValue())) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        return changed;
    }

    /** 覆盖文件取自身 target；物种本体按路径推标识符（cobblemon:garchomp）。 */
    private static String resolveSpeciesId(JarResourcePath path, JsonObject json) {
        String target = JsonUtil.getString(json, "target");
        if (target != null && !target.isBlank()) {
            return target.toLowerCase(Locale.ROOT);
        }
        return SpeciesOverrideIndex.speciesIdOfPath(path.jsonPath());
    }

    /**
     * 把单个字段写进目标文件。
     *
     * <p>{@code forms} 单独处理：按形态名逐个替换，保留目标文件里多出来的形态。
     * 否则会把别的模组加的形态（例如 ZA 的超进化形态）整段删掉。
     *
     * @return 是否真的改动了目标文件
     */
    private static boolean applyField(JsonObject target, String field, JsonElement value) {
        if ("forms".equals(field) && target.has("forms") && target.get("forms").isJsonArray()
            && value != null && value.isJsonArray()) {
            return mergeForms(target.getAsJsonArray("forms"), value.getAsJsonArray());
        }
        if (!target.has(field) || Objects.equals(target.get(field), value)) {
            // 目标文件本来不接管这个字段，或者值已经一样
            return false;
        }
        target.add(field, value.deepCopy());
        return true;
    }

    private static boolean mergeForms(JsonArray existing, JsonArray newForms) {
        boolean changed = false;
        for (JsonElement element : newForms) {
            if (!element.isJsonObject()) {
                continue;
            }
            String name = JsonUtil.getString(element.getAsJsonObject(), "name");
            if (name == null) {
                continue;
            }
            for (int i = 0; i < existing.size(); i++) {
                JsonElement current = existing.get(i);
                if (!current.isJsonObject()) {
                    continue;
                }
                if (name.equals(JsonUtil.getString(current.getAsJsonObject(), "name"))) {
                    if (!Objects.equals(current, element)) {
                        existing.set(i, element.deepCopy());
                        changed = true;
                    }
                    break;
                }
            }
        }
        return changed;
    }
}

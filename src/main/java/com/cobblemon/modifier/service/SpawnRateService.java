package com.cobblemon.modifier.service;

import com.cobblemon.modifier.model.SpawnEntry;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 刷新率服务 —— 把 Cobblemon 的 spawn_pool_world 数据聚合成"物种 -> 刷新条目"的索引，
 * 并支持按物种统一修改权重、还原原版。
 */
public interface SpawnRateService {

    /** 扫描 mods 目录并建立索引（同一目录只建一次）。 */
    void ensureIndexed(File modsFolder) throws Exception;

    /** 索引是否已建立。 */
    boolean isIndexed();

    /** 获取某物种的全部刷新条目。 */
    List<SpawnEntry> getEntries(String speciesName);

    /**
     * 修改某物种所有刷新条目的稀有等级和/或权重，写入覆盖文件。
     *
     * @param bucket 新的稀有等级；null 表示不改动
     * @param weight 新的桶内权重；null 表示不改动
     * @return 实际修改的条目数
     */
    int applyChanges(String speciesName, String bucket, Float weight, File modsFolder)
        throws Exception;

    /** 只改权重（等价于 applyChanges(species, null, weight, folder)）。 */
    default int applyWeight(String speciesName, float weight, File modsFolder) throws Exception {
        return applyChanges(speciesName, null, weight, modsFolder);
    }

    /** 只改稀有等级（等价于 applyChanges(species, bucket, null, folder)）。 */
    default int applyBucket(String speciesName, String bucket, File modsFolder) throws Exception {
        return applyChanges(speciesName, bucket, null, modsFolder);
    }

    /**
     * 删除该物种所有刷新池覆盖文件，恢复原版。
     *
     * @return 实际删除的覆盖文件数
     */
    int restore(String speciesName, File modsFolder) throws Exception;

    /**
     * 物种键归一化：去命名空间、转小写、♀→f、♂→m、去掉所有非字母数字字符。
     * 例如 "Cobblemon:Nidoran♀" -> "nidoranf"，"Mr. Mime" -> "mrmime"，
     * "Farfetch'd" -> "farfetchd"。
     */
    static String normalizeSpecies(String pokemon) {
        if (pokemon == null) {
            return "";
        }
        String value = pokemon.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        value = value.replace('\u2640', 'f').replace('\u2642', 'm');
        return value.replaceAll("[^a-z0-9]", "");
    }

    /**
     * 取空格前主体再归一化，用于带形态后缀的刷新池值。
     * 例如 "qwilfish hisuian" -> "qwilfish"。
     */
    static String firstTokenKey(String pokemon) {
        if (pokemon == null) {
            return "";
        }
        String value = pokemon.trim();
        int space = value.indexOf(' ');
        if (space > 0) {
            value = value.substring(0, space);
        }
        return normalizeSpecies(value);
    }
}

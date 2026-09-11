package com.cobblemon.modifier.model;

/**
 * 一条刷新池条目 —— 对应 Cobblemon data/cobblemon/spawn_pool_world/*.json 里 spawns[] 的一个元素。
 *
 * @param spawnPokemon      原始 pokemon 字段，如 "bulbasaur"、"qwilfish hisuian"
 * @param speciesKey        归一化后的物种键（小写、取空格前主体），用于与物种 JSON 匹配
 * @param jarName           条目所在 JAR 文件名
 * @param filePath          条目所在刷新池 JSON 路径
 * @param entryIndex        在 spawns[] 数组中的下标
 * @param weight            生成权重
 * @param bucket            稀有度桶（common/uncommon/rare/ultra-rare）
 * @param level             等级范围
 * @param conditionSummary  条件摘要（群系等）
 */
public record SpawnEntry(
    String spawnPokemon,
    String speciesKey,
    String jarName,
    String filePath,
    int entryIndex,
    float weight,
    String bucket,
    String level,
    String conditionSummary
) {
}
package com.cobblemon.modifier.service;

import com.cobblemon.modifier.model.JarResourcePath;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物种来源索引 —— 回答"这个物种的数据一共有哪几份文件在管"。
 *
 * <p>整合包大量使用 {@code data/<命名空间>/species_additions/**} 来整体覆盖物种字段，
 * 而 Cobblemon 应用这些覆盖的顺序是 **HashMap 的迭代顺序**（不是数据包优先级），
 * 所以"同一字段被多个覆盖文件接管时谁生效"在游戏里本来就是不确定的。
 * 实测有 {@code 1025 个物种}的招式表被 ≥2 个覆盖文件同时接管。
 *
 * <p>既然顺序不可控，修改器的对策是**扇出写入**：改了某个字段，就把该字段
 * 同步进这个物种的**所有**来源文件（本体 + 每个覆盖文件），这样无论谁最后生效都是用户的值。
 */
public interface SpeciesOverrideIndex {

    /** 物种本体路径：data/&lt;ns&gt;/species/.../名字.json */
    Pattern SPECIES_PATH = Pattern.compile("^data/([^/]+)/species/(?:.+/)?([^/]+)\\.json$");

    /**
     * 返回该物种的其它来源文件（本体 + 覆盖文件），排除用户刚编辑的那一份。
     *
     * @param modsFolder     玩家选定的 mods 目录
     * @param speciesId      物种标识符，如 {@code cobblemon:garchomp}
     * @param exceptJsonPath 已被用户编辑、无需再同步的 JSON 路径
     */
    List<JarResourcePath> otherSourcesOf(File modsFolder, String speciesId, String exceptJsonPath);

    /**
     * 从 JSON 路径推出物种标识符：{@code data/cobblemon/species/generation4/garchomp.json}
     * → {@code cobblemon:garchomp}（与 species_additions 里 target 的写法一致）。
     *
     * @return 不是物种本体路径时返回 null
     */
    static String speciesIdOfPath(String jsonPath) {
        if (jsonPath == null) {
            return null;
        }
        Matcher matcher = SPECIES_PATH.matcher(jsonPath);
        if (!matcher.matches()) {
            return null;
        }
        return (matcher.group(1) + ":" + matcher.group(2)).toLowerCase(Locale.ROOT);
    }
}

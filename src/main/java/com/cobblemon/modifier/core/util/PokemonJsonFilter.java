package com.cobblemon.modifier.core.util;

import com.google.gson.JsonObject;

import java.util.Set;

/**
 * 判定一份 JSON 该不该出现在修改器的列表里。
 *
 * <p>整合包大量使用 {@code data/<命名空间>/species_additions/**} 来**整体覆盖**物种字段
 * （实测 4693 个文件、覆盖 1074 个物种，其中 {@code moves} 被覆盖 2082 次）。
 * 这类文件的优先级高于物种本体，不把它们列出来就会出现"改了本体却毫无效果"。
 *
 * <p>注意覆盖是"赋值"而不是"合并"：Cobblemon 的 {@code SpeciesAdditions$Addition}
 * 就是 {@code (Species 的某个可变属性, 新值)}，应用时直接 {@code species.属性 = 值}。
 * 所以地龙的招式表一旦被 {@code garchomp_move.json} 接管，改本体文件就没用了。
 */
public final class PokemonJsonFilter {

    /** 物种本体一定有的字段。 */
    private static final String BASE_STATS = "baseStats";

    /** species_additions 一定有、而物种本体一定没有的字段。 */
    private static final String TARGET = "target";

    /**
     * 本工具改得动的覆盖字段：招式 / 特性 / 种族值。
     * 只改骑乘、掉落、体型的那些覆盖文件收进来也没有意义，不列出来以免刷屏。
     */
    private static final Set<String> EDITABLE_ADDITION_FIELDS =
        Set.of("moves", "abilities", "baseStats");

    private PokemonJsonFilter() {
    }

    /** 是否是物种本体数据（带种族值）。 */
    public static boolean isSpeciesData(JsonObject json) {
        return json != null && json.has(BASE_STATS) && json.get(BASE_STATS).isJsonObject();
    }

    /** 是否是"物种覆盖文件"里本工具能编辑的那一类。 */
    public static boolean isEditableAddition(JsonObject json) {
        if (json == null || !json.has(TARGET) || !json.get(TARGET).isJsonPrimitive()) {
            return false;
        }
        for (String field : EDITABLE_ADDITION_FIELDS) {
            if (json.has(field)) {
                return true;
            }
        }
        return false;
    }
}

package com.cobblemon.modifier.service;

import com.cobblemon.modifier.model.MoveData;
import com.cobblemon.modifier.model.MoveInfo;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 招式数据（威力 / 命中 / PP / 优先级 / 属性）的读写服务。
 *
 * <p>与"技能修改"（改宝可梦**学得到哪些**招式）不同，这里改的是**招式本身**的数值：
 * 招式定义既可能来自 {@code showdown/data/moves.js}（基础 951 条），
 * 也可能来自数据包的 {@code data/cobblemon/moves/&lt;id&gt;.js}（自定义招式）。
 *
 * <p>两者都是脚本而不是 JSON，因此走"文本覆盖"通道：
 * 把改好的脚本写到 {@code config/cobblemonmodifier/overrides/data/cobblemon/moves/&lt;id&gt;.js}，
 * 再同步成世界数据包，由 Cobblemon 在下次进入世界时读取。
 */
public interface MoveDataService {

    /**
     * 搜索招式。
     *
     * @param modsFolder 玩家选定的目录（通常是 {@code <gameDir>/mods}），
     *                   用于定位整合包的数据包与模组
     * @param query      招式 id 或英文名的一部分，留空表示全部
     * @param limit      最多返回多少条（&lt;= 0 表示不限制）
     */
    List<MoveInfo> search(File modsFolder, String query, int limit);

    /** 招式总数（基础 + 自定义）。 */
    int totalCount(File modsFolder);

    /** 读取某个招式的当前数值（有覆盖时以覆盖为准）。 */
    MoveData load(MoveInfo info) throws Exception;

    /**
     * 保存修改：只把改动过的字段写进覆盖脚本。
     *
     * @return 变更说明，例如 {@code "威力 40 → 80；命中 100 → 120"}
     */
    String save(MoveInfo info, Map<String, String> values) throws Exception;

    /**
     * 删除该招式的覆盖文件（恢复原版）。
     *
     * @return 是否确实删除了覆盖
     */
    boolean restore(MoveInfo info) throws Exception;

    /** 该招式当前是否有我们的覆盖。 */
    boolean hasOverride(MoveInfo info);

    /** 丢弃缓存的招式索引（数据包变化后调用）。 */
    void invalidate();
}

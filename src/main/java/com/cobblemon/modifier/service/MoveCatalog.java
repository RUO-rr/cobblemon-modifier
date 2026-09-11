package com.cobblemon.modifier.service;

import java.util.Set;

/**
 * 已知招式表 —— 用于校验用户填写的招式 id，避免拼错一个字母就让宝可梦/存档出问题。
 *
 * <p>来源有两处：
 * <ol>
 *   <li>基础招式：{@code <gameDir>/showdown/data/moves.js} 里的顶层键（实测 951 个）；</li>
 *   <li>自定义招式：数据包里 {@code data/<命名空间>/moves/**&#47;*.js} 的文件名
 *       （例如 {@code data/cobblemon/moves/ruceking/blitzstrike.js} → {@code blitzstrike}）。</li>
 * </ol>
 *
 * <p>构建结果会被缓存；读不到时 {@link #isReady()} 为 false，此时只做格式校验。
 */
public interface MoveCatalog {

    /** 已知招式 id 集合（全部小写、只含字母数字）。 */
    Set<String> knownMoveIds();

    /** 招式表是否构建成功（false 时跳过 id 校验，只检查格式）。 */
    boolean isReady();
}

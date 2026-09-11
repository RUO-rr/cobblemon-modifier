package com.cobblemon.modifier.service;

import java.nio.file.Path;

/**
 * Mega 限制开关 —— 读写 mega_showdown 的 {@code multipleMegas} 配置。
 *
 * <p>起因：评论区里说的"解除 mega 限制"指的是**解除"同时只能有一只 Mega"的限制**，
 * 而不是解除某个宝可梦的 Mega 形态。mega_showdown 本身就提供了这个开关：
 *
 * <pre>
 * // MegaGimmick.hasMega(player)
 * if (MegaShowdownConfig.multipleMegas) return false;   // 允许多只 → 不再检查"已经 Mega 过"
 * </pre>
 *
 * <p>它读的配置是这个文件（不是老的 mega_showdown-common.toml）：
 * {@code config/mega_showdown/config.json}。
 */
public interface MegaLimitService {

    /** 当前是否允许同时存在多只 Mega；配置文件不存在时按"不允许"处理。 */
    boolean isMultipleMegasEnabled() throws Exception;

    /**
     * 写入"允许多只 Mega"开关，并尝试热重载 mega_showdown 的配置。
     *
     * @return true 表示已热重载、立即生效；false 表示文件已写入但需重启游戏
     */
    boolean setMultipleMegasEnabled(boolean enabled) throws Exception;

    /** 配置文件路径，便于界面提示与测试。 */
    Path getConfigPath();
}

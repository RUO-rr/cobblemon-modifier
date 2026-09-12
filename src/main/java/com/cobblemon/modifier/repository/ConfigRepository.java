package com.cobblemon.modifier.repository;

import java.util.List;

/**
 * 用户配置持久化。
 * 保存/读取上次选择的文件夹路径、插件缓存等。
 */
public interface ConfigRepository {

    /** 获取上次选择的文件夹路径，无则返回 null */
    String getLastFolderPath();

    /** 保存选择的文件夹路径 */
    void saveLastFolderPath(String path);

    /** 获取指定插件的缓存 JSON 路径列表 */
    List<String> getPluginCache(String pluginName);

    /** 保存指定插件的缓存 JSON 路径列表 */
    void savePluginCache(String pluginName, List<String> paths);

    /** 清除所有插件缓存 */
    void clearAllPluginCaches();

    /**
     * 扫描规则版本号。
     *
     * <p>改动"哪些文件算宝可梦数据"（例如放开命名空间限制）时 +1，
     * 启动时发现版本不一致就清掉旧的插件缓存——否则用户升级后
     * 界面仍然用旧列表，会以为"新数据还是没加载进来"。
     */
    int getScanRuleVersion();

    /** 保存当前扫描规则版本。 */
    void saveScanRuleVersion(int version);

    /** 获取旧的通用 JSON 路径缓存（向后兼容） */
    List<String> getLegacyJsonPaths();

    /** 保存旧的通用 JSON 路径缓存 */
    void saveLegacyJsonPaths(List<String> paths);
}

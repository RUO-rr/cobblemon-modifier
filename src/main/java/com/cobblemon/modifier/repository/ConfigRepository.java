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

    /** 获取旧的通用 JSON 路径缓存（向后兼容） */
    List<String> getLegacyJsonPaths();

    /** 保存旧的通用 JSON 路径缓存 */
    void saveLegacyJsonPaths(List<String> paths);
}

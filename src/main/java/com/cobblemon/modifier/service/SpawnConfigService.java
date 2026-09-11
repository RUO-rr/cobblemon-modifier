package com.cobblemon.modifier.service;

import com.cobblemon.modifier.model.SpawnBucketConfig;

import java.nio.file.Path;

/**
 * 全局刷新配置服务：读写 Cobblemon 的 best-spawner-config.json。
 */
public interface SpawnConfigService {

    /** 读取当前配置；文件不存在时返回 Cobblemon 默认值。 */
    SpawnBucketConfig load() throws Exception;

    /** 写回配置，保留文件中的未知字段。 */
    void save(SpawnBucketConfig config) throws Exception;

    /** 配置文件路径，便于界面提示与测试。 */
    Path getConfigPath();

    /**
     * 调用 Cobblemon 的 BestSpawner.reloadConfig() 让配置立即生效。
     *
     * @return true 表示热重载成功；false 表示 Cobblemon 不可用或调用失败
     */
    boolean reloadBestSpawner();
}

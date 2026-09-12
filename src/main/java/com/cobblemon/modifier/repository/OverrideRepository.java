package com.cobblemon.modifier.repository;

import com.google.gson.JsonObject;

import java.nio.file.Path;

/**
 * 修改覆盖存储 —— 不再直接改写模组 JAR，而是把修改后的 JSON 保存到独立目录。
 *
 * <p>覆盖文件镜像原始资源路径（例如 data/cobblemon/species/generation1/nidoranm.json），
 * 同步时会被复制成世界数据包，由 Cobblemon 自己的数据加载器优先读取。
 */
public interface OverrideRepository {

    /**
     * 读取指定资源路径的覆盖 JSON；没有覆盖时返回 null。
     */
    JsonObject readOverride(String jsonPath);

    /**
     * 写入指定资源路径的覆盖 JSON。
     */
    void writeOverride(String jsonPath, JsonObject data) throws Exception;

    /**
     * 读取指定资源路径的覆盖**文本**；没有覆盖时返回 null。
     *
     * <p>招式定义是 {@code data/cobblemon/moves/&lt;id&gt;.js} 这类脚本文件
     * （"JSON + 内嵌函数"），既不是纯 JSON 也不能用 Gson 解析，
     * 所以覆盖层需要一条文本通道。
     */
    String readOverrideText(String path);

    /**
     * 写入指定资源路径的覆盖文本（招式脚本等）。
     */
    void writeOverrideText(String path, String text) throws Exception;

    /**
     * 删除指定资源路径的覆盖文件（恢复原版）。
     *
     * @return 是否确实删除了文件
     */
    boolean deleteOverride(String jsonPath) throws Exception;

    /**
     * 把覆盖目录同步为世界数据包。
     */
    void syncToDatapack(Path worldDatapacksDir) throws Exception;

    /**
     * 覆盖文件的暂存根目录。
     */
    Path getStagingRoot();
}

package com.cobblemon.modifier.repository;

import com.google.gson.JsonObject;
import com.cobblemon.modifier.model.JarResourcePath;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * JAR 文件底层读写操作。
 * 只关心"怎么读/写 JAR 里的文件"，不关心"读什么/为什么读"。
 */
public interface JarRepository {

    /**
     * 列出 JAR 中匹配目标路径前缀的所有 JSON 文件。
     */
    List<String> listJsonFiles(File jarFile, List<String> targetPaths) throws Exception;

    /**
     * 列出压缩包里满足条件的**任意类型**条目路径（不限 .json）。
     *
     * <p>整合包里的自定义招式是 {@code data/<命名空间>/moves/**&#47;*.js} 脚本，
     * 需要靠这个方法把它们找出来。
     */
    List<String> listEntries(File jarFile, Predicate<String> filter) throws Exception;

    /**
     * 从 JAR 中读取指定路径的 JSON。
     */
    JsonObject readJson(File jarFile, String jsonPath) throws Exception;

    /**
     * 批量读取同一个压缩包里的多个 JSON，**只打开一次压缩包**。
     *
     * <p>逐条调用 {@link #readJson} 会为每个文件重新打开一次压缩包；
     * 大整合包里候选文件有八九千个，其中光 Cobblemon 本体就有几千个，
     * 反复打开 135MB 的 JAR 会让"加载宝可梦数据"卡上一分钟。
     *
     * @return 路径 → JSON 的映射；读不到或解析失败的条目不会出现在结果里
     */
    Map<String, JsonObject> readJsonBatch(File jarFile, List<String> jsonPaths) throws Exception;

    /**
     * 读取压缩包里的**文本**条目（招式脚本是 {@code .js}，不是 JSON）。
     *
     * @param entryPath 包内路径，例如 {@code data/cobblemon/moves/tackle.js}
     */
    String readText(File jarFile, String entryPath) throws Exception;

    /**
     * 批量读取同一个压缩包里的多个文本条目，**只打开一次压缩包**。
     *
     * <p>整合包里自定义招式脚本有几百个，逐条读会反复打开同一个 JAR。
     *
     * @return 路径 → 文本；读不到的条目不会出现在结果里
     */
    Map<String, String> readTextBatch(File jarFile, List<String> entryPaths) throws Exception;

    /**
     * 将修改后的 JSON 写回 JAR。
     */
    void writeJson(File jarFile, String jsonPath, JsonObject data) throws Exception;

    /**
     * 备份 JAR 文件到目标路径。
     * @return 备份文件路径
     */
    String backup(File sourceJar, File targetJar) throws Exception;

    /**
     * 扫描文件夹中所有 JAR，返回 "[jarName]jsonPath" 格式的路径列表。
     */
    List<String> scanFolder(File folder, List<String> targetPaths) throws Exception;
}

package com.cobblemon.modifier.service;

import com.google.gson.JsonObject;
import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.model.JarResourcePath;

import java.io.File;
import java.util.Map;

/**
 * JSON 修改服务。
 * 负责读取 JSON、应用修改、写回 JAR。
 */
public interface ModifyService {

    /**
     * 读取并解析 JSON，返回字段值 Map。
     */
    Map<String, Object> readAndParse(JarResourcePath path, File baseFolder,
                                     JsonModifier plugin) throws Exception;

    /**
     * 读取原始 JSON 对象。
     */
    JsonObject readRaw(JarResourcePath path, File baseFolder) throws Exception;

    /**
     * 写入一份覆盖文件（不改动任何原始 JAR / zip）。
     *
     * <p>B1 扇出：改动过的字段还会被同步到这个物种的其它来源文件
     * （本体 + 每个 species_additions 覆盖文件）。因为 Cobblemon 应用覆盖的顺序
     * 是 HashMap 顺序、和数据包优先级无关，只改一份并不能保证生效。
     *
     * @return 额外同步的其它来源文件数量
     */
    int modifyAndSave(JarResourcePath path, File baseFolder,
                      JsonModifier plugin, Map<String, Object> newValues) throws Exception;

    /**
     * 还原某个物种的原版数据：删掉这一份的覆盖文件，**以及扇出写出的其它来源文件的覆盖**。
     *
     * <p>保存时会把改动同步到本体 + 各个 {@code species_additions} 覆盖文件，
     * 只删被编辑的那一份会留下"半还原"状态——本体回原版了，覆盖文件里还是用户的旧值，
     * 游戏依旧按旧值走。
     *
     * @return 实际删除的覆盖文件数量
     */
    int restoreOverrides(JarResourcePath path, File baseFolder) throws Exception;
}

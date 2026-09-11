package com.cobblemon.modifier.service;

import com.google.gson.JsonObject;
import com.cobblemon.modifier.model.JarResourcePath;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * JAR 扫描与 JSON 验证服务。
 * 负责扫描文件夹中的 JAR、列出 JSON、验证有效性、报告进度。
 */
public interface ScanService {

    /**
     * 扫描结果：有效路径列表 + 统计信息。
     */
    record ScanResult(List<JarResourcePath> validPaths, int totalCount, int skippedCount) {}

    /**
     * 进度回调。
     */
    @FunctionalInterface
    interface ProgressCallback {
        void onProgress(int current, int total, String status);
    }

    /**
     * 扫描文件夹中所有 JAR，筛选 JSON 文件并验证。
     *
     * @param folder      目标文件夹
     * @param targetPaths JSON 搜索路径前缀
     * @param jarFilter   JAR 文件名过滤器（如只扫描特定模组的 JAR）
     * @param validator   JSON 有效性验证器
     * @param onProgress  进度回调（在工作线程中调用）
     * @return 扫描结果
     */
    ScanResult scanAndValidate(File folder,
                               List<String> targetPaths,
                               Predicate<String> jarFilter,
                               Predicate<JsonObject> validator,
                               ProgressCallback onProgress) throws Exception;
}

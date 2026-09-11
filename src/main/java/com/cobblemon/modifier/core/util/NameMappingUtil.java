package com.cobblemon.modifier.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 名称映射工具类（通用）
 */
public class NameMappingUtil {
    private static final Logger log = LoggerFactory.getLogger(NameMappingUtil.class);
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    /**
     * 加载名称映射 JSON 文件
     * @param resourceName JSON 文件名（如 "pokemon_name_mapping.json"）
     * @return 映射表（英文→中文）
     */
    public static Map<String, String> loadNameMapping(String resourceName) {
        return CACHE.computeIfAbsent(resourceName, key -> {
            Map<String, String> mapping = new HashMap<>();
            try (InputStream is = NameMappingUtil.class.getResourceAsStream("/" + key)) {
                if (is != null) {
                    JsonObject json = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                    if (json != null) {
                        json.entrySet().forEach(entry ->
                                mapping.put(entry.getValue().getAsString(), entry.getKey())
                        );
                    }
                }
            } catch (Exception e) {
                log.warn("加载映射文件失败：{} - {}", key, e.getMessage());
            }
            return mapping;
        });
    }

    /**
     * 获取中文名→英文名映射
     */
    public static Map<String, String> getChineseToEnglishMap(String resourceName) {
        Map<String, String> engToChi = loadNameMapping(resourceName);
        Map<String, String> chiToEng = new HashMap<>();
        engToChi.forEach((eng, chi) -> chiToEng.put(chi, eng));
        return chiToEng;
    }

    /**
     * 获取英文名→中文名映射
     */
    public static Map<String, String> getEnglishToChineseMap(String resourceName) {
        return loadNameMapping(resourceName);
    }
}

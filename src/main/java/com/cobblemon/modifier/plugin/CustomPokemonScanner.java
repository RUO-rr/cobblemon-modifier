package com.cobblemon.modifier.plugin;

import com.cobblemon.modifier.core.JsonModifier;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * 自定义宝可梦扫描器 —— 扫描特定 JAR 包中的宝可梦 JSON 文件。
 *
 * Phase 3 改造：移除从未使用的 ConfigManager 依赖，添加无参构造器以支持 ServiceLoader。
 */
public class CustomPokemonScanner implements JsonModifier {

    private static final String PLUGIN_NAME = "魔改宝可梦扫描";

    private List<String> FIELD_NAMES;
    private Map<String, String> FIELD_LABELS;
    
    // 目标 JAR 包名称模式（模糊匹配，使用 contains）
    private static final List<String> TARGET_JAR_PATTERNS = List.of(
        "宝可梦-本体",           // [宝可梦 - 本体]Cobblemon-fabric-*.jar
        "宝可梦-ZA",             // [宝可梦-ZA]zamega-fabric-*.jar
        "mushiromega-fabric",    // mushiromega-fabric-*.jar
        "starlightfusion-fabric", // starlightfusion-fabric-*.jar
        "宝可梦-超级对决"       // [宝可梦 - 超级对决]*.jar
    );
    
    // 需要扫描的目录
    private static final List<String> SCAN_DIRECTORIES = List.of(
        "data/",           // 扫描 data 目录及其所有子目录
        "data/cobblemon/species"
    );

    public CustomPokemonScanner() {
        this.FIELD_NAMES = new ArrayList<>();
        this.FIELD_LABELS = new HashMap<>();
        initFields();
    }
    
    /**
     * 初始化字段列表
     * 这个扫描器只需要基础的编号和类型字段来识别宝可梦
     */
    private void initFields() {
        FIELD_NAMES.clear();
        FIELD_LABELS.clear();
        
        // 添加用于识别的基本字段
        FIELD_NAMES.add("nationalPokedexNumber");
        FIELD_LABELS.put("nationalPokedexNumber", "全国图鉴编号");
        
        FIELD_NAMES.add("primaryType");
        FIELD_LABELS.put("primaryType", "第一属性");
        
        FIELD_NAMES.add("secondaryType");
        FIELD_LABELS.put("secondaryType", "第二属性");
        
        // 添加 forms 字段（魔改宝可梦的关键特征）
        FIELD_NAMES.add("forms");
        FIELD_LABELS.put("forms", "形态列表");
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public List<String> getTargetJsonPaths() {
        // 返回空列表，因为我们使用自定义的 JAR 过滤逻辑
        // 实际的扫描在 JarManager.scanJarsInFolder 中通过文件名匹配完成
        return SCAN_DIRECTORIES;
    }

    @Override
    public List<String> getFieldNames() {
        return FIELD_NAMES;
    }

    @Override
    public String getFieldLabel(String fieldName) {
        return FIELD_LABELS.getOrDefault(fieldName, fieldName);
    }

    @Override
    public Map<String, Object> parseJson(JsonObject jsonObject) {
        Map<String, Object> fieldValues = new HashMap<>();
        
        // 提取图鉴编号（如果存在）
        if (jsonObject.has("nationalPokedexNumber")) {
            fieldValues.put("nationalPokedexNumber", 
                jsonObject.get("nationalPokedexNumber").getAsInt());
        } else {
            fieldValues.put("nationalPokedexNumber", -1); // 魔改宝可梦可能没有编号
        }
        
        // 提取属性
        if (jsonObject.has("primaryType")) {
            fieldValues.put("primaryType", 
                jsonObject.get("primaryType").getAsString());
        }
        
        if (jsonObject.has("secondaryType")) {
            fieldValues.put("secondaryType", 
                jsonObject.get("secondaryType").getAsString());
        }
        
        // 标记是否有 forms（魔改宝可梦的特征）
        fieldValues.put("hasForms", jsonObject.has("forms"));
        
        return fieldValues;
    }

    @Override
    public JsonObject modifyJson(JsonObject originalJson, Map<String, Object> newValues) {
        // 扫描器不修改 JSON，只读取
        return originalJson;
    }

    @Override
    public Object convertFieldValue(String fieldName, String inputValue) {
        if ("nationalPokedexNumber".equals(fieldName)) {
            try {
                return Integer.parseInt(inputValue.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return inputValue.trim();
    }

    @Override
    public boolean hasValidStatsFields(JsonObject jsonObject) {
        // 判断是否是有效的宝可梦配置
        // 魔改宝可梦至少应该包含以下特征之一：
        
        // 1. 有 baseStats（种族值）
        if (jsonObject.has("baseStats")) {
            return true;
        }
        
        // 2. 有 forms（形态变化）
        if (jsonObject.has("forms")) {
            return true;
        }
        
        // 3. 有 primaryType（属性）且有 name（名称）
        if (jsonObject.has("primaryType") && jsonObject.has("name")) {
            return true;
        }
        
        // 4. 是基础形态但有 types
        if (jsonObject.has("types")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查 JAR 文件名是否符合我们的扫描条件（模糊匹配）
     */
    public static boolean isTargetJar(String jarFileName) {
        // 移除 .jar 后缀进行比较
        String jarName = jarFileName.toLowerCase().replace(".jar", "");
            
        // 遍历所有模式，只要包含任意一个就匹配成功
        for (String pattern : TARGET_JAR_PATTERNS) {
            if (jarName.contains(pattern.toLowerCase())) {
                return true;
            }
        }
            
        return false;
    }
}

package com.cobblemon.modifier.model;

import java.util.Objects;

/**
 * 表示 JAR 包内一个 JSON 资源的路径。
 * 不可变值对象，统一解析 "[jarName]jsonPath" 格式。
 *
 * 原始格式示例: [cobblemon-fabric-1.0.jar]data/cobblemon/species/pikachu.json
 */
public final class JarResourcePath {

    private final String jarName;
    private final String jsonPath;

    private JarResourcePath(String jarName, String jsonPath) {
        this.jarName = Objects.requireNonNull(jarName, "jarName must not be null");
        this.jsonPath = Objects.requireNonNull(jsonPath, "jsonPath must not be null");
    }

    /**
     * 从 "[jarName]jsonPath" 格式的字符串解析。
     *
     * @param raw 原始字符串，如 "[cobblemon.jar]data/species/pikachu.json"
     * @throws IllegalArgumentException 如果格式不合法
     */
    public static JarResourcePath parse(String raw) {
        int bracketStart = raw.indexOf('[');
        int bracketEnd = raw.lastIndexOf(']');

        if (bracketStart == -1 || bracketEnd == -1 || bracketStart >= bracketEnd) {
            throw new IllegalArgumentException(
                "Invalid path format, expected [jarName]jsonPath but got: " + raw);
        }

        String jarName = raw.substring(bracketStart + 1, bracketEnd);
        String jsonPath = raw.substring(bracketEnd + 1);

        if (jarName.isEmpty()) {
            throw new IllegalArgumentException("JAR name is empty in path: " + raw);
        }
        if (jsonPath.isEmpty()) {
            throw new IllegalArgumentException("JSON path is empty in path: " + raw);
        }

        return new JarResourcePath(jarName, jsonPath);
    }

    /**
     * 尝试解析，失败返回 null（不抛异常）。
     */
    public static JarResourcePath tryParse(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 由已知的压缩包名与 JSON 路径直接构造（不经过 "[jarName]jsonPath" 字符串）。
     *
     * @throws IllegalArgumentException 任一参数为空
     */
    public static JarResourcePath of(String jarName, String jsonPath) {
        if (jarName == null || jarName.isEmpty()) {
            throw new IllegalArgumentException("JAR name is empty");
        }
        if (jsonPath == null || jsonPath.isEmpty()) {
            throw new IllegalArgumentException("JSON path is empty");
        }
        return new JarResourcePath(jarName, jsonPath);
    }

    /**
     * 从显示文本中提取原始路径再解析。
     * 显示格式: [001] [MM-dd HH:mm] [jarName]jsonPath
     */
    public static JarResourcePath parseFromDisplayText(String displayText) {
        int firstBracketEnd = displayText.indexOf(']');
        if (firstBracketEnd == -1) {
            return parse(displayText); // 尝试直接解析
        }
        int secondBracketEnd = displayText.indexOf(']', firstBracketEnd + 1);
        if (secondBracketEnd == -1) {
            return parse(displayText);
        }
        String raw = displayText.substring(secondBracketEnd + 1).trim();
        return parse(raw);
    }

    public String jarName() {
        return jarName;
    }

    public String jsonPath() {
        return jsonPath;
    }

    /**
     * 从 jsonPath 中提取宝可梦名称（文件名去掉路径和扩展名）。
     * 例如: data/cobblemon/species/pikachu.json → pikachu
     */
    public String extractPokemonName() {
        int lastSlash = jsonPath.lastIndexOf('/');
        int lastDot = jsonPath.lastIndexOf('.');
        if (lastSlash >= 0 && lastDot > lastSlash) {
            return jsonPath.substring(lastSlash + 1, lastDot);
        }
        return jsonPath;
    }

    /**
     * 还原为 "[jarName]jsonPath" 格式。
     */
    public String toRawString() {
        return "[" + jarName + "]" + jsonPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JarResourcePath that)) return false;
        return jarName.equals(that.jarName) && jsonPath.equals(that.jsonPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jarName, jsonPath);
    }

    @Override
    public String toString() {
        return toRawString();
    }
}

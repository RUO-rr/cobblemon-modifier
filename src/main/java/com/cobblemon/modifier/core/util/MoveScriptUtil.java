package com.cobblemon.modifier.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招式脚本的**文本级**读写工具。
 *
 * <p>Cobblemon 的招式定义不是 JSON，而是"JSON + 内嵌函数"的脚本：
 * <pre>
 * // data/cobblemon/moves/tackle.js
 * {
 *   num: 33,
 *   accuracy: 100,
 *   basePower: 40,
 *   category: "Physical",
 *   name: "Tackle",
 *   pp: 35,
 *   priority: 0,
 *   flags: { contact: 1, protect: 1 },
 *   onHit(target, source) { ... },          ← 函数体里也可能出现同名字段
 *   secondary: null,
 *   type: "Normal"
 * }
 * </pre>
 *
 * <p>直接把整段文本 {@code replace("basePower", ...)} 会连函数体里的同名量一起改掉
 * （例如"预知未来"的 {@code onTry} 里就嵌着一份 {@code basePower}），
 * 所以这里用一个带括号深度跟踪的扫描器：**只认最外层对象上的字段**，
 * 并跳过字符串与注释，保证改的正是招式本体。
 *
 * <p>纯静态、无状态，便于单元测试。
 */
public final class MoveScriptUtil {

    /** 招式脚本里可编辑的数值/文本字段。 */
    public static final List<String> EDITABLE_FIELDS =
        List.of("basePower", "accuracy", "pp", "priority", "type");

    /**
     * showdown/data/moves.js 里的招式入口：**恰好缩进两个空格**的顶层键。
     * 缩进必须写死，否则 {@code flags: &#123; ... &#125;} 这类内层对象也会被当成招式。
     */
    private static final Pattern ENTRY_HEAD =
        Pattern.compile("(?m)^ {2}['\"]?([A-Za-z0-9_]+)['\"]?[ \\t]*:[ \\t]*\\{");

    private static final Pattern NAME_FIELD =
        Pattern.compile("name[ \\t]*:[ \\t]*\"([^\"]*)\"");

    private MoveScriptUtil() {
    }

    // ================================================================
    // 单字段读写
    // ================================================================

    /**
     * 读取最外层字段的**原始字面量**（例如 {@code "Normal"}、{@code 40}、{@code null}、{@code true}）。
     *
     * @return 找不到时返回 {@code null}
     */
    public static String readField(String script, String field) {
        if (script == null || field == null) {
            return null;
        }
        String[] found = new String[1];
        scanTopLevel(script, (key, start, end) -> {
            if (field.equals(key)) {
                found[0] = script.substring(start, end);
                return false;
            }
            return true;
        });
        return found[0];
    }

    /**
     * 覆盖最外层字段的值；字段不存在时追加到对象末尾。
     *
     * @param literal 新的字面量，**必须已经带好引号**（字符串字段要写成 {@code "Fire"}）
     * @return 新文本；脚本无法解析时原样返回
     */
    public static String writeField(String script, String field, String literal) {
        if (script == null || field == null || literal == null) {
            return script;
        }
        int[] range = {-1, -1};
        scanTopLevel(script, (key, start, end) -> {
            if (field.equals(key)) {
                range[0] = start;
                range[1] = end;
                return false;
            }
            return true;
        });
        if (range[0] >= 0) {
            return script.substring(0, range[0]) + literal + script.substring(range[1]);
        }
        int close = lastClosingBrace(script);
        if (close < 0) {
            return script;
        }
        String head = script.substring(0, close);
        String separator = head.stripTrailing().endsWith(",") ? "\n" : ",\n";
        return head + separator + "  " + field + ": " + literal + "\n" + script.substring(close);
    }

    /**
     * 依次覆盖多个字段，返回新文本。
     *
     * @param literals 字段 → 字面量（例如 {@code basePower -> "80"}）
     */
    public static String writeFields(String script, Map<String, String> literals) {
        String result = script;
        for (Map.Entry<String, String> entry : literals.entrySet()) {
            result = writeField(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ================================================================
    // 从 showdown/data/moves.js 里抽取招式定义
    // ================================================================

    /**
     * 读取基础招式表：{@code <gameDir>/showdown/data/moves.js} 里的顶层招式。
     *
     * @return 招式 id → 英文名（保持文件里的顺序）
     */
    public static Map<String, String> readShowdownMoves(String bundle) {
        Map<String, String> moves = new LinkedHashMap<>();
        if (bundle == null) {
            return moves;
        }
        // Matcher 是有状态的：必须在 find() 当时就把 id 与下标拷出来
        List<String> ids = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = ENTRY_HEAD.matcher(bundle);
        while (matcher.find()) {
            ids.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
            starts.add(matcher.start());
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : bundle.length();
            Matcher name = NAME_FIELD.matcher(bundle.substring(from, Math.min(to, from + 600)));
            moves.putIfAbsent(id, name.find() ? name.group(1) : id);
        }
        return moves;
    }

    /**
     * 从基础招式表里抽出某个招式的对象文本（含外层花括号）。
     *
     * <p>抽出来的文本会被去掉一级缩进，直接落成
     * {@code data/cobblemon/moves/<id>.js} 就能用。
     *
     * @return 找不到时返回 {@code null}
     */
    public static String extractEntry(String bundle, String id) {
        if (bundle == null || id == null) {
            return null;
        }
        bundle = normalizeNewlines(bundle);
        Matcher matcher = ENTRY_HEAD.matcher(bundle);
        while (matcher.find()) {
            if (!id.equalsIgnoreCase(matcher.group(1))) {
                continue;
            }
            int braceStart = bundle.lastIndexOf('{', matcher.end());
            if (braceStart < 0) {
                return null;
            }
            int end = matchBrace(bundle, braceStart);
            if (end < 0) {
                return null;
            }
            return dedent(bundle.substring(braceStart, end + 1));
        }
        return null;
    }

    /** 统一换行符，避免把 showdown 的 CRLF 带进覆盖文件。 */
    public static String normalizeNewlines(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 招式 id 规范化：小写 + 只留字母数字（与 Cobblemon / Showdown 一致）。 */
    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char c = raw.charAt(index);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    // ================================================================
    // 扫描器
    // ================================================================

    private interface FieldVisitor {

        /**
         * @param start 值的起始下标（已跳过冒号后的空白）
         * @param end   值的结束下标（不含，已去掉尾随空白）
         * @return false 表示停止扫描
         */
        boolean visit(String key, int start, int end);
    }

    /** 只回调最外层对象（深度 1）上的 {@code 字段: 值}。 */
    private static void scanTopLevel(String text, FieldVisitor visitor) {
        int depth = 0;
        int index = 0;
        int length = text.length();
        while (index < length) {
            char c = text.charAt(index);
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '/') {
                index = skipLineComment(text, index);
                continue;
            }
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '*') {
                index = skipBlockComment(text, index);
                continue;
            }
            if (isQuote(c)) {
                index = skipString(text, index);
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                index++;
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                depth--;
                index++;
                continue;
            }
            if (depth == 1 && isIdentifierStart(c)) {
                int start = index;
                while (index < length && isIdentifierPart(text.charAt(index))) {
                    index++;
                }
                String key = text.substring(start, index);
                int colon = skipWhitespace(text, index);
                if (colon < length && text.charAt(colon) == ':') {
                    int valueStart = skipWhitespace(text, colon + 1);
                    int valueEnd = findValueEnd(text, valueStart);
                    if (!visitor.visit(key, valueStart, valueEnd)) {
                        return;
                    }
                    index = Math.max(valueEnd, valueStart);
                    continue;
                }
                continue;
            }
            index++;
        }
    }

    /** 从值的第一个字符扫描到它结束的位置（顶层逗号或对象结尾）。 */
    private static int findValueEnd(String text, int start) {
        int depth = 0;
        int index = start;
        int length = text.length();
        while (index < length) {
            char c = text.charAt(index);
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '/') {
                index = skipLineComment(text, index);
                continue;
            }
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '*') {
                index = skipBlockComment(text, index);
                continue;
            }
            if (isQuote(c)) {
                index = skipString(text, index);
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                index++;
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                if (depth == 0) {
                    break;
                }
                depth--;
                index++;
                continue;
            }
            if (depth == 0 && c == ',') {
                break;
            }
            index++;
        }
        int end = index;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    /** 返回与 {@code openIndex} 处花括号配对的下标。 */
    private static int matchBrace(String text, int openIndex) {
        int depth = 0;
        int index = openIndex;
        int length = text.length();
        while (index < length) {
            char c = text.charAt(index);
            if (isQuote(c)) {
                index = skipString(text, index);
                continue;
            }
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '/') {
                index = skipLineComment(text, index);
                continue;
            }
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '*') {
                index = skipBlockComment(text, index);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
            index++;
        }
        return -1;
    }

    /** 找最外层对象的收尾花括号（跳过字符串/注释）。 */
    private static int lastClosingBrace(String text) {
        int depth = 0;
        int result = -1;
        int index = 0;
        int length = text.length();
        while (index < length) {
            char c = text.charAt(index);
            if (isQuote(c)) {
                index = skipString(text, index);
                continue;
            }
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '/') {
                index = skipLineComment(text, index);
                continue;
            }
            if (c == '/' && index + 1 < length && text.charAt(index + 1) == '*') {
                index = skipBlockComment(text, index);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    result = index;
                }
            }
            index++;
        }
        return result;
    }

    /** 去掉每行最多 {@code indent} 个前导空格（第一行保持原样）。 */
    private static String dedent(String text) {
        String[] lines = text.split("\n", -1);
        int min = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int spaces = 0;
            while (spaces < line.length() && line.charAt(spaces) == ' ') {
                spaces++;
            }
            min = Math.min(min, spaces);
        }
        if (min == Integer.MAX_VALUE || min == 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        builder.append(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            builder.append('\n');
            String line = lines[i];
            int cut = 0;
            while (cut < min && cut < line.length() && line.charAt(cut) == ' ') {
                cut++;
            }
            builder.append(line.substring(cut));
        }
        return builder.toString();
    }

    private static int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '`';
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** 跳过字符串字面量（含转义），返回结束引号之后的下标。 */
    private static int skipString(String text, int start) {
        char quote = text.charAt(start);
        int index = start + 1;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == '\\') {
                index += 2;
                continue;
            }
            if (c == quote) {
                return index + 1;
            }
            index++;
        }
        return text.length();
    }

    private static int skipLineComment(String text, int start) {
        int index = start + 2;
        while (index < text.length() && text.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String text, int start) {
        int index = text.indexOf("*/", start + 2);
        return index < 0 ? text.length() : index + 2;
    }
}

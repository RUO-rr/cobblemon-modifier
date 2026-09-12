package com.cobblemon.modifier.core.util;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 招式脚本文本工具的测试。
 *
 * <p>重点覆盖"只改最外层字段"这一点：真实整合包里"预知未来"的
 * {@code onTry} 函数体内嵌了一整份 {@code basePower/accuracy}，
 * 朴素的字符串替换会把它们一起改掉。
 */
public class MoveScriptUtilTest {

    private static final String TACKLE = """
        {
          num: 33,
          accuracy: 100,
          basePower: 40,
          category: "Physical",
          name: "Tackle",
          pp: 35,
          priority: 0,
          flags: { contact: 1, protect: 1, mirror: 1 },
          secondary: null,
          target: "normal",
          type: "Normal"
        }
        """;

    /** 函数体里嵌着同名字段的真实案例（mushiromega 的 futuresight.js 简化版）。 */
    private static final String FUTURE_SIGHT = """
        {
          num: 248,
          accuracy: 100,
          basePower: 120,
          category: "Special",
          name: "Future Sight",
          pp: 10,
          priority: 0,
          onTry(source, target) {
            if (!target.side.addSlotCondition(target, "futuremove"))
              return false;
            Object.assign(target.side.slotConditions[target.position]["futuremove"], {
              duration: 3,
              move: "futuresight",
              moveData: {
                id: "futuresight",
                accuracy: 100,
                basePower: 120,
                type: "Psychic"
              }
            });
          },
          secondary: null,
          target: "normal",
          type: "Psychic"
        }
        """;

    @Test
    public void readFieldReturnsTopLevelLiteral() {
        assertEquals("40", MoveScriptUtil.readField(TACKLE, "basePower"));
        assertEquals("100", MoveScriptUtil.readField(TACKLE, "accuracy"));
        assertEquals("\"Tackle\"", MoveScriptUtil.readField(TACKLE, "name"));
        assertEquals("\"Normal\"", MoveScriptUtil.readField(TACKLE, "type"));
        assertEquals("35", MoveScriptUtil.readField(TACKLE, "pp"));
        assertEquals("0", MoveScriptUtil.readField(TACKLE, "priority"));
    }

    @Test
    public void readFieldReturnsNullForUnknownField() {
        assertNull(MoveScriptUtil.readField(TACKLE, "critRatio"));
        assertNull(MoveScriptUtil.readField(null, "basePower"));
    }

    @Test
    public void writeFieldChangesOnlyTopLevelValue() {
        String updated = MoveScriptUtil.writeField(TACKLE, "basePower", "80");

        assertEquals("80", MoveScriptUtil.readField(updated, "basePower"));
        // 其余字段 unaffected
        assertEquals("100", MoveScriptUtil.readField(updated, "accuracy"));
        assertEquals("\"Tackle\"", MoveScriptUtil.readField(updated, "name"));
        assertTrue(updated.contains("name: \"Tackle\""));
    }

    @Test
    public void writeFieldLeavesNestedValuesAlone() {
        String updated = MoveScriptUtil.writeField(FUTURE_SIGHT, "basePower", "150");

        // 顶层改掉了
        assertEquals("150", MoveScriptUtil.readField(updated, "basePower"));
        // 函数体里的那份仍然是 120
        assertTrue("函数体里的 basePower 不应被改动", updated.contains("basePower: 120"));
        assertEquals("120", firstNestedBasePower(updated));
        assertTrue(updated.contains("moveData:"));
    }

    @Test
    public void writeFieldAppendsMissingField() {
        String script = "{\n  name: \"Tackle\"\n}\n";
        String updated = MoveScriptUtil.writeField(script, "basePower", "45");

        assertEquals("45", MoveScriptUtil.readField(updated, "basePower"));
        assertEquals("\"Tackle\"", MoveScriptUtil.readField(updated, "name"));
        assertTrue(updated.contains("basePower: 45"));
    }

    @Test
    public void writeFieldsAppliesAllLiterals() {
        String updated = MoveScriptUtil.writeFields(TACKLE, Map.of(
            "basePower", "80",
            "accuracy", "true",
            "type", "\"Fire\""
        ));

        assertEquals("80", MoveScriptUtil.readField(updated, "basePower"));
        assertEquals("true", MoveScriptUtil.readField(updated, "accuracy"));
        assertEquals("\"Fire\"", MoveScriptUtil.readField(updated, "type"));
    }

    @Test
    public void readShowdownMovesCollectsIdAndEnglishName() {
        Map<String, String> moves = MoveScriptUtil.readShowdownMoves(SHOWDOWN_BUNDLE);

        assertEquals(3, moves.size());
        assertEquals("Tackle", moves.get("tackle"));
        assertEquals("Future Sight", moves.get("futuresight"));
        assertEquals("Tail Glow", moves.get("tailglow"));
    }

    @Test
    public void extractEntryReturnsStandaloneObject() {
        String entry = MoveScriptUtil.extractEntry(SHOWDOWN_BUNDLE, "tackle");

        assertTrue(entry.startsWith("{"));
        assertTrue(entry.strip().endsWith("}"));
        assertEquals("40", MoveScriptUtil.readField(entry, "basePower"));
        assertEquals("\"Tackle\"", MoveScriptUtil.readField(entry, "name"));
        // 抽出来即可直接当作覆盖文件使用
        String patched = MoveScriptUtil.writeField(entry, "basePower", "90");
        assertEquals("90", MoveScriptUtil.readField(patched, "basePower"));
    }

    @Test
    public void extractEntryReturnsNullForUnknownMove() {
        assertNull(MoveScriptUtil.extractEntry(SHOWDOWN_BUNDLE, "notamove"));
    }

    @Test
    public void normalizeIdStripsPunctuation() {
        assertEquals("dragondance", MoveScriptUtil.normalizeId("Dragon-Dance"));
        assertEquals("futuresight", MoveScriptUtil.normalizeId("Future Sight"));
        assertEquals("", MoveScriptUtil.normalizeId(null));
    }

    /** 取出第一个嵌套（深度 > 1）的 basePower 字面量，用于断言它没被改。 */
    private static String firstNestedBasePower(String script) {
        int idx = script.indexOf("moveData:");
        int nested = script.indexOf("basePower:", idx);
        int start = nested + "basePower:".length();
        while (Character.isWhitespace(script.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < script.length() && (Character.isDigit(script.charAt(end)) || script.charAt(end) == '.')) {
            end++;
        }
        return script.substring(start, end);
    }

    private static final String SHOWDOWN_BUNDLE = """
        export const Moves = {
          tailglow: {
            num: 294,
            accuracy: true,
            basePower: 0,
            category: "Status",
            name: "Tail Glow",
            pp: 20,
            priority: 0,
            type: "Bug"
          },
          tackle: {
            num: 33,
            accuracy: 100,
            basePower: 40,
            category: "Physical",
            name: "Tackle",
            pp: 35,
            priority: 0,
            flags: { contact: 1, protect: 1 },
            type: "Normal"
          },
          futuresight: {
            num: 248,
            accuracy: 100,
            basePower: 120,
            category: "Special",
            name: "Future Sight",
            pp: 10,
            priority: 0,
            type: "Psychic"
          }
        };
        """;
}

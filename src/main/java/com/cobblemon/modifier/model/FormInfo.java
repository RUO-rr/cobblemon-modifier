package com.cobblemon.modifier.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 宝可梦形态信息 —— 不可变值对象。
 * 从原 BaseStatsModifier 和 AbilityModifier 中重复定义的内部类提取而来。
 */
public final class FormInfo {

    /** forms 数组中 name 字段的值，如 "Mega-X" */
    private final String name;

    /** UI 中显示的名称 */
    private final String displayName;

    /** 字段后缀，如 "_mega_x"，用于 UI 字段命名 */
    private final String fieldSuffix;

    public FormInfo(String name, String displayName, String fieldSuffix) {
        this.name = Objects.requireNonNull(name);
        this.displayName = Objects.requireNonNull(displayName);
        this.fieldSuffix = Objects.requireNonNull(fieldSuffix);
    }

    /**
     * 从 forms JSON 数组中提取所有形态信息。
     *
     * @param formsArray JsonObject 中的 "forms" JsonArray
     * @return 形态信息列表，如果没有 forms 则返回空列表
     */
    public static List<FormInfo> extractFromJson(JsonArray formsArray) {
        List<FormInfo> result = new ArrayList<>();
        int formIndex = 0;

        for (JsonElement elem : formsArray) {
            if (!elem.isJsonObject()) continue;

            JsonObject form = elem.getAsJsonObject();
            if (!form.has("name")) continue;

            String formName = form.get("name").getAsString();
            if (formName == null || formName.trim().isEmpty()) continue;

            result.add(create(formName, formIndex));
            formIndex++;
        }
        return result;
    }

    /**
     * 根据形态名称生成 FormInfo。
     */
    private static FormInfo create(String formName, int index) {
        if ("Mega-X".equals(formName)) {
            return new FormInfo(formName, "Mega-X", "_mega_x");
        } else if ("Mega-Y".equals(formName)) {
            return new FormInfo(formName, "Mega-Y", "_mega_y");
        } else if (formName.contains("Mega")) {
            return new FormInfo(formName, formName, "_mega_" + index);
        } else {
            return new FormInfo(formName, formName, "_form_" + index);
        }
    }

    // ---- getters ----

    public String name() { return name; }
    public String displayName() { return displayName; }
    public String fieldSuffix() { return fieldSuffix; }

    // ---- equals / hashCode / toString ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FormInfo that)) return false;
        return name.equals(that.name)
            && displayName.equals(that.displayName)
            && fieldSuffix.equals(that.fieldSuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, displayName, fieldSuffix);
    }

    @Override
    public String toString() {
        return "FormInfo{name='" + name + "', suffix='" + fieldSuffix + "'}";
    }
}

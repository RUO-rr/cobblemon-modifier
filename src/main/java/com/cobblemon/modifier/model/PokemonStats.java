package com.cobblemon.modifier.model;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 宝可梦 6 项种族值 —— 不可变值对象。
 *
 * 替代原来散落在代码中的裸 int 和 Map<String, Object>。
 * 所有字段命名字符串（"hp", "attack", ...）集中定义在这里，不再散落各处。
 */
public final class PokemonStats {

    public static final String HP = "hp";
    public static final String ATTACK = "attack";
    public static final String DEFENCE = "defence";
    public static final String SPECIAL_ATTACK = "special_attack";
    public static final String SPECIAL_DEFENCE = "special_defence";
    public static final String SPEED = "speed";

    /** 6 项种族值的字段名（固定顺序，用于 UI 遍历） */
    public static final String[] FIELD_NAMES = {
        HP, ATTACK, DEFENCE, SPECIAL_ATTACK, SPECIAL_DEFENCE, SPEED
    };

    /** 空种族值（全 0） */
    public static final PokemonStats ZERO = new PokemonStats(0, 0, 0, 0, 0, 0);

    private final int hp;
    private final int attack;
    private final int defence;
    private final int specialAttack;
    private final int specialDefence;
    private final int speed;

    public PokemonStats(int hp, int attack, int defence,
                        int specialAttack, int specialDefence, int speed) {
        this.hp = hp;
        this.attack = attack;
        this.defence = defence;
        this.specialAttack = specialAttack;
        this.specialDefence = specialDefence;
        this.speed = speed;
    }

    /**
     * 从 JsonObject 的 baseStats 节点读取种族值。
     */
    public static PokemonStats fromJson(JsonObject baseStats) {
        if (baseStats == null) return ZERO;
        return new PokemonStats(
            getInt(baseStats, HP),
            getInt(baseStats, ATTACK),
            getInt(baseStats, DEFENCE),
            getInt(baseStats, SPECIAL_ATTACK),
            getInt(baseStats, SPECIAL_DEFENCE),
            getInt(baseStats, SPEED));
    }

    /**
     * 写入到 JsonObject 的 baseStats 节点。
     */
    public void writeToJson(JsonObject target) {
        target.addProperty(HP, hp);
        target.addProperty(ATTACK, attack);
        target.addProperty(DEFENCE, defence);
        target.addProperty(SPECIAL_ATTACK, specialAttack);
        target.addProperty(SPECIAL_DEFENCE, specialDefence);
        target.addProperty(SPEED, speed);
    }

    /**
     * 转换为 UI 字段 Map（key = "hp_base", value = 35）。
     * suffix 通常是 "_base" 或 FormInfo.fieldSuffix()。
     */
    public Map<String, Integer> toFieldMap(String suffix) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(HP + suffix, hp);
        map.put(ATTACK + suffix, attack);
        map.put(DEFENCE + suffix, defence);
        map.put(SPECIAL_ATTACK + suffix, specialAttack);
        map.put(SPECIAL_DEFENCE + suffix, specialDefence);
        map.put(SPEED + suffix, speed);
        return map;
    }

    /**
     * 按字段名获取值（用于从 UI 输入更新）。
     */
    public int getByFieldName(String fieldName) {
        return switch (fieldName) {
            case HP -> hp;
            case ATTACK -> attack;
            case DEFENCE -> defence;
            case SPECIAL_ATTACK -> specialAttack;
            case SPECIAL_DEFENCE -> specialDefence;
            case SPEED -> speed;
            default -> throw new IllegalArgumentException("Unknown stat field: " + fieldName);
        };
    }

    /**
     * 返回更新某一项后的新 PokemonStats（不可变风格）。
     */
    public PokemonStats withField(String fieldName, int value) {
        return switch (fieldName) {
            case HP -> new PokemonStats(value, attack, defence, specialAttack, specialDefence, speed);
            case ATTACK -> new PokemonStats(hp, value, defence, specialAttack, specialDefence, speed);
            case DEFENCE -> new PokemonStats(hp, attack, value, specialAttack, specialDefence, speed);
            case SPECIAL_ATTACK -> new PokemonStats(hp, attack, defence, value, specialDefence, speed);
            case SPECIAL_DEFENCE -> new PokemonStats(hp, attack, defence, specialAttack, value, speed);
            case SPEED -> new PokemonStats(hp, attack, defence, specialAttack, specialDefence, value);
            default -> throw new IllegalArgumentException("Unknown stat field: " + fieldName);
        };
    }

    /**
     * 判断是否至少有一项值大于 0（用于 JSON 有效性验证）。
     */
    public boolean hasAnyPositiveValue() {
        return hp > 0 || attack > 0 || defence > 0
            || specialAttack > 0 || specialDefence > 0 || speed > 0;
    }

    // ---- getters ----
    public int hp() { return hp; }
    public int attack() { return attack; }
    public int defence() { return defence; }
    public int specialAttack() { return specialAttack; }
    public int specialDefence() { return specialDefence; }
    public int speed() { return speed; }

    private static int getInt(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull()
            ? json.get(key).getAsInt() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PokemonStats that)) return false;
        return hp == that.hp && attack == that.attack && defence == that.defence
            && specialAttack == that.specialAttack && specialDefence == that.specialDefence
            && speed == that.speed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hp, attack, defence, specialAttack, specialDefence, speed);
    }

    @Override
    public String toString() {
        return String.format("Stats{HP:%d ATK:%d DEF:%d SpA:%d SpD:%d SPD:%d}",
            hp, attack, defence, specialAttack, specialDefence, speed);
    }
}

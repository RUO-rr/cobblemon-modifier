package com.cobblemon.modifier.model;

import java.io.File;
import java.util.Objects;

/**
 * 一个招式定义的来源信息（不可变值对象）。
 *
 * <p>招式有两种来源：
 * <ul>
 *   <li><b>基础招式</b>：{@code <gameDir>/showdown/data/moves.js} 里的定义
 *       （{@code archive} 与 {@code entryPath} 为 null）；</li>
 *   <li><b>自定义招式</b>：数据包里的 {@code data/cobblemon/moves/&lt;id&gt;.js}
 *       （记录压缩包与包内路径）。</li>
 * </ul>
 */
public class MoveInfo {

    private final String id;
    private final String englishName;
    private final String sourceLabel;
    private final boolean custom;
    private final File archive;
    private final String entryPath;

    public MoveInfo(String id, String englishName, String sourceLabel,
                    boolean custom, File archive, String entryPath) {
        this.id = id;
        this.englishName = englishName == null || englishName.isBlank() ? id : englishName;
        this.sourceLabel = sourceLabel;
        this.custom = custom;
        this.archive = archive;
        this.entryPath = entryPath;
    }

    public String id() {
        return id;
    }

    public String englishName() {
        return englishName;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public boolean custom() {
        return custom;
    }

    public File archive() {
        return archive;
    }

    public String entryPath() {
        return entryPath;
    }

    /**
     * 覆盖文件在数据包里的路径。
     *
     * <p>统一写进 {@code cobblemon} 命名空间：Cobblemon 的招式以**文件名**为 id，
     * 世界数据包在模组之后加载，因此这一份会盖过所有来源。
     */
    public String overridePath() {
        return "data/cobblemon/moves/" + id + ".js";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MoveInfo info)) {
            return false;
        }
        return Objects.equals(id, info.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id;
    }
}

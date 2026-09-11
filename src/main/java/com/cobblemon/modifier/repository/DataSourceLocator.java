package com.cobblemon.modifier.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据源定位器 —— 决定"从哪些压缩包里读宝可梦数据、同名文件以哪份为准"。
 *
 * <p>背景：整合包里的魔改宝可梦（赛尔号系列等）并不在 {@code mods/*.jar} 里，
 * 而是以数据包形式放在 {@code resourcepacks} 与 {@code global_packs} 里。
 * 修改器过去只扫 mods 目录，于是这些精灵在列表里根本看不到。
 *
 * <p>优先级（高 → 低）：
 * <ol>
 *   <li>我们自己的覆盖文件（{@code config/cobblemonmodifier/overrides}）——
 *       由 {@link OverrideRepository} 处理，不在这里涉及；</li>
 *   <li>{@code global_packs/jostar_overrides} —— 整合包作者的覆盖修正包；</li>
 *   <li>{@code global_packs/required_data} —— 整合包的正式数据包；</li>
 *   <li>{@code resourcepacks} —— 与 required_data 同内容的一份拷贝，作为兜底；</li>
 *   <li>{@code mods} —— 模组 JAR。</li>
 * </ol>
 *
 * <p>数据包之间的先后顺序以 {@code saves/&lt;世界&gt;/level.dat} 里的 Enabled 列表为准
 * （列表越靠后优先级越高，我们的 {@code file/cobblemonmodifier} 永远排在最后）：
 * {@code required_data … → jostar_overrides → file/cobblemonmodifier}。
 * 也就是说 **jostar_overrides 的优先级高于 required_data**。
 * 另外实测同一个 zip 在 resourcepacks 与 required_data 各放一份时游戏只加载一份，
 * 所以 resourcepacks 只作为兜底。
 *
 * <p>数据包目录只收 {@code .zip}，mods 目录只收 {@code .jar}；
 * 玩家选定的目录（mods）的**同级目录**会被自动带上，因此不需要额外的界面设置。
 */
public final class DataSourceLocator {

    /** mods 目录的同级目录中，按优先级排列的数据包目录。 */
    private static final String[] DATA_PACK_DIRS = {
        "global_packs/jostar_overrides",
        "global_packs/required_data",
        "resourcepacks"
    };

    private DataSourceLocator() {
    }

    /**
     * 返回按优先级排序、且真实存在的数据源目录。
     *
     * @param modsFolder 玩家选定的目录（通常是 {@code <gameDir>/mods}）
     */
    public static List<File> roots(File modsFolder) {
        List<File> roots = new ArrayList<>();
        if (modsFolder == null) {
            return roots;
        }

        File gameDir = modsFolder.getParentFile();
        if (gameDir != null) {
            for (String relative : DATA_PACK_DIRS) {
                File candidate = new File(gameDir, relative);
                if (candidate.isDirectory()) {
                    roots.add(candidate);
                }
            }
        }

        if (modsFolder.isDirectory()) {
            roots.add(modsFolder);
        }
        return roots;
    }

    /**
     * 列出某个数据源目录下的压缩包。
     * mods 目录收 {@code .jar}，其它目录（数据包）收 {@code .zip}。
     */
    public static List<File> archives(File root, File modsFolder) {
        List<File> result = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return result;
        }

        boolean isModsRoot = modsFolder != null && root.equals(modsFolder);
        String suffix = isModsRoot ? ".jar" : ".zip";

        File[] files = root.listFiles((dir, name) -> name.toLowerCase().endsWith(suffix));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file.isFile()) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * 按优先级把压缩包文件名解析成真实文件；找不到返回 {@code null}。
     *
     * <p>同一文件名可能同时存在于数据包目录和 mods 目录（例如本整合包把同一批
     * 数据包在 resourcepacks 和 global_packs 各放了一份），这里始终取优先级最高的那份。
     */
    public static File resolve(File modsFolder, String fileName) {
        if (modsFolder == null || fileName == null || fileName.isEmpty()) {
            return null;
        }

        for (File root : roots(modsFolder)) {
            File candidate = new File(root, fileName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }
}

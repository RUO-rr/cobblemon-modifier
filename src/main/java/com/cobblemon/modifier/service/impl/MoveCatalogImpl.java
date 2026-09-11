package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.repository.DataSourceLocator;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.service.MoveCatalog;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 已知招式表的默认实现，结果只构建一次并缓存。
 */
public class MoveCatalogImpl implements MoveCatalog {

    private static final Logger log = LoggerFactory.getLogger(MoveCatalogImpl.class);

    /** showdown/data/moves.js 里形如 {@code \n  tackle: {} } 的顶层键。 */
    private static final Pattern SHOWDOWN_KEY = Pattern.compile("(?m)^\\s{2}([a-z0-9]+):\\s*\\{");

    /** 数据包里的自定义招式脚本：data/<命名空间>/moves/任意层级/招式id.js */
    private static final Pattern CUSTOM_MOVE_ENTRY =
        Pattern.compile("^data/[^/]+/moves/(.+)\\.js$");

    private final JarRepository jarRepo;
    private final Path gameDir;

    private volatile Set<String> cached;

    public MoveCatalogImpl(JarRepository jarRepo) {
        this(jarRepo, FabricLoader.getInstance().getGameDir());
    }

    public MoveCatalogImpl(JarRepository jarRepo, Path gameDir) {
        this.jarRepo = jarRepo;
        this.gameDir = gameDir;
    }

    @Override
    public Set<String> knownMoveIds() {
        Set<String> local = cached;
        if (local == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = build();
                }
                local = cached;
            }
        }
        return local;
    }

    @Override
    public boolean isReady() {
        return !knownMoveIds().isEmpty();
    }

    private Set<String> build() {
        Set<String> ids = new HashSet<>();
        int base = readShowdownMoves(ids);
        int custom = readCustomMoves(ids);
        log.info("已知招式表构建完成：基础 {} 条 + 自定义 {} 条 = {} 条", base, custom, ids.size());
        return Collections.unmodifiableSet(ids);
    }

    /** 读取 showdown 的基础招式表（Cobblemon 自己解包出来的那份）。 */
    private int readShowdownMoves(Set<String> ids) {
        Path file = gameDir.resolve("showdown").resolve("data").resolve("moves.js");
        if (!Files.isRegularFile(file)) {
            log.warn("找不到 showdown 招式表，跳过基础招式：{}", file);
            return 0;
        }
        int before = ids.size();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = SHOWDOWN_KEY.matcher(text);
            while (matcher.find()) {
                ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            log.warn("解析 showdown 招式表失败：{}", e.getMessage());
        }
        return ids.size() - before;
    }

    /** 收集数据包里的自定义招式脚本（文件名即招式 id）。 */
    private int readCustomMoves(Set<String> ids) {
        File modsDir = gameDir.resolve("mods").toFile();
        int before = ids.size();
        for (File root : DataSourceLocator.roots(modsDir)) {
            for (File archive : DataSourceLocator.archives(root, modsDir)) {
                List<String> entries;
                try {
                    entries = jarRepo.listEntries(archive,
                        path -> CUSTOM_MOVE_ENTRY.matcher(path).matches());
                } catch (Exception e) {
                    log.warn("读取招式脚本失败：{} - {}", archive.getName(), e.getMessage());
                    continue;
                }
                for (String entry : entries) {
                    Matcher matcher = CUSTOM_MOVE_ENTRY.matcher(entry);
                    if (!matcher.matches()) {
                        continue;
                    }
                    String fileName = matcher.group(1);
                    int slash = fileName.lastIndexOf('/');
                    if (slash >= 0) {
                        fileName = fileName.substring(slash + 1);
                    }
                    ids.add(normalize(fileName));
                }
            }
        }
        return ids.size() - before;
    }

    /** 招式 id 规范化：小写 + 去掉非字母数字（与 Cobblemon / Showdown 的写法一致）。 */
    private static String normalize(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }
}

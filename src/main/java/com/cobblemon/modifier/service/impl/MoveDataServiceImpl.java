package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.core.util.MoveScriptUtil;
import com.cobblemon.modifier.model.MoveData;
import com.cobblemon.modifier.model.MoveInfo;
import com.cobblemon.modifier.repository.DataSourceLocator;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.repository.OverrideRepository;
import com.cobblemon.modifier.service.MoveDataService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招式数据服务的默认实现。
 *
 * <p>索引构建顺序即优先级：先收数据包里的自定义招式（高优先级数据源在前），
 * 再补 {@code showdown/data/moves.js} 的基础招式；同 id 时前者胜出——
 * 这与游戏里的实际加载顺序一致（数据包在模组之后加载）。
 */
public class MoveDataServiceImpl implements MoveDataService {

    private static final Logger log = LoggerFactory.getLogger(MoveDataServiceImpl.class);

    /** 数据包里的自定义招式脚本：data/cobblemon/moves/<任意层级>/<id>.js */
    private static final Pattern CUSTOM_MOVE_ENTRY =
        Pattern.compile("^data/cobblemon/moves/(.+)\\.js$");

    private static final String BASE_SOURCE = "showdown/data/moves.js";

    /** 合法属性（Showdown 的 18 种 + 太晶星的 Stellar）。 */
    private static final Set<String> TYPES = Set.of(
        "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison",
        "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark",
        "steel", "fairy", "stellar");

    private final JarRepository jarRepo;
    private final OverrideRepository overrideRepo;
    private final Path gameDir;

    private volatile String indexKey;
    private volatile List<MoveInfo> index = List.of();
    private volatile String bundleText;

    public MoveDataServiceImpl(JarRepository jarRepo, OverrideRepository overrideRepo, Path gameDir) {
        this.jarRepo = jarRepo;
        this.overrideRepo = overrideRepo;
        this.gameDir = gameDir;
    }

    // ================================================================
    // 索引与搜索
    // ================================================================

    @Override
    public List<MoveInfo> search(File modsFolder, String query, int limit) {
        List<MoveInfo> all = indexOf(modsFolder);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MoveInfo> hits = new ArrayList<>();
        for (MoveInfo info : all) {
            if (needle.isEmpty()
                || info.id().contains(needle)
                || info.englishName().toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(info);
            }
        }
        if (!needle.isEmpty()) {
            hits.sort(Comparator
                .comparingInt((MoveInfo info) -> info.id().equals(needle) ? 0 : 1)
                .thenComparing(MoveInfo::id));
        }
        if (limit > 0 && hits.size() > limit) {
            return List.copyOf(hits.subList(0, limit));
        }
        return List.copyOf(hits);
    }

    @Override
    public int totalCount(File modsFolder) {
        return indexOf(modsFolder).size();
    }

    @Override
    public void invalidate() {
        indexKey = null;
        index = List.of();
        bundleText = null;
    }

    private List<MoveInfo> indexOf(File modsFolder) {
        String key = modsFolder == null ? "" : modsFolder.getAbsolutePath();
        if (key.equals(indexKey) && !index.isEmpty()) {
            return index;
        }
        synchronized (this) {
            if (!key.equals(indexKey) || index.isEmpty()) {
                index = buildIndex(modsFolder);
                indexKey = key;
            }
            return index;
        }
    }

    private List<MoveInfo> buildIndex(File modsFolder) {
        long start = System.currentTimeMillis();
        Map<String, MoveInfo> byId = new LinkedHashMap<>();
        int custom = 0;
        for (File root : DataSourceLocator.roots(modsFolder)) {
            for (File archive : DataSourceLocator.archives(root, modsFolder)) {
                List<String> entries;
                try {
                    entries = jarRepo.listEntries(archive,
                        path -> CUSTOM_MOVE_ENTRY.matcher(path).matches());
                } catch (Exception e) {
                    log.warn("读取招式脚本列表失败：{} - {}", archive.getName(), e.getMessage());
                    continue;
                }
                if (entries.isEmpty()) {
                    continue;
                }
                Map<String, String> texts;
                try {
                    texts = jarRepo.readTextBatch(archive, entries);
                } catch (Exception e) {
                    log.warn("读取招式脚本失败：{} - {}", archive.getName(), e.getMessage());
                    continue;
                }
                for (Map.Entry<String, String> entry : texts.entrySet()) {
                    Matcher matcher = CUSTOM_MOVE_ENTRY.matcher(entry.getKey());
                    if (!matcher.matches()) {
                        continue;
                    }
                    String id = MoveScriptUtil.normalizeId(lastSegment(matcher.group(1)));
                    if (id.isEmpty() || byId.containsKey(id)) {
                        continue;
                    }
                    byId.put(id, new MoveInfo(id,
                        unquote(MoveScriptUtil.readField(entry.getValue(), "name")),
                        archive.getName(), true, archive, entry.getKey()));
                    custom++;
                }
            }
        }
        String bundle = readBundle();
        Map<String, String> baseMoves = MoveScriptUtil.readShowdownMoves(bundle);
        int base = 0;
        for (Map.Entry<String, String> entry : baseMoves.entrySet()) {
            if (byId.containsKey(entry.getKey())) {
                continue;
            }
            byId.put(entry.getKey(), new MoveInfo(entry.getKey(), entry.getValue(),
                BASE_SOURCE, false, null, null));
            base++;
        }
        List<MoveInfo> result = new ArrayList<>(byId.values());
        result.sort(Comparator.comparing(MoveInfo::id));
        log.info("招式索引构建完成：自定义 {} 条 + 基础 {} 条 = {} 条（{} ms）",
            custom, base, result.size(), System.currentTimeMillis() - start);
        return List.copyOf(result);
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String readBundle() {
        String cached = bundleText;
        if (cached != null) {
            return cached;
        }
        Path file = gameDir.resolve("showdown").resolve("data").resolve("moves.js");
        if (!Files.isRegularFile(file)) {
            log.warn("找不到基础招式表：{}", file);
            bundleText = "";
            return bundleText;
        }
        try {
            bundleText = MoveScriptUtil.normalizeNewlines(
                Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("读取基础招式表失败：{}", e.getMessage());
            bundleText = "";
        }
        return bundleText;
    }

    // ================================================================
    // 读取与保存
    // ================================================================

    @Override
    public MoveData load(MoveInfo info) throws Exception {
        String override = overrideRepo.readOverrideText(info.overridePath());
        String text = override != null ? override : sourceText(info);
        return new MoveData(
            info.id(),
            MoveScriptUtil.readField(text, "name"),
            MoveScriptUtil.readField(text, "type"),
            MoveScriptUtil.readField(text, "basePower"),
            MoveScriptUtil.readField(text, "accuracy"),
            MoveScriptUtil.readField(text, "pp"),
            MoveScriptUtil.readField(text, "priority"),
            override != null,
            info.sourceLabel());
    }

    @Override
    public String save(MoveInfo info, Map<String, String> values) throws Exception {
        MoveData current = load(info);
        Map<String, String> literals = new LinkedHashMap<>();
        List<String> changes = new ArrayList<>();

        // 已经有覆盖就在覆盖上继续改，避免把之前改过的字段又退回原版
        String overrideText = overrideRepo.readOverrideText(info.overridePath());
        String script = overrideText != null ? overrideText : sourceText(info);

        for (String field : MoveScriptUtil.EDITABLE_FIELDS) {
            if (!values.containsKey(field)) {
                continue;
            }
            String literal = validate(field, values.get(field));
            String before = MoveScriptUtil.readField(script, field);
            if (literal.equals(before)) {
                continue;
            }
            literals.put(field, literal);
            changes.add(label(field) + " " + display(before) + " → " + display(literal));
        }

        if (literals.isEmpty()) {
            return "没有改动内容";
        }
        String patched = MoveScriptUtil.writeFields(script, literals);
        overrideRepo.writeOverrideText(info.overridePath(), patched);
        return String.join("；", changes);
    }

    @Override
    public boolean restore(MoveInfo info) throws Exception {
        return overrideRepo.deleteOverride(info.overridePath());
    }

    @Override
    public boolean hasOverride(MoveInfo info) {
        return overrideRepo.readOverrideText(info.overridePath()) != null;
    }

    /** 读取招式在游戏里的原始定义文本。 */
    private String sourceText(MoveInfo info) throws Exception {
        if (info.custom() && info.archive() != null && info.entryPath() != null) {
            return MoveScriptUtil.normalizeNewlines(
                jarRepo.readText(info.archive(), info.entryPath()));
        }
        String entry = MoveScriptUtil.extractEntry(readBundle(), info.id());
        if (entry == null) {
            throw new IllegalStateException("找不到招式定义：" + info.id());
        }
        return entry;
    }

    // ================================================================
    // 校验
    // ================================================================

    private static String validate(String field, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label(field) + " 不能为空");
        }
        return switch (field) {
            case "basePower" -> String.valueOf(parseInt(field, value, 0, 999));
            case "pp" -> String.valueOf(parseInt(field, value, 1, 200));
            case "priority" -> String.valueOf(parseInt(field, value, -7, 7));
            case "accuracy" -> validateAccuracy(value);
            case "type" -> "\"" + validateType(value) + "\"";
            default -> value;
        };
    }

    private static int parseInt(String field, String value, int min, int max) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label(field) + " 不是整数：" + value);
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(
                label(field) + " 必须在 " + min + " ~ " + max + " 之间（现在是 " + parsed + "）");
        }
        return parsed;
    }

    private static String validateAccuracy(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("命中不能是 false；必中请填 true");
        }
        return String.valueOf(parseInt("accuracy", value, 1, 100));
    }

    private static String validateType(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (!TYPES.contains(lower)) {
            throw new IllegalArgumentException("未知属性：" + value + "（示例：Fire / Water / Grass）");
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String label(String field) {
        return switch (field) {
            case "basePower" -> "威力";
            case "accuracy" -> "命中";
            case "pp" -> "PP";
            case "priority" -> "优先级";
            case "type" -> "属性";
            default -> field;
        };
    }

    /** 界面展示用的值：去掉字符串字面量的引号。 */
    private static String display(String literal) {
        return literal == null ? "（无）" : unquote(literal);
    }

    /** 去掉字符串字面量的引号。 */
    private static String unquote(String literal) {
        if (literal == null) {
            return "";
        }
        String value = literal.trim();
        if (value.length() >= 2
            && (value.charAt(0) == '"' || value.charAt(0) == '\'')
            && value.charAt(value.length() - 1) == value.charAt(0)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

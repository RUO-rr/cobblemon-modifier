package com.cobblemon.modifier.controller;

import com.google.gson.JsonObject;
import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.util.JsonUtil;
import com.cobblemon.modifier.core.util.PokemonJsonFilter;
import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.model.MoveData;
import com.cobblemon.modifier.model.MoveInfo;
import com.cobblemon.modifier.model.SpawnBucketConfig;
import com.cobblemon.modifier.model.SpawnEntry;
import com.cobblemon.modifier.plugin.*;
import com.cobblemon.modifier.repository.ConfigRepository;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.repository.OverrideRepository;
import com.cobblemon.modifier.service.ModifyService;
import com.cobblemon.modifier.service.ScanService;
import com.cobblemon.modifier.service.SpawnConfigService;
import com.cobblemon.modifier.service.SpawnRateService;
import com.cobblemon.modifier.service.MegaLimitService;
import com.cobblemon.modifier.service.MoveDataService;
import com.cobblemon.modifier.ui.MainFrame;
import com.cobblemon.modifier.ui.PluginPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 主控制器 —— MVC 架构中的 Controller 层。
 *
 * <p>职责：接收 View 的事件，调度 Service 执行业务逻辑，将结果返回给 View。
 * 禁止：直接操作 JAR 文件、直接读写 JSON、直接操作线程（只通过 ExecutorService）。</p>
 *
 * <p>Phase 3 改造：插件通过构造函数注入（由 {@code ModifierContainer} 通过 ServiceLoader 发现）。
 * Phase 1 改造：去除所有 Swing 依赖（SwingUtilities / JOptionPane / JFileChooser），
 * 用 SLF4J logger 替代对话框提示。UI 交互将在 Phase 3 通过 Minecraft Screen 恢复。</p>
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    /**
     * 扫描规则版本：改动"哪些文件算宝可梦数据"时 +1。
     *
     * <p>v2：扫描不再限定 {@code data/cobblemon/...}，任何命名空间的
     * {@code species} / {@code species_additions} 都收（魔改 Mega 石在 {@code data/newsmega/...}）。
     */
    private static final int SCAN_RULE_VERSION = 2;

    // ---- 依赖（构造函数注入） ----
    private final MainFrame view;
    private final PluginPanel pluginPanel;
    private final ScanService scanService;
    private final ModifyService modifyService;
    private final SpawnRateService spawnRateService;
    private final SpawnConfigService spawnConfigService;
    private final MegaLimitService megaLimitService;
    private final MoveDataService moveDataService;
    private final JarRepository jarRepo;
    private final ConfigRepository configRepo;
    private final OverrideRepository overrideRepo;
    private final ExecutorService executor;
    private final Map<String, JsonModifier> plugins;
    private final Runnable datapackSync;

    // ---- 状态 ----
    private File selectedFolder;
    private final Map<String, List<JarResourcePath>> pluginCache = new ConcurrentHashMap<>();
    private volatile List<JarResourcePath> currentPaths = List.of();
    private final List<JarResourcePath> globalPokemonPaths = new CopyOnWriteArrayList<>();
    private JarResourcePath selectedPath;
    private String selectedSpeciesName;
    private final AtomicLong selectionToken = new AtomicLong();
    /** 扫描闸门：同一时间只允许一个扫描任务，避免重复点击把任务堆在 2 个线程的池子里。 */
    private final java.util.concurrent.atomic.AtomicBoolean scanning =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public MainController(MainFrame view,
                          PluginPanel pluginPanel,
                          ScanService scanService,
                          ModifyService modifyService,
                          JarRepository jarRepo,
                          ConfigRepository configRepo,
                          OverrideRepository overrideRepo,
                          SpawnRateService spawnRateService,
                          SpawnConfigService spawnConfigService,
                          MegaLimitService megaLimitService,
                          MoveDataService moveDataService,
                          ExecutorService executor,
                          Map<String, JsonModifier> plugins,
                          Runnable datapackSync) {
        this.view = view;
        this.pluginPanel = pluginPanel;
        this.scanService = scanService;
        this.modifyService = modifyService;
        this.jarRepo = jarRepo;
        this.configRepo = configRepo;
        this.overrideRepo = overrideRepo;
        this.spawnRateService = spawnRateService;
        this.spawnConfigService = spawnConfigService;
        this.megaLimitService = megaLimitService;
        this.moveDataService = moveDataService;
        this.executor = executor;
        this.plugins = plugins;
        this.datapackSync = datapackSync != null ? datapackSync : () -> { };
    }

    // ================================================================
    // 初始化
    // ================================================================

    /** 覆盖文件暂存根目录（config/cobblemonmodifier/overrides）。 */
    public Path getOverrideRoot() {
        return overrideRepo.getStagingRoot();
    }

    public void initialize() {
        view.populatePluginCombo(new ArrayList<>(plugins.keySet()));

        // 扫描规则变了（例如放开命名空间）就丢掉旧缓存，
        // 否则界面会继续用升级前的列表，看起来像"新数据没加载进来"
        int cachedRuleVersion = configRepo.getScanRuleVersion();
        if (cachedRuleVersion != SCAN_RULE_VERSION) {
            log.info("扫描规则版本变化（{} → {}），清除旧的插件缓存",
                cachedRuleVersion, SCAN_RULE_VERSION);
            configRepo.clearAllPluginCaches();
            configRepo.saveScanRuleVersion(SCAN_RULE_VERSION);
        }

        String lastFolder = configRepo.getLastFolderPath();
        if (lastFolder != null) {
            File folder = new File(lastFolder);
            if (folder.exists() && folder.isDirectory()) {
                this.selectedFolder = folder;
                view.setFolderPath(folder.getAbsolutePath());
                loadPluginCaches();
                autoLoadDefaultPlugin();
            }
        }
    }

    private void loadPluginCaches() {
        for (String name : plugins.keySet()) {
            List<String> raw = configRepo.getPluginCache(name);
            if (!raw.isEmpty()) {
                List<JarResourcePath> paths = raw.stream()
                    .map(JarResourcePath::tryParse)
                    .filter(Objects::nonNull)
                    .toList();
                if (!paths.isEmpty()) {
                    pluginCache.put(name, paths);
                }
            }
        }
    }

    private void autoLoadDefaultPlugin() {
        if (plugins.isEmpty()) return;
        String firstName = plugins.keySet().iterator().next();
        List<JarResourcePath> cached = pluginCache.get(firstName);
        if (cached != null && !cached.isEmpty()) {
            this.currentPaths = cached;
            refreshViewList();
        }
    }

    // ================================================================
    // 用户操作：选择文件夹
    // ================================================================

    public void onFolderSelected(File folder) {
        this.selectedFolder = folder;
        configRepo.saveLastFolderPath(folder.getAbsolutePath());
        view.setFolderPath(folder.getAbsolutePath());

        pluginCache.clear();
        configRepo.clearAllPluginCaches();
        globalPokemonPaths.clear();
        currentPaths = List.of();
        selectedPath = null;
        selectedSpeciesName = null;
        selectionToken.incrementAndGet();
        view.clearJsonList();

        if (!plugins.isEmpty()) {
            view.selectPlugin(0);
        }

        onPluginSwitched(view.getSelectedPluginName());
    }

    // ================================================================
    // 用户操作：切换插件
    // ================================================================

    public void onPluginSwitched(String pluginName) {
        if (selectedFolder == null || !selectedFolder.isDirectory()) return;

        JsonModifier plugin = plugins.get(pluginName);
        if (plugin == null) return;

        // 种族值/特性/技能插件始终使用全局宝可梦列表：
        // 这份列表同时包含物种本体与物种覆盖文件（species_additions），
        // 而"技能修改"正需要后者——整合包常用它整体替换掉招式表。
        if ((plugin instanceof BaseStatsModifier || plugin instanceof AbilityModifier
            || plugin instanceof SpawnRateModifier || plugin instanceof MoveModifier)
            && !globalPokemonPaths.isEmpty()) {
            this.currentPaths = globalPokemonPaths;
            cachePluginPaths(pluginName, globalPokemonPaths);
            refreshViewList();
            restoreSelectionAfterPluginSwitch(pluginName);
            return;
        }

        List<JarResourcePath> cached = pluginCache.get(pluginName);
        if (cached != null && !cached.isEmpty()) {
            this.currentPaths = cached;
            refreshViewList();
            view.appendRecord("切换插件：" + pluginName + " (缓存 " + cached.size() + " 个文件)\n");
            restoreSelectionAfterPluginSwitch(pluginName);
            return;
        }

        view.setPluginComboEnabled(false);
        executor.submit(() -> doScanAndValidate(pluginName, jarName -> true));
    }

    // ================================================================
    // 用户操作：加载所有宝可梦
    // ================================================================

    public void onLoadAllPokemon() {
        if (selectedFolder == null || !selectedFolder.isDirectory()) return;

        if (!globalPokemonPaths.isEmpty()) {
            log.info("已加载 {} 个宝可梦文件，重新扫描", globalPokemonPaths.size());
        }

        // 同一时间只允许一个扫描：重复点按钮不会把任务堆在只有 2 个线程的池子里
        if (!scanning.compareAndSet(false, true)) {
            log.warn("已有扫描在进行中，忽略本次请求");
            view.appendRecord("[提示] 上一次扫描还在进行，请稍等");
            return;
        }

        view.setLoadPokemonButtonEnabled(false);
        executor.submit(() -> {
            try {
                ScanService.ScanResult result = scanService.scanAndValidate(
                    selectedFolder,
                    CustomPokemonScanner::isTargetJar,
                    this::isValidPokemonJson,
                    (cur, total, status) -> view.updateProgress(cur, total, status)
                );

                globalPokemonPaths.clear();
                globalPokemonPaths.addAll(result.validPaths());

                view.setLoadPokemonButtonText("已加载 " + result.validPaths().size() + " 个");
                view.appendRecord("加载宝可梦：共 " + result.validPaths().size() + " 个文件\n");

                String currentPlugin = view.getSelectedPluginName();
                JsonModifier currentMod = plugins.get(currentPlugin);
                if (currentMod instanceof BaseStatsModifier || currentMod instanceof AbilityModifier
                    || currentMod instanceof SpawnRateModifier || currentMod instanceof TypeModifier) {
                    this.currentPaths = result.validPaths();
                    cachePluginPaths(currentPlugin, result.validPaths());
                    refreshViewList();
                }

                log.info("扫描完成：总{}，有效{}，跳过{}",
                    result.totalCount(), result.validPaths().size(), result.skippedCount());
            } catch (Throwable t) {
                // 连 Error（比如 OOM）也记下来，否则任务会静默死掉、进度条永远停住
                log.error("加载宝可梦数据失败", t);
                view.showError("扫描失败：" + t);
            } finally {
                view.hideProgress();
                view.setLoadPokemonButtonEnabled(true);
                scanning.set(false);
            }
        });
    }

    // ================================================================
    // 用户操作：选中 JSON 文件
    // ================================================================

    public void onJsonFileSelected(JarResourcePath path) {
        if (path == null) return;

        JsonModifier plugin = plugins.get(view.getSelectedPluginName());
        if (plugin == null) return;

        selectedPath = path;
        // 先按文件名给出兜底名称，避免用户刚点完就保存时拿到上一次的物种。
        selectedSpeciesName = path.extractPokemonName();
        long token = selectionToken.incrementAndGet();
        executor.submit(() -> {
            try {
                JsonObject json = modifyService.readRaw(path, selectedFolder);
                if (selectionToken.get() != token) return;
                String name = JsonUtil.getString(json, "name");
                if (name != null && !name.isBlank()) {
                    selectedSpeciesName = name;
                }
                if (plugin instanceof SpawnRateModifier) {
                    spawnRateService.ensureIndexed(selectedFolder);
                }
                pluginPanel.renderPluginPanel(plugin, json);
                view.refreshCenterPanel();
            } catch (Exception e) {
                if (selectionToken.get() != token) return;
                view.showError("读取 JSON 失败：" + e.getMessage());
            }
        });
    }

    /**
     * 切换插件后，如果之前选中的文件仍在新列表中，则用新插件重新读取并恢复选中。
     * 找不到时清空选中，避免右侧残留旧插件的字段。
     */
    private void restoreSelectionAfterPluginSwitch(String pluginName) {
        if (selectedPath == null) {
            return;
        }
        if (!Objects.equals(pluginName, view.getSelectedPluginName())) {
            return;
        }
        String rawPath = selectedPath.toRawString();
        int index = indexOfRawPath(rawPath);
        if (index >= 0) {
            view.requestSelection(rawPath);
            onJsonFileSelected(selectedPath);
        } else {
            selectedPath = null;
            view.requestSelection("");
            view.clearCenterPanel();
            view.appendRecord("[提示] 切换插件后该文件不在列表中，请重新选择");
        }
    }

    private int indexOfRawPath(String rawPath) {
        for (int i = 0; i < currentPaths.size(); i++) {
            if (Objects.equals(currentPaths.get(i).toRawString(), rawPath)) {
                return i;
            }
        }
        return -1;
    }

    // ================================================================
    // 用户操作：保存修改
    // ================================================================

    public void onSave() {
        if (selectedFolder == null || selectedPath == null) {
            view.showError("请先选择文件夹和 JSON 文件");
            return;
        }

        JsonModifier plugin = plugins.get(view.getSelectedPluginName());
        if (plugin == null) {
            view.showError("请选择有效的修改插件");
            return;
        }


        Map<String, Object> newValues = pluginPanel.getPluginNewValues(plugin);
        executor.submit(() -> {
            try {
                String detail = plugin.usesCustomSave()
                    ? saveCustom(plugin, newValues)
                    : saveDefault(plugin, newValues);
                datapackSync.run();
                view.appendRecord("修改成功：" + detail
                    + "（" + plugin.getPluginName() + "，重进世界后生效，不用重启游戏）→ 覆盖目录 "
                    + overrideRepo.getStagingRoot());
                log.info("修改已保存为覆盖文件：{}", detail);
            } catch (Exception e) {
                view.showError("保存失败：" + e.getMessage());
            }
        });
    }

    private String saveDefault(JsonModifier plugin, Map<String, Object> newValues) throws Exception {
        int synced = modifyService.modifyAndSave(selectedPath, selectedFolder, plugin, newValues);
        return selectedPath.jsonPath()
            + (synced > 0 ? "（已同步 " + synced + " 个其它来源文件）" : "");
    }

    /**
     * 自定义保存流程：目前只有刷新率插件使用。
     */
    private String saveCustom(JsonModifier plugin, Map<String, Object> newValues) throws Exception {
        if (!(plugin instanceof SpawnRateModifier)) {
            throw new IllegalStateException("未知的自定义保存插件：" + plugin.getPluginName());
        }
        String species = resolveSpeciesName();
        if (species.isBlank()) {
            throw new IllegalStateException("无法确定宝可梦名称");
        }
        if (spawnRateService.getEntries(species).isEmpty()) {
            throw new IllegalStateException("该宝可梦没有可修改的刷新条目");
        }

        String bucket = null;
        Object rawBucket = newValues.get("bucket");
        if (rawBucket instanceof String text && !text.isBlank()
            && !SpawnRateModifier.KEEP_BUCKET.equals(text)) {
            bucket = text;
        }

        Float weight = null;
        Object rawWeight = newValues.get("weight");
        if (rawWeight instanceof Number number) {
            weight = number.floatValue();
        } else if (rawWeight instanceof String text && !text.isBlank()) {
            throw new IllegalStateException("请输入有效的权重数字");
        }
        if (bucket == null && weight == null) {
            throw new IllegalStateException("稀有等级和桶内权重都没有需要保存的变化");
        }

        int changed = spawnRateService.applyChanges(species, bucket, weight, selectedFolder);
        StringBuilder detail = new StringBuilder("刷新池 ")
            .append(changed).append(" 条条目（").append(species).append("）");
        if (bucket != null) {
            detail.append("，稀有等级→").append(bucket);
        }
        if (weight != null) {
            detail.append("，权重→").append(SpawnRateModifier.formatWeight(weight));
        }
        return detail.toString();
    }

    private String resolveSpeciesName() {
        if (selectedSpeciesName != null && !selectedSpeciesName.isBlank()) {
            return selectedSpeciesName;
        }
        return selectedPath != null ? selectedPath.extractPokemonName() : "";
    }

    // ================================================================
    // 用户操作：还原原版
    // ================================================================

    public void onRestoreOriginal() {
        if (selectedFolder == null || selectedPath == null) {
            view.showError("请先选择文件夹和 JSON 文件");
            return;
        }
        JsonModifier plugin = plugins.get(view.getSelectedPluginName());
        if (plugin == null) {
            view.showError("请选择有效的修改插件");
            return;
        }
        executor.submit(() -> {
            try {
                int removed = plugin instanceof SpawnRateModifier
                    ? spawnRateService.restore(resolveSpeciesName(), selectedFolder)
                    : modifyService.restoreOverrides(selectedPath, selectedFolder);
                datapackSync.run();
                if (removed > 0) {
                    view.appendRecord("已还原原版：" + selectedPath.jsonPath()
                        + "（删除 " + removed + " 个覆盖文件，重进世界后生效）");
                } else {
                    view.appendRecord("当前没有覆盖文件，该宝可梦已是原版数值。");
                }
                JsonObject json = modifyService.readRaw(selectedPath, selectedFolder);
                pluginPanel.renderPluginPanel(plugin, json);
                view.refreshCenterPanel();
            } catch (Exception e) {
                view.showError("还原失败：" + e.getMessage());
            }
        });
    }

    // ================================================================
    // 用户操作：全局稀有等级权重（best-spawner-config.json）
    // ================================================================

    public Path getSpawnConfigPath() {
        return spawnConfigService.getConfigPath();
    }

    public SpawnBucketConfig loadSpawnBucketConfig() throws Exception {
        return spawnConfigService.load();
    }

    /**
     * 保存全局稀有等级权重，并尝试调用 Cobblemon 的 reloadConfig 立即生效。
     *
     * @return true 表示热重载成功
     */
    public boolean saveSpawnBucketConfig(SpawnBucketConfig config) throws Exception {
        spawnConfigService.save(config);
        boolean reloaded = spawnConfigService.reloadBestSpawner();
        view.appendRecord(reloaded
            ? "稀有等级权重已保存并热重载，立即生效"
            : "稀有等级权重已保存；热重载失败，请重新进入世界或重启游戏生效");
        return reloaded;
    }

    // ================================================================
    // 用户操作：Mega 限制开关（mega_showdown 的 multipleMegas）
    // ================================================================

    /**
     * 当前是否允许同时存在多只 Mega。
     *
     * <p>评论区说的"解除 mega 限制"指的就是这一项：mega_showdown 默认同时只允许一只 Mega，
     * 打开这个开关后可以有多只。读不到配置时按"不允许"（默认值）处理。
     */
    public boolean isMultipleMegasAllowed() {
        try {
            return megaLimitService.isMultipleMegasEnabled();
        } catch (Exception e) {
            log.warn("读取 mega_showdown 配置失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 切换"允许多只 Mega"并热重载 mega_showdown 配置。
     *
     * @return true 表示写入成功（热重载是否成功见返回后的记录文字）
     */
    public boolean setMultipleMegasAllowed(boolean allowed) {
        try {
            boolean reloaded = megaLimitService.setMultipleMegasEnabled(allowed);
            view.appendRecord((allowed
                ? "已解除 Mega 限制：可以同时存在多只 Mega"
                : "已恢复 Mega 限制：同时只允许一只 Mega")
                + (reloaded ? "（已热重载，立即生效）" : "（需重启游戏生效）"));
            return true;
        } catch (Exception e) {
            log.warn("写入 mega_showdown 配置失败：{}", e.getMessage());
            view.showError("写入 mega_showdown 配置失败：" + e.getMessage());
            return false;
        }
    }

    /** mega_showdown 配置文件路径，便于界面提示。 */
    public Path getMegaLimitConfigPath() {
        return megaLimitService.getConfigPath();
    }

    // ================================================================
    // 招式数据（威力 / 命中 / PP / 优先级 / 属性）
    // ================================================================

    /** 当前选定的模组目录（通常是 {@code <gameDir>/mods}）。 */
    public File getSelectedFolder() {
        return selectedFolder;
    }

    /**
     * 搜索招式（基础招式 + 数据包自定义招式）。
     *
     * @param query 招式 id 或英文名的一部分，留空表示全部
     */
    public List<MoveInfo> searchMoves(String query, int limit) {
        return moveDataService.search(selectedFolder, query, limit);
    }

    /** 招式总数。 */
    public int getMoveTotalCount() {
        return moveDataService.totalCount(selectedFolder);
    }

    /** 读取招式当前数值（有覆盖时以覆盖为准）。 */
    public MoveData loadMove(MoveInfo info) throws Exception {
        return moveDataService.load(info);
    }

    /**
     * 保存招式数值并同步到当前世界的数据包。
     *
     * @return 变更说明，例如 {@code "威力 40 → 80"}
     */
    public String saveMove(MoveInfo info, java.util.Map<String, String> values) throws Exception {
        String detail = moveDataService.save(info, values);
        datapackSync.run();
        return detail;
    }

    /** 删除该招式的覆盖（恢复原版），并同步数据包。 */
    public boolean restoreMove(MoveInfo info) throws Exception {
        boolean deleted = moveDataService.restore(info);
        datapackSync.run();
        return deleted;
    }

    /** 招式覆盖文件的暂存目录，便于界面提示。 */
    public Path getMoveOverrideRoot() {
        return overrideRepo.getStagingRoot().resolve("data").resolve("cobblemon").resolve("moves");
    }

    // ================================================================
    // 用户操作：字段查找（全局宝可梦搜索）
    // ================================================================

    /** 一条搜索命中结果。 */
    public record PokemonSearchHit(String rawPath, String pokemonName, String matchedAbility) {
    }

    public List<JarResourcePath> getGlobalPokemonPaths() {
        return List.copyOf(globalPokemonPaths);
    }

    /**
     * 在全局宝可梦列表中执行字段查找（后台线程），完成后回调。
     */
    public void searchGlobalPokemon(FieldSearchModifier.SearchField field, String value,
                                    Consumer<List<PokemonSearchHit>> callback) {
        if (selectedFolder == null || !selectedFolder.isDirectory() || globalPokemonPaths.isEmpty()) {
            callback.accept(List.of());
            return;
        }
        FieldSearchModifier searchModifier = new FieldSearchModifier();
        searchModifier.setSearchField(field);
        searchModifier.setTargetValue(value);

        List<JarResourcePath> snapshot = List.copyOf(globalPokemonPaths);
        File folder = selectedFolder;
        executor.submit(() -> {
            List<PokemonSearchHit> hits = new ArrayList<>();
            for (JarResourcePath path : snapshot) {
                try {
                    JsonObject json = modifyService.readRaw(path, folder);
                    if (searchModifier.matchesSearchCriteria(json)) {
                        hits.add(new PokemonSearchHit(
                            path.toRawString(),
                            describePokemon(json, path),
                            searchModifier.findMatchedAbility(json)));
                    }
                } catch (Exception e) {
                    log.warn("搜索读取失败：{} - {}", path.toRawString(), e.getMessage());
                }
            }
            callback.accept(hits);
        });
    }

    /**
     * 搜索结果跳转前的准备：确保当前插件是物种编辑器，并把列表恢复为全局宝可梦列表。
     */
    public void prepareForSearchJump(String rawPath) {
        if (selectedFolder == null || rawPath == null) {
            return;
        }
        JsonModifier current = plugins.get(view.getSelectedPluginName());
        if (!(current instanceof BaseStatsModifier || current instanceof AbilityModifier)) {
            String speciesPlugin = findSpeciesPluginName();
            if (speciesPlugin != null) {
                view.selectPluginByName(speciesPlugin);
            }
        }
        if (!globalPokemonPaths.isEmpty()) {
            currentPaths = globalPokemonPaths;
            refreshViewList();
        }
    }

    private String findSpeciesPluginName() {
        for (Map.Entry<String, JsonModifier> entry : plugins.entrySet()) {
            if (entry.getValue() instanceof BaseStatsModifier) {
                return entry.getKey();
            }
        }
        for (Map.Entry<String, JsonModifier> entry : plugins.entrySet()) {
            if (entry.getValue() instanceof AbilityModifier) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String describePokemon(JsonObject json, JarResourcePath path) {
        StringBuilder label = new StringBuilder();
        if (json != null && json.has("nationalPokedexNumber")
            && json.get("nationalPokedexNumber").isJsonPrimitive()) {
            try {
                label.append('#').append(json.get("nationalPokedexNumber").getAsInt()).append(' ');
            } catch (RuntimeException ignored) {
                // 编号不是整数时忽略
            }
        }
        String name = null;
        if (json != null && json.has("name") && json.get("name").isJsonPrimitive()) {
            name = json.get("name").getAsString();
        }
        if (name == null || name.isBlank()) {
            name = path.jsonPath();
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            if (name.endsWith(".json")) {
                name = name.substring(0, name.length() - 5);
            }
        }
        label.append(name);
        return label.toString();
    }

    // ================================================================
    // 用户操作：备份 JAR
    // ================================================================

    public void onBackupJar() {
        if (selectedFolder == null || !selectedFolder.isDirectory()) {
            view.showError("请先选择文件夹");
            return;
        }

        File[] jarFiles = selectedFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            view.showError("文件夹中没有 JAR 文件");
            return;
        }

        File source = jarFiles[0];
        File backupFile = new File(source.getAbsolutePath().replace(".jar", "_backup.jar"));
        try {
            String backupPath = jarRepo.backup(source, backupFile);
            view.appendRecord("备份 JAR：" + new File(backupPath).getName() + "\n");
            log.info("备份成功：{}", backupPath);
        } catch (Exception ex) {
            view.showError("备份失败：" + ex.getMessage());
        }
    }

    // ================================================================
    // 用户操作：搜索过滤
    // ================================================================

    public void onEnglishSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            refreshViewList();
            return;
        }
        String kw = keyword.trim().toLowerCase();
        List<JarResourcePath> filtered = currentPaths.stream()
            .filter(p -> p.toRawString().toLowerCase().contains(kw))
            .toList();
        view.refreshJsonList(filtered);
    }

    // ================================================================
    // 内部辅助方法
    // ================================================================

    private void doScanAndValidate(String pluginName,
                                    java.util.function.Predicate<String> jarFilter) {
        // 与"加载宝可梦数据"共用同一个扫描闸门，避免两个扫描同时抢 2 个线程
        if (!scanning.compareAndSet(false, true)) {
            log.warn("已有扫描在进行中，忽略插件 {} 的扫描请求", pluginName);
            view.setPluginComboEnabled(true);
            return;
        }
        try {
            view.showProgress(0);

            JsonModifier plugin = plugins.get(pluginName);
            ScanService.ScanResult result = scanService.scanAndValidate(
                selectedFolder,
                jarFilter,
                plugin != null ? plugin::hasValidStatsFields : json -> true,
                (cur, total, status) -> view.updateProgress(cur, total, status)
            );

            this.currentPaths = result.validPaths();
            cachePluginPaths(pluginName, result.validPaths());
            refreshViewList();
            restoreSelectionAfterPluginSwitch(pluginName);
            view.appendRecord(String.format("扫描到 %d 个文件，有效 %d 个\n",
                result.totalCount(), result.validPaths().size()));
        } catch (Throwable t) {
            log.error("插件 {} 扫描失败", pluginName, t);
            view.showError("扫描失败：" + t);
        } finally {
            view.hideProgress();
            view.setPluginComboEnabled(true);
            scanning.set(false);
        }
    }

    private void cachePluginPaths(String pluginName, List<JarResourcePath> paths) {
        pluginCache.put(pluginName, paths);
        configRepo.savePluginCache(pluginName,
            paths.stream().map(JarResourcePath::toRawString).toList());
    }

    private void refreshViewList() {
        view.refreshJsonList(currentPaths);
    }

    /**
     * 判定一份 JSON 是否该出现在列表里。
     *
     * <p>两类：
     * <ol>
     *   <li><b>物种本体</b>：必须有 baseStats。本整合包 {@code data/*&#47;species/**}
     *       共 1391 个文件全部带 baseStats；而图鉴条目（dex_entries）、骑乘参数（cobbleride）
     *       这些数据都没有，过去那条"有 forms 就算宝可梦"的宽松规则会把上千条垃圾收进来。</li>
     *   <li><b>物种覆盖文件</b>（{@code species_additions}）：用 {@code target} 指向被覆盖的物种，
     *       优先级高于本体。地龙的招式表就是被 {@code garchomp_move.json} 整体替换掉的，
     *       所以这类文件必须列出来，否则"改了本体却没效果"。</li>
     * </ol>
     */
    private boolean isValidPokemonJson(JsonObject json) {
        if (PokemonJsonFilter.isEditableAddition(json)) {
            return true;
        }
        if (!PokemonJsonFilter.isSpeciesData(json)) {
            return false;
        }
        JsonModifier statsPlugin = plugins.get("种族值修改");
        return statsPlugin == null || statsPlugin.hasValidStatsFields(json);
    }

    // ================================================================
    // Getters（供 DI 容器使用）
    // ================================================================

    public MainFrame getView() {
        return view;
    }
}

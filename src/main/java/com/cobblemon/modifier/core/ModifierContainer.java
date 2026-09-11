package com.cobblemon.modifier.core;

import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.plugin.CustomPokemonScanner;
import com.cobblemon.modifier.plugin.FieldSearchModifier;
import com.cobblemon.modifier.plugin.MoveModifier;
import com.cobblemon.modifier.plugin.SpawnRateModifier;
import com.cobblemon.modifier.repository.ConfigRepository;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.repository.OverrideRepository;
import com.cobblemon.modifier.repository.impl.ConfigRepositoryImpl;
import com.cobblemon.modifier.repository.impl.JarRepositoryImpl;
import com.cobblemon.modifier.repository.impl.OverrideRepositoryImpl;
import com.cobblemon.modifier.service.ModifyService;
import com.cobblemon.modifier.service.MegaLimitService;
import com.cobblemon.modifier.service.MoveCatalog;
import com.cobblemon.modifier.service.ScanService;
import com.cobblemon.modifier.service.SpawnConfigService;
import com.cobblemon.modifier.service.SpawnRateService;
import com.cobblemon.modifier.service.SpeciesOverrideIndex;
import com.cobblemon.modifier.service.impl.ModifyServiceImpl;
import com.cobblemon.modifier.service.impl.MegaLimitServiceImpl;
import com.cobblemon.modifier.service.impl.MoveCatalogImpl;
import com.cobblemon.modifier.service.impl.ScanServiceImpl;
import com.cobblemon.modifier.service.impl.SpawnConfigServiceImpl;
import com.cobblemon.modifier.service.impl.SpawnRateServiceImpl;
import com.cobblemon.modifier.service.impl.SpeciesOverrideIndexImpl;
import com.cobblemon.modifier.ui.MainFrame;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中央 DI 容器 —— 替代所有散落的 {@code new} 调用。
 *
 * <p>Phase 3 起保留装配结果（View / Controller）：
 * 客户端入口按 K 时通过 {@link #getController()} 打开 Minecraft Screen。
 */
public class ModifierContainer {

    // ---- 单例 ----
    private final JarRepository jarRepo;
    private final ConfigRepository configRepo;
    private final OverrideRepository overrideRepo;
    private final ScanService scanService;
    private final ModifyService modifyService;
    private final SpawnRateService spawnRateService;
    private final SpawnConfigService spawnConfigService;
    private final MegaLimitService megaLimitService;
    private final MoveCatalog moveCatalog;
    private final SpeciesOverrideIndex speciesOverrideIndex;
    private final ExecutorService executor;
    private final Map<String, JsonModifier> plugins;

    // ---- Phase 3 新增：装配结果 ----
    private MainFrame view;
    private MainController controller;
    private volatile Runnable datapackSync = () -> { };

    public ModifierContainer() {
        // 1. Repository 层
        this.jarRepo = new JarRepositoryImpl();
        this.configRepo = new ConfigRepositoryImpl();
        this.overrideRepo = new OverrideRepositoryImpl();

        // 2. Service 层
        this.speciesOverrideIndex = new SpeciesOverrideIndexImpl(jarRepo);
        this.scanService = new ScanServiceImpl(jarRepo);
        this.modifyService = new ModifyServiceImpl(jarRepo, overrideRepo, speciesOverrideIndex);
        this.spawnRateService = new SpawnRateServiceImpl(jarRepo, overrideRepo);
        this.spawnConfigService = new SpawnConfigServiceImpl();
        this.megaLimitService = new MegaLimitServiceImpl();
        this.moveCatalog = new MoveCatalogImpl(jarRepo);

        // 3. 线程池
        this.executor = Executors.newFixedThreadPool(2);

        // 4. 插件自动发现（ServiceLoader SPI）
        this.plugins = discoverPlugins();
        for (JsonModifier plugin : plugins.values()) {
            if (plugin instanceof SpawnRateModifier spawnRateModifier) {
                spawnRateModifier.setSpawnRateService(spawnRateService);
            }
            if (plugin instanceof MoveModifier moveModifier) {
                moveModifier.setMoveCatalog(moveCatalog);
            }
        }
    }

    // ================================================================
    // 插件发现
    // ================================================================

    /**
     * 通过 Java SPI 机制自动发现 classpath 上所有 {@link JsonModifier} 实现。
     */
    private static Map<String, JsonModifier> discoverPlugins() {
        Map<String, JsonModifier> result = new LinkedHashMap<>();
        ServiceLoader<JsonModifier> loader = ServiceLoader.load(JsonModifier.class);
        for (JsonModifier plugin : loader) {
            result.put(plugin.getPluginName(), plugin);
        }
        return result;
    }

    /**
     * 获取所有已发现的插件（按注册顺序排列）。
     */
    public Map<String, JsonModifier> getPlugins() {
        return Collections.unmodifiableMap(plugins);
    }

    /**
     * 仅返回可编辑插件（用于“修改插件”下拉框）。
     *
     * <p>以下插件不进入下拉框：
     * <ul>
     *   <li>{@link CustomPokemonScanner} —— 魔改宝可梦扫描，辅助功能；</li>
     *   <li>{@link FieldSearchModifier} —— 字段查找，只由主界面的查找按钮使用；</li>
     *   <li>{@link SpawnRateModifier} —— 单只宝可梦刷新率编辑已停用：本整合包里
     *       超过 8 个全局数据包自带 spawn_pool_world，写进世界数据包的覆盖文件
     *       不会真正生效，而且需要重进世界，容易误导。稀有等级的全局概率改由
     *       “稀有等级权重”界面（{@code best-spawner-config.json}）负责，可热重载即时生效。</li>
     * </ul>
     */
    public Map<String, JsonModifier> getEditorPlugins() {
        Map<String, JsonModifier> editors = new LinkedHashMap<>();
        for (Map.Entry<String, JsonModifier> entry : plugins.entrySet()) {
            JsonModifier plugin = entry.getValue();
            if (plugin instanceof CustomPokemonScanner
                || plugin instanceof FieldSearchModifier
                || plugin instanceof SpawnRateModifier) {
                continue;
            }
            editors.put(entry.getKey(), plugin);
        }
        return Collections.unmodifiableMap(editors);
    }

    // ================================================================
    // Getters
    // ================================================================

    public JarRepository getJarRepo() {
        return jarRepo;
    }

    public ConfigRepository getConfigRepo() {
        return configRepo;
    }

    public OverrideRepository getOverrideRepository() {
        return overrideRepo;
    }

    /**
     * 注入数据包同步回调（由客户端入口设置）。
     */
    public void setDatapackSync(Runnable datapackSync) {
        this.datapackSync = datapackSync != null ? datapackSync : () -> { };
    }

    public ScanService getScanService() {
        return scanService;
    }

    public ModifyService getModifyService() {
        return modifyService;
    }

    public SpawnRateService getSpawnRateService() {
        return spawnRateService;
    }

    public SpawnConfigService getSpawnConfigService() {
        return spawnConfigService;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public MainFrame getView() {
        return view;
    }

    public MainController getController() {
        return controller;
    }

    // ================================================================
    // 启动
    // ================================================================

    /**
     * 装配并启动整个应用（MVC：View -> Controller -> Service -> Repository）。
     */
    public void launch() {
        // 4. View 层
        this.view = new MainFrame();

        // 5. Controller 层（注入所有依赖）
        this.controller = new MainController(
            view, view.getPluginPanel(), scanService, modifyService,
            jarRepo, configRepo, overrideRepo, spawnRateService, spawnConfigService,
            megaLimitService, executor, getEditorPlugins(), () -> datapackSync.run());

        // 6. 连接 View -> Controller（绑定事件 + 初始化）
        view.setController(controller);
    }

    /**
     * 关闭容器，释放线程池资源。
     */
    public void shutdown() {
        executor.shutdown();
    }
}

package com.cobblemon.modifier.ui;

import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.model.JarResourcePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 主窗口 —— MVC 架构中的 View 状态模型（线程安全的可观测 POJO）。
 *
 * <p>Phase 1 已移除全部 Swing 依赖；Phase 3 改为纯视图状态模型：
 * 所有写方法都可被工作线程（扫描/保存/读取）安全调用，
 * Minecraft Screen 在客户端渲染线程定时读取快照并重建控件，
 * 从而避免任何跨线程直接操作 Minecraft Widget。
 */
public class MainFrame {

    private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

    // ---- 依赖 ----
    private MainController controller;
    private final PluginPanel pluginPanel = new PluginPanel();

    // ---- 状态（volatile 快照式，供客户端线程轮询） ----
    private volatile String folderPath;
    private volatile String selectedPluginName;
    private volatile List<String> pluginNames = List.of();
    private volatile List<JarResourcePath> currentDisplayPaths = List.of();
    private volatile boolean pluginComboEnabled = true;
    private volatile boolean loadPokemonButtonEnabled = true;
    private volatile String loadPokemonButtonText = "加载宝可梦数据";
    private volatile boolean progressVisible;
    private volatile int progressTotal;
    private volatile int progressCurrent;
    private volatile String progressStatus = "";
    private volatile boolean centerDirty;
    private volatile String pendingSelectRaw;
    private final List<String> logLines = new CopyOnWriteArrayList<>();

    public MainFrame() {
    }

    /**
     * 注入控制器（DI 容器装配时调用），并触发控制器初始化。
     */
    public void setController(MainController controller) {
        this.controller = controller;
        controller.initialize();
    }

    public MainController getController() {
        return controller;
    }

    public PluginPanel getPluginPanel() {
        return pluginPanel;
    }

    // ================================================================
    // View 状态访问（供 Controller 调用；任意线程可安全写入）
    // ================================================================

    public void setFolderPath(String path) {
        this.folderPath = path;
    }

    public String getFolderPath() {
        return folderPath;
    }

    /**
     * 填充插件列表（Phase 3 由 CyclingButtonWidget 使用）。
     */
    public void populatePluginCombo(List<String> names) {
        List<String> snapshot = names != null ? List.copyOf(names) : List.of();
        this.pluginNames = snapshot;
        if (selectedPluginName == null && !snapshot.isEmpty()) {
            this.selectedPluginName = snapshot.get(0);
        }
    }

    public List<String> getPluginNames() {
        return pluginNames;
    }

    /**
     * 选中指定索引的插件。
     */
    public void selectPlugin(int index) {
        if (index >= 0 && index < pluginNames.size()) {
            this.selectedPluginName = pluginNames.get(index);
        }
    }

    /**
     * 按名称选中插件。
     */
    public void selectPluginByName(String name) {
        this.selectedPluginName = name;
    }

    public String getSelectedPluginName() {
        return selectedPluginName;
    }

    public void setPluginComboEnabled(boolean enabled) {
        this.pluginComboEnabled = enabled;
    }

    public boolean isPluginComboEnabled() {
        return pluginComboEnabled;
    }

    public void setLoadPokemonButtonEnabled(boolean enabled) {
        this.loadPokemonButtonEnabled = enabled;
    }

    public boolean isLoadPokemonButtonEnabled() {
        return loadPokemonButtonEnabled;
    }

    public void setLoadPokemonButtonText(String text) {
        this.loadPokemonButtonText = text != null ? text : "";
    }

    public String getLoadPokemonButtonText() {
        return loadPokemonButtonText;
    }

    // ---- JSON 文件列表（快照式赋值，避免跨线程读写同一集合） ----

    public void refreshJsonList(List<JarResourcePath> paths) {
        this.currentDisplayPaths = paths != null ? List.copyOf(paths) : List.of();
    }

    public void clearJsonList() {
        this.currentDisplayPaths = List.of();
    }

    public List<JarResourcePath> getCurrentDisplayPaths() {
        return currentDisplayPaths;
    }

    /**
     * 请求界面把指定原始路径的宝可梦高亮为选中（一次性）。
     * 空字符串表示清空选中，null 表示没有请求。
     */
    public void requestSelection(String rawPath) {
        this.pendingSelectRaw = rawPath;
    }

    public String consumeSelectionRequest() {
        String raw = pendingSelectRaw;
        pendingSelectRaw = null;
        return raw;
    }

    // ---- 进度 ----

    public void showProgress(int total) {
        this.progressVisible = true;
        this.progressTotal = total;
        this.progressCurrent = 0;
        this.progressStatus = "";
    }

    public void updateProgress(int current, int total, String status) {
        this.progressCurrent = current;
        this.progressTotal = total;
        this.progressStatus = status != null ? status : "";
    }

    public void hideProgress() {
        this.progressVisible = false;
        this.progressCurrent = 0;
        this.progressTotal = 0;
        this.progressStatus = "";
    }

    public boolean isProgressVisible() {
        return progressVisible;
    }

    public int getProgressTotal() {
        return progressTotal;
    }

    public int getProgressCurrent() {
        return progressCurrent;
    }

    public String getProgressStatus() {
        return progressStatus;
    }

    // ---- 记录与消息 ----

    public void appendRecord(String text) {
        if (text == null) {
            return;
        }
        String[] lines = text.replace("\r", "").split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                logLines.add(trimmed);
            }
        }
        while (logLines.size() > 200) {
            logLines.remove(0);
        }
        log.info("修改记录：{}", text.trim());
    }

    public void showError(String message) {
        if (message != null) {
            logLines.add("[错误] " + message.trim());
            while (logLines.size() > 200) {
                logLines.remove(0);
            }
            log.warn("错误提示：{}", message);
        }
    }

    public List<String> getLogLines() {
        return List.copyOf(logLines);
    }

    /**
     * 标记右侧编辑区需要重建（Controller 读取 JSON 完成后调用）。
     */
    public void refreshCenterPanel() {
        this.centerDirty = true;
    }

    /**
     * 清空右侧编辑区（切换插件 / 目录时由 Screen 调用）。
     */
    public void clearCenterPanel() {
        pluginPanel.clearEditors();
        this.centerDirty = true;
    }

    /**
     * 客户端线程消费“编辑区已变”标记。
     */
    public boolean consumeCenterDirty() {
        boolean dirty = centerDirty;
        centerDirty = false;
        return dirty;
    }
}

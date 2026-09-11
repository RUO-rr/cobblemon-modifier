package com.cobblemon.modifier.client;

import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.ui.MainFrame;
import com.cobblemon.modifier.ui.PluginPanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 宝可梦修改器主界面 —— Phase 3：Minecraft Screen + Widget。
 *
 * <p>线程模型：业务层在工作线程运行，只写入 {@link MainFrame} 视图模型；
 * 本 Screen 在客户端渲染线程 {@link #tick()} 中轮询快照并重建控件。
 *
 * <p>所有说明文字都用 {@link TextWidget} 组件承载（自带深色底板），
 * 不再直接画在游戏背景上，避免出现“悬空/发虚”的问题。
 */
public class ModifierScreen extends Screen {

    private static final Logger log = LoggerFactory.getLogger(ModifierScreen.class);

    private static final int H = 20;       // 控件标准高度
    private static final int ROW_H = 22;   // 编辑器行高
    private static final int LABEL_H = 12; // 标签组件高度
    private static final int EDITOR_TOP = 110;
    private static final int LIST_TOP = 106;

    private final MainController controller;
    private final MainFrame view;
    private final PluginPanel pluginPanel;

    // ---- 布局 ----
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int rowsPerPage = 8;

    // ---- 左侧控件 ----
    private TextFieldWidget folderField;
    private ButtonWidget applyFolderButton;
    private ButtonWidget loadPokemonButton;
    private TextFieldWidget searchField;
    private ButtonWidget clearSearchButton;
    private PokemonListWidget pokemonList;

    // ---- 右侧控件 ----
    private CyclingButtonWidget<String> pluginButton;
    private ButtonWidget fieldSearchButton;
    private ButtonWidget bucketConfigButton;
    private ButtonWidget closeButton;
    private ButtonWidget prevPageButton;
    private ButtonWidget nextPageButton;
    private ButtonWidget saveButton;
    private ButtonWidget openOverrideButton;
    private ButtonWidget restoreButton;
    private ButtonWidget megaLimitButton;

    // ---- 文字组件（TextWidget，自带底板） ----
    private PanelLabel titleLabel;
    private PanelLabel folderLabel;
    private PanelLabel listHeaderLabel;
    private PanelLabel progressLabel;
    private PanelLabel selectedNameLabel;
    private PanelLabel selectedPathLabel;
    private PanelLabel sectionLabel;
    private PanelLabel pageLabel;
    private PanelLabel bottomLogLabel;

    // ---- 字段编辑器（key -> 输入框 + 标签组件） ----
    private final Map<String, TextFieldWidget> fieldWidgets = new LinkedHashMap<>();
    private final Map<String, CyclingButtonWidget<String>> fieldChoiceWidgets = new LinkedHashMap<>();
    private final Map<String, PanelLabel> editorLabelWidgets = new LinkedHashMap<>();
    private List<PluginPanel.FieldEditor> currentEditors = List.of();
    private int editorPage;

    // ---- 轮询快照 ----
    private List<JarResourcePath> lastDisplayedPaths;
    private String lastFolderShown;
    private int selectedIndex = -1;
    private String selectedRawPath = "";
    private String selectedName = "";
    private String selectedFullPath = "";

    public ModifierScreen(MainController controller) {
        super(Text.literal("Cobblemon 宝可梦修改器"));
        this.controller = controller;
        this.view = controller.getView();
        this.pluginPanel = view.getPluginPanel();
    }

    // ================================================================
    // 初始化
    // ================================================================

    @Override
    protected void init() {
        computeLayout();

        String existingFolder = view.getFolderPath();
        folderField = new TextFieldWidget(textRenderer, leftX, 18, leftW, H,
            Text.literal("模组目录（例如 mods 文件夹）"));
        folderField.setMaxLength(512);
        folderField.setText(existingFolder != null ? existingFolder : defaultModsDir());
        addDrawableChild(folderField);

        int half = (leftW - 4) / 2;
        applyFolderButton = ButtonWidget.builder(Text.literal("应用目录"), b -> onApplyFolder())
            .dimensions(leftX, 42, half, H).build();
        loadPokemonButton = ButtonWidget.builder(Text.literal(view.getLoadPokemonButtonText()), b -> onLoadPokemon())
            .dimensions(leftX + half + 4, 42, leftW - half - 4, H).build();
        addDrawableChild(applyFolderButton);
        addDrawableChild(loadPokemonButton);

        searchField = new TextFieldWidget(textRenderer, leftX, 66, leftW - 68, H,
            Text.literal("英文名/路径搜索"));
        searchField.setChangedListener(text -> {
            if (!text.isBlank() && folderReady()) {
                controller.onEnglishSearch(text);
            }
        });
        addDrawableChild(searchField);

        clearSearchButton = ButtonWidget.builder(Text.literal("清空"), b -> {
            searchField.setText("");
            controller.onEnglishSearch("");
        }).dimensions(leftX + leftW - 64, 66, 64, H).build();
        addDrawableChild(clearSearchButton);

        // 宝可梦 JSON 列表
        int listBottom = height - 44;
        pokemonList = new PokemonListWidget(
            MinecraftClient.getInstance(), leftW, Math.max(20, listBottom - LIST_TOP), LIST_TOP,
            this::onPokemonClicked);
        addDrawableChild(pokemonList);
        pokemonList.setPaths(view.getCurrentDisplayPaths());
        lastDisplayedPaths = view.getCurrentDisplayPaths();

        buildPluginButton(existingPluginName());

        int searchW = Math.min(88, Math.max(56, rightW / 5));
        int bucketW = Math.min(104, Math.max(70, rightW / 5));
        int closeW = Math.min(60, Math.max(40, rightW / 7));
        int pluginW = Math.max(80, rightW - searchW - bucketW - closeW - 6);

        fieldSearchButton = ButtonWidget.builder(Text.literal("字段查找"), b -> onFieldSearch())
            .dimensions(rightX + pluginW + 2, 18, searchW, H).build();
        bucketConfigButton = ButtonWidget.builder(Text.literal("稀有等级权重"), b -> onBucketConfig())
            .tooltip(Tooltip.of(Text.literal("编辑全局稀有等级占比（best-spawner-config.json）\n保存后立即热重载")))
            .dimensions(rightX + pluginW + searchW + 4, 18, bucketW, H).build();
        closeButton = ButtonWidget.builder(Text.literal("关闭"), b -> close())
            .dimensions(rightX + pluginW + searchW + bucketW + 6, 18, closeW, H).build();
        addDrawableChild(fieldSearchButton);
        addDrawableChild(bucketConfigButton);
        addDrawableChild(closeButton);

        // 分页按钮（右侧编辑区）
        prevPageButton = ButtonWidget.builder(Text.literal("上一页"), b -> {
            syncEditorsIntoModel();
            if (editorPage > 0) {
                editorPage--;
                layoutEditorPage();
            }
        }).dimensions(rightX, 84, 64, H).build();
        nextPageButton = ButtonWidget.builder(Text.literal("下一页"), b -> {
            syncEditorsIntoModel();
            if (editorPage < maxPage()) {
                editorPage++;
                layoutEditorPage();
            }
        }).dimensions(rightX + rightW - 64, 84, 64, H).build();
        addDrawableChild(prevPageButton);
        addDrawableChild(nextPageButton);

        int openW = Math.min(104, Math.max(64, rightW / 6));
        int restoreW = Math.min(88, Math.max(56, rightW / 7));
        int megaW = Math.min(116, Math.max(76, rightW / 5));
        int saveW = Math.max(56, rightW - openW - restoreW - megaW - 12);
        saveButton = ButtonWidget.builder(Text.literal("保存修改"), b -> onSave())
            .tooltip(Tooltip.of(Text.literal(
                "写入覆盖文件并同步到世界数据包\n物种数据由 Cobblemon 在世界加载时读取，需重进世界生效（不用重启游戏）")))
            .dimensions(rightX, height - 34, saveW, H).build();
        restoreButton = ButtonWidget.builder(Text.literal("还原原版"), b -> onRestoreOriginal())
            .tooltip(Tooltip.of(Text.literal(
                "删除该宝可梦的覆盖文件，恢复模组原始数值\n重进世界后生效")))
            .dimensions(rightX + saveW + 4, height - 34, restoreW, H).build();
        megaLimitButton = ButtonWidget.builder(
                Text.literal(megaLimitLabel(controller.isMultipleMegasAllowed())),
                b -> onToggleMegaLimit())
            .tooltip(Tooltip.of(Text.literal(
                "mega_showdown 的同时 Mega 数量开关（multipleMegas）\n"
                    + "禁止 = 同时只能有一只 Mega（默认）\n"
                    + "允许 = 解除该限制，可以同时存在多只\n"
                    + "点击立刻切换并热重载，无需重启游戏")))
            .dimensions(rightX + saveW + restoreW + 8, height - 34, megaW, H).build();
        openOverrideButton = ButtonWidget.builder(Text.literal("打开覆盖目录"), b -> onOpenOverrideFolder())
            .tooltip(Tooltip.of(Text.literal("打开 config/cobblemonmodifier/overrides\n删除其中某个文件即可恢复该宝可梦的原版数值")))
            .dimensions(rightX + rightW - openW, height - 34, openW, H).build();
        addDrawableChild(saveButton);
        addDrawableChild(restoreButton);
        addDrawableChild(megaLimitButton);
        addDrawableChild(openOverrideButton);

        // 文字组件（TextWidget，自带深色底板，像按钮一样清晰）
        titleLabel = addDrawableChild(new PanelLabel(0, 4, width, LABEL_H,
            Text.literal("Cobblemon 宝可梦数据修改器"), 0xFFD700));
        titleLabel.alignCenter();
        folderLabel = addDrawableChild(new PanelLabel(leftX, 6, leftW, LABEL_H,
            Text.literal("模组目录 (mods)"), 0xE0E0E0));
        folderLabel.alignLeft();
        listHeaderLabel = addDrawableChild(new PanelLabel(leftX, 88, leftW, LABEL_H,
            Text.literal("宝可梦列表 (0)"), 0xFFFFFF));
        listHeaderLabel.alignLeft();
        progressLabel = addDrawableChild(new PanelLabel(leftX, height - 28, leftW, LABEL_H,
            Text.literal(""), 0xFFFFFF));
        progressLabel.alignLeft();
        progressLabel.visible = false;
        selectedNameLabel = addDrawableChild(new PanelLabel(rightX, 44, rightW, 14,
            Text.literal("未选择宝可梦"), 0xD0D0D0));
        selectedNameLabel.alignLeft();
        selectedPathLabel = addDrawableChild(new PanelLabel(rightX, 60, rightW, 14,
            Text.literal("在左侧列表中点击一个文件开始编辑"), 0x808080));
        selectedPathLabel.alignLeft();
        sectionLabel = addDrawableChild(new PanelLabel(rightX, 76, rightW, 14,
            Text.literal("字段编辑（点击左侧文件后显示）"), 0xFFFFFF));
        sectionLabel.alignLeft();
        pageLabel = addDrawableChild(new PanelLabel(rightX + 68, 94, rightW - 136, 14,
            Text.literal("第 1/1 页，共 0 项"), 0xE0E0E0));
        pageLabel.alignCenter();
        bottomLogLabel = addDrawableChild(new PanelLabel(10, height - 14, width - 20, LABEL_H,
            Text.literal(""), 0x9A9A9A));
        bottomLogLabel.alignCenter();

        refreshEditorsFromModel();
    }

    private void computeLayout() {
        this.leftX = 8;
        this.leftW = Math.max(150, width / 3);
        this.rightX = leftX + leftW + 8;
        this.rightW = Math.max(140, width - rightX - 8);
        this.rowsPerPage = Math.max(1, (height - 40 - EDITOR_TOP) / ROW_H);
    }

    private String defaultModsDir() {
        return new File(MinecraftClient.getInstance().runDirectory, "mods").getAbsolutePath();
    }

    private boolean folderReady() {
        String folder = view.getFolderPath();
        return folder != null && new File(folder).isDirectory();
    }

    private String existingPluginName() {
        String name = view.getSelectedPluginName();
        if (name != null) {
            return name;
        }
        List<String> names = view.getPluginNames();
        return names.isEmpty() ? "无插件" : names.get(0);
    }

    private void buildPluginButton(String selected) {
        if (pluginButton != null) {
            remove(pluginButton);
        }
        List<String> names = view.getPluginNames();
        List<String> values = names.isEmpty() ? List.of("无插件") : names;
        String initial = values.contains(selected) ? selected : values.get(0);
        if (!names.isEmpty() && view.getSelectedPluginName() == null) {
            view.selectPluginByName(initial);
        }

        int searchW = Math.min(110, Math.max(60, rightW / 4));
        int closeW = Math.min(70, Math.max(40, rightW / 6));
        int pluginW = Math.max(80, rightW - searchW - closeW - 4);

        pluginButton = CyclingButtonWidget.<String>builder(Text::literal)
            .values(values)
            .initially(initial)
            .build(rightX, 18, pluginW, H, Text.literal("修改插件"), (btn, value) -> onPluginPicked(value));
        pluginButton.setMessage(Text.literal("修改插件: " + initial));
        addDrawableChild(pluginButton);
    }

    // ================================================================
    // 用户操作
    // ================================================================

    private void onApplyFolder() {
        File folder = new File(folderField.getText().trim());
        if (!folder.isDirectory()) {
            view.showError("目录不存在：" + folder.getAbsolutePath());
            return;
        }
        view.clearCenterPanel();
        controller.onFolderSelected(folder);
    }

    private void onLoadPokemon() {
        if (!folderReady()) {
            view.showError("请先点击“应用目录”选择有效目录");
            return;
        }
        syncEditorsIntoModel();
        view.clearCenterPanel();
        controller.onLoadAllPokemon();
    }

    private void onPluginPicked(String pluginName) {
        view.selectPluginByName(pluginName);
        view.clearCenterPanel();
        controller.onPluginSwitched(pluginName);
    }

    private void onPokemonClicked(int index) {
        List<JarResourcePath> paths = view.getCurrentDisplayPaths();
        if (index < 0 || index >= paths.size()) {
            return;
        }
        JarResourcePath path = paths.get(index);
        selectedIndex = index;
        selectedRawPath = path.toRawString();
        selectedName = pokemonBaseName(path);
        selectedFullPath = path.jarName() + " / " + path.jsonPath();
        controller.onJsonFileSelected(path);
    }

    private void onBucketConfig() {
        MinecraftClient.getInstance().setScreen(new BucketConfigScreen(controller, this));
    }

    /**
     * 切换"允许多只 Mega"。
     *
     * <p>评论区说的"解除 mega 限制"指的是解除"同时只能有一只 Mega"这条限制；
     * mega_showdown 自带这个开关，这里只是把它做成一个按钮。
     */
    private void onToggleMegaLimit() {
        boolean allowed = controller.isMultipleMegasAllowed();
        if (controller.setMultipleMegasAllowed(!allowed)) {
            refreshMegaLimitLabel();
        }
    }

    private void refreshMegaLimitLabel() {
        if (megaLimitButton != null) {
            megaLimitButton.setMessage(Text.literal(megaLimitLabel(controller.isMultipleMegasAllowed())));
        }
    }

    private static String megaLimitLabel(boolean allowed) {
        return allowed ? "多Mega：允许" : "多Mega：禁止";
    }

    private void onFieldSearch() {
        if (!folderReady()) {
            view.showError("请先应用目录");
            return;
        }
        if (controller.getGlobalPokemonPaths().isEmpty()) {
            view.showError("请先点击“加载宝可梦数据”");
            return;
        }
        MinecraftClient.getInstance().setScreen(new FieldSearchScreen(controller));
    }

    private void onRestoreOriginal() {
        if (selectedIndex < 0) {
            view.showError("请先在左侧选择一个宝可梦");
            return;
        }
        controller.onRestoreOriginal();
    }
    private void onOpenOverrideFolder() {
        Path root = controller.getOverrideRoot();
        if (root == null) {
            view.showError("无法确定覆盖目录");
            return;
        }
        try {
            Files.createDirectories(root);
            Util.getOperatingSystem().open(root);
            view.appendRecord("覆盖目录：" + root + "（删除其中文件可恢复原版数值）");
        } catch (Exception e) {
            view.showError("打开覆盖目录失败：" + e.getMessage());
        }
    }

    private void onSave() {
        if (selectedIndex < 0) {
            view.showError("请先在左侧选择一个宝可梦");
            return;
        }
        syncEditorsIntoModel();
        controller.onSave();
    }

    public MainController getController() {
        return controller;
    }

    /**
     * 供 FieldSearchScreen 结果点击跳转使用：按原始路径选中对应宝可梦。
     */
    public void selectRaw(String rawPath) {
        List<JarResourcePath> paths = view.getCurrentDisplayPaths();
        for (int i = 0; i < paths.size(); i++) {
            if (Objects.equals(paths.get(i).toRawString(), rawPath)) {
                onPokemonClicked(i);
                return;
            }
        }
        view.appendRecord("[提示] 该文件不在当前列表中，请切换插件后重试：" + rawPath);
    }

    // ================================================================
    // 客户端线程轮询同步（每次 tick）
    // ================================================================

    @Override
    public void tick() {
        syncFolderField();
        syncList();
        syncPluginButton();
        syncButtons();
        syncLabels();
        if (view.consumeCenterDirty()) {
            refreshEditorsFromModel();
        }
    }

    private void syncFolderField() {
        String folder = view.getFolderPath();
        if (!Objects.equals(folder, lastFolderShown)) {
            lastFolderShown = folder;
            if (folder != null && !folderField.isFocused()) {
                folderField.setText(folder);
            }
        }
    }

    private void syncList() {
        List<JarResourcePath> current = view.getCurrentDisplayPaths();
        if (current != lastDisplayedPaths) {
            lastDisplayedPaths = current;
            if (selectedIndex >= current.size()) {
                selectedIndex = -1;
                selectedName = "";
                selectedFullPath = "";
            }
            pokemonList.setPaths(current);
        }
        applyPendingSelection();
    }

    /**
     * 消费 Controller 发出的一次性选中请求：切换插件后把同一文件重新高亮。
     */
    private void applyPendingSelection() {
        String rawPath = view.consumeSelectionRequest();
        if (rawPath == null) {
            return;
        }
        if (rawPath.isEmpty()) {
            selectedIndex = -1;
            selectedRawPath = "";
            selectedName = "";
            selectedFullPath = "";
            return;
        }
        List<JarResourcePath> paths = view.getCurrentDisplayPaths();
        for (int i = 0; i < paths.size(); i++) {
            if (Objects.equals(paths.get(i).toRawString(), rawPath)) {
                selectedIndex = i;
                selectedRawPath = rawPath;
                selectedName = pokemonBaseName(paths.get(i));
                selectedFullPath = paths.get(i).jarName() + " / " + paths.get(i).jsonPath();
                return;
            }
        }
        selectedIndex = -1;
        selectedRawPath = "";
        selectedName = "";
        selectedFullPath = "";
    }

    private void syncPluginButton() {
        String selected = view.getSelectedPluginName();
        if (selected != null && pluginButton != null && !Objects.equals(selected, pluginButton.getValue())) {
            buildPluginButton(selected);
        }
        if (pluginButton != null) {
            String name = view.getSelectedPluginName() != null ? view.getSelectedPluginName() : "无插件";
            pluginButton.setMessage(Text.literal("修改插件: " + name));
            pluginButton.active = view.isPluginComboEnabled() && !pluginButton.getValue().equals("无插件");
        }
    }

    private void syncButtons() {
        loadPokemonButton.active = view.isLoadPokemonButtonEnabled();
        String loadText = view.getLoadPokemonButtonText();
        if (loadText != null && !loadText.equals(loadPokemonButton.getMessage().getString())) {
            loadPokemonButton.setMessage(Text.literal(loadText));
        }

        boolean enabled = folderReady();
        applyFolderButton.active = !view.isProgressVisible();
        searchField.active = enabled;
        clearSearchButton.active = enabled;
        fieldSearchButton.active = enabled && !lastDisplayedPaths.isEmpty();
        saveButton.active = !currentEditors.isEmpty() && selectedIndex >= 0 && !view.isProgressVisible();
        restoreButton.active = selectedIndex >= 0 && !view.isProgressVisible();

        prevPageButton.active = editorPage > 0;
        nextPageButton.active = editorPage < maxPage();
    }

    private void syncLabels() {
        listHeaderLabel.setMessage(Text.literal("宝可梦列表 (" + view.getCurrentDisplayPaths().size() + ")"));

        if (selectedName.isEmpty()) {
            selectedNameLabel.setMessage(Text.literal("未选择宝可梦"));
            selectedNameLabel.setTextColor(0xD0D0D0);
            selectedPathLabel.setMessage(Text.literal("在左侧列表中点击一个文件开始编辑"));
            selectedPathLabel.setTextColor(0x888888);
        } else {
            selectedNameLabel.setMessage(Text.literal(truncate(selectedName, rightW - 6)));
            selectedNameLabel.setTextColor(0x55FF55);
            selectedPathLabel.setMessage(Text.literal(truncate(selectedFullPath, rightW - 6)));
            selectedPathLabel.setTextColor(0x9A9A9A);
        }

        pageLabel.setMessage(Text.literal(
            "第 " + (editorPage + 1) + "/" + (maxPage() + 1) + " 页，共 " + currentEditors.size() + " 项"));

        progressLabel.visible = view.isProgressVisible();
        if (view.isProgressVisible()) {
            int total = view.getProgressTotal();
            int current = view.getProgressCurrent();
            String status = total > 0
                ? "扫描中: " + current + "/" + total
                + (view.getProgressStatus().isEmpty() ? "" : "  " + view.getProgressStatus())
                : "扫描中: " + view.getProgressStatus();
            progressLabel.setMessage(Text.literal(truncate(status, leftW - 6)));
            progressLabel.setTextColor(0xFFFFFF);
        }

        List<String> logs = view.getLogLines();
        if (!logs.isEmpty()) {
            String last = logs.get(logs.size() - 1);
            int color = 0x9A9A9A;
            if (last.startsWith("[错误]")) {
                color = 0xFF5555;
            } else if (last.contains("修改成功") || last.startsWith("[成功]")) {
                color = 0x55FF55;
            } else if (last.contains("[提示]") || last.contains("请先")) {
                color = 0xFFFF55;
            }
            bottomLogLabel.setMessage(Text.literal(truncate(last, width - 24)));
            bottomLogLabel.setTextColor(color);
        }
    }

    // ================================================================
    // 编辑区管理
    // ================================================================

    private void refreshEditorsFromModel() {
        for (TextFieldWidget widget : fieldWidgets.values()) {
            remove(widget);
        }
        for (CyclingButtonWidget<String> widget : fieldChoiceWidgets.values()) {
            remove(widget);
        }
        for (PanelLabel label : editorLabelWidgets.values()) {
            remove(label);
        }
        fieldWidgets.clear();
        fieldChoiceWidgets.clear();
        editorLabelWidgets.clear();
        currentEditors = List.copyOf(pluginPanel.getEditors());
        editorPage = 0;

        for (PluginPanel.FieldEditor editor : currentEditors) {
            PanelLabel label = new PanelLabel(0, 0, 100, 14, Text.literal(editor.labelText), 0xE0E0E0);
            label.alignLeft();
            editorLabelWidgets.put(editor.key, label);
            addDrawableChild(label);

            if (!editor.choices.isEmpty()) {
                String initial = editor.choices.contains(editor.getValue())
                    ? editor.getValue() : editor.choices.get(0);
                CyclingButtonWidget<String> choice = CyclingButtonWidget
                    .<String>builder(Text::literal)
                    .values(editor.choices)
                    .initially(initial)
                    .build(0, 0, 100, H, Text.literal(editor.labelText),
                        (button, value) -> button.setMessage(Text.literal(value)));
                choice.setMessage(Text.literal(initial));
                fieldChoiceWidgets.put(editor.key, choice);
                addDrawableChild(choice);
            } else {
                TextFieldWidget widget = new TextFieldWidget(textRenderer, 0, 0, 100, H,
                    Text.literal(editor.key));
                widget.setMaxLength(80);
                widget.setText(editor.getValue());
                fieldWidgets.put(editor.key, widget);
                addDrawableChild(widget);
            }
        }
        layoutEditorPage();
    }

    private int maxPage() {
        if (currentEditors.isEmpty()) {
            return 0;
        }
        return Math.max(0, (currentEditors.size() - 1) / rowsPerPage);
    }

    private void layoutEditorPage() {
        if (editorPage > maxPage()) {
            editorPage = maxPage();
        }
        if (editorPage < 0) {
            editorPage = 0;
        }

        int labelW = Math.min(170, Math.max(90, rightW / 3));
        int fieldX = rightX + labelW + 6;
        int fieldW = Math.max(60, rightW - labelW - 6);
        int start = editorPage * rowsPerPage;
        int end = Math.min(currentEditors.size(), start + rowsPerPage);
        for (int i = 0; i < currentEditors.size(); i++) {
            PluginPanel.FieldEditor editor = currentEditors.get(i);
            boolean visible = i >= start && i < end;
            int rowY = EDITOR_TOP + (i - start) * ROW_H;

            PanelLabel label = editorLabelWidgets.get(editor.key);
            if (label != null) {
                label.visible = visible;
                if (visible) {
                    String labelText = editor.labelText != null ? editor.labelText : editor.key;
                    label.setMessage(Text.literal(textRenderer.trimToWidth(labelText, labelW - 6)));
                    label.setDimensionsAndPosition(labelW, 14, rightX, rowY + 3);
                }
            }

            TextFieldWidget widget = fieldWidgets.get(editor.key);
            if (widget != null) {
                widget.setVisible(visible);
                widget.setEditable(visible);
                if (visible) {
                    widget.setDimensionsAndPosition(fieldW, H, fieldX, rowY);
                }
            }

            CyclingButtonWidget<String> choice = fieldChoiceWidgets.get(editor.key);
            if (choice != null) {
                choice.visible = visible;
                choice.active = visible;
                if (visible) {
                    choice.setDimensionsAndPosition(fieldW, H, fieldX, rowY);
                }
            }
        }
    }

    private void syncEditorsIntoModel() {
        for (PluginPanel.FieldEditor editor : currentEditors) {
            CyclingButtonWidget<String> choice = fieldChoiceWidgets.get(editor.key);
            if (choice != null) {
                editor.setValue(choice.getValue());
                continue;
            }
            TextFieldWidget widget = fieldWidgets.get(editor.key);
            if (widget != null) {
                editor.setValue(widget.getText());
            }
        }
    }

    // ================================================================
    // 渲染
    // ================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        // 扫描进度条
        if (view.isProgressVisible()) {
            int total = view.getProgressTotal();
            int current = view.getProgressCurrent();
            context.fill(leftX, 100, leftX + leftW, 104, 0xFF404040);
            if (total > 0) {
                int barW = (int) ((long) leftW * Math.min(current, total) / total);
                context.fill(leftX, 100, leftX + barW, 104, 0xFF55FF55);
            } else {
                context.fill(leftX, 100, leftX + leftW / 2, 104, 0xFF55FF55);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String truncate(String text, int maxWidth) {
        return textRenderer.trimToWidth(text == null ? "" : text, Math.max(20, maxWidth));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ================================================================
    // 文字组件：TextWidget + 深色底板
    // ================================================================

    private static class PanelLabel extends TextWidget {

        PanelLabel(int x, int y, int width, int height, Text message, int color) {
            super(x, y, width, height, message, MinecraftClient.getInstance().textRenderer);
            this.setTextColor(color);
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(getX(), getY(), getRight(), getBottom(), 0xC0101010);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFF4A4A4A);
            super.renderWidget(context, mouseX, mouseY, delta);
        }
    }

    // ================================================================
    // 宝可梦列表控件
    // ================================================================

    private class PokemonListWidget extends EntryListWidget<PokemonListWidget.Entry> {

        private final Consumer<Integer> onClick;
        private final int listWidth;

        PokemonListWidget(MinecraftClient client, int width, int height, int y, Consumer<Integer> onClick) {
            super(client, width, height, y, ROW_H);
            this.onClick = onClick;
            this.listWidth = width;
        }

        @Override
        public int getRowWidth() {
            return listWidth - 8;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            // 列表条目各自渲染，不额外播报
        }

        void setPaths(List<JarResourcePath> paths) {
            clearEntries();
            if (paths == null) {
                return;
            }
            for (int i = 0; i < paths.size(); i++) {
                addEntry(new Entry(paths.get(i), i));
            }
        }

        private class Entry extends EntryListWidget.Entry<Entry> {

            private final JarResourcePath path;
            private final int entryIndex;

            Entry(JarResourcePath path, int entryIndex) {
                this.path = path;
                this.entryIndex = entryIndex;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float tickDelta) {
                boolean selected = path.toRawString().equals(selectedRawPath);
                if (hovered || selected) {
                    context.fill(x, y, x + entryWidth, y + entryHeight,
                        selected ? 0x9033CC33 : 0x30FFFFFF);
                }
                String text = displayName(path);
                int color = selected ? 0xFFFFFF : (hovered ? 0xFFFFFF : 0xC8C8C8);
                context.drawText(textRenderer,
                    textRenderer.trimToWidth(text, entryWidth - 8), x + 4, y + 7, color, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    onClick.accept(entryIndex);
                    return true;
                }
                return false;
            }
        }
    }

    // ================================================================
    // 名称工具
    // ================================================================

    private static String displayName(JarResourcePath path) {
        String name = pokemonBaseName(path);
        String jar = path.jarName();
        if (jar.endsWith(".jar")) {
            jar = jar.substring(0, jar.length() - 4);
        }
        if (jar.length() > 18) {
            jar = jar.substring(0, 17) + "…";
        }
        return name + "   [" + jar + "]";
    }

    private static String pokemonBaseName(JarResourcePath path) {
        String json = path.jsonPath();
        int slash = json.lastIndexOf('/');
        if (slash >= 0) {
            json = json.substring(slash + 1);
        }
        if (json.endsWith(".json")) {
            json = json.substring(0, json.length() - 5);
        }
        return json;
    }
}

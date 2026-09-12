package com.cobblemon.modifier.client;

import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.model.MoveData;
import com.cobblemon.modifier.model.MoveInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 招式数据修改界面（P4 第二档）—— 改**招式本身**的威力 / 命中 / PP / 优先级 / 属性。
 *
 * <p>与"技能修改"（改宝可梦学得到哪些招式）不同：这里改的是招式定义。
 * 两种来源都能改：
 * <ul>
 *   <li>基础招式：{@code showdown/data/moves.js}（tackle、flamethrower…）；</li>
 *   <li>自定义招式：数据包的 {@code data/cobblemon/moves/<id>.js}（赛尔号那些）。</li>
 * </ul>
 *
 * <p>保存时把改好的脚本写进 {@code config/cobblemonmodifier/overrides/data/cobblemon/moves/<id>.js}，
 * 并同步为当前世界的数据包；Cobblemon 只在世界加载时读一次招式数据，所以要重进世界生效。
 */
public class MoveDataScreen extends Screen {

    private static final Logger log = LoggerFactory.getLogger(MoveDataScreen.class);

    private static final int H = 20;
    private static final int ROW_H = 22;
    private static final int LABEL_H = 12;
    private static final int MAX_ROWS = 400;

    /** 界面字段顺序 → 显示名。 */
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("basePower", "威力");
        FIELDS.put("accuracy", "命中");
        FIELDS.put("pp", "PP");
        FIELDS.put("priority", "优先级");
        FIELDS.put("type", "属性");
    }

    private final MainController controller;
    private final Screen parent;
    private final List<MoveInfo> hits = new CopyOnWriteArrayList<>();
    private final Map<String, TextFieldWidget> fieldWidgets = new LinkedHashMap<>();

    private int px;
    private int pw;
    private TextFieldWidget searchField;
    private ButtonWidget searchButton;
    private ButtonWidget clearButton;
    private ButtonWidget saveButton;
    private ButtonWidget restoreButton;
    private ButtonWidget backButton;
    private PanelLabel titleLabel;
    private PanelLabel statusLabel;
    private PanelLabel sourceLabel;
    private MoveListWidget list;

    private MoveInfo selected;
    private volatile boolean resultsDirty;
    private volatile String statusText = "输入招式 id 或英文名后点搜索；留空显示全部";
    private volatile int statusColor = 0xFFFFFF;

    public MoveDataScreen(MainController controller, Screen parent) {
        super(Text.literal("招式数据修改"));
        this.controller = controller;
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = Math.max(12, width / 12);
        pw = Math.max(240, width - px * 2);

        int y = 10;
        titleLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H + 2,
            Text.literal("招式数据修改 —— 威力 / 命中 / PP / 优先级 / 属性"), 0xFFD700));
        titleLabel.alignCenter();
        y += LABEL_H + 8;

        int searchW = 64;
        int clearW = 56;
        int fieldW = Math.max(80, pw - searchW - clearW - 8);
        searchField = new TextFieldWidget(textRenderer, px, y, fieldW, H,
            Text.literal("招式 id 或英文名，例如 tackle / 龙之舞"));
        searchField.setMaxLength(48);
        addDrawableChild(searchField);

        searchButton = ButtonWidget.builder(Text.literal("搜索"), b -> startSearch())
            .dimensions(px + fieldW + 4, y, searchW, H).build();
        addDrawableChild(searchButton);

        clearButton = ButtonWidget.builder(Text.literal("全部"), b -> showAll())
            .dimensions(px + fieldW + searchW + 8, y, clearW, H).build();
        addDrawableChild(clearButton);

        y += H + 4;
        statusLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H,
            Text.literal(statusText), 0xFFFFFF));
        statusLabel.alignLeft();
        y += LABEL_H + 4;

        int bottomStart = height - 70;
        int listHeight = Math.max(24, bottomStart - y - 4);
        list = new MoveListWidget(MinecraftClient.getInstance(), width, listHeight, y, this::onPick);
        addDrawableChild(list);
        list.setHits(List.of());

        sourceLabel = addDrawableChild(new PanelLabel(px, bottomStart, pw, LABEL_H,
            Text.literal("未选择招式"), 0xB0B0B0));
        sourceLabel.alignLeft();

        int rowY = bottomStart + LABEL_H + 4;
        int gap = 6;
        int groups = FIELDS.size();
        int groupW = Math.max(48, (pw - gap * (groups - 1)) / groups);
        int labelW = Math.max(24, Math.min(40, groupW / 2));
        int x = px;
        for (Map.Entry<String, String> entry : FIELDS.entrySet()) {
            PanelLabel label = addDrawableChild(new PanelLabel(x, rowY, labelW, H,
                Text.literal(entry.getValue()), 0xE0E0E0));
            label.alignLeft();
            TextFieldWidget field = new TextFieldWidget(textRenderer, x + labelW + 2, rowY,
                Math.max(28, groupW - labelW - 2), H, Text.literal(entry.getValue()));
            field.setMaxLength(16);
            fieldWidgets.put(entry.getKey(), addDrawableChild(field));
            x += groupW + gap;
        }

        int buttonW = Math.max(76, Math.min(140, (pw - 12) / 3));
        saveButton = ButtonWidget.builder(Text.literal("保存修改"), b -> onSave())
            .tooltip(Tooltip.of(Text.literal(
                "写入覆盖脚本并同步到世界数据包\n招式数据由 Cobblemon 在世界加载时读取，需重进世界生效")))
            .dimensions(px, height - 30, buttonW, H).build();
        restoreButton = ButtonWidget.builder(Text.literal("还原原版"), b -> onRestore())
            .tooltip(Tooltip.of(Text.literal("删除该招式的覆盖脚本，恢复模组原始数值")))
            .dimensions(px + buttonW + 6, height - 30, buttonW, H).build();
        backButton = ButtonWidget.builder(Text.literal("返回主界面"), b -> close())
            .dimensions(px + pw - buttonW, height - 30, buttonW, H).build();
        addDrawableChild(saveButton);
        addDrawableChild(restoreButton);
        addDrawableChild(backButton);

        showAll();
    }

    // ================================================================
    // 搜索 / 选择
    // ================================================================

    private void showAll() {
        searchField.setText("");
        startSearch();
    }

    private void startSearch() {
        int total = controller.getMoveTotalCount();
        if (total == 0) {
            statusText = "找不到招式数据：请先点“应用目录”选择 mods 目录";
            statusColor = 0xFFAA55;
            return;
        }
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        List<MoveInfo> found = controller.searchMoves(query, MAX_ROWS);
        hits.clear();
        hits.addAll(found);
        resultsDirty = true;

        if (found.isEmpty()) {
            statusText = "没有匹配的招式（" + query + "）";
            statusColor = 0xFFAA55;
            return;
        }
        statusText = "共 " + total + " 个招式，"
            + (query.isEmpty() ? "显示前 " : "匹配 ")
            + found.size() + " 条"
            + (query.isEmpty() && total > found.size() ? "（可用搜索缩小范围）" : "");
        statusColor = 0xFFFFFF;
    }

    private void onPick(MoveInfo info) {
        if (info == null) {
            return;
        }
        selected = info;
        try {
            MoveData data = controller.loadMove(info);
            fieldWidgets.get("basePower").setText(trimLiteral(data.basePower()));
            fieldWidgets.get("accuracy").setText(trimLiteral(data.accuracy()));
            fieldWidgets.get("pp").setText(trimLiteral(data.pp()));
            fieldWidgets.get("priority").setText(trimLiteral(data.priority()));
            fieldWidgets.get("type").setText(data.displayType());
            sourceLabel.setMessage(Text.literal("已选中：" + info.id()
                + "（" + data.displayName() + "）　来源：" + info.sourceLabel()
                + (data.overridden() ? "　·　已有覆盖" : "")));
            sourceLabel.setTextColor(data.overridden() ? 0x55FF55 : 0xB0B0B0);
            statusText = "改完点“保存修改”，重进世界后生效";
            statusColor = 0xFFFFFF;
        } catch (Exception e) {
            statusText = "读取失败：" + e.getMessage();
            statusColor = 0xFF5555;
            log.warn("读取招式数据失败：{}", info.id(), e);
        }
    }

    // ================================================================
    // 保存 / 还原
    // ================================================================

    private void onSave() {
        if (selected == null) {
            statusText = "请先在上方列表选择一个招式";
            statusColor = 0xFFAA55;
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : FIELDS.keySet()) {
            String text = fieldWidgets.get(key).getText();
            values.put(key, text == null ? "" : text.trim());
        }
        try {
            String detail = controller.saveMove(selected, values);
            statusText = "已保存：" + detail + "（重进世界后生效）";
            statusColor = 0x55FF55;
            onPick(selected);
        } catch (Exception e) {
            statusText = "保存失败：" + e.getMessage();
            statusColor = 0xFF5555;
            log.warn("保存招式数据失败：{}", selected.id(), e);
        }
    }

    private void onRestore() {
        if (selected == null) {
            statusText = "请先在上方列表选择一个招式";
            statusColor = 0xFFAA55;
            return;
        }
        try {
            boolean deleted = controller.restoreMove(selected);
            statusText = deleted
                ? "已删除覆盖，恢复原版（重进世界后生效）"
                : "这个招式本来就没有覆盖文件";
            statusColor = deleted ? 0x55FF55 : 0xFFFF55;
            onPick(selected);
        } catch (Exception e) {
            statusText = "还原失败：" + e.getMessage();
            statusColor = 0xFF5555;
            log.warn("还原招式失败：{}", selected.id(), e);
        }
    }

    // ================================================================
    // 渲染循环
    // ================================================================

    @Override
    public void tick() {
        if (resultsDirty) {
            resultsDirty = false;
            list.setHits(List.copyOf(hits));
        }
        boolean hasSelection = selected != null;
        saveButton.active = hasSelection;
        restoreButton.active = hasSelection;
        for (TextFieldWidget field : fieldWidgets.values()) {
            field.active = hasSelection;
        }
        if (statusLabel != null) {
            statusLabel.setMessage(Text.literal(statusText == null ? "" : statusText));
            statusLabel.setTextColor(statusColor);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** {@code "Fire"} → {@code Fire}；{@code 100} → {@code 100}。 */
    private static String trimLiteral(String literal) {
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

    private class MoveListWidget extends EntryListWidget<MoveListWidget.MoveEntry> {

        private final Consumer<MoveInfo> onClick;

        MoveListWidget(MinecraftClient client, int width, int height, int y, Consumer<MoveInfo> onClick) {
            super(client, width, height, y, ROW_H);
            this.onClick = onClick;
        }

        @Override
        public int getRowWidth() {
            return Math.max(140, pw - 8);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        }

        void setHits(List<MoveInfo> newHits) {
            clearEntries();
            if (newHits == null) {
                return;
            }
            for (MoveInfo info : newHits) {
                addEntry(new MoveEntry(info));
            }
        }

        private class MoveEntry extends EntryListWidget.Entry<MoveEntry> {

            private final MoveInfo info;

            MoveEntry(MoveInfo info) {
                this.info = info;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float tickDelta) {
                boolean isSelected = info.equals(selected);
                if (hovered || isSelected) {
                    context.fill(x, y, x + entryWidth, y + entryHeight, isSelected ? 0x5055AA55 : 0x30FFFFFF);
                }
                String label = info.id() + "   " + info.englishName()
                    + (info.custom() ? "   [数据包]" : "");
                context.drawText(textRenderer,
                    textRenderer.trimToWidth(label, entryWidth - 8),
                    x + 4, y + 7, isSelected ? 0xFFFFFF : (hovered ? 0xFFFFFF : 0xC8C8C8), false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    onClick.accept(info);
                    return true;
                }
                return false;
            }
        }
    }
}

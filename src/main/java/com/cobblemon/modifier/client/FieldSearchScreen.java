package com.cobblemon.modifier.client;

import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.plugin.FieldSearchModifier.SearchField;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 字段查找界面：按图鉴编号 / 名字 / 特性在全局宝可梦列表中搜索。
 * 点击结果后返回主界面并选中该宝可梦，复用已有编辑器继续修改。
 */
public class FieldSearchScreen extends Screen {

    private static final int H = 20;
    private static final int ROW_H = 22;
    private static final int LABEL_H = 12;

    private final MainController controller;
    private final int searchableCount;
    private final List<MainController.PokemonSearchHit> hits = new CopyOnWriteArrayList<>();

    private int px;
    private int pw;

    private CyclingButtonWidget<SearchField> fieldButton;
    private TextFieldWidget valueField;
    private ButtonWidget searchButton;
    private ButtonWidget backButton;
    private PanelLabel titleLabel;
    private PanelLabel hintLabel;
    private PanelLabel statusLabel;
    private HitListWidget resultList;

    private volatile boolean searching;
    private volatile boolean resultsDirty;
    private volatile String statusText = "";

    public FieldSearchScreen(MainController controller) {
        super(Text.literal("字段查找"));
        this.controller = controller;
        this.searchableCount = controller.getGlobalPokemonPaths().size();
    }

    @Override
    protected void init() {
        px = Math.max(16, width / 8);
        pw = Math.max(240, width - px * 2);

        int y = 16;
        titleLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H + 4,
            Text.literal("字段查找 —— 支持 图鉴编号 / 名字 / 特性"), 0xFFD700));
        titleLabel.alignCenter();
        y += LABEL_H + 10;

        int fieldW = 96;
        int searchW = 72;
        int gap = 4;
        int valueW = Math.max(80, pw - fieldW - searchW - gap * 2);

        fieldButton = CyclingButtonWidget.<SearchField>builder(field -> Text.literal(field.displayName()))
            .values(SearchField.values())
            .initially(SearchField.POKEDEX_NUMBER)
            .build(px, y, fieldW, H, Text.literal("查找字段"), (button, value) -> {
            });
        addDrawableChild(fieldButton);

        valueField = new TextFieldWidget(textRenderer, px + fieldW + gap, y, valueW, H,
            Text.literal("输入查找值，例如 12 / pikachu / levitate"));
        valueField.setMaxLength(64);
        addDrawableChild(valueField);

        searchButton = ButtonWidget.builder(Text.literal("搜索"), button -> startSearch())
            .dimensions(px + fieldW + gap + valueW + gap, y, searchW, H).build();
        addDrawableChild(searchButton);

        y += H + 6;
        hintLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H,
            Text.literal("编号精确匹配；名字/特性英文包含匹配。当前可搜索 " + searchableCount + " 个宝可梦。"),
            0xB0B0B0));
        hintLabel.alignLeft();

        y += LABEL_H + 4;
        statusLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H,
            Text.literal(searchableCount > 0 ? "输入条件后点击搜索" : "请先返回主界面点击加载宝可梦数据"),
            searchableCount > 0 ? 0xFFFFFF : 0xFFAA55));
        statusLabel.alignLeft();
        statusText = statusLabel.getMessage().getString();

        int listTop = y + LABEL_H + 6;
        int listHeight = Math.max(24, height - 40 - listTop);
        resultList = new HitListWidget(MinecraftClient.getInstance(), width, listHeight, listTop, this::openHit);
        addDrawableChild(resultList);
        resultList.setHits(List.of());

        backButton = ButtonWidget.builder(Text.literal("返回主界面"), button -> back())
            .dimensions(px + pw - 110, height - 30, 110, H).build();
        addDrawableChild(backButton);
    }

    @Override
    public void tick() {
        if (resultsDirty) {
            resultsDirty = false;
            resultList.setHits(List.copyOf(hits));
        }
        boolean enabled = !searching && searchableCount > 0;
        fieldButton.active = enabled;
        valueField.active = enabled;
        searchButton.active = enabled;
        if (statusLabel != null) {
            statusLabel.setMessage(Text.literal(statusText == null ? "" : statusText));
            statusLabel.setTextColor(searching ? 0xFFFF55 : 0xFFFFFF);
        }
    }

    private void startSearch() {
        if (searching) {
            return;
        }
        if (searchableCount == 0) {
            statusText = "没有可搜索的宝可梦，请先返回主界面点击加载宝可梦数据";
            return;
        }
        String value = valueField.getText() != null ? valueField.getText().trim() : "";
        if (value.isEmpty()) {
            statusText = "请输入查找值";
            return;
        }
        SearchField field = fieldButton.getValue();
        searching = true;
        statusText = "搜索中...（" + field.displayName() + "：" + value + "）";
        hits.clear();
        resultsDirty = true;

        controller.searchGlobalPokemon(field, value, result -> {
            hits.clear();
            hits.addAll(result);
            if (result.isEmpty()) {
                statusText = "没有找到匹配的宝可梦（" + field.displayName() + "：" + value + "）";
            } else {
                statusText = "找到 " + result.size() + " 个结果（" + field.displayName() + "：" + value + "）";
            }
            searching = false;
            resultsDirty = true;
        });
    }

    private void openHit(MainController.PokemonSearchHit hit) {
        if (hit == null) {
            return;
        }
        controller.prepareForSearchJump(hit.rawPath());
        ModifierScreen main = new ModifierScreen(controller);
        MinecraftClient.getInstance().setScreen(main);
        main.selectRaw(hit.rawPath());
    }

    private void back() {
        MinecraftClient.getInstance().setScreen(new ModifierScreen(controller));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
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

    private class HitListWidget extends EntryListWidget<HitListWidget.HitEntry> {

        private final Consumer<MainController.PokemonSearchHit> onClick;

        HitListWidget(MinecraftClient client, int width, int height, int y,
                      Consumer<MainController.PokemonSearchHit> onClick) {
            super(client, width, height, y, ROW_H);
            this.onClick = onClick;
        }

        @Override
        public int getRowWidth() {
            return Math.max(120, pw - 8);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        }

        void setHits(List<MainController.PokemonSearchHit> newHits) {
            clearEntries();
            if (newHits == null) {
                return;
            }
            for (MainController.PokemonSearchHit hit : newHits) {
                addEntry(new HitEntry(hit));
            }
        }

        private class HitEntry extends EntryListWidget.Entry<HitEntry> {

            private final MainController.PokemonSearchHit hit;

            HitEntry(MainController.PokemonSearchHit hit) {
                this.hit = hit;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float tickDelta) {
                if (hovered) {
                    context.fill(x, y, x + entryWidth, y + entryHeight, 0x30FFFFFF);
                }
                String label = hit.pokemonName();
                if (hit.matchedAbility() != null && !hit.matchedAbility().isBlank()) {
                    label = label + "   → " + hit.matchedAbility();
                }
                context.drawText(textRenderer,
                    textRenderer.trimToWidth(label, entryWidth - 8),
                    x + 4, y + 7, hovered ? 0xFFFFFF : 0xC8C8C8, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    onClick.accept(hit);
                    return true;
                }
                return false;
            }
        }
    }
}

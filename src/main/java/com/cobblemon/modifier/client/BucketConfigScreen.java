package com.cobblemon.modifier.client;

import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.model.SpawnBucketConfig;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局稀有等级权重编辑界面。
 *
 * <p>对应 config/cobblemon/spawning/best-spawner-config.json 的 buckets 与
 * spawnablePositionTypeWeights。保存后调用 BestSpawner.reloadConfig() 立即生效。</p>
 */
public class BucketConfigScreen extends Screen {

    private static final Logger log = LoggerFactory.getLogger(BucketConfigScreen.class);
    private static final int H = 20;
    private static final int ROW_H = 24;
    private static final int LABEL_H = 12;

    private final MainController controller;
    private final Screen parent;
    private final Map<String, TextFieldWidget> bucketFields = new LinkedHashMap<>();
    private final Map<String, TextFieldWidget> positionFields = new LinkedHashMap<>();

    private PanelLabel titleLabel;
    private PanelLabel bucketHeaderLabel;
    private PanelLabel positionHeaderLabel;
    private PanelLabel hintLabel;
    private PanelLabel statusLabel;
    private ButtonWidget saveButton;
    private ButtonWidget defaultsButton;
    private ButtonWidget backButton;

    private boolean replaceWithNewVersion = true;
    private volatile String statusText = "修改数值后点击“保存并热重载”，立即生效";
    private volatile int statusColor = 0xFFFFFF;

    public BucketConfigScreen(MainController controller, Screen parent) {
        super(Text.literal("稀有等级权重"));
        this.controller = controller;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int px = Math.max(16, width / 14);
        int pw = Math.max(320, width - px * 2);
        int colGap = 12;
        int colW = Math.max(140, (pw - colGap) / 2);
        int rightColX = px + colW + colGap;
        int labelW = Math.max(72, Math.min(104, colW / 2));
        int fieldW = Math.max(56, colW - labelW - 6);

        int y = 10;
        titleLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H + 2,
            Text.literal("稀有等级权重（best-spawner-config.json）"), 0xFFD700));
        titleLabel.alignCenter();
        titleLabel.setTooltip(Tooltip.of(Text.literal("配置文件：" + controller.getSpawnConfigPath())));
        y += LABEL_H + 6;

        hintLabel = addDrawableChild(new PanelLabel(px, y, pw, LABEL_H,
            Text.literal("相对权重，不是百分比；数值越大越常见。保存后立即热重载。"), 0xB0B0B0));
        hintLabel.alignLeft();
        y += LABEL_H + 8;

        bucketHeaderLabel = addDrawableChild(new PanelLabel(px, y, colW, LABEL_H,
            Text.literal("稀有等级"), 0xFFFFFF));
        bucketHeaderLabel.alignLeft();
        positionHeaderLabel = addDrawableChild(new PanelLabel(rightColX, y, colW, LABEL_H,
            Text.literal("生成位置"), 0xFFFFFF));
        positionHeaderLabel.alignLeft();
        y += LABEL_H + 4;

        int rowY = y;
        for (String name : SpawnBucketConfig.BUCKET_NAMES) {
            addRow(px, rowY, labelW, bucketLabel(name));
            TextFieldWidget field = new TextFieldWidget(textRenderer, px + labelW + 6, rowY,
                fieldW, H, Text.literal(name));
            field.setMaxLength(12);
            bucketFields.put(name, addDrawableChild(field));
            rowY += ROW_H;
        }

        rowY = y;
        for (String name : SpawnBucketConfig.POSITION_TYPES) {
            addRow(rightColX, rowY, labelW, positionLabel(name));
            TextFieldWidget field = new TextFieldWidget(textRenderer, rightColX + labelW + 6, rowY,
                fieldW, H, Text.literal(name));
            field.setMaxLength(12);
            positionFields.put(name, addDrawableChild(field));
            rowY += ROW_H;
        }

        statusLabel = addDrawableChild(new PanelLabel(px, height - 52, pw, LABEL_H,
            Text.literal(statusText), 0xFFFFFF));
        statusLabel.alignLeft();

        int buttonW = Math.max(90, (pw - 12) / 3);
        saveButton = ButtonWidget.builder(Text.literal("保存并热重载"), b -> onSave())
            .dimensions(px, height - 30, buttonW, H).build();
        defaultsButton = ButtonWidget.builder(Text.literal("恢复默认值"), b -> onDefaults())
            .dimensions(px + buttonW + 6, height - 30, buttonW, H).build();
        backButton = ButtonWidget.builder(Text.literal("返回"), b -> close())
            .dimensions(px + pw - buttonW, height - 30, buttonW, H).build();
        addDrawableChild(saveButton);
        addDrawableChild(defaultsButton);
        addDrawableChild(backButton);

        loadCurrentConfig();
    }

    private void addRow(int x, int y, int labelW, String text) {
        PanelLabel label = addDrawableChild(new PanelLabel(x, y, labelW, H,
            Text.literal(text), 0xE0E0E0));
        label.alignLeft();
    }

    private static String positionLabel(String name) {
        switch (name) {
            case "grounded":
                return "grounded 地面";
            case "submerged":
                return "submerged 水下";
            case "surface":
                return "surface 水面";
            default:
                return name;
        }
    }

    private static String bucketLabel(String name) {
        switch (name) {
            case "common":
                return "common（常见）";
            case "uncommon":
                return "uncommon（少见）";
            case "rare":
                return "rare（稀有）";
            case "ultra-rare":
                return "ultra-rare（极稀有）";
            default:
                return name;
        }
    }

    private void loadCurrentConfig() {
        try {
            SpawnBucketConfig config = controller.loadSpawnBucketConfig();
            replaceWithNewVersion = config.replaceWithNewVersion();
            for (String name : SpawnBucketConfig.BUCKET_NAMES) {
                bucketFields.get(name).setText(format(config.weightOf(name)));
            }
            for (String name : SpawnBucketConfig.POSITION_TYPES) {
                positionFields.get(name).setText(format(config.positionWeightOf(name)));
            }
        } catch (Exception e) {
            statusText = "读取配置失败：" + e.getMessage();
            statusColor = 0xFF5555;
            log.warn("读取刷新配置失败", e);
        }
    }

    private void onSave() {
        try {
            Map<String, Float> buckets = parseFields(bucketFields, SpawnBucketConfig.BUCKET_NAMES, "稀有等级");
            Map<String, Float> positions = parseFields(positionFields, SpawnBucketConfig.POSITION_TYPES, "生成位置");
            List<SpawnBucketConfig.Bucket> bucketList = SpawnBucketConfig.BUCKET_NAMES.stream()
                .map(name -> new SpawnBucketConfig.Bucket(name, buckets.get(name)))
                .toList();
            SpawnBucketConfig config = new SpawnBucketConfig(positions, bucketList, replaceWithNewVersion);
            boolean reloaded = controller.saveSpawnBucketConfig(config);
            statusText = reloaded
                ? "已保存并热重载，立即生效"
                : "已保存；热重载失败，请重新进入世界或重启游戏";
            statusColor = reloaded ? 0x55FF55 : 0xFFFF55;
        } catch (Exception e) {
            statusText = "保存失败：" + e.getMessage();
            statusColor = 0xFF5555;
            log.warn("保存刷新配置失败", e);
        }
    }

    private void onDefaults() {
        SpawnBucketConfig defaults = SpawnBucketConfig.defaults();
        for (String name : SpawnBucketConfig.BUCKET_NAMES) {
            bucketFields.get(name).setText(format(defaults.weightOf(name)));
        }
        for (String name : SpawnBucketConfig.POSITION_TYPES) {
            positionFields.get(name).setText(format(defaults.positionWeightOf(name)));
        }
        statusText = "已填入 Cobblemon 默认值，点击保存后生效";
        statusColor = 0xFFFF55;
    }

    private static Map<String, Float> parseFields(Map<String, TextFieldWidget> fields,
                                                  List<String> keys, String what) {
        Map<String, Float> result = new LinkedHashMap<>();
        for (String key : keys) {
            String text = fields.get(key).getText() == null ? "" : fields.get(key).getText().trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException(what + " " + key + " 不能为空");
            }
            try {
                float value = Float.parseFloat(text);
                if (!Float.isFinite(value) || value < 0.0f) {
                    throw new IllegalArgumentException(what + " " + key + " 必须大于等于 0");
                }
                result.put(key, value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(what + " " + key + " 不是有效数字：" + text);
            }
        }
        return result;
    }

    private static String format(float value) {
        if (value == Math.round(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    @Override
    public void tick() {
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
}

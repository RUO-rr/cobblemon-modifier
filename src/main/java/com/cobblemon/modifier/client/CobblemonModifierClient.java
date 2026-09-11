package com.cobblemon.modifier.client;

import com.cobblemon.modifier.CobblemonModifier;
import com.cobblemon.modifier.controller.MainController;
import com.cobblemon.modifier.core.ModifierContainer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口 —— 注册按键绑定，按 K 打开宝可梦修改器主界面（Phase 3）。
 */
public class CobblemonModifierClient implements ClientModInitializer {

    private static final Logger log = LoggerFactory.getLogger(CobblemonModifierClient.class);

    private static KeyBinding openModifierKey;

    @Override
    public void onInitializeClient() {
        log.info("{} 客户端初始化", CobblemonModifier.MOD_ID);

        openModifierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cobblemonmodifier.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.cobblemonmodifier"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        ModifierContainer container = CobblemonModifier.getContainer();
        if (container != null) {
            container.setDatapackSync(() ->
                DatapackSyncService.syncCurrentWorld(container.getOverrideRepository()));
            ServerLifecycleEvents.SERVER_STARTING.register(server ->
                DatapackSyncService.syncToServer(server, container.getOverrideRepository()));
        }

        log.info("按键绑定注册完成：按 K 打开修改器界面");
    }

    private void onClientTick(MinecraftClient client) {
        while (openModifierKey.wasPressed()) {
            ModifierContainer container = CobblemonModifier.getContainer();
            MainController controller = container != null ? container.getController() : null;
            if (controller == null) {
                log.warn("DI 容器尚未初始化，无法打开修改器界面");
                continue;
            }
            client.setScreen(new ModifierScreen(controller));
        }
    }

    public static KeyBinding getOpenModifierKey() {
        return openModifierKey;
    }
}

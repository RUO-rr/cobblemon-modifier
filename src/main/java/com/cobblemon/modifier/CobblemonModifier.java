package com.cobblemon.modifier;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.core.ModifierContainer;

import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CobblemonModifier implements ModInitializer {
	public static final String MOD_ID = "cobblemonmodifier";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModifierContainer container;

	@Override
	public void onInitialize() {
		LOGGER.info("Cobblemon Modifier 正在初始化...");

		// Phase 2：装配 DI 容器，验证 ServiceLoader 插件发现
		container = new ModifierContainer();
		container.launch();

		Map<String, JsonModifier> plugins = container.getPlugins();
		LOGGER.info("ServiceLoader 发现 {} 个插件（其中 {} 个可编辑）：",
			plugins.size(), container.getEditorPlugins().size());
		for (Map.Entry<String, JsonModifier> entry : plugins.entrySet()) {
			LOGGER.info("  - {} → {}", entry.getKey(), entry.getValue().getClass().getSimpleName());
		}

		LOGGER.info("Cobblemon Modifier 初始化完成。按 K 键打开修改器界面。");
	}

	public static ModifierContainer getContainer() {
		return container;
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}

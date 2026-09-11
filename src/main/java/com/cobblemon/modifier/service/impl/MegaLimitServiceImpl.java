package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.service.MegaLimitService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 读写 {@code config/mega_showdown/config.json} 里的 {@code multipleMegas} 开关。
 *
 * <p>写回时只改这一个字段，其余字段（以及整合包/其它模组写进去的未知字段）原样保留；
 * 写完再反射调用 {@code MegaShowdownConfig.load()} 让 mega_showdown 重新读盘，
 * 于是不需要重启游戏就能生效（{@code MegaGimmick.hasMega} 每次都读这个静态字段）。
 */
public class MegaLimitServiceImpl implements MegaLimitService {

    private static final Logger log = LoggerFactory.getLogger(MegaLimitServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** mega_showdown 的配置类；它的 load() 是公开静态方法。 */
    private static final String CONFIG_CLASS =
        "com.github.yajatkaul.mega_showdown.config.MegaShowdownConfig";

    /** 对应的 JSON 字段名。 */
    private static final String KEY = "multipleMegas";

    private final Path configPath;

    public MegaLimitServiceImpl() {
        this(FabricLoader.getInstance().getConfigDir()
            .resolve("mega_showdown")
            .resolve("config.json"));
    }

    public MegaLimitServiceImpl(Path configPath) {
        this.configPath = configPath.toAbsolutePath().normalize();
    }

    @Override
    public Path getConfigPath() {
        return configPath;
    }

    @Override
    public boolean isMultipleMegasEnabled() throws Exception {
        if (!Files.isRegularFile(configPath)) {
            // 文件还没有生成时 mega_showdown 用的是默认值 false
            return false;
        }
        JsonObject root = readRoot();
        return root.has(KEY) && root.get(KEY).isJsonPrimitive()
            && root.get(KEY).getAsBoolean();
    }

    @Override
    public boolean setMultipleMegasEnabled(boolean enabled) throws Exception {
        JsonObject root = Files.isRegularFile(configPath) ? readRoot() : new JsonObject();
        root.addProperty(KEY, enabled);
        writeAtomically(root);
        log.info("已写入 mega_showdown 配置：{} → {}={}", configPath, KEY, enabled);
        return reloadMegaShowdownConfig();
    }

    /**
     * 反射调用 {@code MegaShowdownConfig.load()}，让它重新读盘。
     *
     * @return true 表示热重载成功；false 表示没装 mega_showdown 或调用失败（需重启游戏）
     */
    private boolean reloadMegaShowdownConfig() {
        try {
            Class<?> type = Class.forName(CONFIG_CLASS);
            Method load = type.getMethod("load");
            load.invoke(null);
            log.info("mega_showdown 配置已热重载");
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("未安装 mega_showdown，配置已写入但不会生效");
        } catch (ReflectiveOperationException e) {
            log.warn("调用 MegaShowdownConfig.load() 失败：{}", e.getMessage());
        }
        return false;
    }

    private JsonObject readRoot() throws IOException {
        String text = Files.readString(configPath, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) {
            throw new IOException("mega_showdown 配置根节点不是 JSON 对象：" + configPath);
        }
        return parsed.getAsJsonObject();
    }

    private void writeAtomically(JsonObject root) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, configPath,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

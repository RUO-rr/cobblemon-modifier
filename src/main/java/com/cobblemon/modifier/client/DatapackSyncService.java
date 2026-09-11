package com.cobblemon.modifier.client;

import com.cobblemon.modifier.repository.OverrideRepository;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 把 config/cobblemonmodifier/overrides 同步为当前世界的数据包。
 *
 * <p>Cobblemon 的物种数据在世界启动时读取一次，数据包覆盖会在下次进入世界时生效。
 */
public final class DatapackSyncService {

    private static final Logger log = LoggerFactory.getLogger(DatapackSyncService.class);

    private DatapackSyncService() {
    }

    public static void syncCurrentWorld(OverrideRepository repository) {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            return;
        }
        syncToServer(server, repository);
    }

    public static void syncToServer(MinecraftServer server, OverrideRepository repository) {
        if (server == null || repository == null) {
            return;
        }
        try {
            Path datapacks = server.getSavePath(WorldSavePath.DATAPACKS);
            repository.syncToDatapack(datapacks);
        } catch (Exception e) {
            log.warn("同步覆盖数据包失败：{}", e.getMessage());
        }
    }
}

package com.cobblemon.modifier.repository.impl;

import com.cobblemon.modifier.repository.OverrideRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 覆盖存储实现：文件保存在 config/cobblemonmodifier/overrides/ 下，
 * 路径与模组内资源路径一一对应。
 */
public class OverrideRepositoryImpl implements OverrideRepository {

    private static final Logger log = LoggerFactory.getLogger(OverrideRepositoryImpl.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DATA_PACK_FORMAT = 48;
    private static final String PACK_NAME = "cobblemonmodifier";

    private final Path stagingRoot;

    public OverrideRepositoryImpl() {
        this(FabricLoader.getInstance().getConfigDir()
            .resolve("cobblemonmodifier")
            .resolve("overrides"));
    }

    public OverrideRepositoryImpl(Path stagingRoot) {
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
    }

    @Override
    public JsonObject readOverride(String jsonPath) {
        Path file = resolve(jsonPath);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            log.warn("读取覆盖文件失败：{} - {}", file, e.getMessage());
            return null;
        }
    }

    @Override
    public void writeOverride(String jsonPath, JsonObject data) throws Exception {
        Path file = resolve(jsonPath);
        if (file == null) {
            throw new IllegalArgumentException("非法的 JSON 路径：" + jsonPath);
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        log.info("已写入覆盖文件：{}", stagingRoot.relativize(file));
    }

    @Override
    public String readOverrideText(String path) {
        Path file = resolve(path);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取覆盖文件失败：{} - {}", file, e.getMessage());
            return null;
        }
    }

    @Override
    public void writeOverrideText(String path, String text) throws Exception {
        Path file = resolve(path);
        if (file == null || text == null) {
            throw new IllegalArgumentException("非法的覆盖路径：" + path);
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        log.info("已写入覆盖文件：{}", stagingRoot.relativize(file));
    }

    @Override
    public boolean deleteOverride(String jsonPath) throws Exception {
        Path file = resolve(jsonPath);
        if (file == null) {
            return false;
        }
        boolean deleted = Files.deleteIfExists(file);
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        if (deleted) {
            cleanEmptyParents(file.getParent());
            log.info("已删除覆盖文件：{}", stagingRoot.relativize(file));
        }
        return deleted;
    }
    @Override
    public void syncToDatapack(Path worldDatapacksDir) throws Exception {
        if (worldDatapacksDir == null) {
            return;
        }
        Path packRoot = worldDatapacksDir.resolve(PACK_NAME);
        Path sourceData = stagingRoot.resolve("data");
        Path targetData = packRoot.resolve("data");

        if (Files.isDirectory(sourceData)) {
            deleteRecursively(targetData, packRoot);
            copyRecursively(sourceData, targetData);
            writePackMcmeta(packRoot);
            log.info("已同步覆盖数据包：{}", packRoot);
        } else {
            deleteRecursively(targetData, packRoot);
            Files.deleteIfExists(packRoot.resolve("pack.mcmeta"));
        }
    }

    @Override
    public Path getStagingRoot() {
        return stagingRoot;
    }

    /** 删除后清理空的父目录，避免留下空文件夹。 */
    private void cleanEmptyParents(Path dir) {
        Path current = dir;
        while (current != null && current.startsWith(stagingRoot) && !current.equals(stagingRoot)) {
            try (Stream<Path> children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            } catch (Exception e) {
                return;
            }
            try {
                Files.deleteIfExists(current);
            } catch (Exception e) {
                return;
            }
            current = current.getParent();
        }
    }
    private Path resolve(String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return null;
        }
        String normalized = jsonPath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path resolved = stagingRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(stagingRoot)) {
            return null;
        }
        return resolved;
    }

    private static void writePackMcmeta(Path packRoot) throws Exception {
        Files.createDirectories(packRoot);
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", DATA_PACK_FORMAT);
        pack.addProperty("description", "Cobblemon Modifier data overrides");
        root.add("pack", pack);
        try (Writer writer = Files.newBufferedWriter(
            packRoot.resolve("pack.mcmeta"), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static void copyRecursively(Path source, Path target) throws Exception {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path target, Path boundary) throws Exception {
        if (!Files.exists(target)) {
            return;
        }
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedBoundary = boundary.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedBoundary)) {
            throw new IllegalArgumentException("拒绝删除数据包目录之外的路径：" + target);
        }
        try (Stream<Path> stream = Files.walk(target)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

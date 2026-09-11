package com.cobblemon.modifier.repository.impl;

import com.cobblemon.modifier.repository.ConfigRepository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Properties 文件的配置持久化实现。
 * 从原 PersistenceUtil 迁移而来。
 */
public class ConfigRepositoryImpl implements ConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfigRepositoryImpl.class);

    private static final String CONFIG_DIR = ".cobblemon-modifier";
    private static final String CONFIG_FILE = "config.properties";
    private static final String KEY_FOLDER_PATH = "last_selected_jar_path";
    private static final String KEY_LEGACY_JSONS = "loaded_json_paths";
    private static final String PLUGIN_CACHE_PREFIX = "plugin_cache_";
    private static final String LIST_SEPARATOR = "|||";

    private final Properties properties;
    private final File configFile;

    public ConfigRepositoryImpl() {
        this.properties = new Properties();
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        this.configFile = new File(configDir, CONFIG_FILE);
        load();
    }

    private void load() {
        if (!configFile.isFile()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(configFile);
             Reader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            log.warn("Failed to load config: {}", e.getMessage());
        }
    }

    private void save() {
        try (FileOutputStream fos = new FileOutputStream(configFile);
             Writer writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            properties.store(writer, "Cobblemon Modifier Config");
        } catch (IOException e) {
            log.warn("Failed to save config: {}", e.getMessage());
        }
    }

    @Override
    public String getLastFolderPath() {
        String path = properties.getProperty(KEY_FOLDER_PATH);
        if (path != null && !path.trim().isEmpty()) {
            File dir = new File(path.trim());
            if (dir.exists() && dir.isDirectory()) {
                return path.trim();
            }
        }
        return null;
    }

    @Override
    public void saveLastFolderPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            properties.setProperty(KEY_FOLDER_PATH, path.trim());
            save();
        }
    }

    @Override
    public List<String> getPluginCache(String pluginName) {
        String key = PLUGIN_CACHE_PREFIX + pluginName;
        return deserializeList(properties.getProperty(key));
    }

    @Override
    public void savePluginCache(String pluginName, List<String> paths) {
        if (pluginName != null && !pluginName.trim().isEmpty() && paths != null) {
            properties.setProperty(PLUGIN_CACHE_PREFIX + pluginName, serializeList(paths));
            save();
        }
    }

    @Override
    public void clearAllPluginCaches() {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(PLUGIN_CACHE_PREFIX)) {
                keysToRemove.add(key);
            }
        }
        keysToRemove.forEach(properties::remove);
        save();
    }

    @Override
    public List<String> getLegacyJsonPaths() {
        return deserializeList(properties.getProperty(KEY_LEGACY_JSONS));
    }

    @Override
    public void saveLegacyJsonPaths(List<String> paths) {
        if (paths != null && !paths.isEmpty()) {
            properties.setProperty(KEY_LEGACY_JSONS, serializeList(paths));
            save();
        }
    }

    private static String serializeList(List<String> list) {
        return String.join(LIST_SEPARATOR, list);
    }

    private static List<String> deserializeList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(raw.split("\\|\\|\\|")));
    }
}

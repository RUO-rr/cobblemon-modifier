package com.cobblemon.modifier.repository.impl;

import com.cobblemon.modifier.repository.JarRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAR 文件底层读写实现。
 * 从原 DefaultJarManager 迁移而来，只保留纯 I/O 逻辑。
 */
public class JarRepositoryImpl implements JarRepository {

    private static final Logger log = LoggerFactory.getLogger(JarRepositoryImpl.class);
    private static final Gson GSON = new Gson();

    @Override
    public List<String> listJsonFiles(File jarFile, List<String> targetPaths) throws Exception {
        List<String> paths = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                if (entry.isDirectory() || !entryPath.endsWith(".json")) {
                    continue;
                }
                for (String target : targetPaths) {
                    if (entryPath.startsWith(target)) {
                        paths.add(entryPath);
                        break;
                    }
                }
            }
        }
        return paths;
    }

    @Override
    public JsonObject readJson(File jarFile, String jsonPath) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(jsonPath);
            if (entry == null) {
                throw new FileNotFoundException("JSON not found in JAR: " + jsonPath);
            }
            try (InputStream in = jar.getInputStream(entry);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        }
    }

    @Override
    public List<String> listEntries(File jarFile, java.util.function.Predicate<String> filter) throws Exception {
        List<String> paths = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                if (!entry.isDirectory() && filter.test(entryPath)) {
                    paths.add(entryPath);
                }
            }
        }
        return paths;
    }

    @Override
    public Map<String, JsonObject> readJsonBatch(File jarFile, List<String> jsonPaths) throws Exception {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        if (jsonPaths == null || jsonPaths.isEmpty()) {
            return result;
        }
        // 关键：整个压缩包只打开一次，把它内部的候选文件一次读完
        try (JarFile jar = new JarFile(jarFile)) {
            for (String jsonPath : jsonPaths) {
                JarEntry entry = jar.getJarEntry(jsonPath);
                if (entry == null) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry);
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        result.put(jsonPath, json);
                    }
                } catch (Exception e) {
                    // 单个文件坏掉不影响同包里的其它文件
                    log.warn("Failed to read {} in {}: {}", jsonPath, jarFile.getName(), e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public void writeJson(File jarFile, String jsonPath, JsonObject data) throws Exception {
        Path tempJar = Files.createTempFile("cobblemon_temp", ".jar");
        try {
            try (JarFile original = new JarFile(jarFile);
                 JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(tempJar.toFile()))) {

                Enumeration<JarEntry> entries = original.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().equals(jsonPath)) {
                        JarEntry newEntry = new JarEntry(jsonPath);
                        jarOut.putNextEntry(newEntry);
                        Writer writer = new OutputStreamWriter(jarOut, StandardCharsets.UTF_8);
                        GSON.toJson(data, writer);
                        writer.flush();
                        jarOut.closeEntry();
                    } else {
                        jarOut.putNextEntry(new JarEntry(entry.getName()));
                        try (InputStream in = original.getInputStream(entry)) {
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                jarOut.write(buf, 0, len);
                            }
                        }
                        jarOut.closeEntry();
                    }
                }
            }
            Files.copy(tempJar, jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    @Override
    public String backup(File sourceJar, File targetJar) throws Exception {
        if (!sourceJar.exists()) {
            throw new FileNotFoundException("Source JAR not found: " + sourceJar.getAbsolutePath());
        }
        Files.copy(sourceJar.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return targetJar.getAbsolutePath();
    }

    @Override
    public List<String> scanFolder(File folder, List<String> targetPaths) throws Exception {
        List<String> allPaths = new ArrayList<>();
        if (!folder.exists() || !folder.isDirectory()) {
            return allPaths;
        }

        File[] jarFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null) {
            return allPaths;
        }

        for (File jarFile : jarFiles) {
            try {
                List<String> jsonFiles = listJsonFiles(jarFile, targetPaths);
                for (String jsonPath : jsonFiles) {
                    allPaths.add("[" + jarFile.getName() + "]" + jsonPath);
                }
            } catch (Exception e) {
                log.warn("Failed to read JAR: {} - {}", jarFile.getName(), e.getMessage());
            }
        }
        return allPaths;
    }
}

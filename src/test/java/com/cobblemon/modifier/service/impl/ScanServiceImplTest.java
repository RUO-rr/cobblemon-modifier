package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.service.ScanService;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class ScanServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private JarRepository jarRepo;
    private ScanServiceImpl scanService;
    private List<String> progressLog;

    @Before
    public void setUp() {
        jarRepo = mock(JarRepository.class);
        scanService = new ScanServiceImpl(jarRepo);
        progressLog = new ArrayList<>();
    }

    // ---- empty folder ----

    @Test
    public void scanAndValidate_shouldReturnEmptyForEmptyFolder() throws Exception {
        File emptyDir = tempFolder.newFolder("empty");

        ScanService.ScanResult result = scanService.scanAndValidate(
            emptyDir, jn -> true, json -> true,
            (cur, total, status) -> progressLog.add(status));

        assertEquals(0, result.totalCount());
        assertTrue(result.validPaths().isEmpty());
    }

    // ---- no matching JARs ----

    @Test
    public void scanAndValidate_shouldReturnEmptyWhenNoJars() throws Exception {
        File dir = tempFolder.newFolder("noJars");
        new File(dir, "readme.txt").createNewFile(); // non-jar file

        ScanService.ScanResult result = scanService.scanAndValidate(
            dir, jn -> true, json -> true, (c, t, s) -> {});

        assertEquals(0, result.totalCount());
    }

    // ---- with matching JAR and JSON ----

    @Test
    public void scanAndValidate_shouldReturnValidPaths() throws Exception {
        File dir = tempFolder.newFolder("withJar");
        File fakeJar = new File(dir, "cobblemon.jar");
        assertTrue(fakeJar.createNewFile());

        when(jarRepo.listEntries(any(File.class), any()))
            .thenReturn(List.of("data/cobblemon/species/pikachu.json"));

        JsonObject validJson = new JsonObject();
        validJson.addProperty("name", "pikachu");
        when(jarRepo.readJsonBatch(any(File.class), anyList()))
            .thenReturn(Map.of("data/cobblemon/species/pikachu.json", validJson));

        ScanService.ScanResult result = scanService.scanAndValidate(
            dir, jn -> true, json -> true,
            (cur, total, status) -> progressLog.add(status));

        assertEquals(1, result.totalCount());
        assertEquals(1, result.validPaths().size());
        assertEquals(0, result.skippedCount());
        assertEquals("cobblemon.jar", result.validPaths().get(0).jarName());
    }

    // ---- JAR filter ----

    @Test
    public void scanAndValidate_shouldRespectJarFilter() throws Exception {
        File dir = tempFolder.newFolder("filtered");
        new File(dir, "keep.jar").createNewFile();
        new File(dir, "ignore.jar").createNewFile();

        // 只有 keep.jar 会返回候选（jar 过滤器在扫描前就把 ignore.jar 挡掉）
        when(jarRepo.listEntries(any(File.class), any()))
            .thenReturn(List.of("data/cobblemon/species/test.json"));

        JsonObject validJson = new JsonObject();
        validJson.addProperty("name", "test");
        when(jarRepo.readJsonBatch(any(File.class), anyList()))
            .thenReturn(Map.of("data/cobblemon/species/test.json", validJson));

        // Filter: only "keep.jar"
        ScanService.ScanResult result = scanService.scanAndValidate(
            dir, jn -> jn.equals("keep.jar"), json -> true,
            (c, t, s) -> {});

        assertEquals(1, result.totalCount());
    }

    // ---- validator rejects ----

    @Test
    public void scanAndValidate_shouldSkipWhenValidatorRejects() throws Exception {
        File dir = tempFolder.newFolder("reject");
        new File(dir, "mod.jar").createNewFile();

        when(jarRepo.listEntries(any(File.class), any()))
            .thenReturn(List.of("data/cobblemon/species/bad.json"));

        JsonObject invalidJson = new JsonObject();
        when(jarRepo.readJsonBatch(any(File.class), anyList()))
            .thenReturn(Map.of("data/cobblemon/species/bad.json", invalidJson));

        ScanService.ScanResult result = scanService.scanAndValidate(
            dir, jn -> true,
            json -> json.has("baseStats"), // validator: must have baseStats
            (c, t, s) -> {});

        assertEquals(1, result.totalCount());
        assertEquals(0, result.validPaths().size());
        assertEquals(1, result.skippedCount());
    }

    // ---- progress callback ----

    @Test
    public void scanAndValidate_shouldInvokeProgressCallback() throws Exception {
        File dir = tempFolder.newFolder("progress");
        new File(dir, "mod.jar").createNewFile();

        when(jarRepo.listEntries(any(File.class), any()))
            .thenReturn(List.of("data/cobblemon/species/a.json",
                "data/cobblemon/species/b.json", "data/cobblemon/species/c.json"));

        JsonObject json = new JsonObject();
        json.addProperty("name", "x");
        when(jarRepo.readJsonBatch(any(File.class), anyList()))
            .thenReturn(Map.of("data/cobblemon/species/a.json", json,
                "data/cobblemon/species/b.json", json, "data/cobblemon/species/c.json", json));

        scanService.scanAndValidate(
            dir, jn -> true, j -> true,
            (cur, total, status) -> progressLog.add(cur + "/" + total));

        // 收集候选阶段会先发 0/0 的"进度提示"，逐个校验的进度是 N/3
        List<String> perFile = progressLog.stream().filter(text -> text.endsWith("/3")).toList();
        assertEquals(3, perFile.size());
        assertEquals("1/3", perFile.get(0));
        assertEquals("2/3", perFile.get(1));
        assertEquals("3/3", perFile.get(2));
    }

    // ---- repo exception is swallowed ----

    @Test
    public void scanAndValidate_shouldSurviveRepoException() throws Exception {
        File dir = tempFolder.newFolder("error");
        new File(dir, "bad.jar").createNewFile();

        when(jarRepo.listEntries(any(File.class), any()))
            .thenReturn(List.of("data/cobblemon/species/bad.json"));
        when(jarRepo.readJsonBatch(any(File.class), anyList()))
            .thenThrow(new RuntimeException("JAR corrupted"));

        ScanService.ScanResult result = scanService.scanAndValidate(
            dir, jn -> true, json -> true, (c, t, s) -> {});

        assertEquals(1, result.totalCount());
        assertEquals(0, result.validPaths().size());
        assertEquals(1, result.skippedCount());
    }
}

package com.cobblemon.modifier.repository.impl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class ConfigRepositoryImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ConfigRepositoryImpl repo;
    private String realDir;

    @Before
    public void setUp() throws IOException {
        repo = new ConfigRepositoryImpl();
        repo.clearAllPluginCaches();
        realDir = tempFolder.newFolder("testDir").getAbsolutePath();
    }

    @Test
    public void getLastFolderPath_shouldReturnNullWhenNotSet() {
        assertNull(repo.getLastFolderPath());
    }

    @Test
    public void saveAndGetLastFolderPath_shouldRoundTrip() {
        repo.saveLastFolderPath(realDir);
        assertEquals(realDir, repo.getLastFolderPath());
    }

    @Test
    public void saveLastFolderPath_shouldIgnoreNull() {
        repo.saveLastFolderPath(realDir);
        repo.saveLastFolderPath(null);
        assertEquals(realDir, repo.getLastFolderPath());
    }

    @Test
    public void saveLastFolderPath_shouldIgnoreEmpty() {
        repo.saveLastFolderPath(realDir);
        repo.saveLastFolderPath("   ");
        assertEquals(realDir, repo.getLastFolderPath());
    }

    @Test
    public void getLastFolderPath_shouldReturnNullIfDeleted() {
        repo.saveLastFolderPath(realDir);
        // delete the directory
        new File(realDir).delete();
        // getLastFolderPath checks File.exists() → returns null
        assertNull(repo.getLastFolderPath());
    }

    // ---- plugin cache ----

    @Test
    public void getPluginCache_shouldReturnEmptyWhenNotSet() {
        List<String> result = repo.getPluginCache("nonexistent");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void saveAndGetPluginCache_shouldRoundTrip() {
        List<String> paths = List.of(
            "[cobblemon.jar]data/species/pika.json",
            "[mod.jar]data/species/char.json"
        );
        repo.savePluginCache("testPlugin", paths);
        List<String> result = repo.getPluginCache("testPlugin");
        assertEquals(paths, result);
    }

    @Test
    public void savePluginCache_shouldIgnoreNullName() {
        repo.savePluginCache(null, List.of("a"));
        // should not throw
    }

    @Test
    public void savePluginCache_shouldIgnoreNullPaths() {
        repo.savePluginCache("plugin", null);
        // should not throw
    }

    // ---- clearAllPluginCaches ----

    @Test
    public void clearAllPluginCaches_shouldRemoveAll() {
        repo.savePluginCache("p1", List.of("a", "b"));
        repo.savePluginCache("p2", List.of("c"));

        repo.clearAllPluginCaches();

        assertTrue(repo.getPluginCache("p1").isEmpty());
        assertTrue(repo.getPluginCache("p2").isEmpty());
    }

    // ---- legacy json paths ----

    @Test
    public void legacyPaths_shouldRoundTrip() {
        // Before save, should be empty (or contain previously persisted data)
        repo.saveLegacyJsonPaths(List.of());  // no-op guard, but try

        List<String> paths = List.of("[jar.jar]data/a.json", "[jar.jar]data/b.json");
        repo.saveLegacyJsonPaths(paths);
        List<String> result = repo.getLegacyJsonPaths();
        assertEquals(paths, result);
    }

    @Test
    public void getLegacyJsonPaths_shouldReturnNonnull() {
        // The legacy paths field may have persisted data from prior runs;
        // the contract is that this never returns null.
        assertNotNull(repo.getLegacyJsonPaths());
    }
}

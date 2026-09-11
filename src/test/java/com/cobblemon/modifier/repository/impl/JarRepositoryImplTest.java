package com.cobblemon.modifier.repository.impl;

import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

public class JarRepositoryImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private JarRepositoryImpl repo;

    @Before
    public void setUp() {
        repo = new JarRepositoryImpl();
    }

    // ---- listJsonFiles ----

    @Test
    public void listJsonFiles_shouldFindMatchingJsonFiles() throws Exception {
        File jarFile = createTestJar(
            "data/cobblemon/species/pikachu.json", "{\"name\":\"pikachu\"}",
            "data/cobblemon/species/charizard.json", "{\"name\":\"charizard\"}",
            "META-INF/MANIFEST.MF", "Manifest-Version: 1.0"
        );

        List<String> paths = repo.listJsonFiles(jarFile, List.of("data/cobblemon/species"));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("data/cobblemon/species/pikachu.json"));
        assertTrue(paths.contains("data/cobblemon/species/charizard.json"));
    }

    @Test
    public void listJsonFiles_shouldFilterByTargetPath() throws Exception {
        File jarFile = createTestJar(
            "data/species/pika.json", "{}",
            "assets/textures/sprite.png", "fake image data"
        );

        List<String> paths = repo.listJsonFiles(jarFile, List.of("data/species"));

        assertEquals(1, paths.size());
        assertEquals("data/species/pika.json", paths.get(0));
    }

    @Test
    public void listJsonFiles_shouldNotMatchNonJsonFiles() throws Exception {
        File jarFile = createTestJar(
            "data/species/info.txt", "not json"
        );

        List<String> paths = repo.listJsonFiles(jarFile, List.of("data/species"));
        assertTrue(paths.isEmpty());
    }

    // ---- readJson ----

    @Test
    public void readJson_shouldParseValidJson() throws Exception {
        File jarFile = createTestJar(
            "data/species/pikachu.json",
            "{\"name\":\"pikachu\",\"nationalPokedexNumber\":25,\"baseStats\":{\"hp\":35}}"
        );

        JsonObject json = repo.readJson(jarFile, "data/species/pikachu.json");

        assertEquals("pikachu", json.get("name").getAsString());
        assertEquals(25, json.get("nationalPokedexNumber").getAsInt());
        assertEquals(35, json.getAsJsonObject("baseStats").get("hp").getAsInt());
    }

    @Test(expected = Exception.class)
    public void readJson_shouldThrowForMissingEntry() throws Exception {
        File jarFile = createTestJar("data/other.json", "{}");
        repo.readJson(jarFile, "data/nonexistent.json");
    }

    // ---- writeJson ----

    @Test
    public void writeJson_shouldModifyAndReadBack() throws Exception {
        File jarFile = createTestJar(
            "data/species/pikachu.json", "{\"name\":\"pikachu\",\"hp\":35}"
        );

        JsonObject modified = new JsonObject();
        modified.addProperty("name", "pikachu");
        modified.addProperty("hp", 100);

        repo.writeJson(jarFile, "data/species/pikachu.json", modified);

        JsonObject readBack = repo.readJson(jarFile, "data/species/pikachu.json");
        assertEquals(100, readBack.get("hp").getAsInt());
    }

    @Test
    public void writeJson_shouldPreserveOtherEntries() throws Exception {
        File jarFile = createTestJar(
            "data/a.json", "{\"value\":1}",
            "data/b.json", "{\"value\":2}"
        );

        JsonObject modified = new JsonObject();
        modified.addProperty("value", 999);
        repo.writeJson(jarFile, "data/a.json", modified);

        // b.json should be unchanged
        JsonObject b = repo.readJson(jarFile, "data/b.json");
        assertEquals(2, b.get("value").getAsInt());
    }

    // ---- backup ----

    @Test
    public void backup_shouldCopyFile() throws Exception {
        File source = createTestJar("data/test.json", "{\"ok\":true}");
        File target = new File(tempFolder.getRoot(), "backup.jar");

        String path = repo.backup(source, target);

        assertEquals(target.getAbsolutePath(), path);
        assertTrue(target.exists());
        assertEquals(source.length(), target.length());
    }

    @Test(expected = Exception.class)
    public void backup_shouldThrowIfSourceNotFound() throws Exception {
        File source = new File(tempFolder.getRoot(), "nonexistent.jar");
        File target = new File(tempFolder.getRoot(), "backup.jar");
        repo.backup(source, target);
    }

    // ---- scanFolder ----

    @Test
    public void scanFolder_shouldScanAllJars() throws Exception {
        File dir = tempFolder.newFolder("mods");
        createTestJarInDir(dir, "a.jar", "data/species/pika.json", "{}");
        createTestJarInDir(dir, "b.jar", "data/species/char.json", "{}");

        List<String> paths = repo.scanFolder(dir, List.of("data/species"));

        assertEquals(2, paths.size());
        assertTrue(paths.stream().anyMatch(p -> p.contains("a.jar") && p.contains("pika.json")));
        assertTrue(paths.stream().anyMatch(p -> p.contains("b.jar") && p.contains("char.json")));
    }

    @Test
    public void scanFolder_shouldReturnEmptyForNonExistentFolder() throws Exception {
        File fake = new File(tempFolder.getRoot(), "does_not_exist");
        List<String> paths = repo.scanFolder(fake, List.of("data/"));
        assertTrue(paths.isEmpty());
    }

    // ---- helpers ----

    /**
     * 批量读取：一次把包里需要的文件都读出来（大整合包的性能关键路径）。
     */
    @Test
    public void readJsonBatch_shouldReadAllRequestedEntries() throws Exception {
        File jarFile = createTestJar(
            "data/species/a.json", "{\"name\":\"a\"}",
            "data/species/b.json", "{\"name\":\"b\"}",
            "data/species/c.json", "{\"name\":\"c\"}"
        );

        Map<String, JsonObject> result = repo.readJsonBatch(jarFile,
            List.of("data/species/a.json", "data/species/b.json", "data/species/missing.json"));

        assertEquals(2, result.size());
        assertEquals("a", result.get("data/species/a.json").get("name").getAsString());
        assertEquals("b", result.get("data/species/b.json").get("name").getAsString());
        assertFalse(result.containsKey("data/species/missing.json"));
    }

    @Test
    public void readJsonBatch_shouldTolerateBrokenEntry() throws Exception {
        File jarFile = createTestJar(
            "data/species/ok.json", "{\"name\":\"ok\"}",
            "data/species/broken.json", "{ this is not json"
        );

        Map<String, JsonObject> result = repo.readJsonBatch(jarFile,
            List.of("data/species/ok.json", "data/species/broken.json"));

        assertEquals(1, result.size());
        assertTrue(result.containsKey("data/species/ok.json"));
    }

    @Test
    public void readJsonBatch_shouldReturnEmptyForNoPaths() throws Exception {
        File jarFile = createTestJar("data/species/a.json", "{\"name\":\"a\"}");

        assertTrue(repo.readJsonBatch(jarFile, List.of()).isEmpty());
    }

    /**
     * Creates a temporary JAR file with the given entries (key=path, value=content).
     */
    private File createTestJar(String... pathContentPairs) throws Exception {
        return createTestJarInDir(tempFolder.newFolder("jarWork"), "test.jar", pathContentPairs);
    }

    private File createTestJarInDir(File dir, String jarName, String... pathContentPairs) throws Exception {
        File jarFile = new File(dir, jarName);
        try (FileOutputStream fos = new FileOutputStream(jarFile);
             JarOutputStream jos = new JarOutputStream(fos)) {

            for (int i = 0; i < pathContentPairs.length; i += 2) {
                String path = pathContentPairs[i];
                String content = pathContentPairs[i + 1];
                JarEntry entry = new JarEntry(path);
                jos.putNextEntry(entry);
                jos.write(content.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jarFile;
    }
}

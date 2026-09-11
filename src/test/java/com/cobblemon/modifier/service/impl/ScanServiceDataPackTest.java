package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.repository.impl.JarRepositoryImpl;
import com.cobblemon.modifier.service.ScanService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * P2：数据源补全。魔改宝可梦（赛尔号系列等）只存在于 resourcepacks / global_packs
 * 的数据包里，扫描必须能把它们收进来，并且同名文件要以数据包那份为准。
 */
public class ScanServiceDataPackTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String RUSIWANG = "data/cobblemon/species/custom/ruceking.json";
    private static final String CHARMANDER = "data/cobblemon/species/generation1/charmander.json";
    private static final String RUSIWANG_JSON = "{\"name\":\"ruceking\",\"baseStats\":{\"hp\":124}}";
    private static final String CHARMANDER_JSON = "{\"name\":\"charmander\",\"baseStats\":{\"hp\":39}}";

    private File gameDir;
    private File modsDir;
    private ScanService scanService;

    private void prepare() throws Exception {
        gameDir = tempFolder.newFolder("game");
        modsDir = new File(gameDir, "mods");
        assertTrue(modsDir.mkdirs());
        scanService = new ScanServiceImpl(new JarRepositoryImpl());
    }

    private ScanService.ScanResult scan() throws Exception {
        return scanService.scanAndValidate(modsDir, List.of("data/cobblemon/species"),
            name -> true, json -> true, (current, total, status) -> { });
    }

    /** 「加载宝可梦数据」用的全量扫描：目标是 data/，会同时收 species 与 species_additions。 */
    private ScanService.ScanResult scanAll() throws Exception {
        return scanService.scanAndValidate(modsDir, List.of("data/"),
            name -> true, json -> true, (current, total, status) -> { });
    }

    @Test
    public void scan_shouldIncludeSpeciesFromGlobalPacks() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        assertTrue(requiredData.mkdirs());
        writeZip(new File(requiredData, "seer.zip"), RUSIWANG, RUSIWANG_JSON);

        ScanService.ScanResult result = scan();

        assertEquals(1, result.totalCount());
        assertEquals(1, result.validPaths().size());
        assertEquals("seer.zip", result.validPaths().get(0).jarName());
        assertEquals(RUSIWANG, result.validPaths().get(0).jsonPath());
    }

    @Test
    public void scan_shouldIncludeSpeciesFromResourcePacks() throws Exception {
        prepare();
        File resourcePacks = new File(gameDir, "resourcepacks");
        assertTrue(resourcePacks.mkdirs());
        writeZip(new File(resourcePacks, "seer.zip"), RUSIWANG, RUSIWANG_JSON);

        ScanService.ScanResult result = scan();

        assertEquals(1, result.validPaths().size());
        assertEquals("seer.zip", result.validPaths().get(0).jarName());
    }

    @Test
    public void scan_shouldDeduplicateSamePathAcrossDataPackCopies() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        File resourcePacks = new File(gameDir, "resourcepacks");
        assertTrue(requiredData.mkdirs());
        assertTrue(resourcePacks.mkdirs());
        // 本整合包把同一批数据包在 resourcepacks 和 global_packs 各放了一份
        writeZip(new File(requiredData, "seer.zip"), RUSIWANG, RUSIWANG_JSON);
        writeZip(new File(resourcePacks, "seer.zip"), RUSIWANG, RUSIWANG_JSON);

        ScanService.ScanResult result = scan();

        assertEquals(1, result.totalCount());
        assertEquals(1, result.validPaths().size());
    }

    @Test
    public void scan_shouldPreferDataPackWhenModJarHasSamePath() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        assertTrue(requiredData.mkdirs());
        writeJar(new File(modsDir, "custom-pack.jar"), CHARMANDER, CHARMANDER_JSON);
        writeZip(new File(requiredData, "无敌怪力3.3.zip"), CHARMANDER, CHARMANDER_JSON);

        ScanService.ScanResult result = scan();

        assertEquals(1, result.validPaths().size());
        assertEquals("无敌怪力3.3.zip", result.validPaths().get(0).jarName());
    }

    @Test
    public void scan_shouldStillReadModJarsWhenNoDataPackHasTheFile() throws Exception {
        prepare();
        writeJar(new File(modsDir, "cobblemon.jar"), CHARMANDER, CHARMANDER_JSON);

        ScanService.ScanResult result = scan();

        assertEquals(1, result.validPaths().size());
        assertEquals("cobblemon.jar", result.validPaths().get(0).jarName());
    }

    @Test
    public void scan_shouldRespectJarFilterForModsOnly() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        assertTrue(requiredData.mkdirs());
        writeJar(new File(modsDir, "ignored.jar"), CHARMANDER, CHARMANDER_JSON);
        writeZip(new File(requiredData, "seer.zip"), RUSIWANG, RUSIWANG_JSON);

        ScanService.ScanResult result = scanService.scanAndValidate(modsDir,
            List.of("data/cobblemon/species"), name -> false, json -> true, (c, t, s) -> { });

        // mods 里的 jar 被过滤掉，但数据包不受 jar 名称过滤器影响
        assertEquals(1, result.validPaths().size());
        assertEquals("seer.zip", result.validPaths().get(0).jarName());
    }

    @Test
    public void scan_shouldIgnoreNonSpeciesJsonInsideDataPacks() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        assertTrue(requiredData.mkdirs());
        File zip = new File(requiredData, "seer.zip");
        // 数据包里除了 species，还有图鉴条目、骑乘参数等一堆同名结构的数据
        writeZip(zip, new String[]{
            "data/cobblemon/species/custom/ruceking.json",
            "data/cobblemon/dex_entries/seer/ruceking.json",
            "data/cobbleride/rideable_species/custom/ruceking.json",
            "data/cobblemon/advancements/test.json"
        }, new String[]{RUSIWANG_JSON, RUSIWANG_JSON, RUSIWANG_JSON, RUSIWANG_JSON});

        ScanService.ScanResult result = scan();

        assertEquals(1, result.totalCount());
        assertEquals(RUSIWANG, result.validPaths().get(0).jsonPath());
    }

    @Test
    public void scan_shouldPreferJostarOverridesOverRequiredData() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        File overrides = new File(gameDir, "global_packs/jostar_overrides");
        assertTrue(requiredData.mkdirs());
        assertTrue(overrides.mkdirs());
        // 同一个物种在正式数据包和覆盖包里各有一份，覆盖包（游戏里优先级更高）应当胜出
        writeZip(new File(requiredData, "星之卡比1.1.zip"), RUSIWANG, RUSIWANG_JSON);
        writeZip(new File(overrides, "move_fix_20260805.zip"), RUSIWANG, RUSIWANG_JSON);

        ScanService.ScanResult result = scan();

        assertEquals(1, result.validPaths().size());
        assertEquals("move_fix_20260805.zip", result.validPaths().get(0).jarName());
    }

    @Test
    public void scan_shouldIncludeSpeciesAdditions() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        assertTrue(requiredData.mkdirs());
        // 整合包用 species_additions 整体覆盖物种字段（地龙的招式表就是被它替换的）
        writeZip(new File(requiredData, "§eJoStar§c大集合.zip"),
            "data/move_calibration/species_additions/garchomp_move.json",
            "{\"target\":\"cobblemon:garchomp\",\"moves\":[\"1:tackle\"]}");

        ScanService.ScanResult result = scanAll();

        assertEquals(1, result.validPaths().size());
        assertEquals("data/move_calibration/species_additions/garchomp_move.json",
            result.validPaths().get(0).jsonPath());
    }

    // ---- helpers ----

    private static void writeZip(File file, String[] entryNames, String[] jsons) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < entryNames.length; i++) {
                out.putNextEntry(new ZipEntry(entryNames[i]));
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                writer.write(jsons[i]);
                writer.flush();
                out.closeEntry();
            }
        }
    }

    private static void writeJar(File file, String entryName, String json) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new JarEntry(entryName));
            // 注意：不能关这个 Writer —— 关掉它会连底层 JarOutputStream 一起关掉，
            // 后面的 closeEntry() 就会抛 "Stream closed"。
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write(json);
            writer.flush();
            out.closeEntry();
        }
    }

    private static void writeZip(File file, String entryName, String json) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry(entryName));
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write(json);
            writer.flush();
            out.closeEntry();
        }
    }
}

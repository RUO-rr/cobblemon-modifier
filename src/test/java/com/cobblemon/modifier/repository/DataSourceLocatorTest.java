package com.cobblemon.modifier.repository;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class DataSourceLocatorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File gameDir;
    private File modsDir;

    private void prepare() throws Exception {
        gameDir = tempFolder.newFolder("game");
        modsDir = new File(gameDir, "mods");
        assertTrue(modsDir.mkdirs());
    }

    @Test
    public void roots_shouldOnlyContainModsWhenNoDataPackFoldersExist() throws Exception {
        prepare();

        List<File> roots = DataSourceLocator.roots(modsDir);

        assertEquals(1, roots.size());
        assertEquals(modsDir, roots.get(0));
    }

    @Test
    public void roots_shouldPutDataPacksBeforeMods() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        File resourcePacks = new File(gameDir, "resourcepacks");
        assertTrue(requiredData.mkdirs());
        assertTrue(resourcePacks.mkdirs());

        List<File> roots = DataSourceLocator.roots(modsDir);

        assertEquals(3, roots.size());
        assertEquals(requiredData, roots.get(0));
        assertEquals(resourcePacks, roots.get(1));
        assertEquals(modsDir, roots.get(2));
    }

    @Test
    public void archives_shouldTakeJarsFromModsAndZipsFromDataPacks() throws Exception {
        prepare();
        File resourcePacks = new File(gameDir, "resourcepacks");
        assertTrue(resourcePacks.mkdirs());
        assertTrue(new File(modsDir, "mod.jar").createNewFile());
        assertTrue(new File(modsDir, "not-an-archive.txt").createNewFile());
        assertTrue(new File(resourcePacks, "seer.zip").createNewFile());
        assertTrue(new File(resourcePacks, "readme.txt").createNewFile());

        List<File> mods = DataSourceLocator.archives(modsDir, modsDir);
        List<File> packs = DataSourceLocator.archives(resourcePacks, modsDir);

        assertEquals(1, mods.size());
        assertEquals("mod.jar", mods.get(0).getName());
        assertEquals(1, packs.size());
        assertEquals("seer.zip", packs.get(0).getName());
    }

    @Test
    public void resolve_shouldPreferDataPackOverMods() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        assertTrue(requiredData.mkdirs());
        File inMods = new File(modsDir, "seer.zip");
        File inPack = new File(requiredData, "seer.zip");
        assertTrue(inMods.createNewFile());
        assertTrue(inPack.createNewFile());

        assertEquals(inPack, DataSourceLocator.resolve(modsDir, "seer.zip"));
    }

    @Test
    public void resolve_shouldPreferJostarOverridesOverRequiredData() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        File overrides = new File(gameDir, "global_packs/jostar_overrides");
        assertTrue(requiredData.mkdirs());
        assertTrue(overrides.mkdirs());
        File inRequired = new File(requiredData, "move_fix.zip");
        File inOverrides = new File(overrides, "move_fix.zip");
        assertTrue(inRequired.createNewFile());
        assertTrue(inOverrides.createNewFile());

        // level.dat 里 jostar_overrides 排在 required_data 之后 = 优先级更高
        assertEquals(inOverrides, DataSourceLocator.resolve(modsDir, "move_fix.zip"));
    }

    @Test
    public void roots_shouldPutJostarOverridesFirst() throws Exception {
        prepare();
        File requiredData = new File(gameDir, "global_packs/required_data");
        File overrides = new File(gameDir, "global_packs/jostar_overrides");
        assertTrue(requiredData.mkdirs());
        assertTrue(overrides.mkdirs());

        List<File> roots = DataSourceLocator.roots(modsDir);

        assertEquals(overrides, roots.get(0));
        assertEquals(requiredData, roots.get(1));
    }

    @Test
    public void resolve_shouldFallBackToMods() throws Exception {
        prepare();
        File jar = new File(modsDir, "cobblemon.jar");
        assertTrue(jar.createNewFile());

        assertEquals(jar, DataSourceLocator.resolve(modsDir, "cobblemon.jar"));
    }

    @Test
    public void resolve_shouldReturnNullWhenMissing() throws Exception {
        prepare();

        assertNull(DataSourceLocator.resolve(modsDir, "nope.zip"));
        assertNull(DataSourceLocator.resolve(modsDir, null));
    }
}

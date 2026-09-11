package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.repository.impl.JarRepositoryImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

/**
 * 物种来源索引：把"本体 + 所有 species_additions 覆盖文件"找齐，供扇出写入使用。
 */
public class SpeciesOverrideIndexImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String SPECIES = "data/cobblemon/species/generation4/garchomp.json";
    private static final String ADD_MOVES =
        "data/move_calibration/species_additions/garchomp_move.json";
    private static final String ADD_CHANGE =
        "data/newsmega/species_additions/generation4/garchomp_change.json";

    private File gameDir;
    private File modsDir;

    private void prepare() throws Exception {
        gameDir = tempFolder.newFolder("game");
        modsDir = new File(gameDir, "mods");
        assertTrue(modsDir.mkdirs());
        // 本体在 mods 里
        writeJar(new File(modsDir, "cobblemon.jar"),
            SPECIES, "{\"name\":\"Garchomp\",\"baseStats\":{\"hp\":108},\"moves\":[\"1:tackle\"]}");
        // 两个覆盖文件，一个在数据包、一个在模组 jar
        File packDir = new File(gameDir, "global_packs/required_data");
        assertTrue(packDir.mkdirs());
        writeJar(new File(packDir, "jostar.zip"),
            ADD_MOVES, "{\"target\":\"cobblemon:garchomp\",\"moves\":[\"1:tackle\"]}");
        writeJar(new File(modsDir, "mushiromega.jar"),
            ADD_CHANGE, "{\"target\":\"cobblemon:garchomp\",\"moves\":[\"1:tackle\"],\"forms\":[]}");
    }

    private SpeciesOverrideIndexImpl index() {
        return new SpeciesOverrideIndexImpl(new JarRepositoryImpl());
    }

    @Test
    public void otherSourcesOf_shouldReturnSpeciesAndAllAdditions() throws Exception {
        prepare();

        List<JarResourcePath> sources =
            index().otherSourcesOf(modsDir, "cobblemon:garchomp", null);

        List<String> paths = sources.stream().map(JarResourcePath::jsonPath).toList();
        assertEquals(3, paths.size());
        assertTrue(paths.contains(SPECIES));
        assertTrue(paths.contains(ADD_MOVES));
        assertTrue(paths.contains(ADD_CHANGE));
    }

    @Test
    public void otherSourcesOf_shouldExcludeTheEditedFile() throws Exception {
        prepare();

        List<JarResourcePath> sources =
            index().otherSourcesOf(modsDir, "cobblemon:garchomp", ADD_MOVES);

        List<String> paths = sources.stream().map(JarResourcePath::jsonPath).toList();
        assertEquals(2, paths.size());
        assertFalse(paths.contains(ADD_MOVES));
        assertTrue(paths.contains(SPECIES));
        assertTrue(paths.contains(ADD_CHANGE));
    }

    @Test
    public void otherSourcesOf_shouldReturnEmptyForUnknownSpecies() throws Exception {
        prepare();

        assertTrue(index().otherSourcesOf(modsDir, "cobblemon:missingno", null).isEmpty());
    }

    /** 同一个 zip 在 resourcepacks 与 global_packs 各有一份，不能重复计算。 */
    @Test
    public void otherSourcesOf_shouldNotDuplicateSameFileFromTwoRoots() throws Exception {
        prepare();
        File resourcePacks = new File(gameDir, "resourcepacks");
        assertTrue(resourcePacks.mkdirs());
        // 和大集合那份完全同路径的一份拷贝
        writeJar(new File(resourcePacks, "jostar.zip"),
            ADD_MOVES, "{\"target\":\"cobblemon:garchomp\",\"moves\":[\"1:tackle\"]}");

        List<JarResourcePath> sources =
            index().otherSourcesOf(modsDir, "cobblemon:garchomp", null);

        List<String> paths = sources.stream().map(JarResourcePath::jsonPath).toList();
        assertEquals(3, paths.size());
        assertEquals(1, paths.stream().filter(p -> p.equals(ADD_MOVES)).count());
    }

    @Test
    public void otherSourcesOf_shouldReturnEmptyForNullInputs() throws Exception {
        prepare();

        assertTrue(index().otherSourcesOf(modsDir, null, null).isEmpty());
        assertTrue(index().otherSourcesOf(null, "cobblemon:garchomp", null).isEmpty());
    }

    private static void writeJar(File file, String entryName, String content) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new JarEntry(entryName));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}

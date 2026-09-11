package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.repository.impl.JarRepositoryImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

/**
 * 已知招式表：基础招式来自 showdown/data/moves.js，自定义招式来自数据包里的 .js 脚本。
 */
public class MoveCatalogImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** 仿照真实的 moves.js：顶层键缩进两格。 */
    private static final String MOVES_JS = "var __defProp = Object.defineProperty;\n"
        + "var Moves = {\n"
        + "  tackle: {\n    basePower: 40,\n    accuracy: 100\n  },\n"
        + "  dragondance: {\n    basePower: 0\n  },\n"
        + "  aerialace: {\n    basePower: 60\n  }\n"
        + "};\n";

    private Path gameDirWithShowdown() throws Exception {
        File game = tempFolder.newFolder("game");
        Path moves = game.toPath().resolve("showdown").resolve("data").resolve("moves.js");
        Files.createDirectories(moves.getParent());
        Files.writeString(moves, MOVES_JS, StandardCharsets.UTF_8);
        Files.createDirectories(game.toPath().resolve("mods"));
        return game.toPath();
    }

    @Test
    public void knownMoveIds_shouldReadBaseMovesFromShowdown() throws Exception {
        Path game = gameDirWithShowdown();

        MoveCatalogImpl catalog = new MoveCatalogImpl(new JarRepositoryImpl(), game);

        assertTrue(catalog.isReady());
        assertTrue(catalog.knownMoveIds().contains("tackle"));
        assertTrue(catalog.knownMoveIds().contains("dragondance"));
        assertEquals(3, catalog.knownMoveIds().size());
    }

    @Test
    public void knownMoveIds_shouldReadCustomMovesFromDataPacks() throws Exception {
        Path game = gameDirWithShowdown();
        File packDir = game.resolve("global_packs/required_data").toFile();
        assertTrue(packDir.mkdirs());
        writeJar(new File(packDir, "seer.zip"),
            "data/cobblemon/moves/ruceking/blitzstrike.js",
            "data/cobblemon/moves/ares/divineprotection.js");

        MoveCatalogImpl catalog = new MoveCatalogImpl(new JarRepositoryImpl(), game);

        assertTrue(catalog.knownMoveIds().contains("blitzstrike"));
        assertTrue(catalog.knownMoveIds().contains("divineprotection"));
    }

    @Test
    public void knownMoveIds_shouldNormalizeCustomFileNames() throws Exception {
        Path game = gameDirWithShowdown();
        File packDir = game.resolve("resourcepacks").toFile();
        assertTrue(packDir.mkdirs());
        writeJar(new File(packDir, "custom.zip"), "data/cobblemon/moves/pack/dragon-dance.js");

        MoveCatalogImpl catalog = new MoveCatalogImpl(new JarRepositoryImpl(), game);

        assertTrue(catalog.knownMoveIds().contains("dragondance"));
    }

    @Test
    public void isReady_shouldBeFalseWhenNothingFound() throws Exception {
        File game = tempFolder.newFolder("emptyGame");

        MoveCatalogImpl catalog = new MoveCatalogImpl(new JarRepositoryImpl(), game.toPath());

        assertFalse(catalog.isReady());
    }

    private static void writeJar(File file, String... entryNames) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            for (String name : entryNames) {
                out.putNextEntry(new JarEntry(name));
                out.write("{}".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}

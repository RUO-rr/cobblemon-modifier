package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.model.MoveData;
import com.cobblemon.modifier.model.MoveInfo;
import com.cobblemon.modifier.repository.impl.JarRepositoryImpl;
import com.cobblemon.modifier.repository.impl.OverrideRepositoryImpl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 招式数据服务测试（真实临时文件 + 真实 JAR，不 mock 文件系统）。
 */
public class MoveDataServiceImplTest {

    private static final String BUNDLE = """
        export const Moves = {
          tackle: {
            num: 33,
            accuracy: 100,
            basePower: 40,
            category: "Physical",
            name: "Tackle",
            pp: 35,
            priority: 0,
            flags: { contact: 1, protect: 1 },
            secondary: null,
            target: "normal",
            type: "Normal"
          },
          tailglow: {
            num: 294,
            accuracy: true,
            basePower: 0,
            category: "Status",
            name: "Tail Glow",
            pp: 20,
            priority: 0,
            type: "Bug"
          },
          flamethrower: {
            num: 53,
            accuracy: 100,
            basePower: 90,
            category: "Special",
            name: "Flamethrower",
            pp: 15,
            priority: 0,
            type: "Fire"
          }
        };
        """;

    private static final String WHITE_ALBUM = """
        {
          num: 10001,
          accuracy: 100,
          basePower: 100,
          category: "Special",
          name: "White Album",
          pp: 5,
          priority: 0,
          flags: { protect: 1, mirror: 1 },
          onModifyMove(move, pokemon) {
            move.category = "Physical";
          },
          type: "Steel"
        }
        """;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path gameDir;
    private Path modsDir;
    private Path staging;
    private OverrideRepositoryImpl overrideRepo;
    private MoveDataServiceImpl service;

    @Before
    public void setUp() throws Exception {
        gameDir = tmp.newFolder("game").toPath();
        modsDir = Files.createDirectories(gameDir.resolve("mods"));
        Path showdown = Files.createDirectories(gameDir.resolve("showdown").resolve("data"));
        Files.writeString(showdown.resolve("moves.js"), BUNDLE, StandardCharsets.UTF_8);
        writeJar(modsDir.resolve("custom.jar"),
            Map.of("data/cobblemon/moves/whitealbum.js", WHITE_ALBUM));
        staging = tmp.newFolder("staging").toPath();
        overrideRepo = new OverrideRepositoryImpl(staging);
        service = new MoveDataServiceImpl(new JarRepositoryImpl(), overrideRepo, gameDir);
    }

    // ================================================================
    // 索引与搜索
    // ================================================================

    @Test
    public void indexMergesBaseAndCustomMoves() {
        assertEquals(4, service.totalCount(modsDir.toFile()));
    }

    @Test
    public void searchMatchesIdAndEnglishName() {
        assertEquals(1, service.search(modsDir.toFile(), "tackle", 50).size());
        // 英文名、忽略大小写
        List<MoveInfo> byName = service.search(modsDir.toFile(), "FLAME", 50);
        assertEquals(1, byName.size());
        assertEquals("flamethrower", byName.get(0).id());
        // 部分匹配
        assertTrue(service.search(modsDir.toFile(), "album", 50).stream()
            .anyMatch(info -> info.id().equals("whitealbum")));
        // 空查询返回全部
        assertEquals(4, service.search(modsDir.toFile(), "", 50).size());
        // limit 生效
        assertEquals(2, service.search(modsDir.toFile(), "", 2).size());
        // 无匹配
        assertTrue(service.search(modsDir.toFile(), "notamove", 50).isEmpty());
    }

    @Test
    public void customMoveWinsOverBaseMoveWithSameId() throws Exception {
        writeJar(modsDir.resolve("override-tackle.jar"),
            Map.of("data/cobblemon/moves/tackle.js", WHITE_ALBUM));
        service.invalidate();

        MoveInfo tackle = service.search(modsDir.toFile(), "tackle", 5).get(0);
        assertTrue("数据包里的定义应当盖过基础招式", tackle.custom());
    }

    // ================================================================
    // 读取
    // ================================================================

    @Test
    public void loadReadsBaseMoveValues() throws Exception {
        MoveData data = service.load(baseMove("tackle"));

        assertEquals("40", data.basePower());
        assertEquals("100", data.accuracy());
        assertEquals("35", data.pp());
        assertEquals("0", data.priority());
        assertEquals("Tackle", data.displayName());
        assertEquals("Normal", data.displayType());
        assertFalse(data.overridden());
    }

    @Test
    public void loadReadsCustomMoveValues() throws Exception {
        MoveData data = service.load(customMove("whitealbum"));

        assertEquals("100", data.basePower());
        assertEquals("White Album", data.displayName());
        assertEquals("Steel", data.displayType());
        assertTrue(data.overridden() == false);
    }

    @Test
    public void loadKeepsAccuracyTrueForStatusMoves() throws Exception {
        MoveData data = service.load(baseMove("tailglow"));

        assertEquals("true", data.accuracy());
        assertEquals("0", data.basePower());
    }

    // ================================================================
    // 保存 / 还原
    // ================================================================

    @Test
    public void saveWritesOverrideAndOnlyChangedFields() throws Exception {
        MoveInfo tackle = baseMove("tackle");
        String detail = service.save(tackle, values("90", "100", "35", "0", "Normal"));

        assertTrue(detail.contains("威力 40 → 90"));
        Path override = staging.resolve("data/cobblemon/moves/tackle.js");
        assertTrue(Files.isRegularFile(override));
        String text = Files.readString(override, StandardCharsets.UTF_8);
        assertTrue("招式名等其它字段必须原样保留", text.contains("name: \"Tackle\""));
        assertTrue(text.contains("basePower: 90"));
        assertTrue(text.contains("flags"));

        MoveData reloaded = service.load(tackle);
        assertEquals("90", reloaded.basePower());
        assertTrue(reloaded.overridden());
    }

    @Test
    public void saveKeepsPreviousChangesWhenEditingAnotherField() throws Exception {
        MoveInfo tackle = baseMove("tackle");
        service.save(tackle, values("90", "100", "35", "0", "Normal"));
        service.save(tackle, values("90", "80", "35", "0", "Normal"));

        MoveData reloaded = service.load(tackle);
        assertEquals("90", reloaded.basePower());
        assertEquals("80", reloaded.accuracy());
    }

    @Test
    public void saveReportsNoChangeWhenNothingEdited() throws Exception {
        String detail = service.save(baseMove("tackle"), values("40", "100", "35", "0", "Normal"));
        assertEquals("没有改动内容", detail);
        assertFalse(Files.exists(staging.resolve("data/cobblemon/moves/tackle.js")));
    }

    @Test
    public void saveWorksForCustomMoves() throws Exception {
        MoveInfo white = customMove("whitealbum");
        service.save(white, values("150", "100", "5", "1", "Ice"));

        MoveData reloaded = service.load(white);
        assertEquals("150", reloaded.basePower());
        assertEquals("1", reloaded.priority());
        assertEquals("Ice", reloaded.displayType());
        String text = Files.readString(staging.resolve("data/cobblemon/moves/whitealbum.js"),
            StandardCharsets.UTF_8);
        assertTrue("自定义招式的函数体必须保留", text.contains("onModifyMove"));
    }

    @Test
    public void saveAcceptsAccuracyTrue() throws Exception {
        MoveInfo tackle = baseMove("tackle");
        service.save(tackle, values("40", "true", "35", "0", "Normal"));

        assertEquals("true", service.load(tackle).accuracy());
    }

    @Test
    public void restoreDeletesOverride() throws Exception {
        MoveInfo tackle = baseMove("tackle");
        service.save(tackle, values("90", "100", "35", "0", "Normal"));
        assertTrue(service.hasOverride(tackle));

        assertTrue(service.restore(tackle));
        assertFalse(service.hasOverride(tackle));
        assertEquals("40", service.load(tackle).basePower());
        assertFalse(service.restore(tackle));
    }

    // ================================================================
    // 校验
    // ================================================================

    @Test
    public void saveRejectsIllegalValues() {
        MoveInfo tackle = baseMove("tackle");

        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("abc", "100", "35", "0", "Normal")));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("-5", "100", "35", "0", "Normal")));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("40", "0", "35", "0", "Normal")));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("40", "100", "0", "0", "Normal")));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("40", "100", "35", "99", "Normal")));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("40", "100", "35", "0", "Foo")));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(tackle, values("40", "100", "35", "0", "")));
    }

    // ================================================================
    // 工具
    // ================================================================

    private MoveInfo baseMove(String id) {
        return service.search(modsDir.toFile(), id, 5).stream()
            .filter(info -> info.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("找不到招式：" + id));
    }

    private MoveInfo customMove(String id) {
        return baseMove(id);
    }

    private static Map<String, String> values(String basePower, String accuracy,
                                              String pp, String priority, String type) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("basePower", basePower);
        map.put("accuracy", accuracy);
        map.put("pp", pp);
        map.put("priority", priority);
        map.put("type", type);
        return map;
    }

    private static void writeJar(Path jar, Map<String, String> entries) throws Exception {
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jarOut.putNextEntry(new JarEntry(entry.getKey()));
                jarOut.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jarOut.closeEntry();
            }
        }
    }
}

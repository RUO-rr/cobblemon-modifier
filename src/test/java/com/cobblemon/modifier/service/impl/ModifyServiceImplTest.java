package com.cobblemon.modifier.service.impl;

import com.cobblemon.modifier.core.JsonModifier;
import com.cobblemon.modifier.model.JarResourcePath;
import com.cobblemon.modifier.repository.JarRepository;
import com.cobblemon.modifier.repository.OverrideRepository;
import com.cobblemon.modifier.service.SpeciesOverrideIndex;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.mockito.ArgumentCaptor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ModifyServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private JarRepository jarRepo;
    private OverrideRepository overrideRepo;
    private ModifyServiceImpl modifyService;

    @Before
    public void setUp() {
        jarRepo = mock(JarRepository.class);
        overrideRepo = mock(OverrideRepository.class);
        modifyService = new ModifyServiceImpl(jarRepo, overrideRepo);
    }

    @Test
    public void readRaw_shouldPreferOverride() throws Exception {
        File dir = tempFolder.newFolder("mods");
        JarResourcePath path = JarResourcePath.parse("[test.jar]data/species/pika.json");

        JsonObject fromOverride = new JsonObject();
        fromOverride.addProperty("hp", 100);
        when(overrideRepo.readOverride("data/species/pika.json")).thenReturn(fromOverride);

        JsonObject result = modifyService.readRaw(path, dir);

        assertEquals(100, result.get("hp").getAsInt());
        verify(jarRepo, never()).readJson(any(File.class), anyString());
    }

    @Test
    public void readRaw_shouldFallBackToJar() throws Exception {
        File dir = tempFolder.newFolder("mods2");
        assertTrue(new File(dir, "test.jar").createNewFile());
        JarResourcePath path = JarResourcePath.parse("[test.jar]data/species/pika.json");

        JsonObject expected = new JsonObject();
        expected.addProperty("name", "pikachu");
        when(overrideRepo.readOverride("data/species/pika.json")).thenReturn(null);
        when(jarRepo.readJson(any(File.class), eq("data/species/pika.json")))
            .thenReturn(expected);

        JsonObject result = modifyService.readRaw(path, dir);

        assertEquals("pikachu", result.get("name").getAsString());
    }

    @Test
    public void readAndParse_shouldDelegateToPlugin() throws Exception {
        File dir = tempFolder.newFolder("parse");
        assertTrue(new File(dir, "mod.jar").createNewFile());
        JarResourcePath path = JarResourcePath.parse("[mod.jar]data/species/bulba.json");

        JsonObject json = new JsonObject();
        json.addProperty("name", "bulbasaur");
        when(overrideRepo.readOverride(anyString())).thenReturn(null);
        when(jarRepo.readJson(any(File.class), anyString())).thenReturn(json);

        JsonModifier plugin = mock(JsonModifier.class);
        Map<String, Object> parsed = Map.of("hp", 45);
        when(plugin.parseJson(json)).thenReturn(parsed);

        Map<String, Object> result = modifyService.readAndParse(path, dir, plugin);

        assertEquals(45, result.get("hp"));
        verify(plugin).parseJson(json);
    }

    @Test
    public void modifyAndSave_shouldWriteOverrideInsteadOfJar() throws Exception {
        File dir = tempFolder.newFolder("save");
        assertTrue(new File(dir, "mod.jar").createNewFile());
        JarResourcePath path = JarResourcePath.parse("[mod.jar]data/species/eevee.json");

        JsonObject original = new JsonObject();
        original.addProperty("hp", 55);
        when(overrideRepo.readOverride(anyString())).thenReturn(null);
        when(jarRepo.readJson(any(File.class), anyString())).thenReturn(original);

        JsonObject modified = new JsonObject();
        modified.addProperty("hp", 100);

        JsonModifier plugin = mock(JsonModifier.class);
        Map<String, Object> newValues = Map.of("hp_base", 100);
        when(plugin.modifyJson(original, newValues)).thenReturn(modified);

        modifyService.modifyAndSave(path, dir, plugin, newValues);

        verify(plugin).modifyJson(original, newValues);
        verify(overrideRepo).writeOverride("data/species/eevee.json", modified);
        verify(jarRepo, never()).writeJson(any(File.class), anyString(), any(JsonObject.class));
    }

    // ---- B1 扇出：改动字段同步到该物种的其它来源文件 ----

    /** 同物种的其它来源（本体 + 覆盖文件）都要收到同一个改动。 */
    @Test
    public void modifyAndSave_shouldFanOutChangedFieldToOtherSources() throws Exception {
        File game = tempFolder.newFolder("fanout");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File packs = new File(game, "global_packs/required_data");
        assertTrue(packs.mkdirs());
        assertTrue(new File(mods, "cobblemon.jar").createNewFile());
        assertTrue(new File(packs, "jostar.zip").createNewFile());

        String additionPath = "data/move_calibration/species_additions/garchomp_move.json";
        SpeciesOverrideIndex index = (folder, speciesId, except) ->
            List.of(JarResourcePath.of("jostar.zip", additionPath));

        modifyService = new ModifyServiceImpl(jarRepo, overrideRepo, index);

        JarResourcePath path =
            JarResourcePath.parse("[cobblemon.jar]data/cobblemon/species/generation4/garchomp.json");
        JsonObject original = new JsonObject();
        original.addProperty("name", "Garchomp");
        original.add("moves", arrayOf("1:tackle"));
        JsonObject modified = new JsonObject();
        modified.addProperty("name", "Garchomp");
        modified.add("moves", arrayOf("1:tackle", "1:dragondance"));

        when(overrideRepo.readOverride(anyString())).thenReturn(null);
        when(jarRepo.readJson(any(File.class), eq(path.jsonPath()))).thenReturn(original);
        JsonObject addition = new JsonObject();
        addition.addProperty("target", "cobblemon:garchomp");
        addition.add("moves", arrayOf("1:tackle"));
        when(jarRepo.readJson(any(File.class), eq(additionPath))).thenReturn(addition);

        JsonModifier plugin = mock(JsonModifier.class);
        Map<String, Object> newValues = Map.of("moves", "1:dragondance");
        when(plugin.modifyJson(original, newValues)).thenReturn(modified);

        int synced = modifyService.modifyAndSave(path, mods, plugin, newValues);

        assertEquals(1, synced);
        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(overrideRepo).writeOverride(eq(additionPath), captor.capture());
        assertEquals(2, captor.getValue().getAsJsonArray("moves").size());
        assertEquals("1:dragondance",
            captor.getValue().getAsJsonArray("moves").get(1).getAsString());
        // target 等其它字段必须原样保留
        assertEquals("cobblemon:garchomp", captor.getValue().get("target").getAsString());
    }

    /** forms 按形态名合并，不能把别的模组加的形态（如 ZA 超进化）删掉。 */
    @Test
    public void fanOut_shouldMergeFormsByNameAndKeepExtraForms() throws Exception {
        File game = tempFolder.newFolder("fanoutForms");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File packs = new File(game, "global_packs/required_data");
        assertTrue(packs.mkdirs());
        assertTrue(new File(mods, "cobblemon.jar").createNewFile());
        assertTrue(new File(packs, "zamega.zip").createNewFile());

        String additionPath = "data/cobblemon/species_additions/generation3/absol_mega.json";
        SpeciesOverrideIndex index = (folder, speciesId, except) ->
            List.of(JarResourcePath.of("zamega.zip", additionPath));
        modifyService = new ModifyServiceImpl(jarRepo, overrideRepo, index);

        JarResourcePath path =
            JarResourcePath.parse("[cobblemon.jar]data/cobblemon/species/generation3/absol.json");
        JsonObject original = new JsonObject();
        original.addProperty("name", "Absol");
        original.add("forms", arrayOf(form("Mega", 150)));
        JsonObject modified = new JsonObject();
        modified.addProperty("name", "Absol");
        modified.add("forms", arrayOf(form("Mega", 170)));

        when(overrideRepo.readOverride(anyString())).thenReturn(null);
        when(jarRepo.readJson(any(File.class), eq(path.jsonPath()))).thenReturn(original);
        // ZA 的覆盖文件里有它自己的 Mega-Z 形态，必须保留
        JsonObject addition = new JsonObject();
        addition.addProperty("target", "cobblemon:absol");
        addition.add("forms", arrayOf(form("Mega", 150), form("Mega-Z", 151)));
        when(jarRepo.readJson(any(File.class), eq(additionPath))).thenReturn(addition);

        JsonModifier plugin = mock(JsonModifier.class);
        Map<String, Object> newValues = Map.of("forms", "changed");
        when(plugin.modifyJson(original, newValues)).thenReturn(modified);

        int synced = modifyService.modifyAndSave(path, mods, plugin, newValues);

        assertEquals(1, synced);
        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(overrideRepo).writeOverride(eq(additionPath), captor.capture());
        var forms = captor.getValue().getAsJsonArray("forms");
        assertEquals(2, forms.size());
        assertEquals(170, forms.get(0).getAsJsonObject().get("baseStats")
            .getAsJsonObject().get("attack").getAsInt());
        assertEquals("Mega-Z", forms.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals(151, forms.get(1).getAsJsonObject().get("baseStats")
            .getAsJsonObject().get("attack").getAsInt());
    }

    // ---- 还原：扇出写过的覆盖文件也要一起删 ----

    @Test
    public void restoreOverrides_shouldDeleteFanOutOverridesToo() throws Exception {
        File game = tempFolder.newFolder("restore");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File packs = new File(game, "global_packs/required_data");
        assertTrue(packs.mkdirs());
        assertTrue(new File(mods, "cobblemon.jar").createNewFile());
        assertTrue(new File(packs, "jostar.zip").createNewFile());

        String speciesPath = "data/cobblemon/species/generation4/garchomp.json";
        String additionPath = "data/move_calibration/species_additions/garchomp_move.json";
        SpeciesOverrideIndex index = (folder, speciesId, except) ->
            List.of(JarResourcePath.of("jostar.zip", additionPath));
        modifyService = new ModifyServiceImpl(jarRepo, overrideRepo, index);

        JarResourcePath path = JarResourcePath.parse("[cobblemon.jar]" + speciesPath);
        JsonObject species = new JsonObject();
        species.addProperty("name", "Garchomp");
        when(overrideRepo.readOverride(anyString())).thenReturn(null);
        when(jarRepo.readJson(any(File.class), eq(speciesPath))).thenReturn(species);
        when(overrideRepo.deleteOverride(anyString())).thenReturn(true);

        int removed = modifyService.restoreOverrides(path, mods);

        assertEquals(2, removed);
        verify(overrideRepo).deleteOverride(speciesPath);
        verify(overrideRepo).deleteOverride(additionPath);
    }

    @Test
    public void restoreOverrides_shouldCountOnlyExistingOverrides() throws Exception {
        File game = tempFolder.newFolder("restorePartial");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File packs = new File(game, "global_packs/required_data");
        assertTrue(packs.mkdirs());
        assertTrue(new File(mods, "cobblemon.jar").createNewFile());
        assertTrue(new File(packs, "jostar.zip").createNewFile());

        String speciesPath = "data/cobblemon/species/generation4/garchomp.json";
        String additionPath = "data/move_calibration/species_additions/garchomp_move.json";
        SpeciesOverrideIndex index = (folder, speciesId, except) ->
            List.of(JarResourcePath.of("jostar.zip", additionPath));
        modifyService = new ModifyServiceImpl(jarRepo, overrideRepo, index);

        JarResourcePath path = JarResourcePath.parse("[cobblemon.jar]" + speciesPath);
        JsonObject species = new JsonObject();
        when(overrideRepo.readOverride(anyString())).thenReturn(null);
        when(jarRepo.readJson(any(File.class), eq(speciesPath))).thenReturn(species);
        // 只有本体那份有覆盖，扇出的那份没有
        when(overrideRepo.deleteOverride(speciesPath)).thenReturn(true);
        when(overrideRepo.deleteOverride(additionPath)).thenReturn(false);

        assertEquals(1, modifyService.restoreOverrides(path, mods));
    }

    private static JsonArray arrayOf(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray arrayOf(com.google.gson.JsonElement... values) {
        JsonArray array = new JsonArray();
        for (com.google.gson.JsonElement value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonObject form(String name, int attack) {
        JsonObject form = new JsonObject();
        form.addProperty("name", name);
        JsonObject stats = new JsonObject();
        stats.addProperty("attack", attack);
        form.add("baseStats", stats);
        return form;
    }
}

package com.cobblemon.modifier.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpeciesOverrideIndexTest {

    @Test
    public void speciesIdOfPath_shouldUseNamespaceAndFileName() {
        // 物种标识符 = 数据包命名空间 + 文件名（不含目录），与 species_additions 的 target 一致
        assertEquals("cobblemon:garchomp",
            SpeciesOverrideIndex.speciesIdOfPath("data/cobblemon/species/generation4/garchomp.json"));
        assertEquals("cobblemon:ruceking",
            SpeciesOverrideIndex.speciesIdOfPath("data/cobblemon/species/custom/ruceking.json"));
        assertEquals("cobblemon:eborotto",
            SpeciesOverrideIndex.speciesIdOfPath(
                "data/cobblemon/species/custom/eborotto/eborotto.json"));
    }

    @Test
    public void speciesIdOfPath_shouldLowercase() {
        assertEquals("cobblemon:baxcalibur",
            SpeciesOverrideIndex.speciesIdOfPath("data/cobblemon/species/generation9/Baxcalibur.json"));
    }

    @Test
    public void speciesIdOfPath_shouldReturnNullForNonSpeciesPaths() {
        assertNull(SpeciesOverrideIndex.speciesIdOfPath(
            "data/move_calibration/species_additions/garchomp_move.json"));
        assertNull(SpeciesOverrideIndex.speciesIdOfPath("data/cobblemon/spawn_pool_world/x.json"));
        assertNull(SpeciesOverrideIndex.speciesIdOfPath(null));
    }
}

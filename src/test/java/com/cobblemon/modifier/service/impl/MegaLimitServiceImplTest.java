package com.cobblemon.modifier.service.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Mega 限制开关（mega_showdown 的 multipleMegas）。
 *
 * <p>测试环境里没有 mega_showdown，所以热重载会返回 false，
 * 但配置文件本身应当被正确读写。
 */
public class MegaLimitServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path configPath() throws Exception {
        File dir = tempFolder.newFolder("mega_showdown");
        return new File(dir, "config.json").toPath();
    }

    @Test
    public void isMultipleMegasEnabled_shouldDefaultToFalseWhenFileMissing() throws Exception {
        MegaLimitServiceImpl service = new MegaLimitServiceImpl(configPath());

        assertFalse(service.isMultipleMegasEnabled());
    }

    @Test
    public void setMultipleMegasEnabled_shouldWriteFlag() throws Exception {
        Path path = configPath();
        MegaLimitServiceImpl service = new MegaLimitServiceImpl(path);

        service.setMultipleMegasEnabled(true);

        assertTrue(service.isMultipleMegasEnabled());
        JsonObject root = JsonParser.parseString(
            Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("multipleMegas").getAsBoolean());
    }

    @Test
    public void setMultipleMegasEnabled_shouldPreserveUnknownKeys() throws Exception {
        Path path = configPath();
        Files.writeString(path, "{\n  \"multipleMegas\": false,\n  \"dynamax\": true,\n"
            + "  \"someCustomKey\": \"keep-me\"\n}", StandardCharsets.UTF_8);
        MegaLimitServiceImpl service = new MegaLimitServiceImpl(path);

        service.setMultipleMegasEnabled(true);

        JsonObject root = JsonParser.parseString(
            Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("multipleMegas").getAsBoolean());
        assertTrue(root.get("dynamax").getAsBoolean());
        assertEquals("keep-me", root.get("someCustomKey").getAsString());
    }

    @Test
    public void setMultipleMegasEnabled_shouldToggleBackToFalse() throws Exception {
        Path path = configPath();
        MegaLimitServiceImpl service = new MegaLimitServiceImpl(path);

        service.setMultipleMegasEnabled(true);
        service.setMultipleMegasEnabled(false);

        assertFalse(service.isMultipleMegasEnabled());
    }

    @Test
    public void setMultipleMegasEnabled_shouldNotThrowWithoutMegaShowdown() throws Exception {
        MegaLimitServiceImpl service = new MegaLimitServiceImpl(configPath());

        // 测试环境没有 mega_showdown，热重载应当返回 false 而不是抛异常
        assertFalse(service.setMultipleMegasEnabled(true));
    }
}

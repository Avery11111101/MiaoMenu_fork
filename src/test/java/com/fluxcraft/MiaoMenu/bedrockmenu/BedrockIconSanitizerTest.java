package com.fluxcraft.MiaoMenu.bedrockmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BedrockIconSanitizerTest {

    @Test
    void stripsPngFromVanillaTextures() {
        var result1 = BedrockIconSanitizer.sanitize("textures/items/diamond.png", "path");
        assertEquals("textures/items/diamond", result1.pathOrUrl());
        assertEquals("path", result1.iconType());

        var result2 = BedrockIconSanitizer.sanitize("textures/blocks/dirt.PNG", null);
        assertEquals("textures/blocks/dirt", result2.pathOrUrl());
        assertEquals("path", result2.iconType());
    }

    @Test
    void autoDetectsAndUpgradesUrls() {
        var result1 = BedrockIconSanitizer.sanitize("http://example.com/logo.png", "path");
        assertEquals("https://example.com/logo.png", result1.pathOrUrl());
        assertEquals("url", result1.iconType());

        var result2 = BedrockIconSanitizer.sanitize("https://example.com/logo.png", null);
        assertEquals("https://example.com/logo.png", result2.pathOrUrl());
        assertEquals("url", result2.iconType());
    }

    @Test
    void normalizesSlashes() {
        var result = BedrockIconSanitizer.sanitize("\\textures\\items\\diamond", "path");
        assertEquals("textures/items/diamond", result.pathOrUrl());
        assertEquals("path", result.iconType());

        var leadingSlash = BedrockIconSanitizer.sanitize("/textures/items/emerald", "path");
        assertEquals("textures/items/emerald", leadingSlash.pathOrUrl());
    }

    @Test
    void resolvesMaterialNamesToBedrockPath() {
        var result = BedrockIconSanitizer.sanitize("DIAMOND_SWORD", "path");
        assertEquals("textures/items/diamond_sword", result.pathOrUrl());
        assertEquals("path", result.iconType());

        var glassResult = BedrockIconSanitizer.sanitize("GRAY_STAINED_GLASS_PANE", null);
        assertEquals("textures/blocks/glass_gray", glassResult.pathOrUrl());
    }

    @Test
    void resolvesBase64Head() {
        String hash = "e00b95764024ba1766a41e97669ae0d8ad75c3efb756be5e7ab332c918ee9117";
        var result = BedrockIconSanitizer.sanitize("base64head:" + hash, "path");
        assertTrue(result.pathOrUrl().startsWith("https://textures.minecraft.net/texture/"));
        assertEquals("url", result.iconType());
    }

    @Test
    void handlesEmptyGracefully() {
        var result = BedrockIconSanitizer.sanitize("", null);
        assertTrue(result.isEmpty());
        assertEquals("path", result.iconType());
        assertFalse(BedrockIconSanitizer.sanitize("textures/items/paper", null).isEmpty());
    }
}

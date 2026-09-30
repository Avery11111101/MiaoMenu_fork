package com.fluxcraft.MiaoMenu.bedrockmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

public class BedrockTextureMapperTest {

    @Test
    void mapsModernVanillaItemsCorrectly() {
        assertEquals("textures/items/diamond", BedrockTextureMapper.getTexturePath("DIAMOND"));
        assertEquals("textures/items/mace", BedrockTextureMapper.getTexturePath("MACE"));
        assertEquals("textures/blocks/crafter_top", BedrockTextureMapper.getTexturePath("CRAFTER"));
        assertEquals("textures/items/breeze_rod", BedrockTextureMapper.getTexturePath("BREEZE_ROD"));
        assertEquals("textures/items/netherite_sword", BedrockTextureMapper.getTexturePath("NETHERITE_SWORD"));
        assertEquals("textures/items/netherite_ingot", BedrockTextureMapper.getTexturePath("NETHERITE_INGOT"));
        assertEquals("textures/items/apple_golden", BedrockTextureMapper.getTexturePath("GOLDEN_APPLE"));
        assertEquals("textures/items/clock_item", BedrockTextureMapper.getTexturePath("CLOCK"));
        assertEquals("textures/items/totem", BedrockTextureMapper.getTexturePath("TOTEM_OF_UNDYING"));
    }

    @Test
    void mapsStainedGlassPanes() {
        assertEquals("textures/blocks/glass_gray", BedrockTextureMapper.getTexturePath("GRAY_STAINED_GLASS_PANE"));
        assertEquals("textures/blocks/glass_black", BedrockTextureMapper.getTexturePath("BLACK_STAINED_GLASS_PANE"));
        assertEquals("textures/blocks/glass_red", BedrockTextureMapper.getTexturePath("RED_STAINED_GLASS_PANE"));
    }

    @Test
    void heuristicFallbacks() {
        assertEquals("textures/items/wooden_sword", BedrockTextureMapper.getTexturePath("WOODEN_SWORD"));
        assertEquals("textures/blocks/stone", BedrockTextureMapper.getTexturePath(""));
        assertEquals("textures/blocks/stone", BedrockTextureMapper.getTexturePath((Material) null));
    }

    @Test
    void recognizesMaterialNames() {
        assertTrue(BedrockTextureMapper.isRecognizedMaterial("DIAMOND"));
        assertTrue(BedrockTextureMapper.isRecognizedMaterial("MACE"));
        assertTrue(BedrockTextureMapper.isRecognizedMaterial("GRAY_STAINED_GLASS_PANE"));
        assertFalse(BedrockTextureMapper.isRecognizedMaterial("custom_texture_path/foo"));
    }
}

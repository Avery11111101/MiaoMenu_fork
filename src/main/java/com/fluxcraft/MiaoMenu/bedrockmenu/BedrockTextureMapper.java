package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 將 Java 版 Bukkit Material 映射至基岩版 (Bedrock / Geyser) 資源包材質路徑。
 * 涵蓋 1.16 ~ 1.21+（包含下界合金、重錘 Mace、合成器 Crafter、旋風棒 Breeze Rod 等最新物品），
 * 並提供對照快取與啟發式後備規則。
 */
public final class BedrockTextureMapper {

    private static final Map<String, String> MATERIAL_MAP = new HashMap<>(300);

    static {
        // --- 貴金屬、礦物與戰利品 ---
        put("DIAMOND", "textures/items/diamond");
        put("DIAMOND_BLOCK", "textures/blocks/diamond_block");
        put("DIAMOND_ORE", "textures/blocks/diamond_ore");
        put("DEEPSLATE_DIAMOND_ORE", "textures/blocks/deepslate_diamond_ore");
        put("EMERALD", "textures/items/emerald");
        put("EMERALD_BLOCK", "textures/blocks/emerald_block");
        put("EMERALD_ORE", "textures/blocks/emerald_ore");
        put("DEEPSLATE_EMERALD_ORE", "textures/blocks/deepslate_emerald_ore");
        put("GOLD_INGOT", "textures/items/gold_ingot");
        put("GOLD_NUGGET", "textures/items/gold_nugget");
        put("GOLD_BLOCK", "textures/blocks/gold_block");
        put("GOLD_ORE", "textures/blocks/gold_ore");
        put("DEEPSLATE_GOLD_ORE", "textures/blocks/deepslate_gold_ore");
        put("RAW_GOLD", "textures/items/raw_gold");
        put("RAW_GOLD_BLOCK", "textures/blocks/raw_gold_block");
        put("IRON_INGOT", "textures/items/iron_ingot");
        put("IRON_NUGGET", "textures/items/iron_nugget");
        put("IRON_BLOCK", "textures/blocks/iron_block");
        put("IRON_ORE", "textures/blocks/iron_ore");
        put("DEEPSLATE_IRON_ORE", "textures/blocks/deepslate_iron_ore");
        put("RAW_IRON", "textures/items/raw_iron");
        put("RAW_IRON_BLOCK", "textures/blocks/raw_iron_block");
        put("COPPER_INGOT", "textures/items/copper_ingot");
        put("COPPER_BLOCK", "textures/blocks/copper_block");
        put("RAW_COPPER", "textures/items/raw_copper");
        put("RAW_COPPER_BLOCK", "textures/blocks/raw_copper_block");
        put("COAL", "textures/items/coal");
        put("CHARCOAL", "textures/items/charcoal");
        put("COAL_BLOCK", "textures/blocks/coal_block");
        put("COAL_ORE", "textures/blocks/coal_ore");
        put("REDSTONE", "textures/items/redstone_dust");
        put("REDSTONE_BLOCK", "textures/blocks/redstone_block");
        put("LAPIS_LAZULI", "textures/items/dye_powder_blue");
        put("LAPIS_BLOCK", "textures/blocks/lapis_block");
        put("NETHERITE_INGOT", "textures/items/netherite_ingot");
        put("NETHERITE_SCRAP", "textures/items/netherite_scrap");
        put("NETHERITE_BLOCK", "textures/blocks/netherite_block");
        put("ANCIENT_DEBRIS", "textures/blocks/ancient_debris_side");
        put("AMETHYST_SHARD", "textures/items/amethyst_shard");
        put("AMETHYST_BLOCK", "textures/blocks/amethyst_block");
        put("NETHER_STAR", "textures/items/nether_star");

        // --- 1.21 新物品 (Tricky Trials) ---
        put("MACE", "textures/items/mace");
        put("BREEZE_ROD", "textures/items/breeze_rod");
        put("WIND_CHARGE", "textures/items/wind_charge");
        put("CRAFTER", "textures/blocks/crafter_top");
        put("HEAVY_CORE", "textures/blocks/heavy_core");
        put("TRIAL_KEY", "textures/items/trial_key");
        put("OMINOUS_TRIAL_KEY", "textures/items/ominous_trial_key");
        put("OMINOUS_BOTTLE", "textures/items/ominous_bottle");

        // --- 裝備、武器與工具 (Netherite, Diamond, Iron, Gold, Stone, Wooden) ---
        put("NETHERITE_SWORD", "textures/items/netherite_sword");
        put("NETHERITE_PICKAXE", "textures/items/netherite_pickaxe");
        put("NETHERITE_AXE", "textures/items/netherite_axe");
        put("NETHERITE_SHOVEL", "textures/items/netherite_shovel");
        put("NETHERITE_HOE", "textures/items/netherite_hoe");
        put("NETHERITE_HELMET", "textures/items/netherite_helmet");
        put("NETHERITE_CHESTPLATE", "textures/items/netherite_chestplate");
        put("NETHERITE_LEGGINGS", "textures/items/netherite_leggings");
        put("NETHERITE_BOOTS", "textures/items/netherite_boots");

        put("DIAMOND_SWORD", "textures/items/diamond_sword");
        put("DIAMOND_PICKAXE", "textures/items/diamond_pickaxe");
        put("DIAMOND_AXE", "textures/items/diamond_axe");
        put("DIAMOND_SHOVEL", "textures/items/diamond_shovel");
        put("DIAMOND_HOE", "textures/items/diamond_hoe");
        put("DIAMOND_HELMET", "textures/items/diamond_helmet");
        put("DIAMOND_CHESTPLATE", "textures/items/diamond_chestplate");
        put("DIAMOND_LEGGINGS", "textures/items/diamond_leggings");
        put("DIAMOND_BOOTS", "textures/items/diamond_boots");

        put("IRON_SWORD", "textures/items/iron_sword");
        put("IRON_PICKAXE", "textures/items/iron_pickaxe");
        put("IRON_AXE", "textures/items/iron_axe");
        put("IRON_SHOVEL", "textures/items/iron_shovel");
        put("IRON_HOE", "textures/items/iron_hoe");
        put("IRON_HELMET", "textures/items/iron_helmet");
        put("IRON_CHESTPLATE", "textures/items/iron_chestplate");
        put("IRON_LEGGINGS", "textures/items/iron_leggings");
        put("IRON_BOOTS", "textures/items/iron_boots");

        put("GOLDEN_SWORD", "textures/items/gold_sword");
        put("GOLDEN_PICKAXE", "textures/items/gold_pickaxe");
        put("GOLDEN_AXE", "textures/items/gold_axe");
        put("GOLDEN_SHOVEL", "textures/items/gold_shovel");
        put("GOLDEN_HOE", "textures/items/gold_hoe");
        put("GOLDEN_HELMET", "textures/items/gold_helmet");
        put("GOLDEN_CHESTPLATE", "textures/items/gold_chestplate");
        put("GOLDEN_LEGGINGS", "textures/items/gold_leggings");
        put("GOLDEN_BOOTS", "textures/items/gold_boots");

        put("BOW", "textures/items/bow_standby");
        put("CROSSBOW", "textures/items/crossbow_standby");
        put("TRIDENT", "textures/items/trident");
        put("SHIELD", "textures/items/shield");
        put("ELYTRA", "textures/items/elytra");
        put("ARROW", "textures/items/arrow");
        put("SPECTRAL_ARROW", "textures/items/spectral_arrow");

        // --- 常用選單道具與公用工具 ---
        put("CLOCK", "textures/items/clock_item");
        put("COMPASS", "textures/items/compass_item");
        put("RECOVERY_COMPASS", "textures/items/recovery_compass_item");
        put("SPYGLASS", "textures/items/spyglass");
        put("TOTEM_OF_UNDYING", "textures/items/totem");
        put("EXPERIENCE_BOTTLE", "textures/items/experience_bottle");
        put("ENDER_PEARL", "textures/items/ender_pearl");
        put("ENDER_EYE", "textures/items/ender_eye");
        put("FIREWORK_ROCKET", "textures/items/fireworks");
        put("NAME_TAG", "textures/items/name_tag");
        put("LEAD", "textures/items/lead");
        put("SADDLE", "textures/items/saddle");
        put("PAPER", "textures/items/paper");
        put("BOOK", "textures/items/book_normal");
        put("ENCHANTED_BOOK", "textures/items/book_enchanted");
        put("WRITABLE_BOOK", "textures/items/book_writable");
        put("WRITTEN_BOOK", "textures/items/book_written");
        put("MAP", "textures/items/map_filled");
        put("EMPTY_MAP", "textures/items/map_empty");
        put("FILLED_MAP", "textures/items/map_filled");
        put("BUCKET", "textures/items/bucket_empty");
        put("WATER_BUCKET", "textures/items/bucket_water");
        put("LAVA_BUCKET", "textures/items/bucket_lava");
        put("MILK_BUCKET", "textures/items/bucket_milk");
        put("SHEARS", "textures/items/shears");
        put("FLINT_AND_STEEL", "textures/items/flint_and_steel");
        put("FISHING_ROD", "textures/items/fishing_rod_uncast");
        put("BRUSH", "textures/items/brush");

        // --- 食物與藥水 ---
        put("APPLE", "textures/items/apple");
        put("GOLDEN_APPLE", "textures/items/apple_golden");
        put("ENCHANTED_GOLDEN_APPLE", "textures/items/apple_golden");
        put("BREAD", "textures/items/bread");
        put("COOKED_BEEF", "textures/items/beef_cooked");
        put("COOKED_PORKCHOP", "textures/items/porkchop_cooked");
        put("COOKED_MUTTON", "textures/items/mutton_cooked");
        put("COOKED_CHICKEN", "textures/items/chicken_cooked");
        put("GOLDEN_CARROT", "textures/items/carrot_golden");
        put("POTION", "textures/items/potion_bottle_drinkable");
        put("SPLASH_POTION", "textures/items/potion_bottle_splash");
        put("LINGERING_POTION", "textures/items/potion_bottle_lingering");
        put("GLASS_BOTTLE", "textures/items/potion_bottle_empty");

        // --- 頭部與骷髏 ---
        put("PLAYER_HEAD", "textures/items/skull_player");
        put("PLAYER_WALL_HEAD", "textures/items/skull_player");
        put("SKELETON_SKULL", "textures/items/skull_skeleton");
        put("WITHER_SKELETON_SKULL", "textures/items/skull_wither");
        put("ZOMBIE_HEAD", "textures/items/skull_zombie");
        put("CREEPER_HEAD", "textures/items/skull_creeper");
        put("DRAGON_HEAD", "textures/items/skull_dragon");
        put("PIGLIN_HEAD", "textures/items/skull_piglin");

        // --- 常用選單裝飾方塊：彩色玻璃板 (Stained Glass Panes) ---
        putStainedGlassPanes();

        // --- 常用功能方塊與容器 ---
        put("BARRIER", "textures/blocks/barrier");
        put("STRUCTURE_VOID", "textures/blocks/structure_void");
        put("BEACON", "textures/blocks/beacon");
        put("CHEST", "textures/blocks/chest_front");
        put("ENDER_CHEST", "textures/blocks/ender_chest_front");
        put("TRAPPED_CHEST", "textures/blocks/chest_front");
        put("BARREL", "textures/blocks/barrel_top");
        put("CRAFTING_TABLE", "textures/blocks/crafting_table_front");
        put("FURNACE", "textures/blocks/furnace_front_off");
        put("BLAST_FURNACE", "textures/blocks/blast_furnace_front_off");
        put("SMOKER", "textures/blocks/smoker_front_off");
        put("ANVIL", "textures/blocks/anvil_base");
        put("ENCHANTING_TABLE", "textures/blocks/enchanting_table_top");
        put("BREWING_STAND", "textures/items/brewing_stand");
        put("BELL", "textures/blocks/bell_top");
        put("CAMPFIRE", "textures/items/campfire");
        put("RESPAWN_ANCHOR", "textures/blocks/respawn_anchor_top_off");
        put("LODESTONE", "textures/blocks/lodestone_top");

        // --- 原版自然與建材 ---
        put("GRASS_BLOCK", "textures/blocks/grass_side_carried");
        put("DIRT", "textures/blocks/dirt");
        put("COBBLESTONE", "textures/blocks/cobblestone");
        put("STONE", "textures/blocks/stone");
        put("SMOOTH_STONE", "textures/blocks/stone_slab_top");
        put("BEDROCK", "textures/blocks/bedrock");
        put("OBSIDIAN", "textures/blocks/obsidian");
        put("CRYING_OBSIDIAN", "textures/blocks/crying_obsidian");
        put("DEEPSLATE", "textures/blocks/deepslate");
        put("COBBLED_DEEPSLATE", "textures/blocks/cobbled_deepslate");
        put("OAK_PLANKS", "textures/blocks/planks_oak");
        put("OAK_LOG", "textures/blocks/log_oak");
        put("OAK_SAPLING", "textures/blocks/sapling_oak");
        put("CHERRY_PLANKS", "textures/blocks/cherry_planks");
        put("CHERRY_LOG", "textures/blocks/cherry_log_side");
        put("BAMBOO_PLANKS", "textures/blocks/bamboo_planks");
        put("SNIFFER_EGG", "textures/blocks/sniffer_egg_top");
        put("TORCH", "textures/blocks/torch_on");
        put("SOUL_TORCH", "textures/blocks/soul_torch");
        put("REDSTONE_TORCH", "textures/blocks/redstone_torch_on");
        put("LANTERN", "textures/items/lantern");
        put("SOUL_LANTERN", "textures/items/soul_lantern");
        put("SEA_LANTERN", "textures/blocks/sea_lantern");
        put("GLOWSTONE", "textures/blocks/glowstone");
        put("FROGLIGHT", "textures/blocks/ochre_froglight_side");
        put("OCHRE_FROGLIGHT", "textures/blocks/ochre_froglight_side");
        put("VERDANT_FROGLIGHT", "textures/blocks/verdant_froglight_side");
        put("PEARLESCENT_FROGLIGHT", "textures/blocks/pearlescent_froglight_side");
    }

    private static void put(String key, String path) {
        MATERIAL_MAP.put(key.toUpperCase(Locale.ROOT), path);
    }

    private static void putStainedGlassPanes() {
        String[] colors = {
                "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
                "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
        };
        for (String c : colors) {
            String cLower = c.toLowerCase(Locale.ROOT);
            put(c + "_STAINED_GLASS_PANE", "textures/blocks/glass_" + cLower);
            put(c + "_STAINED_GLASS", "textures/blocks/glass_" + cLower);
            put(c + "_WOOL", "textures/blocks/wool_colored_" + cLower);
            put(c + "_CONCRETE", "textures/blocks/concrete_" + cLower);
            put(c + "_CONCRETE_POWDER", "textures/blocks/concrete_powder_" + cLower);
            put(c + "_TERRACOTTA", "textures/blocks/hardened_clay_stained_" + cLower);
            put(c + "_GLAZED_TERRACOTTA", "textures/blocks/glazed_terracotta_" + cLower);
            put(c + "_CARPET", "textures/blocks/wool_colored_" + cLower);
            put(c + "_BED", "textures/items/bed_" + cLower);
            put(c + "_SHULKER_BOX", "textures/blocks/shulker_top_" + cLower);
            put(c + "_DYE", "textures/items/dye_powder_" + cLower);
        }
        put("GLASS_PANE", "textures/blocks/glass");
        put("GLASS", "textures/blocks/glass");
    }

    private BedrockTextureMapper() {}

    /**
     * 取得特定 Material 的基岩版資源路徑。
     *
     * @param material Bukkit Material
     * @return 資源路徑（不帶 .png），若無精確對照則啟發式推導，皆失敗時回傳預設 stone
     */
    @NotNull
    public static String getTexturePath(@Nullable Material material) {
        if (material == null || material.isAir()) {
            return "textures/blocks/stone";
        }
        return getTexturePath(material.name());
    }

    /**
     * 依材質名稱字串取得基岩版資源路徑。
     *
     * @param materialName 材質名稱（不分大小寫）
     * @return 資源路徑（不帶 .png）
     */
    @NotNull
    public static String getTexturePath(@Nullable String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return "textures/blocks/stone";
        }
        String upper = materialName.trim().toUpperCase(Locale.ROOT);

        // 1. 精確對照
        String exact = MATERIAL_MAP.get(upper);
        if (exact != null) {
            return exact;
        }

        // 2. 啟發式規則推導
        if (upper.endsWith("_SWORD") || upper.endsWith("_PICKAXE") || upper.endsWith("_AXE")
                || upper.endsWith("_SHOVEL") || upper.endsWith("_HOE") || upper.endsWith("_HELMET")
                || upper.endsWith("_CHESTPLATE") || upper.endsWith("_LEGGINGS") || upper.endsWith("_BOOTS")) {
            return "textures/items/" + upper.toLowerCase(Locale.ROOT);
        }

        if (upper.endsWith("_ORE")) {
            return "textures/blocks/" + upper.toLowerCase(Locale.ROOT);
        }

        if (upper.endsWith("_BLOCK")) {
            return "textures/blocks/" + upper.toLowerCase(Locale.ROOT);
        }

        if (upper.endsWith("_INGOT") || upper.endsWith("_NUGGET")) {
            return "textures/items/" + upper.toLowerCase(Locale.ROOT);
        }

        if (upper.endsWith("_LOG") || upper.endsWith("_PLANKS") || upper.endsWith("_WOOD")) {
            return "textures/blocks/" + upper.toLowerCase(Locale.ROOT);
        }

        // 3. 原生 match 檢查
        Material mat = Material.matchMaterial(upper);
        if (mat != null) {
            if (mat.isBlock()) {
                return "textures/blocks/" + upper.toLowerCase(Locale.ROOT);
            } else {
                return "textures/items/" + upper.toLowerCase(Locale.ROOT);
            }
        }

        return "textures/blocks/stone";
    }

    /**
     * 判斷某字串是否為此 Mapper 可辨識的 Java 材質名稱。
     */
    public static boolean isRecognizedMaterial(@Nullable String name) {
        if (name == null || name.isBlank()) return false;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        return MATERIAL_MAP.containsKey(upper) || Material.matchMaterial(upper) != null;
    }
}

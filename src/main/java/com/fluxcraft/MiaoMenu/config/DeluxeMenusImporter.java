package com.fluxcraft.MiaoMenu.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.fluxcraft.MiaoMenu.bedrockmenu.BedrockIconSanitizer;
import com.fluxcraft.MiaoMenu.bedrockmenu.BedrockTextureMapper;

/**
 * 把 DeluxeMenus 的 YAML 結構轉換為 MiaoMenu_fork 的 java_menus 格式。
 *
 * <h3>結構對應</h3>
 * <pre>
 * DeluxeMenus                              MiaoMenu_fork
 * --------------------------------         -----------------------------
 * config.yml.gui_menus.&lt;id&gt;.file → 來源檔   java_menus/&lt;id&gt;.yml
 * menu_title                       →        menu_title                   （原樣）
 * size: 54                         →        rows: 6                      （除 9，1–6 區間）
 * open_command / register_command  →        略過                          （MiaoMenu 走 /dgm open &lt;id&gt;）
 * items.&lt;id&gt;.slot                  →        items.&lt;id&gt;.slot              （原樣）
 * items.&lt;id&gt;.material              →        items.&lt;id&gt;.material          （原樣）
 * items.&lt;id&gt;.display_name          →        items.&lt;id&gt;.display_name      （原樣）
 * items.&lt;id&gt;.lore                  →        items.&lt;id&gt;.lore              （原樣）
 * items.&lt;id&gt;.custom_model_data     →        items.&lt;id&gt;.custom_model_data （原樣）
 * items.&lt;id&gt;.left_click_commands   →        items.&lt;id&gt;.left_click_commands （動作 prefix 轉換）
 * items.&lt;id&gt;.right_click_commands  →        items.&lt;id&gt;.right_click_commands（動作 prefix 轉換）
 * </pre>
 *
 * <h3>動作 prefix 對應</h3>
 * <pre>
 * [player] ...     → [player] ...      （MiaoMenu 也有）
 * [message] ...    → [message] ...     （兩邊相同）
 * [close]          → [close]           （兩邊相同）
 * [console] ...    → [cmd] ...         （MiaoMenu 用 cmd 走 console）
 * [openguimenu] X  → [player] dgm open X （走 MiaoMenu 主指令，最穩定的等價路徑）
 * 其他             → 原樣保留           （由 MiaoMenu 的 DefaultAction 處理，未知 prefix 預設視同 player）
 * </pre>
 *
 * <h3>不轉換 / 略過的欄位</h3>
 * <ul>
 *   <li>{@code update: true} — MiaoMenu 不需此標記（PlaceholderAPI 解析時機由 MiaoMenu 自己決定）</li>
 *   <li>{@code skin: <player_name>} — MiaoMenu 目前不支援 PLAYER_HEAD 的 skin 動態欄位，保留 material 但丟棄 skin</li>
 *   <li>{@code view_requirement}、{@code conditions} — DeluxeMenus 與 MiaoMenu 的 requirement 系統格式接近但細節差異，保留欄位內容讓使用者手動審閱</li>
 * </ul>
 *
 * 所有公開方法皆為純函式，無副作用（除 {@link #applyConversion} 會寫檔案）。
 */
public final class DeluxeMenusImporter {
    public static final String CONFIG_FILE = "config.yml";
    public static final String MENUS_DIR = "gui_menus";
    public static final String JAVA_MENUS_DIR = "java_menus";
    public static final String BEDROCK_MENUS_DIR = "bedrock_menus";

    /**
     * 一個 DeluxeMenus 來源 menu 在 MiaoMenu_fork 端的輸出路徑：
     * 同時記錄 Java 箱子 GUI 與 Bedrock Floodgate SimpleForm 兩份輸出，
     * 確保兩個檔名同根（{@code <safeId>.yml}），rollback 與差異比對都能對齊。
     */
    public record MenuEntry(String id, Path sourceFile, String outputRelative, String bedrockOutputRelative) {
        // 向後相容 3 參數寫法：自動由 java_menus/<safeId>.yml 推回 bedrock_menus/<safeId>.yml
        public MenuEntry(String id, Path sourceFile, String outputRelative) {
            this(id, sourceFile, outputRelative, deriveBedrockOutput(outputRelative));
        }

        private static String deriveBedrockOutput(String javaRelative) {
            if (javaRelative == null) {
                return BEDROCK_MENUS_DIR + "/menu.yml";
            }
            String stripped = javaRelative;
            if (stripped.startsWith(JAVA_MENUS_DIR + "/")) {
                stripped = stripped.substring((JAVA_MENUS_DIR + "/").length());
            }
            return BEDROCK_MENUS_DIR + "/" + stripped;
        }
    }

    /**
     * Material → Bedrock 內建貼圖路徑對照（vanilla 子集，刻意保守）。
     * 未命中者輸出空字串 icon → BedrockMenu 在 {@code hasIcon()} 回 false 時會 fallback 為純文字按鈕，
     * 不會因找不到貼圖而崩潰；玩家側只是少了圖示。
     */
    private static final Map<String, String> MATERIAL_TO_BEDROCK_ICON = Map.ofEntries(
            Map.entry("DIAMOND", "textures/items/diamond"),
            Map.entry("DIAMOND_SWORD", "textures/items/diamond_sword"),
            Map.entry("DIAMOND_PICKAXE", "textures/items/diamond_pickaxe"),
            Map.entry("DIAMOND_AXE", "textures/items/diamond_axe"),
            Map.entry("IRON_INGOT", "textures/items/iron_ingot"),
            Map.entry("IRON_SWORD", "textures/items/iron_sword"),
            Map.entry("GOLD_INGOT", "textures/items/gold_ingot"),
            Map.entry("EMERALD", "textures/items/emerald"),
            Map.entry("REDSTONE", "textures/items/redstone_dust"),
            Map.entry("PAPER", "textures/items/paper"),
            Map.entry("BOOK", "textures/items/book_normal"),
            Map.entry("ENCHANTED_BOOK", "textures/items/book_enchanted"),
            Map.entry("WRITABLE_BOOK", "textures/items/book_writable"),
            Map.entry("WRITTEN_BOOK", "textures/items/book_written"),
            Map.entry("COMPASS", "textures/items/compass_item"),
            Map.entry("CLOCK", "textures/items/clock_item"),
            Map.entry("MAP", "textures/items/map_filled"),
            Map.entry("NAME_TAG", "textures/items/name_tag"),
            Map.entry("ARROW", "textures/items/arrow"),
            Map.entry("ENDER_PEARL", "textures/items/ender_pearl"),
            Map.entry("EXPERIENCE_BOTTLE", "textures/items/experience_bottle"),
            Map.entry("FIREWORK_ROCKET", "textures/items/fireworks"),
            Map.entry("NETHER_STAR", "textures/items/nether_star"),
            Map.entry("BARRIER", "textures/blocks/barrier"),
            Map.entry("BEACON", "textures/blocks/beacon"),
            Map.entry("CHEST", "textures/blocks/chest_front"),
            Map.entry("ENDER_CHEST", "textures/blocks/ender_chest_front"),
            Map.entry("CRAFTING_TABLE", "textures/blocks/crafting_table_front"),
            Map.entry("FURNACE", "textures/blocks/furnace_front_off"),
            Map.entry("GRASS_BLOCK", "textures/blocks/grass_side_carried"),
            Map.entry("DIRT", "textures/blocks/dirt"),
            Map.entry("COBBLESTONE", "textures/blocks/cobblestone"),
            Map.entry("STONE", "textures/blocks/stone"),
            Map.entry("OAK_PLANKS", "textures/blocks/planks_oak"),
            Map.entry("OAK_LOG", "textures/blocks/log_oak"),
            Map.entry("OAK_SAPLING", "textures/blocks/sapling_oak"),
            Map.entry("PLAYER_HEAD", "textures/items/skull_player"),
            Map.entry("SKELETON_SKULL", "textures/items/skull_skeleton"),
            Map.entry("ZOMBIE_HEAD", "textures/items/skull_zombie"),
            Map.entry("CREEPER_HEAD", "textures/items/skull_creeper"),
            Map.entry("DRAGON_HEAD", "textures/items/skull_dragon")
    );

    private DeluxeMenusImporter() {
    }

    /**
     * 判斷某資料夾是否為 DeluxeMenus 結構：config.yml 內含 {@code gui_menus:} 鍵 + 存在 gui_menus/ 子資料夾。
     */
    public static boolean detect(Path sourceDir) {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            return false;
        }
        Path config = sourceDir.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(config)) {
            return false;
        }
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(config.toFile());
            return y.isConfigurationSection("gui_menus");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 列出 DeluxeMenus config.yml 內定義的所有選單；
     * 每個 entry 標記原始檔案路徑與轉換後在 java_menus/ 下的目標檔名（用 menu id）。
     */
    public static List<MenuEntry> listMenus(Path sourceDir) {
        List<MenuEntry> entries = new ArrayList<>();
        Path config = sourceDir.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(config)) {
            return entries;
        }
        YamlConfiguration y;
        try {
            y = YamlConfiguration.loadConfiguration(config.toFile());
        } catch (RuntimeException e) {
            return entries;
        }
        ConfigurationSection guiMenus = y.getConfigurationSection("gui_menus");
        if (guiMenus == null) {
            return entries;
        }
        for (String id : guiMenus.getKeys(false)) {
            String relativeFile = guiMenus.getString(id + ".file");
            if (relativeFile == null || relativeFile.isBlank()) {
                continue;
            }
            Path menuFile = sourceDir.resolve(MENUS_DIR).resolve(relativeFile);
            if (!Files.isRegularFile(menuFile)) {
                // 容錯：DeluxeMenus 設定指向不存在的檔案 → 略過
                continue;
            }
            String safeId = sanitizeFileSegment(id);
            String outputRel = "java_menus/" + safeId + ".yml";
            entries.add(new MenuEntry(id, menuFile, outputRel));
        }
        return entries;
    }

    /**
     * 把單一 DeluxeMenus menu yaml 轉成 MiaoMenu_fork 的 YAML 字串。
     */
    public static String convertMenu(Path sourceFile) throws IOException {
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("menu file not found: " + sourceFile);
        }
        YamlConfiguration src;
        try {
            src = YamlConfiguration.loadConfiguration(sourceFile.toFile());
        } catch (RuntimeException e) {
            throw new IOException("failed to parse DeluxeMenus YAML: " + sourceFile.getFileName(), e);
        }
        YamlConfiguration dst = new YamlConfiguration();

        // menu_title 原樣搬過去；缺值給一個合理 fallback
        dst.set("menu_title", src.getString("menu_title", sourceFile.getFileName().toString()));

        // size 是格數（9, 18, ...）→ MiaoMenu 用 rows（1–6）
        int size = src.getInt("size", src.getInt("rows", 54) * 9);
        int rows = Math.max(1, Math.min(6, size / 9));
        dst.set("rows", rows);

        // open_command / register_command 不轉（MiaoMenu 走主指令 /dgm open <menuId>）
        // 為了讓使用者能溯源，把原始 open_command 寫到一個保留欄位 _imported_open_command。
        // DeluxeMenus 自 1.13.x 起允許 open_command 為 string 或 list；
        // 用 getString 會把 list 變成 "[a, b]" 這種 toString 亂碼，所以分支處理。
        if (src.contains("open_command")) {
            Object raw = src.get("open_command");
            if (raw instanceof java.util.List<?>) {
                java.util.List<String> aliases = src.getStringList("open_command");
                if (!aliases.isEmpty()) {
                    dst.set("_imported_open_command", aliases);
                }
            } else if (raw != null) {
                dst.set("_imported_open_command", raw.toString());
            }
        }

        ConfigurationSection items = src.getConfigurationSection("items");
        if (items != null) {
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection srcItem = items.getConfigurationSection(itemId);
                if (srcItem == null) {
                    continue;
                }
                String basePath = "items." + itemId;
                copyIfPresent(srcItem, dst, basePath, "slot");
                copyIfPresent(srcItem, dst, basePath, "material");
                copyIfPresent(srcItem, dst, basePath, "custom_model_data");
                copyIfPresent(srcItem, dst, basePath, "display_name");
                if (srcItem.contains("lore")) {
                    dst.set(basePath + ".lore", srcItem.getStringList("lore"));
                }
                copyClickList(srcItem, dst, basePath, "left_click_commands");
                copyClickList(srcItem, dst, basePath, "right_click_commands");
                copyClickList(srcItem, dst, basePath, "middle_click_commands");
                copyClickList(srcItem, dst, basePath, "shift_left_click_commands");
                copyClickList(srcItem, dst, basePath, "shift_right_click_commands");
                copyClickList(srcItem, dst, basePath, "click_commands");
                // 保留 conditions/view_requirement/lock_message 等欄位讓使用者手動審閱
                copyIfPresent(srcItem, dst, basePath, "lock_message");
                copyIfPresent(srcItem, dst, basePath, "view_requirement");
                copyIfPresent(srcItem, dst, basePath, "conditions");
                copyIfPresent(srcItem, dst, basePath, "item_conditions");
            }
        }

        String header = "# 由 MiaoMenu_fork DeluxeMenusImporter 從 " + sourceFile.getFileName()
                + " 自動轉換產生。\n"
                + "# 動作前綴對應：[console]/[commandevent]→[cmd]、[openguimenu] X→[player] dgm open X、\n"
                + "# [chat]/[broadcast]/[json]/[minimessage]/[actionbar]/[title]/[subtitle]→[message]、\n"
                + "# [refresh]→[close]、[connect] X→[player] server X、[sound] X→[cmd] playsound X %player_name%。\n"
                + "# 其餘 prefix（[player]/[message]/[close]/未知）原樣保留。\n"
                + "# 如需手動微調 PlaceholderAPI、條件或物品 skin，請直接編輯本檔。\n\n";
        return header + dst.saveToString();
    }

    /**
     * 把同一份 DeluxeMenus menu yaml 轉成 MiaoMenu_fork 的「基岩端表單」YAML 字串
     * （Floodgate SimpleForm 結構，落到 {@code bedrock_menus/<id>.yml}）。
     *
     * <h3>取捨</h3>
     * <ul>
     *   <li>箱子有 slot 概念，表單沒有 → items 依 slot 升冪排成單一按鈕列表。</li>
     *   <li>SimpleForm 一顆按鈕只能執行一個動作 → 從 {@code left_click_commands}
     *       （若空 fallback {@code click_commands}）取「第一個」有效動作，其餘多動作
     *       與 right/middle/shift_* 全部捨棄並在 YAML 頂部註解保留原文供手動審閱。</li>
     *   <li>material → icon 走 {@link #MATERIAL_TO_BEDROCK_ICON} vanilla 對照表，
     *       未命中時 icon 留空 → 基岩端 fallback 為純文字按鈕，不會炸。</li>
     *   <li>{@code conditions} / {@code lock_message} / {@code view_requirement}
     *       語意兩邊一致，原樣搬。</li>
     * </ul>
     */
    public static String convertMenuToBedrock(Path sourceFile) throws IOException {
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("menu file not found: " + sourceFile);
        }
        YamlConfiguration src;
        try {
            src = YamlConfiguration.loadConfiguration(sourceFile.toFile());
        } catch (RuntimeException e) {
            throw new IOException("failed to parse DeluxeMenus YAML: " + sourceFile.getFileName(), e);
        }
        YamlConfiguration dst = new YamlConfiguration();
        dst.set("menu.title", src.getString("menu_title", sourceFile.getFileName().toString()));

        List<Map<String, Object>> bedrockItems = new ArrayList<>();
        List<String> droppedNotes = new ArrayList<>();
        ConfigurationSection items = src.getConfigurationSection("items");
        if (items != null) {
            List<ConfigurationSection> sorted = new ArrayList<>();
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection srcItem = items.getConfigurationSection(itemId);
                if (srcItem != null) {
                    sorted.add(srcItem);
                }
            }
            sorted.sort(Comparator.comparingInt(s -> s.getInt("slot", Integer.MAX_VALUE)));

            for (ConfigurationSection srcItem : sorted) {
                Map<String, Object> bItem = new LinkedHashMap<>();

                String displayName = srcItem.getString("display_name", "");
                List<String> lore = srcItem.getStringList("lore");
                String text = lore.isEmpty() ? displayName
                        : displayName + "\n" + String.join("\n", lore);
                bItem.put("text", text);

                String material = srcItem.getString("material", "");
                String rawIcon;
                String rawIconType = "path";
                if (material != null && material.toLowerCase(Locale.ROOT).startsWith("base64head:")) {
                    rawIcon = material;
                    rawIconType = "url";
                } else {
                    rawIcon = BedrockTextureMapper.getTexturePath(material);
                }
                BedrockIconSanitizer.SanitizedIcon sanitized = BedrockIconSanitizer.sanitize(rawIcon, rawIconType);
                bItem.put("icon", sanitized.pathOrUrl());
                bItem.put("icon_type", sanitized.iconType());

                List<String> leftClicks = srcItem.getStringList("left_click_commands");
                List<String> clickFallback = srcItem.getStringList("click_commands");
                BedrockAction action = extractFirstBedrockAction(leftClicks, clickFallback);
                bItem.put("command", action.command());
                bItem.put("execute_as", action.executeAs());

                int leftSize = leftClicks == null ? 0 : leftClicks.size();
                int fallbackSize = clickFallback == null ? 0 : clickFallback.size();
                if (leftSize + fallbackSize > 1) {
                    droppedNotes.add("slot " + srcItem.getInt("slot", -1)
                            + " (" + srcItem.getName() + "): 共 " + (leftSize + fallbackSize)
                            + " 個動作，僅保留第一個用於基岩表單");
                }

                // 若擷取結果為空（left/click 都沒料），但來源確實有 right/middle/shift_* 動作 →
                // 標記為 Bedrock 不支援的按鈕，BedrockMenuManager 點下去會回提示而非 silent 無反應。
                boolean hasOtherClicks = !srcItem.getStringList("right_click_commands").isEmpty()
                        || !srcItem.getStringList("middle_click_commands").isEmpty()
                        || !srcItem.getStringList("shift_left_click_commands").isEmpty()
                        || !srcItem.getStringList("shift_right_click_commands").isEmpty();
                if (action.command().isEmpty() && hasOtherClicks) {
                    bItem.put("unsupported_on_bedrock", true);
                }

                if (srcItem.contains("lock_message")) {
                    bItem.put("lock_message", srcItem.getString("lock_message"));
                }
                if (srcItem.contains("conditions")) {
                    bItem.put("conditions", srcItem.get("conditions"));
                }
                if (srcItem.contains("item_conditions")) {
                    bItem.put("item_conditions", srcItem.get("item_conditions"));
                }
                bedrockItems.add(bItem);
            }
        }
        dst.set("menu.items", bedrockItems);

        if (src.contains("view_requirement")) {
            dst.set("view_requirement", src.get("view_requirement"));
        }

        StringBuilder header = new StringBuilder();
        header.append("# 由 MiaoMenu_fork DeluxeMenusImporter 從 ")
                .append(sourceFile.getFileName())
                .append(" 自動轉換為基岩端表單。\n");
        header.append("# 動作策略：每顆按鈕只保留 left_click_commands 第一個動作（fallback click_commands）；\n");
        header.append("# Bedrock SimpleForm 一顆按鈕只能跑一個動作，right/middle/shift_* 全部捨棄。\n");
        header.append("# material → icon 用內建 vanilla 對照表，未命中者 icon 留空 → 顯示為純文字按鈕。\n");
        if (!droppedNotes.isEmpty()) {
            header.append("# 以下 item 有多動作被簡化，請手動審閱對應的 java_menus/ 版本以掌握完整邏輯：\n");
            for (String note : droppedNotes) {
                header.append("#   - ").append(note).append("\n");
            }
        }
        header.append("\n");
        return header.toString() + dst.saveToString();
    }

    /**
     * 從 DeluxeMenus 動作 list 中擷取「第一個」有效動作並轉成 Bedrock 端的 {@code command} + {@code execute_as}。
     * 先掃 primary（通常是 left_click_commands），空才 fallback 到 fallback（通常是 click_commands）。
     * 都沒有則回空動作（command=""、execute_as="player"）。
     */
    static BedrockAction extractFirstBedrockAction(List<String> primary, List<String> fallback) {
        BedrockAction a = scanFirst(primary);
        if (a != null) {
            return a;
        }
        a = scanFirst(fallback);
        if (a != null) {
            return a;
        }
        return new BedrockAction("", "player");
    }

    private static BedrockAction scanFirst(List<String> actions) {
        if (actions == null) {
            return null;
        }
        for (String raw : actions) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int bracketStart = trimmed.indexOf('[');
            int bracketEnd = trimmed.indexOf(']');
            if (bracketStart != 0 || bracketEnd <= 0) {
                return new BedrockAction(trimmed, "player");
            }
            String prefix = trimmed.substring(1, bracketEnd).trim().toLowerCase(Locale.ROOT);
            String body = trimmed.substring(bracketEnd + 1).trim();
            return switch (prefix) {
                case "player" -> new BedrockAction(body, "player");
                case "console", "cmd" -> new BedrockAction(body, "cmd");
                case "message" -> new BedrockAction(body, "message");
                // BedrockMenuManager 對空 command 會 return 不 dispatch，故給 "close" placeholder 讓 CloseAction 觸發
                case "close" -> new BedrockAction("close", "close");
                case "openguimenu" -> new BedrockAction("dgm open " + body, "player");
                default -> new BedrockAction(body, "player");
            };
        }
        return null;
    }

    /** 表單按鈕綁定的單一動作（command + execute_as）。 */
    public record BedrockAction(String command, String executeAs) {
    }

    /**
     * 把轉換後內容寫入目標檔，回傳實際寫入的位元組數。
     */
    public static long applyConversion(Path targetFile, String yamlContent) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        byte[] bytes = yamlContent.getBytes(StandardCharsets.UTF_8);
        Files.write(targetFile, bytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return bytes.length;
    }

    /* ---------------- 內部工具 ---------------- */

    private static void copyIfPresent(ConfigurationSection src, YamlConfiguration dst, String basePath, String key) {
        if (src.contains(key)) {
            dst.set(basePath + "." + key, src.get(key));
        }
    }

    /**
     * 把 click 動作 list（含 shift/middle/通用變體）原樣經 {@link #convertActions} 走 prefix 轉換後寫回。
     * MiaoMenu_fork 已在 v1.3 補齊 middle / shift_left / shift_right / click_commands 的讀取與 dispatch，
     * 所以匯入端維持與來源相同的鍵名即可，無需做欄位重命名。
     */
    private static void copyClickList(ConfigurationSection src, YamlConfiguration dst, String basePath, String key) {
        if (src.contains(key)) {
            dst.set(basePath + "." + key, convertActions(src.getStringList(key)));
        }
    }

    /**
     * DeluxeMenus → MiaoMenu_fork 動作 prefix 轉換。
     * 不認識的 prefix 原樣保留，由 MiaoMenu 的 DefaultAction 處理。
     */
    static List<String> convertActions(List<String> src) {
        List<String> dst = new ArrayList<>(src.size());
        for (String raw : src) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                dst.add(raw);
                continue;
            }
            // 找出 [prefix]<空白>... 結構
            int bracketStart = trimmed.indexOf('[');
            int bracketEnd = trimmed.indexOf(']');
            if (bracketStart != 0 || bracketEnd <= 0) {
                dst.add(raw);
                continue;
            }
            String prefix = trimmed.substring(1, bracketEnd).trim().toLowerCase();
            String body = trimmed.substring(bracketEnd + 1).trim();
            // 擴充對應表：DeluxeMenus 常見 prefix 全部明確映射，避免 default 走原樣保留時
            // 被 DefaultAction 當成玩家身分指令誤觸發（例如 [broadcast] 變 /broadcast）。
            String converted = switch (prefix) {
                case "console" -> "[cmd] " + body;
                case "openguimenu" -> "[player] dgm open " + body;
                // 純訊息類動作：以聊天訊息呈現給玩家，避免被當成指令送出
                case "chat", "broadcast", "json", "minimessage", "actionbar", "title", "subtitle" -> "[message] " + body;
                // 副作用為「重新整理 / 連線 / 觸發事件」這類在 MiaoMenu 沒有對應動作的 prefix：
                case "refresh" -> "[close]";
                // [connect] 走玩家身分 /server <name>（MiaoMenu PlayerAction 內建 ProxyManager 處理跨服）
                case "connect" -> "[player] server " + body;
                // [sound] 用 PAPI 注入 %player_name% 作為 selector，避免 console 端 @s 失效
                case "sound" -> "[cmd] playsound " + body + " %player_name%";
                case "commandevent" -> "[cmd] " + body;
                // 其他通通保留：[player] [message] [close] 以及未知 prefix
                default -> raw;
            };
            dst.add(converted);
        }
        return dst;
    }

    /**
     * 把 DeluxeMenus 的 menu id 變成 java_menus 下安全的檔名：
     * 移除路徑相關字元、空白；只保留 [A-Za-z0-9._-]；避免 path traversal。
     */
    static String sanitizeFileSegment(String input) {
        if (input == null || input.isEmpty()) {
            return "menu";
        }
        StringBuilder sb = new StringBuilder();
        int len = Math.min(input.length(), 64);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else if (Character.isWhitespace(c)) {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (result.isEmpty() || result.equals(".") || result.equals("..")) {
            return "menu";
        }
        // 防止以 . 開頭被當隱藏檔；MiaoMenu 不希望有這種行為
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        return result.isEmpty() ? "menu" : result;
    }

    /**
     * 給 {@code ImportCommand} preview 用的合計：在現有 dataFolder 下，逐個 menu 比對「會新增 vs 衝突」。
     */
    public static Map<String, EntryResolution> resolveDiff(List<MenuEntry> entries, Path targetDataDir) {
        Map<String, EntryResolution> resolved = new LinkedHashMap<>();
        for (MenuEntry e : entries) {
            Path dst = targetDataDir.resolve(e.outputRelative());
            if (Files.exists(dst)) {
                resolved.put(e.id(), new EntryResolution(e, true));
            } else {
                resolved.put(e.id(), new EntryResolution(e, false));
            }
        }
        return resolved;
    }

    public record EntryResolution(MenuEntry entry, boolean targetExists) {
    }
}

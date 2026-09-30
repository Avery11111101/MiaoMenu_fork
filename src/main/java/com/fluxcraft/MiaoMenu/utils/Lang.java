package com.fluxcraft.MiaoMenu.utils;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 載入並提供語言檔（lang/&lt;language&gt;.yml）的訊息。
 * 顏色代碼使用 '&'，透過 Adventure 的 LegacyComponentSerializer 轉成 legacySection 字串。
 *
 * 對外仍保留靜態 API（Lang.get(key)）以維持向後相容；
 * 內部改為從獨立的 lang 檔讀取，而不是 config.yml 的 messages 區塊。
 */
public final class Lang {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private static Plugin plugin;
    // volatile：UpdateChecker 的 HttpClient async 回呼、Floodgate Netty 緒等都會呼叫 Lang.get；
    // 若主緒 Lang.load 正在替換 messages，非主緒可能讀到 partially published 的 reference 或舊值。
    private static volatile FileConfiguration messages;
    private static volatile String currentLanguage = "zh_TW";

    private Lang() {
    }

    /**
     * 初始化語言系統，並將內建語言檔釋出到 plugins/&lt;plugin&gt;/lang/。
     */
    public static void init(Plugin plugin) {
        Lang.plugin = plugin;
        saveBundledLanguages();
        String configuredLang = plugin.getConfig().getString("language",
                plugin.getConfig().getString("settings.lang", "zh_TW"));
        load(configuredLang);
    }

    private static void saveBundledLanguages() {
        saveDefault("lang/en.yml");
        saveDefault("lang/zh_TW.yml");
        saveDefault("lang/en-us.yml");
        saveDefault("lang/zh-cn.yml");
        saveDefault("lang/zh-tw.yml");
        saveDefault("lang/ja-jp.yml");
        saveDefault("lang/vi-vn.yml");
    }

    /**
     * 載入指定語言檔；找不到時 fallback 到 en.yml，並以 jar 內 en.yml 作為缺鍵的預設。
     * YAML 解析失敗時保留前次成功的語言內容，避免熱重載期間玩家看到亂碼 key。
     */
    public static void load(String language) {
        if (plugin == null) {
            return;
        }
        if (language == null || language.isEmpty()) {
            language = "zh_TW";
        }
        saveBundledLanguages();

        File file = resolveLanguageFile(language);
        if (!file.exists()) {
            plugin.getLogger().warning("找不到語言檔 lang/" + language + ".yml，改用 en。");
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }
        FileConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("載入語言檔 " + file.getName() + " 失敗（YAML 解析錯誤），沿用前次設定。原因：" + e.getMessage());
            return;
        }

        InputStream def = plugin.getResource("lang/en.yml");
        if (def != null) {
            try {
                YamlConfiguration defCfg = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(def, StandardCharsets.UTF_8));
                cfg.setDefaults(defCfg);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("讀取內建預設語言檔 lang/en.yml 失敗（缺鍵將直接回傳 key）：" + e.getMessage());
            }
        }
        messages = cfg;
        currentLanguage = language;
        plugin.getLogger().info("已載入語言檔：" + file.getName() + "（current=" + currentLanguage + "）");
    }

    private static File resolveLanguageFile(String language) {
        File exact = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (exact.exists()) {
            return exact;
        }
        // 嘗試常見別名轉換
        String alt = switch (language.toLowerCase()) {
            case "zh_tw", "zh-tw" -> "zh_TW";
            case "en", "en_us", "en-us" -> "en";
            default -> language;
        };
        File altFile = new File(plugin.getDataFolder(), "lang/" + alt + ".yml");
        if (altFile.exists()) {
            return altFile;
        }
        return exact;
    }

    public static void reload() {
        load(currentLanguage);
    }

    public static String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * 將 jar 內的語言檔釋出至資料夾（檔案不存在時才釋出）。
     */
    private static void saveDefault(String path) {
        if (plugin == null) {
            return;
        }
        File target = new File(plugin.getDataFolder(), path);
        if (target.exists()) {
            return;
        }
        InputStream in = plugin.getResource(path);
        if (in == null) {
            return;
        }
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException ignored) {
            // 資源不存在時 saveResource 會丟例外；保險起見忽略。
        }
    }

    /**
     * 依鍵值取得已格式化的文字（顏色碼轉成 legacySection）。
     * 先試 lang 檔中的 key 本身，找不到時退回 config.yml 的 messages.&lt;key&gt; 與 &lt;key&gt;（向後相容）。
     */
    public static String get(String key) {
        if (plugin == null) {
            return key;
        }
        String message = null;
        if (messages != null) {
            message = messages.getString(key);
            if (message == null) {
                message = messages.getString("messages." + key);
            }
        }
        if (message == null) {
            // 向後相容：嘗試回讀 config.yml 內舊有的 messages.&lt;key&gt;。
            message = plugin.getConfig().getString("messages." + key);
            if (message == null) {
                message = plugin.getConfig().getString(key);
            }
        }
        if (message == null) {
            return key;
        }
        return SECTION.serialize(AMPERSAND.deserialize(message));
    }

    public static Map<String, String> getStringSection(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        if (messages != null) {
            ConfigurationSection section = messages.getConfigurationSection(prefix);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (section.isString(key)) {
                        result.put(key, section.getString(key));
                    }
                }
            }
        }
        return result;
    }
}

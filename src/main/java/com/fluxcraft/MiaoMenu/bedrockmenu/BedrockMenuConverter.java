package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.constants.Constants.ConfigKeys;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenu;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

/**
 * 將 Java 版選單 (JavaMenu) 動態轉譯為基岩版 (BedrockMenu / SimpleForm)。
 * 當伺服器未針對特定選單建立 bedrock_menus/*.yml 時，提供即時無痛回退，
 * 確保基岩版玩家能正常開啟選單並享有貼圖消毒與頭顱貼圖顯示。
 */
public final class BedrockMenuConverter {

    private BedrockMenuConverter() {}

    /**
     * 從 JavaMenu 動態產生等價的 BedrockMenu 物件。
     *
     * @param javaMenu 來源 Java 版選單
     * @param plugin MiaoMenu 插件實例
     * @param requirementService 條件驗證服務
     * @return 轉譯後的 BedrockMenu
     */
    public static BedrockMenu fromJavaMenu(JavaMenu javaMenu, MiaoMenu plugin, RequirementService requirementService) {
        YamlConfiguration config = new YamlConfiguration();
        config.set(ConfigKeys.MENU_TITLE, javaMenu.getTitle());

        List<Map<String, Object>> items = new ArrayList<>();
        for (JavaMenu.MenuItem item : javaMenu.getItems()) {
            Map<String, Object> map = new LinkedHashMap<>();

            // 組合按鈕標題與說明文字
            String text = item.getName();
            if (!item.getLore().isEmpty()) {
                text += "\n" + String.join("\n", item.getLore());
            }
            map.put(ConfigKeys.TEXT, text);

            // 解析圖示與類型
            String material = item.getMaterial();
            String rawIcon;
            String rawType = ConfigKeys.ICON_TYPE_PATH;
            if (material != null && material.toLowerCase(Locale.ROOT).startsWith("base64head:")) {
                rawIcon = material;
                rawType = ConfigKeys.ICON_TYPE_URL;
            } else {
                rawIcon = BedrockTextureMapper.getTexturePath(material);
            }
            BedrockIconSanitizer.SanitizedIcon sanitized = BedrockIconSanitizer.sanitize(rawIcon, rawType);
            map.put(ConfigKeys.ICON, sanitized.pathOrUrl());
            map.put(ConfigKeys.ICON_TYPE, sanitized.iconType());

            // 動作指令優先順序：左鍵 -> 通用
            List<String> commands = !item.getLeftClickCommands().isEmpty()
                    ? item.getLeftClickCommands()
                    : item.getClickCommands();
            if (!commands.isEmpty()) {
                map.put(ConfigKeys.COMMAND, commands.getFirst());
            }

            if (item.getLockMessage() != null) {
                map.put("lock_message", item.getLockMessage());
            }

            items.add(map);
        }
        config.set(ConfigKeys.MENU_ITEMS, items);

        if (!javaMenu.getViewRequirements().isEmpty()) {
            config.set("view_requirement.requirements", javaMenu.getViewRequirements());
        }
        if (javaMenu.getDenyMessage() != null) {
            config.set("view_requirement.deny_message", javaMenu.getDenyMessage());
        }
        if (javaMenu.getFallbackMenu() != null) {
            config.set("view_requirement.fallback_menu", javaMenu.getFallbackMenu());
        }

        return new BedrockMenu(javaMenu.getName(), config, plugin, requirementService);
    }
}

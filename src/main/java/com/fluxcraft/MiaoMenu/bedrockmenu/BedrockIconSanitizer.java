package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.net.URI;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fluxcraft.MiaoMenu.constants.Constants.ConfigKeys;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;

/**
 * 負責基岩版 Form 按鈕圖示的智慧消毒、自動協定識別與路徑修復。
 * 解決以下基岩版常見圖片破圖與不正常問題：
 * 1. 原版貼圖路徑帶有 .png（基岩客戶端會導致紫黑方格或透明失效）。
 * 2. 網路圖片 (URL) 漏設 icon_type: url（預設被當成 path 導致在資源包尋找失敗）。
 * 3. 網路圖片使用未加密的 http://（在 iOS ATS / Android 9+ 被強制阻擋）。
 * 4. 路徑帶有 Windows 反斜線 \ 或開頭斜線 /。
 * 5. 頭顱 base64head:<hash> 自動轉譯為 Mojang 官方 CDN 圖片 URL。
 * 6. 填寫 Java Material 名稱時自動透過 {@link BedrockTextureMapper} 轉為基岩貼圖。
 */
public final class BedrockIconSanitizer {

    public record SanitizedIcon(@NotNull String pathOrUrl, @NotNull String iconType) {
        public boolean isEmpty() {
            return pathOrUrl.isBlank();
        }
    }

    private BedrockIconSanitizer() {}

    /**
     * 對設定檔中的 icon 與 iconType 進行全自動消毒與正規化。
     *
     * @param rawIcon 原始 icon 字串
     * @param rawType 原始 iconType 字串（可能為 null 或 "path"/"url"）
     * @return 消毒後的圖示資訊
     */
    @NotNull
    public static SanitizedIcon sanitize(@Nullable String rawIcon, @Nullable String rawType) {
        if (rawIcon == null || rawIcon.isBlank()) {
            return new SanitizedIcon("", ConfigKeys.ICON_TYPE_PATH);
        }

        String icon = rawIcon.trim();
        String type = rawType != null ? rawType.trim().toLowerCase(Locale.ROOT) : ConfigKeys.ICON_TYPE_PATH;

        // 1. 支援自訂頭顱格式 base64head:<hash> -> 轉為 Mojang CDN URL
        if (icon.toLowerCase(Locale.ROOT).startsWith("base64head:")) {
            String payload = icon.substring(11).trim();
            URI uri = ItemResolver.resolveSkinTextureUri(payload);
            if (uri != null) {
                return new SanitizedIcon(uri.toString(), ConfigKeys.ICON_TYPE_URL);
            }
        }

        // 2. 智慧偵測 URL：若開頭為 http:// 或 https://，強制修正為 URL 類型
        if (icon.startsWith("http://") || icon.startsWith("https://")) {
            // 自動升級 http 為 https 避免行動裝置安全政策阻擋
            if (icon.startsWith("http://")) {
                icon = "https://" + icon.substring("http://".length());
            }
            return new SanitizedIcon(icon, ConfigKeys.ICON_TYPE_URL);
        }

        // 3. 若手動宣告為 URL 但未帶協定（如 example.com/icon.png）
        if (ConfigKeys.ICON_TYPE_URL.equalsIgnoreCase(type)) {
            if (!icon.contains("://")) {
                icon = "https://" + icon;
            }
            return new SanitizedIcon(icon, ConfigKeys.ICON_TYPE_URL);
        }

        // 4. PATH 模式路徑消毒與正規化
        String path = icon.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        // 5. 若是基岩原版貼圖目錄（textures/...），基岩客戶端嚴禁帶 .png，否則會破圖！
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.startsWith("textures/items/")
                || lowerPath.startsWith("textures/blocks/")
                || lowerPath.startsWith("textures/ui/")
                || lowerPath.startsWith("textures/gui/")) {
            if (path.endsWith(".png") || path.endsWith(".PNG")) {
                path = path.substring(0, path.length() - 4);
            }
            return new SanitizedIcon(path.toLowerCase(Locale.ROOT), ConfigKeys.ICON_TYPE_PATH);
        }

        // 6. 若用戶直接填寫了 Java 材質名稱（如 DIAMOND_SWORD, GOLDEN_APPLE, STONE 等）
        if (BedrockTextureMapper.isRecognizedMaterial(path)) {
            String mapped = BedrockTextureMapper.getTexturePath(path);
            return new SanitizedIcon(mapped, ConfigKeys.ICON_TYPE_PATH);
        }

        // 7. 若用戶只寫了單純物品名但省略了 textures/items/ 前綴（如 diamond）
        if (!path.contains("/")) {
            String mapped = BedrockTextureMapper.getTexturePath(path);
            return new SanitizedIcon(mapped, ConfigKeys.ICON_TYPE_PATH);
        }

        // 8. 自訂資源包路徑（保留原始大小寫與副檔名）
        return new SanitizedIcon(path, ConfigKeys.ICON_TYPE_PATH);
    }
}

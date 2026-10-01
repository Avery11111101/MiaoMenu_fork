package com.fluxcraft.MiaoMenu.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpdateServiceTest {

    @Test
    @DisplayName("語意化版本比對測試")
    void testVersionComparison() {
        // 基本數字大小比較
        assertTrue(UpdateService.isNewerVersion("1.4.0", "1.4.1"));
        assertFalse(UpdateService.isNewerVersion("1.4.1", "1.4.0"));
        assertFalse(UpdateService.isNewerVersion("1.4.0", "1.4.0"));

        // 前綴 'v' 容錯
        assertTrue(UpdateService.isNewerVersion("v1.4.0", "v1.4.1"));
        assertTrue(UpdateService.isNewerVersion("1.4.0", "v1.5.0"));

        // 正式版 vs 預發布版：同主版本時，正式版比任何預發布版新
        assertTrue(UpdateService.isNewerVersion("1.4.0-beta.2", "1.4.0"));
        assertFalse(UpdateService.isNewerVersion("1.4.0", "1.4.0-beta.2"));

        // 預發布版內部自然數字序號比較
        assertTrue(UpdateService.isNewerVersion("1.4.0-beta.2", "1.4.0-beta.3"));
        assertTrue(UpdateService.isNewerVersion("1.4.0-beta.2", "1.4.0-beta.10"));
        assertFalse(UpdateService.isNewerVersion("1.4.0-beta.10", "1.4.0-beta.2"));

        // 跨主版本時，高主版本的預發布版高於低主版本的正式版
        assertTrue(UpdateService.isNewerVersion("1.3.0", "1.4.0-beta.1"));
    }

    @Test
    @DisplayName("Markdown 聊天色彩排版轉換測試")
    void testMarkdownFormatting() {
        String md = """
                # 測試發布標題
                ## 次標題
                - 修正物品顯示問題
                **重大更新**功能
                """;
        List<String> formatted = UpdateService.formatMarkdown(md, 10);
        assertFalse(formatted.isEmpty());
        assertEquals("§6§l=== 測試發布標題 ===", formatted.get(0));
        assertEquals("§e§l▸ 次標題", formatted.get(1));
        assertEquals("§7    • §f修正物品顯示問題", formatted.get(2));
        assertTrue(formatted.get(3).contains("§e§l重大更新§r§7"));
    }
}

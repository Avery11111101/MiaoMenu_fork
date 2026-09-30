package com.fluxcraft.MiaoMenu.menu.action.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.constants.Constants;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.proxy.ProxyManager;
import com.fluxcraft.MiaoMenu.security.InputValidator;
import com.fluxcraft.MiaoMenu.utils.Lang;

import java.util.regex.Pattern;

public class PlayerAction implements com.fluxcraft.MiaoMenu.menu.action.MenuAction {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public void execute(Player player, String content, Plugin plugin) {
        if (content == null || content.isEmpty()) return;
        String cmd = Constants.stripLeadingSlash(content);

        // 與 CmdAction 一致：在派發前過濾不安全字元（PlaceholderAPI 展開後可能藏 ;; / && / ||）。
        if (!InputValidator.isSafeCommandContent(cmd)) {
            player.sendMessage(Lang.get("message.unsafe-input"));
            plugin.getLogger().warning("[player] 拒絕執行不安全指令內容（player=" + player.getName() + "）：" + cmd);
            return;
        }

        // 與 Paper 處理 client chat packet 時印的 `issued server command:` 對齊；
        // 因 player.performCommand() / ProxyManager.sendServerCommand() 都不會印這行，
        // 走選單觸發的指令在主控台會默默消失，難以稽核。
        Bukkit.getLogger().info(player.getName() + " issued server command: /" + cmd);

        if (plugin instanceof MiaoMenu miaoMenu) {
            String[] parts = WHITESPACE.split(cmd, 2);

            if (parts.length > 0 && parts[0].equalsIgnoreCase("server") && parts.length > 1) {
                String serverName = parts[1].trim();
                ProxyManager proxyManager = miaoMenu.getProxyManager();
                if (proxyManager != null && proxyManager.isProxyConnected()) {
                    boolean success = proxyManager.sendServerCommand(player, serverName);
                    if (success) return;
                }
            }
        }

        // Folia 下 player.performCommand 必須在該玩家所屬的 region 緒；
        // 透過 BedrockMenu Floodgate callback 進來時呼叫端是 Netty I/O 緒。
        FoliaFactory.getAdapter().runForEntity(plugin, player, () -> player.performCommand(cmd));
    }
}

package com.fluxcraft.MiaoMenu.update;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;

/**
 * 管理員進服更新提示監聽器
 * 當具有 dgeysermenu.admin 權限的玩家進服時，若檢測到新版本，延遲 40 ticks 發送提示訊息。
 */
public final class UpdateNoticeListener implements Listener {

    private final MiaoMenu plugin;

    public UpdateNoticeListener(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dgeysermenu.admin")) {
            return;
        }

        if (!plugin.getConfig().getBoolean("updater.notify-admin-on-join", true)) {
            return;
        }

        UpdateService updateService = plugin.getUpdateService();
        if (updateService == null || !updateService.hasUpdate()) {
            return;
        }

        var release = updateService.getCachedLatestRelease();
        if (release == null) {
            return;
        }

        // 延遲 40 ticks（約 2 秒）發送，避免玩家剛進服訊息被刷掉；相容 Folia 與 Paper
        FoliaFactory.getAdapter().runTaskLaterForEntity(plugin, player, () -> {
            if (player.isOnline()) {
                String typeTag = release.isPrerelease() ? "§b[測試版 🧪]" : "§a[正式版 🌟]";
                player.sendMessage("§6[MiaoMenu] 發現新版本 " + typeTag + ": §f" + release.tagName()
                        + " §7(目前: v" + plugin.getPluginMeta().getVersion() + ")");
                player.sendMessage("§e請執行指令 §b/dgm update §e查看詳細日誌或選擇下載更新。");
            }
        }, 40L);
    }
}

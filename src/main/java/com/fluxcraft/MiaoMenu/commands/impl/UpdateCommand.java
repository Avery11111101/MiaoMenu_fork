package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.update.UpdateService;

/**
 * /dgm update [check|download [release|beta]]
 * 提供雙軌更新檢測與自動安全下載指令。
 */
public class UpdateCommand implements PluginCommand {

    private final MiaoMenu plugin;

    public UpdateCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        UpdateService service = plugin.getUpdateService();
        if (service == null) {
            sender.sendMessage("§c[MiaoMenu] 更新服務尚未初始化完成。");
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("check")) {
            service.checkForUpdates(true, sender, null);
            return;
        }

        if (args[0].equalsIgnoreCase("download")) {
            String channel = args.length > 1 ? args[1] : null;
            service.downloadUpdate(channel, sender, null);
            return;
        }

        sender.sendMessage("§6§l=================[ MiaoMenu 更新指令 ]=================");
        sender.sendMessage("§e用法說明:");
        sender.sendMessage("  §f/dgm update [check] §7- 檢查 GitHub 最新發布版本");
        sender.sendMessage("  §f/dgm update download [release|beta] §7- 下載指定版本並安全取代檔案");
        sender.sendMessage("§6§l====================================================");
    }

    @Override
    public @Nullable List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (String sub : List.of("check", "download")) {
                if (sub.startsWith(prefix)) {
                    list.add(sub);
                }
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("download")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (String ch : List.of("release", "beta")) {
                if (ch.startsWith(prefix)) {
                    list.add(ch);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}

package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.update.UpdateService;
import com.fluxcraft.MiaoMenu.utils.Lang;

/**
 * /dgm about — 顯示插件版本、Fork / 原作 / Modrinth 連結。
 * 同時被 onEnable 啟動 banner 與 PlayerJoin 通知共用 about 文案來源（lang/&lt;language&gt;.yml）。
 */
public class AboutCommand implements PluginCommand {
    public static final String FORK_URL = "https://github.com/Avery11111101/MiaoMenu_fork";
    public static final String ORIGINAL_URL = "https://github.com/Yamada0001/MiaoMenu";
    public static final String MODRINTH_URL = "https://modrinth.com/plugin/miaomenu_fork";

    private final MiaoMenu plugin;

    public AboutCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        for (String line : renderLines(plugin)) {
            sender.sendMessage(line);
        }
        UpdateService service = plugin.getUpdateService();
        if (service != null && service.hasUpdate()) {
            var release = service.getCachedLatestRelease();
            if (release != null) {
                sender.sendMessage(Lang.get("about.update-available")
                        .replace("{0}", release.tagName())
                        .replace("{1}", plugin.getPluginMeta().getVersion())
                        .replace("{2}", release.htmlUrl()));
            }
        }
    }

    /**
     * 共用文案：about 指令、啟動 banner、上線通知都從這拉。
     * 作者只列 Avery；原作資訊請看 Fork 連結（GitHub 內的 fork 來源欄）。
     */
    public static List<String> renderLines(MiaoMenu plugin) {
        String version = plugin.getPluginMeta().getVersion();
        return List.of(
                Lang.get("about.header"),
                Lang.get("about.version").replace("{0}", version),
                Lang.get("about.author"),
                Lang.get("about.fork").replace("{0}", FORK_URL),
                Lang.get("about.modrinth").replace("{0}", MODRINTH_URL),
                Lang.get("about.footer")
        );
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}

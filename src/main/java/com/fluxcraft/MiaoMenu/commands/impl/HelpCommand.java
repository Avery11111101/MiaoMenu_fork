package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.utils.Lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * /dgm help [&lt;子指令&gt;] — 列出所有子指令；帶參數時顯示對應子指令的詳細教學。
 *
 * 詳細教學從 lang 檔的 `help.commands.&lt;name&gt;` 區塊取 usage / detail / permission，
 * 切換語言後（/dgm lang）自然會跟著變語系。
 */
public class HelpCommand implements PluginCommand {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private volatile Map<String, String> commandDescriptions;

    public HelpCommand(Map<String, String> commandDescriptions) {
        this.commandDescriptions = commandDescriptions;
    }

    /**
     * 供 CommandManager 在 `/dgm lang` 切換語系後熱替換描述快取。
     */
    public void setDescriptions(Map<String, String> descriptions) {
        this.commandDescriptions = descriptions;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            showDetail(sender, args[0].toLowerCase(Locale.ROOT));
            return;
        }
        showList(sender);
    }

    /** 列表模式：原本的 `/dgm help` 行為，列出所有子指令 + 一句說明。 */
    private void showList(CommandSender sender) {
        String headerRaw = Lang.get("help.header");
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(headerRaw));
        Map<String, String> descs = commandDescriptions;
        for (Map.Entry<String, String> entry : descs.entrySet()) {
            String cmdName = entry.getKey();
            String descRaw = entry.getValue();
            Component cmdComponent = Component.text(cmdName)
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.suggestCommand("/dgm " + cmdName))
                    .hoverEvent(Component.text(descRaw).asHoverEvent());
            Component descComponent = LEGACY_SERIALIZER.deserialize(descRaw);
            sender.sendMessage(
                    Component.empty()
                            .append(Component.text(" ☛ ").color(NamedTextColor.DARK_GRAY))
                            .append(cmdComponent)
                            .append(Component.text(" : ").color(NamedTextColor.GRAY))
                            .append(descComponent)
            );
        }
        sender.sendMessage(Component.empty());
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(Lang.get("help.usage")));
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(Lang.get("help.detail-hint")));
    }

    /** 詳細模式：顯示單一子指令的 usage / detail / permission。 */
    private void showDetail(CommandSender sender, String name) {
        Map<String, String> descs = commandDescriptions;
        if (descs == null || !descs.containsKey(name)) {
            sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                    Lang.get("help.unknown-command").replace("{0}", name)));
            return;
        }
        String usage = lookupOrEmpty("help.commands." + name + ".usage");
        String detail = lookupOrEmpty("help.commands." + name + ".detail");
        String permission = lookupOrEmpty("help.commands." + name + ".permission");

        sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                Lang.get("help.detail.header").replace("{0}", name)));
        if (!usage.isEmpty()) {
            sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                    Lang.get("help.detail.usage-label") + " " + usage));
        }
        if (!detail.isEmpty()) {
            for (String line : detail.split("\n")) {
                sender.sendMessage(LEGACY_SERIALIZER.deserialize(line));
            }
        }
        if (!permission.isEmpty()) {
            sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                    Lang.get("help.detail.permission-label") + " " + permission));
        }
    }

    private String lookupOrEmpty(String key) {
        String value = Lang.get(key);
        return value == null || value.equals(key) ? "" : value;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0] != null ? args[0].toLowerCase(Locale.ROOT) : "";
        Map<String, String> descs = commandDescriptions;
        if (descs == null) {
            return Collections.emptyList();
        }
        return descs.keySet().stream()
                .filter(s -> s.startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }

    /** 給 CommandManager.loadHelpDescriptions 用的固定子指令順序。 */
    public static List<String> defaultCommandOrder() {
        return Arrays.asList("open", "reload", "import", "lang", "update", "about", "whoami", "help");
    }
}

package com.fluxcraft.MiaoMenu.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class ProxyManager implements PluginMessageListener {
    private static final String BUNGEECORD_CHANNEL = "BungeeCord";
    private static final String PROXY_MODE_KEY = "settings.proxy-mode";

    private final MiaoMenu plugin;
    private ProxyType proxyType;

    public ProxyManager(MiaoMenu plugin) {
        this.plugin = plugin;
        this.proxyType = ProxyType.NONE;
    }

    public void initialize() {
        proxyType = configuredProxyType();
        if (proxyType != ProxyType.NONE) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEECORD_CHANNEL, this);
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);
            plugin.getLogger().info(Lang.get("log.proxy.detected").replace("{0}", proxyType.name()));
            if (proxyType == ProxyType.VELOCITY) {
                plugin.getLogger().info(Lang.get("log.proxy.velocity-forwarding-required"));
            }
        } else {
            plugin.getLogger().info(Lang.get("log.proxy.disabled"));
        }
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public void shutdown() {
        if (plugin.getServer().getMessenger().isIncomingChannelRegistered(plugin, BUNGEECORD_CHANNEL)) {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEECORD_CHANNEL);
        }
        if (plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEECORD_CHANNEL)) {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);
        }
        proxyType = ProxyType.NONE;
    }

    private ProxyType configuredProxyType() {
        String configuredMode = plugin.getConfig().getString(PROXY_MODE_KEY);
        if (configuredMode == null || configuredMode.isBlank()) {
            if (plugin.getConfig().contains("settings.velocity-network")) {
                plugin.getLogger().warning("settings.velocity-network is deprecated; use settings.proxy-mode instead.");
                return plugin.getConfig().getBoolean("settings.velocity-network", false)
                        ? ProxyType.VELOCITY
                        : ProxyType.NONE;
            }
            if (plugin.getConfig().getBoolean("settings.bungeecord-network", false)) {
                return ProxyType.BUNGEECORD;
            }
            // 不能用 Class.forName("net.md_5.bungee.api.ChatColor") 作判斷：
            // Paper / Folia 自帶 bungee-chat shaded class，單機伺服器也永遠回 true，
            // 結果跨服指令 silently 失敗（plugin channel 沒有上游 proxy 收）。
            // 改讀 spigot.yml 的 settings.bungeecord 旗標（Paper 與 Spigot 共用 API），
            // 這才是真正的「後端是否接在 BungeeCord/Velocity 之下」設定。
            try {
                if (Bukkit.spigot().getSpigotConfig().getBoolean("settings.bungeecord", false)) {
                    return ProxyType.BUNGEECORD;
                }
            } catch (Throwable ignored) {
                // 非 Spigot/Paper 衍生 server 取不到該 API：當作未連 proxy。
            }
            return ProxyType.NONE;
        }

        ProxyType configuredType = ProxyType.fromConfigValue(configuredMode);
        if (configuredType == ProxyType.NONE && !"NONE".equalsIgnoreCase(configuredMode.trim())) {
            plugin.getLogger().warning("Unknown proxy mode '" + configuredMode + "'; disabling proxy integration.");
        }
        return configuredType;
    }

    public boolean isProxyConnected() {
        return proxyType != ProxyType.NONE;
    }

    public boolean sendServerCommand(Player player, String serverName) {
        if (proxyType == ProxyType.NONE) {
            plugin.getLogger().warning(Lang.get("log.proxy.not-detected"));
            return false;
        }
        try {
            sendBungeeCordServerCommand(player, serverName);
            return true;
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.proxy.send-failed").replace("{0}", serverName), e);
            return false;
        }
    }

    private void sendBungeeCordServerCommand(Player player, String serverName) {
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, BUNGEECORD_CHANNEL)) {
            throw new IllegalStateException(Lang.get("log.proxy.channel-not-registered").replace("{0}", BUNGEECORD_CHANNEL));
        }
        byte[] message = createBungeeCordServerMessage(serverName);
        player.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, message);
    }

    private byte[] createBungeeCordServerMessage(String serverName) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream dataOut = new DataOutputStream(byteStream)) {
            dataOut.writeUTF("Connect");
            dataOut.writeUTF(serverName);
            return byteStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(Lang.get("log.proxy.message-create-failed").replace("{0}", serverName), e);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, @NonNull Player player, byte @NonNull [] message) {
        if (channel.equals(BUNGEECORD_CHANNEL)) {
            handleBungeeCordResponse(message);
        }
    }

    private void handleBungeeCordResponse(byte[] message) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(message);
             DataInputStream dataIn = new DataInputStream(byteStream)) {
            String subchannel = dataIn.readUTF();
            plugin.getLogger().fine(Lang.get("log.proxy.message-received").replace("{0}", subchannel));
        } catch (IOException e) {
            plugin.getLogger().log(Level.FINE, Lang.get("log.proxy.response-handle-failed"), e);
        }
    }

    public enum ProxyType {
        NONE,
        BUNGEECORD,
        VELOCITY;

        static ProxyType fromConfigValue(String value) {
            if (value == null) {
                return NONE;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "BUNGEE", "BUNGEECORD" -> BUNGEECORD;
                case "VELOCITY" -> VELOCITY;
                default -> NONE;
            };
        }
    }
}

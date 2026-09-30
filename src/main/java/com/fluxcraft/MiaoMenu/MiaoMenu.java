package com.fluxcraft.MiaoMenu;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.fluxcraft.MiaoMenu.bedrockmenu.BedrockMenuManager;
import com.fluxcraft.MiaoMenu.commands.CommandManager;
import com.fluxcraft.MiaoMenu.commands.impl.AboutCommand;
import com.fluxcraft.MiaoMenu.commands.impl.GetMenuClockCommand;
import com.fluxcraft.MiaoMenu.commands.impl.LangCommand;
import com.fluxcraft.MiaoMenu.config.ConfigManager;
import com.fluxcraft.MiaoMenu.config.LegacyDataMigrator;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenuListener;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenuManager;
import com.fluxcraft.MiaoMenu.listeners.ClockInteractionListener;
import com.fluxcraft.MiaoMenu.listeners.PlayerLifecycleListener;
import com.fluxcraft.MiaoMenu.listeners.PlayerLifecycleListener_Folia;
import com.fluxcraft.MiaoMenu.managers.HotReloadManager;
import com.fluxcraft.MiaoMenu.managers.MenuClockManager;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.proxy.ProxyManager;
import com.fluxcraft.MiaoMenu.security.RateLimiter;
import com.fluxcraft.MiaoMenu.update.UpdateChecker;
import com.fluxcraft.MiaoMenu.update.UpdateNoticeListener;
import com.fluxcraft.MiaoMenu.utils.Lang;

import cn.handyplus.lib.adapter.HandySchedulerUtil;
import me.clip.placeholderapi.PlaceholderAPI;

public class MiaoMenu extends JavaPlugin {
    private static final int BSTATS_ID = 28979;
    public static final int JOIN_DELAY_TICKS = 20;
    private static final String MAIN_COMMAND = "dgeysermenu";
    private static final String CLOCK_COMMAND = "getmenuclock";
    private static final String LANG_COMMAND = "mmflang";

    private final ThreadLocal<Set<String>> menuOpenChain = ThreadLocal.withInitial(HashSet::new);

    private ConfigManager configManager;
    private JavaMenuManager javaMenuManager;
    private BedrockMenuManager bedrockMenuManager;
    private CommandManager commandManager;
    private MenuClockManager menuClockManager;
    private HotReloadManager hotReloadManager;
    private ProxyManager proxyManager;
    private RequirementService requirementService;
    private RateLimiter interactionRateLimiter;
    private UpdateChecker updateChecker;
    private ActionRegistry actionRegistry;

    private Class<?> floodgateApiClass;
    private Object floodgateApiInstance;
    private Method floodgateIsPlayerMethod;

    @Override
    public void onEnable() {
        try {
            initializeCoreServices();
            initializeMenuSystems();
            registerListeners();
            registerCommands();
            initializeOptionalFeatures();
            initializeUpdateChecker();
            printAboutBanner();
            getLogger().info(Lang.get("log.plugin.enabled").replace("{0}", getPluginMeta().getVersion()));
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, Lang.get("log.plugin.enable-failed"), e);
            throw e;
        }
    }

    /** 啟動時把 about 資訊也印到主控台，讓伺服器主有一眼可看到的 GitHub / Modrinth 連結。 */
    private void printAboutBanner() {
        // 用 Lang.get 拉純文字（已含 &color，主控台會略過 ANSI/legacy section 但仍可讀）
        getLogger().info("------------------------------------------------------------");
        for (String line : AboutCommand.renderLines(this)) {
            // 主控台不渲染顏色碼，剝掉 §x 以維持可讀性
            getLogger().info(stripLegacyColors(line));
        }
        getLogger().info("------------------------------------------------------------");
    }

    private static String stripLegacyColors(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void initializeUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        updateChecker.refreshAsync(); // 非同步、失敗不影響啟動
    }

    @Override
    public void onDisable() {
        if (hotReloadManager != null) {
            hotReloadManager.shutdown();
        }
        if (interactionRateLimiter != null) {
            interactionRateLimiter.clearAll();
        }
        if (proxyManager != null) {
            proxyManager.shutdown();
        }
    }

    private void initializeCoreServices() {
        LegacyDataMigrator.migrateIfNeeded(this);
        saveDefaultConfig();
        Lang.init(this);
        HandySchedulerUtil.init(this);
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        configManager.checkAndRefreshMenus();
        requirementService = new RequirementService(this);
        interactionRateLimiter = new RateLimiter(java.time.Duration.ofSeconds(2), 10);
    }

    private void initializeMenuSystems() {
        NamespacedKey clockKey = new NamespacedKey(this, "menu_clock");
        RequirementFeedbackHandler requirementFeedbackHandler = new RequirementFeedbackHandler(this);
        ItemResolver itemResolver = new ItemResolver(this, configManager.getCraftEngineFallbackMaterial());
        SoundsClock soundsClock = new SoundsClock(this);
        actionRegistry = new ActionRegistry(this);
        javaMenuManager = new JavaMenuManager(this, itemResolver, soundsClock, requirementService, requirementFeedbackHandler);
        bedrockMenuManager = new BedrockMenuManager(this, actionRegistry, soundsClock, requirementService, requirementFeedbackHandler);
        commandManager = new CommandManager(this);
        menuClockManager = new MenuClockManager(this, clockKey);
        hotReloadManager = new HotReloadManager(this);
        proxyManager = new ProxyManager(this);
        proxyManager.initialize();
        javaMenuManager.loadAllMenus();
        bedrockMenuManager.loadAllMenus();
        registerClockListeners(menuClockManager);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JavaMenuListener(this, actionRegistry), this);
        getServer().getPluginManager().registerEvents(new UpdateNoticeListener(this), this);
    }

    private void registerClockListeners(MenuClockManager clockManager) {
        getServer().getPluginManager().registerEvents(new ClockInteractionListener(clockManager), this);
        if (FoliaFactory.isFolia()) {
            getServer().getPluginManager().registerEvents(new PlayerLifecycleListener_Folia(this, clockManager), this);
            return;
        }
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, clockManager), this);
    }

    private void initializeOptionalFeatures() {
        if (configManager.getConfig().getBoolean("settings.hot-reload.enabled", true)) {
            initializeHotReload();
        }
        initializeBStats();
        logPlaceholderApiStatus();
    }

    /**
     * 在 onEnable 末段印一行 PlaceholderAPI 狀態，讓伺服器主一眼判斷
     * 「選單 %placeholders% 沒解析」是 PAPI 未裝、還是 expansion 沒下載。
     * 大部分「PAPI 已裝但佔位符顯示原文」實際是缺對應 expansion，
     * 此 log 不會修問題，但能避免使用者陷入「程式碼是不是有 bug」的迷宮。
     */
    private void logPlaceholderApiStatus() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().warning("PlaceholderAPI 未啟用 — 選單 YAML 內的 %placeholders% 將原樣顯示（內建 %player_name% / %player% 仍會解析）。");
            return;
        }
        try {
            int count = PlaceholderAPI.getRegisteredIdentifiers().size();
            if (count == 0) {
                getLogger().warning("PlaceholderAPI 已啟用但未載入任何 expansion — 大部分 %placeholders% 會顯示原文。請執行 /papi ecloud download <名稱> 後再 /papi reload。");
            } else {
                getLogger().info("PlaceholderAPI 偵測通過，已載入 " + count
                        + " 個 expansion。若選單仍顯示原文，請用 /papi list 確認對應 expansion；未下載的可用 /papi ecloud download <名稱> 取得。");
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "PlaceholderAPI 偵測通過但 expansion 列舉失敗（可能 API 不相容）", e);
        }
    }

    private void initializeHotReload() {
        try {
            hotReloadManager.initialize();
        } catch (java.io.IOException | IllegalStateException | SecurityException e) {
            getLogger().log(Level.SEVERE, Lang.get("log.hot-reload.initialize-failed"), e);
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand(MAIN_COMMAND);
        if (command != null) {
            command.setExecutor(commandManager);
            command.setTabCompleter(commandManager);
        } else {
            getLogger().severe(Lang.get("log.command.register-failed").replace("{0}", MAIN_COMMAND));
        }

        PluginCommand clockCommand = getCommand(CLOCK_COMMAND);
        if (clockCommand != null) {
            clockCommand.setExecutor(new GetMenuClockCommand(this));
        } else {
            getLogger().severe(Lang.get("log.command.register-failed").replace("{0}", CLOCK_COMMAND));
        }

        // /mmflang（alias: lang）— 獨立短捷指令，行為等同 /dgm lang
        PluginCommand langCommand = getCommand(LANG_COMMAND);
        if (langCommand != null) {
            LangCommand langImpl = new LangCommand(this);
            langCommand.setExecutor((sender, cmd, label, args) -> {
                langImpl.execute(sender, args);
                return true;
            });
            langCommand.setTabCompleter((sender, cmd, label, args) -> langImpl.tabComplete(sender, args));
        } else {
            getLogger().severe(Lang.get("log.command.register-failed").replace("{0}", LANG_COMMAND));
        }
    }

    public void openSmartMenu(Player player, String menuName) {
        if (menuName == null) {
            player.sendMessage(Lang.get("message.menu-not-found").replace("{0}", ""));
            return;
        }
        Set<String> openingMenus = menuOpenChain.get();
        String normalizedMenuName = menuName.toLowerCase(Locale.ROOT);
        String openingMenuKey = player.getUniqueId() + ":" + normalizedMenuName;
        if (!openingMenus.add(openingMenuKey)) {
            getLogger().warning("Blocked recursive menu fallback for " + player.getName() + ": " + menuName);
            return;
        }

        try {
            if (isBedrockPlayer(player)) {
                bedrockMenuManager.openMenu(player, menuName);
                return;
            }
            javaMenuManager.openMenu(player, menuName);
        } finally {
            openingMenus.remove(openingMenuKey);
            if (openingMenus.isEmpty()) {
                menuOpenChain.remove();
            }
        }
    }

    public boolean isBedrockPlayer(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return false;
        }
        try {
            if (floodgateApiClass == null) {
                floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            }
            if (floodgateApiInstance == null) {
                floodgateApiInstance = floodgateApiClass.getMethod("getInstance").invoke(null);
            }
            if (floodgateIsPlayerMethod == null) {
                floodgateIsPlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
            }
            return (Boolean) floodgateIsPlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
        } catch (ReflectiveOperationException e) {
            getLogger().log(Level.WARNING, Lang.get("log.floodgate.player-check-failed").replace("{0}", player.getName()), e);
            return false;
        }
    }

    private void initializeBStats() {
        try {
            Metrics metrics = new Metrics(this, BSTATS_ID);
            metrics.addCustomChart(new SimplePie("server_software", this::detectServerSoftware));
            metrics.addCustomChart(new SimplePie("minecraft_version", this::detectMinecraftVersion));
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, Lang.get("log.bstats.initialize-failed"), e);
        }
    }

    private String detectServerSoftware() {
        String version = Bukkit.getVersion();
        return Stream.of("Paper", "Spigot", "Purpur", "Mint", "Leaves", "Leaf", "Luminol", "Folia")
                .filter(version::contains)
                .findFirst()
                .orElse("Other");
    }

    private String detectMinecraftVersion() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        int maxLength = Math.min(4, version.length());
        return version.substring(0, maxLength);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public JavaMenuManager getJavaMenuManager() {
        return javaMenuManager;
    }

    public BedrockMenuManager getBedrockMenuManager() {
        return bedrockMenuManager;
    }

    public ProxyManager getProxyManager() {
        return proxyManager;
    }

    public RequirementService getRequirementService() {
        return requirementService;
    }

    public RateLimiter getInteractionRateLimiter() {
        return interactionRateLimiter;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public MenuClockManager getMenuClockManager() {
        return menuClockManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}

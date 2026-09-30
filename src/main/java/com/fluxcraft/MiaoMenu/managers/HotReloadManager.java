package com.fluxcraft.MiaoMenu.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class HotReloadManager {
    private static final String JAVA_MENU_DIR = "java_menus";
    private static final String BEDROCK_MENU_DIR = "bedrock_menus";
    private static final String LANG_DIR = "lang";
    private static final String CONFIG_FILE = "config.yml";
    private static final String FILE_EXTENSION = ".yml";
    private static final long DEBOUNCE_DELAY_MS = 500;

    private final MiaoMenu plugin;
    private final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
    private final Map<String, Long> lastMenuReloadTimes = new ConcurrentHashMap<>();
    private WatchService watchService;
    private volatile boolean running = false;
    private Thread watcherThread;
    // volatile：watcher 緒寫、其他緒可能透過 reload 觸發點讀；64-bit long 在 32-bit JVM 上沒 volatile 會 torn read。
    private volatile long lastConfigReloadTime = 0;
    private volatile long lastLangReloadTime = 0;

    public HotReloadManager(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        this.running = true;
        registerDirectory(plugin.getDataFolder().toPath());
        registerDirectory(new File(plugin.getDataFolder(), JAVA_MENU_DIR).toPath());
        registerDirectory(new File(plugin.getDataFolder(), BEDROCK_MENU_DIR).toPath());
        registerDirectory(new File(plugin.getDataFolder(), LANG_DIR).toPath());
        plugin.getLogger().info(Lang.get("hot-reload.initialized"));
        startWatcher();
    }

    private void registerDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        WatchKey key = dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );
        keys.put(key, dir);
    }

    @SuppressWarnings("unchecked")
    private void startWatcher() {
        watcherThread = new Thread(() -> {
            while (running) {
                try {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path filename = pathEvent.context();
                        Path dir = keys.get(key);
                        if (dir == null) {
                            continue;
                        }
                        if (filename.toString().equals(CONFIG_FILE) && dir.equals(plugin.getDataFolder().toPath())) {
                            handleConfigReload();
                            continue;
                        }
                        Path langDir = new File(plugin.getDataFolder(), LANG_DIR).toPath();
                        if (dir.equals(langDir) && filename.toString().endsWith(FILE_EXTENSION)) {
                            handleLangReload(filename.toString());
                            continue;
                        }
                        if (filename.toString().endsWith(FILE_EXTENSION)) {
                            handleMenuReload(filename.toString());
                        }
                    }
                    if (!key.reset()) {
                        keys.remove(key);
                        if (keys.isEmpty()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, Lang.get("log.hot-reload.loop-failed"), e);
                }
            }
        }, "MiaoMenu_fork-HotReload-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void handleConfigReload() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastConfigReloadTime < DEBOUNCE_DELAY_MS) {
            return;
        }
        lastConfigReloadTime = currentTime;
        scheduleReload(() -> {
            plugin.getConfigManager().loadConfig();
            Lang.reload();
            plugin.getConfigManager().checkAndRefreshMenus();
            plugin.getJavaMenuManager().loadAllMenus();
            plugin.getBedrockMenuManager().loadAllMenus();
            if (plugin.getProxyManager() != null) {
                plugin.getProxyManager().reload();
            }
            plugin.getLogger().info(Lang.get("message.reloaded"));
        });
    }

    private void handleLangReload(String fileName) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLangReloadTime < DEBOUNCE_DELAY_MS) {
            return;
        }
        lastLangReloadTime = currentTime;
        scheduleReload(() -> {
            // 依目前 config.yml 的 language 設定重新載入語言檔
            Lang.load(plugin.getConfig().getString("language", "en"));
            plugin.getLogger().info(Lang.get("message.reloaded"));
        });
    }

    private void handleMenuReload(String fileName) {
        long currentTime = System.currentTimeMillis();
        Long lastReload = lastMenuReloadTimes.get(fileName);
        if (lastReload != null && currentTime - lastReload < DEBOUNCE_DELAY_MS) {
            return;
        }
        lastMenuReloadTimes.put(fileName, currentTime);
        plugin.getLogger().info(Lang.get("hot-reload.detected").replace("{0}", fileName));
        scheduleReload(() -> {
            plugin.getJavaMenuManager().loadAllMenus();
            plugin.getBedrockMenuManager().loadAllMenus();
            plugin.getLogger().info(Lang.get("message.reloaded"));
        });
    }

    private void scheduleReload(Runnable task) {
        // shutdown 已把 running 設為 false 但 watcher 可能還在 events 處理迴圈中；
        // 若此時 plugin 已 disable，再呼叫 Bukkit scheduler 會丟 IllegalPluginAccessException。
        if (!running || !plugin.isEnabled()) {
            return;
        }
        if (FoliaFactory.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), 10L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, 10L);
        }
    }

    public void shutdown() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
            try {
                // 給 watcher 緒少量時間清理；逾時就放它跑成 daemon，避免阻塞主 onDisable。
                watcherThread.join(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

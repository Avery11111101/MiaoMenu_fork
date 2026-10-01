package com.fluxcraft.MiaoMenu.update;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * GitHub Releases 自動更新與版本服務 (雙重推播制，移植自 ashop)
 * 支援非同步取得 GitHub Releases 列表、雙軌分辨正式穩定版與搶先測試版、
 * 動態解析 Release Notes 說明文字、以及外掛 Jar 實體安全替換機制。
 */
public final class UpdateService {

    public record ReleaseInfo(
            String tagName,
            String name,
            String body,
            String htmlUrl,
            String downloadUrl,
            String assetName,
            long assetSize,
            boolean isPrerelease,
            String publishedAt
    ) {}

    public record UpdateCatalog(
            ReleaseInfo latestOfficial,
            ReleaseInfo latestBeta,
            ReleaseInfo currentVersionInfo,
            List<ReleaseInfo> allReleases
    ) {}

    private static final long CACHE_TTL_MS = 3 * 60 * 1000L; // 3 分鐘快取防止 GitHub API 速率限制

    private final MiaoMenu plugin;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean isChecking = new AtomicBoolean(false);

    private volatile UpdateCatalog cachedCatalog = null;
    private volatile long lastFetchTime = 0L;

    public UpdateService(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    /**
     * 是否有可用更新（不論正式或測試版）
     */
    public boolean hasUpdate() {
        if (cachedCatalog == null) return false;
        String current = plugin.getPluginMeta().getVersion();
        boolean hasOfficial = cachedCatalog.latestOfficial() != null
                && isNewerVersion(current, cachedCatalog.latestOfficial().tagName());
        boolean hasBeta = cachedCatalog.latestBeta() != null
                && isNewerVersion(current, cachedCatalog.latestBeta().tagName());
        return hasOfficial || hasBeta;
    }

    public UpdateCatalog getCachedCatalog() {
        return cachedCatalog;
    }

    public ReleaseInfo getCachedLatestRelease() {
        if (cachedCatalog == null) return null;
        String channel = plugin.getConfig().getString("updater.channel", "RELEASE").trim().toUpperCase();
        String current = plugin.getPluginMeta().getVersion();

        if ("BETA".equals(channel) && cachedCatalog.latestBeta() != null) {
            if (isNewerVersion(current, cachedCatalog.latestBeta().tagName())) {
                return cachedCatalog.latestBeta();
            }
        }
        if (cachedCatalog.latestOfficial() != null && isNewerVersion(current, cachedCatalog.latestOfficial().tagName())) {
            return cachedCatalog.latestOfficial();
        }
        return cachedCatalog.latestBeta() != null ? cachedCatalog.latestBeta() : cachedCatalog.latestOfficial();
    }

    /**
     * 伺服器啟動時的初始化檢查
     */
    public void handleStartupCheck() {
        boolean checkOnStartup = plugin.getConfig().getBoolean("updater.check-on-startup", true);
        boolean autoDownload = plugin.getConfig().getBoolean("updater.auto-download", false);

        if (!checkOnStartup && !autoDownload) {
            return;
        }

        plugin.getLogger().info("正在檢查 GitHub Releases 最新發布版本 (雙軌檢查)...");
        fetchReleases(false, catalog -> {
            if (catalog == null) return;

            String current = plugin.getPluginMeta().getVersion();
            boolean hasOfficial = catalog.latestOfficial() != null && isNewerVersion(current, catalog.latestOfficial().tagName());
            boolean hasBeta = catalog.latestBeta() != null && isNewerVersion(current, catalog.latestBeta().tagName());

            if (hasOfficial) {
                plugin.getLogger().info("🌟 發現新正式穩定版: " + catalog.latestOfficial().tagName()
                        + " (" + catalog.latestOfficial().name() + ")");
                plugin.getLogger().info("   發布頁面: " + catalog.latestOfficial().htmlUrl());
            }
            if (hasBeta) {
                plugin.getLogger().info("🧪 發現新搶先測試版: " + catalog.latestBeta().tagName()
                        + " (" + catalog.latestBeta().name() + ")");
                plugin.getLogger().info("   發布頁面: " + catalog.latestBeta().htmlUrl());
            }

            if (autoDownload && (hasOfficial || hasBeta)) {
                plugin.getLogger().info("已啟用 updater.auto-download，正在背景下載更新檔案...");
                downloadUpdate(null, null, null);
            } else if (!hasOfficial && !hasBeta) {
                plugin.getLogger().info("目前已是最新版本 (v" + current + ")");
            }
        });
    }

    /**
     * 非同步獲取 Releases 列表並建立快取目錄
     */
    public void fetchReleases(boolean forceRefresh, Consumer<UpdateCatalog> onComplete) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedCatalog != null && (now - lastFetchTime < CACHE_TTL_MS)) {
            if (onComplete != null) onComplete.accept(cachedCatalog);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String repo = plugin.getConfig().getString("updater.repo", "Avery11111101/MiaoMenu_fork").trim();
                String apiUrl = "https://api.github.com/repos/" + repo + "/releases?per_page=15";

                var url = URI.create(apiUrl).toURL();
                var conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MiaoMenu_fork-Updater/" + plugin.getPluginMeta().getVersion());
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("獲取 GitHub Releases 失敗: HTTP " + responseCode);
                    if (onComplete != null) onComplete.accept(cachedCatalog);
                    return;
                }

                JsonArray array;
                try (var reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    array = new JsonParser().parse(reader).getAsJsonArray();
                }

                List<ReleaseInfo> list = new ArrayList<>();
                ReleaseInfo firstOfficial = null;
                ReleaseInfo firstBeta = null;
                ReleaseInfo currentMatch = null;

                String currentVer = plugin.getPluginMeta().getVersion().trim().toLowerCase();
                String cleanCurrent = currentVer.startsWith("v") ? currentVer.substring(1) : currentVer;

                for (JsonElement elem : array) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();

                    boolean draft = obj.has("draft") && obj.get("draft").getAsBoolean();
                    if (draft) continue;

                    boolean prerelease = obj.has("prerelease") && obj.get("prerelease").getAsBoolean();
                    String tagName = obj.has("tag_name") ? obj.get("tag_name").getAsString() : "";
                    String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : tagName;
                    String body = obj.has("body") && !obj.get("body").isJsonNull() ? obj.get("body").getAsString() : "";
                    String htmlUrl = obj.has("html_url") ? obj.get("html_url").getAsString() : "";
                    String publishedAt = obj.has("published_at") && !obj.get("published_at").isJsonNull()
                            ? obj.get("published_at").getAsString() : "";

                    String downloadUrl = null;
                    String assetName = null;
                    long assetSize = 0;

                    if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                        for (JsonElement a : obj.getAsJsonArray("assets")) {
                            if (a.isJsonObject()) {
                                var assetObj = a.getAsJsonObject();
                                String aname = assetObj.get("name").getAsString();
                                if (aname.endsWith(".jar")) {
                                    downloadUrl = assetObj.get("browser_download_url").getAsString();
                                    assetName = aname;
                                    assetSize = assetObj.get("size").getAsLong();
                                    break;
                                }
                            }
                        }
                    }

                    var release = new ReleaseInfo(tagName, name, body, htmlUrl, downloadUrl, assetName, assetSize, prerelease, publishedAt);
                    list.add(release);

                    if (!prerelease && firstOfficial == null) {
                        firstOfficial = release;
                    }
                    if (prerelease && firstBeta == null) {
                        firstBeta = release;
                    }

                    String cleanTag = tagName.trim().toLowerCase();
                    if (cleanTag.startsWith("v")) cleanTag = cleanTag.substring(1);
                    if (cleanTag.equalsIgnoreCase(cleanCurrent) && currentMatch == null) {
                        currentMatch = release;
                    }
                }

                UpdateCatalog catalog = new UpdateCatalog(firstOfficial, firstBeta, currentMatch, Collections.unmodifiableList(list));
                this.cachedCatalog = catalog;
                this.lastFetchTime = System.currentTimeMillis();

                if (onComplete != null) {
                    onComplete.accept(catalog);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("連線 GitHub 檢查更新發生錯誤: " + e.getMessage());
                if (onComplete != null) onComplete.accept(cachedCatalog);
            }
        });
    }

    /**
     * 檢查更新（雙重推播輸出）
     */
    public void checkForUpdates(boolean notifyIfLatest, CommandSender feedbackSender, Consumer<UpdateCatalog> onComplete) {
        if (isChecking.getAndSet(true)) {
            if (feedbackSender != null) {
                feedbackSender.sendMessage("§e[MiaoMenu] 正在連線 GitHub 檢查更新中，請稍候...");
            }
            return;
        }

        fetchReleases(true, catalog -> {
            isChecking.set(false);
            if (catalog == null) {
                if (feedbackSender != null) {
                    feedbackSender.sendMessage("§c[MiaoMenu] 無法連線至 GitHub 取得更新資訊，請稍後再試。");
                }
                if (onComplete != null) onComplete.accept(null);
                return;
            }

            String current = plugin.getPluginMeta().getVersion();
            boolean officialNewer = catalog.latestOfficial() != null && isNewerVersion(current, catalog.latestOfficial().tagName());
            boolean betaNewer = catalog.latestBeta() != null && isNewerVersion(current, catalog.latestBeta().tagName());

            if (feedbackSender != null) {
                feedbackSender.sendMessage("§6§l=================[ MiaoMenu 雙軌更新檢測 ]=================");
                feedbackSender.sendMessage("§7目前外掛版本: §f" + current);
                feedbackSender.sendMessage("");

                // 1. 最新正式發布版
                if (catalog.latestOfficial() != null) {
                    var off = catalog.latestOfficial();
                    if (officialNewer) {
                        feedbackSender.sendMessage("§a§l🌟 [最新正式穩定版] §a" + off.tagName() + " §7- §f" + off.name());
                        feedbackSender.sendMessage("  §7連結: §b" + off.htmlUrl());
                        showMarkdownSummary(feedbackSender, off.body(), 3);
                        feedbackSender.sendMessage("  §e👉 下載正式版: §f/dgm update download release");
                    } else {
                        feedbackSender.sendMessage("§7🌟 [最新正式穩定版] " + off.tagName() + " §a(目前已是最新正式版)");
                    }
                } else {
                    feedbackSender.sendMessage("§7🌟 [最新正式穩定版] 尚未發布正式版本");
                }
                feedbackSender.sendMessage("");

                // 2. 最新搶先測試版
                if (catalog.latestBeta() != null) {
                    var beta = catalog.latestBeta();
                    if (betaNewer) {
                        feedbackSender.sendMessage("§b§l🧪 [最新搶先測試版] §b" + beta.tagName() + " §7- §f" + beta.name());
                        feedbackSender.sendMessage("  §7連結: §b" + beta.htmlUrl());
                        showMarkdownSummary(feedbackSender, beta.body(), 3);
                        feedbackSender.sendMessage("  §e👉 下載測試版: §f/dgm update download beta");
                    } else {
                        feedbackSender.sendMessage("§7🧪 [最新搶先測試版] " + beta.tagName() + " §8(目前版本已高於或等於此測試版)");
                    }
                } else {
                    feedbackSender.sendMessage("§7🧪 [最新搶先測試版] 目前無測試版發布");
                }

                feedbackSender.sendMessage("");
                if (!officialNewer && !betaNewer) {
                    if (notifyIfLatest) {
                        feedbackSender.sendMessage("§a✔ 目前外掛已是最新版本，運作狀態良好！");
                    }
                } else {
                    feedbackSender.sendMessage("§7提示: 可依伺服器營運需求選擇穩定版 (release) 或搶先測試版 (beta) 更新。");
                }
                feedbackSender.sendMessage("§6§l====================================================");
            }

            if (onComplete != null) onComplete.accept(catalog);
        });
    }

    /**
     * 下載指定版本並安全取代外掛檔案
     *
     * @param channelChoice "release", "beta" 或 null (依 config 預設)
     */
    public void downloadUpdate(String channelChoice, CommandSender feedbackSender, Consumer<Boolean> onComplete) {
        fetchReleases(false, catalog -> {
            if (catalog == null) {
                if (feedbackSender != null) {
                    feedbackSender.sendMessage("§c[MiaoMenu] 無法取得 Releases 清單，請確認網路連線與 GitHub 設定。");
                }
                if (onComplete != null) onComplete.accept(false);
                return;
            }

            ReleaseInfo target = selectTargetRelease(catalog, channelChoice);
            if (target == null || target.downloadUrl() == null) {
                if (feedbackSender != null) {
                    feedbackSender.sendMessage("§c[MiaoMenu] 找不到符合指定條件且包含 .jar 資產的發布版本。");
                }
                if (onComplete != null) onComplete.accept(false);
                return;
            }

            if (isDownloading.getAndSet(true)) {
                if (feedbackSender != null) {
                    feedbackSender.sendMessage("§c[MiaoMenu] 檔案正在下載中，請稍候...");
                }
                if (onComplete != null) onComplete.accept(false);
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    String channelName = target.isPrerelease() ? "搶先測試版 🧪" : "正式穩定版 🌟";
                    if (feedbackSender != null) {
                        feedbackSender.sendMessage("§e[MiaoMenu] 開始下載 " + channelName + " (" + target.tagName() + ")...");
                    } else {
                        plugin.getLogger().info("開始下載 " + channelName + " (" + target.tagName() + ")...");
                    }

                    File pluginsFolder = plugin.getDataFolder().getParentFile();
                    File currentJar = plugin.getPluginFile();
                    String currentJarName = currentJar != null ? currentJar.getName() : "MiaoMenu_fork.jar";
                    String targetJarName = target.assetName() != null ? target.assetName() : currentJarName;

                    File tempFile = new File(pluginsFolder, ".miaomenu_download.part");
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }

                    // 1. 串流下載至臨時檔案
                    var url = URI.create(target.downloadUrl()).toURL();
                    var conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "MiaoMenu_fork-Updater/" + plugin.getPluginMeta().getVersion());
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(30000);

                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        conn = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                        conn.setRequestProperty("User-Agent", "MiaoMenu_fork-Updater/" + plugin.getPluginMeta().getVersion());
                    }

                    try (var in = new BufferedInputStream(conn.getInputStream());
                         var out = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        out.flush();
                    }

                    if (!tempFile.exists() || tempFile.length() == 0) {
                        throw new IllegalStateException("下載檔案為空或寫入失敗！");
                    }

                    // 2. 執行外掛檔案取代邏輯
                    boolean directReplaced = false;
                    boolean queuedForRestart = false;

                    if (currentJar != null && targetJarName.equalsIgnoreCase(currentJarName)) {
                        // 情況 A：新舊檔名相同
                        try {
                            Files.move(tempFile.toPath(), currentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            directReplaced = true;
                        } catch (Exception e) {
                            // Windows JVM 鎖定保護：放入 Bukkit 原生 plugins/update/ 目錄
                            File updateFolder = new File(pluginsFolder, "update");
                            if (!updateFolder.exists()) {
                                updateFolder.mkdirs();
                            }
                            File updateTarget = new File(updateFolder, currentJarName);
                            Files.move(tempFile.toPath(), updateTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            queuedForRestart = true;
                        }
                    } else {
                        // 情況 B：新舊檔名不同
                        File targetFile = new File(pluginsFolder, targetJarName);
                        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        directReplaced = true;

                        if (currentJar != null) {
                            boolean oldDeleted = currentJar.delete();
                            if (!oldDeleted) {
                                currentJar.deleteOnExit();
                                queuedForRestart = true;
                            }
                        }
                    }

                    // 3. 發送成果通知
                    if (directReplaced && !queuedForRestart) {
                        String msg = "§a[MiaoMenu] ✔ 成功下載並取代外掛檔案！\n"
                                + "§a[MiaoMenu] 已就緒檔案: §fplugins/" + targetJarName + "\n"
                                + "§6[MiaoMenu] 請於離峰時段重啟伺服器 (/restart 或 /stop)，新版本將完整載入！";
                        if (feedbackSender != null) feedbackSender.sendMessage(msg);
                        plugin.getLogger().info("新版本已直接替換外掛檔 plugins/" + targetJarName + "，請重啟伺服器生效。");
                    } else {
                        String msg = "§a[MiaoMenu] ✔ 成功下載新版本 " + target.tagName() + "！\n"
                                + "§a[MiaoMenu] 新檔案已寫入: §fplugins/" + targetJarName + "\n"
                                + "§e[MiaoMenu] 舊檔案已排定於伺服器關機時自動清除，避免多版本衝突。\n"
                                + "§6[MiaoMenu] 請於離峰時段重啟伺服器 (/restart) 完成版本升級套用！";
                        if (feedbackSender != null) feedbackSender.sendMessage(msg);
                        plugin.getLogger().info("新版本已就緒 (plugins/" + targetJarName + ")，舊檔已排定清除，請重啟伺服器以套用。");
                    }

                    if (onComplete != null) onComplete.accept(true);

                } catch (Exception e) {
                    String err = "§c[MiaoMenu] 下載或替換外掛失敗: " + e.getMessage();
                    if (feedbackSender != null) feedbackSender.sendMessage(err);
                    plugin.getLogger().severe("下載或替換外掛失敗: " + e.getMessage());
                    if (onComplete != null) onComplete.accept(false);
                } finally {
                    isDownloading.set(false);
                }
            });
        });
    }

    private ReleaseInfo selectTargetRelease(UpdateCatalog catalog, String channelChoice) {
        if (channelChoice != null) {
            String c = channelChoice.trim().toLowerCase();
            if (c.contains("beta") || c.contains("test") || c.contains("測試")) {
                return catalog.latestBeta();
            }
            if (c.contains("release") || c.contains("official") || c.contains("正式") || c.contains("stable")) {
                return catalog.latestOfficial();
            }
        }

        // 預設遵循 config.yml
        String pref = plugin.getConfig().getString("updater.channel", "RELEASE").trim().toUpperCase();
        String current = plugin.getPluginMeta().getVersion();

        if ("BETA".equals(pref)) {
            if (catalog.latestBeta() != null && isNewerVersion(current, catalog.latestBeta().tagName())) {
                return catalog.latestBeta();
            }
            if (catalog.latestOfficial() != null && isNewerVersion(current, catalog.latestOfficial().tagName())) {
                return catalog.latestOfficial();
            }
            return catalog.latestBeta() != null ? catalog.latestBeta() : catalog.latestOfficial();
        } else {
            if (catalog.latestOfficial() != null && isNewerVersion(current, catalog.latestOfficial().tagName())) {
                return catalog.latestOfficial();
            }
            if (catalog.latestBeta() != null && isNewerVersion(current, catalog.latestBeta().tagName())) {
                return catalog.latestBeta();
            }
            return catalog.latestOfficial() != null ? catalog.latestOfficial() : catalog.latestBeta();
        }
    }

    private void showMarkdownSummary(CommandSender sender, String markdown, int maxLines) {
        List<String> lines = formatMarkdown(markdown, maxLines);
        for (String line : lines) {
            sender.sendMessage("  " + line);
        }
    }

    /**
     * 將 GitHub Markdown 轉為 Minecraft 聊天色彩排版
     */
    public static List<String> formatMarkdown(String markdown, int maxLines) {
        if (markdown == null || markdown.isBlank()) {
            return List.of("§7  (無更新說明)");
        }

        List<String> output = new ArrayList<>();
        String[] rawLines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        int count = 0;
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("```") || trimmed.startsWith("---") || trimmed.startsWith("___")) {
                continue;
            }

            // 過濾 HTML 標籤
            trimmed = trimmed.replaceAll("<[^>]*>", "");
            if (trimmed.isBlank()) continue;

            // 粗體轉換 **text** -> §e§ltext§r§7
            trimmed = trimmed.replaceAll("\\*\\*([^*]+)\\*\\*", "§e§l$1§r§7");

            if (trimmed.startsWith("# ")) {
                output.add("§6§l=== " + trimmed.substring(2) + " ===");
            } else if (trimmed.startsWith("## ")) {
                output.add("§e§l▸ " + trimmed.substring(3));
            } else if (trimmed.startsWith("### ")) {
                output.add("§b§l  • " + trimmed.substring(4));
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                output.add("§7    • §f" + trimmed.substring(2));
            } else {
                output.add("§7    " + trimmed);
            }

            count++;
            if (count >= maxLines) {
                output.add("§8    ... (更多詳細資訊請點擊發布網址查閱)");
                break;
            }
        }

        return output.isEmpty() ? List.of("§7  (無更新說明)") : output;
    }

    /**
     * 語意化版本比對 (SemVer comparison)
     * @return true 若 latestStr 較 currentStr 新
     */
    public static boolean isNewerVersion(String currentStr, String latestStr) {
        if (currentStr == null || latestStr == null) return false;

        String c = currentStr.trim().toLowerCase();
        String l = latestStr.trim().toLowerCase();

        if (c.startsWith("v")) c = c.substring(1);
        if (l.startsWith("v")) l = l.substring(1);

        if (c.equalsIgnoreCase(l)) return false;

        String[] cParts = c.split("-", 2);
        String[] lParts = l.split("-", 2);

        String[] cCore = cParts[0].split("\\.");
        String[] lCore = lParts[0].split("\\.");

        int maxLen = Math.max(cCore.length, lCore.length);
        for (int i = 0; i < maxLen; i++) {
            int cNum = 0;
            int lNum = 0;
            if (i < cCore.length) {
                try { cNum = Integer.parseInt(cCore[i]); } catch (NumberFormatException ignored) {}
            }
            if (i < lCore.length) {
                try { lNum = Integer.parseInt(lCore[i]); } catch (NumberFormatException ignored) {}
            }
            if (lNum > cNum) return true;
            if (lNum < cNum) return false;
        }

        // 若核心版本號相同（例如 1.8.0 與 1.8.0-beta.2）
        // 正式發布版 (無預發布後綴) 比任何預發布版 (beta / rc) 都新
        boolean cHasPre = cParts.length > 1;
        boolean lHasPre = lParts.length > 1;

        if (cHasPre && !lHasPre) {
            return true; // 例如 current = 1.8.0-beta.2, latest = 1.8.0 -> latest 正式版較新
        }
        if (!cHasPre && lHasPre) {
            return false; // 例如 current = 1.8.0, latest = 1.8.0-beta.3 -> current 已是正式版
        }

        if (cHasPre && lHasPre) {
            // 兩者皆有預發布標籤，進行自然數字序號解析比較（如 beta.2 vs beta.10）
            return comparePreRelease(cParts[1], lParts[1]) < 0;
        }

        return false;
    }

    private static int comparePreRelease(String pre1, String pre2) {
        String[] p1 = pre1.split("\\.");
        String[] p2 = pre2.split("\\.");
        int len = Math.max(p1.length, p2.length);

        for (int i = 0; i < len; i++) {
            if (i >= p1.length) return -1;
            if (i >= p2.length) return 1;

            String s1 = p1[i];
            String s2 = p2[i];

            boolean s1IsNum = s1.matches("\\d+");
            boolean s2IsNum = s2.matches("\\d+");

            if (s1IsNum && s2IsNum) {
                int n1 = Integer.parseInt(s1);
                int n2 = Integer.parseInt(s2);
                if (n1 != n2) return Integer.compare(n1, n2);
            } else {
                int cmp = s1.compareToIgnoreCase(s2);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }
}

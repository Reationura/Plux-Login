package com.plux.login;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class UpdateChecker {
    private final PluxLogin plugin;
    private final Pattern versionPattern = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    // 缓存最新版本号，避免每次玩家登录都发起一次 HTTP 请求
    private volatile String latestVersion;

    public UpdateChecker(PluxLogin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!this.plugin.getConfigManager().isUpdateCheckerEnabled()) {
            return;
        }
        this.checkForUpdates();
        int interval = 72000;
        new BukkitRunnable(){

            public void run() {
                UpdateChecker.this.checkForUpdates();
            }
        }.runTaskTimerAsynchronously((Plugin)this.plugin, (long)interval, (long)interval);
    }

    public void checkForUpdates() {
        try {
            String currentVersion = this.plugin.getVersion();
            String latestVersion = this.fetchLatestVersion();
            if (latestVersion != null) {
                this.latestVersion = latestVersion;
            }
            if (latestVersion != null && this.isNewerVersion(latestVersion, currentVersion)) {
                this.plugin.getLogger().info("============== PluxLogin ==============");
                this.plugin.getLogger().info("检测到有新版本可用，当前使用版本: " + currentVersion + "，最新版本: " + latestVersion);
                this.plugin.getLogger().info("请前往Minebbs.com/icecloudplux.github.io进行下载");
                this.plugin.getLogger().info("===================================");
                this.sendUpdateNotificationToAdmins(currentVersion, latestVersion);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("\u68c0\u67e5\u66f4\u65b0\u65f6\u51fa\u9519: " + e.getMessage());
        }
    }

    public void sendUpdateNotificationToAdmins(String currentVersion, String latestVersion) {
        // 本方法可能从异步定时器调起，玩家消息发送需回到主线程，避免跨线程操作实体
        this.plugin.getServer().getScheduler().runTask((Plugin) this.plugin, () -> {
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                if (!player.hasPermission("pluxlogin.admin")) continue;
                player.sendMessage("============== PluxLogin ==============");
                player.sendMessage("§e检测到有新版本可用，当前使用版本: §a" + currentVersion + "§e，最新版本: §a" + latestVersion);
                player.sendMessage("§e请前往Minebbs.com/icecloudplux.github.io进行下载");
                player.sendMessage("===================================");
            }
        });
    }

    public void notifyAdminOnLogin(Player player, String currentVersion, String latestVersion) {
        if (latestVersion == null || !this.isNewerVersion(latestVersion, currentVersion)) return;
        // 版本判断在异步线程完成，真正的消息发送回到主线程
        this.plugin.getServer().getScheduler().runTask((Plugin) this.plugin, () -> {
            if (!player.isOnline() || !player.hasPermission("pluxlogin.admin")) return;
            player.sendMessage("============== PluxLogin ==============");
            player.sendMessage("§e检测到有新版本可用，当前使用版本: §a" + currentVersion + "§e，最新版本: §a" + latestVersion);
            player.sendMessage("§e请前往Minebbs.com/icecloudplux.github.io进行下载");
            player.sendMessage("===================================");
        });
    }

    public String getLatestVersion() {
        return this.latestVersion;
    }

    public String fetchLatestVersion() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://gitee.com/cllupdate/pluginupdate/raw/master/lcloginversion.txt");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "PluxLogin-UpdateChecker");
            connection.setRequestProperty("Accept", "text/plain");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                String version;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    version = (line == null) ? null : line.trim();
                }
                if (version != null && this.versionPattern.matcher(version).matches()) {
                    return version;
                }
                this.plugin.getLogger().warning("\u7248\u672c\u53f7\u683c\u5f0f\u4e0d\u6b63\u786e: " + version);
            } else {
                this.plugin.getLogger().warning("\u66f4\u65b0\u68c0\u67e5\u5931\u8d25\uff0c\u54cd\u5e94\u7801: " + responseCode);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("\u83b7\u53d6\u6700\u65b0\u7248\u672c\u65f6\u51fa\u9519: " + e.getMessage());
        }
        finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        try {
            String[] latestParts = latestVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");
            int i = 0;
            while (true) {
                if (i >= Math.max(latestParts.length, currentParts.length)) {
                    return false;
                }
                int latest = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (latest > current) {
                    return true;
                }
                if (latest < current) {
                    return false;
                }
                ++i;
            }
        }
        catch (NumberFormatException e) {
            return false;
        }
    }
}
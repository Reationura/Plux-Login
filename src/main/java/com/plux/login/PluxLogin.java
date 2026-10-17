package com.plux.login;

import com.plux.login.adapter.NMSAdapter;
import com.plux.login.adapter.NMSAdapterFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

public class PluxLogin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CaptchaManager captchaManager;
    private EmailManager emailManager;
    private TOTPManager totpManager;
    private PlayerListener playerListener;
    private CommandHandler commandHandler;
    private UpdateChecker updateChecker;
    private WebApiServer webApiServer;
    private NMSAdapter nmsAdapter;

    // 会话数据 - 全部使用 ConcurrentHashMap 保证线程安全
    private final Map<UUID, PlayerSession> playerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBinding> pendingEmailBindings = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBinding> pendingQQBindings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> wrongPasswordCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kickCount = new ConcurrentHashMap<>();
    private final Map<UUID, PasswordResetVerification> pendingPasswordResets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fastJoinCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tempBannedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActionTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingTOTPVerifications = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        nmsAdapter = NMSAdapterFactory.createAdapter(getLogger());

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        captchaManager = new CaptchaManager(this);
        emailManager = new EmailManager(this);
        totpManager = new TOTPManager();

        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents((Listener) playerListener, (Plugin) this);

        commandHandler = new CommandHandler(this);
        commandHandler.registerCommands();

        updateChecker = new UpdateChecker(this);
        updateChecker.start();

        // 启动 Web API 服务（如果启用）
        if (configManager.isWebApiEnabled()) {
            webApiServer = new WebApiServer(this);
            webApiServer.start();
        }

        String enableMessage = configManager.getPluginEnableMessage().replace("{version}", getVersion());
        getLogger().info(Utils.translateColorCodes(enableMessage));
    }

    @Override
    public void onDisable() {
        // 停止 Web API
        if (webApiServer != null) {
            webApiServer.stop();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        // 一次性清理所有缓存
        playerSessions.clear();
        pendingEmailBindings.clear();
        pendingQQBindings.clear();
        wrongPasswordCount.clear();
        kickCount.clear();
        pendingPasswordResets.clear();
        joinTimestamps.clear();
        fastJoinCount.clear();
        tempBannedUntil.clear();
        lastActionTimestamps.clear();
        pendingTOTPVerifications.clear();

        if (configManager != null) {
            getLogger().info(Utils.translateColorCodes(configManager.getPluginDisableMessage()));
        }
    }

    // ========== Session 管理 ==========

    public PlayerSession getPlayerSession(UUID uuid) {
        return playerSessions.get(uuid);
    }

    public void createPlayerSession(UUID uuid, PlayerSession session) {
        playerSessions.put(uuid, session);
    }

    public void setPlayerSession(UUID uuid, PlayerSession session) {
        playerSessions.put(uuid, session);
    }

    public void removePlayerSession(UUID uuid) {
        playerSessions.remove(uuid);
    }

    // ========== 邮箱绑定待验证 ==========

    public PendingBinding getPendingEmailBinding(UUID uuid) {
        return pendingEmailBindings.get(uuid);
    }

    public void createPendingEmailBinding(UUID uuid, PendingBinding binding) {
        pendingEmailBindings.put(uuid, binding);
    }

    public void removePendingEmailBinding(UUID uuid) {
        pendingEmailBindings.remove(uuid);
    }

    // ========== QQ 绑定待验证 ==========

    public PendingBinding getPendingQQBinding(UUID uuid) {
        return pendingQQBindings.get(uuid);
    }

    public void createPendingQQBinding(UUID uuid, PendingBinding binding) {
        pendingQQBindings.put(uuid, binding);
    }

    public void removePendingQQBinding(UUID uuid) {
        pendingQQBindings.remove(uuid);
    }

    // ========== 密码错误计数 ==========

    public int getWrongPasswordCount(UUID uuid) {
        return wrongPasswordCount.getOrDefault(uuid, 0);
    }

    public void incrementWrongPasswordCount(UUID uuid) {
        wrongPasswordCount.merge(uuid, 1, Integer::sum);
    }

    public void resetWrongPasswordCount(UUID uuid) {
        wrongPasswordCount.remove(uuid);
    }

    // ========== 踢出计数 ==========

    public int getKickCount(UUID uuid) {
        return kickCount.getOrDefault(uuid, 0);
    }

    public void incrementKickCount(UUID uuid) {
        kickCount.merge(uuid, 1, Integer::sum);
    }

    public void resetKickCount(UUID uuid) {
        kickCount.remove(uuid);
    }

    public void executeKickCommands(String playerName) {
        for (String cmd : configManager.getKickCommands()) {
            // 同时支持 {player} 与 %player% 两种占位符写法
            String formattedCmd = cmd.replace("{player}", playerName).replace("%player%", playerName);
            getServer().dispatchCommand((CommandSender) getServer().getConsoleSender(), formattedCmd);
        }
    }

    // ========== 密码重置待验证 ==========

    public PasswordResetVerification getPendingPasswordReset(UUID uuid) {
        return pendingPasswordResets.get(uuid);
    }

    public void createPendingPasswordReset(UUID uuid, PasswordResetVerification verification) {
        pendingPasswordResets.put(uuid, verification);
    }

    public void removePendingPasswordReset(UUID uuid) {
        pendingPasswordResets.remove(uuid);
    }

    // ========== 快速加入检测 / 临时封禁 ==========

    public boolean isTempBanned(UUID uuid) {
        Long bannedUntil = tempBannedUntil.get(uuid);
        if (bannedUntil == null) return false;
        if (System.currentTimeMillis() > bannedUntil) {
            tempBannedUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public Long getTempBannedUntil(UUID uuid) { return tempBannedUntil.get(uuid); }

    public void setTempBannedUntil(UUID uuid, long time) { tempBannedUntil.put(uuid, time); }

    public String getTempBanRemainingTime(UUID uuid) {
        Long bannedUntil = tempBannedUntil.get(uuid);
        if (bannedUntil == null) return "0";
        long remaining = (bannedUntil - System.currentTimeMillis()) / 1000L;
        return remaining > 0L ? String.valueOf(remaining) : "0";
    }

    public Long getJoinTimestamp(UUID uuid) { return joinTimestamps.get(uuid); }

    public void setJoinTimestamp(UUID uuid, long time) { joinTimestamps.put(uuid, time); }

    public int getFastJoinCount(UUID uuid) { return fastJoinCount.getOrDefault(uuid, 0); }

    public int incrementFastJoinCount(UUID uuid) { return fastJoinCount.merge(uuid, 1, Integer::sum); }

    public void resetFastJoinCount(UUID uuid) { fastJoinCount.remove(uuid); }

    public void resetJoinTimestamp(UUID uuid) {
        joinTimestamps.remove(uuid);
        fastJoinCount.remove(uuid);
    }

    // ========== 全局操作冷却 ==========

    public void removeLastActionTimestamp(UUID uuid) { lastActionTimestamps.remove(uuid); }

    public boolean canPerformAction(UUID uuid) {
        Long lastAction = lastActionTimestamps.get(uuid);
        long now = System.currentTimeMillis();
        int cooldown = configManager.getGlobalActionCooldown();
        if (lastAction != null && now - lastAction < (long) cooldown * 1000L) {
            return false;
        }
        lastActionTimestamps.put(uuid, now);
        return true;
    }

    public int getActionCooldownRemaining(UUID uuid) {
        Long lastAction = lastActionTimestamps.get(uuid);
        if (lastAction == null) return 0;
        long remaining = ((long) configManager.getGlobalActionCooldown() * 1000L - (System.currentTimeMillis() - lastAction)) / 1000L;
        return remaining > 0L ? (int) remaining : 0;
    }

    // ========== TOTP/2FA 验证 ==========

    public boolean isPendingTOTPVerification(UUID uuid) {
        return pendingTOTPVerifications.containsKey(uuid);
    }

    public void setPendingTOTPVerification(UUID uuid) {
        pendingTOTPVerifications.put(uuid, System.currentTimeMillis());
    }

    public void removePendingTOTPVerification(UUID uuid) {
        pendingTOTPVerifications.remove(uuid);
    }

    // ========== 强制绑定邮箱检查 ==========

    public boolean isForceEmailEnabled() {
        return configManager.isForceEmailEnabled();
    }

    public boolean shouldForceEmailBind(PlayerData playerData) {
        return configManager.isForceEmailEnabled()
                && (playerData.getEmail() == null || playerData.getEmail().isEmpty());
    }

    // ========== Getter ==========

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public CaptchaManager getCaptchaManager() { return captchaManager; }
    public EmailManager getEmailManager() { return emailManager; }
    public TOTPManager getTOTPManager() { return totpManager; }
    public NMSAdapter getNMSAdapter() { return nmsAdapter; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public WebApiServer getWebApiServer() { return webApiServer; }

    public String getVersion() { return getDescription().getVersion(); }

    public java.util.List<String> getAllowedCommands() { return configManager.getAllowedCommands(); }

    // ========== 内部数据类 ==========

    public static class PasswordResetVerification {
        private final String type;
        private final String value;
        private final String code;
        private final long timestamp;

        public PasswordResetVerification(String type, String value, String code) {
            this.type = type;
            this.value = value;
            this.code = code;
            this.timestamp = System.currentTimeMillis();
        }

        public String getType() { return type; }
        public String getValue() { return value; }
        public String getCode() { return code; }
        public long getTimestamp() { return timestamp; }

        public boolean isExpired(long validityTime) {
            return System.currentTimeMillis() - timestamp > validityTime * 1000L;
        }
    }

    public static class PendingBinding {
        private final String value;
        private final String code;
        private final long timestamp;

        public PendingBinding(String value, String code) {
            this.value = value;
            this.code = code;
            this.timestamp = System.currentTimeMillis();
        }

        public String getValue() { return value; }
        public String getCode() { return code; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PlayerSession {
        private final UUID uuid;
        private final String playerName;
        private final long joinTime;
        private volatile boolean loggedIn;
        private volatile boolean captchaVerified;
        private volatile SessionState state;
        private int titleTaskId = -1;
        private int actionbarTaskId = -1;
        private volatile int remainingTime;

        public PlayerSession(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.joinTime = System.currentTimeMillis();
            this.loggedIn = false;
            this.captchaVerified = false;
            this.state = SessionState.CAPTCHA;
            this.remainingTime = 0;
        }

        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public long getJoinTime() { return joinTime; }
        public boolean isLoggedIn() { return loggedIn; }
        public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
        public boolean isCaptchaVerified() { return captchaVerified; }
        public void setCaptchaVerified(boolean captchaVerified) { this.captchaVerified = captchaVerified; }
        public SessionState getState() { return state; }
        public void setState(SessionState state) { this.state = state; }
        public int getTitleTaskId() { return titleTaskId; }
        public void setTitleTaskId(int taskId) { this.titleTaskId = taskId; }
        public int getActionbarTaskId() { return actionbarTaskId; }
        public void setActionbarTaskId(int taskId) { this.actionbarTaskId = taskId; }
        public int getRemainingTime() { return remainingTime; }
        public void setRemainingTime(int remainingTime) { this.remainingTime = remainingTime; }

        public void cancelTasks(Plugin plugin) {
            if (titleTaskId > 0) {
                plugin.getServer().getScheduler().cancelTask(titleTaskId);
                titleTaskId = -1;
            }
            if (actionbarTaskId > 0) {
                plugin.getServer().getScheduler().cancelTask(actionbarTaskId);
                actionbarTaskId = -1;
            }
        }
    }

    public enum SessionState {
        CAPTCHA,
        REGISTER,
        LOGIN,
        FORCE_EMAIL,   // 强制绑定邮箱状态
        TOTP_VERIFY     // 2FA 验证状态
    }
}

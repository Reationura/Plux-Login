package com.plux.login;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ConfigManager {

    private final PluxLogin plugin;
    private FileConfiguration config;
    private FileConfiguration messageConfig;
    private File messageFile;
    private File welcomeFile;
    private List<String> welcomeMessages;
    private List<String> rawWelcomeLines;
    private File joinFile;
    private List<String> rawJoinLines;

    public ConfigManager(PluxLogin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadMessageConfig();
        loadWelcomeFile();
        loadJoinFile();
    }

    // ========== 文件加载 ==========

    private void loadMessageConfig() {
        messageFile = new File(plugin.getDataFolder(), "message.yml");
        if (!messageFile.exists()) {
            plugin.saveResource("message.yml", false);
        }
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
    }

    private void loadWelcomeFile() {
        welcomeFile = new File(plugin.getDataFolder(), "welcome.txt");
        if (!welcomeFile.exists()) {
            plugin.saveResource("welcome.txt", false);
        }
        loadWelcomeMessages();
    }

    private void loadWelcomeMessages() {
        welcomeMessages = new ArrayList<>();
        rawWelcomeLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(welcomeFile.toPath(), new OpenOption[0]), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
                rawWelcomeLines.add(line);
                welcomeMessages.add(Utils.translateColorCodes(line));
            }
        } catch (IOException e) {
            plugin.getLogger().severe("无法加载 welcome.txt: " + e.getMessage());
        }
    }

    private void loadJoinFile() {
        joinFile = new File(plugin.getDataFolder(), "join.txt");
        if (!joinFile.exists()) {
            plugin.saveResource("join.txt", false);
        }
        loadJoinMessages();
    }

    private void loadJoinMessages() {
        rawJoinLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(joinFile.toPath(), new OpenOption[0]), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
                rawJoinLines.add(line);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("无法加载 join.txt: " + e.getMessage());
        }
    }

    // ========== 通用消息获取（核心优化：用统一方法替代几十个重复getter）==========

    public String msg(String path) {
        return Utils.translateColorCodes(messageConfig.getString(path, ""));
    }

    public String msg(String path, String defaultValue) {
        return Utils.translateColorCodes(messageConfig.getString(path, defaultValue));
    }

    public String msg(String path, String placeholder, String value) {
        return msg(path).replace(placeholder, value);
    }

    public String prefix() {
        return getMessagePrefix();
    }

    public String getMessagePrefix() {
        String prefix = messageConfig.getString("prefix");
        if (prefix == null || prefix.isEmpty()) return "";
        return Utils.translateColorCodes(prefix);
    }

    // ========== 登录消息 ==========

    public String getLoginSuccessMessage() { return msg("login.success"); }
    public String getLoginWrongPasswordMessage(int remaining) { return msg("login.wrong-password", "{remaining}", String.valueOf(remaining)); }
    public String getLoginNotRegisteredMessage() { return msg("login.not-registered"); }
    public String getLoginAlreadyLoggedInMessage() { return msg("login.already-logged-in"); }
    public String getLoginAutoLoginMessage() { return msg("login.auto-login"); }
    public String getLoginNeedLoginMessage() { return msg("login.need-login"); }
    public String getLoginTimeoutKickMessage() { return msg("login.timeout-kick"); }
    public String getLoginMaxWrongKickMessage() { return msg("login.max-wrong-kick"); }
    public String getLoginLogoutSuccessMessage() { return msg("login.logout-success"); }

    // ========== 注册消息 ==========

    public String getRegisterSuccessMessage() { return msg("register.success"); }
    public String getRegisterPasswordMismatchMessage() { return msg("register.password-mismatch"); }
    public String getRegisterPasswordTooShortMessage(int min) { return msg("register.password-too-short", "{min}", String.valueOf(min)); }
    public String getRegisterPasswordTooLongMessage(int max) { return msg("register.password-too-long", "{max}", String.valueOf(max)); }
    public String getRegisterPasswordInvalidMessage() { return msg("register.password-invalid"); }
    public String getRegisterAlreadyRegisteredMessage() { return msg("register.already-registered"); }
    public String getRegisterNeedCaptchaMessage() { return msg("register.need-captcha"); }
    public String getRegisterCaptchaWrongMessage() { return msg("register.captcha-wrong"); }
    public String getRegisterCaptchaExpiredMessage() { return msg("register.captcha-expired"); }
    public String getRegisterTimeoutKickMessage() { return msg("register.timeout-kick"); }

    // ========== 验证码消息 ==========

    public String getCaptchaMessage(String captcha) { return msg("captcha.message", "{code}", captcha); }
    public String getCaptchaPromptMessage() { return msg("captcha.prompt"); }
    public String getCaptchaSuccessMessage() { return msg("captcha.success"); }

    // ========== 邮箱消息 ==========

    public String getEmailBindSuccessMessage() { return msg("email.bind-success"); }
    public String getEmailBindFailedMessage() { return msg("email.bind-failed"); }
    public String getEmailAlreadyBoundMessage() { return msg("email.already-bound"); }
    public String getEmailCodeSentMessage() { return msg("email.code-sent"); }
    public String getEmailCodeWrongMessage() { return msg("email.code-wrong"); }
    public String getEmailCodeExpiredMessage() { return msg("email.code-expired"); }
    public String getEmailCooldownMessage(int time) { return msg("email.cooldown", "{time}", String.valueOf(time)); }
    public String getEmailNeedVerifyMessage() { return msg("email.need-verify"); }
    public String getEmailNotBoundMessage() { return msg("email.not-bound"); }
    public String getEmailInvalidMessage() { return msg("email.invalid"); }
    public String getEmailNoPendingMessage() { return msg("email.no-pending"); }

    // ========== QQ 消息 ==========

    public String getQQBindSuccessMessage() { return msg("qq.bind-success"); }
    public String getQQBindFailedMessage() { return msg("qq.bind-failed"); }
    public String getQQAlreadyBoundMessage() { return msg("qq.already-bound"); }
    public String getQQCodeSentMessage() { return msg("qq.code-sent"); }
    public String getQQCodeWrongMessage() { return msg("qq.code-wrong"); }
    public String getQQCodeExpiredMessage() { return msg("qq.code-expired"); }
    public String getQQCooldownMessage(int time) { return msg("qq.cooldown", "{time}", String.valueOf(time)); }
    public String getQQNeedVerifyMessage() { return msg("qq.need-verify"); }
    public String getQQNotBoundMessage() { return msg("qq.not-bound"); }
    public String getQQInvalidMessage() { return msg("qq.invalid"); }
    public String getQQNoPendingMessage() { return msg("qq.no-pending"); }

    // ========== 限制消息 ==========

    public String getChatDeniedMessage() { return msg("restrictions.chat-denied"); }
    public String getCommandDeniedMessage() { return msg("restrictions.command-denied"); }
    public String getMoveDeniedMessage() { return msg("restrictions.move-denied"); }
    public String getInteractDeniedMessage() { return msg("restrictions.interact-denied"); }
    public String getBreakDeniedMessage() { return msg("restrictions.break-denied"); }
    public String getPlaceDeniedMessage() { return msg("restrictions.place-denied"); }
    public String getDamageDeniedMessage() { return msg("restrictions.damage-denied"); }
    public String getInventoryDeniedMessage() { return msg("restrictions.inventory-denied"); }

    // ========== 错误消息 ==========

    public String getErrorDatabaseErrorMessage() { return msg("error.database-error"); }
    public String getErrorEmailErrorMessage() { return msg("error.email-error"); }
    public String getErrorUnknownErrorMessage() { return msg("error.unknown-error"); }
    public String getErrorPlayerOnlyMessage() { return msg("error.player-only"); }
    public String getErrorNeedLoginFirstMessage() { return msg("error.need-login-first"); }
    public String getErrorNoPermissionMessage() { return msg("error.no-permission"); }
    public String getErrorPlayerNotFoundMessage() { return msg("error.player-not-found"); }

    // ========== 密码重置消息 ==========

    public String getPasswordResetEmailCodeSentMessage() { return msg("password-reset.email-code-sent"); }
    public String getPasswordResetQQCodeSentMessage() { return msg("password-reset.qq-code-sent"); }
    public String getPasswordResetVerifySuccessMessage() { return msg("password-reset.verify-success"); }
    public String getPasswordResetNoBindingMessage(String method) { return msg("password-reset.no-binding", "{method}", method); }
    public String getPasswordResetInvalidMethodMessage() { return msg("password-reset.invalid-method"); }

    // ========== 修改密码/邮箱/QQ 消息 ==========

    public String getChangePasswordSuccessMessage() { return msg("change-password.success"); }
    public String getChangePasswordWrongOldMessage() { return msg("change-password.wrong-old-password"); }
    public String getChangePasswordSameMessage() { return msg("change-password.same-password"); }
    public String getChangePasswordNeedLoginMessage() { return msg("change-password.need-login"); }

    public String getChangeEmailSuccessMessage() { return msg("change-email.success"); }
    public String getChangeEmailWrongOldMessage() { return msg("change-email.wrong-old-email"); }
    public String getChangeEmailCodeSentMessage() { return msg("change-email.code-sent"); }
    public String getChangeEmailVerifySuccessMessage(String email) { return msg("change-email.verify-success", "{email}", email); }
    public String getChangeEmailSameMessage() { return msg("change-email.same-email"); }
    public String getChangeEmailNeedLoginMessage() { return msg("change-email.need-login"); }
    public String getChangeEmailNeedVerifyFirstMessage() { return msg("change-email.need-verify-first"); }

    public String getChangeQQSuccessMessage() { return msg("change-qq.success"); }
    public String getChangeQQWrongOldMessage() { return msg("change-qq.wrong-old-qq"); }
    public String getChangeQQCodeSentMessage() { return msg("change-qq.code-sent"); }
    public String getChangeQQVerifySuccessMessage(String qq) { return msg("change-qq.verify-success", "{qq}", qq); }
    public String getChangeQQSameMessage() { return msg("change-qq.same-qq"); }
    public String getChangeQQNeedLoginMessage() { return msg("change-qq.need-login"); }
    public String getChangeQQNeedVerifyFirstMessage() { return msg("change-qq.need-verify-first"); }

    // ========== 主命令消息 ==========

    public String getMainCommandHelpHeader() { return msg("main-command.help-header"); }
    public String getMainCommandHelpFooter() { return msg("main-command.help-footer"); }
    public String getMainCommandReloadSuccess() { return msg("main-command.reload-success"); }
    public String getMainCommandUpdateChecking() { return msg("main-command.update-checking"); }
    public String getMainCommandUpdateCheckComplete() { return msg("main-command.update-check-complete"); }
    // 保留 {version} 占位符，由调用方（/pluxlogin version）传入真实版本号，避免被提前替换为 UNKNOWN
    public String getMainCommandVersion() { return msg("main-command.version"); }

    // ========== TOTP 消息 ==========

    public String getTotpEnabledMessage() { return msg("totp.enabled"); }
    public String getTotpDisabledMessage() { return msg("totp.disabled"); }
    public String getTotpQrCodeMessage(String url) { return msg("totp.qr-code", "{url}", url); }
    public String getTotpCodeSentMessage() { return msg("totp.code-sent"); }
    public String getTotpCodeWrongMessage() { return msg("totp.code-wrong"); }
    public String getTotpCodeExpiredMessage() { return msg("totp.code-expired"); }
    public String getTotpCooldownMessage(int time) { return msg("totp.cooldown", "{time}", String.valueOf(time)); }
    public String getTotpNeedVerifyMessage() { return msg("totp.need-verify"); }
    public String getTotpAlreadyEnabledMessage() { return msg("totp.already-enabled"); }
    public String getTotpNotEnabledMessage() { return msg("totp.not-enabled"); }
    public String getTotpNeedLoginMessage() { return msg("totp.need-login"); }
    public String getTotpVerifySuccessMessage() { return msg("totp.verify-success"); }
    public String getTotpVerifyFailedMessage() { return msg("totp.verify-failed"); }
    public String getTotpUsageMessage() { return msg("totp.usage"); }
    public String getTotpUsageConfirmMessage() { return msg("totp.usage-c"); }

    // ========== 插件启停消息 ==========

    public String getPluginEnableMessage() { return msg("plugin.enable.message"); }
    public String getPluginDisableMessage() { return msg("plugin.disable.message"); }

    // ========== 全局冷却消息 ==========

    public String getGlobalCooldownMessage(int time) { return msg("global.cooldown", "{time}", String.valueOf(time)); }

    // ========== 强制邮箱绑定消息 ==========

    public String getForceEmailRequiredMessage() { return msg("force-email.required"); }
    public String getForceEmailPromptMessage() { return msg("force-email.prompt"); }
    public String getForceEmailKickMessage() { return msg("force-email.kick-message"); }

    // ========== 用法提示 ==========

    public String getUsageLoginMessage() { return msg("misc.usage-login"); }
    public String getUsageRegisterMessage() { return msg("misc.usage-register"); }
    public String getUsageCaptchaMessage() { return msg("misc.usage-captcha"); }
    public String getUsageMailMessage() { return msg("misc.usage-mail"); }
    public String getUsageMailcMessage() { return msg("misc.usage-mailc"); }
    public String getUsageQQMessage() { return msg("misc.usage-qq"); }
    public String getUsageQQcMessage() { return msg("misc.usage-qqc"); }
    public String getUsageLogoutMessage() { return msg("misc.usage-logout"); }
    public String getUsageRegdelMessage() { return msg("misc.usage-regdel"); }
    public String getUsageMailzhpassMessage() { return msg("misc.usage-mailzhpass"); }
    public String getUsageQqzhpassMessage() { return msg("misc.usage-qqzhpass"); }
    public String getUsageChangepassMessage() { return msg("misc.usage-changepass"); }
    public String getUsageChangemailMessage() { return msg("misc.usage-changemail"); }
    public String getUsageChangeqqMessage() { return msg("misc.usage-changeqq"); }

    // ========== 管理员消息 ==========

    public String getAdminRegdelSuccessMessage(String player) { return msg("admin.regdel-success", "{player}", player); }
    public String getAdminRegdelFailedMessage() { return msg("admin.regdel-failed"); }

    // ========== Title / Actionbar 配置 ==========

    public boolean isTitleEnabled() { return config.getBoolean("display.title.enabled", true); }
    public int getTitleInterval() { return config.getInt("display.title.interval", 3); }

    public String getCaptchaTitle() { return Utils.translateColorCodes(config.getString("titles.captcha.title", "&e请输入验证码")); }
    public String getCaptchaSubtitle() { return Utils.translateColorCodes(config.getString("titles.captcha.subtitle", "&6使用 /captcha <验证码>")); }
    public String getCaptchaSubtitle(int remaining) { return getCaptchaSubtitle().replace("{time}", String.valueOf(remaining)); }
    public String getRegisterTitle() { return Utils.translateColorCodes(config.getString("titles.register.title", "&e请注册账号")); }
    public String getRegisterSubtitle() { return Utils.translateColorCodes(config.getString("titles.register.subtitle", "&6使用 /register <密码> <重复密码>")); }
    public String getRegisterSubtitle(int remaining) { return getRegisterSubtitle().replace("{time}", String.valueOf(remaining)); }
    public String getLoginTitle() { return Utils.translateColorCodes(config.getString("titles.login.title", "&e请登录")); }
    public String getLoginSubtitle() { return Utils.translateColorCodes(config.getString("titles.login.subtitle", "&6使用 /login <密码>")); }
    public String getLoginSubtitle(int remaining) { return getLoginSubtitle().replace("{time}", String.valueOf(remaining)); }

    public String getCaptchaActionbar(int remaining) { return Utils.translateColorCodes(config.getString("actionbar.captcha", "&e剩余时间: &6{time} &e| 请输入验证码").replace("{time}", String.valueOf(remaining))); }
    public String getRegisterActionbar(int remaining) { return Utils.translateColorCodes(config.getString("actionbar.register", "&e剩余时间: &6{time} &e| 请注册账号").replace("{time}", String.valueOf(remaining))); }
    public String getLoginActionbar(int remaining) { return Utils.translateColorCodes(config.getString("actionbar.login", "&e剩余时间: &6{time} &e| 请登录").replace("{time}", String.valueOf(remaining))); }

    public String getLoginSuccessTitle() { return Utils.translateColorCodes(config.getString("titles.login-success.title", "&a登录成功")); }
    public String getLoginSuccessSubtitle() { return Utils.translateColorCodes(config.getString("titles.login-success.subtitle", "&a欢迎回来！")); }
    public String getRegisterSuccessTitle() { return Utils.translateColorCodes(config.getString("titles.register-success.title", "&a注册成功")); }
    public String getRegisterSuccessSubtitle() { return Utils.translateColorCodes(config.getString("titles.register-success.subtitle", "&a欢迎加入服务器！")); }

    // ========== 功能开关 ==========

    public boolean isCaptchaTitleEnabled() { return config.getBoolean("titles.captcha.enabled", true); }
    public boolean isRegisterTitleEnabled() { return config.getBoolean("titles.register.enabled", true); }
    public boolean isLoginTitleEnabled() { return config.getBoolean("titles.login.enabled", true); }
    public boolean isLoginSuccessTitleEnabled() { return config.getBoolean("titles.login-success.enabled", true); }
    public boolean isRegisterSuccessTitleEnabled() { return config.getBoolean("titles.register-success.enabled", true); }
    public boolean isActionbarEnabled() { return config.getBoolean("display.actionbar.enabled", true); }
    public int getActionbarInterval() { return config.getInt("display.actionbar.interval", 1); }
    public int getTitleUpdateInterval() { return config.getInt("settings.title-update-interval", 20); }
    public int getLoginSuccessTitleDuration() { return config.getInt("settings.login-success-title-duration", 3); }
    public int getRegisterSuccessTitleDuration() { return config.getInt("settings.register-success-title-duration", 3); }

    // ========== 核心配置值 ==========

    public int getLoginTimeout() { return config.getInt("timeouts.login", 300); }
    public int getAutoLoginDuration() { return config.getInt("login.auto-login-duration", 7200); }
    public int getMaxWrongPassword() { return config.getInt("security.max-wrong-password", 5); }
    public int getMaxKickCount() { return config.getInt("security.max-kick-count", 3); }
    public List<String> getKickCommands() {
        // config.yml 使用单条字符串 security.kick-command，同时兼容列表写法
        if (config.isList("security.kick-commands")) {
            return config.getStringList("security.kick-commands");
        }
        String single = config.getString("security.kick-command", "");
        List<String> list = new ArrayList<>();
        if (single != null && !single.isEmpty()) list.add(single);
        return list;
    }

    public int getRegistrationTimeout() { return config.getInt("timeouts.register", 300); }
    public int getMaxPasswordLength() { return config.getInt("password.max-length", 32); }
    public int getMinPasswordLength() { return config.getInt("password.min-length", 6); }
    public Pattern getPasswordPattern() {
        String patternStr = config.getString("password.pattern", "^[a-zA-Z0-9_!@#$%^&*]+$");
        return Pattern.compile(patternStr);
    }

    public int getCaptchaLength() { return config.getInt("captcha.length", 4); }
    public boolean isAlphaNumCaptcha() { return config.getBoolean("captcha.alpha-numeric", false); }
    public int getCaptchaTimeout() { return config.getInt("captcha.timeout", 120); }

    public boolean isEmailEnabled() { return config.getBoolean("email.enabled", false); }
    public String getEmailHost() { return config.getString("email.host", "smtp.qq.com"); }
    public int getEmailPort() { return config.getInt("email.port", 465); }
    public String getEmailUsername() { return config.getString("email.username", ""); }
    public String getEmailPassword() { return config.getString("email.password", ""); }
    public String getEmailFrom() { return config.getString("email.from", "PluxLogin <noreply@example.com>"); }
    public int getEmailCodeValidity() { return config.getInt("email.code-validity", 300); }
    public int getEmailCooldown() { return config.getInt("email.cooldown", 60); }

    public boolean isQQEnabled() { return config.getBoolean("qq.enabled", true); }
    public boolean isQQUseEmail() { return config.getBoolean("qq.use-email", true); }
    public String getQQEmailDomain() { return config.getString("qq.email-domain", "qq.com"); }
    public int getQQCodeValidity() { return config.getInt("qq.code-validity", 300); }
    public int getQQCooldown() { return config.getInt("qq.cooldown", 60); }

    public boolean isUpdateCheckerEnabled() { return config.getBoolean("update-checker.enabled", true); }
    public boolean isTOTPEnabled() { return config.getBoolean("totp.enabled", true); }
    public String getTOTPIssuer() { return config.getString("totp.issuer", "PluxLogin"); }
    public int getTOTPCodeValidity() { return config.getInt("totp.code-validity", 300); }
    public int getTOTPCooldown() { return config.getInt("totp.cooldown", 60); }

    public int getGlobalActionCooldown() { return config.getInt("security.action-cooldown", 5); }
    public List<String> getAllowedCommands() { return config.getStringList("allowed-commands"); }

    // ========== 新增功能配置 ==========

    /** 是否启用强制绑定邮箱 */
    public boolean isForceEmailEnabled() { return config.getBoolean("force-email.enabled", false); }

    /** 强制绑定邮箱超时时间（秒），超时未绑定则踢出 */
    public int getForceEmailTimeout() { return config.getInt("force-email.timeout", 120); }

    /** 是否启用 GUI 管理界面 */
    public boolean isGuiEnabled() { return config.getBoolean("gui.enabled", false); }

    /** 是否启用 Web API */
    public boolean isWebApiEnabled() { return config.getBoolean("web-api.enabled", false); }

    /** Web API 端口 */
    public int getWebApiPort() { return config.getInt("web-api.port", 8701); }

    /** Web API 密钥（用于鉴权） */
    public String getWebApiSecret() { return config.getString("web-api.secret", ""); }

    // ========== 动作执行系统（合并重复逻辑）==========

    /**
     * 处理动作列表（支持 [TP], [DELAY], [COMMAND], 普通消息）
     * 用于 welcome.txt 和 join.txt 的统一处理
     */
    public void executeActions(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        processActions(player, lines, 0);
    }

    public void executeJoinActions(Player player) {
        executeActions(player, rawJoinLines);
    }

    public void executeWelcomeActions(Player player) {
        if (rawWelcomeLines == null || rawWelcomeLines.isEmpty()) {
            for (String msg : getWelcomeMessages(player.getName())) {
                player.sendMessage(msg);
            }
            return;
        }
        executeActions(player, rawWelcomeLines);
    }

    private void processActions(Player player, List<String> lines, int startIndex) {
        org.bukkit.scheduler.BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int index = startIndex;

            @Override
            public void run() {
                if (index >= lines.size() || !player.isOnline()) {
                    setWelcomeTask(null);
                    cancel();
                    return;
                }

                String line = lines.get(index++);
                String processed = line.replace("{player}", player.getName());

                if (line.startsWith("[TP]")) {
                    handleTeleport(player, processed);
                } else if (line.startsWith("[DELAY]")) {
                    handleDelay(player, lines, index);
                } else if (line.startsWith("[COMMAND]")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed.substring(9).trim());
                } else {
                    player.sendMessage(Utils.translateColorCodes(processed));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        setWelcomeTask(task);
    }

    private void handleTeleport(Player player, String processed) {
        String[] parts = processed.substring(4).trim().split("\\s+");
        if (parts.length >= 4) {
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) {
                    player.sendMessage(Utils.translateColorCodes("&c传送失败: 找不到世界 " + parts[0]));
                    return;
                }
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : player.getLocation().getYaw();
                float pitch = parts.length >= 6 ? Float.parseFloat(parts[5]) : player.getLocation().getPitch();
                player.teleport(new Location(world, x, y, z, yaw, pitch));
            } catch (NumberFormatException e) {
                player.sendMessage(Utils.translateColorCodes("&c传送失败: 坐标格式错误"));
            }
        } else {
            player.sendMessage(Utils.translateColorCodes("&c传送失败: 格式应为 [TP] world x y z [yaw] [pitch]"));
        }
    }

    private void handleDelay(Player player, List<String> lines, int currentIndex) {
        try {
            // 从完整的行中提取延迟秒数
            String delayLine = lines.get(currentIndex - 1);
            int seconds = Integer.parseInt(delayLine.substring(7).trim());
            cancelWelcomeTask();
            Bukkit.getScheduler().runTaskLater(plugin, () -> processActions(player, lines, currentIndex), seconds * 20L);
        } catch (NumberFormatException e) {
            player.sendMessage(Utils.translateColorCodes("&c延时失败: 格式应为 [DELAY] 秒数"));
        }
    }

    // ========== 辅助方法 ==========

    public List<String> getWelcomeMessages(String playerName) {
        ArrayList<String> messages = new ArrayList<>(welcomeMessages.size());
        for (String line : welcomeMessages) {
            messages.add(line.replace("{player}", playerName));
        }
        return messages;
    }

    public String translateColorCodes(String message) {
        return Utils.translateColorCodes(message);
    }

    public String getPrefix() {
        return Utils.translateColorCodes(messageConfig.getString("prefix", "&6[PluxLogin] &r"));
    }

    // ========== 验证码配置（补充）==========

    public boolean isCaptchaEnabled() { return config.getBoolean("captcha.enabled", true); }
    public String getRegisterCaptchaMessage(String captcha) { return prefix() + msg("register.captcha-message", "{captcha}", captcha); }

    // ========== 注册消息补充 ==========

    public String getRegisterNeedRegisterMessage() { return prefix() + msg("register.need-register"); }

    // ========== Anti-Bot 配置 ==========

    public boolean isAntiBotEnabled() { return config.getBoolean("anti-bot.enabled", true); }
    public long getAntiBotMinInterval() { return config.getLong("anti-bot.min-interval", 5000L); }
    public int getAntiBotMaxFastJoins() { return config.getInt("anti-bot.max-fast-joins", 3); }
    public long getAntiBotTempBanDuration() { return config.getLong("anti-bot.temp-ban-duration", 300L); }
    public String getAntiBotTempBanKickMessage(int remaining) {
        // message.yml 实际定义的是 login.temp-ban-kick（旧代码误读 anti-bot.temp-ban-kick，导致踢出消息为空）
        return prefix() + msg("login.temp-ban-kick", "{time}", String.valueOf(remaining));
    }

    /** 取消当前欢迎动作任务 */
    private volatile org.bukkit.scheduler.BukkitTask welcomeTask;
    public void setWelcomeTask(org.bukkit.scheduler.BukkitTask task) { this.welcomeTask = task; }
    public void cancelWelcomeTask() {
        if (welcomeTask != null) { welcomeTask.cancel(); welcomeTask = null; }
    }
}

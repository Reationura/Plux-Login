package com.plux.login;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class CommandHandler implements CommandExecutor {

    private final PluxLogin plugin;

    public CommandHandler(PluxLogin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        String[] cmds = {"login", "l", "register", "reg", "captcha",
                "mail", "mailc", "qq", "qqc", "logout", "regdel",
                "mailzhpass", "qqzhpass", "changepass", "cpss", "changepassword",
                "changemail", "cmail", "changeqq", "cq", "pluxlogin", "2fa", "2fac"};
        for (String cmd : cmds) {
            plugin.getCommand(cmd).setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "l": case "login": return handleLogin(sender, args);
            case "register": case "reg": return handleRegister(sender, args);
            case "captcha": return handleCaptcha(sender, args);
            case "mail": return handleMail(sender, args);
            case "mailc": return handleMailConfirm(sender, args);
            case "qq": return handleQQ(sender, args);
            case "qqc": return handleQQConfirm(sender, args);
            case "logout": return handleLogout(sender, args);
            case "regdel": return handleRegdel(sender, args);
            case "mailzhpass": return handleMailResetPassword(sender, args);
            case "qqzhpass": return handleQQResetPassword(sender, args);
            case "changepass": case "cpss": case "changepassword": return handleChangePassword(sender, args);
            case "changemail": case "cmail": return handleChangeEmail(sender, args);
            case "cq": case "changeqq": return handleChangeQQ(sender, args);
            case "pluxlogin": return handleMainCommand(sender, args);
            case "2fa": return handleTOTP(sender, args);
            case "2fac": return handleTOTPConfirm(sender, args);
        }
        return false;
    }

    // ========== 通用验证方法（消除重复代码）==========

    /** 检查是否为玩家 */
    private Player checkPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getErrorPlayerOnlyMessage());
            return null;
        }
        return (Player) sender;
    }

    /** 获取已登录的玩家会话（返回null则已发送错误消息）*/
    private PluxLogin.PlayerSession requireLoggedInSession(CommandSender sender) {
        Player player = checkPlayer(sender);
        if (player == null) return null;

        PluxLogin.PlayerSession session = plugin.getPlayerSession(player.getUniqueId());
        if (session == null || !session.isLoggedIn()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorNeedLoginFirstMessage());
            return null;
        }
        return session;
    }

    /** 获取有效的会话（不需要登录）*/
    private PluxLogin.PlayerSession requireSession(CommandSender sender) {
        Player player = checkPlayer(sender);
        if (player == null) return null;

        PluxLogin.PlayerSession session = plugin.getPlayerSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorUnknownErrorMessage());
            return null;
        }
        return session;
    }

    private String prefix() { return plugin.getConfigManager().getMessagePrefix(); }

    // ========== 登录 ==========

    private boolean handleLogin(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        UUID uuid = player.getUniqueId();
        PluxLogin.PlayerSession session = requireSession(sender);
        if (session == null) return true;

        if (session.isLoggedIn()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getLoginAlreadyLoggedInMessage());
            return true;
        }
        if (!plugin.getDatabaseManager().isPlayerRegistered(uuid)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getLoginNotRegisteredMessage());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageLoginMessage());
            return true;
        }

        // 验证密码
        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (!Utils.verifyPassword(args[0], data.getPasswordHash())) {
            int wrongCount = plugin.getWrongPasswordCount(uuid) + 1;
            int maxWrong = plugin.getConfigManager().getMaxWrongPassword();
            int remaining = maxWrong - wrongCount;
            plugin.incrementWrongPasswordCount(uuid);

            if (remaining <= 0) {
                player.kickPlayer(plugin.getConfigManager().getLoginMaxWrongKickMessage());
                plugin.incrementKickCount(uuid);
                if (plugin.getKickCount(uuid) >= plugin.getConfigManager().getMaxKickCount()) {
                    plugin.executeKickCommands(player.getName());
                    plugin.resetKickCount(uuid);
                }
            } else {
                player.sendMessage(prefix() + plugin.getConfigManager().getLoginWrongPasswordMessage(remaining));
                sendErrorTitle(player, "\u00a7c密码错误！", "\u00a76你还有 \u00a7a" + remaining + "\u00a76 次机会!");
            }
            return true;
        }

        // 密码正确 - 完成登录流程
        completeLogin(player, session, uuid);
        return true;
    }

    /**
     * 完成登录的统一流程（登录/注册共用）
     */
    private void completeLogin(Player player, PluxLogin.PlayerSession session, UUID uuid) {
        plugin.resetWrongPasswordCount(uuid);
        session.setLoggedIn(true);
        session.cancelTasks((org.bukkit.plugin.Plugin) plugin);
        plugin.getDatabaseManager().updateLastLogin(uuid);
        player.sendMessage(prefix() + plugin.getConfigManager().getLoginSuccessMessage());
        sendSuccessTitle(player);
        plugin.getConfigManager().executeWelcomeActions(player);

        // 使用后台定时检查缓存的最新版本通知管理员，避免每次登录都发起网络请求
        plugin.getUpdateChecker().notifyAdminOnLogin(player, plugin.getVersion(), plugin.getUpdateChecker().getLatestVersion());
    }

    // ========== 注册 ==========

    private boolean handleRegister(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        UUID uuid = player.getUniqueId();
        PluxLogin.PlayerSession session = requireSession(sender);
        if (session == null) return true;

        if (plugin.getDatabaseManager().isPlayerRegistered(uuid)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getRegisterAlreadyRegisteredMessage());
            return true;
        }
        if (!session.isCaptchaVerified()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getRegisterNeedCaptchaMessage());
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageRegisterMessage());
            return true;
        }

        String password = args[0];
        String confirmPassword = args[1];

        if (!password.equals(confirmPassword)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getRegisterPasswordMismatchMessage());
            sendErrorTitle(player, "\u00a7c两次输入的密码不一致！", "\u00a76请使用 /register <密码> <重复密码>");
            return true;
        }

        // 密码验证
        String passwordError = validatePassword(password);
        if (passwordError != null) {
            player.sendMessage(prefix() + passwordError);
            return true;
        }

        // 执行注册
        String hash = Utils.hashPassword(password);
        plugin.getDatabaseManager().registerPlayer(uuid, player.getName(), hash);
        completeLogin(player, session, uuid); // 复用完成登录流程
        player.sendMessage(prefix() + plugin.getConfigManager().getRegisterSuccessMessage());
        sendRegisterTitle(player);
        return true;
    }

    /**
     * 统一密码验证，返回错误消息或null表示通过
     */
    private String validatePassword(String password) {
        ConfigManager cfg = plugin.getConfigManager();
        if (password.length() < cfg.getMinPasswordLength())
            return cfg.getRegisterPasswordTooShortMessage(cfg.getMinPasswordLength());
        if (password.length() > cfg.getMaxPasswordLength())
            return cfg.getRegisterPasswordTooLongMessage(cfg.getMaxPasswordLength());
        if (!cfg.getPasswordPattern().matcher(password).matches())
            return cfg.getRegisterPasswordInvalidMessage();
        return null;
    }

    // ========== 验证码 ==========

    private boolean handleCaptcha(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        PluxLogin.PlayerSession session = requireSession(sender);
        if (session == null) return true;

        if (session.isCaptchaVerified()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getCaptchaSuccessMessage());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageCaptchaMessage());
            return true;
        }

        if (!plugin.getCaptchaManager().validateCaptcha(player.getUniqueId(), args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getRegisterCaptchaWrongMessage());
            return true;
        }

        session.setCaptchaVerified(true);
        session.cancelTasks((org.bukkit.plugin.Plugin) plugin);

        PlayerListener listener = new PlayerListener(plugin);
        boolean registered = plugin.getDatabaseManager().isPlayerRegistered(player.getUniqueId());

        if (registered) {
            session.setState(PluxLogin.SessionState.LOGIN);
            session.setRemainingTime(plugin.getConfigManager().getLoginTimeout());
            listener.startTitleTask(player, session, PluxLogin.SessionState.LOGIN);
            listener.startActionbarTask(player, session, PluxLogin.SessionState.LOGIN);
            player.sendMessage(prefix() + plugin.getConfigManager().getCaptchaSuccessMessage());
            player.sendMessage(prefix() + plugin.getConfigManager().getLoginNeedLoginMessage());
        } else {
            session.setState(PluxLogin.SessionState.REGISTER);
            session.setRemainingTime(plugin.getConfigManager().getRegistrationTimeout());
            listener.startTitleTask(player, session, PluxLogin.SessionState.REGISTER);
            listener.startActionbarTask(player, session, PluxLogin.SessionState.REGISTER);
            player.sendMessage(prefix() + plugin.getConfigManager().getCaptchaSuccessMessage());
        }
        return true;
    }

    // ========== 邮箱绑定 ==========

    private boolean handleMail(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (!plugin.getConfigManager().isEmailEnabled()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorUnknownErrorMessage());
            return true;
        }
        if (!checkCooldown(player, uuid)) return true;
        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageMailMessage());
            return true;
        }
        if (!Utils.isValidEmail(args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailInvalidMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getEmail() != null && !data.getEmail().isEmpty()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailAlreadyBoundMessage());
            return true;
        }

        // 冷却检查
        PluxLogin.PendingBinding existing = plugin.getPendingEmailBinding(uuid);
        if (existing != null) {
            long elapsed = System.currentTimeMillis() - existing.getTimestamp();
            long cooldownMs = (long) plugin.getConfigManager().getEmailCooldown() * 1000;
            if (elapsed < cooldownMs) {
                player.sendMessage(prefix() + plugin.getConfigManager().getEmailCooldownMessage(
                        (int) ((cooldownMs - elapsed) / 1000)));
                return true;
            }
        }

        String code = Utils.generateCode(6);
        if (!plugin.getEmailManager().sendVerificationCode(args[0], code)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorEmailErrorMessage());
            return true;
        }

        plugin.createPendingEmailBinding(uuid, new PluxLogin.PendingBinding(args[0], code));
        player.sendMessage(prefix() + plugin.getConfigManager().getEmailCodeSentMessage());
        player.sendMessage(prefix() + plugin.getConfigManager().getEmailNeedVerifyMessage());
        return true;
    }

    private boolean handleMailConfirm(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageMailcMessage());
            return true;
        }

        PluxLogin.PendingBinding binding = plugin.getPendingEmailBinding(uuid);
        if (binding == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailNoPendingMessage());
            return true;
        }

        // 过期检查
        long validityMs = (long) plugin.getConfigManager().getEmailCodeValidity() * 1000;
        if (System.currentTimeMillis() - binding.getTimestamp() > validityMs) {
            plugin.removePendingEmailBinding(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailCodeExpiredMessage());
            return true;
        }

        if (!binding.getCode().equals(args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailCodeWrongMessage());
            return true;
        }

        if (plugin.getDatabaseManager().updateEmail(uuid, binding.getValue())) {
            plugin.removePendingEmailBinding(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailBindSuccessMessage());
        } else {
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailBindFailedMessage());
        }
        return true;
    }

    // ========== QQ 绑定 ==========

    private boolean handleQQ(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (!plugin.getConfigManager().isQQEnabled()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorUnknownErrorMessage());
            return true;
        }
        if (!checkCooldown(player, uuid)) return true;
        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageQQMessage());
            return true;
        }
        if (!Utils.isValidQQ(args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getQQInvalidMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getQq() != null && !data.getQq().isEmpty()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getQQAlreadyBoundMessage());
            return true;
        }

        PluxLogin.PendingBinding existing = plugin.getPendingQQBinding(uuid);
        if (existing != null) {
            long elapsed = System.currentTimeMillis() - existing.getTimestamp();
            long cooldownMs = (long) plugin.getConfigManager().getQQCooldown() * 1000;
            if (elapsed < cooldownMs) {
                player.sendMessage(prefix() + plugin.getConfigManager().getQQCooldownMessage(
                        (int) ((cooldownMs - elapsed) / 1000)));
                return true;
            }
        }

        String code = Utils.generateCode(6);
        String qqEmail = args[0] + "@" + plugin.getConfigManager().getQQEmailDomain();
        if (!plugin.getEmailManager().sendEmail(qqEmail, "PluxLogin QQ验证",
                "您的验证码是: " + code + "\n\n有效期5分钟。\n\n- PluxLogin")) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorEmailErrorMessage());
            return true;
        }

        plugin.createPendingQQBinding(uuid, new PluxLogin.PendingBinding(args[0], code));
        player.sendMessage(prefix() + plugin.getConfigManager().getQQCodeSentMessage());
        player.sendMessage(prefix() + plugin.getConfigManager().getQQNeedVerifyMessage());
        return true;
    }

    private boolean handleQQConfirm(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageQQcMessage());
            return true;
        }

        PluxLogin.PendingBinding binding = plugin.getPendingQQBinding(uuid);
        if (binding == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getQQNoPendingMessage());
            return true;
        }

        long validityMs = (long) plugin.getConfigManager().getQQCodeValidity() * 1000;
        if (System.currentTimeMillis() - binding.getTimestamp() > validityMs) {
            plugin.removePendingQQBinding(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getQQCodeExpiredMessage());
            return true;
        }

        if (!binding.getCode().equals(args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getQQCodeWrongMessage());
            return true;
        }

        if (plugin.getDatabaseManager().updateQQ(uuid, binding.getValue())) {
            plugin.removePendingQQBinding(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getQQBindSuccessMessage());
        } else {
            player.sendMessage(prefix() + plugin.getConfigManager().getQQBindFailedMessage());
        }
        return true;
    }

    // ========== 登出 ==========

    private boolean handleLogout(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        UUID uuid = player.getUniqueId();
        PluxLogin.PlayerSession session = plugin.getPlayerSession(uuid);
        if (session == null || !session.isLoggedIn()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getLoginNotRegisteredMessage());
            return true;
        }

        session.setLoggedIn(false);
        session.setState(PluxLogin.SessionState.LOGIN);
        session.setRemainingTime(plugin.getConfigManager().getLoginTimeout());
        player.sendMessage(prefix() + plugin.getConfigManager().getLoginLogoutSuccessMessage());
        player.sendMessage(prefix() + plugin.getConfigManager().getLoginNeedLoginMessage());

        // 重新启动 title 和 actionbar
        PlayerListener listener = new PlayerListener(plugin);
        listener.startTitleTask(player, session, PluxLogin.SessionState.LOGIN);
        listener.startActionbarTask(player, session, PluxLogin.SessionState.LOGIN);
        return true;
    }

    // ========== 删除注册 ==========

    private boolean handleRegdel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pluxlogin.admin")) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getErrorNoPermissionMessage());
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getUsageRegdelMessage());
            return true;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[0]);
        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            sender.sendMessage(prefix() + plugin.getConfigManager().getErrorPlayerNotFoundMessage());
            return true;
        }

        UUID uuid = offlinePlayer.getUniqueId();
        boolean success = plugin.getDatabaseManager().deletePlayer(uuid);
        if (success) {
            if (offlinePlayer.isOnline()) {
                offlinePlayer.getPlayer().kickPlayer("\u60a8\u7684\u8d26\u53f7\u5df2\u88ab\u5220\u9664\uff01");
            }
            sender.sendMessage(prefix() + plugin.getConfigManager().getAdminRegdelSuccessMessage(args[0]));
        } else {
            sender.sendMessage(prefix() + plugin.getConfigManager().getAdminRegdelFailedMessage());
        }
        return true;
    }

    // ========== 密码重置 ==========

    private boolean handleMailResetPassword(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        UUID uuid = player.getUniqueId();
        if (!plugin.getDatabaseManager().isPlayerRegistered(uuid)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getLoginNotRegisteredMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getEmail() == null || data.getEmail().isEmpty()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getPasswordResetNoBindingMessage("邮箱"));
            return true;
        }
        if (!checkCooldown(player, uuid)) return true;

        String code = Utils.generateCode(6);
        if (!plugin.getEmailManager().sendEmail(data.getEmail(), "PluxLogin 密码重置",
                "您的验证码是: " + code + "\n\n有效期5分钟。\n\n- PluxLogin")) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorEmailErrorMessage());
            return true;
        }

        plugin.createPendingPasswordReset(uuid, new PluxLogin.PasswordResetVerification("email", data.getEmail(), code));
        player.sendMessage(prefix() + plugin.getConfigManager().getPasswordResetEmailCodeSentMessage());
        return true;
    }

    private boolean handleQQResetPassword(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        UUID uuid = player.getUniqueId();
        if (!plugin.getDatabaseManager().isPlayerRegistered(uuid)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getLoginNotRegisteredMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getQq() == null || data.getQq().isEmpty()) {
            player.sendMessage(prefix() + plugin.getConfigManager().getPasswordResetNoBindingMessage("QQ"));
            return true;
        }
        if (!checkCooldown(player, uuid)) return true;

        String code = Utils.generateCode(6);
        String qqEmail = data.getQq() + "@" + plugin.getConfigManager().getQQEmailDomain();
        if (!plugin.getEmailManager().sendEmail(qqEmail, "PluxLogin 密码重置",
                "您的验证码是: " + code + "\n\n有效期5分钟。\n\n- PluxLogin")) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorEmailErrorMessage());
            return true;
        }

        plugin.createPendingPasswordReset(uuid, new PluxLogin.PasswordResetVerification("qq", data.getQq(), code));
        player.sendMessage(prefix() + plugin.getConfigManager().getPasswordResetQQCodeSentMessage());
        return true;
    }

    // ========== 修改密码/邮箱/QQ ==========

    private boolean handleChangePassword(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        if (args.length != 2) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageChangepassMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (!Utils.verifyPassword(args[0], data.getPasswordHash())) {
            player.sendMessage(prefix() + plugin.getConfigManager().getChangePasswordWrongOldMessage());
            return true;
        }
        if (args[0].equals(args[1])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getChangePasswordSameMessage());
            return true;
        }

        String error = validatePassword(args[1]);
        if (error != null) {
            player.sendMessage(prefix() + error);
            return true;
        }

        boolean success = plugin.getDatabaseManager().updatePassword(player.getUniqueId(), Utils.hashPassword(args[1]));
        player.sendMessage(prefix() + (success
                ? plugin.getConfigManager().getChangePasswordSuccessMessage()
                : plugin.getConfigManager().getErrorDatabaseErrorMessage()));
        return true;
    }

    private boolean handleChangeEmail(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        if (args.length != 2) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageChangemailMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getEmail() == null || !data.getEmail().equalsIgnoreCase(args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getChangeEmailWrongOldMessage());
            return true;
        }
        if (args[0].equalsIgnoreCase(args[1])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getChangeEmailSameMessage());
            return true;
        }
        if (!Utils.isValidEmail(args[1])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getEmailInvalidMessage());
            return true;
        }
        if (!checkCooldown(player, uuid)) return true;

        String code = Utils.generateCode(6);
        if (!plugin.getEmailManager().sendEmail(args[1], "PluxLogin 邮箱修改验证",
                "验证码: " + code + "\n\n有效期5分钟。- PluxLogin")) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorEmailErrorMessage());
            return true;
        }

        plugin.createPendingEmailBinding(uuid, new PluxLogin.PendingBinding(args[1], code));
        player.sendMessage(prefix() + plugin.getConfigManager().getChangeEmailCodeSentMessage());
        return true;
    }

    private boolean handleChangeQQ(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        if (args.length != 2) {
            player.sendMessage(prefix() + plugin.getConfigManager().getUsageChangeqqMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getQq() == null || !data.getQq().equals(args[0])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getChangeQQWrongOldMessage());
            return true;
        }
        if (args[0].equals(args[1])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getChangeQQSameMessage());
            return true;
        }
        if (!Utils.isValidQQ(args[1])) {
            player.sendMessage(prefix() + plugin.getConfigManager().getQQInvalidMessage());
            return true;
        }
        if (!checkCooldown(player, uuid)) return true;

        String code = Utils.generateCode(6);
        String qqEmail = args[1] + "@" + plugin.getConfigManager().getQQEmailDomain();
        if (!plugin.getEmailManager().sendEmail(qqEmail, "PluxLogin QQ修改验证",
                "验证码: " + code + "\n\n有效期5分钟。- PluxLogin")) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorEmailErrorMessage());
            return true;
        }

        plugin.createPendingQQBinding(uuid, new PluxLogin.PendingBinding(args[1], code));
        player.sendMessage(prefix() + plugin.getConfigManager().getChangeQQCodeSentMessage());
        return true;
    }

    // ========== TOTP/2FA ==========

    private boolean handleTOTP(CommandSender sender, String[] args) {
        PluxLogin.PlayerSession session = requireLoggedInSession(sender);
        if (session == null) return true;

        Player player = (Player) sender;
        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpUsageMessage());
            return true;
        }

        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        String subCmd = args[0].toLowerCase();

        if ("on".equals(subCmd)) {
            if (data.is2FAEnabled()) {
                player.sendMessage(prefix() + plugin.getConfigManager().getTotpAlreadyEnabledMessage());
                return true;
            }
            String secret = plugin.getTOTPManager().generateSecret();
            String url = plugin.getTOTPManager().generateQRCodeURL(player.getName(), secret,
                    plugin.getConfigManager().getTOTPIssuer());
            // 存储 secret 等待确认
            plugin.setPendingTOTPVerification(uuid);
            // 将 secret 存入临时位置供确认时使用
            plugin.getDatabaseManager().updateTotpSecret(uuid, secret);
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpEnabledMessage());
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpQrCodeMessage(url));
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpNeedVerifyMessage());
        } else if ("off".equals(subCmd)) {
            if (!data.is2FAEnabled()) {
                player.sendMessage(prefix() + plugin.getConfigManager().getTotpNotEnabledMessage());
                return true;
            }
            plugin.getDatabaseManager().updateTotpSecret(uuid, null);
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpDisabledMessage());
        } else {
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpUsageMessage());
        }
        return true;
    }

    private boolean handleTOTPConfirm(CommandSender sender, String[] args) {
        Player player = checkPlayer(sender);
        if (player == null) return true;

        UUID uuid = player.getUniqueId();
        if (!plugin.isPendingTOTPVerification(uuid)) {
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpNotEnabledMessage());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpUsageConfirmMessage());
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data == null) {
            plugin.removePendingTOTPVerification(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getErrorDatabaseErrorMessage());
            return true;
        }
        if (data.getTotpSecret() == null || data.getTotpSecret().isEmpty()) {
            plugin.removePendingTOTPVerification(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpNotEnabledMessage());
            return true;
        }

        if (plugin.getTOTPManager().verifyTOTP(data.getTotpSecret(), args[0])) {
            plugin.removePendingTOTPVerification(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpVerifySuccessMessage());
        } else {
            player.sendMessage(prefix() + plugin.getConfigManager().getTotpVerifyFailedMessage());
        }
        return true;
    }

    // ========== 主命令 ==========

    private boolean handleMainCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(prefix() + Utils.translateColorCodes("&6PluxLogin &ev" + plugin.getVersion()));
            sender.sendMessage(prefix() + Utils.translateColorCodes("&e作者: &aya_xzer21145"));
            sender.sendMessage(prefix() + Utils.translateColorCodes("&e使用 &a/pluxlogin help &e查看帮助"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;
            case "reload":
                if (!sender.hasPermission("pluxlogin.admin")) {
                    sender.sendMessage(prefix() + plugin.getConfigManager().getErrorNoPermissionMessage());
                    return true;
                }
                plugin.getConfigManager().loadConfig();
                plugin.getEmailManager().reload();
                sender.sendMessage(prefix() + plugin.getConfigManager().getMainCommandReloadSuccess());
                break;
            case "update":
                if (!sender.hasPermission("pluxlogin.admin")) {
                    sender.sendMessage(prefix() + plugin.getConfigManager().getErrorNoPermissionMessage());
                    return true;
                }
                sender.sendMessage(prefix() + plugin.getConfigManager().getMainCommandUpdateChecking());
                plugin.getServer().getScheduler().runTaskAsynchronously((Plugin) plugin, () -> {
                    plugin.getUpdateChecker().checkForUpdates();
                    sender.sendMessage(prefix() + plugin.getConfigManager().getMainCommandUpdateCheckComplete());
                });
                break;
            case "version":
                sender.sendMessage(prefix() + plugin.getConfigManager().getMainCommandVersion()
                        .replace("{version}", plugin.getVersion()));
                break;
            case "gui":
                if (!sender.hasPermission("pluxlogin.admin")) {
                    sender.sendMessage(prefix() + plugin.getConfigManager().getErrorNoPermissionMessage());
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix() + plugin.getConfigManager().getErrorPlayerOnlyMessage());
                    return true;
                }
                if (plugin.getConfigManager().isGuiEnabled()) {
                    InventoryGui.openAdminGUI((Player) sender, plugin);
                } else {
                    sender.sendMessage(prefix() + Utils.translateColorCodes("&cGUI功能未启用！请在 config.yml 中配置 gui.enabled: true"));
                }
                break;
            default:
                sender.sendMessage(prefix() + Utils.translateColorCodes("&c未知子命令！使用 /pluxlogin help 查看帮助。"));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        sender.sendMessage(cfg.getMainCommandHelpHeader());
        sender.sendMessage(Utils.translateColorCodes("&e登录相关:"));
        sender.sendMessage(Utils.translateColorCodes("  &a/login <密码> &7- 登录服务器"));
        sender.sendMessage(Utils.translateColorCodes("  &a/register <密码> <重复密码> &7- 注册账号"));
        sender.sendMessage(Utils.translateColorCodes("  &a/captcha <验证码> &7- 验证验证码"));
        sender.sendMessage(Utils.translateColorCodes("  &a/logout &7- 登出服务器"));
        sender.sendMessage(Utils.translateColorCodes("&e绑定相关:"));
        sender.sendMessage(Utils.translateColorCodes("  &a/mail <邮箱> &7- 绑定邮箱"));
        sender.sendMessage(Utils.translateColorCodes("  &a/mailc <验证码> &7- 验证邮箱"));
        sender.sendMessage(Utils.translateColorCodes("  &a/qq <QQ号> &7- 绑定QQ"));
        sender.sendMessage(Utils.translateColorCodes("  &a/qqc <验证码> &7- 验证QQ"));
        sender.sendMessage(Utils.translateColorCodes("&e修改相关:"));
        sender.sendMessage(Utils.translateColorCodes("  &a/changepass <原密码> <新密码> &7- 修改密码"));
        sender.sendMessage(Utils.translateColorCodes("  &a/changemail <原邮箱> <新邮箱> &7- 修改邮箱"));
        sender.sendMessage(Utils.translateColorCodes("  &a/changeqq <原QQ> <新QQ> &7- 修改QQ"));
        sender.sendMessage(Utils.translateColorCodes("&e密码重置:"));
        sender.sendMessage(Utils.translateColorCodes("  &a/mailzhpass &7- 通过邮箱重置密码"));
        sender.sendMessage(Utils.translateColorCodes("  &a/qqzhpass &7- 通过QQ重置密码"));
        if (sender.hasPermission("pluxlogin.admin")) {
            sender.sendMessage(Utils.translateColorCodes("&e管理员:"));
            sender.sendMessage(Utils.translateColorCodes("  &a/regdel <玩家> &7- 删除玩家注册"));
            sender.sendMessage(Utils.translateColorCodes("  &a/pluxlogin reload &7- 重载配置"));
            sender.sendMessage(Utils.translateColorCodes("  &a/pluxlogin update &7- 检查更新"));
            sender.sendMessage(Utils.translateColorCodes("  &a/pluxlogin gui &7- 打开管理界面"));
        }
        sender.sendMessage(cfg.getMainCommandHelpFooter());
    }

    // ========== 辅助方法 ==========

    /** 全局冷却检查 */
    private boolean checkCooldown(Player player, UUID uuid) {
        if (!plugin.canPerformAction(uuid)) {
            int remaining = plugin.getActionCooldownRemaining(uuid);
            player.sendMessage(prefix() + plugin.getConfigManager().getGlobalCooldownMessage(remaining));
            return false;
        }
        return true;
    }

    private void sendSuccessTitle(Player player) {
        if (!plugin.getConfigManager().isLoginSuccessTitleEnabled()) return;
        plugin.getNMSAdapter().sendTitle(player,
                plugin.getConfigManager().getLoginSuccessTitle(),
                plugin.getConfigManager().getLoginSuccessSubtitle(),
                10, plugin.getConfigManager().getLoginSuccessTitleDuration() * 20, 10);
    }

    private void sendRegisterTitle(Player player) {
        if (!plugin.getConfigManager().isRegisterSuccessTitleEnabled()) return;
        plugin.getNMSAdapter().sendTitle(player,
                plugin.getConfigManager().getRegisterSuccessTitle(),
                plugin.getConfigManager().getRegisterSuccessSubtitle(),
                10, plugin.getConfigManager().getRegisterSuccessTitleDuration() * 20, 10);
    }

    private void sendErrorTitle(Player player, String title, String subtitle) {
        plugin.getNMSAdapter().sendTitle(player, title, subtitle, 0, 40, 0);
    }
}

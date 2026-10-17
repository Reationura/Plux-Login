package com.plux.login;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {

    private final PluxLogin plugin;

    public PlayerListener(PluxLogin plugin) {
        this.plugin = plugin;
    }

    // ========== 玩家加入（核心登录流程）==========

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        // 快速重入检测
        if (isFastRejoin(uuid)) return;

        // 临时封禁检查
        Long tempBanned = plugin.getTempBannedUntil(uuid);
        if (tempBanned != null && System.currentTimeMillis() < tempBanned) {
            long remaining = (tempBanned - System.currentTimeMillis()) / 1000;
            player.kickPlayer(plugin.getConfigManager().getAntiBotTempBanKickMessage((int) remaining));
            return;
        }

        // 创建会话
        PluxLogin.PlayerSession session = new PluxLogin.PlayerSession(uuid, player.getName());
        plugin.setPlayerSession(uuid, session);

        // 判断状态：已注册 -> 登录 / 未注册 -> 验证码
        boolean registered = plugin.getDatabaseManager().isPlayerRegistered(uuid);

        // 检查是否启用验证码
        if (plugin.getConfigManager().isCaptchaEnabled()) {
            session.setState(PluxLogin.SessionState.CAPTCHA);
            session.setRemainingTime(plugin.getConfigManager().getCaptchaTimeout());
            String captcha = plugin.getCaptchaManager().generateCaptcha(uuid);
            startTitleTask(player, session, PluxLogin.SessionState.CAPTCHA);
            startActionbarTask(player, session, PluxLogin.SessionState.CAPTCHA);
            player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                    + plugin.getConfigManager().getRegisterCaptchaMessage(captcha));
            return;
        }

        // 无验证码模式：直接进入注册或登录
        if (registered) {
            session.setState(PluxLogin.SessionState.LOGIN);
            session.setRemainingTime(plugin.getConfigManager().getLoginTimeout());
            startTitleTask(player, session, PluxLogin.SessionState.LOGIN);
            startActionbarTask(player, session, PluxLogin.SessionState.LOGIN);
            player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                    + plugin.getConfigManager().getLoginNeedLoginMessage());
        } else {
            session.setState(PluxLogin.SessionState.REGISTER);
            session.setRemainingTime(plugin.getConfigManager().getRegistrationTimeout());
            startTitleTask(player, session, PluxLogin.SessionState.REGISTER);
            startActionbarTask(player, session, PluxLogin.SessionState.REGISTER);
            player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                    + plugin.getConfigManager().getRegisterNeedRegisterMessage());
        }
    }

    // ========== 玩家退出 ==========

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PluxLogin.PlayerSession session = plugin.getPlayerSession(uuid);
        if (session != null) {
            session.cancelTasks((org.bukkit.plugin.Plugin) plugin);
        }
        cleanupSession(uuid);
    }

    // ========== 聊天拦截（未登录时拦截）==========

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PluxLogin.PlayerSession session = plugin.getPlayerSession(uuid);

        if (session == null || !session.isLoggedIn()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                    + plugin.getConfigManager().getErrorNeedLoginFirstMessage());
        }
    }

    // ========== 命令拦截（未登录时只允许认证命令）==========

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PluxLogin.PlayerSession session = plugin.getPlayerSession(uuid);

        if (session == null || !session.isLoggedIn()) {
            String cmd = event.getMessage().toLowerCase();
            for (String allowed : plugin.getAllowedCommands()) {
                if (cmd.startsWith("/" + allowed.toLowerCase())) return;
            }
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                    + plugin.getConfigManager().getErrorNeedLoginFirstMessage());
        }
    }

    // ========== 移动/交互/伤害拦截（未登录时限制操作）==========

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PluxLogin.PlayerSession session = plugin.getPlayerSession(player.getUniqueId());

        if (session == null || !session.isLoggedIn()) {
            // 允许头部旋转，阻止位置移动
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isLoggedIn(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isLoggedIn(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && !isLoggedIn((Player) event.getWhoClicked()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && !isLoggedIn((Player) event.getDamager()))
            event.setCancelled(true);
    }

    // ========== 登录后处理（2FA + 强制邮箱）==========

    /**
     * 登录后的高级验证入口（预留）。
     * 当前默认登录流程为保证稳定性不会在未完整实现的 2FA/强制邮箱门上拦截玩家，
     * 因此本方法暂未接入 completeLogin()；保留以便后续版本完善 2FA / 强制邮箱登录门控时启用。
     */
    public void handlePostLogin(Player player, PluxLogin.PlayerSession session) {
        UUID uuid = player.getUniqueId();

        // 1. 检查 2FA
        PlayerData data = plugin.getDatabaseManager().getPlayerData(uuid);
        if (data.is2FAEnabled() && data.getTotpSecret() != null) {
            session.setState(PluxLogin.SessionState.TOTP_VERIFY);
            session.setLoggedIn(false); // 暂时标记为未完成验证
            player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                    + plugin.getConfigManager().getTotpNeedVerifyMessage());
            return;
        }

        // 2. 检查强制绑定邮箱
        if (plugin.getConfigManager().isForceEmailEnabled()) {
            if (data.getEmail() == null || data.getEmail().isEmpty()) {
                session.setState(PluxLogin.SessionState.FORCE_EMAIL);
                session.setLoggedIn(false);
                int timeout = plugin.getConfigManager().getForceEmailTimeout();
                session.setRemainingTime(timeout);
                startTitleTask(player, session, PluxLogin.SessionState.FORCE_EMAIL);
                startActionbarTask(player, session, PluxLogin.SessionState.FORCE_EMAIL);
                player.sendMessage(plugin.getConfigManager().getMessagePrefix()
                        + Utils.translateColorCodes("&c您必须绑定邮箱才能进入服务器！"));
                player.sendMessage(Utils.translateColorCodes("&e请使用 &a/mail <邮箱> &e来绑定您的邮箱。"));
                player.sendMessage(Utils.translateColorCodes("&7超时时间: " + timeout + " 秒"));
                return;
            }
        }

        // 全部通过，执行欢迎动作
        plugin.getConfigManager().executeWelcomeActions(player);
    }

    // ========== 辅助方法 ==========

    private boolean isLoggedIn(Player player) {
        PluxLogin.PlayerSession session = plugin.getPlayerSession(player.getUniqueId());
        return session != null && session.isLoggedIn();
    }

    /** 快速重入检测 */
    private boolean isFastRejoin(UUID uuid) {
        if (!plugin.getConfigManager().isAntiBotEnabled()) return false;

        long now = System.currentTimeMillis();
        Long lastJoin = plugin.getJoinTimestamp(uuid);
        if (lastJoin != null && now - lastJoin < plugin.getConfigManager().getAntiBotMinInterval()) {
            int count = plugin.incrementFastJoinCount(uuid);
            if (count >= plugin.getConfigManager().getAntiBotMaxFastJoins()) {
                long banDuration = (long) plugin.getConfigManager().getAntiBotTempBanDuration() * 1000;
                plugin.setTempBannedUntil(uuid, now + banDuration);
                plugin.resetFastJoinCount(uuid);
                return true;
            }
        } else {
            plugin.resetFastJoinCount(uuid);
        }
        plugin.setJoinTimestamp(uuid, now);
        return false;
    }

    private void cleanupSession(UUID uuid) {
        plugin.removePlayerSession(uuid);
        plugin.getCaptchaManager().removeCaptcha(uuid);
        plugin.removePendingEmailBinding(uuid);
        plugin.removePendingQQBinding(uuid);
        plugin.resetWrongPasswordCount(uuid);
        plugin.removePendingPasswordReset(uuid);
        plugin.removePendingTOTPVerification(uuid);
        plugin.removeLastActionTimestamp(uuid);
    }

    // ========== Title 和 ActionBar 任务 ==========

    public void startTitleTask(final Player player, final PluxLogin.PlayerSession session,
                               final PluxLogin.SessionState state) {
        final boolean titleEnabled = plugin.getConfigManager().isTitleEnabled();
        if (!titleEnabled) {
            scheduleTimeoutTask(player, session, state);
            return;
        }

        final int[] lastTime = {Integer.MAX_VALUE};

        session.setTitleTaskId(plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                (org.bukkit.plugin.Plugin) plugin, () -> {
                    try {
                        if (!player.isOnline()) { session.cancelTasks((org.bukkit.plugin.Plugin) plugin); return; }
                        PluxLogin.PlayerSession current = plugin.getPlayerSession(player.getUniqueId());
                        if (current == null || current.getState() != state || current.isLoggedIn())
                            { session.cancelTasks((org.bukkit.plugin.Plugin) plugin); return; }

                        session.setRemainingTime(session.getRemainingTime() - 1);
                        if (session.getRemainingTime() <= 0) {
                            player.kickPlayer(plugin.getConfigManager().getRegisterTimeoutKickMessage());
                            session.cancelTasks((org.bukkit.plugin.Plugin) plugin);
                            return;
                        }

                        int currentTime = session.getRemainingTime();
                        if (currentTime != lastTime[0]) {
                            lastTime[0] = currentTime;
                            String title, subtitle;
                            switch (state) {
                                case CAPTCHA:
                                    title = plugin.getConfigManager().getCaptchaTitle();
                                    subtitle = plugin.getConfigManager().getCaptchaSubtitle(currentTime);
                                    break;
                                case LOGIN:
                                    title = plugin.getConfigManager().getLoginTitle();
                                    subtitle = plugin.getConfigManager().getLoginSubtitle(currentTime);
                                    break;
                                case REGISTER:
                                    title = plugin.getConfigManager().getRegisterTitle();
                                    subtitle = plugin.getConfigManager().getRegisterSubtitle(currentTime);
                                    break;
                                case FORCE_EMAIL:
                                    title = "\u00a7c\u5fc3\u8d23\u7ed1\u5b9a";
                                    subtitle = "\u00a76\u8bf7\u4f7f\u7528 /mail <\u90ae\u7bb1> \u7ed1\u5b9a \u00a77\u5269\u4f59 " + currentTime + "s";
                                    break;
                                case TOTP_VERIFY:
                                    title = "\u00a7e2FA \u9a8c\u8bc1";
                                    subtitle = "\u00a76\u8bf7\u4f7f\u7528 /2fac <\u7801> \u9a8c\u8bc1 \u00a77\u5269\u4f59 " + currentTime + "s";
                                    break;
                                default: return;
                            }
                            plugin.getNMSAdapter().sendTitle(player, title, subtitle, 2, 6000, 2);
                        }
                    } catch (Exception ignored) {}
                }, 0L, 20L));
    }

    private void scheduleTimeoutTask(final Player player, final PluxLogin.PlayerSession session,
                                     final PluxLogin.SessionState state) {
        session.setTitleTaskId(plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                (org.bukkit.plugin.Plugin) plugin, () -> {
                    try {
                        if (!player.isOnline()) { session.cancelTasks((org.bukkit.plugin.Plugin) plugin); return; }
                        PluxLogin.PlayerSession current = plugin.getPlayerSession(player.getUniqueId());
                        if (current == null || current.getState() != state || current.isLoggedIn())
                            { session.cancelTasks((org.bukkit.plugin.Plugin) plugin); return; }

                        session.setRemainingTime(session.getRemainingTime() - 1);
                        if (session.getRemainingTime() <= 0) {
                            player.kickPlayer(plugin.getConfigManager().getRegisterTimeoutKickMessage());
                            session.cancelTasks((org.bukkit.plugin.Plugin) plugin);
                        }
                    } catch (Exception ignored) {}
                }, 0L, 20L));
    }

    public void startActionbarTask(final Player player, final PluxLogin.PlayerSession session,
                                   final PluxLogin.SessionState state) {
        final boolean actionbarEnabled = plugin.getConfigManager().isActionbarEnabled();
        if (!actionbarEnabled) return;

        final int interval = plugin.getConfigManager().getActionbarInterval();
        final int sendInterval = interval * 20;
        final int[] tickCounter = {0};

        session.setActionbarTaskId(plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                (org.bukkit.plugin.Plugin) plugin, () -> {
                    try {
                        if (!player.isOnline()) { session.cancelTasks((org.bukkit.plugin.Plugin) plugin); return; }
                        PluxLogin.PlayerSession current = plugin.getPlayerSession(player.getUniqueId());
                        if (current == null || current.getState() != state || current.isLoggedIn())
                            { session.cancelTasks((org.bukkit.plugin.Plugin) plugin); return; }

                        if (tickCounter[0]++ % sendInterval == 0) {
                            String message;
                            switch (state) {
                                case CAPTCHA:
                                    message = plugin.getConfigManager().getCaptchaActionbar(session.getRemainingTime());
                                    break;
                                case LOGIN:
                                    message = plugin.getConfigManager().getLoginActionbar(session.getRemainingTime());
                                    break;
                                case REGISTER:
                                    message = plugin.getConfigManager().getRegisterActionbar(session.getRemainingTime());
                                    break;
                                case FORCE_EMAIL:
                                    message = Utils.translateColorCodes("\u00a7c[\u5fc3\u8d23\u7ed1\u5b9a] \u00a76\u5269\u4f59: \u00a7a" + session.getRemainingTime() + "\u00a76s - \u00a7e/mail <\u90ae\u7bb1>");
                                    break;
                                case TOTP_VERIFY:
                                    message = Utils.translateColorCodes("\u00a7e[2FA] \u00a76\u5269\u4f59: \u00a7a" + session.getRemainingTime() + "\u00a76s - \u00a7e/2fac <\u9a8c\u8bc1\u7801>");
                                    break;
                                default: return;
                            }
                            plugin.getNMSAdapter().sendActionBar(player, message);
                        }
                    } catch (Exception ignored) {}
                }, 0L, 20L));
    }
}

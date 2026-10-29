package com.plux.login.adapter;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * 1.8+ 通用适配器（含 1.20.5 可重定位 CB 与 26.x 新命名版本）
 *
 * 全程使用 Spigot/Bukkit API，不依赖任何 NMS 包，天然跨版本兼容；
 * 并对参数做空值防御，对极端 API 异常做降级，避免崩服。
 */
public class NMSAdapter_Modern implements NMSAdapter {

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) return;
        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (Throwable t) {
            // 某些 fork/新版本 API 变更或非法参数时降级为聊天消息，保证不中断登录流程
            if (title != null && !title.isEmpty()) player.sendMessage(title);
            if (subtitle != null && !subtitle.isEmpty()) player.sendMessage(subtitle);
        }
    }

    @Override
    public void sendActionBar(Player player, String message) {
        if (player == null || message == null || !player.isOnline()) return;
        try {
            // fromLegacyText 正确解析 § 颜色码为格式组件，跨 1.8 ~ 26.3 表现一致
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable t) {
            // spigot() 不可用（如纯 Bukkit 端）时降级为普通消息
            player.sendMessage(message);
        }
    }
}

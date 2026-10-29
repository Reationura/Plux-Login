package com.plux.login.adapter;

import org.bukkit.Bukkit;

import java.util.regex.Pattern;

/**
 * 版本工具类（全版本兼容，支持至 Minecraft 26.3+）
 *
 * 兼容规范（基于工业级插件开发范式）：
 * 1. 禁止使用 Bukkit.getMinecraftVersion()（1.13+ 才加入）
 * 2. NMS 版本通过 Server 类包名解析；自 1.20.5+ CraftBukkit 改为可重定位（relocatable），
 *    包名不再带版本后缀，此处通过正则严格校验，非法时判定为“无版本 NMS”
 * 3. MC 原版版本通过 getVersion() 的 (MC: ) 标记解析
 * 4. 自 2026 起官方版本号采用 YY.D.H 规则（如 26.1、26.3、26.3.1），
 *    同时兼容社区仍使用的 1.26.x 别名写法，解析时统一归一化
 */
public final class VersionUtil {

    /** 经典 NMS 包版本格式：v1_8_R3、v1_20_R1 ... */
    private static final Pattern CLASSIC_NMS_PATTERN = Pattern.compile("^v\\d+_\\d+_R\\d+$");

    // 所有字段 volatile，保证异步线程读取到已完成初始化的快照（initialized 最后写入）
    private static volatile String nmsVersion = "";
    private static volatile boolean versionedNms = false;
    private static volatile String mcVersion = "1.8";
    private static volatile int majorVersion = 1;
    private static volatile int minorVersion = 8;
    private static volatile int patchVersion = 0;
    private static volatile boolean initialized = false;

    private VersionUtil() {}

    /**
     * 初始化版本信息（线程安全，仅执行一次）
     */
    public static synchronized void init() {
        if (initialized) return;

        // 1. 获取 NMS 包版本
        //    - 经典（<=1.20.4）：org.bukkit.craftbukkit.v1_20_R1 -> "v1_20_R1"
        //    - relocatable（1.20.5+/26.x）：org.bukkit.craftbukkit -> 无版本后缀
        boolean verNms = false;
        String nms = "";
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String last = pkg.substring(pkg.lastIndexOf('.') + 1);
            if (CLASSIC_NMS_PATTERN.matcher(last).matches()) {
                nms = last;
                verNms = true;
            }
            // 否则保持空串：新版 CB 已移除包版本段，反射型 NMS 需走 net.minecraft.* 布局
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PluxLogin] 无法解析 NMS 包版本: " + e.getMessage());
        }

        // 2. 获取 MC 原版版本（返回如 26.3、1.21.4、1.7.10）
        String ver;
        try {
            String full = Bukkit.getVersion();
            int start = full.indexOf("(MC: ");
            if (start >= 0) {
                start += 5; // 跳过 "(MC: "
                int end = full.indexOf(')', start);
                ver = (end > start) ? full.substring(start, end).trim() : full.trim();
            } else {
                // 降级方案：使用 bukkitVersion（如 "26.3-R0.1-SNAPSHOT" / "1.20.1-R0.1-SNAPSHOT"）
                ver = Bukkit.getBukkitVersion().split("-")[0].trim();
            }
        } catch (Exception e) {
            ver = "1.8";
        }

        // 3. 解析主次版本号（含 26.x 新命名与 1.26.x 别名归一化）
        int[] parsed = parseVersionNumbers(ver);

        // 先发布数据字段，最后再置 initialized=true，避免异步线程读到半初始化状态
        nmsVersion = nms;
        versionedNms = verNms;
        mcVersion = ver;
        majorVersion = parsed[0];
        minorVersion = parsed[1];
        patchVersion = parsed[2];
        initialized = true;

        Bukkit.getLogger().info("[PluxLogin] 版本检测完成 -> MC: " + mcVersion
                + " (v" + majorVersion + "." + minorVersion + "." + patchVersion + ")"
                + ", NMS: " + (nmsVersion.isEmpty() ? "relocatable(无版本包名)" : nmsVersion));
    }

    /**
     * 获取经典 NMS 包名版本（如 v1_20_R1、v1_7_R4）
     * 在 CraftBukkit 可重定位版本（1.20.5+/26.x）下返回空串，
     * 此时应使用 {@link #isVersionedNms()} 判定后再走反射
     */
    public static String getNMSVersion() {
        if (!initialized) init();
        return nmsVersion;
    }

    /**
     * 服务端是否使用“带版本号”的经典 NMS/CB 包布局。
     * true  -> 1.20.4 及以下（net.minecraft.server.vX_Y_RZ）
     * false -> 1.20.5+ / 26.x（relocatable，net.minecraft.*）
     */
    public static boolean isVersionedNms() {
        if (!initialized) init();
        return versionedNms;
    }

    /**
     * 获取 Minecraft 原版版本号（如 26.3、1.21.4、1.7.10）
     */
    public static String getMcVersion() {
        if (!initialized) init();
        return mcVersion;
    }

    /** 获取主版本号（旧体系为 1；2026 新体系为年份，如 26） */
    public static int getMajorVersion() {
        if (!initialized) init();
        return majorVersion;
    }

    /** 获取次版本号（旧体系为 8/20/21…；新体系为当年发布序号） */
    public static int getMinorVersion() {
        if (!initialized) init();
        return minorVersion;
    }

    /** 获取补丁 / 热修复版本号 */
    public static int getPatchVersion() {
        if (!initialized) init();
        return patchVersion;
    }

    // ========== 版本比较内核 ==========

    /**
     * 将当前版本与 (maj, min) 比较：当前 > 返回 1，== 返回 0，< 返回 -1。
     * 由于 2026 起主版本号（年份，如 26）远大于旧体系恒定的 1，
     * 跨命名体系的大小比较天然成立（1.x < 26.x）。
     */
    private static int compareTo(int maj, int min) {
        if (!initialized) init();
        if (majorVersion != maj) return Integer.compare(majorVersion, maj);
        return Integer.compare(minorVersion, min);
    }

    // ========== 版本判断辅助方法 ==========

    /** 是否低于 1.8（即 1.7.x，需要特殊 NMS 适配） */
    public static boolean isBelow_1_8() {
        return compareTo(1, 8) < 0;
    }

    /** 是否为 1.12 及以下版本（ID 系统变更分界线） */
    public static boolean isBelowOrEqual_1_12() {
        return compareTo(1, 12) <= 0;
    }

    /** 是否为 1.16.5 及以下版本（Java 分界线） */
    public static boolean isBelowOrEqual_1_16() {
        return compareTo(1, 16) <= 0;
    }

    /** 是否为 1.20.4 及以下版本（经典带版本 NMS 包布局的最后区间） */
    public static boolean isBelowOrEqual_1_20_4() {
        if (!initialized) init();
        int c = compareTo(1, 20);
        if (c != 0) return c < 0;
        return patchVersion <= 4;
    }

    /** 是否为 1.25.x 或更高版本 */
    public static boolean isAtLeast_1_25() {
        return compareTo(1, 25) >= 0;
    }

    /** 是否为 26.1+ 版本（官方 2026 新命名规则） */
    public static boolean isAtLeast_26_1() {
        return compareTo(26, 1) >= 0;
    }

    /** 是否为 26.3+ 版本（本次适配的目标版本） */
    public static boolean isAtLeast_26_3() {
        return compareTo(26, 3) >= 0;
    }

    /** 通用“至少”判断：当前版本 >= (maj, min) */
    public static boolean isAtLeast(int maj, int min) {
        return compareTo(maj, min) >= 0;
    }

    // ========== 内部方法 ==========

    /**
     * 解析版本号为 [major, minor, patch]，同时兼容：
     * - 旧体系 1.x.y（1.7.10 / 1.20.1 / 1.21.4）
     * - 新体系 YY.D / YY.D.H（26.1 / 26.3 / 26.3.1）
     * - 社区别名 1.26.x：旧体系次版本不可能 > 21，故 major==1 且 minor>21 视为
     *   新体系别名，归一化为 (minor, patch, 0)，即 1.26.3 -> 26.3
     */
    private static int[] parseVersionNumbers(String version) {
        int major = 1, minor = 8, patch = 0;
        try {
            // 去掉可能携带的 -snapshot / -rc / pre 等后缀，仅取数字段
            String cleaned = version.trim();
            int dash = cleaned.indexOf('-');
            if (dash >= 0) cleaned = cleaned.substring(0, dash);

            String[] parts = cleaned.split("\\.");
            int[] nums = new int[]{major, minor, patch};
            for (int i = 0; i < parts.length && i < 3; i++) {
                String p = parts[i].replaceAll("[^0-9]", "");
                if (!p.isEmpty()) nums[i] = Integer.parseInt(p);
            }
            major = nums[0];
            minor = nums[1];
            patch = nums[2];

            // 别名归一化：1.26.x 实为社区对 26.x 的旧式书写
            if (major == 1 && minor > 21) {
                major = minor;
                minor = patch;
                patch = 0;
            }
        } catch (NumberFormatException e) {
            // 解析失败回退默认值
            major = 1;
            minor = 8;
            patch = 0;
        }
        return new int[]{major, minor, patch};
    }
}

package com.plux.login.adapter;

import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具类（全版本兼容）
 *
 * 核心规范：
 * 1. 禁止直接引用 NMS 类，通过反射封装调用
 * 2. 所有反射结果必须缓存，降低性能损耗
 * 3. 异常静默处理，避免崩溃影响服务端运行
 */
public final class ReflectionUtil {

    // ========== 缓存容器（线程安全）==========

    /** 类缓存 */
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /** 方法缓存 */
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    /** 字段缓存 */
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    /** 构造器缓存 */
    private static final Map<String, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private ReflectionUtil() {}

    // ========== 类操作 ==========

    /**
     * 获取 NMS 类（带缓存）
     *
     * @param name NMS 类名（不含包名前缀），如 "EntityPlayer"、"PacketPlayOutChat"
     * @return 对应的 Class 对象，失败返回 null
     */
    public static Class<?> getNmsClass(String name) {
        // 1.20.5+/26.x 的 CraftBukkit 已可重定位，不再有 net.minecraft.server.vX_Y_RZ 布局，
        // 此时经典反射路径无效（现代适配器不使用反射，正常不会走到这里）
        if (!VersionUtil.isVersionedNms()) {
            warnUnversionedOnce("NMS:" + name);
            return null;
        }
        String fullName = "net.minecraft.server." + VersionUtil.getNMSVersion() + "." + name;

        Class<?> cached = CLASS_CACHE.get(fullName);
        if (cached != null) return cached;

        try {
            Class<?> clazz = Class.forName(fullName);
            CLASS_CACHE.put(fullName, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().warning("[PluxLogin] 反射获取 NMS 类失败: " + fullName);
            return null;
        }
    }

    /**
     * 获取 CraftBukkit 类（带缓存）
     *
     * @param name CB 类名，如 "CraftPlayer"
     * @return 对应的 Class 对象
     */
    public static Class<?> getCraftbukkitClass(String name) {
        if (!VersionUtil.isVersionedNms()) {
            warnUnversionedOnce("CB:" + name);
            return null;
        }
        String fullName = "org.bukkit.craftbukkit." + VersionUtil.getNMSVersion() + "." + name;

        Class<?> cached = CLASS_CACHE.get(fullName);
        if (cached != null) return cached;

        try {
            Class<?> clazz = Class.forName(fullName);
            CLASS_CACHE.put(fullName, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().warning("[PluxLogin] 反射获取 CB 类失败: " + fullName);
            return null;
        }
    }

    // ========== 方法操作 ==========

    /**
     * 获取方法（带缓存）
     *
     * @param clazz   目标类
     * @param name    方法名
     * @param params  参数类型
     * @return Method 对象，失败返回 null
     */
    public static Method getMethod(Class<?> clazz, String name, Class<?>... params) {
        if (clazz == null) return null;

        String key = buildMethodKey(clazz.getName(), name, params);

        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        try {
            Method m = clazz.getDeclaredMethod(name, params);
            m.setAccessible(true);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (NoSuchMethodException e) {
            // 尝试公共方法
            try {
                Method m = clazz.getMethod(name, params);
                METHOD_CACHE.put(key, m);
                return m;
            } catch (NoSuchMethodException ex) {
                Bukkit.getLogger().fine("[PluxLogin] 反射获取方法失败: " + key);
                return null;
            }
        }
    }

    /**
     * 调用方法（安全封装）
     *
     * @param method 目标方法
     * @param target 目标对象（静态方法为 null）
     * @param args   参数列表
     * @return 返回值，异常返回 null
     */
    public static Object invoke(Method method, Object target, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PluxLogin] 反射调用方法失败: " + method.getName() + " -> " + e.getMessage());
            return null;
        }
    }

    // ========== 字段操作 ==========

    /**
     * 获取字段（带缓存）
     *
     * @param clazz 目标类
     * @param name  字段名
     * @return Field 对象，失败返回 null
     */
    public static Field getField(Class<?> clazz, String name) {
        if (clazz == null) return null;

        String key = clazz.getName() + "." + name;

        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;

        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            FIELD_CACHE.put(key, field);
            return field;
        } catch (NoSuchFieldException e) {
            // 尝试父类
            Class<?> superClass = clazz.getSuperclass();
            while (superClass != null && superClass != Object.class) {
                try {
                    Field field = superClass.getDeclaredField(name);
                    field.setAccessible(true);
                    FIELD_CACHE.put(key, field);
                    return field;
                } catch (NoSuchFieldException ignored) {}
                superClass = superClass.getSuperclass();
            }
            Bukkit.getLogger().fine("[PluxLogin] 反射获取字段失败: " + key);
            return null;
        }
    }

    /**
     * 获取字段值（安全封装）
     */
    public static Object getFieldValue(Object target, String fieldName) {
        Field field = getField(target.getClass(), fieldName);
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 设置字段值（安全封装）
     */
    public static boolean setFieldValue(Object target, String fieldName, Object value) {
        Field field = getField(target.getClass(), fieldName);
        if (field == null) return false;
        try {
            field.set(target, value);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    // ========== 构造器操作 ==========

    /**
     * 获取构造器（带缓存）
     */
    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... params) {
        if (clazz == null) return null;

        String key = buildConstructorKey(clazz.getName(), params);

        Constructor<?> cached = CONSTRUCTOR_CACHE.get(key);
        if (cached != null) return cached;

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(params);
            constructor.setAccessible(true);
            CONSTRUCTOR_CACHE.put(key, constructor);
            return constructor;
        } catch (NoSuchMethodException e) {
            Bukkit.getLogger().fine("[PluxLogin] 反射获取构造器失败: " + key);
            return null;
        }
    }

    /**
     * 创建实例（安全封装）
     */
    public static Object newInstance(Constructor<?> constructor, Object... args) {
        if (constructor == null) return null;
        try {
            return constructor.newInstance(args);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PluxLogin] 反射创建实例失败: " + constructor.getDeclaringClass().getSimpleName());
            return null;
        }
    }

    // ========== 实体/数据操作快捷方法 ==========

    /**
     * 获取玩家 NMS EntityPlayer 对象
     * 全版本通用：先尝试 getHandle()，再尝试反射方式
     */
    public static Object getEntityPlayer(org.bukkit.entity.Player player) {
        // 直接 API 方式（1.5+ 可用）
        try {
            return player.getClass().getMethod("getHandle").invoke(player);
        } catch (Exception ignored) {}

        // 反射降级方式
        Method handleMethod = getMethod(player.getClass(), "getHandle");
        return invoke(handleMethod, player);
    }

    /**
     * 获取 PlayerConnection 对象
     */
    public static Object getPlayerConnection(Object entityPlayer) {
        if (entityPlayer == null) return null;
        Object connection = getFieldValue(entityPlayer, "playerConnection");
        if (connection == null) {
            connection = getFieldValue(entityPlayer, "connection"); // 1.17+ 字段名变更
        }
        return connection;
    }

    /** 无版本 NMS 布局下仅告警一次，避免刷屏 */
    private static final java.util.Set<String> UNVERSIONED_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnUnversionedOnce(String tag) {
        if (UNVERSIONED_WARNED.add(tag)) {
            Bukkit.getLogger().fine("[PluxLogin] 当前服务端为可重定位 NMS（1.20.5+/26.x），经典反射不可用: " + tag);
        }
    }

    // ========== 内部工具方法 ==========

    private static String buildMethodKey(String className, String methodName, Class<?>... params) {
        StringBuilder sb = new StringBuilder(className).append(".").append(methodName).append("(");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(params[i].getName());
        }
        sb.append(")");
        return sb.toString();
    }

    private static String buildConstructorKey(String className, Class<?>... params) {
        StringBuilder sb = new StringBuilder(className).append(".<init>(");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(params[i].getName());
        }
        sb.append(")");
        return sb.toString();
    }

    // ========== 缓存统计（调试用）==========

    public static int getClassCacheSize() { return CLASS_CACHE.size(); }
    public static int getMethodCacheSize() { return METHOD_CACHE.size(); }
    public static int getFieldCacheSize() { return FIELD_CACHE.size(); }
    public static int getConstructorCacheSize() { return CONSTRUCTOR_CACHE.size(); }
}

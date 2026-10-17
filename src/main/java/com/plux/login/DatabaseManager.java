package com.plux.login;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;

public class DatabaseManager {

    private final PluxLogin plugin;
    private HikariDataSource dataSource;
    private String dbType;

    public DatabaseManager(PluxLogin plugin) {
        this.plugin = plugin;
        this.dbType = "sqlite";
    }

    public void initialize() {
        try {
            dbType = plugin.getConfig().getString("database.type", "sqlite");
            if ("mysql".equalsIgnoreCase(dbType)) {
                initMySQL();
            } else {
                initSQLite();
            }
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "数据库初始化失败", e);
        }
    }

    private void initSQLite() {
        String path = plugin.getConfig().getString("database.sqlite.path", "plugins/PluxLogin/data.db");
        HikariConfig config = new HikariConfig();
        config.setPoolName("PluxLogin-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + path);
        config.setMaximumPoolSize(1); // SQLite 单连接
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(0); // SQLite 不需要回收连接
        dataSource = new HikariDataSource(config);
    }

    private void initMySQL() {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "pluxlogin");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");

        HikariConfig config = new HikariConfig();
        config.setPoolName("PluxLogin-MySQL");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&useUnicode=true&characterEncoding=utf8"
                + "&autoReconnect=true&rewriteBatchedStatements=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        dataSource = new HikariDataSource(config);
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(16) NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "email VARCHAR(255), " +
                "qq VARCHAR(20), " +
                "totp_secret VARCHAR(255), " +
                "last_login BIGINT DEFAULT 0, " +
                "register_time BIGINT DEFAULT 0" +
                ")";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("数据源未初始化或已关闭");
        }
        return dataSource.getConnection();
    }

    // ========== 玩家注册状态 ==========

    public boolean isPlayerRegistered(UUID uuid) {
        String sql = "SELECT 1 FROM players WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询玩家注册状态失败", e);
            return false;
        }
    }

    public boolean isPlayerRegisteredByName(String username) {
        String sql = "SELECT 1 FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "按名称查询玩家注册状态失败", e);
            return false;
        }
    }

    // ========== 注册 / 登录时间 ==========

    public boolean registerPlayer(UUID uuid, String username, String passwordHash) {
        String sql = "INSERT INTO players (uuid, username, password_hash, last_login, register_time) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, username);
            pstmt.setString(3, passwordHash);
            pstmt.setLong(4, now);
            pstmt.setLong(5, now);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "玩家注册失败", e);
            return false;
        }
    }

    public boolean updateLastLogin(UUID uuid) {
        return executeUpdate("UPDATE players SET last_login = ? WHERE uuid = ?",
                System.currentTimeMillis(), uuid.toString());
    }

    // ========== 密码管理 ==========

    public boolean updatePassword(UUID uuid, String newPasswordHash) {
        return executeUpdate("UPDATE players SET password_hash = ? WHERE uuid = ?",
                newPasswordHash, uuid.toString());
    }

    public String getPasswordHash(UUID uuid) {
        return queryString("SELECT password_hash FROM players WHERE uuid = ?", uuid.toString());
    }

    // ========== 邮箱管理 ==========

    public boolean updateEmail(UUID uuid, String email) {
        return executeUpdate("UPDATE players SET email = ? WHERE uuid = ?", email, uuid.toString());
    }

    public String getEmail(UUID uuid) {
        return queryString("SELECT email FROM players WHERE uuid = ?", uuid.toString());
    }

    public boolean isEmailBound(String email) {
        return queryExists("SELECT 1 FROM players WHERE email = ?", email);
    }

    public boolean hasEmail(UUID uuid) {
        String email = getEmail(uuid);
        return email != null && !email.isEmpty();
    }

    // ========== QQ 管理 ==========

    public boolean updateQQ(UUID uuid, String qq) {
        return executeUpdate("UPDATE players SET qq = ? WHERE uuid = ?", qq, uuid.toString());
    }

    public String getQQ(UUID uuid) {
        return queryString("SELECT qq FROM players WHERE uuid = ?", uuid.toString());
    }

    public boolean isQQBound(String qq) {
        return queryExists("SELECT 1 FROM players WHERE qq = ?", qq);
    }

    public boolean hasQQ(UUID uuid) {
        String qq = getQQ(uuid);
        return qq != null && !qq.isEmpty();
    }

    // ========== TOTP 管理 ==========

    public boolean updateTotpSecret(UUID uuid, String secret) {
        return executeUpdate("UPDATE players SET totp_secret = ? WHERE uuid = ?", secret, uuid.toString());
    }

    public String getTotpSecret(UUID uuid) {
        return queryString("SELECT totp_secret FROM players WHERE uuid = ?", uuid.toString());
    }

    // ========== 删除玩家 ==========

    public boolean deletePlayer(String username) {
        String sql = "DELETE FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "删除玩家失败", e);
            return false;
        }
    }

    public boolean deletePlayer(UUID uuid) {
        // 直接按 uuid 删除，避免因玩家改名后按 username 删除导致删错行或删不到
        String sql = "DELETE FROM players WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "按 UUID 删除玩家失败", e);
            return false;
        }
    }

    // ========== 综合查询 ==========

    public PlayerData getPlayerData(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("email"),
                            rs.getString("qq"),
                            rs.getString("totp_secret"),
                            rs.getLong("last_login"),
                            rs.getLong("register_time")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家数据失败", e);
        }
        return null;
    }

    public Long getLastLogin(UUID uuid) {
        String sql = "SELECT last_login FROM players WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getLong("last_login");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取最后登录时间失败", e);
        }
        return null;
    }

    public String getPlayerNameByUUID(UUID uuid) {
        return queryString("SELECT username FROM players WHERE uuid = ?", uuid.toString());
    }

    public UUID getUUIDByPlayerName(String username) {
        String sql = "SELECT uuid FROM players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "通过名称获取UUID失败", e);
        }
        return null;
    }

    // ========== 兼容别名 ==========

    public boolean updateLoginTime(UUID uuid) { return updateLastLogin(uuid); }
    public boolean updateTOTPSecret(UUID uuid, String secret) { return updateTotpSecret(uuid, secret); }

    // ========== 通用数据库操作方法（消除重复代码）==========

    /**
     * 执行更新/删除操作（内部处理异常）
     */
    private boolean executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "执行更新失败: " + sql, e);
            return false;
        }
    }

    /**
     * 查询单个字符串值
     */
    private String queryString(String sql, Object... params) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "查询字符串失败: " + sql, e);
            return null;
        }
    }

    /**
     * 检查记录是否存在
     */
    private boolean queryExists(String sql, Object... params) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "检查存在性失败: " + sql, e);
            return false;
        }
    }

    // ========== 关闭 ==========

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接池已关闭");
        }
    }

    /** 检查连接是否健康 */
    public boolean isHealthy() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否使用 MySQL */
    public boolean isMySQL() {
        return "mysql".equalsIgnoreCase(dbType);
    }

    /** 获取已注册玩家总数 */
    public int getRegisteredCount() {
        String sql = "SELECT COUNT(*) FROM players";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "获取注册玩家数量失败", e);
        }
        return 0;
    }
}

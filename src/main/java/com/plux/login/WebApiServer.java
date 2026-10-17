package com.plux.login;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class WebApiServer {

    private final PluxLogin plugin;
    private HttpServer server;
    private boolean running = false;
    private long startTime;

    public WebApiServer(PluxLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动 Web API 服务器
     */
    public void start() {
        if (!plugin.getConfigManager().isWebApiEnabled()) return;
        this.startTime = System.currentTimeMillis();

        int port = plugin.getConfigManager().getWebApiPort();
        String secret = plugin.getConfigManager().getWebApiSecret();

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            final String apiSecret = (secret == null) ? "" : secret;
            server.createContext("/api/", exchange -> {
                try {
                    // CORS
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

                    // 鉴权检查：密钥为空时直接拒绝，避免未配置时误开放
                    if (apiSecret.isEmpty()) {
                        sendJson(exchange, 503, "{\"error\":\"Web API secret not configured\",\"code\":503}");
                        return;
                    }
                    String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                    if (authHeader == null || !constantTimeEquals("Bearer " + apiSecret, authHeader)) {
                        sendJson(exchange, 401, "{\"error\":\"Unauthorized\",\"code\":401}");
                        return;
                    }

                    String path = exchange.getRequestURI().getPath();
                    switch (path) {
                        case "/api/status":
                            sendJson(exchange, 200, handleStatus());
                            break;
                        case "/api/players":
                            sendJson(exchange, 200, handlePlayers());
                            break;
                        case "/api/player":
                            String query = exchange.getRequestURI().getQuery();
                            if (query != null && query.startsWith("name=")) {
                                sendJson(exchange, 200, handlePlayerInfo(query.substring(5)));
                            } else {
                                sendJson(exchange, 400, "{\"error\":\"Missing parameter: name\"}");
                            }
                            break;
                        default:
                            sendJson(exchange, 404, "{\"error\":\"Not Found\",\"code\":404}");
                            break;
                    }
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"Internal Server Error\"}");
                } finally {
                    exchange.close();
                }
            });

            server.setExecutor(null); // 使用默认执行器
            server.start();
            running = true;
            plugin.getLogger().info("[PluxLogin] Web API 已启动，端口: " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("[PluxLogin] Web API 启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止 Web API 服务器
     */
    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            plugin.getLogger().info("[PluxLogin] Web API 已停止");
        }
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int code, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException ignored) {}
    }

    /**
     * 将依赖 Bukkit 主线程 API 的读取提交到主线程执行并阻塞等待结果，
     * 避免从 HTTP 线程直接访问 Bukkit 导致并发异常（在 Folia 上尤其危险）。
     */
    private <T> T callSync(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    /** 常数时间比较，避免鉴权密钥比较时的时序侧信道 */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 转义 JSON 字符串中的特殊字符，防止玩家名等字段破坏 JSON 结构 */
    private String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========== API 处理方法 ==========

    /** 服务器状态接口 */
    private String handleStatus() {
        int[] counts = callSync(() -> new int[]{ Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers() });
        int online = counts != null ? counts[0] : 0;
        int max = counts != null ? counts[1] : 0;
        int registered = 0;
        try { registered = plugin.getDatabaseManager().getRegisteredCount(); } catch (Exception ignored) {}
        boolean mysql;
        try { mysql = plugin.getDatabaseManager().isMySQL(); } catch (Exception ignored) { mysql = false; }

        return "{"
                + "\"plugin\":\"PluxLogin\","
                + "\"version\":\"" + jsonEscape(plugin.getVersion()) + "\","
                + "\"online\":" + online + ","
                + "\"maxPlayers\":" + max + ","
                + "\"registered\":" + registered + ","
                + "\"database\":\"" + (mysql ? "mysql" : "sqlite") + "\","
                + "\"uptime\":" + (System.currentTimeMillis() - getPluginStartTime()) / 1000
                + "}";
    }

    /** 在线玩家列表接口 */
    private String handlePlayers() {
        String result = callSync(this::collectPlayers);
        return result != null ? result : "[]";
    }

    /** 收集在线玩家（必须在主线程执行） */
    private String collectPlayers() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            PluxLogin.PlayerSession session = plugin.getPlayerSession(p.getUniqueId());
            if (!first) sb.append(",");
            first = false;
            sb.append("{")
                    .append("\"name\":\"").append(jsonEscape(p.getName())).append("\",")
                    .append("\"uuid\":\"").append(p.getUniqueId().toString()).append("\",")
                    .append("\"loggedIn\":").append(session != null && session.isLoggedIn())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /** 玩家信息查询接口 */
    private String handlePlayerInfo(String playerName) {
        Object[] info = callSync(() -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
            if (op == null || !op.hasPlayedBefore()) return null;
            return new Object[]{ op.getUniqueId(), op.isOnline() };
        });
        if (info == null) {
            return "{\"error\":\"Player not found\"}";
        }

        UUID uuid = (UUID) info[0];
        boolean online = (Boolean) info[1];
        PlayerData data = null;
        try { data = plugin.getDatabaseManager().getPlayerData(uuid); } catch (Exception ignored) {}

        if (data == null) {
            return "{\"error\":\"Player data not found in database\"}";
        }

        return "{"
                + "\"name\":\"" + jsonEscape(playerName) + "\","
                + "\"uuid\":\"" + uuid.toString() + "\","
                + "\"registered\":" + (data.getPasswordHash() != null && !data.getPasswordHash().isEmpty()) + ","
                + "\"email\":\"" + jsonEscape(data.getEmail() != null ? data.getEmail() : "") + "\","
                + "\"qq\":\"" + jsonEscape(data.getQq() != null ? data.getQq() : "") + "\","
                + "\"2FAEnabled\":" + data.is2FAEnabled() + ","
                + "\"isOnline\":" + online
                + "}";
    }

    public boolean isRunning() { return running; }

    private long getPluginStartTime() { return startTime; }
}

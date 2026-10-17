# PluxLogin 更新日志

- **配置文件版本**：config.yml v2.0.2（message.yml 结构未变）
- **插件版本**：PluxLogin v2.0.2
- **构建产物**：`PluxLogin-2.0.2.jar`（约 13.4 MB / 14,047,454 字节，maven-shade 打包，内含 SQLite-JDBC 3.42.0.0、HikariCP 4.0.3、commons-email 1.5）
- **构建校验**：`mvn clean package` → BUILD SUCCESS（Java 1.8 target）
- **兼容范围**：Minecraft 1.7.10 ~ 1.26.1+，Spigot / Paper / Purpur / Folia（folia-supported）
- **更新日期**：2026-10-17

---

## 【修复】

- **修复：配置项读取路径与 config.yml 严重不一致，导致几乎所有设置失效**
  - 原因：`ConfigManager` 读取的是 `login.timeout`、`registration.min-password-length`、`captcha.validity-time`、`global-action-cooldown`、`anti-bot.min-interval-ms`、`anti-bot.temp-ban-duration-seconds` 等键，而 `config.yml`（及 README）实际使用的是 `timeouts.login`、`password.min-length`、`captcha.timeout`、`security.action-cooldown`、`anti-bot.min-interval`、`anti-bot.temp-ban-duration`。二者完全不匹配，代码永远走内置默认值，管理员改配置毫无效果。
  - 方案：将超时、密码规则（长度/正则）、验证码时长、防机器人间隔与封禁时长、踢出阈值、全局冷却等 getter 全部对齐到 config.yml 的真实键名，默认值同步为文档值。
  - 影响：登录/注册超时、密码复杂度、验证码、防机器人、冷却等配置真正生效；行为回归设计预期。

- **修复：踢出惩罚命令从未执行且占位符不替换**
  - 原因：`getKickCommands()` 读取不存在的 `login.kick-commands`（返回空列表），而惩罚命令实际定义在 `security.kick-command`；同时 `executeKickCommands` 只替换 `{player}`，而配置使用 `%player%`。结果是“达到最大踢出次数后执行 ban”这一逻辑从不触发，即使命令存在也会因 `%player%` 未替换而报错。
  - 方案：改为读取 `security.kick-command`（兼容列表写法），并同时替换 `{player}` 与 `%player%`。
  - 影响：反滥用惩罚链恢复正常。

- **修复：临时封禁玩家被踢时收到空白提示**
  - 原因：`getAntiBotTempBanKickMessage()` 读取 `message.yml` 中不存在的 `anti-bot.temp-ban-kick`，返回空前缀字符串。
  - 方案：改指向已存在的 `login.temp-ban-kick`（含 `{time}` 占位符）。
  - 影响：封禁玩家能看到“请在 X 秒后再尝试加入”的正确提示。

- **修复：`/pluxlogin version` 永远显示 UNKNOWN**
  - 原因：`getMainCommandVersion()` 先把 `{version}` 占位符替换成 `UNKNOWN`，调用方随后再对 `{version}` 做真实版本替换时占位符已消失。
  - 方案：getter 保留 `{version}` 原样，交由调用方替换真实版本号。
  - 影响：管理员能正确查看插件版本。

- **修复：数据库异常导致命令线程抛出 NullPointerException（多处）**
  - 原因：`getPlayerData()` 在 SQL 异常时返回 `null`，而登录、邮箱/QQ 绑定、改密/改邮/改 QQ、邮箱/QQ 找回密码、2FA 开启与确认等命令在取到数据后直接 `data.getXxx()`，一旦 DB 抖动即 NPE，玩家操作无响应。
  - 方案：在 10 处命令入口补 `data == null` 守卫，统一回复“数据库错误”提示并安全返回。
  - 影响：DB 故障时不再崩服/无反馈，给出友好错误信息。

- **修复：`/regdel` 按用户名删除导致删错行或删不到**
  - 原因：`deletePlayer(UUID)` 先查 `username` 再 `DELETE WHERE username=?`，玩家改名后可能命中旧行或查不到；且多一次无谓查询。
  - 方案：改为直接 `DELETE FROM players WHERE uuid=?`。
  - 影响：管理员删除注册数据更准确、原子。

- **修复：Web API 访问未知路径时抛 IllegalStateException**
  - 原因：`/api/` 处理器的 `default` 分支先调用 `exchange.sendResponseHeaders(404, 0)`，随后又统一执行 `sendJson(exchange, 200, …)` 再次写响应头，触发重复发送头异常。
  - 方案：取消预发响应头，状态码统一由 `sendJson` 一次性写出（未知路径返回 404）。
  - 影响：错误路径不再抛异常，接口更健壮。

- **修复：默认 465 端口 SMTP 无法发信**
  - 原因：`EmailManager` 无论端口一律启用 STARTTLS，而 config.yml 默认 `smtp.qq.com:465` 属隐式 SSL（SMTPS），握手失败导致验证码邮件发不出去。
  - 方案：端口为 465 时启用 `mail.smtp.ssl.enable` 与 `SSLSocketFactory`，其余端口保持 STARTTLS；补充 `writetimeout`，并在 `mailSession` 未初始化时安全返回。
  - 影响：按默认配置即可正常收发邮件。

- **修复：更新检查全局关闭 TLS 主机名校验（安全隐患）**
  - 原因：`fetchLatestVersion()` 调用 `HttpsURLConnection.setDefaultHostnameVerifier((h,s)->true)`，会作用于整个 JVM，构成中间人攻击风险。
  - 方案：移除该全局信任设置，改用 JVM 默认证书校验。
  - 影响：消除对全 JVM 的安全污染。

## 【优化】

- **线程安全优化**：更新通知此前在异步定时器线程与“每次登录”的异步任务中直接对玩家 `sendMessage`；Web API 在 HTTP 线程直接调用 `Bukkit.getOnlinePlayers()/getOfflinePlayer()`。现统一将玩家消息回主线程发送，Web API 的 Bukkit 读取通过 `callSync()` 提交主线程并限时（5s）返回，显著降低跨线程并发异常风险。

- **登录不再发起网络请求**：`completeLogin()` 原先每次登录都异步 `fetchLatestVersion()`（一次 HTTP）。改为由后台定时检查缓存最新版本，登录通知读取缓存 `getLatestVersion()`，去除登录链路网络开销与延迟。

- **更新检查器稳定性**：读空响应不再 NPE（`readLine()` 判空），请求连接与缓冲流改用 `try-with-resources` + `finally disconnect()`，超时 30s→10s，移除每请求多行 info 日志与 `printStackTrace()`，噪声更低、无资源泄漏。

- **Web API 安全加固**：密钥为空时直接返回 503，避免“忘配密钥即裸奔”；鉴权改为常数时间比较（`MessageDigest.isEqual`）抵御时序侧信道；响应输出流 `try-with-resources`、异常统一 500、`exchange.close()`；对玩家名/邮箱/QQ 等字段做 JSON 转义，防止特殊字符破坏响应结构。

- **GUI 跨版本兼容**：`InventoryGui` 原直接引用 `Material.PLAYER_HEAD`、`COMMAND_BLOCK` 等枚举常量，在 1.7.x 等旧版本缺失会抛 `NoSuchFieldError`。改为 `Material.matchMaterial` 字符串查找并在缺失时降级为 `PAPER`，`openAdminGUI` 包裹 try/catch。老服开启 GUI 不再崩溃。

- **移除死代码/冲突实现**：删除从未被调用、且硬编码阈值（5 秒 / 6 次）与配置化逻辑相冲突的 `PluxLogin.checkFastJoin()`，快速重入检测统一由读取 config 的 `PlayerListener.isFastRejoin()` 承担。

## 【新增】

- 本次为版本对齐与稳定性/兼容性修复，**无新增功能项**。
- 附：`config.yml` 补充此前缺失、导致“更新检查器无法开关”的 `update-checker.enabled` 配置段，使该行为可被管理员控制（属配置可用性补齐，非新功能）。

## 【其他】

- **版本统一**：`pom.xml`、`plugin.yml`、`config.yml`（文件头及三处版本标注）、`README.md`（徽章与开发者信息）全部统一为 **2.0.2**；Java 代码通过 `getDescription().getVersion()` 动态读取 plugin.yml，不存在硬编码版本，保持一致。
- **关于 2FA / 强制邮箱登录门控**：审查发现 `PlayerListener.handlePostLogin()`（2FA、强制邮箱进入拦截）从未被 `completeLogin()` 调用，且其配套的登录期验证码校验命令、超时/完成链路并不完整；若贸然接入会使已开启 2FA/强制邮箱的玩家卡在验证态、无法正常登录。为保证“登录流程稳定可用”这一原有设计目标，本次**未接入该门控**，仅修正其误导性注释并保留代码，待后续版本完善后再启用。
- **构建校验**：`mvn clean package` 通过，产物 `target/PluxLogin-2.0.2.jar`。
- **遗留说明（未改动，避免破坏兼容）**：`Utils.hashPassword` 仍为无盐 SHA-256，改为加盐会使现有数据库密码全部失效，属于需配套“渐进式重哈希”的功能级改造，不在本次“修复+优化、不改变既有行为”范围内，建议列入后续版本规划。

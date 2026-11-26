# PluxLogin

> 高性能、高安全 Minecraft 登录注册插件 - BCrypt 密码渐进升级 / AES-256-GCM 字段加密 / AuthMe 导入 / 本地网页管理 / REST API | 兼容 1.7.10 ~ 26.3+

![Version](https://img.shields.io/badge/version-2.1.0-orange)
![Java](https://img.shields.io/badge/Java-8-blue)
![Folia](https://img.shields.io/badge/Folia-supported-green)
![Security](https://img.shields.io/badge/Security-AES--256--GCM--BCrypt-red)

## 功能特性

- 🔐 **登录/注册系统** - 基础的账号密码登录注册功能，密码默认 BCrypt（cost 12）
- 🔄 **渐进式密码哈希升级** - 旧 PluxLogin 无盐 SHA-256、AuthMe `$SHA$` / BCrypt / PBKDF2 用户首次登录自动重哈希为 BCrypt
- 🎫 **验证码系统** - 注册前需完成验证码验证，防止恶意注册
- 📧 **邮箱绑定** - 支持邮箱绑定、邮箱找回密码，敏感字段 AES-256-GCM 加密存储
- 🐧 **QQ 绑定** - 支持 QQ 号绑定与 QQ 邮箱找回密码，敏感字段 AES-256-GCM 加密存储
- 🔑 **双因素认证 (2FA/TOTP)** - 基于 TOTP 的二次验证，密钥加密存储
- 🖥️ **游戏内 GUI 管理** - 可视化仪表盘、玩家管理、危险操作 CAS 二次确认、AuthMe 导入
- 🌐 **REST API v1** - 带 Bearer 鉴权、固定窗口限流、revision CAS、分页查询，详见 [WEB-API.md](WEB-API.md)
- 🏠 **本地网页控制台** - 插件内置 HTTP 服务，仅限回环地址，独立管理员凭据，CSRF + 会话过期保护，无外部 CDN 依赖
- 📥 **AuthMe 导入** - dry-run → 确认令牌 → 真实导入三阶段，支持 SQLite 文件和 MySQL 远端
- 🛡️ **数据库字段加密** - 邮箱/QQ/TOTP 使用 AES-256-GCM + 盲索引，密钥文件 `security/keys.yml` 必须离线备份
- 🤖 **反机器人系统** - 快速加入检测、临时封禁，抵御机器人攻击
- 📩 **强制邮箱绑定** - 可配置强制新玩家绑定邮箱
- 💾 **多数据库支持** - 同时支持 SQLite（WAL + busy_timeout）和 MySQL（HikariCP 连接池）
- 💬 **Title/ActionBar 提示** - 丰富的界面提示方式，支持 Folia Region 调度
- 🎉 **欢迎动作系统** - `actions.yml` 配置加入/登录后自定义动作，支持 `[COMMAND]` / `[TP]` / 消息
- 🔄 **更新检查器** - 自动检查插件更新（后台定时，不阻塞登录）
- ⚡ **有界异步架构** - DB/密码/邮件/HTTP 四线程池独立，固定拒绝策略不阻塞服务器线程
- 🧵 **Folia 原生兼容** - `PlatformScheduler` 统一 Bukkit/Folia 任务调度，全局/玩家任务自动选择正确线程

## 兼容版本

- Minecraft 1.7.10 ~ 26.3+
- 支持 Folia 服务端
- 支持 Spigot / Paper / Purpur 等主流服务端

## 软依赖

| 插件 | 作用 |
|------|------|
| Vault | 经济系统支持（可选） |
| PlaceholderAPI | 变量支持（可选） |
| ProtocolLib | 协议库支持（可选） |

## 快速开始

### 安装

1. 下载最新版本的 `PluxLogin-2.1.0.jar`
2. 将插件放入服务器的 `plugins` 文件夹
3. 启动服务器，插件自动生成 `settings.yml` / `messages.yml` / `gui.yml` / `actions.yml` / `security/keys.yml`
4. 首次启动后请立即备份 `plugins/PluxLogin/security/keys.yml`（AES 主密钥）
5. 根据需要修改 `settings.yml` 配置文件
6. 执行 `/pluxlogin reload` 重载配置

### 旧版本升级

从 PluxLogin 2.0.x 升级时：

- 旧 `config.yml` 自动迁移到 `settings.yml`，旧文件保持不变并备份到 `backups/`
- 旧 `message.yml` 自动迁移到 `messages.yml`
- 旧 `welcome.txt` / `join.txt` 自动迁移到 `actions.yml`
- 密码字段保持旧格式；用户首次登录时会自动重哈希为 BCrypt

### 编译

项目使用 Maven 构建：

```bash
mvn clean package
```

编译完成后，jar 文件位于 `target/PluxLogin-2.1.0.jar`。

## 命令列表

### 玩家命令

| 命令 | 别名 | 描述 | 用法 |
|------|------|------|------|
| `/login` | `/l` | 登录服务器 | `/login <密码>` |
| `/register` | `/reg` | 注册账号 | `/register <密码> <重复密码>` |
| `/captcha` | - | 验证验证码 | `/captcha <验证码>` |
| `/mail` | - | 绑定邮箱 | `/mail <邮箱>` |
| `/mailc` | - | 验证邮箱绑定 | `/mailc <验证码>` |
| `/qq` | - | 绑定 QQ 号 | `/qq <QQ号>` |
| `/qqc` | - | 验证 QQ 绑定 | `/qqc <验证码>` |
| `/logout` | - | 登出服务器 | `/logout` |
| `/mailzhpass` | - | 通过邮箱重置密码 | `/mailzhpass` |
| `/qqzhpass` | - | 通过 QQ 重置密码 | `/qqzhpass` |
| `/resetpass` | - | 重置密码（未登录状态） | `/resetpass` |
| `/changepass` | `/cpss`, `/changepassword` | 修改密码 | `/changepass <原密码> <新密码>` |
| `/changemail` | `/cmail` | 修改邮箱 | `/changemail <原邮箱> <新邮箱>` |
| `/changeqq` | `/cq` | 修改 QQ 号 | `/changeqq <原QQ> <新QQ>` |
| `/2fa` | - | 管理双因素认证 | `/2fa <on\|off>` |
| `/2fac` | - | 验证双因素认证 | `/2fac <验证码>` |

### 管理员命令

| 命令 | 描述 | 用法 | 权限 |
|------|------|------|------|
| `/pluxlogin help` | 查看帮助 | - | `pluxlogin.admin` |
| `/pluxlogin reload` | 重载配置 | - | `pluxlogin.admin` |
| `/pluxlogin gui` | 打开 GUI 管理界面 | - | `pluxlogin.gui` |
| `/pluxlogin version` | 查看版本 | - | - |
| `/pluxlogin console setup` | 初始化本地网页控制台凭据 | - | `pluxlogin.admin` |
| `/pluxlogin console reset-password` | 重置控制台密码 | - | `pluxlogin.admin` |
| `/pluxlogin import authme ...` | AuthMe 数据库导入（dry-run / confirm / status） | 见 `WEB-API.md` | `pluxlogin.admin` |
| `/regdel` | 删除玩家注册数据 | `/regdel <玩家名>` | `pluxlogin.admin` |

## 权限节点

| 权限节点 | 描述 | 默认值 |
|----------|------|--------|
| `pluxlogin.admin` | 管理员权限 | op |
| `pluxlogin.gui` | 使用 GUI 管理界面 | op |
| `pluxlogin.bypass.captcha` | 跳过验证码验证 | false |
| `pluxlogin.bypass.force-email` | 跳过强制邮箱绑定 | false |

## 配置说明

### 数据库配置（settings.yml）

```yaml
storage:
  type: sqlite                    # sqlite 或 mysql
  revision: 1
  sqlite:
    path: "plugins/PluxLogin/data.db"
  mysql:
    host: 127.0.0.1
    port: 3306
    database: pluxlogin
    username: root
    password: ""
    ssl: false
    pool-size: 10
    min-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

### 密码策略

```yaml
password-policy:
  min-length: 6
  max-length: 32                   # UTF-8 编码后不得超过 BCrypt 72 字节上限
  pattern: "^[a-zA-Z0-9_!@#$%^&*]+$"
  bcrypt-cost: 12                  # 安全范围 10-14，超出配置拒绝启动
```

### Web API 配置

```yaml
api:
  enabled: false
  bind-address: 127.0.0.1          # 务必只绑定回环地址
  port: 8701
  secret: ""                       # 至少 32 字符，建议 openssl rand -hex 24 生成
  allowed-origins: []              # CORS 白名单；不能填 *
  rate-limit: 120                  # req/分钟
  legacy-enabled: false            # 同时暴露旧只读 API
```

### 本地网页控制台

```yaml
console:
  enabled: false
  bind-address: 127.0.0.1          # 只允许回环地址
  port: 8702
  session-minutes: 30              # 会话空闲超时
  rate-limit: 30                   # req/分钟
```

### AuthMe 导入

不直接在配置中设置；通过 `/pluxlogin import authme ...` 命令、GUI 或 REST API 发起。详见 [WEB-API.md#6-authme-导入任务](WEB-API.md#6-authme-导入任务)。

### 动作系统（actions.yml）

替代旧 `welcome.txt` / `join.txt`：

```yaml
config-version: 1
join:
  - "[COMMAND] say {player} 进来了！"
authenticated:
  - "[TP] 0 100 0 world"
  - "[MESSAGE] &a欢迎 {player} 回来！"
```

## 文件结构

```
PluxLogin/
├── src/main/java/com/plux/login/
│   ├── PluxLogin.java                # 插件主类（组合根）
│   ├── ConfigManager.java            # 配置快照加载/迁移/校验/发布
│   ├── DatabaseManager.java          # SQLite/MySQL + AES 加解密 + revision CAS
│   ├── WebApiServer.java             # REST API v1（Gson /api/v1/*）
│   ├── PlayerListener.java           # 登录状态机 / 事件拦截
│   ├── CommandHandler.java           # 主命令 + 玩家/管理员子命令
│   ├── ImportCommandHandler.java     # AuthMe import 子命令
│   ├── PlayerData.java               # 玩家数据模型
│   ├── InventoryGui.java             # 已删除，由 GuiManager 替代
│   ├── PlayerSession.java            # 每玩家登录会话
│   ├── Utils.java                    # 工具类
│   ├── adapter/                      # NMS 版本适配（V2.0.3 已升级）
│   ├── security/                     # 密码/加密/TOTP 原语层
│   │   ├── PasswordService.java
│   │   ├── FieldEncryptionService.java
│   │   ├── KeyManager.java
│   │   ├── TOTPManager.java
│   │   └── Base32.java
│   ├── auth/                         # 认证/绑定/管理业务层
│   │   ├── AuthenticationService.java
│   │   ├── BindingService.java
│   │   └── AdminService.java
│   ├── storage/                      # 存储迁移/执行器
│   │   ├── AsyncDatabaseExecutor.java
│   │   └── DatabaseMigration.java
│   ├── importer/authme/              # AuthMe 导入流水线
│   │   ├── AuthMeImportJobs.java
│   │   ├── AuthMeImportService.java
│   │   ├── AuthMeSourceReader.java
│   │   ├── AuthMeHashClassifier.java
│   │   ├── ImportReport.java
│   │   └── SourceConfig.java
│   ├── gui/                          # GUI 管理界面
│   │   └── GuiManager.java
│   ├── platform/                     # Bukkit/Folia 统一调度
│   │   └── PlatformScheduler.java
│   ├── web/                          # HTTP 共享层 + 本地控制台
│   │   ├── WebSupport.java
│   │   └── console/LocalWebConsole.java
│   └── UpdateChecker.java
├── src/main/resources/
│   ├── plugin.yml                   # 插件描述（版本 2.1.0，Java 8）
│   ├── settings.yml                 # 主配置（config-version: 1）
│   ├── messages.yml                 # 消息配置
│   ├── gui.yml                      # GUI 配置
│   ├── actions.yml                  # 动作配置
│   ├── NMSAdapter_*.java            # 适配器源码（运行期反射加载）
│   ├── web-console/                 # 本地网页控制台静态资源
│   │   ├── index.html
│   │   ├── app.css
│   │   ├── app.js
│   │   └── favicon.svg
│   └── protocol/                    # 1.7.x 反射适配器依赖
├── src/test/java/
│   └── ...                          # 26 项单元测试（密码/加密/Base32/TOTP/迁移/AuthMe Reader）
├── pom.xml                          # Maven 构建（Java 8，Shade 打包）
├── CHANGELOG2.1.0.md                # 本次更新日志
├── WEB-API.md                       # REST API 使用教程
└── README.md
```

## 安全注意

1. **`security/keys.yml` 必须离线备份**：该文件包含 AES-256-GCM 主密钥；丢失后邮箱/QQ/TOTP 密文无法解密
2. **密钥文件不要提交版本库**：请在 `.gitignore` 中加入 `security/keys.yml`
3. **API 密钥 ≥ 32 字符**：短密钥启动时直接拒绝；建议 `openssl rand -hex 24` 生成
4. **API / 控制台默认只绑定 127.0.0.1**：如需外网访问请走反代 + TLS，不要直接暴露
5. **AuthMe 导入前务必备份**：源库和 PluxLogin 当前数据库都要备份；dry-run 不写入，confirm 后才真正导入
6. **CORS 不能填 `*`**：生产环境请显式列出前端域名

## 开发者信息

- **作者**: aya_xzer21145
- **插件版本**: 2.1.0
- **Java 版本**: 8+
- **构建工具**: Maven
- **测试框架**: JUnit 4（26 项单元测试，全部通过）

## 更新日志

- [CHANGELOG2.1.0.md](CHANGELOG2.1.0.md) - 本次版本详细变更
- [CHANGELOG2.0.3.md](CHANGELOG2.0.3.md) - 2.0.3 版本（1.20.5+ 可重定位 NMS + 26.3 新命名体系适配）
- [CHANGELOG2.0.2.md](CHANGELOG2.0.2.md) - 2.0.2 版本（配置路径对齐 + MySQL SSL + 并发安全）

## 许可证

MIT

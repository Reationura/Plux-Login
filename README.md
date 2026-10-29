# PluxLogin

> 高性能 Minecraft 登录注册插件 - 支持验证码/邮箱/QQ绑定/2FA/GUI/WebAPI | 兼容 1.7.10 ~ 26.3+

![Version](https://img.shields.io/badge/version-2.0.3-orange)
![Java](https://img.shields.io/badge/Java-8-blue)
![Folia](https://img.shields.io/badge/Folia-supported-green)

## 功能特性

- 🔐 **登录/注册系统** - 基础的账号密码登录注册功能
- 🎫 **验证码系统** - 注册前需完成验证码验证，防止恶意注册
- 📧 **邮箱绑定** - 支持邮箱绑定与邮箱找回密码
- 🐧 **QQ绑定** - 支持QQ号绑定与QQ邮箱找回密码
- 🔑 **双因素认证 (2FA/TOTP)** - 基于 TOTP 的二次验证，提升账号安全性
- 🖥️ **GUI 管理界面** - 可视化管理界面，操作更便捷
- 🌐 **Web API 接口** - 提供 HTTP API，支持外部系统集成
- 🤖 **反机器人系统** - 快速加入检测、临时封禁，抵御机器人攻击
- 📩 **强制邮箱绑定** - 可配置强制新玩家绑定邮箱
- 💾 **多数据库支持** - 同时支持 SQLite 和 MySQL
- 💬 **Title/ActionBar 提示** - 丰富的界面提示方式
- 🎉 **欢迎动作系统** - 登录成功后执行自定义动作
- 🔄 **更新检查器** - 自动检查插件更新
- ⚡ **高性能** - 全部使用 ConcurrentHashMap 保证线程安全

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

1. 下载最新版本的 `PluxLogin.jar`
2. 将插件放入服务器的 `plugins` 文件夹
3. 启动服务器，插件将自动生成配置文件
4. 根据需要修改 `plugins/PluxLogin/config.yml` 配置文件
5. 执行 `/pluxlogin reload` 重载配置

### 编译

项目使用 Maven 构建：

```bash
mvn clean package
```

编译完成后，jar 文件位于 `target/` 目录下。

## 命令列表

### 玩家命令

| 命令 | 别名 | 描述 | 用法 |
|------|------|------|------|
| `/login` | `/l` | 登录服务器 | `/login <密码>` |
| `/register` | `/reg` | 注册账号 | `/register <密码> <重复密码>` |
| `/captcha` | - | 验证验证码 | `/captcha <验证码>` |
| `/mail` | - | 绑定邮箱 | `/mail <邮箱>` |
| `/mailc` | - | 验证邮箱绑定 | `/mailc <验证码>` |
| `/qq` | - | 绑定QQ号 | `/qq <QQ号>` |
| `/qqc` | - | 验证QQ绑定 | `/qqc <验证码>` |
| `/logout` | - | 登出服务器 | `/logout` |
| `/mailzhpass` | - | 通过邮箱重置密码 | `/mailzhpass` |
| `/qqzhpass` | - | 通过QQ重置密码 | `/qqzhpass` |
| `/changepass` | `/cpss`, `/changepassword` | 修改密码 | `/changepass <原密码> <新密码>` |
| `/changemail` | `/cmail` | 修改邮箱 | `/changemail <原邮箱> <新邮箱>` |
| `/changeqq` | `/cq` | 修改QQ号 | `/changeqq <原QQ> <新QQ>` |
| `/2fa` | - | 管理双因素认证 | `/2fa <on\|off>` |
| `/2fac` | - | 验证双因素认证 | `/2fac <验证码>` |

### 管理员命令

| 命令 | 描述 | 用法 | 权限 |
|------|------|------|------|
| `/pluxlogin` | 插件主命令 | `/pluxlogin <help\|reload\|update\|version\|gui>` | - |
| `/regdel` | 删除玩家注册数据 | `/regdel <玩家名>` | `pluxlogin.admin` |

## 权限节点

| 权限节点 | 描述 | 默认值 |
|----------|------|--------|
| `pluxlogin.admin` | 管理员权限 | op |
| `pluxlogin.gui` | 使用 GUI 管理界面 | op |
| `pluxlogin.bypass.captcha` | 跳过验证码验证 | false |
| `pluxlogin.bypass.force-email` | 跳过强制邮箱绑定 | false |

## 配置说明

### 数据库配置

插件支持 SQLite 和 MySQL 两种数据库：

```yaml
database:
  type: sqlite          # sqlite 或 mysql
  mysql:
    host: localhost
    port: 3306
    database: pluxlogin
    username: root
    password: ""
    pool-size: 10
    min-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

### 验证码设置

```yaml
captcha:
  enabled: true         # 是否启用验证码
  length: 4             # 验证码长度 (4-8)
  alpha-numeric: false  # 是否启用字母数字混合验证码
  timeout: 120          # 验证码超时时间（秒）
```

### 安全设置

```yaml
security:
  max-wrong-password: 5     # 最大错误密码次数
  max-kick-count: 3         # 最大踢出次数后执行命令
  kick-command: "ban %player% 踢出次数过多"
  global-cooldown: 3        # 全局冷却时间（秒）
  action-cooldown: 5        # 操作冷却时间（秒）
```

### 反机器人设置

```yaml
anti-bot:
  enabled: true             # 是否启用反机器人
  min-interval: 5000        # 最小重入间隔（毫秒）
  max-fast-joins: 3         # 最大快速加入次数
  temp-ban-duration: 300    # 临时封禁时长（秒）
```

### 邮箱配置

```yaml
email:
  enabled: false
  host: smtp.qq.com
  port: 465
  username: ""
  password: ""
  from: ""
  cooldown: 60
  code-validity: 300
```

### Web API 配置

```yaml
web-api:
  enabled: false
  port: 8701
  secret: "change-me-to-a-secure-secret-key"
```

## 文件结构

```
PluxLogin/
├── src/main/java/com/plux/login/
│   ├── PluxLogin.java          # 插件主类
│   ├── ConfigManager.java      # 配置管理器
│   ├── DatabaseManager.java    # 数据库管理器
│   ├── CaptchaManager.java     # 验证码管理器
│   ├── EmailManager.java       # 邮箱管理器
│   ├── TOTPManager.java        # TOTP/2FA 管理器
│   ├── PlayerListener.java     # 玩家事件监听器
│   ├── CommandHandler.java     # 命令处理器
│   ├── PlayerData.java         # 玩家数据模型
│   ├── InventoryGui.java       # GUI 界面
│   ├── WebApiServer.java       # Web API 服务
│   ├── UpdateChecker.java      # 更新检查器
│   ├── Utils.java              # 工具类
│   └── adapter/                # NMS 适配器
│       ├── NMSAdapter.java
│       ├── NMSAdapterFactory.java
│       ├── NMSAdapter_Modern.java
│       ├── NMSAdapter_v1_7_R4.java
│       ├── ReflectionUtil.java
│       └── VersionUtil.java
├── src/main/resources/
│   ├── plugin.yml              # 插件描述文件
│   ├── config.yml              # 主配置文件
│   ├── message.yml             # 消息配置文件
│   ├── welcome.txt             # 登录成功欢迎动作
│   └── join.txt                # 加入服务器动作
└── pom.xml                     # Maven 构建配置
```

## 开发者信息

- **作者**: aya_xzer21145
- **插件版本**: 2.0.3
- **Java 版本**: 8+
- **构建工具**: Maven

## 许可证

本项目仅供学习交流使用。

[# PluxLogin 更新日志

- **配置文件版本**：config.yml v2.0.3（message.yml 结构未变）
- **插件版本**：PluxLogin v2.0.3
- **本次主题**：版本适配器升级，正式适配 Minecraft 2026 新命名体系（截至 **26.3**），并修复适配器潜在缺陷、优化并发与性能
- **兼容范围**：Minecraft 1.7.10 ~ 26.3+，Spigot / Paper / Purpur / Folia（folia-supported）
- **更新日期**：2026-10-29

---

## 【适配】

- **适配 2026 全新版本号命名（26.3）**
  - 背景：自 2026 起 Mojang 将 Java 版版本号由 `1.x.y` 改为 `YY.D.H`（年份.发布序号.热修复），如 `26.1`、`26.3`、`26.3.1`。旧 `VersionUtil` 的注释与判断仍假设社区式 `1.26.1` 写法，对官方 `26.3` 缺乏明确支持。
  - 方案：重写版本号解析，原生识别 `26.x` / `26.x.y`，并对仍写作 `1.26.x` 的别名做归一化（`1.26.3 → 26.3`）。由于新体系主版本号（年份 `26`）远大于旧体系恒定的 `1`，跨命名体系的大小比较天然成立（`1.x < 26.x`）。新增 `isAtLeast_26_3()`、通用 `isAtLeast(major, minor)`、`getPatchVersion()`，并解析 `-snapshot/-rc/-pre` 等后缀，避免快照/预发布服上解析异常。

- **适配 CraftBukkit「可重定位（relocatable）」包布局**
  - 背景：自 1.20.5 起 CraftBukkit 移除包名中的版本段，`Bukkit.getServer().getClass().getPackage()` 返回 `org.bukkit.craftbukkit`（不再有 `vXXXX_RY`）。原 `getNMSVersion()` 会把 `craftbukkit` 当作 NMS 版本返回，导致反射拼出 `net.minecraft.server.craftbukkit.*` 之类的无效路径。
  - 方案：以正则 `v\d+_\d+_R\d+` 严格校验包尾段，非法时判定为「无版本 NMS」，新增 `isVersionedNms()` 供上层判断；`ReflectionUtil` 的 `getNmsClass/getCraftbukkitClass` 在无版本布局下直接安全返回 `null` 并仅告警一次（现代适配器基于纯 Bukkit/Spigot API，不依赖反射，功能不受影响）。

## 【修复】

- **修复异步线程读取版本信息的可见性问题**：`VersionUtil` 字段此前为非 `volatile`，`init()` 与字段赋值未构成安全发布，Title/ActionBar 定时器所在的异步/调度线程可能读到半初始化状态（如 `initialized=true` 但 `majorVersion` 仍为默认值）。改为全字段 `volatile`，并保证 `initialized` 最后写入。

- **修复 `isBelowOrEqual_1_20_4()` 潜在异常**：原实现对第 3 段直接 `Integer.parseInt(parts[2])`，遇到非数字补丁段（如 `1.20.4-rc1`）会抛 `NumberFormatException`。改为在解析阶段统一清洗为纯数字并缓存 `patchVersion`。

- **修复 ActionBar 颜色码解析不一致**：`NMSAdapter_Modern` 原以 `new TextComponent(message)` 直接承载含 `§` 的字符串，在新版（Adventure 组件化）服务端上着色/格式表现不稳定。改用 `TextComponent.fromLegacyText(message)`，`1.8 ~ 26.3` 表现一致。

- **修复 Title/ActionBar 在离线玩家或异常时的崩溃风险**：新增 `player == null || !player.isOnline()` 前置守卫，并对 `sendTitle` / `spigot()` 调用包裹 `try/catch(Throwable)`，在极端 fork 或 API 变更时降级为普通聊天消息，绝不中断登录主流程。

- **修复工厂类并发创建与空指针**：`NMSAdapterFactory.instance` 改为 `volatile` 并以双重检查加锁创建，避免并发下重复创建适配器；`logger` 为 `null` 时回退到默认 `Logger`，不再 NPE。

## 【优化】

- **适配器选择路径零反射开销**：`1.8 ~ 26.3+` 全部走纯 API 现代适配器，`VersionUtil` 一次解析、全局缓存，运行期无重复字符串解析与反射查类开销。

- **告警降噪**：可重定位 NMS 布局下的反射失败仅按 `类名` 去重告警一次（`ConcurrentHashMap.newKeySet`），避免高频调用刷屏；版本检测日志中文化并输出 MC 版本、归一化 `v26.3.x` 与 NMS 布局类型，便于运维定位。

## 【其他】

- **版本统一**：`pom.xml`、`plugin.yml`（含描述兼容区间）、`config.yml`（文件头版本标注）、`README.md`（徽章、兼容版本、开发者信息）全部统一为 **2.0.3**，兼容区间标注更新为 **1.7.10 ~ 26.3+**；Java 代码通过 `getDescription().getVersion()` 动态读取，无硬编码版本。
- **行为兼容**：本次仅触及 `adapter` 包与版本元数据，不改变登录/注册/验证码/数据库等业务行为；`1.7.x` 反射适配器逻辑保持原样。
- **构建校验**：`mvn clean compile` → BUILD SUCCESS（Java 1.8 target，19 个源文件全部通过；仅 `sendTitle` 的既定 deprecation 提示，与升级前一致）。
]()
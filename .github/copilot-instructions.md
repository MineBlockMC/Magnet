# Magnet 项目指南

Magnet 是一个基于 Paper 的 Minecraft 1.12.2 分支，构建于已归档的 [Dionysus](https://github.com/nopjmp/Dionysus) 分支之上。其当前活跃分支为 `ver/1.12.2`；该项目的目标是维持旧版本的生命力，重点在于更新依赖项版本，而非改变游戏玩法。

## 仓库模型（请先阅读）

Java 源代码位于 `Magnet-API/` 和 `Magnet-Server/` 目录中，但**这两个目录均未被 Git 纳入版本控制**。它们属于临时克隆副本，由 Toothpick 构建系统根据 `Dionysus` 子模块重新生成，并重置为上游仓库的 `origin/master` 分支。实际作为“唯一事实来源”（Source of Truth）的是 `patches/api/*.patch` 和 `patches/server/*.patch` 文件。

- 在 `Magnet-API/` 或 `Magnet-Server/` 中进行的任何未提交的修改，都会在下一次执行 `applyPatches` 时被清除。
- 工作流程：在 `Magnet-API/` 或 `Magnet-Server/` 中进行修改，在对应的仓库内执行 `git commit`，然后运行 ​​`gradlew rebuildPatches`。每一次提交都会对应生成一个补丁文件。
- 补丁按文件名顺序应用；执行 `rebuildPatches` 时，系统会删除并重新生成所有补丁文件（`git format-patch --no-stat --zero-commit --full-index --no-signature -N origin/master`）。请勿手动编写、编辑或重命名补丁文件。
- 补丁中的作者及日期元数据源自子项目中的提交记录，因此必须在这些子项目中配置好 Git 用户身份信息。
- `Dionysus/` 是 Git 子模块，版本由主仓库的 gitlink 锁定；`last-dionysus` 只记录 `setupUpstream` 上次运行的提交，用于判断是否需要重跑。请勿直接修改该目录下的文件；若需更新，请使用 `updateUpstream`。 
- 项目根目录为主仓库，不应当直接创建提交；生成补丁后应提示用户手动审阅和提交。
- 未执行 `applyPatches` 时 `Magnet-API/`、`Magnet-Server/` 不存在，这是正常状态。详细流程见 [AGENTS.md](../AGENTS.md) 与 [magnet-patch-workflow 技能](skills/magnet-patch-workflow/SKILL.md)。

## 构建、测试与打包

前提条件：JDK 21 或更高版本（构建过程在 JDK 25 上运行，目标字节码版本为 Java 21）、Git、环境变量 `PATH` 中需包含 `bash`（`setupUpstream` 脚本通过 bash 执行 `./dionysus patch` —— 在 Windows 上请使用 Git Bash），以及用于 `paperclip` 的 Maven (`mvn`)。项目中的 Gradle Wrapper 锁定了 Gradle 9.8.1 版本；由于分发 URL 指向腾讯镜像源，而执行 `gradlew wrapper` 会将其重置回 `services.gradle.org`，因此在执行该命令后，请务必手动恢复镜像源配置。

首次设置（在上游版本更新后也需执行此操作）。此步骤会克隆并修补上游代码树，因此首次运行速度较慢：

```bash
gradlew applyPatches
```

在执行上述命令之前，直接运行 `build` 会失败，并提示：“Workspace has not been setup. Try running './gradlew applyPatches' first”（工作区尚未设置。请先尝试运行 './gradlew applyPatches'）。

```bash
gradlew build        # 编译并测试两个项目
gradlew paperclip    # 在仓库根目录下构建 magnetclip.jar（需要 Maven）
```

在 Windows 系统上，请使用 `.\gradlew.bat`。

测试基于 JUnit 4 框架，通过 Gradle（而非 Maven）运行：

```bash
gradlew :magnet-api:test
gradlew :magnet-server:test
gradlew :magnet-api:test --tests "org.bukkit.ChatColorTest"        # 测试单个类
gradlew :magnet-server:test --tests "org.bukkit.BiomeTest.testBukkitToMinecraft" # 测试单个方法
```

`:magnet-server:test` 任务排除了 `org/bukkit/craftbukkit/inventory/ItemStack*Test` 相关测试；`-Pfast` 会跳过所有测试。

在此项目中，增量编译（Incremental compilation）可能不可靠：修改服务端源码后，`:magnet-server:compileJava` 可能会在未改动的 NMS（Minecraft 原生代码）文件中报错，例如提示 `cannot find symbol: method isValidLocation()` 或 `a has private access`。这是因为增量编译步骤从 `minecraft-server` JAR 包中解析这些类，而非从已修补的源码树中解析。若遇到此问题，请执行 `gradlew :magnet-server:clean :magnet-server:compileJava` 进行修复。运行服务器（工作目录为仓库根目录的 `run/`）：

```bash
gradlew runServer     # 运行打包好的 Shadow JAR
gradlew runDevServer  # 运行未经重定位（relocation）的类，主类为 org.bukkit.craftbukkit.Main
```

上游维护：

```bash
gradlew updateUpstream  # 将 Dionysus 子模块更新至 origin/dev
gradlew applyPatches    # 在新的上游基础上重新应用补丁
gradlew upstreamCommit  # 提交子模块更新（提交信息：“Updated Upstream (Dionysus)”）
```

## 架构

整体架构采用分层补丁堆栈形式：Minecraft 1.12.2 → Spigot/CraftBukkit → Paper → **Dionysus**（子模块，上游已归档）→ **Magnet** 补丁。

`settings.gradle.kts` 应用了 `xyz.jpenilla.toothpick` 设置插件，该插件将两个上游代码树转换为 Gradle 项目。由于上游的 Toothpick 项目已停止维护且基于 Gradle 7 + Shadow 7 构建，因此该插件被以“本地引入”（vendored）方式存放在 `build-logic/` 目录下（通过 `pluginManagement { includeBuild("build-logic") }` 引入），并针对 Gradle 9 + Shadow 9 进行了适配补丁；插件 ID（`xyz.jpenilla.toothpick` 和 `xyz.jpenilla.toothpick.settings`）保持不变：

- `:magnet-api` ← `Dionysus/Magnet-API` + `patches/api` — 即 Bukkit/Paper API（`org.bukkit...`）。
- `:magnet-server` ← `Dionysus/Magnet-Server` + `patches/server` — 包含 CraftBukkit 及 NMS。NMS 位于 `net.minecraft.server` 包中，在源码层面未进行重定向（relocation）；Toothpick 的 `minecraftVersion = "1.12.2"` 和 `nmsPackage = "1_12_R1"` 配置项决定了后续的打包流程。

项目中启用了类型安全的访问器（Type-safe project accessors），因此构建脚本中可直接通过 `projects.magnetApi` 和 `projects.magnetServer` 进行引用。`paperclip` 负责组装 `magnetclip.jar`——这是一个由 Maven Paperclip 项目（位于 `Dionysus/Paper/work/Paperclip`）生成的独立启动器。

Magnet 特有的代码分布在两个模块中：

- `org.mineblock.magnet.MagnetConfig`（server）— 服务器启动时（由 `DedicatedServer` 调用）从工作目录加载 `magnet.yml`。
- `org.mineblock.magnet.proxy`（server）— 把 Velocity 的“现代”玩家信息转发（player-info forwarding）机制反向移植到 1.12.2：1.12.2 没有登录插件（login-plugin）数据包，请求/响应帧由 Netty 手工编写，`VelocityForwardingHandler` 会在 ViaVersion 有机会取消响应之前将其“消费”掉。需要后端 `online-mode=false`、安装 ViaVersion，并与 Velocity 使用相同密钥。
- `org.mineblock.magnet.adventure`（API 与 server）— Adventure 组件与 legacy/JSON 文本互转（API 侧 `MagnetText`），以及 `Audience`、`BossBar`、控制台输出等 1.12.2 实现（server 侧）。

值得了解的构建脚本职责：

- 根目录 [`build.gradle.kts`](../build.gradle.kts)：配置 Toothpick（分支、版本、上游、补丁目录）；添加 PaperMC 仓库；为 `importMCDev` 预建反编译用的 `libraries/` 目录；打包前把 Paperclip POM 的仓库 URL 改为 HTTPS，随后由 `finalizedBy` 的 `restorePaperclipPoms` 恢复。不要提交这些临时 POM 修改。
- [`subprojects/api.gradle.kts`](../subprojects/api.gradle.kts) 与 [`subprojects/server.gradle.kts`](../subprojects/server.gradle.kts)：禁用 Javadoc（旧 1.12.2 源码在现代 JDK 上无法干净生成文档）；server 侧还固定 log4j / Adventure 依赖顺序，并排除 `:magnet-server:test` 中的 `ItemStack*Test`。Dionysus 的 SIMD 工具类已被移除，因为 `jdk.incubator.vector` 只在 Java 17/18 上安全。
- `server.gradle.kts` 还解析本地 Maven 仓库：`-Dmaven.repo.local` → `MAVEN_OPTS` 中的 `-Dmaven.repo.local=` → 用户/全局 `settings.xml` 的 `<localRepository>` → `~/.m2/repository`。上游会把 `org.spigotmc:minecraft-server` 安装到这里。
- [`build-logic/`](../build-logic) 是 Toothpick（MIT）的本地副本，已适配 Gradle 9 + Shadow 9，负责 `applyPatches`、`rebuildPatches`、`paperclip`、`importMCDev`、`runServer`、`runDevServer` 等任务。改动后运行 `gradlew help --warning-mode all` 检查弃用告警；细节见 [build-logic/README.md](../build-logic/README.md)。

## 关键约定

- 对上游（upstream）文件所做的每一处更改，都必须使用 Magnet 标记（markers）进行标识。由 Magnet 添加的注释、Javadoc 以及 `magnet.yml` 中的文本均需使用简体中文编写，而上游原有的注释则应保持不变。详细规则（包括标记、Javadoc、行内注释及 `magnet.yml` 选项文本格式）请参阅 [.github/instructions/java-comments.instructions.md](instructions/java-comments.instructions.md)。您可以使用 `grep "// Magnet"` 命令来审查该分支（fork）中的差异。
- API 侧目前只有 Adventure 相关改动（`patches/api/0005`、`0006`）；其余分支逻辑位于 server 侧的 `org.mineblock.magnet` 包内。
- `MagnetConfig` 选项：定义一个 `private static <type> option` 字段，并配套 `private static void optionName()` 加载方法。`load()` 反射调用所有无参、返回 `void` 的 `private static` 方法；`getBoolean`、`getInt`、`getDouble`、`getString`、`getStringList` 会把默认值与注释写入 `magnet.yml`，但不会覆盖用户已修改的值。
- 对于长期存在的分支特性，应将其独立为单独的补丁（patch）及对应的 `org.mineblock.magnet` 类，而不是直接将其合并到上游代码类中。
- 调试输出通过 `MagnetConfig.log(...)` / `logError(...)`：`log` 受 `verbose` 控制，`logError` 始终输出；这两个方法不拼接前缀，消息里的 `[Magnet]` 由调用方写入。
- Log4j 固定为 2.26.1，必须在两处同步：`Magnet-Server/pom.xml` 的 `<log4j2.version>` 与 [`subprojects/server.gradle.kts`](../subprojects/server.gradle.kts) 中 `magnetPinnedLog4j` 配置的依赖版本。`org.spigotmc:minecraft-server` 是自带旧 log4j 的胖包，ShadowJar 必须把 pinned 配置排在 `runtimeClasspath` 之前。
- 版本号格式为 `git-Magnet-<构建编号或 Git 哈希值>`，构建产物发布在 `org.mineblock.magnet` 组（group）下。
- 采用上游 Java 代码风格（继承自 Bukkit/CraftBukkit）：使用 4 个空格缩进，不使用制表符（tab），行尾无多余空格，禁止使用通配符导入（wildcard imports）。
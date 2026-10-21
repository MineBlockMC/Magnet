# Magnet 项目指南

Magnet 是基于 Paper 的 Minecraft 1.16.5 分支，上游为 [Purpur](https://github.com/PurpurMC/Purpur/tree/ver/1.16.5)（Purpur 又基于 Paper 1.16.5）。目标是维持旧版本的生命力，重点更新依赖版本，而不是改变玩法。

## 仓库模型（请先阅读）

Java 源码位于 `Magnet-API/` 和 `Magnet-Server/`，但**这两个目录不被 Git 跟踪**（见 `.gitignore`）。它们由 Toothpick 从 `Purpur/Purpur-API`、`Purpur/Purpur-Server` 克隆并重置到 `origin/master`，再应用 `patches/api/*.patch` 和 `patches/server/*.patch` 生成。真正的事实来源是 `patches/`。

- 在 `Magnet-API/` 或 `Magnet-Server/` 中修改后，先在对应仓库内 `git commit`，再运行 `gradlew rebuildPatches`。每次提交会生成一个补丁。
- `rebuildPatches` 会删除旧补丁，并用 `git format-patch --no-stat --zero-commit --full-index --no-signature -N -o <patchesDir> origin/master` 重新生成。不要手写、编辑或重命名补丁文件。
- 补丁按文件名顺序应用；作者和日期来自子仓库提交，先配置好 Git 用户身份。
- `Purpur/` 是根仓库的子模块，锁定在 `ver/1.16.5`；内部还有 `Paper/` 子模块。不要直接修改 `Purpur/` 或 `Purpur/Paper/` 下的文件，也不要把 `Purpur/patches/` 当作 Magnet 的补丁。更新上游用 `gradlew updateUpstream`，再运行 `gradlew applyPatches`。
- 根仓库只跟踪 `patches/`、构建脚本、README 和 `.github/` 等手写文件。不要代替用户提交根仓库；补丁生成后提示用户审阅并提交。
- `Magnet-API`、`Magnet-Server`、`last-purpur`、`magnetclip.jar`、`*clip.jar` 均被 `.gitignore` 忽略，不要把它们当作可提交的源码。

## 构建、测试与打包

前提条件：
- JDK 21+（构建与目标字节码均为 Java 21；当前在 JDK 25 上验证）。
- `applyPatches` 的 Paper 初始化阶段会为旧版 SpecialSource 自动替换内置 ASM 为 9.9.1（首次从 Maven Central 下载并缓存），因此所有步骤都使用运行 Gradle 的 JDK，不再需要 JDK 16/17。
- Git、Git for Windows（`setupUpstream` 会在 Git Bash 中执行 `Paper/paper patch`；`build-logic` 会自动定位 Git Bash，无需加入 `PATH`）。
- Maven（`paper patch` 与 `paperclip` 通过 `mvn` 运行）。

首次设置（上游更新后也要执行；会克隆并反编译，首次很慢）：

```bash
gradlew applyPatches
```

`applyPatches` 会先执行 `setupUpstream`（用 Git Bash 运行 `Paper/paper patch`，再由 Magnet 的 Toothpick 导入 mc-dev 并应用 `Purpur/patches`，生成 `Purpur-API`、`Purpur-Server` 和 `Purpur/Paper/work/Minecraft/1.16.5`；不再执行 `Purpur/gradlew`），再克隆到 `Magnet-API`/`Magnet-Server` 并应用补丁。直接运行 `gradlew build` 会报 `Workspace has not been setup. Try running './gradlew applyPatches' first`。

**首次设置常见失败**：Gradle 9 要求 `Magnet-API/` 和 `Magnet-Server/` 在配置期存在，`settings.gradle.kts` 会自动创建空目录；`applyPatches` 会在目录没有 `.git` 时删除残留内容并重新克隆。如果仍有异常，先运行 `gradlew cleanSubprojects` 再执行 `applyPatches`。

```bash
gradlew build        # 编译并测试两个子项目
gradlew paperclip    # 打包 Paperclip（需要 Maven）
gradlew :magnet-api:test
gradlew :magnet-server:test
gradlew :magnet-api:test --tests "org.bukkit.ChatColorTest"
gradlew :magnet-server:test --tests "org.bukkit.BiomeTest.testBukkitToMinecraft"
```

- `paperclipName` 已设置为 `magnetclip`，产物为根目录下的 `magnetclip.jar`。
- 测试基于 JUnit 4；Toothpick 会排除 `org/bukkit/craftbukkit/inventory/ItemStack*Test`。
- 运行测试服务器：`gradlew runServer`（打包后的 Shadow JAR）、`gradlew runDevServer`（未重定位的类，主类 `org.bukkit.craftbukkit.Main`）；工作目录是根目录下的 `run/`，首次运行需同意 EULA。
- `gradlew cleanSubprojects` / `gradlew cleanToothpick` 会删除生成目录（后者还会删除 `Purpur` 的工作区），不要在有未提交改动时使用。
- 如果 `:magnet-server:compileJava` 在未改动的 NMS 文件上报大量错误，先执行 `gradlew :magnet-server:clean :magnet-server:compileJava`；最终以 `gradlew paperclip` 的结果为准。

上游维护：

```bash
gradlew updateUpstream  # fetch + reset Purpur 子模块到 origin/ver/1.16.5
gradlew applyPatches    # 在新的上游基础上重新应用 Magnet 补丁
gradlew upstreamCommit  # 在根仓库提交子模块更新
```

## 架构

补丁栈：Minecraft 1.16.5 → Paper（`Purpur/Paper`，`ver/1.16.5`）→ Purpur（`Purpur/`，`ver/1.16.5`）→ Magnet。

- `:magnet-api` ← `Magnet-API`：Bukkit/Paper/Purpur API，补丁在 `patches/api`。
- `:magnet-server` ← `Magnet-Server`：CraftBukkit + NMS（`net.minecraft.server`，源码层不重定位），补丁在 `patches/server`。
- Toothpick 已从 1.12.2 移植并 vendored 到 `build-logic/`，通过 `pluginManagement { includeBuild("build-logic") }` 引入，适配 Gradle 9 + Shadow 9。修改 `build-logic/` 后运行 `gradlew help --warning-mode all` 验证。
- `subprojects/api.gradle.kts` 只添加 `adventure-text-minimessage 4.1.0-SNAPSHOT`；`subprojects/server.gradle.kts` 只添加 `rhino-js-engine 1.7.7.1`。其余构建逻辑来自上游 POM 和 Toothpick。
- 配置：`nmsPackage = "1_16_R3"`，版本 `1.16.5-R0.1-SNAPSHOT`，groupId `org.mineblock.magnet`，版本号 `git-Magnet-<BUILD_NUMBER 或 commit hash>`。
- 配置文件默认放在 `config/`：`bukkit.yml`、`commands.yml`、`spigot.yml`、`paper.yml`、`tuinity.yml`、`purpur.yml`、`help.yml`、`magnet.yml`。
- 品牌已改为 Magnet；版本检查由 `org.mineblock.magnet.MagnetVersionFetcher` 负责（GitHub 分支 `ver/1.16.5`）；Timings 默认关闭，Log4j 使用 `nolookups`。

### Magnet 特有代码（`Magnet-Server/src/main/java/org/mineblock/magnet/`）

- `MagnetConfig`：读写 `config/magnet.yml`；`init(File)` 后通过反射调用所有无参、返回 `void` 的 `private static` 方法；提供 `getBoolean`、`getInt`、`getDouble`、`getString`、`getList`，签名均为 `(path, default)`；`log(String)` / `log(Level, String)` 受 `verbose` 控制，当前没有 `logError`，也没有统一的 `[Magnet]` 前缀。
- `MagnetWorldConfig`：按世界读取 `world-settings.default.<path>`，`world-settings.<world>.<path>` 可覆盖；`CraftServer` 对每个世界调用 `world.magnetConfig.init()`。
- `MagnetCommand`：`/magnet reload|version`。
- `MagnetVersionFetcher`：GitHub 版本检查。
- API 侧只有 `Server#getMagnetConfig()` 默认方法；`CraftServer` 返回 `MagnetConfig.config`。
- 本版本**没有** 1.12.2 的 Velocity 现代转发、`MagnetText` / `org.mineblock.magnet.adventure`、`MagnetConfig.logError` 等；不要照搬这些符号。

## 关键约定

- 对上游文件（Paper/Purpur/CraftBukkit/NMS）的每一处改动都必须加 Magnet 标记；规则见 [Java 注释规范](instructions/java-comments.instructions.md)。`org.mineblock.magnet.*` 下的新文件整体属于 Magnet，不需要标记。
- 上游原有注释保持原样；Magnet 新增的注释、Javadoc 和配置说明使用简体中文。
- 源码语言级别是 **Java 21**（`sourceCompatibility`/`targetCompatibility` 均为 21）。上游代码仍保持 Java 8 风格；Magnet 新增代码可以使用 Java 21 语法，但不要为了用新语法重写上游代码。`///` 文档注释仍是 Java 23+ 特性，不要使用。
- 优先做增量补丁：保持最小 diff，不要重排或改写无关行；在 `Magnet-API`/`Magnet-Server` 中用完整限定名引用 Magnet 类，避免新增 import 造成补丁噪音。
- 配置项模式：在 `MagnetConfig` 中定义 `private static` 字段和同名的 `private static void` 无参加载方法，`init` 会自动调用；世界级选项放在 `MagnetWorldConfig`。注意 helper 的 `def` 只通过 `addDefault` 写入文件，读取表达式是 `config.getBoolean(path, config.getBoolean(path))`，首次读取尚未写入时会得到类型默认值；新增默认为 `true` 的选项时要先修正 helper 或显式 `set`。
- 调试输出走 `MagnetConfig.log(...)`（仅 `org.mineblock.magnet` 包内可访问，受 `verbose` 控制）；其他包使用 `Bukkit.getLogger()` 或上游 `LOGGER`。不要假设存在 `logError`。
- 长期存在的 fork 特性应拆成独立补丁和 `org.mineblock.magnet` 类，不要混进上游类的大段改动。
- 4 空格缩进，不用 Tab，不留行尾空白，不用通配 import，文件编码 UTF-8。
- 审计 fork 差异：在 `Magnet-API`/`Magnet-Server` 中 `grep "Magnet"`；根仓库 `patches/` 是最终审阅对象。

## 常见问题

- `applyPatches` 会丢弃 `Magnet-API`/`Magnet-Server` 中未提交的改动；先 commit。
- `rebuildPatches` 会删除所有旧补丁；新增文件必须先 `git add`/commit，否则不会进入补丁。
- `Magnet-API`/`Magnet-Server` 不在根仓库 Git 中，根仓库 `git status` 看不到它们的修改。
- `Purpur/` 及其 `Paper/` 是子模块，改动会被 `updateUpstream`/`setupUpstream` 重置。
- `paperclip` 与 `paper patch` 需要 Maven；`setupUpstream` 需要 Git Bash，不需要额外的 JDK（旧 SpecialSource 会在 setup 时自动打 ASM 补丁）。
- 1.16.5 的 Adventure 是 4.1.0-SNAPSHOT（1.12.2 是 5.2.0），组件 API 不同（例如 `TextComponent.ofChildren`）；从 1.12.2 复制代码前先核对依赖版本。
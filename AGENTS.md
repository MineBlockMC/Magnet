# AGENTS.md

Magnet：为 Minecraft 1.12.2 续命的 Paper 分支，基于 `Dionysus` 上游子模块，用 Toothpick Gradle 插件构建。完整说明见 [.github/copilot-instructions.md](.github/copilot-instructions.md)，构建插件细节见 [build-logic/README.md](build-logic/README.md)。

## 构建 / 测试

- Gradle Wrapper **9.8.1**（分发源为腾讯镜像）；需要 JDK **21+**，字节码目标 Java 21。
- `gradlew applyPatches`：由 `patches/` 重新生成 `Magnet-API/`、`Magnet-Server/`；首次运行会先克隆并修补上游，较慢。工作区未初始化时 `build` 会报 `Workspace has not been setup`。
- `gradlew build`：编译 API + Server 并跑测试；`-Pfast` 跳过测试。
- `gradlew paperclip`：产出根目录下可运行的 `magnetclip.jar`（需要 `mvn`）。
- `gradlew runServer` / `runDevServer`：工作目录是仓库根目录的 `run/`，不是 `Magnet-Server/run`。
- 增量编译偶发在未改动的 NMS 文件上报错：先执行 `gradlew :magnet-server:clean :magnet-server:compileJava`。
- 排查日志/配置问题：`gradlew :magnet-server:test --tests org.bukkit.BiomeTest`，再看 `Magnet-Server/build/test-results/test/TEST-*.xml`（log4j StatusLogger 输出在 `system-out`）。
- 本仓库没有 CI。

## 源码实际在哪

`Magnet-API/`、`Magnet-Server/` 是**生成物**（已 gitignore，未执行 `applyPatches` 时不存在）：`applyPatches` 克隆 `Dionysus/Dionysus-{API,Server}` 后应用 `patches/{api,server}/*.patch`。唯一事实来源是补丁，完整步骤见 [magnet-patch-workflow 技能](.github/skills/magnet-patch-workflow/SKILL.md)：

1. 改 `Magnet-Server/` / `Magnet-API/` 里的文件；
2. 在生成仓库里提交：`git -C Magnet-Server commit ...`；本机启用 GPG 签名但无密钥时，加 `-c commit.gpgsign=false`；
3. `gradlew rebuildPatches` 重新生成 `patches/api|server/*.patch`。

`Dionysus/` 是上游子模块，不要改；更新上游用 `gradlew updateUpstream` + `gradlew applyPatches`。

## 补丁 / 提交信息

- 一个补丁只做一件事；文件名 `NNNN-<slug>.patch`，`patches/api` 与 `patches/server` 各自从 `0001` 递增；文件名由 `git format-patch` 从提交标题生成，不要手工编辑或重命名。
- 补丁的 `Subject:` 就是提交信息首行，所以写成祈使句，**不要加项目名前缀**：例如 `Upgrade Log4J`，而不是 `Magnet - upgrade log4j to 2.26.1`（现有 `0001`/`0002` 的 `Magnet ...` 是历史遗留）。
- 变更理由写在提交信息正文（会成为补丁正文），不要塞进标题。

## 代码注释

- 遵循 [.github/instructions/java-comments.instructions.md](.github/instructions/java-comments.instructions.md)：上游文件中的改动用 `// Magnet start` / `// Magnet end` 或行尾 `// Magnet - 说明`；新建的 `org.mineblock.magnet.*` 文件整体属于 Magnet，不加标记。
- 注释要短：一句话说明效果，前缀 `Magnet - `；代码本身够清楚时不写注释。禁止多行注释块。`grep "Magnet"` 可审计 fork 改动。

## 日志 / log4j

- log4j 固定为 2.26.1：`Magnet-Server/pom.xml` 的 `<log4j2.version>` 必须与 `subprojects/server.gradle.kts` 中 `magnetPinnedLog4j` 配置的依赖版本同步。
- `org.spigotmc:minecraft-server` 是胖包，仍带 Mojang 的旧 log4j；ShadowJar 的 `configurations` 必须把 `pinnedLog4j`（及 `pinnedAdventure`）排在 `runtimeClasspath` 之前，否则旧 log4j 就会胜出。日志 pattern 的选项嵌套保持 ≤2 层（如 `%minecraftFormatting{%msg}`），否则旧解析器会把 `%minecraftFormatting` 当字面量输出。

## 其他

- 版本号 `git-Magnet-<BUILD_NUMBER 或 7 位哈希>`，Maven group 为 `org.mineblock.magnet`。
- 本地 Maven 仓库解析顺序见 `subprojects/server.gradle.kts` 的 `magnetLocalMavenRepo()`；上游的 `org.spigotmc:minecraft-server` 需要安装到该仓库。
- 代码风格：4 空格缩进、无 Tab、无行尾空白、禁止通配 import；编译目标 Java 21。

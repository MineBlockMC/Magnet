# AGENTS.md

Magnet：为 Minecraft 1.12.2 续命的 Paper 分支，基于 `Dionysus` 上游子模块，用 Toothpick Gradle 插件构建。构建入口见 [README.md](README.md)。

## 构建 / 测试

- Gradle **必须用 JDK 17**（wrapper 为 Gradle 7.6.6）；JDK 25 会让 Kotlin DSL 编译器失败（`IllegalArgumentException: 25.0.3`）。
- `gradlew applyPatches`：由 `patches/` 重新生成 `Magnet-API/`、`Magnet-Server/`；改完补丁后必须重跑。
- `gradlew build`：编译 API + Server 并跑测试。
- `gradlew paperclip`：产出根目录下可运行的 `magnetclip.jar`。
- 排查日志/配置问题：`gradlew :magnet-server:test --tests org.bukkit.BiomeTest`，再看 `Magnet-Server/build/test-results/test/TEST-*.xml`（log4j StatusLogger 输出在 `system-out`）。

## 源码实际在哪

`Magnet-API/`、`Magnet-Server/` 是**生成物**（已 gitignore）：`applyPatches` 克隆 `Dionysus/Dionysus-{API,Server}` 后应用 `patches/{api,server}/*.patch`。工作流：

1. 改 `Magnet-Server/` / `Magnet-API/` 里的文件；
2. 在生成仓库里提交：`git -C Magnet-Server -c commit.gpgsign=false commit ...`（两个生成仓库都写了 `commit.gpgsign=true` 却没有签名密钥，不加 `-c commit.gpgsign=false` 会提交失败）；
3. `gradlew rebuildPatches` 重新生成 `patches/api|server/*.patch`。

`Dionysus/` 是上游子模块，不要改。

## 补丁 / 提交信息

- 一个补丁只做一件事；文件名 `NNNN-<kebab-slug>.patch`，`patches/api` 与 `patches/server` 各自从 `0001` 递增。
- 补丁的 `Subject:` 就是提交信息首行，所以写成祈使句，**不要加项目名前缀**：例如 `Upgrade Log4J`，而不是 `Magnet - upgrade log4j to 2.26.1`。
- 变更理由写在提交信息正文（会成为补丁正文），不要塞进标题。

## 代码注释

- 注释要短：一句话说明效果，前缀 `Magnet - `；代码本身够清楚时不写注释。禁止多行注释块。

## 日志 / log4j

- log4j 版本定义在 `Magnet-Server/pom.xml` 的 `<log4j2.version>`，必须同步到 `subprojects/server.gradle.kts` 的 `magnetPinnedLog4j` 配置。
- `org.spigotmc:minecraft-server` 是胖包，仍带 Mojang 的旧 log4j；不把补丁里声明的 log4j 排在它前面，旧 log4j 就会胜出。日志 pattern 的选项嵌套保持 ≤2 层（如 `%minecraftFormatting{%msg}`），否则旧解析器会把 `%minecraftFormatting` 当字面量输出。

## 其他

- 本仓库没有 CI。

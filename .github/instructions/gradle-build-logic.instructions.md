---
description: "Use when editing Gradle Kotlin DSL, settings/build scripts, or the vendored Toothpick logic in build-logic. Covers Magnet markers, Gradle 9/Shadow 9 constraints, JDK targets, and verification commands."
applyTo: "**/*.gradle.kts,build-logic/**/*.kt"
---
# Gradle 与 build-logic 规范

`build-logic/` 是从 1.12.2 移植并 vendored 的 Toothpick（`1.1.0-Magnet`），通过 `pluginManagement { includeBuild("build-logic") }` 引入，不进补丁栈，改动直接属于根仓库。

- 保持最小 diff；新增或修改的行加 `// Magnet - <原因>`，成段修改用 `// Magnet start` / `// Magnet end`。
- `build-logic` 自身编译到 **Java 17**（`build-logic/build.gradle.kts` 的 `JvmTarget.JVM_17`），根项目与 `subprojects` 的目标字节码是 **Java 21**；不要把两个目标改成一致。
- 使用 Gradle 9 / Kotlin DSL 的推荐写法：不要在 task action 中访问 `Task.project`（在配置期捕获），用 `providers.exec` 代替配置期 `ProcessBuilder`，依赖声明用单字符串记号。
- `subprojects/api.gradle.kts` 与 `subprojects/server.gradle.kts` 只放依赖；其余构建逻辑来自上游 POM 和 Toothpick，不要搬进来。
- `settings.gradle.kts` 在配置期创建 `Magnet-API`/`Magnet-Server` 空目录是 Gradle 9 的要求；不要删除。
- 不要修改 `Purpur/`、`Purpur/Paper/` 下的构建文件：它们是子模块，`updateUpstream`/`setupUpstream` 会重置。
- 保留这些 Magnet 定制：`ConfigureSubprojects.kt` 的 Shadow 依赖排除、Log4j 重新打包与 `removeVanillaMain`；`Paperclip.kt` 的仓库地址改写；`SetupUpstream.kt` 的 ASM 9.9.1 覆盖与 Git Bash 定位；`Constants.kt` 的 `JVM_ADD_OPENS` 与 PaperMC 仓库地址。

验证顺序：

```bash
gradlew help --warning-mode all   # 先确认配置期无弃用或解析错误
gradlew build                     # 编译 + 测试
gradlew paperclip                 # 需要 Maven，产物为 magnetclip.jar
```

- 只改 `build-logic` 时 `gradlew help` 能覆盖大部分配置错误，最终仍以 `gradlew build` / `gradlew paperclip` 为准。
- `gradlew build -Pfast` 跳过测试和 Javadoc。
- 不要在有未提交改动时运行 `cleanSubprojects` / `cleanToothpick`；后者还会删除 `Purpur` 工作区。

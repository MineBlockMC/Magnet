---
name: magnet-patch-workflow
description: "Use when changing Magnet sources or patches (Paper 1.12.2 fork). Covers the edit generated source → commit in Magnet-Server/Magnet-API → gradlew rebuildPatches loop, patch ordering, Magnet markers, build verification, and recovering from lost changes. Trigger words: patch, rebuildPatches, applyPatches, Magnet-Server, Magnet-API, 补丁."
---

# Magnet 补丁工作流

`patches/api/*.patch` 与 `patches/server/*.patch` 是唯一事实来源。`Magnet-API/`、`Magnet-Server/` 是 `applyPatches` 生成的临时仓库（已 gitignore），未提交的改动会在下一次 `applyPatches` 的 `git reset --hard origin/master` 中丢失。

## 何时使用

- 修改 `Magnet-Server/`、`Magnet-API/` 中的 Java 源码或资源
- 新增、拆分或重排补丁
- 补丁应用失败或需要恢复生成仓库

## 前置检查

1. `Magnet-API/`、`Magnet-Server/` 是否存在；不存在则运行 `gradlew applyPatches`（首次会克隆并修补上游，较慢）。
2. `git -C Magnet-Server status`、`git -C Magnet-API status` 是否干净；有未提交改动时先确认是否要保留。
3. `bash` 是否在 `PATH`（Windows 用 Git Bash）；`paperclip` 还需要 `mvn`。

## 修改并生成补丁

1. 直接在 `Magnet-API/` 或 `Magnet-Server/` 中编辑；上游文件中的改动必须加 Magnet 标记，新建的 `org.mineblock.magnet.*` 文件不需要标记。规范见 [java-comments 指令](../../instructions/java-comments.instructions.md)。
2. 在对应的生成仓库中提交：

   ```bash
   git -C Magnet-Server commit -am "<祈使句标题>"
   ```

   本机启用 GPG 签名但无可用密钥时，加 `-c commit.gpgsign=false`：`git -C Magnet-Server -c commit.gpgsign=false commit -am "..."`。

3. 在仓库根目录运行 `gradlew rebuildPatches`；它会删除并重建 `patches/{api,server}` 下全部 `.patch`。
4. 检查生成补丁的内容与编号，确认没有把无关改动带进同一个补丁。

## 规则

- 一个补丁只做一件事；`patches/api` 与 `patches/server` 各自从 `0001` 递增，按文件名顺序应用。
- 文件名 `NNNN-<slug>.patch` 由 `git format-patch` 从提交标题生成；不要手工编辑或重命名补丁。
- `Subject:` 用祈使句，不加项目名前缀（如 `Upgrade Log4J`）；变更理由写在提交信息正文。
- 不要修改 `Dionysus/`；更新上游用 `gradlew updateUpstream` + `gradlew applyPatches`。
- 根目录仓库不要直接提交；生成补丁后提示用户审阅并提交。

## 验证

- `gradlew :magnet-server:compileJava`；增量编译在未改动的 NMS 文件上报错时，先执行 `gradlew :magnet-server:clean :magnet-server:compileJava`。
- `gradlew :magnet-server:test --tests "org.mineblock.magnet.adventure.MagnetTextTest"`，或按改动选择对应测试类。
- 只改了 `build-logic/` 或 Kotlin 构建脚本时，运行 `gradlew help --warning-mode all`。

## 恢复

- 生成仓库被重置：`gradlew applyPatches` 会重新克隆/重置并应用现有补丁；此前未提交的源码改动无法恢复。
- 补丁缺失或重复：确认生成仓库包含全部预期提交，再运行 `gradlew rebuildPatches`。

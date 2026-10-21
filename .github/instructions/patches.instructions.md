---
description: "Use when inspecting or modifying files under patches/. Magnet patches are generated from Magnet-API/Magnet-Server commits and must never be hand-edited or renamed."
applyTo: "patches/**"
---
# Magnet 补丁规范

`patches/` 是根仓库的事实来源，但补丁文件是生成物：不要手写、编辑或重命名 `.patch`。

工作流：

1. 在 `Magnet-API/` 或 `Magnet-Server/` 中修改并 `git commit`；作者和日期会写进补丁，先配置好 Git 用户身份。
2. 运行 `gradlew rebuildPatches`。它会删除旧补丁，再用 `git format-patch --no-stat --zero-commit --full-index --no-signature -N -o <patchesDir> origin/master` 重新生成。
3. 审阅 `patches/api` / `patches/server` 的 diff，确认只包含预期改动和 Magnet 标记；新增文件必须先 `git add` 并提交，否则不会进入补丁。
4. 不要提交根仓库；提示用户审阅后自行提交。

- 补丁按文件名顺序应用；补丁名来自提交标题，保持 `0001-Magnet-...` 风格。
- 要修改已有补丁时，改写 `Magnet-API`/`Magnet-Server` 中对应的提交（`git commit --amend` 或 `rebase`），再运行 `rebuildPatches`；不要直接编辑 `.patch`。
- 全局 Git 签名开启但没有可用密钥时，用 `git -c commit.gpgsign=false commit ...`。
- `applyPatches` 会 `git reset --hard origin/master`，丢弃子仓库未提交的改动；先提交再切换。
- `Purpur/patches/` 是上游补丁，不是 Magnet 的补丁。
- 审计 fork 差异时先看 `patches/` 的最终 diff，再在 `Magnet-API`/`Magnet-Server` 中 `grep "Magnet"`。

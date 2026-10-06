# Magnet
为旧版本 Minecraft 续命的 Paper 分支。

为 [MineBlock](https://www.mineblock.cc) 设计。

## ver/1.12.2

基于 [Dionysus](https://github.com/nopjmp/Dionysus)，维护重点为升级依赖版本。

## 构建

需要 **Java 17**。

**初始化**

```bash
gradlew applyPatches
```

**创建补丁**

在 `Magnet-API` 或 `Magnet-Server` 创建提交后，使用命令 `gradlew rebuildPatches`，之后补丁会存放在 `patches` 文件夹中。

**打包编译**

```bash
gradlew paperclip
```

产物为根目录下的 `magnetclip.jar`。
# Magnet
为旧版本 Minecraft 续命的 Paper 分支。

为 [MineBlock](https://www.mineblock.cc) 设计。

## 版本

我们维护我们认为非常稳定的 Minecraft 版本，也称为 LTS 版本。

目前正在维护的版本：
- [1.12.2](https://github.com/MineBlockMC/Magnet/tree/ver/1.12.2)
- [1.16.5](https://github.com/MineBlockMC/Magnet/tree/ver/1.16.5)

## JAR、API

我们不发布构建好的服务端 JAR 文件，也不考虑将添加的 API 发布到其他地方。API 设计起来很困难。在像 Bukkit、Spigot、Paper 等公共项目中，一旦 API 提交，几乎是永久的。如果没有充分的理由，你不能轻易修改它。

因此，我们编写的是基于我们基本需求的 API，而这些需求往往不“完整”或“符合上游仓库的样式指南”。我们没有时间编写那些我们个人不需要的代码。

我们还希望保留对这些 API 进行破坏性更改的能力，如果这能带来更好的操作方式或性能提升。

这就是为什么我们会为这些旧版 Minecraft 引入许多来自新版服务端软件中非常实用的 API，并且我们不考虑你会怎么使用，也没有兴趣解决你遇到的任何问题。

如果以后我们改变了想法，那到时候再说。

## 补丁

所有由 MineBlock 团队及 DreamVoid 个人为 Magnet 编写的补丁均以 MIT 授权，在遵循协议的前提下可自由使用。

我们对这些补丁不提供任何支持。如果你想使用它们，必须自己从我们的仓库提取出来，应用到你的代码中，并自行解决任何困难以使其能够编译。

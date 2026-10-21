---
description: "Use when writing or editing Java source in this repository. Covers the // Magnet change markers, Simplified Chinese comment/Javadoc style, concise note-only wording, and MagnetConfig/magnet.yml option patterns. Trigger words: Javadoc, comment, 注释, 文档注释, Magnet marker, MagnetConfig."
applyTo: "**/*.java"
---
# Java 注释与 Javadoc 规范

Magnet 通过补丁维护上游代码（见 [copilot-instructions.md](../copilot-instructions.md)），注释首先要让 fork 改动可追溯，并保证上游同步时不产生冲突。
本规范只适用于 Magnet 自己新增的代码和标记块：**注释、Javadoc 和 `magnet.yml` 选项说明一律使用简体中文**；标识符、API 名、包名、配置键和日志原文保持英文。上游原有注释保持原样，不要翻译、改写或“整理”。
构建使用 JDK 21+，源码的 `sourceCompatibility` / `targetCompatibility` 是 **Java 21**。上游代码仍保持 Java 8 风格；Magnet 新增代码可以使用 Java 21 语法，但不要为了用新语法重写上游代码。不要使用 Java 23 才引入的 `///` 文档注释。

## 1. 上游文件中的改动必须带 Magnet 标记

只有修改上游文件时才加标记；新建的 `org.mineblock.magnet.*` 文件整体属于 Magnet，不需要标记。

块级改动用成对标记，缩进与被包代码一致。`// Magnet start` 默认不带说明；确有必要时在同行写一句简短原因，`// Magnet end` 不带说明：

```java
// Magnet start
org.mineblock.magnet.MagnetConfig.init((java.io.File) options.valueOf("magnet-settings"));
org.mineblock.magnet.MagnetConfig.load((java.io.File) options.valueOf("magnet-settings"));
// Magnet end
```

单行改动或字段用行尾标记：

```java
public final org.mineblock.magnet.MagnetWorldConfig magnetConfig; // Magnet
```

```java
// Magnet start - 关闭上游的 Java 版本检查
// SomeUpstreamChecker.checkJvm();
// Magnet end
```

- 一处改动只加一组标记，不要逐行重复行尾标记。
- 不要改写、删除或“整理”上游已有的 `// Paper start`、`// CraftBukkit`、`// Spigot` 等标记；自己的标记只包住最小改动范围。
- 上游原有注释保持原样（包括其中的英文说明）；只翻译和编写 Magnet 自己新增的注释。
- 禁用上游行为时不要删除代码，保留原行并注释掉，必要时补一句中文说明，便于上游同步时对照。
- Gradle 脚本、XML、`.gitignore` 等非 Java 文件使用对应语法的 Magnet 标记（如 `// Magnet -`、`<!-- Magnet - -->`、`# Magnet -`）；`grep "Magnet"` 用于审计 fork 的全部改动。

## 2. Javadoc

- 一律使用多行 Javadoc 块，即使只有一句话也不要写成 `/** ... */` 单行：

```java
/**
 * Magnet 的版本检查器。
 */
public class MagnetVersionFetcher implements VersionFetcher {
```

- `public` / `protected` 类型、构造器和方法写 Javadoc；私有成员默认不写，需要说明时用 `//`。
- 摘要用一句中文说明作用；确有必要时再用 `<p>` 补一句注意事项（运行前提、依赖的配置项），不写设计背景。
- 方法有参数时用 `@param`，有返回值时必须用 `@return` 说明返回值；声明受检异常时用 `@throws`：

```java
/**
 * 校验缓冲区开头的 HMAC-SHA256 签名，并把读取位置移到签名之后。
 *
 * @param buf 待校验的缓冲区，读取位置需指向签名开头
 * @return 签名是否有效
 */
public static boolean checkIntegrity(ByteBuf buf) {
```

- 类型/方法用 `{@link X}`，字面量、标识符、包名、配置键用 `{@code x}`。
- `@Override` 方法行为自明时不重复接口 Javadoc。

## 3. 行内注释（`//`）

- 只在代码本身看不出来时才写，一句能说清就不写第二句。
- 只写两类内容：**注意事项**（线程、Netty 引用计数、协议状态、反射行为、配置副作用等）或**代码作用**（这段代码在做什么、为什么必须放在这个位置）。
- 不写设计决策、方案对比、历史背景和实现过程；这类内容放在 PR 描述或提交信息里。
- 不复述代码已经表达的“做什么”，不保留注释掉的调试代码（第 1 节禁用上游行为的写法除外）。
- 连续多行 `//` 与相邻代码同缩进；不要用大段 `/* ... */`。

```java
// 先解析再释放：缓冲区释放后任何读取都会抛 IllegalReferenceCountException。
```

## 4. 配置项

`MagnetConfig.init(File)` 会通过反射调用所有无参、返回 `void` 的 `private static` 方法，因此每个选项用一个同名的加载方法来读取：

```java
private static boolean exampleOption;

private static void exampleOption() {
    exampleOption = getBoolean("settings.example-option", false);
}
```

- helper 的签名是 `(path, default)`，没有注释参数；不要调用不存在的三参数重载。
- `magnet.yml` 目前只有 `HEADER` 文本。新增的配置说明使用简体中文；现有的英文 `HEADER` 保持不动，除非用户明确要求翻译。
- 世界级选项放在 `MagnetWorldConfig`：`set` 写入 `world-settings.default.<path>`，`get*` 先读 `world-settings.<world>.<path>`，再回退到 default。
- 注意 helper 的 `def` 只通过 `addDefault` 写入文件，读取表达式是 `config.getBoolean(path, config.getBoolean(path))`；首次读取尚未写入时会得到类型默认值，新增默认为 `true` 的选项时要先修正 helper 或显式 `set`。
- 实现细节不写进配置说明，放到字段 `//` 注释里。

## 5. 格式与补丁卫生

- 4 空格缩进，不用 Tab，不留行尾空白，不使用通配 import。
- 不要重排、对齐或改写与本次改动无关的行：补丁按行生成，无关格式变化会制造合并噪音。
- 只编写或翻译 Magnet 自己新增的注释；上游原有注释保持原样，即使其中是英文。
- 新增文件直接以 `package` 开头，不加许可证头（与现有 Magnet 及上游文件一致）。
- 调试输出使用 `MagnetConfig.log(...)`（受 `verbose` 控制，仅 `org.mineblock.magnet` 包内可访问；当前没有 `logError`，也没有统一的 `[Magnet]` 前缀）。其他包的日志使用 `Bukkit.getLogger()` 或上游 `LOGGER`。

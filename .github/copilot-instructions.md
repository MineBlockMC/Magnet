# Copilot instructions for Magnet

Magnet is a Paper fork for Minecraft 1.12.2 built on the archived [Dionysus](https://github.com/nopjmp/Dionysus) fork. The active branch is `ver/1.12.2`; the fork's goal is keeping the old version alive, with an emphasis on modern dependency versions rather than gameplay changes.

## Repository model (read first)

The Java sources live in `Magnet-API/` and `Magnet-Server/`, but **neither directory is tracked by git**. They are disposable clones that the Toothpick build recreates from the `Dionysus` submodule and resets to that upstream's `origin/master`. The tracked source of truth is `patches/api/*.patch` and `patches/server/*.patch`.

- Any uncommitted edit inside `Magnet-API/` or `Magnet-Server/` is destroyed by the next `applyPatches`.
- Workflow: edit in `Magnet-API/`/`Magnet-Server/`, `git commit` in that repository, then run `gradlew rebuildPatches`. One commit becomes one patch file.
- Patches are applied in filename order, and `rebuildPatches` deletes and regenerates all of them with `git format-patch --zero-commit --full-index -N origin/master`. Do not hand-write or hand-edit patch files, and do not rename them manually.
- The patch's author/date metadata comes from the commit made in the subproject, so git identity must be configured there.
- `Dionysus/` is a git submodule pinned to the commit stored in `last-dionysus`. Do not modify files inside it; use `updateUpstream` instead.

## Build, test, and package

Prerequisites: JDK 21 or newer (the build runs on JDK 25 and targets Java 21 bytecode), Git, `bash` on `PATH` (`setupUpstream` runs `./dionysus patch` through bash — use Git Bash on Windows), and Maven (`mvn`) on `PATH` for `paperclip`. The wrapper pins Gradle 9.8.1; the distribution URL points at the Tencent mirror and `gradlew wrapper` resets it to services.gradle.org, so restore the mirror line afterwards.

First-time setup (also required after an upstream bump). It clones/patches the upstream trees, so the first run is slow:

```bash
gradlew applyPatches
```

`build` fails with "Workspace has not been setup. Try running './gradlew applyPatches' first" until this has been run.

```bash
gradlew build        # compile + test both projects
gradlew paperclip    # builds magnetclip.jar in the repository root (requires Maven)
```

On Windows use `.\gradlew.bat`.

Tests are JUnit 4 and run through Gradle, not Maven:

```bash
gradlew :magnet-api:test
gradlew :magnet-server:test
gradlew :magnet-api:test --tests "org.bukkit.ChatColorTest"        # single class
gradlew :magnet-server:test --tests "org.bukkit.BiomeTest.testBukkitToMinecraft" # single method
```

`:magnet-server:test` excludes `org/bukkit/craftbukkit/inventory/ItemStack*Test`.

Incremental compilation is unreliable here: after editing server sources, `:magnet-server:compileJava` can fail in untouched NMS files with errors like `cannot find symbol: method isValidLocation()` or `a has private access`, because the incremental step resolves those classes from the `minecraft-server` jar instead of the patched source tree. Recover with `gradlew :magnet-server:clean :magnet-server:compileJava`.

Running a server (working directory is `Magnet-Server/run`):

```bash
gradlew runServer     # runs the packaged shadow jar
gradlew runDevServer  # runs unrelocated classes, main class org.bukkit.craftbukkit.Main
```

Upstream maintenance:

```bash
gradlew updateUpstream  # move the Dionysus submodule to origin/dev
gradlew applyPatches    # re-apply patches on the new upstream
gradlew upstreamCommit  # commit the submodule bump ("Updated Upstream (Dionysus)")
```

## Architecture

The "big picture" is a layered patch stack: Minecraft 1.12.2 → Spigot/CraftBukkit → Paper → **Dionysus** (submodule, archived upstream) → **Magnet** patches.

`settings.gradle.kts` applies the `xyz.jpenilla.toothpick` settings plugin, which turns the two upstream trees into Gradle projects. Upstream Toothpick is unmaintained and built against Gradle 7 + Shadow 7, so the plugin is vendored under `build-logic/` (an included build, resolved through `pluginManagement { includeBuild("build-logic") }`) and patched for Gradle 9 + Shadow 9; keep the two plugin ids (`xyz.jpenilla.toothpick`, `xyz.jpenilla.toothpick.settings`) unchanged:

- `:magnet-api` ← `Dionysus/Magnet-API` + `patches/api` — the Bukkit/Paper API (`org.bukkit...`).
- `:magnet-server` ← `Dionysus/Magnet-Server` + `patches/server` — CraftBukkit plus NMS. NMS lives in `net.minecraft.server` and is not relocated in source; Toothpick's `minecraftVersion = "1.12.2"` / `nmsPackage = "1_12_R1"` drive the packaging steps.

Type-safe project accessors are enabled, so build scripts refer to `projects.magnetApi` / `projects.magnetServer`. `paperclip` assembles `magnetclip.jar`, a self-contained launcher produced by the Maven Paperclip project under `Dionysus/Paper/work/Paperclip`.

Magnet-specific code lives in `:magnet-server`:

- `org.mineblock.magnet.MagnetConfig` — loads `magnet.yml` from the server working directory at startup (called from `DedicatedServer`).
- `org.mineblock.magnet.proxy` — backport of Velocity "modern" player-info forwarding to the 1.12.2 protocol. Because 1.12.2 has no login-plugin packets, the request/response frames are written by hand with Netty and `VelocityForwardingHandler` consumes the reply before ViaVersion can cancel it. It requires `online-mode=false` and ViaVersion on the backend.

Build-script responsibilities worth knowing:

- Root [`build.gradle.kts`](../build.gradle.kts) configures Toothpick (fork name, version, upstream, patch directories), adds the PaperMC repo, creates the missing decompile `libraries/` directory that `importMCDev` expects, and rewrites Paperclip POM repository URLs to HTTPS before packaging, restoring the upstream POMs afterwards via `restorePaperclipPoms` (run as `finalizedBy`). Never commit those temporary POM edits.
- [`subprojects/api.gradle.kts`](../subprojects/api.gradle.kts) and [`subprojects/server.gradle.kts`](../subprojects/server.gradle.kts) disable Javadoc because the 1.12.2 sources do not doc cleanly on JDK 25. Dionysus' SIMD utilities were removed because `jdk.incubator.vector` is only safe on Java 17/18.
- `server.gradle.kts` resolves the local Maven repository like Maven itself (`-Dmaven.repo.local`, then `settings.xml`, then `~/.m2/repository`) because `org.spigotmc:minecraft-server` is installed there by the upstream build.
- [`build-logic/`](../build-logic) is a vendored copy of Toothpick (MIT) ported to Gradle 9 / Shadow 9. It owns the `applyPatches`/`rebuildPatches`/`paperclip`/`importMCDev` tasks, the POM-driven dependency and relocation setup, and the `runServer`/`runDevServer` tasks. Changes there affect every build, so run `gradlew help --warning-mode all` after editing it.

## Key conventions

- Mark every change made to upstream-owned files with Magnet markers; comments, Javadoc, and `magnet.yml` text added by Magnet are written in Simplified Chinese, and upstream comments are left untouched. The detailed rules (markers, Javadoc, inline comments, `magnet.yml` option text) live in [.github/instructions/java-comments.instructions.md](instructions/java-comments.instructions.md). `grep "// Magnet"` audits the fork's diff.
- There are no API additions so far; fork behaviour lives in `org.mineblock.magnet` and in `patches/server`.
- `MagnetConfig` options: add a `private static <type> option` field plus a `private static void optionName()` loader. `MagnetConfig.load()` reflectively invokes every private static, no-arg, `void` method, and the `getBoolean/getInt/getDouble/getString/getStringList` helpers write the default value and comment into `magnet.yml`.
- Long-lived fork features get their own patch and their own `org.mineblock.magnet` class rather than being folded into upstream classes.
- Debug output goes through `MagnetConfig.log(...)` / `logError(...)`, is gated by the `verbose` config option, and is prefixed `[Magnet]`.
- Log4j is deliberately pinned to 2.26.1 in two places that must stay in sync: `<log4j2.version>` in `Magnet-Server/pom.xml` and `pinnedLog4j` in [`subprojects/server.gradle.kts`](../subprojects/server.gradle.kts) (it is shaded ahead of the legacy log4j bundled with `minecraft-server`).
- Versions are `git-Magnet-<BUILD_NUMBER or git hash>` and artifacts are published under the `org.mineblock.magnet` group.
- Upstream Java style (inherited from Bukkit/CraftBukkit): 4 spaces, no tabs, no trailing whitespace, no wildcard imports.

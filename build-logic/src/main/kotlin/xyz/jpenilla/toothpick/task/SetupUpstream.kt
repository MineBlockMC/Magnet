/*
 * This file is part of Toothpick, licensed under the MIT License.
 *
 * Copyright (c) 2020-2021 Jason Penilla & Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.toothpick.task

import org.gradle.api.tasks.TaskAction
import xyz.jpenilla.toothpick.bashCmd
import xyz.jpenilla.toothpick.gitCmd
import xyz.jpenilla.toothpick.gitHash
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

public abstract class SetupUpstream : ToothpickTask() {
  @TaskAction
  public fun setupUpstream() {
    val upstreamDir = toothpick.upstreamDir
    if (upstreamDir.resolve(toothpick.upstreamLowercase).exists()) {
      setupPaperOrBYOFUpstream(upstreamDir)
    } else if (
      upstreamDir.resolve("build.gradle.kts").exists()
      && upstreamDir.resolve("subprojects/server.gradle.kts").exists()
      && upstreamDir.resolve("subprojects/api.gradle.kts").exists()
    ) {
      setupToothpickUpstream(upstreamDir)
    } else {
      error("Don't know how to setup upstream!")
    }
    // Magnet - 整个上游初始化成功后才写入标记，避免半途失败后跳过 setup
    toothpick.lastUpstream.writeText(gitHash(upstreamDir))
  }

  private fun setupPaperOrBYOFUpstream(upstreamDir: File) {
    try {
      ensurePaperTools(upstreamDir)
      runSetupUpstreamCommand("./${toothpick.upstreamLowercase} patch", upstreamDir)
    } finally {
      restorePaperTools(upstreamDir)
    }
  }

  // Magnet - Purpur 层由本插件直接完成，不再启动 Purpur 的 Gradle 7.0.1
  private fun setupToothpickUpstream(upstreamDir: File) {
    // Magnet - Windows 的 ProcessBuilder 会优先在 System32 找到 WSL 的 bash.exe，
    // 因此这里显式用 Git Bash 准备 Paper 工作区
    val paperDir = upstreamDir.resolve("Paper")
    if (paperDir.resolve("paper").isFile) {
      try {
        ensurePaperTools(paperDir)
        logger.lifecycle(">>> Applying Paper patches")
        val paperResult = bashCmd("./paper patch", dir = paperDir, printOut = true, environment = javaHomeEnvironment())
        if (paperResult.exitCode != 0) {
          error("Failed to apply Paper patches: script exited with code ${paperResult.exitCode}")
        }
      } finally {
        restorePaperTools(paperDir)
      }
    }

    val decompDir = paperDir.resolve("work/Minecraft/${toothpick.minecraftVersion}")
    val paperApiDir = paperDir.resolve("Paper-API")
    val paperServerDir = paperDir.resolve("Paper-Server")
    // Magnet - 中间层固定为 Purpur，目录位于子模块内部
    val forkName = "Purpur"
    val forkApiDir = upstreamDir.resolve("$forkName-API")
    val forkServerDir = upstreamDir.resolve("$forkName-Server")
    val apiPatchesDir = upstreamDir.resolve("patches/api")
    val serverPatchesDir = upstreamDir.resolve("patches/server")

    // Purpur 的 server 补丁依赖的 NMS 源码需要先导入 Paper-Server
    McDevImporter(
      paperDecompDir = decompDir,
      upstreamServer = paperServerDir,
      importsConfiguration = upstreamDir.resolve("mcdevimports.conf"),
      logger = logger
    ).import(patches = patchFiles(serverPatchesDir))

    applyPatchSet(
      baseDir = paperApiDir,
      projectDir = forkApiDir,
      patchesDir = apiPatchesDir,
      applyCommand = ApplyPatches.DEFAULT_APPLY_COMMAND,
      logger = logger,
      workingDir = upstreamDir
    )
    applyPatchSet(
      baseDir = paperServerDir,
      projectDir = forkServerDir,
      patchesDir = serverPatchesDir,
      applyCommand = ApplyPatches.DEFAULT_APPLY_COMMAND,
      logger = logger,
      workingDir = upstreamDir
    )
  }

  private fun runSetupUpstreamCommand(setupUpstreamCommand: String, upstreamDir: File) {
    val result = bashCmd(setupUpstreamCommand, dir = upstreamDir, printOut = true, environment = javaHomeEnvironment())
    if (result.exitCode != 0) {
      error("Failed to apply upstream patches: script exited with code ${result.exitCode}")
    }
  }

  // Magnet - Paper 1.16.5 自带的 SpecialSource 只支持到 Java 17；
  // 用新版 ASM 替换其内置 ASM 后可在 JDK 21/25 上运行，且输出与旧工具一致
  private fun ensurePaperTools(paperDir: File) {
    val updateResult = gitCmd("submodule", "update", "--init", dir = paperDir, printOut = true)
    if (updateResult.exitCode != 0) {
      error("Failed to initialize Paper submodules: git exited with code ${updateResult.exitCode}")
    }

    val buildDataBin = paperDir.resolve("work/BuildData/bin")
    val targetJars = listOf("SpecialSource-2.jar", "SpecialSource.jar").map { buildDataBin.resolve(it) }
    targetJars.forEach {
      if (!it.isFile) error("Missing Paper build tool: ${it.absolutePath}")
    }

    val cacheDir = toolCacheDir()
    val asmJars = ASM_MODULES.map { module ->
      val file = cacheDir.resolve("asm/$ASM_VERSION/$module-$ASM_VERSION.jar")
      if (!file.isFile) {
        download("$ASM_MAVEN/$module/$ASM_VERSION/$module-$ASM_VERSION.jar", file)
      }
      file
    }

    val patchedDir = cacheDir.resolve("patched/$ASM_VERSION")
    targetJars.forEach { target ->
      val patched = patchedDir.resolve(target.name)
      val newestInput = (listOf(target) + asmJars).maxOfOrNull { it.lastModified() } ?: 0L
      if (!patched.isFile || patched.lastModified() < newestInput) {
        buildPatchedJar(target, asmJars, patched)
      }
      patched.copyTo(target, overwrite = true)
      logger.lifecycle(">>> Patched ${target.name} with ASM $ASM_VERSION")
    }
  }

  // Magnet - 恢复上游工具 jar，避免 Paper/BuildData 子模块长期处于修改状态
  private fun restorePaperTools(paperDir: File) {
    val buildDataDir = paperDir.resolve("work/BuildData")
    if (!buildDataDir.resolve(".git").exists()) return
    val result = gitCmd("checkout", "--", "bin/SpecialSource-2.jar", "bin/SpecialSource.jar", dir = buildDataDir)
    if (result.exitCode != 0) {
      logger.warn("Failed to restore Paper build tools; run 'git -C ${buildDataDir.absolutePath} checkout -- bin' to clean up.")
    }
  }

  private fun toolCacheDir(): File {
    val gradleHome = System.getenv("GRADLE_USER_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
      ?: File(System.getProperty("user.home"), ".gradle")
    return File(gradleHome, "caches/magnet-tools")
  }

  private fun download(url: String, target: File) {
    logger.lifecycle(">>> Downloading $url")
    target.parentFile.mkdirs()
    val temp = File(target.parentFile, "${target.name}.tmp")
    val connection = URI(url).toURL().openConnection().apply {
      connectTimeout = 30_000
      readTimeout = 120_000
    }
    connection.getInputStream().use { input ->
      temp.outputStream().buffered().use { output -> input.copyTo(output) }
    }
    if (!temp.renameTo(target)) {
      temp.copyTo(target, overwrite = true)
      temp.delete()
    }
  }

  private fun buildPatchedJar(source: File, asmJars: List<File>, output: File) {
    output.parentFile.mkdirs()
    val temp = File(output.parentFile, "${output.name}.tmp")
    val seen = HashSet<String>()
    ZipOutputStream(temp.outputStream().buffered()).use { out ->
      ZipFile(source).use { zip ->
        for (entry in zip.entries()) {
          if (entry.name.startsWith("org/objectweb/asm/")) continue
          if (entry.isDirectory) {
            if (seen.add(entry.name)) {
              out.putNextEntry(ZipEntry(entry.name))
              out.closeEntry()
            }
            continue
          }
          addZipEntry(out, entry.name, zip.getInputStream(entry), seen)
        }
      }
      for (asmJar in asmJars) {
        ZipFile(asmJar).use { zip ->
          for (entry in zip.entries()) {
            if (entry.isDirectory || !entry.name.startsWith("org/objectweb/asm/")) continue
            addZipEntry(out, entry.name, zip.getInputStream(entry), seen)
          }
        }
      }
    }
    if (!temp.renameTo(output)) {
      temp.copyTo(output, overwrite = true)
      temp.delete()
    }
  }

  private fun addZipEntry(out: ZipOutputStream, name: String, input: InputStream, seen: MutableSet<String>) {
    if (!seen.add(name)) {
      input.close()
      return
    }
    out.putNextEntry(ZipEntry(name))
    input.use { it.copyTo(out) }
    out.closeEntry()
  }

  // Magnet - 子进程统一使用运行 Gradle 的 JDK
  private fun javaHomeEnvironment(): Map<String, String> {
    val javaHome = File(System.getProperty("java.home")).absolutePath
    val path = System.getenv("PATH") ?: ""
    return mapOf(
      "JAVA_HOME" to javaHome,
      "PATH" to "${File(javaHome, "bin").absolutePath}${File.pathSeparator}$path"
    )
  }

  private companion object {
    private const val ASM_VERSION = "9.9.1"
    private const val ASM_MAVEN = "https://repo1.maven.org/maven2/org/ow2/asm"
    private val ASM_MODULES = listOf("asm", "asm-tree", "asm-commons", "asm-analysis", "asm-util")
  }
}
